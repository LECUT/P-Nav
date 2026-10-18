package com.ecut.pnav.util;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局未捕获异常处理器
 * 将所有崩溃信息写入设备存储的日志文件
 *
 * 使用方法：在 Application.onCreate() 中调用 CrashHandler.init(this);
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String LOG_FILE_PREFIX = "crash_";

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Application application;

    private static CrashHandler instance;

    private CrashHandler(Application app) {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.application = app;
    }

    public static void init(Application app) {
        if (instance == null) {
            instance = new CrashHandler(app);
            Thread.setDefaultUncaughtExceptionHandler(instance);
            Log.i(TAG, "CrashHandler initialized");
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 写入日志文件
        writeCrashLog(ex);

        // 交给系统默认处理器（显示"应用已停止"对话框等）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            // 如果没有默认处理器，直接杀死进程
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private void writeCrashLog(Throwable ex) {
        FileOutputStream fos = null;
        try {
            File logDir = StorageUtil.getDataDir(application);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = LOG_FILE_PREFIX + sdf.format(new Date()) + ".log";
            File logFile = new File(logDir, fileName);

            fos = new FileOutputStream(logFile, true);

            // 写入分隔线和时间
            String header = "========================================\n" +
                           "Crash Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date()) + "\n" +
                           "========================================\n";
            fos.write(header.getBytes("UTF-8"));

            // 写入设备信息
            writeDeviceInfo(fos);

            // 写入异常堆栈
            writeException(ex, fos);

            fos.flush();
            Log.i(TAG, "Crash log written to: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log", e);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void writeDeviceInfo(FileOutputStream fos) throws Exception {
        StringBuilder info = new StringBuilder();
        info.append("\n--- Device Info ---\n");
        info.append("Brand: ").append(Build.BRAND).append("\n");
        info.append("Model: ").append(Build.MODEL).append("\n");
        info.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        info.append("Product: ").append(Build.PRODUCT).append("\n");

        try {
            PackageManager pm = application.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(application.getPackageName(), 0);
            info.append("App Version: ").append(pi.versionName)
                .append(" (").append(pi.versionCode).append(")\n");
        } catch (Exception e) {
            info.append("App Version: unknown\n");
        }
        info.append("-------------------\n\n");
        fos.write(info.toString().getBytes("UTF-8"));
    }

    private void writeException(Throwable ex, FileOutputStream fos) throws Exception {
        fos.write("--- Exception Stack ---\n".getBytes("UTF-8"));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        pw.flush();
        fos.write(sw.toString().getBytes("UTF-8"));

        // 如果有 Cause，也写出来
        Throwable cause = ex.getCause();
        while (cause != null) {
            fos.write("\n--- Caused by ---\n".getBytes("UTF-8"));
            sw = new StringWriter();
            pw = new PrintWriter(sw);
            cause.printStackTrace(pw);
            pw.flush();
            fos.write(sw.toString().getBytes("UTF-8"));
            cause = cause.getCause();
        }
    }

}
