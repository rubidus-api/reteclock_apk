package com.reteclock;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import com.reteclock.core.WebLifetime;

import java.util.List;

/**
 * Keeps the web administrator answering when none of the app's screens is showing (R141).
 *
 * <p>Only for the person who chose it: the server's lifetime is {@code always}. The service is one
 * more host of {@link WebAdmin}, as a screen is, and that is all it does for the server; the rest
 * is what Android asks of something that goes on in the background — a notification that says so,
 * with the address and a way to stop it.
 *
 * <p>It is started only from a screen of the app and is not sticky: ended with the app, or by a
 * restart of the phone, it stays ended until ReteClock is opened again (owner, 2026-10-08).
 * Nothing starts at boot.
 *
 * <p>With the screen off the device is kept awake for it — a wake lock and a Wi-Fi lock — only on
 * the charger. On battery nothing is held, and the notification says the server may stop answering.
 */
public class WebAdminService extends Service {

    static final String ACTION_STOP = "com.reteclock.action.WEB_STOP";
    private static final String CHANNEL = "web_admin";
    private static final int ID = 4200;

    private final Handler handler = new Handler();
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private boolean plugged, foreground, hosting, watching;
    private String shown;

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            plugged = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
            awake();
            refresh();
        }
    };

    /** The address can change under the service — another network, a new port. */
    private final Runnable look = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 5000);
        }
    };

    /** From a screen of the app only: Android does not let a background app start this. */
    static void start(Context context) {
        Intent intent = new Intent(context, WebAdminService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                WakeApi26.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // The platform refused; the server goes on as long as a screen of the app shows.
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, WebAdminService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reteclock:web");
            wakeLock.setReferenceCounted(false);
        }
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createWifiLock(Build.VERSION.SDK_INT >= 12
                        ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL,
                        "reteclock:web");
                wifiLock.setReferenceCounted(false);
            }
        } catch (RuntimeException e) {
            // A device without Wi-Fi, or one that will not give the lock: the wake lock remains.
        }
        if (Build.VERSION.SDK_INT >= 26) {
            WakeApi26.createQuietChannel(this, CHANNEL, getString(R.string.web_bg_channel),
                    getString(R.string.web_bg_channel_note));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            // As the Stop button on the page: the server is switched off, not only sent home.
            WebAdmin.account(this).edit().putBoolean("enabled", false).commit();
            WebAdmin.reconcile();
            finish();
            return START_NOT_STICKY;
        }
        // Foreground first, whatever follows: Android 8 and up stop a service that was started to
        // be foreground and did not become so within seconds.
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        plugged = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        shown = text();
        startForeground(ID, notification());
        foreground = true;
        if (!WebLifetime.background(WebAdmin.lifetime(this), WebAdmin.enabled(this))) {
            // Started for a choice that has since been unmade.
            finish();
            return START_NOT_STICKY;
        }
        if (!watching) {
            watching = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(power, filter);
            handler.postDelayed(look, 5000);
        }
        awake();
        if (!hosting) {
            hosting = true;
            WebAdmin.enter(this, this);
        }
        return START_NOT_STICKY;
    }

    /** Swiped out of the recent apps: the app is ended, and the server with it. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        finish();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void finish() {
        release();
        if (foreground) {
            foreground = false;
            stopForeground(true);
        }
        stopSelf();
    }

    private void release() {
        handler.removeCallbacks(look);
        if (watching) {
            watching = false;
            try {
                unregisterReceiver(power);
            } catch (RuntimeException e) {
                // Not registered after all.
            }
        }
        plugged = false;
        awake();
        if (hosting) {
            hosting = false;
            WebAdmin.leave(this);
        }
    }

    /** Holds the device awake for the server only on the charger. */
    private void awake() {
        boolean hold = watching
                && WebLifetime.staysAwake(WebAdmin.lifetime(this), WebAdmin.enabled(this), plugged);
        try {
            if (wakeLock != null && hold != wakeLock.isHeld()) {
                if (hold) {
                    wakeLock.acquire();
                } else {
                    wakeLock.release();
                }
            }
            if (wifiLock != null && hold != wifiLock.isHeld()) {
                if (hold) {
                    wifiLock.acquire();
                } else {
                    wifiLock.release();
                }
            }
        } catch (RuntimeException e) {
            // A lock the platform would not give: the server answers while the device is awake.
        }
    }

    // ---- the notification -----------------------------------------------------------------------

    /** The first address, IPv4 before IPv6, or that there is none; and what the screen off means. */
    private String text() {
        List<String[]> urls = WebAdmin.urls();
        return (urls.isEmpty() ? getString(R.string.web_bg_no_address) : urls.get(0)[1]) + "\n"
                + getString(plugged ? R.string.web_bg_plugged : R.string.web_bg_battery);
    }

    private void refresh() {
        if (!foreground) {
            return;
        }
        String now = text();
        if (now.equals(shown)) {
            return;
        }
        shown = now;
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(ID, notification());
            }
        } catch (RuntimeException e) {
            // A notification that could not be updated still says the server is running.
        }
    }

    @SuppressWarnings("deprecation")
    private Notification notification() {
        int cut = shown.indexOf('\n');
        String title = shown.substring(0, cut), text = shown.substring(cut + 1);
        PendingIntent open = PendingIntent.getActivity(this, 40,
                new Intent(this, WebSettingsActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT < 11) {
            Notification old = new Notification(R.drawable.ic_launcher_monochrome, title,
                    System.currentTimeMillis());
            old.setLatestEventInfo(this, title, text, open);
            old.flags |= Notification.FLAG_ONGOING_EVENT;
            return old;
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? WakeApi26.builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open);
        if (Build.VERSION.SDK_INT >= 16) {
            Intent stop = new Intent(this, WebAdminService.class);
            stop.setAction(ACTION_STOP);
            builder.addAction(0, getString(R.string.web_bg_stop), PendingIntent.getService(this, 41,
                    stop, PendingIntent.FLAG_UPDATE_CURRENT));
            return builder.build();
        }
        return builder.getNotification();
    }
}
