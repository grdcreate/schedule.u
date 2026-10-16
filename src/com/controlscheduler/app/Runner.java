package com.controlscheduler.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every step of a schedule and reports what really happened.
 *
 * Method chosen per step (unless the schedule pins one):
 *   radio toggles  -> root, else Shizuku, else kill switch (internet) / settings screen
 *   kill switch    -> built-in VPN, no root needed
 *   direct actions -> app itself (flashlight, DND, ringer, volume, brightness, rotate)
 */
public class Runner {

    private static final String CHANNEL = "runs";

    /** Executes the schedule. When notifyResult is false it stays quiet (used by "Test now"). */
    public static String run(Context ctx, Schedule s, boolean notifyResult) {
        List<String> summary = new ArrayList<>();
        boolean ok = true;
        for (Step step : s.steps) {
            String one = runStep(ctx, s, step);
            summary.add(one);
            if (looksFailed(one)) ok = false;
        }
        String text = join(summary);
        if (notifyResult) notify(ctx, s.name.isEmpty() ? "Schedule" : s.name, text, null);
        if (s.id > 0) {
            try { Db.get(ctx).log(s.name, text, ok); } catch (Throwable ignored) { }
        }
        try {
            String title = s.name == null || s.name.isEmpty() ? "Schedule" : s.name;
            Prefs.setLastRun(ctx, System.currentTimeMillis(), title + " \u2014 " + shortText(text), ok);
        } catch (Throwable ignored) { }
        return text;
    }

    /** A step reported a problem if it says so in plain words (drives the history dot + LAST RUN). */
    public static boolean looksFailed(String one) {
        String t = one.toLowerCase();
        return t.contains("failed") || t.contains("needs") || t.contains("grant")
                || t.contains("skipped") || t.contains("unknown schedule") || t.contains("error")
                || t.contains("blocked") || t.contains("no root/shizuku") || t.contains("not available");
    }

    /** Runs one "Test now" action without saving anything. */
    public static String preview(Context ctx, Action action, int level, String command) {
        Schedule s = new Schedule();
        s.name = "Test";
        s.command = command;
        s.steps = new ArrayList<>();
        s.steps.add(new Step(action, level));
        String text = runStep(ctx, s, s.steps.get(0));
        notify(ctx, "Test: " + action.label, text, null);
        return text;
    }

    // ------------------------------------------------------------------ one step

    private static String runStep(Context ctx, Schedule s, Step step) {
        Action a = step.action;
        try {
            switch (a.kind) {
                case DIRECT:
                    return direct(ctx, a, step.level);

                case VPN:
                    if (a == Action.INTERNET_OFF) {
                        return killSwitchOn(ctx);
                    } else {
                        Prefs.setInternetBlocked(ctx, false);
                        EngineService.start(ctx, EngineService.ACTION_UNBLOCK);
                        return "Internet restored";
                    }

                case PRIV: {
                    int method = s.method;
                    if (method == 1 || method == 2 || (method == 0 && Shell.anyPrivilege())) {
                        Shell.Result r = Shell.runAction(ctx, a);
                        if (r.ok) return a.label + " \u2713 (" + r.via + ")";
                        if (method == 1 || method == 2) {
                            return a.label + " failed via " + (method == 1 ? "root" : "Shizuku")
                                    + (r.output.isEmpty() ? "" : " \u2014 " + shortText(r.output));
                        }
                    }
                    if (method == 4) return fallbackToScreen(ctx, a);
                    return fallback(ctx, a);
                }

                case SCREEN:
                    openScreen(ctx, a, false);
                    return "Opened " + a.label.replace("Open ", "");

                case CMD: {
                    String cmd = (s.command == null || s.command.trim().isEmpty()) ? "echo control-scheduler"
                            : s.command.trim();
                    if (!Shell.anyPrivilege()) return "Run command needs root or Shizuku";
                    Shell.Result r = Shell.exec(ctx, cmd);
                    return r.ok ? "Command ok (" + r.via + ")" : "Command failed (" + r.via + ")";
                }

                default:
                    return "Nothing to do";
            }
        } catch (Throwable t) {
            return a.label + " error";
        }
    }

    /** No root and no Shizuku: use the kill switch where it makes sense, otherwise the screen. */
    private static String fallback(Context ctx, Action a) {
        switch (a) {
            case DATA_OFF:
            case AIRPLANE_ON:
                return "No root/Shizuku \u2014 " + killSwitchOn(ctx);
            case DATA_ON:
            case AIRPLANE_OFF:
                Prefs.setInternetBlocked(ctx, false);
                EngineService.start(ctx, EngineService.ACTION_UNBLOCK);
                return "Internet restored";
            default:
                return fallbackToScreen(ctx, a);
        }
    }

    private static String fallbackToScreen(Context ctx, Action a) {
        Action screen = a.fallbackScreen();
        if (screen == null) return a.label + ": needs root or Shizuku";
        openScreen(ctx, screen, false);
        return a.label + ": needs root or Shizuku \u2014 opened the settings screen instead";
    }

    // ------------------------------------------------------------------ direct actions

    private static String direct(Context ctx, Action a, int level) throws Exception {
        switch (a) {
            case FLASHLIGHT_ON: setTorch(ctx, true); return "Flashlight on";
            case FLASHLIGHT_OFF: setTorch(ctx, false); return "Flashlight off";

            case DND_ON: return setDnd(ctx, true);
            case DND_OFF: return setDnd(ctx, false);

            case ROTATE_ON: return setRotate(ctx, true);
            case ROTATE_OFF: return setRotate(ctx, false);

            case RINGER_SILENT: return setRinger(ctx, AudioManager.RINGER_MODE_SILENT, "Ringer silent");
            case RINGER_VIBRATE: return setRinger(ctx, AudioManager.RINGER_MODE_VIBRATE, "Ringer vibrate");
            case RINGER_NORMAL: return setRinger(ctx, AudioManager.RINGER_MODE_NORMAL, "Ringer normal");

            case VOLUME: return setVolume(ctx, level);
            case BRIGHTNESS: return setBrightness(ctx, level);

            default: return a.label;
        }
    }

    private static void setTorch(Context ctx, boolean on) throws Exception {
        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) throw new IllegalStateException("no camera service");
        for (String id : cm.getCameraIdList()) {
            Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flash != null && flash) { cm.setTorchMode(id, on); return; }
        }
        throw new IllegalStateException("no flash unit");
    }

    private static String setDnd(Context ctx, boolean on) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return "No notification service";
        if (!nm.isNotificationPolicyAccessGranted()) return "Do Not Disturb: grant access in Setup";
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_NONE
                                    : NotificationManager.INTERRUPTION_FILTER_ALL);
        return on ? "Do Not Disturb on" : "Do Not Disturb off";
    }

    private static String setRotate(Context ctx, boolean on) {
        if (!Settings.System.canWrite(ctx)) return "Auto-rotate: grant 'Modify system settings' in Setup";
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, on ? 1 : 0);
        return on ? "Auto-rotate on" : "Auto-rotate off";
    }

    /** Ringer changes need Do Not Disturb access on Android 7+ - say so instead of failing vaguely. */
    private static String setRinger(Context ctx, int mode, String label) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
            return "Ringer: grant Do Not Disturb access in Setup";
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return "Ringer: no audio service on this device";
            am.setRingerMode(mode);
            return label;
        } catch (Throwable t) {
            return "Ringer: Android blocked the change \u2014 grant Do Not Disturb access in Setup";
        }
    }

    private static String setVolume(Context ctx, int percent) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return "Volume: no audio service on this device";
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(percent * max / 100f), 0);
            return "Media volume " + percent + "%";
        } catch (Throwable t) {
            return "Volume: Android blocked the change \u2014 turn Do Not Disturb off or grant DND access";
        }
    }

    private static String setBrightness(Context ctx, int percent) {
        if (!Settings.System.canWrite(ctx)) return "Brightness: grant 'Modify system settings' in Setup";
        int value = Math.round(Math.max(1, Math.min(100, percent)) * 255 / 100f);
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        return "Brightness " + percent + "%";
    }

    // ------------------------------------------------------------------ screens

    private static void openScreen(Context ctx, Action a, boolean viaNotification) {
        Intent i;
        switch (a) {
            case OPEN_WIFI_SETTINGS: i = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
            case OPEN_DATA_SETTINGS: i = new Intent(Settings.ACTION_WIRELESS_SETTINGS); break;
            case OPEN_AIRPLANE_SETTINGS: i = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS); break;
            case OPEN_LOCATION_SETTINGS: i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
            case OPEN_NFC_SETTINGS: i = new Intent("android.settings.NFC_SETTINGS"); break;
            case OPEN_HOTSPOT_SETTINGS: i = new Intent("android.settings.TETHER_SETTINGS"); break;
            case OPEN_INTERNET_PANEL:
                i = Build.VERSION.SDK_INT >= 29
                        ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        : new Intent(Settings.ACTION_WIFI_SETTINGS);
                break;
            default: i = new Intent(Settings.ACTION_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (viaNotification || !Settings.canDrawOverlays(ctx)) {
            notify(ctx, "Tap to open " + a.label.replace("Open ", ""), "Android needs a tap for this screen", i);
            return;
        }
        try {
            ctx.startActivity(i);
        } catch (Throwable t) {
            notify(ctx, "Tap to open " + a.label.replace("Open ", ""), "Android blocked the auto-open", i);
        }
    }

    // ------------------------------------------------------------------ notifications

    private static void notify(Context ctx, String title, String text, Intent open) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Scheduled runs",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true);
            if (open != null) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getActivity(ctx, (int) (System.currentTimeMillis() % 100000), open, flags);
                b.setContentIntent(pi);
            }
            nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Throwable ignored) { }
    }


    /** Turns the local-VPN kill switch on, asking for the one-time consent if needed. */
    private static String killSwitchOn(Context ctx) {
        if (!Prefs.vpnConsent(ctx)) {
            EngineService.start(ctx, EngineService.ACTION_ARM);
            notifyConsent(ctx);
            return "internet-off needs one tap: allow the kill switch (notification)";
        }
        Prefs.setInternetBlocked(ctx, true);
        boolean started = EngineService.start(ctx, EngineService.ACTION_BLOCK);
        if (!started) {
            notifyConsent(ctx);
            return "kill switch is on, but Android blocked the background start \u2014 tap the notification";
        }
        return "kill switch on \u2014 mobile data and Wi-Fi are offline";
    }

    public static void notifyConsent(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("alerts", "Needs your tap",
                        NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent open = VpnConsentActivity.intent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 77, open, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, "alerts")
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle("Allow the kill switch")
                    .setContentText("One-time Android permission \u2014 tap to allow internet-off schedules")
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(4343, b.build());
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ helpers


    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append("\n\u2022 ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String shortText(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 60 ? t.substring(0, 60) + "\u2026" : t;
    }
}
