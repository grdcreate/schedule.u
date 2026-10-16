package com.controlscheduler.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

public class Scheduler {

    public static final String EXTRA_ID = "schedule_id";

    /** Next wall-clock moment this schedule should fire. */
    public static long nextTrigger(Schedule s) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, s.hour);
        cal.set(Calendar.MINUTE, s.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);

        for (int i = 0; i < 8; i++) {
            int dow = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sunday
            if (allowed(s, dow)) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    /** The most recent moment this schedule *should* have fired at or before now. */
    public static long previousTrigger(Schedule s) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, s.hour);
        cal.set(Calendar.MINUTE, s.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() > System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, -1);
        for (int i = 0; i < 8; i++) {
            if (allowed(s, cal.get(Calendar.DAY_OF_WEEK))) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return cal.getTimeInMillis();
    }

    /** True when the last due moment never ran and is still inside the catch-up window. */
    public static boolean missedSince(long previousTrigger, long lastFiredAt, long now, long windowMs) {
        return previousTrigger > lastFiredAt && now - previousTrigger <= windowMs;
    }

    private static boolean allowed(Schedule s, int dow) {
        switch (s.repeat) {
            case 0: return true;                                  // once
            case 1: return true;                                  // daily
            case 2: return dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
            case 3: return dow == Calendar.SUNDAY || dow == Calendar.SATURDAY;
            default: return (s.daysMask & (1 << (dow - 1))) != 0;
        }
    }

    private static PendingIntent intentFor(Context c, long id) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, (int) id, i, flags);
    }

    public static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static void schedule(Context c, Schedule s) {
        if (!s.enabled || !Prefs.armed(c)) { cancel(c, s.id); return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = nextTrigger(s);
        PendingIntent pi = intentFor(c, s.id);
        // three layers, so a schedule is never dropped without a word
        try {
            if (exactAllowed(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                return;
            }
        } catch (Throwable ignored) { }
        try {
            // alarm-clock alarms survive the most aggressive doze/ROM power saving
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
            return;
        } catch (Throwable ignored) { }
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Throwable ignored) { }
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intentFor(c, id));
    }

    public static void rescheduleAll(Context c) {
        List<Schedule> list = Db.get(c).enabled();
        for (Schedule s : list) schedule(c, s);
    }

    public static void cancelAll(Context c) {
        List<Schedule> list = Db.get(c).all();
        for (Schedule s : list) cancel(c, s.id);
    }

    /** Human text for the next occurrence of a schedule (12h or 24h, per the user's choice). */
    public static String nextText(Context c, Schedule s) {
        long t = nextTrigger(s);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        return new java.text.SimpleDateFormat("EEE d MMM, ", java.util.Locale.getDefault()).format(new java.util.Date(t))
                + TimeFmt.label(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), Prefs.twelve(c));
    }

    /** 24-hour variant, kept for callers without a Context. */
    public static String nextText(Schedule s) {
        return new java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(nextTrigger(s)));
    }
}
