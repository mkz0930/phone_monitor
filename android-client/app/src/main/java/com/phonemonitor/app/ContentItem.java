package com.phonemonitor.app;

import android.database.Cursor;

/**
 * 知识库内容数据模型
 */
public class ContentItem {
    public long id;
    public String title;
    public String content;
    public String url;
    public String type;       // note/article/link/code/other
    public String source;     // clipboard/manual/notification
    public String summary;
    public String tags;       // comma-separated
    public boolean isFavorite;
    public String createdAt;
    public String updatedAt;
    public boolean synced;

    public ContentItem() {}

    public ContentItem(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        this.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        this.content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
        this.url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
        this.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        this.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
        this.summary = cursor.getString(cursor.getColumnIndexOrThrow("summary"));
        this.tags = cursor.getString(cursor.getColumnIndexOrThrow("tags"));
        this.isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1;
        this.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
        this.updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"));
        this.synced = cursor.getInt(cursor.getColumnIndexOrThrow("synced")) == 1;
    }

    public String getTypeEmoji() {
        if (type == null) return "📎";
        switch (type) {
            case "note":    return "📝";
            case "article": return "📰";
            case "link":    return "🔗";
            case "code":    return "💻";
            default:        return "📎";
        }
    }

    public String getSourceEmoji() {
        if (source == null) return "";
        switch (source) {
            case "clipboard":    return "📋";
            case "manual":       return "✍️";
            case "notification": return "🔔";
            default:             return "";
        }
    }

    public String getRelativeTime() {
        if (createdAt == null) return "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(createdAt);
            if (date == null) return createdAt;

            long diff = System.currentTimeMillis() - date.getTime();
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (seconds < 60) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            if (hours < 24) return hours + "小时前";
            if (days == 1) return "昨天";
            if (days < 7) return days + "天前";
            if (days < 30) return (days / 7) + "周前";
            return new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(date);
        } catch (Exception e) {
            return createdAt;
        }
    }

    public String getFullTimestamp() {
        if (createdAt == null) return "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(createdAt);
            if (date == null) return createdAt;
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(date);
        } catch (Exception e) {
            return createdAt;
        }
    }

    public boolean isDouyinUrl() {
        return url != null && (url.contains("v.douyin.com") || url.contains("www.douyin.com"));
    }

    public boolean hasUrl() {
        return url != null && !url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public String getPreview(int maxLen) {
        if (content == null) return "";
        String clean = content.replace("\n", " ").trim();
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen) + "…";
    }
}
