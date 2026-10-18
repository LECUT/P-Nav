package com.ecut.pnav.util;

import android.content.Context;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 存储路径工具 — 统一管理应用文件目录
 */
public class StorageUtil {

    private static final String DATA_DIR = "PNav";
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy_M_d_H_mm_ss");

    /**
     * 获取（并确保存在）应用数据目录：Android/data/&lt;pkg&gt;/files/PNav/
     */
    public static File getDataDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * 获取（并确保存在）日志目录：Android/data/&lt;pkg&gt;/files/PNav/logs/
     */
    public static File getLogDir(Context context) {
        File dir = new File(getDataDir(context), "logs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * 返回 UTC 时间戳字符串，用于文件名：yyyy_M_d_H_mm_ss
     */
    public static String getTimestamp() {
        return LocalDateTime.now(ZoneOffset.UTC).format(TS_FORMATTER);
    }
}
