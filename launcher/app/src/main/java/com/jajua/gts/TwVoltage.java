package com.jajua.gts;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.reflect.Method;

/**
 * Car battery voltage from the head unit's MCU, read exactly the way the TS10's own status bar does
 * it (com.android.systemui StatusBar$2.handleMessage / TWSystemUI, firmware TS10.2.2_20241022):
 *   android.tw.john.TWUtil: open(short[]{1292}), start(), addHandler(tag, handler),
 *   request with write(1292, 255); reply what=1292, arg1=0, obj=byte[]:
 *   if bit 7 of b[0] is clear, volts = ((b[0] & 0x7F) * 256 + (b[1] & 0xFF)) * 0.01.
 * Only this read-only request is ever written. TWUtil is a vendor framework class, so it is
 * reached by reflection and any failure simply leaves the voltage hidden.
 */
final class TwVoltage {
    interface Listener {
        void onVoltage(Float volts);
    }

    private static final int MSG_VOLTAGE = 1292;
    private static final String TAG = "gts-voltage";

    /** Last reading, or null when there is none. */
    Float volts;
    /** Why it failed, for the diagnostic (null when it works). */
    String error;

    private final Listener listener;
    private Object tw;
    private Class<?> cls;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            if (m.what != MSG_VOLTAGE || m.arg1 != 0 || !(m.obj instanceof byte[])) return;
            byte[] b = (byte[]) m.obj;
            if (b.length < 2 || (b[0] & 0x80) != 0) return;
            float v = (((b[0] & 0x7F) << 8) | (b[1] & 0xFF)) * 0.01f;
            // Anything outside a 12 V car system's range is a bad frame, not a reading.
            Float next = v >= 6f && v <= 18f ? v : null;
            if (next == null && volts == null) return;
            if (next != null && volts != null && Math.abs(next - volts) < 0.05f) return;
            volts = next;
            listener.onVoltage(volts);
        }
    };

    TwVoltage(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (tw != null) return;
        try {
            cls = Class.forName("android.tw.john.TWUtil");
            Object t = cls.getConstructor().newInstance();
            Object r = cls.getMethod("open", short[].class).invoke(t, (Object) new short[] {MSG_VOLTAGE});
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

    /** Asks the MCU for the current voltage (the same request the status bar sends). */
    void request() {
        if (tw == null) return;
        try {
            cls.getMethod("write", int.class, int.class).invoke(tw, MSG_VOLTAGE, 255);
        } catch (Throwable ignored) {
            // Keep whatever was last reported.
        }
    }

    void stop() {
        if (tw == null) return;
        Object t = tw;
        tw = null;
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
