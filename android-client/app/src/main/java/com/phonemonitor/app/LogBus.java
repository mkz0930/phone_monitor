package com.phonemonitor.app;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的日志事件总线：后台服务 → UI 日志显示
 */
public class LogBus {
    public interface LogListener {
        void onLog(String tag, String message);
    }

    private static final List<LogListener> listeners = new ArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void register(LogListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public static void unregister(LogListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * 发送日志（可从任意线程调用，自动切到主线程）
     */
    public static void post(String tag, String message) {
        mainHandler.post(() -> {
            synchronized (listeners) {
                for (LogListener l : listeners) {
                    l.onLog(tag, message);
                }
            }
        });
    }
}
