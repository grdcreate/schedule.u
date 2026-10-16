package com.controlscheduler.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Builds a schedule: any number of steps, one time, one repeat rule. */
public class AddScheduleActivity extends Activity {

    private EditText inputName, inputCommand;
    private TextView timeText, timeAmpm, timeHint, levelLabel, hint, emptySteps;
    private TextView chipAm, chipPm, chip24;   // AM / PM quick-pick + 12h-or-24h toggle
    private LinearLayout stepsBox, levelCard, daysRow, methodRow;
    private SeekBar levelSeek;
    private int levelTarget = -1;              // which step the level slider edits

    private int hour = 23, minute = 0;
    private int repeat = 1;
    private int daysMask = 0b1111111;
    private int method = 0;
    private final List<Step> steps = new ArrayList<>();
    private Schedule editing;

    private final List<TextView> repeatChips = new ArrayList<>();
    private final List<TextView> methodChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add);

        inputName = findViewById(R.id.input_name);
        inputCommand = findViewById(R.id.input_command);
        timeText = findViewById(R.id.time_text);
        timeAmpm = findViewById(R.id.time_ampm);
        timeHint = findViewById(R.id.time_hint);
        levelLabel = findViewById(R.id.param_label);
        levelCard = findViewById(R.id.param_card);
        levelSeek = findViewById(R.id.param_seek);
        hint = findViewById(R.id.action_hint);
        daysRow = findViewById(R.id.days_row);
        methodRow = findViewById(R.id.group_method);
        stepsBox = findViewById(R.id.steps_container);
        emptySteps = findViewById(R.id.steps_empty);

        long id = getIntent().getLongExtra("id", -1);
        if (id > 0) {
            editing = Db.get(this).byId(id);
            if (editing != null) {
                hour = editing.hour;
                minute = editing.minute;
                repeat = editing.repeat;
                daysMask = editing.daysMask;
                method = editing.method;
                inputName.setText(editing.name);
                inputCommand.setText(editing.command);
                steps.addAll(editing.steps);
                ((TextView) findViewById(R.id.screen_title)).setText("Edit schedule");
            }
        }
        if (steps.isEmpty()) steps.add(new Step(Action.DATA_OFF, 60));  // sensible default

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        // the picker shows the same clock the screen shows: AM/PM dial in 12-hour mode, 24-hour otherwise
        findViewById(R.id.time_card).setOnClickListener(v ->
                new TimePickerDialog(this, (view, h, m) -> { hour = h; minute = m; refreshTime(); },
                        hour, minute, !Prefs.twelve(this)).show());
        buildMeridiemChips();

        buildActionChips();
        buildRepeatChips();
        buildMethodChips();
        buildDayChips();

        levelSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) { refreshLevel(); }
            public void onStartTrackingTouch(SeekBar sb) { }
            public void onStopTrackingTouch(SeekBar sb) { }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_test).setOnClickListener(v -> {
            final Schedule tmp = collect();
            final AddScheduleActivity self = this;
            Toast.makeText(this, "Testing\u2026", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final String out = Runner.run(self, tmp, false);
                runOnUiThread(() -> Toast.makeText(self, out, Toast.LENGTH_LONG).show());
            }, "test-run").start();
        });

        refreshTime();
        refreshSteps();
        refreshRepeatUi();
        refreshMethodUi();
        refreshHint();
        refreshLevel();
    }

    // ------------------------------------------------------------- chips

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13f);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.chip);
        t.setTextColor(getResources().getColorStateList(R.color.chip_text, getTheme()));
        int px = Ui.dp(this, 16), py = Ui.dp(this, 10);
        t.setPadding(px, py, px, py);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        return t;
    }

    /** Tapping a control chip adds it as a step (multi-action schedules). */
    private void buildActionChips() {
        LinearLayout toggles = findViewById(R.id.group_toggles);
        LinearLayout sound = findViewById(R.id.group_sound);
        LinearLayout net = findViewById(R.id.group_network);
        LinearLayout internet = findViewById(R.id.group_internet);
        LinearLayout screens = findViewById(R.id.group_screens);
        LinearLayout advanced = findViewById(R.id.group_advanced);

        for (final Action a : Action.values()) {
            TextView t = chip(a.label);
            t.setOnClickListener(v -> {
                steps.add(new Step(a, preferredLevel(a)));
                if (a == Action.VOLUME || a == Action.BRIGHTNESS) {
                    levelTarget = steps.size() - 1;
                    levelSeek.setProgress(steps.get(levelTarget).level);   // keeps slider + step in sync
                }
                refreshSteps();
                refreshHint();
            });
            switch (a.group) {
                case "Toggles": toggles.addView(t); break;
                case "Sound & screen": sound.addView(t); break;
                case "Network": net.addView(t); break;
                case "Internet": internet.addView(t); break;
                case "Screens": screens.addView(t); break;
                default: advanced.addView(t); break;
            }
        }
    }

    private int preferredLevel(Action a) {
        return a == Action.BRIGHTNESS ? 25 : 40;
    }

    private void buildRepeatChips() {
        LinearLayout row = findViewById(R.id.group_repeat);
        for (int i = 0; i < Schedule.REPEAT_LABELS.length; i++) {
            final int value = i;
            TextView t = chip(Schedule.REPEAT_LABELS[i]);
            t.setOnClickListener(v -> { repeat = value; refreshRepeatUi(); });
            repeatChips.add(t);
            row.addView(t);
        }
    }

    private void buildMethodChips() {
        methodRow.removeAllViews();
        for (int i = 0; i < Schedule.METHOD_LABELS.length; i++) {
            final int value = i;
            TextView t = chip(Schedule.METHOD_LABELS[i]);
            t.setOnClickListener(v -> { method = value; refreshMethodUi(); refreshHint(); });
            methodChips.add(t);
            methodRow.addView(t);
        }
    }

    private void buildDayChips() {
        String[] names = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(12f);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundResource(R.drawable.day);
            t.setTextColor(getResources().getColorStateList(R.color.day_text, getTheme()));
            t.setSelected((daysMask & bit) != 0);
            int size = Ui.dp(this, 38);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Ui.dp(this, 8);
            t.setLayoutParams(lp);
            t.setClickable(true);
            t.setOnClickListener(v -> {
                daysMask = (daysMask & bit) != 0 ? daysMask & ~bit : daysMask | bit;
                t.setSelected((daysMask & bit) != 0);
            });
            daysRow.addView(t);
        }
    }

    // ------------------------------------------------------------- selected steps

    private void refreshSteps() { refreshSteps(true); }

    private void refreshSteps(boolean rebuild) {
        if (!rebuild && stepsBox.getChildCount() == steps.size()) {
            for (int i = 0; i < steps.size(); i++) {
                View row = stepsBox.getChildAt(i);
                ((TextView) row.findViewById(R.id.step_title)).setText(steps.get(i).describe());
                ((TextView) row.findViewById(R.id.step_type)).setText(methodTextFor(steps.get(i).action));
            }
            return;
        }
        stepsBox.removeAllViews();
        emptySteps.setVisibility(steps.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            final Step step = steps.get(i);
            View row = getLayoutInflater().inflate(R.layout.item_step, stepsBox, false);
            TextView title = row.findViewById(R.id.step_title);
            TextView number = row.findViewById(R.id.step_number);
            TextView type = row.findViewById(R.id.step_type);

            number.setText(String.valueOf(i + 1));
            title.setText(step.describe());
            type.setText(methodTextFor(step.action));

            row.findViewById(R.id.step_remove).setOnClickListener(v -> {
                steps.remove(index);
                if (levelTarget >= steps.size()) levelTarget = steps.size() - 1;
                refreshSteps();
                refreshHint();
            });
            row.setOnClickListener(v -> {
                if (step.action == Action.VOLUME || step.action == Action.BRIGHTNESS) {
                    levelTarget = index;
                    levelSeek.setProgress(step.level);
                    refreshLevel();                      // also when the value did not change
                } else {
                    levelTarget = -1;
                    refreshLevel();
                }
            });
            row.setOnLongClickListener(v -> {
                if (index > 0) {
                    Step moved = steps.remove(index);
                    steps.add(index - 1, moved);
                    refreshSteps();
                }
                return true;
            });
            stepsBox.addView(row);
            if (rebuild) {
                row.setAlpha(0f);
                row.setTranslationX(Ui.dp(this, 16));
                row.animate().alpha(1f).translationX(0f).setStartDelay(30L * index)
                        .setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }
    }

    private boolean touchesInternet() {
        for (Step step : steps) {
            if (step.action == Action.INTERNET_OFF || step.action == Action.DATA_OFF) return true;
        }
        return false;
    }

    private String methodTextFor(Action a) {
        switch (a.kind) {
            case DIRECT: return "in-app";
            case VPN: return "kill switch";
            case CMD: return "root / Shizuku";
            case SCREEN: return "opens screen";
            default:
                if (method == 1) return "root only";
                if (method == 2) return "Shizuku only";
                if (method == 3) return "kill switch";
                if (method == 4) return "screen only";
                if (Shell.hasRoot()) return "root";
                if (Shell.hasShizuku()) return "Shizuku";
                return "auto \u2192 kill switch / screen";
        }
    }

    private boolean hasLevelStep() {
        for (Step st : steps) {
            if (st.action == Action.VOLUME || st.action == Action.BRIGHTNESS) return true;
        }
        return false;
    }

    private boolean hasCommandStep() {
        for (Step st : steps) {
            if (st.action == Action.COMMAND) return true;
        }
        return false;
    }

    private void refreshHint() {
        levelCard.setVisibility(hasLevelStep() ? View.VISIBLE : View.GONE);
        inputCommand.setVisibility(hasCommandStep() ? View.VISIBLE : View.GONE);
        if (levelTarget < 0 && hasLevelStep()) refreshLevel();
        StringBuilder sb = new StringBuilder();
        boolean priv = false, vpn = false;
        for (Step s : steps) {
            if (s.action.kind == Action.Kind.PRIV) priv = true;
            if (s.action.kind == Action.Kind.VPN) vpn = true;
            if (s.action.note != null && sb.length() < 200) { sb.append(s.action.note).append(' '); }
        }
        if (priv && !Shell.anyPrivilege() && method == 0) {
            sb.append("No root or Shizuku found: mobile data / airplane will use the kill switch, other radios open their screen. See Setup.");
        }
        if (vpn) sb.append("Kill switch needs the one-time VPN consent.");
        if (sb.length() == 0) {
            hint.setVisibility(View.GONE);
        } else {
            hint.setVisibility(View.VISIBLE);
            hint.setText(sb.toString().trim());
        }
    }

    private void refreshRepeatUi() {
        for (int i = 0; i < repeatChips.size(); i++) bounce(repeatChips.get(i), i == repeat);
        daysRow.setVisibility(repeat == 4 ? View.VISIBLE : View.GONE);
    }

    private void refreshMethodUi() {
        for (int i = 0; i < methodChips.size(); i++) bounce(methodChips.get(i), i == method);
    }

    private void refreshLevel() {
        int value = levelSeek.getProgress();
        String name = null;
        if (levelTarget >= 0 && levelTarget < steps.size()) {
            Step target = steps.get(levelTarget);
            if (target.action == Action.VOLUME || target.action == Action.BRIGHTNESS) {
                target.level = value;
                name = target.describe();
            } else {
                levelTarget = -1;
            }
        }
        if (name == null) {
            levelLabel.setText("Tap a volume or brightness step in the stack, then drag");
        } else {
            levelLabel.setText("Editing step " + (levelTarget + 1) + " \u2014 " + name);
        }
        refreshSteps(false);   // repaint titles without stealing the slider focus
    }

    /** AM / PM quick-pick next to the clock face, plus the 12h / 24h switch. */
    private void buildMeridiemChips() {
        LinearLayout row = findViewById(R.id.group_meridiem);
        chipAm = chip("AM");
        chipPm = chip("PM");
        chip24 = chip("24H");
        chipAm.setOnClickListener(v -> setMeridiem(false));
        chipPm.setOnClickListener(v -> setMeridiem(true));
        chip24.setOnClickListener(v -> {
            Prefs.setTwelve(this, !Prefs.twelve(this));
            refreshTime();
        });
        row.addView(chipAm);
        row.addView(chipPm);
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(spacer);
        row.addView(chip24);
    }

    /** tap AM/PM -> jump to the morning or the afternoon, clock face untouched */
    private void setMeridiem(boolean pm) {
        hour = TimeFmt.setMeridiem(hour, pm);
        Prefs.setTwelve(this, true);       // picking AM/PM means you want to read AM/PM
        refreshTime();
    }

    private void refreshTime() {
        boolean twelve = Prefs.twelve(this);
        timeText.setText(twelve ? TimeFmt.clockPart(hour, minute) : TimeFmt.h24(hour, minute));
        timeAmpm.setText(TimeFmt.meridiem(hour));
        timeAmpm.setVisibility(twelve ? View.VISIBLE : View.GONE);
        if (chipAm != null) chipAm.setSelected(hour < 12);
        if (chipPm != null) chipPm.setSelected(hour >= 12);
        if (chip24 != null) chip24.setSelected(!twelve);
        refreshTimeHint();
    }

    /** Say it out loud when the chosen time already went by today. */
    private void refreshTimeHint() {
        if (timeHint == null) return;
        java.util.Calendar now = java.util.Calendar.getInstance();
        int nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int pick = hour * 60 + minute;
        if (pick < nowMin) {
            timeHint.setText(Ui.time(this, hour, minute) + " already passed today \u2014 the first run is tomorrow at "
                    + Ui.time(this, hour, minute) + ". Long-press the schedule and tap Run now to test it straight away.");
            timeHint.setVisibility(View.VISIBLE);
        } else if (pick - nowMin <= 2) {
            timeHint.setText("About to run \u2014 " + Ui.time(this, hour, minute) + " is a minute away.");
            timeHint.setVisibility(View.VISIBLE);
        } else {
            timeHint.setVisibility(View.GONE);
        }
    }

    /** small spring on the chip the user just picked */
    private void bounce(final TextView chip, boolean selected) {
        chip.setSelected(selected);
        if (!selected) return;
        chip.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90)
                .withEndAction(() -> chip.animate().scaleX(1f).scaleY(1f).setDuration(130)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start())
                .start();
    }

    // ------------------------------------------------------------- save

    private Schedule collect() {
        Schedule s = editing != null ? editing : new Schedule();
        String name = inputName.getText().toString().trim();
        s.name = TextUtils.isEmpty(name) ? (steps.isEmpty() ? "Schedule" : steps.get(0).describe()) : name;
        s.hour = hour;
        s.minute = minute;
        s.repeat = repeat;
        s.daysMask = daysMask;
        s.method = method;
        s.command = inputCommand.getText().toString().trim();
        s.steps = new ArrayList<>(steps);
        s.enabled = editing == null || editing.enabled;
        return s;
    }

    private void save() {
        if (steps.isEmpty()) {
            Toast.makeText(this, "Add at least one action", Toast.LENGTH_SHORT).show();
            return;
        }
        Schedule s = collect();
        Db db = Db.get(this);
        if (editing == null) s.id = db.insert(s);       // the alarm, the run and the list row all key on this id
        else db.update(s);
        Scheduler.schedule(this, s);
        EngineService.ensureRunning(this);
        Toast.makeText(this, "Saved \u2022 next run " + Scheduler.nextText(this, s), Toast.LENGTH_LONG).show();
        if (touchesInternet() && !Prefs.vpnConsent(this)) {
            Toast.makeText(this, "One tap makes internet steps real \u2014 opening the permission", Toast.LENGTH_LONG).show();
            startActivity(VpnConsentActivity.intent(this));
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHint();
    }
}
