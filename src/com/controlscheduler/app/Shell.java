package com.controlscheduler.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Runs privileged shell commands through root, or through Shizuku (ADB wireless
 * debugging) when the phone is not rooted. Shizuku runs the command with the
 * shell UID, which is allowed to flip Wi-Fi, mobile data, airplane mode and more.
 */
public class Shell {

    public static class Result {
        public boolean ok;
        public String output = "";
        public String via = "";
    }

    private static Boolean rootCache = null;
    private static Boolean shizukuCache = null;

    public static void reset() {
        rootCache = null;
        shizukuCache = null;
    }

    // ---------------------------------------------------------------- probes

    public static boolean hasRoot() {
        if (rootCache != null) return rootCache;
        boolean ok = false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            readAll(p.getInputStream());
            ok = p.waitFor(6, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) p.destroy();
        } catch (Throwable t) {
            ok = false;
        }
        rootCache = ok;
        return ok;
    }

    public static boolean hasShizuku() {
        if (shizukuCache != null) return shizukuCache;
        boolean ok = false;
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.isPreV11()) {
                    ok = false;
                } else {
                    ok = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                }
            }
        } catch (Throwable t) {
            ok = false;
        }
        shizukuCache = ok;
        return ok;
    }

    public static boolean shizukuRunning() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean anyPrivilege() {
        return hasRoot() || hasShizuku();
    }

    // ---------------------------------------------------------------- exec

    public static Result exec(Context ctx, String cmd) {
        Result r = new Result();
        if (hasRoot()) {
            r.via = "root";
            r.ok = runRoot(cmd, r);
            if (r.ok) return r;
        }
        if (hasShizuku()) {
            Result s = new Result();
            s.via = "Shizuku";
            s.ok = runShizuku(cmd, s);
            if (s.ok) return s;
            if (r.output.isEmpty()) return s;
        }
        if (r.via.isEmpty()) {
            r.via = "none";
            r.output = "Root and Shizuku are not available";
        }
        return r;
    }

    private static boolean runRoot(String cmd, Result r) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String out = readAll(p.getInputStream()) + readAll(p.getErrorStream());
            r.output = out.trim();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) { p.destroy(); return false; }
            return p.exitValue() == 0 && !looksFailed(out);
        } catch (Throwable t) {
            r.output = String.valueOf(t.getMessage());
            return false;
        }
    }

    private static boolean runShizuku(String cmd, Result r) {
        try {
            Process p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            String out = readAll(p.getInputStream()) + readAll(p.getErrorStream());
            r.output = out.trim();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) { p.destroy(); return false; }
            return p.exitValue() == 0 && !looksFailed(out);
        } catch (Throwable t) {
            r.output = String.valueOf(t.getMessage());
            return false;
        }
    }

    private static boolean looksFailed(String out) {
        if (out == null) return false;
        String s = out.toLowerCase();
        return s.contains("exception") || s.contains("error:") || s.contains("not found")
                || s.contains("permission denied") || s.contains("unknown command");
    }

    private static String readAll(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    // ------------------------------------------------- shell command recipes

    /** Candidate shell commands for a radio action, tried in order until one succeeds. */
    public static String[] commandsFor(Action a) {
        switch (a) {
            case WIFI_OFF: return new String[]{"svc wifi disable", "cmd wifi set-wifi-enabled disabled"};
            case WIFI_ON: return new String[]{"svc wifi enable", "cmd wifi set-wifi-enabled enabled"};
            case DATA_OFF: return new String[]{"svc data disable", "cmd phone data disable"};
            case DATA_ON: return new String[]{"svc data enable", "cmd phone data enable"};
            case AIRPLANE_ON: return new String[]{
                    "cmd connectivity airplane-mode enable",
                    "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"};
            case AIRPLANE_OFF: return new String[]{
                    "cmd connectivity airplane-mode disable",
                    "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false"};
            case BT_OFF: return new String[]{"svc bluetooth disable", "cmd bluetooth_manager disable"};
            case BT_ON: return new String[]{"svc bluetooth enable", "cmd bluetooth_manager enable"};
            case HOTSPOT_OFF: return new String[]{"cmd wifi stop-softap", "svc wifi disable-tethering"};
            case HOTSPOT_ON: return new String[]{"cmd wifi start-softap"};
            case LOCATION_OFF: return new String[]{"settings put secure location_mode 0"};
            case LOCATION_ON: return new String[]{"settings put secure location_mode 3"};
            case NFC_OFF: return new String[]{"svc nfc disable"};
            case NFC_ON: return new String[]{"svc nfc enable"};
            default: return new String[0];
        }
    }

    public static Result runAction(Context ctx, Action a) {
        Result last = new Result();
        last.ok = false;
        last.output = "No command for " + a.label;
        for (String cmd : commandsFor(a)) {
            Result r = exec(ctx, cmd);
            if (r.ok) return r;
            last = r;
        }
        return last;
    }
}
