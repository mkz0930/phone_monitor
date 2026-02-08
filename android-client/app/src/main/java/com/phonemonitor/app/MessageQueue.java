package com.phonemonitor.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线消息队列：无网络时缓存消息，恢复后自动发送
 */
public class MessageQueue {
    private static final String TAG = "MessageQueue";
    private static final String QUEUE_FILE = "message_queue.json";
    private static final int MAX_QUEUE_SIZE = 50;

    private static MessageQueue instance;
    private final Context context;
    private final List<String> queue = new ArrayList<>();
    private boolean networkAvailable = true;
    private boolean draining = false;

    public static synchronized MessageQueue getInstance(Context context) {
        if (instance == null) {
            instance = new MessageQueue(context.getApplicationContext());
        }
        return instance;
    }

    private MessageQueue(Context context) {
        this.context = context;
        loadQueue();
        registerNetworkCallback();
    }

    /**
     * 发送消息：有网直接发，无网入队
     */
    public void send(String text) {
        if (networkAvailable) {
            new Thread(() -> {
                boolean ok = FeishuWebhook.sendText(context, text);
                if (!ok) {
                    enqueue(text);
                }
            }).start();
        } else {
            enqueue(text);
            Log.i(TAG, "📴 无网络，已入队 (队列: " + queue.size() + ")");
        }
    }

    private synchronized void enqueue(String text) {
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.remove(0); // 丢弃最旧的
        }
        queue.add(text);
        saveQueue();
    }

    /**
     * 网络恢复时排空队列
     */
    private synchronized void drain() {
        if (draining || queue.isEmpty()) return;
        draining = true;

        new Thread(() -> {
            Log.i(TAG, "📶 网络恢复，发送 " + queue.size() + " 条缓存消息");
            List<String> toSend;
            synchronized (this) {
                toSend = new ArrayList<>(queue);
                queue.clear();
                saveQueue();
            }

            int success = 0;
            for (String msg : toSend) {
                if (FeishuWebhook.sendText(context, msg)) {
                    success++;
                } else {
                    // 发送失败，重新入队
                    enqueue(msg);
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            Log.i(TAG, "✅ 已发送 " + success + "/" + toSend.size());
            draining = false;
        }).start();
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    networkAvailable = true;
                    drain();
                }

                @Override
                public void onLost(Network network) {
                    networkAvailable = false;
                    Log.i(TAG, "📴 网络断开");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "注册网络回调失败: " + e.getMessage());
        }
    }

    private void loadQueue() {
        try {
            File file = new File(context.getFilesDir(), QUEUE_FILE);
            if (!file.exists()) return;

            FileReader reader = new FileReader(file);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                queue.add(arr.getString(i));
            }
            if (!queue.isEmpty()) {
                Log.i(TAG, "📂 加载 " + queue.size() + " 条缓存消息");
            }
        } catch (Exception e) {
            Log.w(TAG, "加载队列失败: " + e.getMessage());
        }
    }

    private void saveQueue() {
        try {
            JSONArray arr = new JSONArray();
            for (String msg : queue) arr.put(msg);

            File file = new File(context.getFilesDir(), QUEUE_FILE);
            FileWriter writer = new FileWriter(file);
            writer.write(arr.toString());
            writer.close();
        } catch (Exception e) {
            Log.w(TAG, "保存队列失败: " + e.getMessage());
        }
    }
}
