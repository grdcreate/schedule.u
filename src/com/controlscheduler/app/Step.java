package com.controlscheduler.app;

/** One action inside a schedule, with an optional level (volume / brightness). */
public class Step {
    public Action action;
    public int level = 60;

    public Step(Action a, int level) { this.action = a; this.level = level; }

    public static Step parse(String part) {
        int i = part.indexOf('|');
        String name = i < 0 ? part : part.substring(0, i);
        int lvl = 60;
        if (i >= 0) {
            try { lvl = Integer.parseInt(part.substring(i + 1)); } catch (Throwable ignored) { }
        }
        return new Step(Action.of(name), lvl);
    }

    public String serialize() {
        return action.name() + "|" + level;
    }

    public String describe() {
        if (action == Action.VOLUME) return "Volume " + level + "%";
        if (action == Action.BRIGHTNESS) return "Brightness " + level + "%";
        return action.label;
    }
}
