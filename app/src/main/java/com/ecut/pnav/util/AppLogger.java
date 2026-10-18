package com.ecut.pnav.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 内置日志文件记录器
 * 应用启动后自动将所有 logcat 日志写入设备文件
 * 日志文件位置: Android/data/com.ecut.pnav/files/PNav/logs/app_YYYYMMDD_HHMMSS.log
 *
 * 无需 PC/ADB，APK 安装后自动运行
 * 自动清理旧日志，最多保留 10 个文件
 */
public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final int MAX_LOG_FILES = 10;

    private static Thread logcatThread;
    private static Process logcatProcess;
    private static volatile boolean running = false;
    private static File currentLogFile;

    /**
     * 启动日志记录（在 Application.onCreate 中调用）
     */
    public static void start(Context context) {
        if (running) return;
        running = true;

        try {
            File logDir = StorageUtil.getLogDir(context);

            cleanOldLogs(logDir);

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            currentLogFile = new File(logDir, "app_" + ts + ".log");

            writeHeader(context, currentLogFile);

            final int pid = android.os.Process.myPid();
            logcatThread = new Thread(() -> runLogcat(pid, currentLogFile), "LogcatCapture");
            logcatThread.setDaemon(true);
            logcatThread.setPriority(Thread.MIN_PRIORITY);
            logcatThread.start();

            Log.i(TAG, "Log file started: " + currentLogFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AppLogger", e);
            running = false;
        }
    }

    /** 获取当前日志文件 */
    public static File getCurrentLogFile() {
        return currentLogFile;
    }

    /** 停止日志记录 */
    public static void stop() {
        running = false;
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        if (logcatThread != null && logcatThread.isAlive()) {
            logcatThread.interrupt();
        }
    }

    // ---- 内部实现 ----

    private static void runLogcat(int pid, File logFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true), 8192)) {
            // 尝试方案 1: logcat --pid (Android 7.0+)
            ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "threadtime", "--pid=" + pid);
            pb.redirectErrorStream(true);
            logcatProcess = pb.start();
            readLines(logcatProcess, writer);
        } catch (Exception e) {
            Log.e(TAG, "--pid mode failed, trying tag filter", e);
            tryFallback(logFile);
        }
    }

    /** 备用：通过 TAG 过滤日志 */
    private static void tryFallback(File logFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true), 8192)) {
            ProcessBuilder pb = new ProcessBuilder(
                "logcat", "-v", "threadtime",
                "AndroidRuntime:E", "System.err:W",
                // Application & infrastructure
                "MyApplication:D", "AppLogger:D", "CrashHandler:D",
                "MAIN:D",
                // Core services
                "RTCM_SVC:D", "measure_util:D",
                // SPP
                "SPP:D", "SppFragment:D", "SppViewModel:D", "DIAG_SPP:D", "SPPActivity:D",
                // DGNSS
                "DGNSS_DIAG:D", "DgnssFragment:D", "RTD:D",
                // RTK
                "RTK:D", "RTK_DIAG:D", "RtkFragment:D", "RtkViewModel:D",
                // PPP
                "PPP:D", "PPP_DIAG:D", "PppFragment:D", "PppViewModel:D", "PPPActivity:D",
                // Status/Home
                "status:D", "GnssStatus:D", "HomeFragment:D", "HomeViewModel:D",
                // NTRIP
                "NtripFragment:D", "NtripViewModel:D", "SwitchButton:D",
                // Data/Recording
                "DataFragment:D", "DataViewModel:D",
                // Settings/Solution
                "Solragment:D", "SettingsFragment:D", "Decompress:D",
                // Quality
                "QualityFragment:D", "QualityViewModel:D",
                // Map
                "MapFragment:D", "MapViewModel:D",
                // Positioning
                "PositioningFragment:D",
                // Misc
                "spp:D", "FtpHelper:D", "SelectedPosition:D", "TAG:D",
                "LocaleAwareActivity:D", "LocaleHelper:D",
                "*:S"
            );
            pb.redirectErrorStream(true);
            logcatProcess = pb.start();
            readLines(logcatProcess, writer);
        } catch (Exception e2) {
            Log.e(TAG, "Fallback logcat also failed", e2);
        }
    }

    private static void readLines(Process process, BufferedWriter writer) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()), 8192);
        String line;
        int count = 0;
        while (running && (line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
            if (++count % 50 == 0) writer.flush();
        }
        writer.flush();
    }

    private static void writeHeader(Context context, File logFile) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile, false))) {
            w.write("=== P-Nav App Log ===");
            w.newLine();
            w.write("Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
            w.newLine();
            w.write("App: " + context.getPackageName());
            w.newLine();
            w.write("Device: " + android.os.Build.BRAND + " " + android.os.Build.MODEL);
            w.newLine();
            w.write("Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")");
            w.newLine();
            try {
                android.content.pm.PackageInfo pi = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                w.write("Version: " + pi.versionName + " (" + pi.versionCode + ")");
                w.newLine();
            } catch (Exception ignored) {}
            w.write("================================");
            w.newLine();
            w.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write log header", e);
        }
    }

    private static void cleanOldLogs(File logDir) {
        try {
            File[] files = logDir.listFiles((d, n) -> n.startsWith("app_") && n.endsWith(".log"));
            if (files == null || files.length <= MAX_LOG_FILES) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_LOG_FILES; i++) {
                files[i].delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean old logs", e);
        }
    }
}
