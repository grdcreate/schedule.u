package com.controlscheduler.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores alarms and the engine service after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.rescheduleAll(context);
        Shell.reset();
        if (Prefs.armed(context) || Prefs.internetBlocked(context)) {
            EngineService.start(context, EngineService.ACTION_ARM);
        }
    }
}
