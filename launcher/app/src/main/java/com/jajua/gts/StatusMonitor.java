package com.jajua.gts;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Clock, Bluetooth and Wi-Fi state, driven purely by system broadcasts and callbacks (no polling).
 * Only reads state: it never enables, pairs or connects anything, so it cannot disturb the
 * head unit's own Bluetooth connection.
 */
final class StatusMonitor {
    interface Listener {
        void onStatusChanged(StatusMonitor s);
    }

    // Head-unit side profiles: the car is the A2DP *sink* and the hands-free *client*.
    // These constants are hidden in the public SDK; values are from AOSP BluetoothProfile.
    private static final int A2DP_SINK = 11;
    private static final int HEADSET_CLIENT = 16;
    private static final String ACTION_A2DP_SINK =
            "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
    private static final String ACTION_HFP_CLIENT =
            "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED";

    /** Null when the device has no Android Bluetooth adapter or it cannot be read. */
    Boolean bluetoothOn;
    /** Only true when Android itself reports a phone on A2DP sink / hands-free client. */
    boolean phoneConnected;
    String phoneName;
    boolean wifiConnected;
    /**
     * Car battery voltage from the MCU (set by HomeActivity from TwVoltage). Android's own battery
     * voltage on the TS10 is a fixed fake value (5 mV), so it is not used.
     */
    Float volts;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Network> wifiNetworks = new HashSet<>();
    private boolean running;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                updateWifi();
                return;
            }
            if (ACTION_A2DP_SINK.equals(action) || ACTION_HFP_CLIENT.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (state == BluetoothProfile.STATE_CONNECTED && d != null) phoneName = safeName(d);
            }
            if (!Intent.ACTION_TIME_TICK.equals(action)) refreshBluetooth();
            listener.onStatusChanged(StatusMonitor.this);
        }
    };

    private final ConnectivityManager.NetworkCallback wifiCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            main.post(() -> {
                wifiNetworks.add(network);
                updateWifi();
            });
        }

        @Override
        public void onLost(Network network) {
            main.post(() -> {
                wifiNetworks.remove(network);
                updateWifi();
            });
        }
    };

    StatusMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(ACTION_A2DP_SINK);
        f.addAction(ACTION_HFP_CLIENT);
        f.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        context.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);

        wifiNetworks.clear();
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        try {
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback);
        } catch (RuntimeException ex) {
            // No network permission or service: the Wi-Fi icon simply stays hidden.
        }
        refreshBluetooth();
        listener.onStatusChanged(this);
    }

    void stop() {
        if (!running) return;
        running = false;
        context.unregisterReceiver(receiver);
        try {
            context.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(wifiCallback);
        } catch (RuntimeException ignored) {
            // Already unregistered.
        }
    }

    private void updateWifi() {
        // Wireless CarPlay turns the Wi-Fi chip into a hotspot for the phone; the client radio is
        // then off and any leftover network entry must not light the icon.
        android.net.wifi.WifiManager wm = context.getSystemService(android.net.wifi.WifiManager.class);
        boolean radioOn = wm == null || wm.isWifiEnabled();
        wifiConnected = radioOn && !wifiNetworks.isEmpty();
        if (running) listener.onStatusChanged(this);
    }

    private void refreshBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            bluetoothOn = null;
            phoneConnected = false;
            return;
        }
        try {
            bluetoothOn = adapter.isEnabled();
            phoneConnected = bluetoothOn
                    && (adapter.getProfileConnectionState(A2DP_SINK) == BluetoothAdapter.STATE_CONNECTED
                    || adapter.getProfileConnectionState(HEADSET_CLIENT) == BluetoothAdapter.STATE_CONNECTED);
            if (!phoneConnected) phoneName = null;
            else if (phoneName == null) phoneName = connectedName(adapter);
        } catch (SecurityException ex) {
            bluetoothOn = null;
            phoneConnected = false;
        }
    }

    /** BluetoothDevice.isConnected() is hidden but greylisted (readable) on Android 10. */
    @android.annotation.SuppressLint("MissingPermission") // SecurityException is caught below.
    private static String connectedName(BluetoothAdapter adapter) {
        try {
            Method isConnected = BluetoothDevice.class.getMethod("isConnected");
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                if (Boolean.TRUE.equals(isConnected.invoke(d))) return safeName(d);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Name is optional.
        }
        return null;
    }

    private static String safeName(BluetoothDevice d) {
        try {
            return d.getName();
        } catch (SecurityException ex) {
            return null;
        }
    }
}
