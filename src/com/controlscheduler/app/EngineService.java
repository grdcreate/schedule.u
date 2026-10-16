package com.controlscheduler.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Two jobs in one service:
 *   1. the always-available "internet kill switch" - a local VPN that drops every
 *      packet, so the phone is really offline without root;
 *   2. the armed background engine, so exact alarms and scheduled runs fire reliably.
 */
public class EngineService extends VpnService {

    public static final String ACTION_ARM = "cs.ARM";
    public static final String ACTION_DISARM = "cs.DISARM";
    public static final String ACTION_BLOCK = "cs.BLOCK";
    public static final String ACTION_UNBLOCK = "cs.UNBLOCK";
    /** run one saved schedule (used by AlarmReceiver so long step stacks can't ANR a receiver) */
    public static final String ACTION_RUN = "cs.RUN";
    public static final String EXTRA_ID = "schedule_id";

    private static final String CHANNEL = "engine";
    private static final int NOTIF_ID = 42;

    private static EngineService instance;

    private ParcelFileDescriptor tun;
    private Thread pump;
    private boolean armed = true;

    public static boolean isTunnelUp() {
        return instance != null && instance.tun != null;
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        armed = Prefs.armed(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null || intent.getAction() == null ? ACTION_ARM : intent.getAction();
        armed = Prefs.armed(this);
        switch (action) {
            case ACTION_DISARM:
                ensureForeground();          // required before stopping a startForegroundService()
                stopTunnel();
                Prefs.setInternetBlocked(this, false);
                stopForegroundSafe();
                stopSelf();
                return START_NOT_STICKY;
            case ACTION_BLOCK:
                Prefs.setInternetBlocked(this, true);
                break;
            case ACTION_UNBLOCK:
                Prefs.setInternetBlocked(this, false);
                stopTunnel();
                if (!Prefs.armed(this)) {
                    ensureForeground();
                    stopForegroundSafe();
                    stopSelf();
                    return START_NOT_STICKY;
                }
                break;
            case ACTION_ARM:
                if (Prefs.internetBlocked(this)) { /* keep tunnel */ }
                if (!armed && !Prefs.internetBlocked(this)) {
                    stopForegroundSafe();
                    stopSelf();
                    return START_NOT_STICKY;
                }
                break;
        }
        ensureForeground();
        if (ACTION_ARM.equals(action) && Prefs.internetBlocked(this) && !Prefs.vpnConsent(this)) {
            Runner.notifyConsent(this);
        }
        if (ACTION_RUN.equals(action)) {
            final long id = intent.getLongExtra(EXTRA_ID, -1);
            if (id > 0) {
                new Thread(new Runnable() {
                    @Override public void run() { executeSchedule(EngineService.this, id); }
                }, "schedule-run").start();
            }
        }
        if (Prefs.internetBlocked(this)) startTunnel();
        else stopTunnel();
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        // the user switched our VPN off in system settings
        Prefs.setInternetBlocked(this, false);
        stopTunnel();
        updateNotification();
        if (!Prefs.armed(this)) {
            stopForegroundSafe();
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        instance = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------ tunnel

    private void startTunnel() {
        if (tun != null) return;
        try {
            Builder b = new Builder();
            b.setSession("schedule.u \u2014 internet off");
            b.setMtu(1500);
            b.addAddress("10.111.222.1", 32);
            b.addAddress("fd00:1:fd00:1::1", 128);
            b.addRoute("0.0.0.0", 0);
            b.addRoute("::", 0);
            b.setBlocking(true);
            try { b.addDisallowedApplication(getPackageName()); } catch (Throwable ignored) { }
            tun = b.establish();
            if (tun == null) { Prefs.setInternetBlocked(this, false); return; }
            startPump();
        } catch (Throwable t) {
            tun = null;
            Prefs.setInternetBlocked(this, false);
        }
    }

    /** Reads and throws away everything, so all traffic is dead-ended. */
    private void startPump() {
        if (tun == null) return;
        final InputStream in = new FileInputStream(tun.getFileDescriptor());
        pump = new Thread(new Runnable() {
            @Override public void run() {
                byte[] buf = new byte[32768];
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        int n = in.read(buf);
                        if (n < 0) break;
                    }
                } catch (Throwable ignored) {
                } finally {
                    try { in.close(); } catch (Throwable ignored) { }
                }
            }
        }, "kill-switch");
        pump.setDaemon(true);
        pump.start();
    }

    private void stopTunnel() {
        if (pump != null) { pump.interrupt(); pump = null; }
        if (tun != null) {
            try { tun.close(); } catch (Throwable ignored) { }
            tun = null;
        }
    }

    // ------------------------------------------------------------ notification

    private void ensureForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Automation engine", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("Shows when schedules are armed or internet is blocked");
            nm.createNotificationChannel(ch);
        }
        try { startForeground(NOTIF_ID, buildNotification()); }
        catch (Throwable ignored) { }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification());
        } catch (Throwable ignored) { }
    }

    private void stopForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Throwable ignored) { }
    }

    private Notification buildNotification() {
        boolean blocked = Prefs.internetBlocked(this);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Automation engine", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_tile)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setShowWhen(false);

        if (blocked) {
            Intent fix = new Intent(this, EngineService.class).setAction(ACTION_UNBLOCK);
            PendingIntent fixPi = PendingIntent.getService(this, 2, fix, piFlags);
            b.setContentTitle("Internet blocked")
                    .setContentText(armed ? "schedules armed \u2022 tap Restore to go back online"
                                          : "tap Restore internet to go back online")
                    .addAction(new Notification.Action.Builder(null, "Restore internet", fixPi).build());
        } else {
            b.setContentTitle("schedule.u \u00b7 armed")
                    .setContentText(nextRunText());
        }
        return b.build();
    }

    private String nextRunText() {
        try {
            java.util.List<Schedule> list = Db.get(this).enabled();
            if (list.isEmpty()) return "No schedules yet";
            Schedule soonest = list.get(0);
            long best = Long.MAX_VALUE;
            for (Schedule s : list) {
                long t = Scheduler.nextTrigger(s);
                if (t < best) { best = t; soonest = s; }
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(best);
            String when = new java.text.SimpleDateFormat("EEE ", java.util.Locale.getDefault())
                    .format(new java.util.Date(best))
                    + Ui.time(this, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
            String label = soonest.name == null || soonest.name.isEmpty()
                    ? Ui.time(this, soonest.hour, soonest.minute) : soonest.name;
            return "Next: " + label + " \u2022 " + when;
        } catch (Throwable t) {
            return "Schedules are armed";
        }
    }

    // ------------------------------------------------------------ static API

    /** @return true when the system accepted the start request. */
    public static boolean start(Context ctx, String action, long id) {
        Intent i = new Intent(ctx, EngineService.class).setAction(action).putExtra(EXTRA_ID, id);
        return startIntent(ctx, i);
    }

    /** Runs a saved schedule outside the broadcast receiver (avoids receiver ANRs). */
    public static void runSchedule(Context ctx, long id) {
        start(ctx, ACTION_RUN, id);
    }

    /** Keeps the engine alive only when the user wants it and something needs it. */
    public static void ensureRunning(Context ctx) {
        if (Prefs.serviceWanted(ctx) && (Prefs.armed(ctx) || Prefs.internetBlocked(ctx))) {
            start(ctx, ACTION_ARM);
        }
    }

    /** Executes a saved schedule and re-arms it (one-shots switch themselves off). */
    public static void executeSchedule(Context ctx, long id) {
        android.os.PowerManager.WakeLock lock = null;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                lock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "schedule.u:run");
                lock.acquire(120_000L);
            }
        } catch (Throwable ignored) { }

        try {
            Db db = Db.get(ctx);
            Schedule s = db.byId(id);
            if (s == null) {
                // a run must never vanish without a trace
                db.log("Unknown schedule", "nothing stored for id " + id + " - was it deleted?", false);
                Prefs.setLastRun(ctx, System.currentTimeMillis(),
                        "Unknown schedule - no schedule with id " + id, false);
                return;
            }
            if (!s.enabled) {
                db.log(s.name, "skipped - this schedule is switched off", false);
                Prefs.setLastRun(ctx, System.currentTimeMillis(), s.name + " - skipped (switched off)", false);
                return;
            }
            if (!Prefs.armed(ctx)) {
                db.log(s.name, "skipped - the engine is paused in the app", false);
                Prefs.setLastRun(ctx, System.currentTimeMillis(), s.name + " - skipped (engine paused)", false);
                return;
            }
            Runner.run(ctx, s, true);
            Prefs.markFired(ctx, id, System.currentTimeMillis());
            if (s.repeat == 0) db.setEnabled(id, false);
            else Scheduler.schedule(ctx, s);
        } catch (Throwable t) {
            try { Db.get(ctx).log("Run failed", String.valueOf(t), false); } catch (Throwable ignored) { }
            try { Prefs.setLastRun(ctx, System.currentTimeMillis(), "Run failed - " + t, false); } catch (Throwable ignored) { }
        } finally {
            try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) { }
        }
    }

    public static boolean start(Context ctx, String action) {
        return startIntent(ctx, new Intent(ctx, EngineService.class).setAction(action));
    }

    private static boolean startIntent(Context ctx, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            return true;
        } catch (Throwable t) {
            try {
                ctx.startService(i);
                return true;
            } catch (Throwable t2) {
                return false;
            }
        }
    }
}
