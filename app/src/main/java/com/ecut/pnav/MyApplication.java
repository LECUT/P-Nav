package com.ecut.pnav;

import android.app.Application;
import android.util.Log;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.ecut.pnav.util.AppLogger;
import com.ecut.pnav.util.CrashHandler;
import com.ecut.pnav.util.SessionLogger;

/**
 * 自定义 Application 类
 * 在应用启动时初始化：
 * 1. CrashHandler — 全局崩溃捕获
 * 2. AppLogger — 内置日志文件记录（自动写入设备存储）
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. 启动文件日志记录器（必须先于所有操作，确保日志被捕获）
        AppLogger.start(this);
        SessionLogger.start(this);
        Log.i(TAG, "AppLogger + SessionLogger started");

        // 2. 百度地图 SDK 初始化（隐私合规须在 initialize 之前）
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
        try {
            SDKInitializer.initialize(this);
        } catch (Exception e) {
            Log.e(TAG, "BaiduMap SDK init failed", e);
        }
        SDKInitializer.setCoordType(CoordType.BD09LL);

        // 3. 初始化全局崩溃捕获
        CrashHandler.init(this);
        Log.i(TAG, "Application initialized — crash handler + file logger ready");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        AppLogger.stop();
        SessionLogger.stop();
    }
}
