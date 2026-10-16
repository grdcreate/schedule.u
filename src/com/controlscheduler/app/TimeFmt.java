package com.controlscheduler.app;

/**
 * Time labels shared by every screen. The user can read times as 12-hour with AM/PM (default) or
 * as 24-hour; both are produced here so no screen can drift out of sync.
 * Pure Java on purpose - the desktop test harness runs these on the JVM.
 */
public final class TimeFmt {

    private TimeFmt() { }

    /** "23:30" */
    public static String h24(int hour, int minute) {
        return two(hour) + ":" + two(minute);
    }

    /** "11:30 PM" */
    public static String h12(int hour, int minute) {
        return clockPart(hour, minute) + " " + meridiem(hour);
    }

    /** the clock face without the AM/PM part - "11:30", "7:05", "12:00" */
    public static String clockPart(int hour, int minute) {
        int h = hour % 12;
        if (h == 0) h = 12;
        return h + ":" + two(minute);
    }

    /** "AM" before noon, "PM" from noon to 23:59 */
    public static String meridiem(int hour) {
        return hour < 12 ? "AM" : "PM";
    }

    /** what the user asked for: AM/PM or 24-hour */
    public static String label(int hour, int minute, boolean twelve) {
        return twelve ? h12(hour, minute) : h24(hour, minute);
    }

    /**
     * Move an hour into the morning (pm=false) or the afternoon/evening (pm=true) half
     * without changing the clock face: 23 -> 11 AM, 7 -> 7 PM, 12 -> 12 AM, 0 -> 12 PM.
     */
    public static int setMeridiem(int hour, boolean pm) {
        int h = (((hour % 24) + 24) % 24) % 12;
        return pm ? h + 12 : h;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
