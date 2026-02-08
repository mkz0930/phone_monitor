package com.phonemonitor.app;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备型号映射：内部代号 → 友好名称
 */
public class DeviceNames {
    private static final Map<String, String> MAP = new HashMap<>();

    static {
        // OPPO
        MAP.put("PKH120", "OPPO Find N5");
        MAP.put("PHZ110", "Find N5 Flip");
        MAP.put("PHQ110", "Find X8 Pro");
        MAP.put("PKG110", "Find X8 Ultra");
        MAP.put("PHK110", "Find X8");
    }

    /**
     * 返回友好设备名，找不到则返回原始 Build.MODEL
     */
    public static String get() {
        String model = Build.MODEL;
        String friendly = MAP.get(model);
        return friendly != null ? friendly : model;
    }
}
