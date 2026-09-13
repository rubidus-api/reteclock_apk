package com.reteclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

/**
 * The notification channel and the foreground service, on Android 8.0 and up (RFC-0012).
 *
 * <p>The channel is created only when *Alarms (experimental)* is switched on (F3): on Android 13 and
 * newer it is what lets the platform ask its notification question, and nobody who does not use the
 * experiment should ever see that question. Touched only behind {@code SDK_INT >= 26}.
 */
final class WakeApi26 {

    private WakeApi26() {
    }

    static void createChannel(Context context, String id, CharSequence name, String description) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel =
                new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        // The ring is the service's own sound on the alarm stream; the channel must not add a
        // second one of its own, or a person hears the notification tone over their alarm.
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    static Notification.Builder builder(Context context, String channelId) {
        return new Notification.Builder(context, channelId);
    }

    static void startForegroundService(Context context, Intent intent) {
        context.startForegroundService(intent);
    }
}
