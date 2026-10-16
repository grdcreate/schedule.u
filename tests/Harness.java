import com.controlscheduler.app.*;
import java.util.*;

/** Runs the real app logic classes on the JVM (no Android runtime needed for these paths). */
public class Harness {
    static int pass = 0, fail = 0;

    static void check(String what, boolean ok, String detail) {
        System.out.printf("%-52s %s   %s%n", what, ok ? "PASS" : "FAIL", detail);
        if (ok) pass++; else fail++;
    }

    static String fmt(long t) {
        return new java.text.SimpleDateFormat("EEE dd MMM HH:mm", Locale.US).format(new Date(t));
    }

    public static void main(String[] a) {
        Calendar now = Calendar.getInstance();
        System.out.println("now = " + fmt(now.getTimeInMillis()) + "\n");

        // 1 - daily schedule
        Schedule s = new Schedule(); s.hour = 23; s.minute = 30; s.repeat = 1;
        long t = Scheduler.nextTrigger(s);
        check("daily 23:30 fires in the future", t > System.currentTimeMillis(), fmt(t));

        // 2 - a time already past today must roll to tomorrow
        Calendar past = Calendar.getInstance();
        past.add(Calendar.HOUR_OF_DAY, -1);
        Schedule p = new Schedule(); p.hour = past.get(Calendar.HOUR_OF_DAY); p.minute = 0; p.repeat = 1;
        long tp = Scheduler.nextTrigger(p);
        check("past time rolls to next day", tp - System.currentTimeMillis() > 20 * 3600_000L, fmt(tp));

        // 3 - weekdays only: result must land Mon-Fri
        Schedule w = new Schedule(); w.hour = 9; w.repeat = 2;
        Calendar cw = Calendar.getInstance(); cw.setTimeInMillis(Scheduler.nextTrigger(w));
        int dow = cw.get(Calendar.DAY_OF_WEEK);
        check("weekdays 09:00 lands Mon-Fri", dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY, fmt(cw.getTimeInMillis()));

        // 4 - weekend rule
        Schedule we = new Schedule(); we.hour = 9; we.repeat = 3;
        Calendar cwe = Calendar.getInstance(); cwe.setTimeInMillis(Scheduler.nextTrigger(we));
        int d2 = cwe.get(Calendar.DAY_OF_WEEK);
        check("weekends land Sat/Sun", d2 == Calendar.SATURDAY || d2 == Calendar.SUNDAY, fmt(cwe.getTimeInMillis()));

        // 5 - custom day mask (Sunday only)
        Schedule cu = new Schedule(); cu.hour = 9; cu.repeat = 4; cu.daysMask = 1;   // bit0 = Sunday
        Calendar ccu = Calendar.getInstance(); ccu.setTimeInMillis(Scheduler.nextTrigger(cu));
        check("custom mask picks Sunday only", ccu.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY, fmt(ccu.getTimeInMillis()));

        // 6 - custom mask of zeros must not hang (falls back after the 8-day scan)
        Schedule cz = new Schedule(); cz.repeat = 4; cz.daysMask = 0;
        long tz = Scheduler.nextTrigger(cz);
        check("empty day mask terminates safely", tz > System.currentTimeMillis(), fmt(tz));

        // 7 - multi-action stack survives a DB round trip
        Schedule x = new Schedule();
        x.steps.add(new Step(Action.WIFI_OFF, 0));
        x.steps.add(new Step(Action.DATA_OFF, 0));
        x.steps.add(new Step(Action.DND_ON, 0));
        x.steps.add(new Step(Action.BRIGHTNESS, 12));
        String ser = x.stepsSerialized();
        Schedule y = new Schedule(); y.stepsFromSerialized(ser);
        boolean same = y.steps.size() == 4 && y.steps.get(3).level == 12
                && y.steps.get(1).action == Action.DATA_OFF && y.steps.get(0).action == Action.WIFI_OFF;
        check("step stack serialises + parses", same, ser);

        // 8 - v1 database rows must migrate to the right v2 actions (was: silently became flashlight)
        boolean legacyOk = Action.of("WIFI_SETTINGS") == Action.OPEN_WIFI_SETTINGS
                && Action.of("MOBILE_DATA") == Action.DATA_OFF
                && Action.of("CUSTOM_COMMAND") == Action.COMMAND
                && Action.of("AIRPLANE") == Action.AIRPLANE_ON
                && Action.of("BLUETOOTH") == Action.BT_OFF
                && Action.of("LOCATION") == Action.LOCATION_OFF;
        check("v1 action names migrate correctly", legacyOk,
                Action.of("WIFI_SETTINGS") + "/" + Action.of("MOBILE_DATA") + "/" + Action.of("CUSTOM_COMMAND"));

        // 9 - unknown junk must not crash the parser
        check("unknown action name fails safe", Action.of("__nope__") == Action.FLASHLIGHT_ON, "-> FLASHLIGHT_ON");

        // 10 - no-privilege fallbacks point at sensible screens
        check("Bluetooth fallback -> Bluetooth screen",
                Action.BT_OFF.fallbackScreen() == Action.OPEN_BLUETOOTH_SETTINGS,
                String.valueOf(Action.BT_OFF.fallbackScreen()));
        check("data off is kill-switchable (non-screen) action",
                Action.DATA_OFF.fallbackScreen() != null, "screen also available as last resort");

        // 11 - next trigger is stable / idempotent (no drift on repeated calls)
        Schedule rep = new Schedule(); rep.hour = 6; rep.minute = 15; rep.repeat = 1;
        check("next trigger is deterministic", Scheduler.nextTrigger(rep) == Scheduler.nextTrigger(rep),
                fmt(Scheduler.nextTrigger(rep)));

        // 12 - every action has a label, tag and a sane description for the UI
        boolean allGood = true; String bad = "";
        for (Action act : Action.values()) {
            if (act.label == null || act.tag == null || act.tag.length() != 2 || act.group == null) {
                allGood = false; bad = act.name();
            }
        }
        check("all " + Action.values().length + " actions well formed", allGood, bad);

        // 13 - first-run presets are complete and sane
        java.util.List<Schedule> presets = Presets.definitions();
        Schedule night = presets.get(0), morning = presets.get(1);
        boolean nightOk = night.hour == 23 && night.minute == 30 && night.repeat == 1
                && night.steps.size() == 2
                && night.steps.get(0).action == Action.INTERNET_OFF
                && night.steps.get(1).action == Action.DND_ON;
        check("preset: Sleep mode 23:30 internet-off + DND", nightOk, night.name + " " + night.stepsText());

        boolean morningOk = morning.hour == 7 && morning.minute == 0 && morning.repeat == 1
                && morning.steps.get(0).action == Action.INTERNET_ON
                && morning.steps.get(1).action == Action.DND_OFF
                && morning.steps.get(2).action == Action.RINGER_NORMAL;
        check("preset: Good morning 07:00 internet-on", morningOk, morning.name + " " + morning.stepsText());

        long tn = Scheduler.nextTrigger(night);
        long tm = Scheduler.nextTrigger(morning);
        Calendar cn = Calendar.getInstance(); cn.setTimeInMillis(tn);
        Calendar cm = Calendar.getInstance(); cm.setTimeInMillis(tm);
        check("presets land at 23:30 and 07:00", cn.get(Calendar.HOUR_OF_DAY) == 23 && cn.get(Calendar.MINUTE) == 30
                && cm.get(Calendar.HOUR_OF_DAY) == 7 && cm.get(Calendar.MINUTE) == 0,
                fmt(tn) + " / " + fmt(tm));

        // 14 - the timer's AM/PM option
        check("12h label 23:30 -> 11:30 PM", TimeFmt.h12(23, 30).equals("11:30 PM"), TimeFmt.h12(23, 30));
        check("12h label 07:00 -> 7:00 AM", TimeFmt.h12(7, 0).equals("7:00 AM"), TimeFmt.h12(7, 0));
        check("midnight is 12:30 AM, noon is 12:00 PM",
                TimeFmt.h12(0, 30).equals("12:30 AM") && TimeFmt.h12(12, 0).equals("12:00 PM"),
                TimeFmt.h12(0, 30) + " / " + TimeFmt.h12(12, 0));
        check("clock face next to the AM/PM button",
                TimeFmt.clockPart(23, 30).equals("11:30") && TimeFmt.clockPart(7, 5).equals("7:05"),
                TimeFmt.clockPart(23, 30) + " / " + TimeFmt.clockPart(7, 5));
        check("format switch keeps both readings",
                TimeFmt.label(23, 30, true).equals("11:30 PM") && TimeFmt.label(23, 30, false).equals("23:30"),
                TimeFmt.label(23, 30, true) + " / " + TimeFmt.label(23, 30, false));
        check("AM/PM buttons move the half-day, not the face",
                TimeFmt.setMeridiem(23, false) == 11 && TimeFmt.setMeridiem(7, true) == 19,
                "11:30 PM + AM -> 11:30 AM, 7:00 AM + PM -> 7:00 PM");
        check("noon / midnight edge cases survive the AM/PM buttons",
                TimeFmt.setMeridiem(12, true) == 12 && TimeFmt.setMeridiem(12, false) == 0
                        && TimeFmt.setMeridiem(0, true) == 12 && TimeFmt.setMeridiem(0, false) == 0,
                "12 PM stays 12, 12 AM -> 0, 0 + PM -> 12");

        // 15 - missed-run catch-up (the safety net behind "the time passed and nothing happened")
        long nowMs = System.currentTimeMillis();
        Schedule catchProbe = new Schedule(); catchProbe.hour = 23; catchProbe.minute = 30; catchProbe.repeat = 1;
        long prev = Scheduler.previousTrigger(catchProbe);
        check("previous occurrence sits in the past",
                prev <= nowMs && prev > nowMs - 25 * 3600_000L, fmt(prev));
        check("previous occurrence is before the next one",
                prev < Scheduler.nextTrigger(catchProbe), fmt(prev) + " < " + fmt(Scheduler.nextTrigger(catchProbe)));
        check("a run missed 5 minutes ago is caught up",
                Scheduler.missedSince(nowMs - 5 * 60_000L, nowMs - 25 * 3600_000L, nowMs, 30 * 60_000L),
                "missed 5 min ago -> run now");
        check("a run that already fired is not repeated",
                !Scheduler.missedSince(nowMs - 5 * 60_000L, nowMs - 60_000L, nowMs, 30 * 60_000L),
                "fired 1 min ago -> nothing to do");
        check("a run missed hours ago is never replayed silently",
                !Scheduler.missedSince(nowMs - 3 * 3600_000L, 0L, nowMs, 30 * 60_000L),
                "3 h late -> left alone, logged instead");

        // 16 - the "needs attention" test behind the history dot and the LAST RUN line colour
        check("a clean step is not flagged",
                !Runner.looksFailed("Flashlight off") && !Runner.looksFailed("Ringer normal")
                        && !Runner.looksFailed("kill switch on \u2014 mobile data and Wi-Fi are offline"),
                "clean steps stay rose");
        check("missing Do Not Disturb access is flagged",
                Runner.looksFailed("Ringer: grant Do Not Disturb access in Setup")
                        && Runner.looksFailed("Do Not Disturb: grant access in Setup"),
                "grant messages -> amber");
        check("skipped / unknown runs are flagged",
                Runner.looksFailed("skipped - the engine is paused in the app")
                        && Runner.looksFailed("Unknown schedule - no schedule with id 0"),
                "skips are never silent");
        check("blocked-by-Android is flagged",
                Runner.looksFailed("Volume: Android blocked the change")
                        && Runner.looksFailed("internet-off needs one tap: allow the kill switch (notification)"),
                "blocked -> amber");

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
