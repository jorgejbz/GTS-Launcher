package com.jajua.gts;

import android.service.notification.NotificationListenerService;

/**
 * Android only hands out the list of active media sessions (what is playing, play/pause, next)
 * to an enabled notification listener. Notifications are ignored apart from the diagnostic log below.
 */
public final class MediaAccessService extends NotificationListenerService {
    /**
     * TLINK5's media session carries no track name; this records whether it shows one in a
     * notification instead (diagnostic only, logged for TLINK5 and the head unit's own apps).
     */
    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!pkg.equals("com.zjinnova.zlink") && !pkg.startsWith("com.tw.")) return;
        android.os.Bundle x = sbn.getNotification().extras;
        CharSequence title = x.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text = x.getCharSequence(android.app.Notification.EXTRA_TEXT);
        MediaLog.add(this, "notificación: " + pkg + " · «" + title + "» · «" + text + "»");
    }
}
