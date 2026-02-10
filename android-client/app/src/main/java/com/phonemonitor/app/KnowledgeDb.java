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
 * 知识库 SQLite 数据库
 */
public class KnowledgeDb extends SQLiteOpenHelper {
    private static final String TAG = "KnowledgeDb";
    private static final String DB_NAME = "knowledge.db";
    private static final int DB_VERSION = 1;

    private static KnowledgeDb instance;

    public static synchronized KnowledgeDb getInstance(Context context) {
        if (instance == null) {
            instance = new KnowledgeDb(context.getApplicationContext());
        }
        return instance;
    }

    private KnowledgeDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE contents (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "content TEXT NOT NULL," +
                "url TEXT," +
                "type TEXT DEFAULT 'note'," +
                "source TEXT DEFAULT 'clipboard'," +
                "summary TEXT," +
                "tags TEXT," +
                "is_favorite INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))," +
                "synced INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE tags (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "color TEXT," +
                "count INTEGER DEFAULT 0)");

        // Indexes for common queries
        db.execSQL("CREATE INDEX idx_contents_type ON contents(type)");
        db.execSQL("CREATE INDEX idx_contents_created ON contents(created_at DESC)");
        db.execSQL("CREATE INDEX idx_contents_synced ON contents(synced)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations go here
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    // ==================== CRUD ====================

    /**
     * 插入内容
     */
    public long insertContent(String title, String content, String url,
                              String type, String source, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("content", content);
        cv.put("url", url);
        cv.put("type", type != null ? type : "note");
        cv.put("source", source != null ? source : "clipboard");
        cv.put("tags", tags);
        cv.put("created_at", now());
        cv.put("updated_at", now());

        long id = db.insert("contents", null, cv);
        if (id > 0) {
            Log.i(TAG, "✅ 内容已保存 #" + id + " [" + type + "]");
            updateTagCounts(tags);
        }
        return id;
    }

    /**
     * 获取所有内容（分页）
     */
    public List<ContentItem> getAllContents(int limit, int offset) {
        return queryContents(
                "SELECT * FROM contents ORDER BY created_at DESC LIMIT ? OFFSET ?",
                new String[]{String.valueOf(limit), String.valueOf(offset)});
    }

    /**
     * 搜索内容
     */
    public List<ContentItem> searchContents(String query) {
        String like = "%" + query + "%";
        return queryContents(
                "SELECT * FROM contents WHERE title LIKE ? OR content LIKE ? OR tags LIKE ? ORDER BY created_at DESC",
                new String[]{like, like, like});
    }

    /**
     * 按 ID 获取
     */
    public ContentItem getContentById(long id) {
        List<ContentItem> items = queryContents(
                "SELECT * FROM contents WHERE id = ?",
                new String[]{String.valueOf(id)});
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * 更新内容
     */
    public boolean updateContent(long id, String title, String content, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("content", content);
        cv.put("tags", tags);
        cv.put("updated_at", now());
        return db.update("contents", cv, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * 更新标题
     */
    public boolean updateTitle(long id, String title) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("updated_at", now());
        return db.update("contents", cv, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * 删除内容
     */
    public boolean deleteContent(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("contents", "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * 切换收藏
     */
    public boolean toggleFavorite(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE contents SET is_favorite = 1 - is_favorite, updated_at = ? WHERE id = ?",
                new Object[]{now(), id});
        return true;
    }

    /**
     * 内容总数
     */
    public int getContentCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM contents", null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    /**
     * 最近内容
     */
    public List<ContentItem> getRecentContents(int limit) {
        return queryContents(
                "SELECT * FROM contents ORDER BY created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
    }

    /**
     * 按类型筛选
     */
    public List<ContentItem> getContentsByType(String type) {
        return queryContents(
                "SELECT * FROM contents WHERE type = ? ORDER BY created_at DESC",
                new String[]{type});
    }

    /**
     * 获取未同步内容
     */
    public List<ContentItem> getUnsyncedContents() {
        return queryContents(
                "SELECT * FROM contents WHERE synced = 0 ORDER BY created_at ASC",
                null);
    }

    /**
     * 标记已同步
     */
    public void markSynced(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("synced", 1);
        db.update("contents", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    // ==================== Internal ====================

    private List<ContentItem> queryContents(String sql, String[] args) {
        List<ContentItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(sql, args);
        while (cursor.moveToNext()) {
            items.add(new ContentItem(cursor));
        }
        cursor.close();
        return items;
    }

    private void updateTagCounts(String tags) {
        if (tags == null || tags.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        for (String tag : tags.split(",")) {
            tag = tag.trim();
            if (tag.isEmpty()) continue;
            db.execSQL("INSERT OR IGNORE INTO tags (name, count) VALUES (?, 0)", new Object[]{tag});
            db.execSQL("UPDATE tags SET count = count + 1 WHERE name = ?", new Object[]{tag});
        }
    }
}
