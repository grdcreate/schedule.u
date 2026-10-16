package com.controlscheduler.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout container;
    private LinearLayout emptyState;
    private Switch engineSwitch, dataSwitch;
    private TextView engineStatus;
    private View alarmCard;
    private boolean syncing;

    private TextView runLine, needText;
    private View needCard, fab;
    private String heroCache = "";

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshHero();
            ticker.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.list_container);
        emptyState = findViewById(R.id.empty_state);
        engineSwitch = findViewById(R.id.engine_switch);
        dataSwitch = findViewById(R.id.data_switch);
        engineStatus = findViewById(R.id.engine_status);
        alarmCard = findViewById(R.id.alarm_card);
        alarmCard.setOnClickListener(v -> openExactAlarmSettings());

        Ui.tintSwitch(engineSwitch);
        Ui.tintSwitch(dataSwitch);

        // first launch: Sleep mode (23:30) + Good morning (07:00) are already there
        if (Presets.seedIfFirstRun(this)) {
            Toast.makeText(this, "Sleep mode " + Ui.time(this, 23, 30)
                    + " and Good morning " + Ui.time(this, 7, 0) + " added", Toast.LENGTH_LONG).show();
            EngineService.ensureRunning(this);
        }

        engineSwitch.setChecked(Prefs.armed(this));
        engineSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            Prefs.setArmed(this, checked);
            if (checked) {
                Scheduler.rescheduleAll(this);
                EngineService.start(this, EngineService.ACTION_ARM);
            } else {
                Scheduler.cancelAll(this);
                if (!Prefs.internetBlocked(this)) EngineService.start(this, EngineService.ACTION_DISARM);
            }
            updateEngineLabel();
            refreshHero();
            Toast.makeText(this, checked ? "Schedules armed" : "All schedules paused", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.engine_card).setOnClickListener(v -> engineSwitch.setChecked(!engineSwitch.isChecked()));

        dataSwitch.setChecked(Prefs.internetBlocked(this));
        dataSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            setInternet(checked);
        });
        findViewById(R.id.data_card).setOnClickListener(v -> dataSwitch.setChecked(!dataSwitch.isChecked()));

        findViewById(R.id.fab).setOnClickListener(v -> startActivity(new Intent(this, AddScheduleActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_log).setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));
        runLine = findViewById(R.id.run_line);
        runLine.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));
        needCard = findViewById(R.id.need_card);
        needText = findViewById(R.id.need_text);
        needCard.setOnClickListener(v -> openMostUrgentFix());

        fab = findViewById(R.id.fab);
        fab.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(90).start();
            } else if (e.getAction() == android.view.MotionEvent.ACTION_UP
                    || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            }
            return false;
        });

        askForRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncing = true;                      // stop programmatic switches firing their listeners
        engineSwitch.setChecked(Prefs.armed(this));
        dataSwitch.setChecked(Prefs.internetBlocked(this));
        syncing = false;

        updateEngineLabel();
        alarmCard.setVisibility(Scheduler.exactAllowed(this) ? View.GONE : View.VISIBLE);
        // self-healing: every open re-registers the alarms, so a dropped one is restored
        if (Prefs.armed(this)) Scheduler.rescheduleAll(this);
        refreshHero();
        load();
        refreshNeeds();
        catchUpMissed();
        maybeAskForVpnConsent();
        EngineService.ensureRunning(this);
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, "Settings \u203a Apps \u203a Special access \u203a Alarms & reminders", Toast.LENGTH_LONG).show();
        }
    }

    private void setInternet(boolean block) {
        if (block && !Prefs.vpnConsent(this)) {
            startActivity(VpnConsentActivity.intent(this, true));   // consent, then block right away
            return;
        }
        Prefs.setInternetBlocked(this, block);
        EngineService.start(this, block ? EngineService.ACTION_BLOCK : EngineService.ACTION_UNBLOCK);
        updateEngineLabel();
        Toast.makeText(this, block ? "Internet blocked \u2014 mobile data + Wi-Fi are dead" : "Internet restored", Toast.LENGTH_SHORT).show();
    }

    private void updateEngineLabel() {
        boolean on = Prefs.armed(this);
        engineStatus.setText(on ? "armed \u2022 schedules are live" : "paused \u2022 nothing will fire");
        engineStatus.setTextColor(on ? Ui.ACCENT : Ui.TEXT2);
    }

    // ------------------------------------------------------------------ masthead

    private void refreshHero() {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy", Locale.US);

        ((TextView) findViewById(R.id.rail_time)).setText(
                Ui.time(this, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
        ((TextView) findViewById(R.id.rail_date)).setText(date.format(now.getTime()).toUpperCase(Locale.US));

        TextView heroTime = findViewById(R.id.hero_time);
        TextView heroWhen = findViewById(R.id.hero_when);
        TextView heroMeta = findViewById(R.id.hero_meta);

        List<Schedule> list = Db.get(this).enabled();
        Schedule soonest = null;
        long best = Long.MAX_VALUE;
        for (Schedule s : list) {
            long t = Scheduler.nextTrigger(s);
            if (t < best) { best = t; soonest = s; }
        }

        if (soonest == null) {
            heroTime.setText("--:--");
            heroWhen.setText("nothing scheduled");
            heroMeta.setText(Prefs.armed(this)
                    ? "Tap + to stack up to any number of controls on one time."
                    : "Schedules are paused \u2014 flip the switch above to arm them.");
            return;
        }

        CharSequence face = Ui.bigTime(this, soonest.hour, soonest.minute, 0.34f);
        if (!face.toString().equals(heroCache)) {          // cross-fade only when the time really changed
            heroCache = face.toString();
            heroTime.setAlpha(0.15f);
            heroTime.animate().alpha(1f).setDuration(280).start();
        }
        heroTime.setText(face);
        heroWhen.setText(whenWord(best));
        heroMeta.setText((soonest.name.isEmpty() ? "Schedule" : soonest.name)
                + "  \u2022  in " + countdown(best) + "\n" + soonest.stepsText());
        refreshRunLine();
    }

    /** "7h 12m" / "42m" / "now" - the run is coming, visibly. */
    private String countdown(long trigger) {
        long ms = trigger - System.currentTimeMillis();
        if (ms <= 30_000L) return "under a minute";
        long mins = Math.round(ms / 60_000.0);
        if (mins < 60) return mins + " min";
        long hours = mins / 60, rest = mins % 60;
        if (hours < 24) return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    /** What happened the last time a schedule actually ran. */
    private void refreshRunLine() {
        if (runLine == null) return;
        long ts = Prefs.lastRunTs(this);
        if (ts == 0L) {
            runLine.setText("No runs yet \u2014 long-press a schedule and tap Run now to test it");
            runLine.setTextColor(Ui.TEXT3);
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        String when = TimeFmt.label(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), Prefs.twelve(this));
        runLine.setText("Last run " + when + " \u2022 " + Prefs.lastRunText(this));
        runLine.setTextColor(Prefs.lastRunOk(this) ? Ui.TEXT2 : Ui.WARN);
    }

    // ------------------------------------------------------------------ readiness

    private boolean hasNotifications() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasDndAccess() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private boolean batteryFree() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    /** does any enabled schedule touch the internet? then the kill switch matters */
    private boolean needsInternetControl() {
        for (Schedule s : Db.get(this).enabled()) {
            for (Step st : s.steps) {
                if (st.action == Action.INTERNET_OFF || st.action == Action.DATA_OFF) return true;
            }
        }
        return false;
    }

    /** Everything that would silently ruin a run, in the order it should be fixed. */
    private void refreshNeeds() {
        if (needCard == null) return;
        ArrayList<String> missing = new ArrayList<>();
        if (!hasNotifications()) missing.add("Notifications \u2014 so runs can tell you what happened");
        if (needsInternetControl() && !Prefs.vpnConsent(this)) missing.add("Internet control \u2014 one tap makes internet-off work without root");
        if (!hasDndAccess()) missing.add("Do Not Disturb access \u2014 needed by DND steps");
        if (!Settings.System.canWrite(this)) missing.add("Modify system settings \u2014 needed by brightness / auto-rotate steps");
        if (!batteryFree()) missing.add("Battery: unrestricted \u2014 so the ROM can't kill the alarm");
        if (missing.isEmpty()) {
            needCard.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            sb.append("\u2022 ").append(missing.get(i));
            if (i < missing.size() - 1) sb.append('\n');
        }
        needText.setText(sb.toString() + "\n\nTAP TO FIX THE FIRST ONE");
        needText.setTextColor(Ui.ACCENT);
        needCard.setVisibility(View.VISIBLE);
    }

    private void openMostUrgentFix() {
        if (needsInternetControl() && !Prefs.vpnConsent(this)) {
            startActivity(VpnConsentActivity.intent(this));
            return;
        }
        if (!hasNotifications()) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            }
            return;
        }
        if (!hasDndAccess()) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "Settings \u203a Sound \u203a Do Not Disturb access", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!Settings.System.canWrite(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Toast.makeText(this, "Settings \u203a Apps \u203a Modify system settings", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!batteryFree()) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Toast.makeText(this, "Settings \u203a Battery \u203a Unrestricted", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------------ catch-up

    /**
     * If an alarm was dropped (phone off, ROM power saving, force stop) the run is not lost:
     * opening the app runs the schedule that should have fired in the last half hour.
     */
    private void catchUpMissed() {
        if (!Prefs.armed(this)) return;
        long now = System.currentTimeMillis();
        long window = 30 * 60_000L;
        for (Schedule s : Db.get(this).enabled()) {
            long last = Prefs.lastFired(this, s.id);
            if (last <= 0) continue;                       // never ran before: don't run retroactively
            long prev = Scheduler.previousTrigger(s);
            if (!Scheduler.missedSince(prev, last, now, window)) continue;
            Toast.makeText(this, "Running " + (s.name.isEmpty() ? "schedule" : s.name)
                    + " late \u2014 the alarm was missed", Toast.LENGTH_LONG).show();
            EngineService.runSchedule(this, s.id);
            break;
        }
    }

    /** The presets include internet steps, so ask for the one-time kill-switch consent on first open. */
    private void maybeAskForVpnConsent() {
        if (Prefs.vpnConsent(this) || Prefs.askedVpn(this)) return;
        if (!Prefs.armed(this) || !needsInternetControl()) return;
        Prefs.setAskedVpn(this, true);
        startActivity(VpnConsentActivity.intent(this));
    }

    // ------------------------------------------------------------------ animation

    private void animateRows() {
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(Ui.dp(this, 14));
            row.animate().alpha(1f).translationY(0f)
                    .setStartDelay(40L * i).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    /** "tonight" / "tomorrow" / weekday name, matching the reference artwork's tone. */
    private String whenWord(long trigger) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(trigger);
        int dayDelta = b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR);
        if (b.get(Calendar.YEAR) != a.get(Calendar.YEAR)) dayDelta = 1;
        if (dayDelta <= 0) {
            int hour = b.get(Calendar.HOUR_OF_DAY);
            if (a.get(Calendar.HOUR_OF_DAY) < 5 && hour < 5) return "tonight";
            return hour >= 18 || hour < 5 ? "tonight" : "today";
        }
        if (dayDelta == 1) return "tomorrow";
        return new SimpleDateFormat("EEEE", Locale.US).format(new Date(trigger)).toLowerCase(Locale.US);
    }

    // ------------------------------------------------------------------ list

    private void load() {
        container.removeAllViews();
        List<Schedule> list = Db.get(this).all();
        boolean empty = list.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.list_scroll).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        LayoutInflater inf = LayoutInflater.from(this);
        for (final Schedule s : list) {
            View row = inf.inflate(R.layout.item_schedule, container, false);
            TextView badge = row.findViewById(R.id.item_badge);
            TextView name = row.findViewById(R.id.item_name);
            TextView meta = row.findViewById(R.id.item_meta);
            TextView next = row.findViewById(R.id.item_next);
            Switch sw = row.findViewById(R.id.item_switch);

            badge.setText(Ui.bigTime(this, s.hour, s.minute, 0.62f));
            badge.setTextColor(s.enabled ? Ui.ACCENT : Ui.TEXT3);
            name.setText(s.name.isEmpty() ? "Schedule" : s.name);
            name.setTextColor(s.enabled ? Ui.TEXT : Ui.TEXT2);
            meta.setText(s.repeatText() + "  \u2022  " + s.stepsText());
            if (s.enabled) {
                next.setText("NEXT RUN  " + Scheduler.nextText(this, s));
                next.setVisibility(View.VISIBLE);
            } else {
                next.setVisibility(View.GONE);
            }

            Ui.tintSwitch(sw);
            sw.setChecked(s.enabled);
            sw.setOnCheckedChangeListener((v, checked) -> {
                Db.get(this).setEnabled(s.id, checked);
                s.enabled = checked;
                if (checked) Scheduler.schedule(this, s); else Scheduler.cancel(this, s.id);
                refreshHero();
                load();
            });
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, AddScheduleActivity.class);
                i.putExtra("id", s.id);
                startActivity(i);
            });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(s.name.isEmpty() ? "Schedule" : s.name)
                        .setPositiveButton("Run now", (d, w) -> {
                            final MainActivity self = this;
                            Toast.makeText(this, "Running\u2026", Toast.LENGTH_SHORT).show();
                            new Thread(() -> {
                                final String out = Runner.run(self, s, false);
                                runOnUiThread(() -> Toast.makeText(self, out, Toast.LENGTH_LONG).show());
                            }, "run-now").start();
                        })
                        .setNeutralButton("Delete", (d, w) -> {
                            Scheduler.cancel(this, s.id);
                            Db.get(this).delete(s.id);
                            refreshHero();
                            load();
                        })
                        .setNegativeButton("Close", null)
                        .show();
                return true;
            });
            container.addView(row);
        }
        animateRows();
    }

    private void askForRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }
}
