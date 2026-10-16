package com.controlscheduler.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Calendar;

/**
 * One tap: shows exactly what this phone lets the app do, and blinks the flashlight
 * so the user can see that a "direct" step really runs. Safe - nothing is left changed.
 */
public final class SelfTest {

    private SelfTest() { }

    public static String run(Context ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append(row("Notifications", hasNotifications(ctx),
                "the engine can tell you what each run did",
                "denied - runs stay silent"));

        sb.append(row("Alarms & reminders", Scheduler.exactAllowed(ctx),
                "schedules fire at the exact minute",
                "inexact - allow it in Setup for on-the-minute runs"));

        sb.append(row("Battery unrestricted", batteryFree(ctx),
                "the ROM will not kill the alarm",
                "restricted - alarms can be delayed or dropped"));

        sb.append(row("Do Not Disturb access", dndAccess(ctx),
                "DND + ringer steps work",
                "missing - DND and ringer steps cannot run"));

        sb.append(row("Modify system settings", Settings.System.canWrite(ctx),
                "brightness + auto-rotate steps work",
                "missing - brightness/rotate steps cannot run"));

        sb.append(row("Internet control (kill switch)", Prefs.vpnConsent(ctx),
                "internet off/on works with no root",
                "not allowed yet - tap it in Setup"));

        sb.append(row("Display over other apps", Settings.canDrawOverlays(ctx),
                "settings screens can open without a tap",
                "optional - otherwise you get a notification to tap"));

        boolean root = false, shizuku = false;
        try { root = Shell.hasRoot(); } catch (Throwable ignored) { }
        try { shizuku = Shell.hasShizuku(); } catch (Throwable ignored) { }
        sb.append(row("Root", root, "real Wi-Fi / data / airplane / Bluetooth switching",
                "not rooted - that is fine"));
        sb.append(row("Shizuku", shizuku, "same real switching without root",
                "not running - install Shizuku for radios"));

        // visible proof that the app itself can act, not just ask
        boolean blinked = false;
        String why = "no usable flash unit, or another app is holding the camera";
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean flash = cm.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) {
                        cm.setTorchMode(id, true);
                        try { Thread.sleep(700); } catch (InterruptedException ignored) { }
                        cm.setTorchMode(id, false);
                        blinked = true;
                        why = "";
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            why = "Android refused the torch: " + t.getClass().getSimpleName();
        }
        sb.append(row("Flashlight", blinked, "it just blinked once - direct steps really run", why));

        int armed = 0;
        long soonest = Long.MAX_VALUE;
        try {
            for (Schedule s : Db.get(ctx).enabled()) {
                armed++;
                long t = Scheduler.nextTrigger(s);
                if (t < soonest) soonest = t;
            }
        } catch (Throwable ignored) { }
        String next = "";
        if (soonest != Long.MAX_VALUE) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(soonest);
            next = " \u2022 next run " + TimeFmt.label(cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE), Prefs.twelve(ctx));
        }
        sb.append("\n").append(armed).append(armed == 1 ? " schedule armed" : " schedules armed")
                .append(Prefs.armed(ctx) ? "" : " \u2014 but the engine switch is OFF")
                .append(next).append('\n');

        if (!Prefs.armed(ctx)) {
            sb.append("\nThe master switch on the home screen is off, so nothing will fire.");
        }
        return sb.toString();
    }

    private static String row(String name, boolean ok, String good, String bad) {
        return (ok ? "\u2713 " : "\u2715 ") + name + " \u2014 " + (ok ? good : bad) + "\n";
    }

    private static boolean hasNotifications(Context ctx) {
        return Build.VERSION.SDK_INT < 33
                || ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean dndAccess(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private static boolean batteryFree(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }
}
