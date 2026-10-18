package com.ecut.pnav.util;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 结构化会话日志 — 直接写文件，不依赖 logcat 缓冲区。
 * 每次启动生成一个文件: PNav/logs/session_YYYYMMDD_HHMMSS.log
 */
public class SessionLogger {

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE_TS = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private static BufferedWriter writer;
    private static File logFile;
    private static boolean enabled = false;
    private static ScheduledExecutorService flushScheduler;

    public static void start(Context context) {
        try {
            File logDir = StorageUtil.getLogDir(context);
            logFile = new File(logDir, "session_" + FILE_TS.format(new Date()) + ".log");
            writer = new BufferedWriter(new FileWriter(logFile, true), 4096);
            enabled = true;
            w("══════ SESSION START ══════");
            w("Device: " + android.os.Build.BRAND + " " + android.os.Build.MODEL);
            w("Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")");
            // 每 5 秒批量刷盘，避免每行日志都 flush
            flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SessionLogger-flush");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            flushScheduler.scheduleWithFixedDelay(() -> {
                synchronized (SessionLogger.class) {
                    if (writer != null) {
                        try { writer.flush(); } catch (Exception ignored) {}
                    }
                }
            }, 5, 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            enabled = false;
        }
    }

    public static File getCurrentFile() { return logFile; }

    public static void stop() {
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try { flushScheduler.awaitTermination(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
            flushScheduler = null;
        }
        if (writer != null) {
            try { w("══════ SESSION END ══════"); writer.flush(); writer.close(); } catch (Exception ignored) {}
            writer = null;
        }
        enabled = false;
    }

    // ---- 事件记录 ----

    /** 通用事件 — 写入缓冲区，不立刻刷盘 */
    public static synchronized void event(String tag, String msg) {
        if (!enabled || writer == null) return;
        try {
            writer.write(TS.format(new Date()) + " [" + tag + "] " + msg);
            writer.newLine();
            // flush 由定时器每 5 秒执行，stop() 时最终刷盘
        } catch (Exception ignored) {}
    }

    /** CORS/NTRIP 连接事件 */
    public static void cors(String msg) { event("CORS", msg); }
    /** RTCM 数据事件 */
    public static void rtcm(String msg) { event("RTCM", msg); }
    /** 定位解算事件 (SPP/DGNSS/RTK/PPP) */
    public static void sol(String mode, String msg) { event("SOL_" + mode, msg); }
    /** Fragment 生命周期事件 */
    public static void ui(String msg) { event("UI", msg); }
    /** 错误 */
    public static void err(String tag, String msg) { event("ERR_" + tag, msg); }

    // ---- 内部 ----

    private static void w(String line) {
        try { writer.write(TS.format(new Date()) + " [SYS] " + line); writer.newLine(); writer.flush(); } catch (Exception ignored) {}
    }
}
