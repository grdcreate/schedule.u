package com.controlscheduler.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {

    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "control_scheduler.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE schedules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, action TEXT, enabled INTEGER," +
                "hour INTEGER, minute INTEGER, repeat INTEGER, days_mask INTEGER," +
                "level INTEGER, command TEXT, steps TEXT, method INTEGER)");
        db.execSQL("CREATE TABLE runs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER, schedule_name TEXT, summary TEXT, ok INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            try { db.execSQL("ALTER TABLE schedules ADD COLUMN steps TEXT"); } catch (Throwable ignored) { }
            try { db.execSQL("ALTER TABLE schedules ADD COLUMN method INTEGER DEFAULT 0"); } catch (Throwable ignored) { }
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS runs (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "ts INTEGER, schedule_name TEXT, summary TEXT, ok INTEGER)");
            } catch (Throwable ignored) { }
            // migrate old single-action rows into the new steps format
            try {
                db.execSQL("UPDATE schedules SET steps = action || '|' || level WHERE steps IS NULL OR steps = ''");
            } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------- schedules

    private static final String COLS = "id,name,action,enabled,hour,minute,repeat,days_mask,level,command,steps,method";

    private Schedule read(Cursor c) {
        Schedule s = new Schedule();
        s.id = c.getLong(0);
        s.name = c.getString(1) == null ? "" : c.getString(1);
        s.enabled = c.getInt(3) == 1;
        s.hour = c.getInt(4);
        s.minute = c.getInt(5);
        s.repeat = c.getInt(6);
        s.daysMask = c.getInt(7);
        s.command = c.getString(9) == null ? "" : c.getString(9);
        String steps = c.getString(10);
        if (steps == null || steps.trim().isEmpty()) {
            // legacy row: single action + level
            s.steps = new ArrayList<>();
            s.steps.add(new Step(Action.of(c.getString(2)), c.getInt(8)));
        } else {
            s.stepsFromSerialized(steps);
        }
        s.method = c.getInt(11);
        return s;
    }

    private ContentValues write(Schedule s) {
        ContentValues v = new ContentValues();
        v.put("name", s.name);
        v.put("action", s.firstAction().name());
        v.put("enabled", s.enabled ? 1 : 0);
        v.put("hour", s.hour);
        v.put("minute", s.minute);
        v.put("repeat", s.repeat);
        v.put("days_mask", s.daysMask);
        v.put("level", s.steps.isEmpty() ? 60 : s.steps.get(0).level);
        v.put("command", s.command);
        v.put("steps", s.stepsSerialized());
        v.put("method", s.method);
        return v;
    }

    public List<Schedule> all() {
        List<Schedule> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM schedules ORDER BY hour, minute", null);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    public List<Schedule> enabled() {
        List<Schedule> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM schedules WHERE enabled=1 ORDER BY hour, minute", null);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    public Schedule byId(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM schedules WHERE id=?",
                new String[]{String.valueOf(id)});
        Schedule s = c.moveToNext() ? read(c) : null;
        c.close();
        return s;
    }

    public long insert(Schedule s) {
        long id = getWritableDatabase().insert("schedules", null, write(s));
        // the caller needs the real id: it keys the alarm, the run and the list row
        if (id > 0) s.id = id;
        return id;
    }

    public void update(Schedule s) {
        getWritableDatabase().update("schedules", write(s), "id=?", new String[]{String.valueOf(s.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete("schedules", "id=?", new String[]{String.valueOf(id)});
    }

    public void setEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().update("schedules", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------- run log

    public void log(String name, String summary, boolean ok) {
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("schedule_name", name);
        v.put("summary", summary);
        v.put("ok", ok ? 1 : 0);
        getWritableDatabase().insert("runs", null, v);
        getWritableDatabase().execSQL("DELETE FROM runs WHERE id NOT IN (SELECT id FROM runs ORDER BY id DESC LIMIT 40)");
    }

    public static class Run {
        public long ts;
        public String name;
        public String summary;
        public boolean ok;
    }

    public List<Run> recentRuns(int limit) {
        List<Run> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT ts,schedule_name,summary,ok FROM runs ORDER BY id DESC LIMIT " + limit, null);
        while (c.moveToNext()) {
            Run r = new Run();
            r.ts = c.getLong(0);
            r.name = c.getString(1);
            r.summary = c.getString(2);
            r.ok = c.getInt(3) == 1;
            out.add(r);
        }
        c.close();
        return out;
    }
}
