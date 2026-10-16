package com.controlscheduler.app;

import java.util.ArrayList;
import java.util.List;

public class Schedule {
    public long id = 0;
    public String name = "";
    public boolean enabled = true;
    public int hour = 23;
    public int minute = 0;
    public int repeat = 1;             // 0 once, 1 daily, 2 weekdays, 3 weekends, 4 custom
    public int daysMask = 0b1111111;   // bit0 Sunday .. bit6 Saturday
    public int method = 0;             // 0 auto, 1 root, 2 Shizuku, 3 kill switch, 4 screens only
    public String command = "";
    public List<Step> steps = new ArrayList<>();

    public static final String[] REPEAT_LABELS = {"Once", "Daily", "Weekdays", "Weekends", "Custom"};
    public static final String[] METHOD_LABELS = {"Auto", "Root", "Shizuku", "Kill switch", "Screens"};

    public String timeText() {
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
    }

    public String repeatText() {
        return REPEAT_LABELS[Math.max(0, Math.min(4, repeat))];
    }

    public String methodText() {
        return METHOD_LABELS[Math.max(0, Math.min(4, method))];
    }

    public String stepsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) sb.append(" \u2022 ");
            sb.append(steps.get(i).describe());
        }
        return sb.toString();
    }

    public String stepsSerialized() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(steps.get(i).serialize());
        }
        return sb.toString();
    }

    public void stepsFromSerialized(String raw) {
        steps = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String part : raw.split(";")) {
            if (part.trim().isEmpty()) continue;
            steps.add(Step.parse(part.trim()));
        }
    }

    public Action firstAction() {
        return steps.isEmpty() ? Action.FLASHLIGHT_ON : steps.get(0).action;
    }

    public boolean needsSpecialAccess() {
        for (Step s : steps) {
            if (s.action.kind == Action.Kind.PRIV || s.action.kind == Action.Kind.VPN
                    || s.action.kind == Action.Kind.CMD) return true;
        }
        return false;
    }
}
