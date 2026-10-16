package com.controlscheduler.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fired by the exact alarm. The work is handed to the engine service when the
 * user keeps it running (long action stacks can't ANR a receiver that way);
 * otherwise it runs here on a background thread.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final long id = intent.getLongExtra(Scheduler.EXTRA_ID, -1);
        if (id < 0) return;

        if (Prefs.serviceWanted(context) && EngineService.start(context, EngineService.ACTION_RUN, id)) {
            return;   // the service owns execution and rescheduling
        }

        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    EngineService.executeSchedule(context, id);
                } finally {
                    pr.finish();
                }
            }
        }, "scheduler-run").start();
    }
}
