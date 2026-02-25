package com.phonemonitor.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GrowthGoalDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "growth_goals.db";
    private static final int DB_VERSION = 1;

    private static GrowthGoalDb instance;

    public static synchronized GrowthGoalDb getInstance(Context context) {
        if (instance == null) {
            instance = new GrowthGoalDb(context.getApplicationContext());
        }
        return instance;
    }

    private GrowthGoalDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE goals (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "goal_type TEXT NOT NULL," +
                "target_value INTEGER NOT NULL," +
                "current_value INTEGER DEFAULT 0," +
                "unit TEXT," +
                "start_date TEXT," +
                "end_date TEXT," +
                "status TEXT DEFAULT 'active'," +
                "created_at TEXT DEFAULT (datetime('now','localtime')))");

        db.execSQL("CREATE TABLE goal_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "goal_id INTEGER NOT NULL," +
                "date TEXT NOT NULL," +
                "value INTEGER DEFAULT 0," +
                "met INTEGER DEFAULT 0," +
                "UNIQUE(goal_id, date))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public long insertGoal(String goalType, int targetValue, String unit) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("goal_type", goalType);
        cv.put("target_value", targetValue);
        cv.put("unit", unit);
        cv.put("start_date", today());
        cv.put("status", "active");
        return db.insert("goals", null, cv);
    }

    public void updateGoal(long id, int targetValue) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("target_value", targetValue);
        db.update("goals", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void updateGoalCurrentValue(long id, int currentValue) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("current_value", currentValue);
        db.update("goals", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteGoal(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("goals", "id = ?", new String[]{String.valueOf(id)});
        db.delete("goal_history", "goal_id = ?", new String[]{String.valueOf(id)});
    }

    public List<Goal> getActiveGoals() {
        List<Goal> goals = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM goals WHERE status = 'active' ORDER BY created_at DESC", null);
        while (cursor.moveToNext()) {
            goals.add(new Goal(cursor));
        }
        cursor.close();
        return goals;
    }

    public Goal getGoalByType(String goalType) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM goals WHERE goal_type = ? AND status = 'active' LIMIT 1",
                new String[]{goalType});
        Goal goal = null;
        if (cursor.moveToFirst()) {
            goal = new Goal(cursor);
        }
        cursor.close();
        return goal;
    }

    public void recordHistory(long goalId, String date, int value, boolean met) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("goal_id", goalId);
        cv.put("date", date);
        cv.put("value", value);
        cv.put("met", met ? 1 : 0);
        db.insertWithOnConflict("goal_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<GoalHistory> getGoalHistory(long goalId, int days) {
        List<GoalHistory> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM goal_history WHERE goal_id = ? ORDER BY date DESC LIMIT ?",
                new String[]{String.valueOf(goalId), String.valueOf(days)});
        while (cursor.moveToNext()) {
            list.add(new GoalHistory(cursor));
        }
        cursor.close();
        return list;
    }

    public int getStreakDays(long goalId) {
        List<GoalHistory> history = getGoalHistory(goalId, 30);
        int streak = 0;
        for (GoalHistory h : history) {
            if (h.met) streak++;
            else break;
        }
        return streak;
    }

    public static class Goal {
        public long id;
        public String goalType;
        public int targetValue;
        public int currentValue;
        public String unit;
        public String startDate;
        public String endDate;
        public String status;

        public Goal(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            this.goalType = cursor.getString(cursor.getColumnIndexOrThrow("goal_type"));
            this.targetValue = cursor.getInt(cursor.getColumnIndexOrThrow("target_value"));
            this.currentValue = cursor.getInt(cursor.getColumnIndexOrThrow("current_value"));
            this.unit = cursor.getString(cursor.getColumnIndexOrThrow("unit"));
            this.startDate = cursor.getString(cursor.getColumnIndexOrThrow("start_date"));
            this.endDate = cursor.getString(cursor.getColumnIndexOrThrow("end_date"));
            this.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
        }
    }

    public static class GoalHistory {
        public long id;
        public long goalId;
        public String date;
        public int value;
        public boolean met;

        public GoalHistory(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            this.goalId = cursor.getLong(cursor.getColumnIndexOrThrow("goal_id"));
            this.date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
            this.value = cursor.getInt(cursor.getColumnIndexOrThrow("value"));
            this.met = cursor.getInt(cursor.getColumnIndexOrThrow("met")) == 1;
        }
    }
}
