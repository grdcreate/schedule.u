package com.controlscheduler.app;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "control_scheduler";
    private static final String ARMED = "engine_enabled";
    private static final String BLOCKED = "internet_blocked";
    private static final String VPN_OK = "vpn_consent";
    private static final String SERVICE = "service_wanted";
    private static final String ROOT = "root_ok";
    private static final String SHIZUKU = "shizuku_ok";
    private static final String SEEDED = "seeded_v1";
    private static final String TWELVE = "twelve_v1";
    private static final String LAST_RUN_TS = "last_run_ts";
    private static final String LAST_RUN_TEXT = "last_run_text";
    private static final String LAST_RUN_OK = "last_run_ok";
    private static final String ASKED_VPN = "asked_vpn";
    private static final String FIRED = "fired_";

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean armed(Context c) { return sp(c).getBoolean(ARMED, true); }
    public static void setArmed(Context c, boolean v) { sp(c).edit().putBoolean(ARMED, v).apply(); }

    public static boolean internetBlocked(Context c) { return sp(c).getBoolean(BLOCKED, false); }
    public static void setInternetBlocked(Context c, boolean v) { sp(c).edit().putBoolean(BLOCKED, v).apply(); }

    public static boolean vpnConsent(Context c) { return sp(c).getBoolean(VPN_OK, false); }
    public static void setVpnConsent(Context c, boolean v) { sp(c).edit().putBoolean(VPN_OK, v).apply(); }

    /** user wants the background engine service running (recommended for reliability) */
    public static boolean serviceWanted(Context c) { return sp(c).getBoolean(SERVICE, true); }
    public static void setServiceWanted(Context c, boolean v) { sp(c).edit().putBoolean(SERVICE, v).apply(); }

    public static boolean rootCached(Context c) { return sp(c).getBoolean(ROOT, false); }
    public static void setRootCached(Context c, boolean v) { sp(c).edit().putBoolean(ROOT, v).apply(); }

    public static boolean seeded(Context c) { return sp(c).getBoolean(SEEDED, false); }
    public static void setSeeded(Context c, boolean v) { sp(c).edit().putBoolean(SEEDED, v).apply(); }

    /** when a schedule last really ran - drives the missed-run catch-up */
    public static long lastFired(Context c, long id) { return sp(c).getLong(FIRED + id, 0L); }
    public static void markFired(Context c, long id, long ts) { sp(c).edit().putLong(FIRED + id, ts).apply(); }

    /** one line about the most recent run, shown on the home screen */
    public static void setLastRun(Context c, long ts, String text, boolean ok) {
        sp(c).edit().putLong(LAST_RUN_TS, ts).putString(LAST_RUN_TEXT, text).putBoolean(LAST_RUN_OK, ok).apply();
    }
    public static long lastRunTs(Context c) { return sp(c).getLong(LAST_RUN_TS, 0L); }
    public static String lastRunText(Context c) { return sp(c).getString(LAST_RUN_TEXT, ""); }
    public static boolean lastRunOk(Context c) { return sp(c).getBoolean(LAST_RUN_OK, true); }

    /** we ask for the kill-switch consent once, on the user's behalf */
    public static boolean askedVpn(Context c) { return sp(c).getBoolean(ASKED_VPN, false); }
    public static void setAskedVpn(Context c, boolean v) { sp(c).edit().putBoolean(ASKED_VPN, v).apply(); }

    /** 12-hour clock with AM/PM (default) or 24-hour - chosen in the new-schedule screen or Setup. */
    public static boolean twelve(Context c) { return sp(c).getBoolean(TWELVE, true); }
    public static void setTwelve(Context c, boolean v) { sp(c).edit().putBoolean(TWELVE, v).apply(); }

    public static boolean shizukuCached(Context c) { return sp(c).getBoolean(SHIZUKU, false); }
    public static void setShizukuCached(Context c, boolean v) { sp(c).edit().putBoolean(SHIZUKU, v).apply(); }
}
