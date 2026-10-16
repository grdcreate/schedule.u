package com.controlscheduler.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

/** Setup + engine status. Every row reflects live state. */
public class SettingsActivity extends Activity {

    /** Root probing spawns "su", which pops a Magisk prompt - only do it once per app run. */
    private static boolean probedThisSession = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.row_write).setOnClickListener(v -> openWriteSettings());
        findViewById(R.id.row_dnd).setOnClickListener(v -> openDndSettings());
        findViewById(R.id.row_alarm).setOnClickListener(v -> openExactAlarmSettings());
        findViewById(R.id.row_battery).setOnClickListener(v -> openBatterySettings());
        findViewById(R.id.row_overlay).setOnClickListener(v -> openOverlaySettings());
        findViewById(R.id.row_vpn).setOnClickListener(v -> openVpnConsent());
        findViewById(R.id.row_always_vpn).setOnClickListener(v -> openVpnSettings());
        findViewById(R.id.row_root).setOnClickListener(v -> probePrivileges(true));
        findViewById(R.id.row_shizuku).setOnClickListener(v -> openShizukuApp());
        findViewById(R.id.state_shizuku).setOnClickListener(v -> probePrivileges(true));
        findViewById(R.id.row_service).setOnClickListener(v -> toggleService());
        findViewById(R.id.row_timefmt).setOnClickListener(v -> toggleTimeFormat());
        findViewById(R.id.row_selftest).setOnClickListener(v -> runSelfTest());
        findViewById(R.id.state_selftest).setOnClickListener(v -> runSelfTest());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mark(R.id.state_write, Settings.System.canWrite(this));
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mark(R.id.state_dnd, nm != null && nm.isNotificationPolicyAccessGranted());

        boolean exact;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            exact = am != null && am.canScheduleExactAlarms();
        } else exact = true;
        mark(R.id.state_alarm, exact);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mark(R.id.state_battery, pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
        mark(R.id.state_overlay, Settings.canDrawOverlays(this));
        mark(R.id.state_vpn, Prefs.vpnConsent(this));
        mark(R.id.state_always_vpn, isAlwaysOnVpn());
        refreshTimeFormat();

        if (!probedThisSession) {
            probedThisSession = true;
            probePrivileges(false);
        } else {
            refreshPrivilegeText();
        }

        TextView state = findViewById(R.id.state_service);
        boolean on = Prefs.serviceWanted(this);
        state.setText(on ? "\u2713 ON" : "OFF");
        state.setTextColor(on ? Ui.ACCENT : Ui.TEXT2);
    }

    private void mark(int stateId, boolean ok) {
        TextView t = findViewById(stateId);
        t.setText(ok ? "\u2713 DONE" : "OPEN");
        t.setTextColor(ok ? Ui.ACCENT : Ui.TEXT);
    }

    /** Re-paints the cached privilege state without spawning su again. */
    private void refreshPrivilegeText() {
        boolean root = Prefs.rootCached(this);
        boolean shizuku = Prefs.shizukuCached(this);
        TextView rootState = findViewById(R.id.state_root);
        TextView shizukuState = findViewById(R.id.state_shizuku);
        rootState.setText(root ? "\u2713 FULL" : "NOT AVAILABLE");
        rootState.setTextColor(root ? Ui.ACCENT : Ui.TEXT2);
        shizukuState.setText(shizuku ? "\u2713 GRANTED" : "NOT RUNNING");
        shizukuState.setTextColor(shizuku ? Ui.ACCENT : Ui.TEXT2);
    }

    /** Root first, then Shizuku - also shows what the engine will use right now. */
    private void probePrivileges(boolean interactive) {
        Shell.reset();
        TextView root = findViewById(R.id.state_root);
        TextView shizuku = findViewById(R.id.state_shizuku);
        final TextView engine = findViewById(R.id.engine_capability);

        root.setText("\u2026"); shizuku.setText("\u2026");
        if (interactive) engine.setText("Probing\u2026");

        new Thread(() -> {
            final boolean hasRoot = Shell.hasRoot();
            final boolean hasShizuku = Shell.hasShizuku();
            final boolean shRunning = Shell.shizukuRunning();
            runOnUiThread(() -> {
                Prefs.setRootCached(this, hasRoot);
                Prefs.setShizukuCached(this, hasShizuku);

                root.setText(hasRoot ? "\u2713 FULL" : "NOT AVAILABLE");
                root.setTextColor(hasRoot ? Ui.ACCENT : Ui.TEXT2);

                if (hasShizuku) {
                    shizuku.setText("\u2713 GRANTED");
                    shizuku.setTextColor(Ui.ACCENT);
                } else if (shRunning) {
                    shizuku.setClickable(true);
                    shizuku.setText("ALLOW \u2192");
                    shizuku.setTextColor(Ui.ACCENT);
                    shizuku.setOnClickListener(v -> requestShizuku());
                } else {
                    shizuku.setText("NOT RUNNING");
                    shizuku.setTextColor(Ui.TEXT2);
                    shizuku.setOnClickListener(v -> openShizukuApp());
                }

                if (hasRoot) {
                    engine.setText("Engine: root \u2014 Wi-Fi, mobile data, airplane mode, Bluetooth and more switch automatically.");
                    engine.setTextColor(Ui.ACCENT);
                } else if (hasShizuku) {
                    engine.setText("Engine: Shizuku \u2014 same automatic switching, granted through ADB (shell).");
                    engine.setTextColor(Ui.ACCENT);
                } else {
                    engine.setText("Engine: kill switch \u2014 internet actions work everywhere; other radios will open their settings screen. Install Shizuku + enable wireless debugging for full automatic control without root.");
                    engine.setTextColor(Ui.WARN);
                }
            });
        }, "probe").start();
    }

    private void requestShizuku() {
        try {
            rikka.shizuku.Shizuku.requestPermission(0);
            Toast.makeText(this, "Allow the request from Shizuku, then probe again", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Open Shizuku and allow schedule.u", Toast.LENGTH_LONG).show();
        }
    }

    private void openShizukuApp() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i == null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")));
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Shizuku app not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleService() {
        boolean on = !Prefs.serviceWanted(this);
        Prefs.setServiceWanted(this, on);
        if (on) EngineService.ensureRunning(this);
        else if (!Prefs.internetBlocked(this)) EngineService.start(this, EngineService.ACTION_DISARM);
        Toast.makeText(this, on ? "Background engine on" : "Background engine off", Toast.LENGTH_SHORT).show();
        onResume();
    }

    /** True when the user pinned this app as the always-on VPN. */
    private boolean isAlwaysOnVpn() {
        try {
            String app = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
            return getPackageName().equals(app);
        } catch (Throwable t) {
            return false;
        }
    }

    private void openVpnSettings() {
        try {
            Intent i = new Intent("android.settings.VPN_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Toast.makeText(this, "Choose schedule.u → Always-on (and turn Lockdown on if you want it permanent)", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Settings › Network › VPN › Control Scheduler › Always-on", Toast.LENGTH_LONG).show();
        }
    }

    private void openVpnConsent() {
        try {
            Intent prepare = VpnService.prepare(this);
            if (prepare == null) {
                Prefs.setVpnConsent(this, true);
                Toast.makeText(this, "Kill switch already allowed", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(VpnConsentActivity.intent(this));
            }
        } catch (Throwable t) {
            Toast.makeText(this, "VPN consent not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWriteSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, "Settings \u203a Apps \u203a Special access \u203a Modify system settings", Toast.LENGTH_LONG).show();
        }
    }

    private void openDndSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, "Settings \u203a Notifications \u203a Do Not Disturb access", Toast.LENGTH_LONG).show();
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) {
            Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, "Settings \u203a Apps \u203a Special access \u203a Alarms & reminders", Toast.LENGTH_LONG).show();
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                Toast.makeText(this, "Settings \u203a Battery \u203a Unrestricted", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Probes the phone on a worker thread (it blinks the torch) and reports back in a dialog. */
    private void runSelfTest() {
        final SettingsActivity self = this;
        Toast.makeText(this, "Testing\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String report;
            try {
                report = SelfTest.run(self);
            } catch (Throwable t) {
                report = "Self-test failed: " + t;
            }
            final String out = report;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new android.app.AlertDialog.Builder(self)
                        .setTitle("Self-test")
                        .setMessage(out)
                        .setPositiveButton("Close", null)
                        .show();
            });
        }, "self-test").start();
    }

    /** 12-hour with AM/PM <-> 24-hour, applied everywhere (screens, list badges, notifications). */
    private void toggleTimeFormat() {
        Prefs.setTwelve(this, !Prefs.twelve(this));
        refreshTimeFormat();
        Toast.makeText(this, Prefs.twelve(this)
                ? "Times now read as 11:30 PM"
                : "Times now read as 23:30", Toast.LENGTH_SHORT).show();
    }

    private void refreshTimeFormat() {
        TextView tv = findViewById(R.id.state_timefmt);
        boolean twelve = Prefs.twelve(this);
        tv.setText(twelve ? "12-HOUR" : "24-HOUR");
        tv.setTextColor(twelve ? Ui.ACCENT : Ui.TEXT2);
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, "Settings \u203a Apps \u203a Display over other apps", Toast.LENGTH_LONG).show();
        }
    }
}
