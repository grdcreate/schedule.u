package com.controlscheduler.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Two schedules are ready the moment the app is first opened, so the phone
 * actually goes offline at night and comes back in the morning without setup:
 *
 *   23:30  Sleep mode   -> internet off (kill switch) + Do Not Disturb on
 *   07:00  Good morning -> internet on + Do Not Disturb off + ringer normal
 */
public class Presets {

    public static List<Schedule> definitions() {
        List<Schedule> out = new ArrayList<>();

        Schedule night = new Schedule();
        night.name = "Sleep mode";
        night.hour = 23;
        night.minute = 30;
        night.repeat = 1;                 // daily
        night.method = 0;                 // auto engine
        night.steps.add(new Step(Action.INTERNET_OFF, 0));
        night.steps.add(new Step(Action.DND_ON, 0));

        Schedule morning = new Schedule();
        morning.name = "Good morning";
        morning.hour = 7;
        morning.minute = 0;
        morning.repeat = 1;               // daily
        morning.method = 0;
        morning.steps.add(new Step(Action.INTERNET_ON, 0));
        morning.steps.add(new Step(Action.DND_OFF, 0));
        morning.steps.add(new Step(Action.RINGER_NORMAL, 0));

        out.add(night);
        out.add(morning);
        return out;
    }

    /** @return true when the presets were just created. */
    public static boolean seedIfFirstRun(Context ctx) {
        if (Prefs.seeded(ctx)) return false;
        Db db = Db.get(ctx);
        for (Schedule s : definitions()) {
            long id = db.insert(s);
            s.id = id;
            Scheduler.schedule(ctx, s);
        }
        Prefs.setSeeded(ctx, true);
        return true;
    }
}
