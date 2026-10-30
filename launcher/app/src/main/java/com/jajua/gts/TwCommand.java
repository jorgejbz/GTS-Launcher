package com.jajua.gts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client of the head unit's command bus, XTService (com.tw.service.xt/.CommandService, version
 * 2022.02.17 on this TS10). Its AIDL is not obfuscated; codes below are its own TRANSACTION_ values.
 * This is the path the steering-wheel and voice commands use:
 *  - mediaPlay / mediaPause / mediaNext / mediaPre go to the current source: radio (1), USB music
 *    (3), Bluetooth (8) or video (9) through that app's callback, any other source as an Android
 *    media key (85 play/pause, 127 pause, 87 next, 88 previous), which reaches TLINK5, YouTube…
 *  - Bundle {project=system, action=source_request} makes it report the current source, and it
 *    also reports every change, as Bundle {action=Source_recieve, SourceValue=int} to registered
 *    ITWCommandCallbackAidl callbacks.
 * Only those calls are used. Nothing that writes raw data to the MCU is ever sent.
 */
final class TwCommand {
    interface Listener {
        void onSourceChanged(TwCommand c);
    }

    static final int SOURCE_UNKNOWN = -1;
    /** Reported while an Android app (TLINK5, YouTube…) is the source; also XTService's initial value. */
    static final int SOURCE_APP = 0;
    static final int SOURCE_RADIO = 1;
    static final int SOURCE_MUSIC = 3;
    static final int SOURCE_BT = 8;
    static final int SOURCE_VIDEO = 9;

    private static final String PACKAGE = "com.tw.service.xt";
    private static final String SERVICE = "com.tw.service.xt.CommandService";
    private static final String DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl";
    private static final String CALLBACK_DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandCallbackAidl";
    private static final int REGISTER_CALLBACK = 1;
    private static final int UNREGISTER_CALLBACK = 2;
    private static final int MEDIA_NEXT = 55;
    private static final int MEDIA_PRE = 56;
    private static final int MEDIA_PLAY = 57;
    private static final int MEDIA_PAUSE = 58;
    private static final int EXTENDED_INTERFACE = 67;
    private static final int CALLBACK_EXTENDED = 8;

    /** Current audio source as XTService reports it. */
    int source = SOURCE_UNKNOWN;
    /**
     * Bluetooth play state as com.tw.bt reports it to XTService (Bundle action=com.tw.bt.av.play,
     * playState=boolean), forwarded the moment it changes. Null until the first report.
     */
    Boolean btPlaying;
    /** False until the service is bound, or when the firmware lacks it. */
    boolean connected;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private IBinder remote;
    private boolean bound;

    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK_DESCRIPTOR);
            if (code == CALLBACK_EXTENDED) {
                Bundle b = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                String action = b != null ? b.getString("action") : null;
                if ("Source_recieve".equals(action)) {
                    int value = b.getInt("SourceValue", SOURCE_UNKNOWN);
                    main.post(() -> {
                        if (value == source) return;
                        source = value;
                        listener.onSourceChanged(TwCommand.this);
                    });
                } else if ("com.tw.bt.av.play".equals(action)) {
                    boolean playing = b.getBoolean("playState");
                    main.post(() -> {
                        MediaLog.add(context, "xt: bluetooth " + (playing ? "reproduciendo" : "en pausa"));
                        if (Boolean.valueOf(playing).equals(btPlaying)) return;
                        btPlaying = playing;
                        listener.onSourceChanged(TwCommand.this);
                    });
                } else if (action != null) {
                    main.post(() -> MediaLog.add(context, "xt: aviso " + action + " " + b.keySet()));
                }
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = service;
            connected = true;
            io.execute(() -> {
                call(REGISTER_CALLBACK, p -> p.writeStrongBinder(callback));
                Bundle req = new Bundle();
                req.putString("project", "system");
                req.putString("action", "source_request");
                call(EXTENDED_INTERFACE, p -> {
                    p.writeInt(1);
                    req.writeToParcel(p, 0);
                });
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            connected = false;
            source = SOURCE_UNKNOWN;
            listener.onSourceChanged(TwCommand.this);
        }
    };

    TwCommand(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        callback.attachInterface(new IInterface() {
            @Override
            public IBinder asBinder() {
                return callback;
            }
        }, CALLBACK_DESCRIPTOR);
    }

    void start() {
        if (bound) return;
        try {
            bound = context.bindService(new Intent().setClassName(PACKAGE, SERVICE), connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ex) {
            bound = false;
        }
    }

    void stop() {
        if (!bound) return;
        bound = false;
        IBinder r = remote;
        if (r != null) io.execute(() -> transact(r, UNREGISTER_CALLBACK, p -> p.writeStrongBinder(callback)));
        try {
            context.unbindService(connection);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        remote = null;
        connected = false;
    }

    void next() {
        command(MEDIA_NEXT);
    }

    void previous() {
        command(MEDIA_PRE);
    }

    /** For radio/USB/BT this plays; for any other source XTService sends the play/pause toggle key. */
    void play() {
        command(MEDIA_PLAY);
    }

    void pause() {
        command(MEDIA_PAUSE);
    }

    private void command(int code) {
        io.execute(() -> call(code, p -> {}));
    }

    private interface Args {
        void write(Parcel p);
    }

    private void call(int code, Args args) {
        IBinder r = remote;
        if (r != null) transact(r, code, args);
    }

    private static void transact(IBinder r, int code, Args args) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            args.write(data);
            r.transact(code, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
            // Service went away or rejected the call; the UI keeps working without it.
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
