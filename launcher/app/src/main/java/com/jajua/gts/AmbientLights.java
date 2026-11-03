package com.jajua.gts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Our own BLE client for the ambient RGB light controllers that the "Magic Lantern" app drives,
 * so the launcher can set the lights itself instead of opening that app. Picking an accent colour
 * can then recolour the interface and the lights in one gesture.
 *
 * <p>Clean-room: only the wire protocol is reused (interoperability with hardware the user owns),
 * never that app's code. The controller exposes GATT service {@code fff0} with a write-only
 * characteristic {@code fff3}; every command is nine bytes framed {@code 7E ... EF}:
 * <ul>
 *   <li>colour&nbsp;&nbsp;{@code 7E 07 05 03 RR GG BB 10 EF}</li>
 *   <li>on/off&nbsp;&nbsp;{@code 7E 04 04 SS 00 SS FF 00 EF} (SS = 01 on / 00 off)</li>
 *   <li>brightness&nbsp;&nbsp;{@code 7E 04 01 BB MM FF FF 00 EF} (BB 0..100, MM mode 0xFF)</li>
 * </ul>
 * A single connection is kept alive (see {@link LightsService}) so a colour change reaches the
 * lights instantly, and it reconnects on its own if the link drops.
 */
final class AmbientLights {
    private static final String TAG = "gts-lights";
    private static final UUID SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_CHAR = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");

    /** Gap between writes. The controller drops bytes if hit too fast; the source app uses ~5 ms. */
    private static final long WRITE_GAP_MS = 25;
    private static final long RECONNECT_MS = 4000;
    private static final long SCAN_MS = 8000;

    interface ScanListener {
        /** address -> display name, newest set each time a device is seen. */
        void onDevices(Map<String, String> devices);
    }

    private static AmbientLights instance;

    static synchronized AmbientLights get(Context c) {
        if (instance == null) instance = new AmbientLights(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Prefs prefs;
    private final BluetoothAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler io;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private volatile boolean ready;
    private boolean wanted;              // the service wants us connected
    private String address;
    private boolean triedRandom;         // reconnect fallback for random-address controllers

    /** Devices as the scan found them, so we connect with the right address type (not assumed public). */
    private final Map<String, BluetoothDevice> scannedDevices = new HashMap<>();

    /** Commands waiting for the link; consecutive colour commands coalesce to the latest. */
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();

    private AmbientLights(Context app) {
        this.app = app;
        this.prefs = Prefs.get(app);
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = bm != null ? bm.getAdapter() : null;
        HandlerThread t = new HandlerThread("gts-lights");
        t.start();
        this.io = new Handler(t.getLooper());
    }

    // --- Lifecycle, driven by LightsService ------------------------------------------------

    /** Connect to the saved controller and keep the link up until {@link #stop()}. */
    void start() {
        wanted = true;
        triedRandom = false;
        address = prefs.lightsAddress();
        if (address == null || adapter == null || !adapter.isEnabled() || !canConnect()) {
            Log.d(TAG, "start skipped: addr=" + address + " btOff=" + bluetoothOff() + " canConnect=" + canConnect());
            return;
        }
        io.post(this::openGatt);
    }

    void stop() {
        wanted = false;
        io.post(this::closeGatt);
    }

    boolean isReady() {
        return ready;
    }

    // --- Public commands -------------------------------------------------------------------

    /**
     * The launcher's accent colour and the lights are one thing: this sets the accent (so the whole
     * interface recolours) and, when the lights are linked, sends the same colour to them. The home
     * screen rebuilds on resume because {@link Prefs#setAccent} bumps the visual stamp.
     */
    static void applyAccentColor(Context c, int color) {
        Prefs p = Prefs.get(c);
        p.setAccent(color, p.glow());
        get(c).pushAccentIfLinked();
    }

    /** Send the launcher's current accent colour if the user linked the lights to it. */
    void pushAccentIfLinked() {
        if (prefs.lightsLinkColor()) sendColor(prefs.accentColor());
    }

    void sendColor(int argb) {
        byte r = (byte) Color.red(argb), g = (byte) Color.green(argb), b = (byte) Color.blue(argb);
        enqueueColor(new byte[] {0x7E, 0x07, 0x05, 0x03, r, g, b, 0x10, (byte) 0xEF});
    }

    void setOn(boolean on) {
        byte s = (byte) (on ? 1 : 0);
        enqueue(new byte[] {0x7E, 0x04, 0x04, s, 0x00, s, (byte) 0xFF, 0x00, (byte) 0xEF});
    }

    /** brightness 0..100. */
    void setBrightness(int pct) {
        byte b = (byte) Math.max(0, Math.min(100, pct));
        enqueue(new byte[] {0x7E, 0x04, 0x01, b, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xEF});
    }

    /** A dynamic effect (one of {@link Lights#EFFECTS}). */
    void setMode(int id) {
        enqueue(new byte[] {0x7E, 0x05, 0x03, (byte) id, 0x06, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xEF});
    }

    /** Effect speed 1..100. */
    void setModeSpeed(int speed) {
        byte s = (byte) Math.max(1, Math.min(100, speed));
        enqueue(new byte[] {0x7E, 0x04, 0x02, s, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xEF});
    }

    /** A built-in scene (one of {@link Lights#SCENES}). */
    void setScene(int id) {
        enqueue(new byte[] {0x7E, 0x05, 0x31, (byte) id, 0x07, (byte) 0xFF, (byte) 0xFF, 0x01, (byte) 0xEF});
    }

    /** White on the warm/cold axis: {@code cold} 0..100 (0 = fully warm, 100 = fully cold). */
    void setWhite(int cold) {
        int c = Math.max(0, Math.min(100, cold));
        enqueue(new byte[] {0x7E, 0x06, 0x05, 0x02, (byte) (100 - c), (byte) c, (byte) 0xFF, 0x08, (byte) 0xEF});
    }

    /** A music frame: the colour with its brightness modulated by the audio amplitude (byte7 = 0x20). */
    private void sendMusicColor(int argb) {
        byte r = (byte) Color.red(argb), g = (byte) Color.green(argb), b = (byte) Color.blue(argb);
        enqueueColor(new byte[] {0x7E, 0x07, 0x05, 0x03, r, g, b, 0x20, (byte) 0xEF});
    }

    // --- Queue -----------------------------------------------------------------------------

    private void enqueueColor(byte[] cmd) {
        synchronized (queue) {
            // Drop an unsent colour so a drag of the picker never backs up the link.
            byte[] tail = queue.peekLast();
            if (tail != null && tail.length > 3 && tail[3] == 0x03) queue.pollLast();
            queue.addLast(cmd);
        }
        io.post(this::drain);
    }

    private void enqueue(byte[] cmd) {
        synchronized (queue) {
            queue.addLast(cmd);
        }
        io.post(this::drain);
    }

    private void drain() {
        if (!ready || gatt == null || writeChar == null) return;
        byte[] cmd;
        synchronized (queue) {
            cmd = queue.pollFirst();
        }
        if (cmd == null) return;
        write(cmd);
        if (!queue.isEmpty()) io.postDelayed(this::drain, WRITE_GAP_MS);
    }

    @SuppressLint("MissingPermission")
    private void write(byte[] cmd) {
        if (!canConnect()) return;
        StringBuilder h = new StringBuilder();
        for (byte b : cmd) h.append(String.format("%02X", b));
        Log.d(TAG, "write " + h);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(writeChar, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                writeChar.setValue(cmd);
                gatt.writeCharacteristic(writeChar);
            }
        } catch (Exception ignored) {
            // A transient failure just drops this frame; the next colour will refresh the lights.
        }
    }

    // --- GATT ------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private void openGatt() {
        if (gatt != null || address == null || adapter == null || !canConnect()) return;
        try {
            BluetoothDevice dev = deviceFor(address);
            Log.d(TAG, "connecting to " + address + " type=" + dev.getType());
            gatt = dev.connectGatt(app, true, cb, BluetoothDevice.TRANSPORT_LE);
        } catch (Exception e) {
            Log.w(TAG, "connectGatt failed", e);
        }
    }

    /** Prefer the exact device the scan returned (correct address type); else build one from the
     * string, trying the random type on the retry since these controllers often use it. */
    private BluetoothDevice deviceFor(String addr) {
        BluetoothDevice scanned = scannedDevices.get(addr);
        if (scanned != null) return scanned;
        if (triedRandom && Build.VERSION.SDK_INT >= 34) {
            try {
                return adapter.getRemoteLeDevice(addr, BluetoothDevice.ADDRESS_TYPE_RANDOM);
            } catch (Exception ignored) {
            }
        }
        return adapter.getRemoteDevice(addr);
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        ready = false;
        writeChar = null;
        synchronized (queue) {
            queue.clear();
        }
        if (gatt != null && canConnect()) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
        }
        gatt = null;
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            Log.d(TAG, "state=" + newState + " status=" + status);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                triedRandom = false;
                if (canConnect()) io.post(() -> {
                    try {
                        g.discoverServices();
                    } catch (Exception ignored) {
                    }
                });
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                ready = false;
                writeChar = null;
                if (music) stopMusic();
                // A failed connect by string address (no scanned device) may be the wrong address
                // type; switch to random once before the normal retry cadence.
                if (wanted && status != BluetoothGatt.GATT_SUCCESS
                        && scannedDevices.get(address) == null && !triedRandom && Build.VERSION.SDK_INT >= 34) {
                    triedRandom = true;
                    io.post(() -> {
                        closeGatt();
                        openGatt();
                    });
                } else if (wanted) {
                    io.postDelayed(AmbientLights.this::reconnect, RECONNECT_MS);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService s = g.getService(SERVICE);
            writeChar = s != null ? s.getCharacteristic(WRITE_CHAR) : null;
            ready = writeChar != null;
            Log.d(TAG, "services discovered; fff0=" + (s != null) + " fff3=" + (writeChar != null));
            if (ready) {
                restoreState();
                io.post(AmbientLights.this::drain);
            }
        }
    };

    /** Bring the lights back to the last state the user left: on/off, then the solid colour, the
     * effect (with its speed) or the scene, plus the brightness. Called on every (re)connect. */
    private void restoreState() {
        boolean on = prefs.lightsOn();
        setOn(on);
        if (!on) return;
        switch (prefs.lightsMode()) {
            case Prefs.MODE_EFFECT:
                if (prefs.lightsEffect() >= 0) {
                    setMode(prefs.lightsEffect());
                    setModeSpeed(prefs.lightsSpeed());
                }
                break;
            case Prefs.MODE_SCENE:
                if (prefs.lightsScene() >= 0) setScene(prefs.lightsScene());
                break;
            default:
                if (prefs.lightsLinkColor()) sendColor(prefs.accentColor());
        }
        setBrightness(prefs.lightsBrightness());
    }

    @SuppressLint("MissingPermission")
    private void reconnect() {
        if (!wanted || !canConnect()) return;
        if (gatt != null) {
            try {
                gatt.connect();
                return;
            } catch (Exception ignored) {
            }
        }
        openGatt();
    }

    // --- Scanning (used by Ajustes to pick the controller) ---------------------------------

    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, Integer> rssis = new LinkedHashMap<>();

    /**
     * These controllers don't advertise their {@code fff0} service, and a connected one stops
     * advertising at all, so we scan with no filter and list every device, strongest signal first,
     * for the user to pick (like the original app does). A device advertising {@code fff0} is
     * flagged as a likely match.
     */
    @SuppressLint("MissingPermission")
    void scan(ScanListener listener) {
        if (adapter == null || !adapter.isEnabled() || !canScan()) {
            listener.onDevices(new LinkedHashMap<>());
            return;
        }
        stopScan();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onDevices(new LinkedHashMap<>());
            return;
        }
        names.clear();
        rssis.clear();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanCb = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int type, ScanResult r) {
                BluetoothDevice d = r.getDevice();
                String addr = d.getAddress();
                scannedDevices.put(addr, d);
                String name = d.getName();
                if (name == null && r.getScanRecord() != null) name = r.getScanRecord().getDeviceName();
                boolean likely = r.getScanRecord() != null && r.getScanRecord().getServiceUuids() != null
                        && r.getScanRecord().getServiceUuids().contains(new ParcelUuid(SERVICE));
                String label = (name == null || name.isEmpty() ? "(sin nombre)" : name)
                        + (likely ? " ✓ luces" : "") + " · " + addr + " · " + r.getRssi() + " dBm";
                names.put(addr, label);
                rssis.put(addr, r.getRssi());
                main.post(() -> listener.onDevices(sortedByRssi()));
            }
        };
        try {
            scanner.startScan(new ArrayList<ScanFilter>(), settings, scanCb);
        } catch (Exception ignored) {
        }
        main.postDelayed(this::stopScan, SCAN_MS);
    }

    /** Devices seen so far, strongest signal (closest) first. */
    private Map<String, String> sortedByRssi() {
        List<Map.Entry<String, Integer>> es = new ArrayList<>(rssis.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : es) out.put(e.getKey(), names.get(e.getKey()));
        return out;
    }

    @SuppressLint("MissingPermission")
    void stopScan() {
        if (scanner != null && scanCb != null && canScan()) {
            try {
                scanner.stopScan(scanCb);
            } catch (Exception ignored) {
            }
        }
        scanCb = null;
    }

    // --- Music: react to sound from the microphone -----------------------------------------

    private MediaRecorder recorder;
    private volatile boolean music;
    private float musicGain = 1f;
    private boolean musicRainbow;
    private float musicHue;

    boolean isMusic() {
        return music;
    }

    /** Microphone sensitivity 1..100; higher reacts to quieter sound. */
    void setMusicSensitivity(int pct) {
        musicGain = 0.4f + (Math.max(1, Math.min(100, pct)) / 100f) * 1.9f; // ~0.42..2.3
    }

    void setMusicRainbow(boolean on) {
        musicRainbow = on;
    }

    boolean canRecord() {
        return app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    void startMusic() {
        if (music || !canRecord()) return;
        setMusicSensitivity(prefs.lightsMusicSensitivity());
        musicRainbow = prefs.lightsMusicRainbow();
        io.post(() -> {
            try {
                recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(app) : new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setOutputFile(new java.io.File(app.getCacheDir(), "lights-mic.3gp").getAbsolutePath());
                recorder.prepare();
                recorder.start();
                recorder.getMaxAmplitude(); // prime the baseline
                music = true;
                io.postDelayed(musicPoll, 150);
            } catch (Exception e) {
                Log.w(TAG, "music start failed", e);
                stopMusic();
            }
        });
    }

    void stopMusic() {
        music = false;
        io.post(() -> {
            io.removeCallbacks(musicPoll);
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Exception ignored) {
                }
                try {
                    recorder.release();
                } catch (Exception ignored) {
                }
                recorder = null;
            }
        });
    }

    private final Runnable musicPoll = new Runnable() {
        @Override
        public void run() {
            if (!music || recorder == null) return;
            int amp = 0;
            try {
                amp = recorder.getMaxAmplitude();
            } catch (Exception ignored) {
            }
            float level = Math.min(1f, amp * musicGain / 12000f);  // ~0..1 from mic loudness
            float scale = 0.12f + 0.88f * level;                   // never fully dark, full on peaks
            int base;
            if (musicRainbow) {
                musicHue = (musicHue + 6f) % 360f;                 // drift through the rainbow
                base = Color.HSVToColor(new float[] {musicHue, 1f, 1f});
            } else {
                base = prefs.accentColor();
            }
            sendMusicColor(Color.rgb((int) (Color.red(base) * scale),
                    (int) (Color.green(base) * scale), (int) (Color.blue(base) * scale)));
            io.postDelayed(this, 70);
        }
    };

    // --- Permissions -----------------------------------------------------------------------

    /** BLUETOOTH_CONNECT is only a runtime permission from Android 12 (API 31). */
    boolean canConnect() {
        return Build.VERSION.SDK_INT < 31
                || app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    boolean canScan() {
        if (Build.VERSION.SDK_INT >= 31) {
            return app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && canConnect();
        }
        // Pre-12 a BLE scan needs location permission.
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    boolean bluetoothOff() {
        return adapter == null || !adapter.isEnabled();
    }
}
