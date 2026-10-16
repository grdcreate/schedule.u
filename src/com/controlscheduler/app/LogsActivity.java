package com.controlscheduler.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Every scheduled run, newest first - proof of what fired while you slept. */
public class LogsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_logs);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        LinearLayout list = findViewById(R.id.logs_container);
        List<Db.Run> runs = Db.get(this).recentRuns(40);
        if (runs.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Nothing has fired yet.\nScheduled runs will show up here.");
            tv.setTextColor(Ui.TEXT3);
            tv.setTextSize(13f);
            tv.setPadding(0, Ui.dp(this, 20), 0, 0);
            list.addView(tv);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
        for (Db.Run r : runs) {
            View row = getLayoutInflater().inflate(R.layout.item_log, list, false);
            ((TextView) row.findViewById(R.id.log_title)).setText(r.name);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(r.ts);
            ((TextView) row.findViewById(R.id.log_when)).setText(fmt.format(new Date(r.ts)) + ", "
                    + Ui.time(this, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));
            ((TextView) row.findViewById(R.id.log_body)).setText(r.summary);
            ((TextView) row.findViewById(R.id.log_dot)).setTextColor(r.ok ? Ui.ACCENT : Ui.WARN);
            list.addView(row);
        }
    }
}
