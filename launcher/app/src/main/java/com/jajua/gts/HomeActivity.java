package com.jajua.gts;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;

/**
 * The home screen: four tall shortcuts (CarPlay, Android Auto, Radio, Bluetooth Audio), the vehicle
 * panel, and a dock with the interior lights (Magic Lantern), the app drawer and settings. Every shortcut only opens the
 * app that already handles that function on the TS10; the launcher never talks to the hardware.
 */
public final class HomeActivity extends Activity implements AppCatalog.Listener, StatusMonitor.Listener,
        MediaMonitor.Listener, SpeedMonitor.Listener, TwBluetooth.Listener, TwCommand.Listener,
        TwVoltage.Listener, TwLights.Listener, TLinkState.Listener, DrawerView.Callbacks {
    private static final Role[] MAIN = {Role.CARPLAY, Role.ANDROID_AUTO, Role.RADIO, Role.BT_AUDIO};
    private static final Locale ES = new Locale("es");

    private Prefs prefs;
    private int builtStamp = -1;
    private TopBar top;
    private InfoPanel info;
    private Dock dock;
    private DrawerView drawer;
    private final EnumMap<Role, Tile> tiles = new EnumMap<>(Role.class);

    private StatusMonitor status;
    private MediaMonitor media;
    private SpeedMonitor speed;
    private TwBluetooth btSong;
    private TwCommand xt;
    private TwVoltage voltage;
    private TwLights lights;
    private TLinkState tlink;

    /** What the playback panel currently shows, which decides where its buttons go. */
    private enum Panel { IDLE, RADIO, BLUETOOTH, MUSIC, SESSION }

    private Panel panel = Panel.IDLE;
    /** USB music reports no state; remembers the last play/pause sent to it. */
    private boolean musicPlaying = true;
    /** elapsedRealtime when CarPlay/Android Auto was last opened from here. */
    private long phoneLinkSince;
    /** Audio source last opened from the home screen (in memory only). */
    private Role lastSource;

    private static final long VOLTAGE_REFRESH_MS = 30000;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    /** The MCU pushes voltage changes itself; this re-asks occasionally in case one is missed. */
    private final Runnable voltageRefresh = new Runnable() {
        @Override
        public void run() {
            voltage.request();
            lights.request();
            main.postDelayed(this, VOLTAGE_REFRESH_MS);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            AppCatalog.reload(HomeActivity.this, HomeActivity.this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        status = new StatusMonitor(this, this);
        media = new MediaMonitor(this, this);
        speed = new SpeedMonitor(this, this);
        btSong = new TwBluetooth(this, this);
        xt = new TwCommand(this, this);
        voltage = new TwVoltage(this);
        lights = new TwLights(this);
        tlink = new TLinkState(this, this);
        build();
        tlink.register();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        AppCatalog.reload(this, this);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @Override
    protected void onDestroy() {
        tlink.unregister();
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        askLocationOnce();
        // Listeners only run while the home screen is visible: zero work while another app is in front.
        status.start();
        media.start();
        speed.start();
        btSong.start();
        xt.start();
        voltage.start();
        lights.start();
        main.postDelayed(voltageRefresh, VOLTAGE_REFRESH_MS);
    }

    @Override
    protected void onStop() {
        status.stop();
        media.stop();
        speed.stop();
        btSong.stop();
        xt.stop();
        main.removeCallbacks(voltageRefresh);
        voltage.stop();
        lights.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.stamp() != builtStamp) build();
        else refreshTiles();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: go back to the main screen.
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation") // Android 13+ uses the OnBackInvoked callback above.
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        // A launcher never finishes on back.
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        build();
    }

    /**
     * The launcher hides the TS10's status bar and navigation arrows (time, Wi-Fi and voltage are
     * in its own top bar). A swipe from the edge shows them briefly; other apps are not affected.
     */
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void build() {
        Ui.init(this, true);
        Palette.load(prefs);
        builtStamp = prefs.stamp();
        boolean drawerOpen = drawer != null && drawer.getVisibility() == View.VISIBLE;
        Palette p = Palette.current;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);

        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(Ui.u(22), Ui.u(4), Ui.u(22), Ui.u(6));

        top = new TopBar(this, this::openMediaApp, () -> openRole(Role.BT_AUDIO), this::openWifiSettings);
        home.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.u(54)));

        LinearLayout main = new LinearLayout(this);
        // Smooth widen/narrow when CarPlay or Android Auto connects or disconnects.
        android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
        lt.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        lt.setDuration(220);
        main.setLayoutTransition(lt);
        tiles.clear();
        for (int i = 0; i < MAIN.length; i++) {
            Role role = MAIN[i];
            Tile t = new Tile(this, role.icon, role.title);
            t.setOnClickListener(v -> openRole(role));
            if (role == Role.CARPLAY) t.setOnHelp(() -> help(HelpDialog.Kind.CARPLAY, role));
            if (role == Role.ANDROID_AUTO) t.setOnHelp(() -> help(HelpDialog.Kind.ANDROID_AUTO, role));
            if (role == Role.BT_AUDIO) t.setOnHelp(() -> help(HelpDialog.Kind.BLUETOOTH, role));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.leftMargin = i == 0 ? 0 : Ui.u(14);
            main.addView(t, lp);
            tiles.put(role, t);
        }
        info = new InfoPanel(this, () -> control(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS),
                () -> control(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                () -> control(android.view.KeyEvent.KEYCODE_MEDIA_NEXT), this::openMediaApp);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.u(300), LinearLayout.LayoutParams.MATCH_PARENT);
        il.leftMargin = Ui.u(16);
        main.addView(info, il);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ml.topMargin = Ui.u(6);
        home.addView(main, ml);

        dock = new Dock(this, () -> openRole(Role.LANTERN), this::openDrawer, this::openSettings);
        home.addView(dock, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.u(112)));
        root.addView(home);

        drawer = new DrawerView(this, this);
        drawer.setVisibility(drawerOpen ? View.VISIBLE : View.GONE);
        if (drawerOpen) drawer.setApps(AppCatalog.apps());
        root.addView(drawer);
        setContentView(root);
        hideSystemBars();

        onStatusChanged(status);
        refreshMedia();
        top.setLights(lights.on);
        onSpeedChanged(speed);
        if (tlink != null) applyLinkLayout();
    }

    // --- State from the monitors.

    @Override
    public void onAppsChanged() {
        refreshTiles();
        refreshMedia();
        if (drawer.getVisibility() == View.VISIBLE) drawer.setApps(AppCatalog.apps());
    }

    @Override
    public void onStatusChanged(StatusMonitor s) {
        top.setStatus(s);
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(this);
        String time = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", ES).format(now);
        // 12-hour clock: plain "AM"/"PM" (Spanish locale would give "a. m."/"p. m.").
        String ampm = h24 ? null : new SimpleDateFormat("a", Locale.US).format(now);
        String date = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(now);
        date = date.substring(0, 1).toUpperCase(ES) + date.substring(1);
        info.setTime(time, ampm, date);
        refreshTiles();
    }

    @Override
    public void onMediaChanged(MediaMonitor m) {
        refreshMedia();
    }

    @Override
    public void onBluetoothSongChanged(TwBluetooth b) {
        refreshMedia();
    }

    @Override
    public void onSourceChanged(TwCommand c) {
        // The Bluetooth app's own play state is instant; the progress heuristic follows it.
        if (c.btPlaying != null && c.btPlaying != btSong.isLive()) btSong.hint(c.btPlaying);
        refreshMedia();
    }

    @Override
    public void onTLinkChanged(TLinkState s) {
        if (info == null) return;
        refreshMedia();
        applyLinkLayout();
    }

    /**
     * While TLINK5 has CarPlay (or Android Auto) connected, that tile widens over both slots so the
     * screen shows which one is in use; when it disconnects both tiles come back.
     */
    private void applyLinkLayout() {
        Tile cp = tiles.get(Role.CARPLAY);
        Tile aa = tiles.get(Role.ANDROID_AUTO);
        if (cp == null || aa == null) return;
        boolean carplay = tlink.isCarPlay();
        boolean auto = !carplay && tlink.isAndroidAuto();
        setSlot(cp, !auto, carplay ? 2f : 1f, 0);
        setSlot(aa, !carplay, auto ? 2f : 1f, carplay || auto ? 0 : Ui.u(14));
        // The widened tile names the live link; otherwise the usual subtitle comes back.
        refreshTiles();
    }

    private static void setSlot(Tile t, boolean visible, float weight, int leftMargin) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
        lp.weight = weight;
        lp.leftMargin = leftMargin;
        t.setLayoutParams(lp);
        t.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLights(Boolean on) {
        MediaLog.add(this, "luces: " + (on == null ? "sin dato" : on ? "encendidas" : "apagadas"));
        top.setLights(on);
    }

    @Override
    public void onVoltage(Float volts) {
        status.volts = volts;
        top.setStatus(status);
    }

    /**
     * Decides what the playback panel shows. With XTService connected, the head unit itself says
     * which source is active (radio, USB music, Bluetooth or an app such as TLINK5), so an old song
     * can never stand in for the radio. Without it, only sources that are really playing are shown.
     */
    private void refreshMedia() {
        // Seen on the TS10: 1 while the radio plays, 8 for Bluetooth, 0 while an Android app such as
        // TLINK5 (CarPlay/Android Auto) is the source.
        int raw = xt.connected ? xt.source : TwCommand.SOURCE_UNKNOWN;
        boolean appSource = raw == TwCommand.SOURCE_APP;
        int src = raw > 0 ? raw : TwCommand.SOURCE_UNKNOWN;
        boolean sessionPlaying = media.hasSession() && media.playing;
        boolean sessionMeta = media.hasSession() && media.title != null;
        // CarPlay/Android Auto run inside TLINK5, whose session often has no state or metadata.
        Targets.Target link = Targets.resolve(this, Role.CARPLAY);
        boolean linkSession = media.hasSession() && link != null && link.pkg.equals(media.pkg);
        boolean phoneLink = tlink.connected() || lastSource == Role.CARPLAY || lastSource == Role.ANDROID_AUTO
                || (appSource && linkSession);
        // What TLINK5 says it connected wins over which tile was tapped.
        String linkMode = tlink.mode != null ? tlink.mode : lastSource == Role.ANDROID_AUTO ? "Android Auto"
                : lastSource == Role.CARPLAY ? "Apple CarPlay" : "CarPlay / Android Auto";
        String line = null;
        String logLine;
        if (src == TwCommand.SOURCE_RADIO) {
            panel = Panel.RADIO;
            info.show("Radio", "FM · AM", "Fuente actual del estéreo", true, false, false);
            logLine = "radio (XTService)";
        } else if (src == TwCommand.SOURCE_MUSIC) {
            panel = Panel.MUSIC;
            info.show("Música", "USB / almacenamiento", "Fuente actual del estéreo", true, true, musicPlaying);
            logLine = "música USB (XTService)";
        } else if (sessionMeta && (sessionPlaying || phoneLink)) {
            panel = Panel.SESSION;
            String label = appLabel(media.pkg);
            info.show(media.title, media.artist, label != null ? (media.playing ? "En " : "Pausado · ") + label : "",
                    true, true, media.playing ? Boolean.TRUE : phoneLink ? null : Boolean.FALSE);
            line = media.artist != null ? media.title + " · " + media.artist : media.title;
            logLine = "sesión " + media.pkg;
        } else if (phoneLink && src != TwCommand.SOURCE_BT && btSong.title != null
                && (btSong.isLive() || btSong.changedAt > phoneLinkSince)) {
            // The iPhone/Android phone also sends the track over Bluetooth; while TLINK5 has no
            // metadata, a song that started after CarPlay was opened is the CarPlay song.
            panel = Panel.SESSION;
            info.show(btSong.title, btSong.artist,
                    "Sonando en " + linkMode, true, true, null);
            line = btSong.artist != null ? btSong.title + " · " + btSong.artist : btSong.title;
            logLine = "canción del teléfono vía Bluetooth en CarPlay/AA";
        } else if (src == TwCommand.SOURCE_BT || btSong.isLive()
                || (btSong.title != null && !appSource && (lastSource == Role.BT_AUDIO
                        || (btSong.recentlyLive(10 * 60_000L) && lastSource != Role.RADIO && !phoneLink)))) {
            panel = Panel.BLUETOOTH;
            boolean live = btSong.isLive();
            String t = btSong.title != null ? btSong.title : "Bluetooth Audio";
            info.show(t, btSong.title != null ? btSong.artist : "", (live ? "Sonando" : "En pausa") + " por Bluetooth",
                    true, true, live);
            if (btSong.title != null) line = btSong.artist != null ? btSong.title + " · " + btSong.artist : btSong.title;
            logLine = "bluetooth";
        } else if (media.hasSession() && (sessionPlaying || phoneLink)) {
            panel = Panel.SESSION;
            String label = appLabel(media.pkg);
            if (linkSession) {
                // Confirmed on the TS10: TLINK5 shares neither the track nor play/pause state, and
                // the phone does not send it over the head unit's Bluetooth during CarPlay either.
                info.show(linkMode, "El teléfono no comparte el nombre de la canción",
                        label != null ? "Sonando en " + label : "", true, true, tlink.audioPlaying);
            } else {
                info.show(label != null ? "Audio de " + label : "Reproduciendo", "La app no comparte el nombre de la canción",
                        label != null ? "En " + label : "", true, true, media.playing ? Boolean.TRUE : null);
            }
            logLine = "sesión sin metadatos " + media.pkg;
        } else if (tlink.connected() && src == TwCommand.SOURCE_UNKNOWN) {
            panel = Panel.SESSION;
            AppCatalog.App linkApp = link != null ? AppCatalog.find(link.pkg) : null;
            info.show(linkMode, "El teléfono no comparte el nombre de la canción",
                    "Conectado por " + (linkApp != null ? linkApp.label : "la app de enlace"),
                    true, true, tlink.audioPlaying);
            logLine = "tlink conectado sin sesión (" + tlink.rawMode + ")";
        } else if (lastSource == Role.RADIO && !appSource) {
            panel = Panel.RADIO;
            info.show("Radio", "FM · AM", "Última fuente elegida en el inicio", true, false, false);
            logLine = "radio (última elegida)";
        } else {
            panel = Panel.IDLE;
            info.setIdle(media.access);
            logLine = "nada";
        }
        MediaLog.add(this, "panel: " + logLine + " · fuente XT " + xt.source);
        top.setNowPlaying(line);
        refreshTiles();
    }

    private String appLabel(String pkg) {
        if (pkg == null) return null;
        AppCatalog.App app = AppCatalog.find(pkg);
        if (app != null) return app.label;
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException ex) {
            return null;
        }
    }

    /**
     * "Automática" falls back to the GPS speed (as the stock theme does) when the car reports none,
     * which needs the location permission. Asked once; afterwards it is changed in Ajustes.
     */
    private void askLocationOnce() {
        if (!SpeedMonitor.AUTO.equals(prefs.speedMode()) || prefs.locationAsked()) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) return;
        prefs.setLocationAsked();
        requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 7) {
            // Restart so the GPS listener is registered with the new permission.
            speed.stop();
            speed.start();
        }
    }

    @Override
    public void onSpeedChanged(SpeedMonitor s) {
        info.setSpeed(s.display(), s.unitLabel(), s.caption);
    }

    private void refreshTiles() {
        if (tiles.isEmpty()) return;
        for (Role role : MAIN) {
            Tile tile = tiles.get(role);
            if (!AppCatalog.loaded()) {
                tile.setSub("", true);
                continue;
            }
            Targets.Target t = Targets.resolve(this, role);
            if (t == null) {
                tile.setSub("No encontrada · toca para elegir", false);
                continue;
            }
            String sub = t.label;
            boolean ownsSession = media.hasSession() && t.pkg.equals(media.pkg) && media.title != null;
            if (role == Role.RADIO) {
                sub = ownsSession ? media.title : "FM · AM";
            } else if (role == Role.BT_AUDIO) {
                if (ownsSession) sub = media.title;
                else if (btSong.isLive() && TwBluetooth.PACKAGE.equals(t.pkg)) sub = btSong.title;
                else if (status.phoneConnected) sub = status.phoneName != null ? status.phoneName : "Teléfono conectado";
            }
            if ((role == Role.CARPLAY && tlink.isCarPlay()) || (role == Role.ANDROID_AUTO && tlink.isAndroidAuto())) {
                String type = tlink.linkType();
                sub = "Conectado" + (type != null ? " · " + type : "");
            }
            tile.setSub(sub, true);
        }
    }

    // --- Actions.

    private void openRole(Role role) {
        if (!AppCatalog.loaded()) return;
        if (role == Role.RADIO || role == Role.BT_AUDIO || role == Role.CARPLAY
                || role == Role.ANDROID_AUTO || role == Role.MUSIC) {
            lastSource = role;
            if (role == Role.CARPLAY || role == Role.ANDROID_AUTO) phoneLinkSince = android.os.SystemClock.elapsedRealtime();
            MediaLog.add(this, "abrir: " + role.title);
            refreshMedia();
        }
        Targets.Target t = Targets.resolve(this, role);
        if (t != null) {
            Targets.launch(this, t);
            return;
        }
        Pickers.dialog(this)
                .setTitle(role.title)
                .setMessage("No se encontró una app instalada para «" + role.title + "». ¿Quieres elegir cuál usar?")
                .setPositiveButton("Elegir app", (d, w) -> Pickers.pickApp(this, role.title, app -> {
                    if (app == null) return;
                    prefs.setTarget(role, app.pkg, null);
                    refreshTiles();
                    Targets.start(this, app.launchIntent(), app.label);
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void help(HelpDialog.Kind kind, Role role) {
        Targets.Target t = Targets.resolve(this, role);
        if (t == null) HelpDialog.show(this, kind, null, null);
        else HelpDialog.show(this, kind, "Abrir " + t.label, () -> Targets.launch(this, t));
    }

    private void openMediaApp() {
        if (panel == Panel.RADIO) {
            openRole(Role.RADIO);
            return;
        }
        if (panel == Panel.MUSIC) {
            openRole(Role.MUSIC);
            return;
        }
        String pkg = panel == Panel.BLUETOOTH ? TwBluetooth.PACKAGE : media.pkg;
        if (pkg == null) return;
        AppCatalog.App app = AppCatalog.find(pkg);
        Intent i = app != null ? app.launchIntent() : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) Targets.start(this, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), app != null ? app.label : pkg);
    }

    /**
     * Playback buttons. They go through XTService, the same path as the steering-wheel buttons,
     * which delivers them to whichever source is active (radio station, Bluetooth, USB music or the
     * app in front such as TLINK5). Without XTService an Android session is controlled directly.
     */
    private void control(int keyCode) {
        boolean prev = keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        boolean next = keyCode == android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
        MediaLog.add(this, "botón: " + (prev ? "anterior" : next ? "siguiente" : "play/pausa") + " · panel " + panel);
        if (!xt.connected) {
            if (panel != Panel.SESSION) return;
            if (prev) media.previous();
            else if (next) media.next();
            else media.playPause();
            return;
        }
        if (prev) {
            xt.previous();
        } else if (next) {
            xt.next();
        } else if (panel == Panel.BLUETOOTH) {
            boolean playing = btSong.isLive();
            if (playing) xt.pause();
            else xt.play();
            btSong.assume(!playing);
            refreshMedia();
        } else if (panel == Panel.MUSIC) {
            if (musicPlaying) xt.pause();
            else xt.play();
            musicPlaying = !musicPlaying;
            refreshMedia();
        } else if (panel == Panel.SESSION) {
            // For app sources XTService sends the play/pause toggle key (85).
            xt.play();
            if (tlink.audioPlaying != null) {
                // Flip at once; TLINK5's MAIN_AUDIO_START/STOP confirms (or corrects) it.
                tlink.audioPlaying = !tlink.audioPlaying;
                refreshMedia();
            }
        }
    }

    private void openWifiSettings() {
        Targets.start(this, new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Wi-Fi");
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openDrawer() {
        drawer.setApps(AppCatalog.apps());
        drawer.scrollToTop();
        drawer.setAlpha(0f);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).setDuration(140).start();
    }

    private void closeDrawer() {
        drawer.animate().alpha(0f).setDuration(120).withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    @Override
    public void onAppClick(AppCatalog.App app) {
        Targets.start(this, app.launchIntent(), app.label);
    }

    @Override
    public void onAppLongClick(AppCatalog.App app) {
        Targets.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Información de la app");
    }

    @Override
    public void onClose() {
        closeDrawer();
    }
}
