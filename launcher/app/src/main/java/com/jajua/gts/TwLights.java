package com.jajua.gts;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.reflect.Method;

/**
 * Whether the car's lights are on, from the head unit's MCU illumination input (the signal that
 * dims the screen at night). Read the way XTService does it (com.tw.service.xt CommandService,
 * firmware TS10.2.2): android.tw.john.TWUtil open(short[]{516}), request with write(516, 255);
 * reply what=516: arg1 == 1 means night mode (lights on), arg2 holds the day/night brightness.
 * XTService turns the same message into net.easyconn.navi.night / .daytime.
 * Only this read-only request is ever written; any failure leaves the indicator hidden.
 */
final class TwLights {
    interface Listener {
        void onLights(Boolean on);
    }

    private static final int MSG_ILLUMINATION = 516;
    private static final String TAG = "gts-lights";

    /** Last state, or null when the MCU has not answered. */
    Boolean on;
    /** Why it failed, for the diagnostic (null when it works). */
    String error;

    private final Listener listener;
    private Object tw;
    private Class<?> cls;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            if (m.what != MSG_ILLUMINATION) return;
            Boolean next = m.arg1 == 1;
            if (next.equals(on)) return;
            on = next;
            listener.onLights(on);
        }
    };

    TwLights(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (tw != null) return;
        try {
            cls = Class.forName("android.tw.john.TWUtil");
            Object t = cls.getConstructor().newInstance();
            Object r = cls.getMethod("open", short[].class).invoke(t, (Object) new short[] {MSG_ILLUMINATION});
            if (r instanceof Integer && (Integer) r != 0) {
                error = "TWUtil.open devolvió " + r;
                return;
            }
            cls.getMethod("start").invoke(t);
            cls.getMethod("addHandler", String.class, Handler.class).invoke(t, TAG, handler);
            tw = t;
            request();
            error = null;
        } catch (ClassNotFoundException ex) {
            error = "TWUtil no existe";
        } catch (Throwable ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            error = cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
        }
    }

    /** Asks the MCU for the current illumination state (the same request XTService sends at start). */
    void request() {
        if (tw == null) return;
        try {
            cls.getMethod("write", int.class, int.class).invoke(tw, MSG_ILLUMINATION, 255);
        } catch (Throwable ignored) {
            // Keep whatever was last reported.
        }
    }

    void stop() {
        if (tw == null) return;
        Object t = tw;
        tw = null;
        on = null;
        for (String step : new String[] {"removeHandler", "stop", "close"}) {
            try {
                Method m = step.equals("removeHandler") ? cls.getMethod(step, String.class) : cls.getMethod(step);
                if (step.equals("removeHandler")) m.invoke(t, TAG);
                else m.invoke(t);
            } catch (Throwable ignored) {
                // Best effort cleanup.
            }
        }
    }
}
