package com.phonemonitor.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * App 使用统计数据库
 * 保留所有历史数据，支持趋势分析和周规律统计
 */
public class UsageStatsDb extends SQLiteOpenHelper {
    private static final String TAG = "UsageStatsDb";
    private static final String DB_NAME = "usage_stats.db";
    private static final int DB_VERSION = 1;

    private static UsageStatsDb instance;

    public static synchronized UsageStatsDb getInstance(Context context) {
        if (instance == null) {
            instance = new UsageStatsDb(context.getApplicationContext());
        }
        return instance;
    }

    private UsageStatsDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 每日应用使用记录
        db.execSQL("CREATE TABLE daily_usage (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL," +
                "package_name TEXT NOT NULL," +
                "app_name TEXT," +
                "category TEXT," +
                "usage_ms INTEGER DEFAULT 0," +
                "launch_count INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "UNIQUE(date, package_name))");

        // 每日汇总统计
        db.execSQL("CREATE TABLE daily_summary (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL UNIQUE," +
                "total_usage_ms INTEGER DEFAULT 0," +
                "total_apps INTEGER DEFAULT 0," +
                "top_app TEXT," +
                "top_category TEXT," +
                "created_at TEXT DEFAULT (datetime('now','localtime')))");

        // 索引优化查询
        db.execSQL("CREATE INDEX idx_daily_usage_date ON daily_usage(date DESC)");
        db.execSQL("CREATE INDEX idx_daily_usage_package ON daily_usage(package_name)");
        db.execSQL("CREATE INDEX idx_daily_summary_date ON daily_summary(date DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // ==================== 数据插入 ====================

    /**
     * 插入或更新每日应用使用记录
     */
    public void upsertDailyUsage(String date, String packageName, String appName,
                                  String category, long usageMs, int launchCount) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("date", date);
        cv.put("package_name", packageName);
        cv.put("app_name", appName);
        cv.put("category", category);
        cv.put("usage_ms", usageMs);
        cv.put("launch_count", launchCount);
        cv.put("created_at", now());

        long result = db.insertWithOnConflict("daily_usage", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (result > 0) {
            Log.d(TAG, "✅ 已保存使用记录: " + date + " " + appName + " " + (usageMs / 60000) + "min");
        }
    }

    /**
     * 插入每日汇总
     */
    public void insertDailySummary(String date, long totalUsageMs, int totalApps,
                                    String topApp, String topCategory) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("date", date);
        cv.put("total_usage_ms", totalUsageMs);
        cv.put("total_apps", totalApps);
        cv.put("top_app", topApp);
        cv.put("top_category", topCategory);
        cv.put("created_at", now());

        db.insertWithOnConflict("daily_summary", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ==================== 查询 ====================

    /**
     * 获取指定日期的应用使用记录
     */
    public List<AppUsageRecord> getDailyUsage(String date) {
        List<AppUsageRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM daily_usage WHERE date = ? ORDER BY usage_ms DESC",
                new String[]{date});

        while (cursor.moveToNext()) {
            records.add(new AppUsageRecord(cursor));
        }
        cursor.close();
        return records;
    }

    /**
     * 获取指定日期的汇总统计
     */
    public DailySummary getDailySummary(String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM daily_summary WHERE date = ?",
                new String[]{date});

        DailySummary summary = null;
        if (cursor.moveToFirst()) {
            summary = new DailySummary(cursor);
        }
        cursor.close();
        return summary;
    }

    /**
     * 获取最近 N 天的汇总统计
     */
    public List<DailySummary> getRecentSummaries(int days) {
        List<DailySummary> summaries = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM daily_summary ORDER BY date DESC LIMIT ?",
                new String[]{String.valueOf(days)});

        while (cursor.moveToNext()) {
            summaries.add(new DailySummary(cursor));
        }
        cursor.close();
        return summaries;
    }

    /**
     * 获取指定应用的历史趋势（最近 N 天）
     */
    public List<AppUsageRecord> getAppTrend(String packageName, int days) {
        List<AppUsageRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM daily_usage WHERE package_name = ? " +
                        "ORDER BY date DESC LIMIT ?",
                new String[]{packageName, String.valueOf(days)});

        while (cursor.moveToNext()) {
            records.add(new AppUsageRecord(cursor));
        }
        cursor.close();
        return records;
    }

    /**
     * 获取指定分类的历史趋势（最近 N 天）
     */
    public List<CategoryUsage> getCategoryTrend(int days) {
        List<CategoryUsage> trends = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT date, category, SUM(usage_ms) as total_ms " +
                        "FROM daily_usage " +
                        "GROUP BY date, category " +
                        "ORDER BY date DESC, total_ms DESC " +
                        "LIMIT ?",
                new String[]{String.valueOf(days * 10)});

        while (cursor.moveToNext()) {
            trends.add(new CategoryUsage(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getLong(2)
            ));
        }
        cursor.close();
        return trends;
    }

    /**
     * 获取周规律统计（工作日 vs 周末）
     */
    public WeeklyPattern getWeeklyPattern() {
        SQLiteDatabase db = getReadableDatabase();
        
        // 最近 7 天的平均使用时长
        Cursor cursor = db.rawQuery(
                "SELECT AVG(total_usage_ms) FROM daily_summary " +
                        "WHERE date >= date('now', '-7 days')",
                null);
        
        long weekAvg = 0;
        if (cursor.moveToFirst()) {
            weekAvg = cursor.getLong(0);
        }
        cursor.close();

        // 工作日平均（周一到周五）
        cursor = db.rawQuery(
                "SELECT AVG(total_usage_ms) FROM daily_summary " +
                        "WHERE date >= date('now', '-30 days') " +
                        "AND CAST(strftime('%w', date) AS INTEGER) BETWEEN 1 AND 5",
                null);
        
        long weekdayAvg = 0;
        if (cursor.moveToFirst()) {
            weekdayAvg = cursor.getLong(0);
        }
        cursor.close();

        // 周末平均（周六周日）
        cursor = db.rawQuery(
                "SELECT AVG(total_usage_ms) FROM daily_summary " +
                        "WHERE date >= date('now', '-30 days') " +
                        "AND CAST(strftime('%w', date) AS INTEGER) IN (0, 6)",
                null);
        
        long weekendAvg = 0;
        if (cursor.moveToFirst()) {
            weekendAvg = cursor.getLong(0);
        }
        cursor.close();

        // 峰值日期
        cursor = db.rawQuery(
                "SELECT date, total_usage_ms FROM daily_summary " +
                        "WHERE date >= date('now', '-7 days') " +
                        "ORDER BY total_usage_ms DESC LIMIT 1",
                null);
        
        String peakDate = "";
        long peakUsage = 0;
        if (cursor.moveToFirst()) {
            peakDate = cursor.getString(0);
            peakUsage = cursor.getLong(1);
        }
        cursor.close();

        return new WeeklyPattern(weekAvg, weekdayAvg, weekendAvg, peakDate, peakUsage);
    }

    // ==================== 数据模型 ====================

    public static class AppUsageRecord {
        public long id;
        public String date;
        public String packageName;
        public String appName;
        public String category;
        public long usageMs;
        public int launchCount;

        public AppUsageRecord(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            this.date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
            this.packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
            this.appName = cursor.getString(cursor.getColumnIndexOrThrow("app_name"));
            this.category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
            this.usageMs = cursor.getLong(cursor.getColumnIndexOrThrow("usage_ms"));
            this.launchCount = cursor.getInt(cursor.getColumnIndexOrThrow("launch_count"));
        }
    }

    public static class DailySummary {
        public long id;
        public String date;
        public long totalUsageMs;
        public int totalApps;
        public String topApp;
        public String topCategory;

        public DailySummary(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            this.date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
            this.totalUsageMs = cursor.getLong(cursor.getColumnIndexOrThrow("total_usage_ms"));
            this.totalApps = cursor.getInt(cursor.getColumnIndexOrThrow("total_apps"));
            this.topApp = cursor.getString(cursor.getColumnIndexOrThrow("top_app"));
            this.topCategory = cursor.getString(cursor.getColumnIndexOrThrow("top_category"));
        }
    }

    public static class CategoryUsage {
        public String date;
        public String category;
        public long totalMs;

        public CategoryUsage(String date, String category, long totalMs) {
            this.date = date;
            this.category = category;
            this.totalMs = totalMs;
        }
    }

    public static class WeeklyPattern {
        public long weekAvg;
        public long weekdayAvg;
        public long weekendAvg;
        public String peakDate;
        public long peakUsage;

        public WeeklyPattern(long weekAvg, long weekdayAvg, long weekendAvg,
                             String peakDate, long peakUsage) {
            this.weekAvg = weekAvg;
            this.weekdayAvg = weekdayAvg;
            this.weekendAvg = weekendAvg;
            this.peakDate = peakDate;
            this.peakUsage = peakUsage;
        }
    }
}
