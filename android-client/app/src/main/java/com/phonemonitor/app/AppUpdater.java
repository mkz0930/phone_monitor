package com.phonemonitor.app;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub Releases 自动升级
 * 检查 mkz0930/phone_monitor 的最新 release，下载 APK 并安装
 */
public class AppUpdater {
    private static final String TAG = "AppUpdater";
    private static final String GITHUB_API = "https://api.github.com/repos/mkz0930/phone_monitor/releases/latest";
    private static final String CHANNEL_ID = "update_channel";
    private static final int NOTIFY_ID = 9527;

    /**
     * 自动检查（静默失败）
     */
    public static void checkForUpdate(Activity activity) {
        checkForUpdate(activity, false);
    }

    /**
     * 手动检查（失败时 Toast 提示）
     */
    public static void checkForUpdateManual(Activity activity) {
        checkForUpdate(activity, true);
    }

    private static void checkForUpdate(Activity activity, boolean manual) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "PhoneMonitor-Android");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    if (manual) showToast(activity, "❌ 检查失败: HTTP " + conn.getResponseCode());
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.getString("tag_name").replaceFirst("^v", "");
                String body = release.optString("body", "");
                String currentVersion = getCurrentVersion(activity);

                if (isNewer(tagName, currentVersion)) {
                    // 找 APK asset
                    String apkUrl = findApkUrl(release);
                    if (apkUrl == null) {
                        if (manual) showToast(activity, "⚠️ 新版本 v" + tagName + " 无 APK 文件");
                        return;
                    }
                    new Handler(Looper.getMainLooper()).post(() ->
                            showUpdateDialog(activity, tagName, body, apkUrl));
                } else {
                    if (manual) showToast(activity, "✅ 已是最新版本 v" + currentVersion);
                }
            } catch (Exception e) {
                if (manual) showToast(activity, "❌ 检查更新失败: " + e.getMessage());
            }
        }).start();
    }

    private static String findApkUrl(JSONObject release) {
        try {
            JSONArray assets = release.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");
                if (name.endsWith(".apk")) {
                    return asset.getString("browser_download_url");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void showUpdateDialog(Activity activity, String version, String notes, String apkUrl) {
        String message = "发现新版本 v" + version + "\n\n";
        if (!notes.isEmpty()) {
            // 截取前 500 字符避免对话框过长
            message += notes.length() > 500 ? notes.substring(0, 500) + "..." : notes;
        }

        new MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setTitle("🔄 发现新版本")
                .setMessage(message)
                .setPositiveButton("⬇️ 立即更新", (d, w) -> downloadAndInstall(activity, apkUrl, version))
                .setNegativeButton("稍后", null)
                .setCancelable(true)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl, String version) {
        createNotificationChannel(activity);

        NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载 v" + version)
                .setProgress(100, 0, false)
                .setOngoing(true);
        nm.notify(NOTIFY_ID, nb.build());

        LogBus.post("UPDATE", "⬇️ 开始下载 v" + version);

        new Thread(() -> {
            File apkFile = null;
            try {
                // 清理旧 APK
                File cacheDir = activity.getExternalCacheDir();
                if (cacheDir != null) {
                    File[] oldApks = cacheDir.listFiles((dir, name) -> name.endsWith(".apk"));
                    if (oldApks != null) {
                        for (File f : oldApks) f.delete();
                    }
                }

                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setRequestProperty("User-Agent", "PhoneMonitor-Android");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                int totalSize = conn.getContentLength();
                apkFile = new File(cacheDir, "phone_monitor_v" + version + ".apk");

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(apkFile)) {
                    byte[] buf = new byte[8192];
                    int read;
                    long downloaded = 0;
                    int lastPercent = 0;

                    while ((read = is.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        downloaded += read;
                        if (totalSize > 0) {
                            int percent = (int) (downloaded * 100 / totalSize);
                            if (percent > lastPercent) {
                                lastPercent = percent;
                                nb.setProgress(100, percent, false)
                                        .setContentText(percent + "%");
                                nm.notify(NOTIFY_ID, nb.build());
                            }
                        }
                    }
                }

                // 下载完成
                nm.cancel(NOTIFY_ID);
                LogBus.post("UPDATE", "✅ 下载完成，准备安装 v" + version);

                // 触发安装
                installApk(activity, apkFile);

            } catch (Exception e) {
                nm.cancel(NOTIFY_ID);
                LogBus.post("UPDATE", "❌ 下载失败: " + e.getMessage());
                showToast(activity, "❌ 下载失败: " + e.getMessage());
                if (apkFile != null) apkFile.delete();
            }
        }).start();
    }

    private static void installApk(Activity activity, File apkFile) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri uri = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".fileprovider", apkFile);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setDataAndType(Uri.fromFile(apkFile),
                            "application/vnd.android.package-archive");
                }

                // 检查是否有安装权限 (Android 8+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && !activity.getPackageManager().canRequestPackageInstalls()) {
                    // 引导用户开启"允许安装未知应用"
                    Intent settingsIntent = new Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(settingsIntent);
                    Toast.makeText(activity, "请先允许安装未知应用，然后重试", Toast.LENGTH_LONG).show();
                    return;
                }

                activity.startActivity(intent);
            } catch (Exception e) {
                LogBus.post("UPDATE", "❌ 安装失败: " + e.getMessage());
                Toast.makeText(activity, "❌ 安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String getCurrentVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /**
     * 语义化版本比较: remote > local 返回 true
     */
    static boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        for (int i = 0; i < 3; i++) {
            if (r[i] > l[i]) return true;
            if (r[i] < l[i]) return false;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        int[] parts = {0, 0, 0};
        try {
            String[] s = v.split("\\.");
            for (int i = 0; i < Math.min(s.length, 3); i++) {
                parts[i] = Integer.parseInt(s[i].replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        return parts;
    }

    private static void createNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "应用更新", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("APK 下载进度");
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private static void showToast(Activity activity, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }
}
