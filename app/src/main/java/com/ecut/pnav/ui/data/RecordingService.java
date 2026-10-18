package com.ecut.pnav.ui.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.File;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ecut.pnav.MainActivity;
import com.ecut.pnav.R;
import com.ecut.pnav.util.StorageUtil;

/**
 * Lightweight foreground service that keeps the app process alive during GNSS recording.
 * The actual GNSS data capture and file writing is handled by DataViewModel.
 * This service only shows a persistent notification and prevents process termination.
 *
 * Inspired by GPSTest's ForegroundOnlyLocationService.
 */
public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "gnss_recording_channel";
    private static final int NOTIFICATION_ID = 4221;

    // Intent actions
    public static final String ACTION_STOP_FROM_NOTIFICATION = "com.ecut.pnav.action.STOP_FROM_NOTIFICATION";

    // Broadcast: sent FROM this service TO DataFragment with state updates
    public static final String BROADCAST_STATE_UPDATE = "com.ecut.pnav.broadcast.RECORDING_STATE";
    public static final String EXTRA_IS_RECORDING = "is_recording";
    public static final String EXTRA_ELAPSED_SECONDS = "elapsed_seconds";
    public static final String EXTRA_FILE_SIZE = "file_size";
    public static final String EXTRA_EPOCH_COUNT = "epoch_count";
    public static final String EXTRA_FILE_NAME = "file_name";

    // Broadcast: received FROM DataFragment TO this service with live stats
    public static final String BROADCAST_STATS_UPDATE = "com.ecut.pnav.broadcast.STATS_UPDATE";

    // Preferences keys (public for DataFragment access)
    public static final String KEY_LOGCAT_ENABLED = "logcat_enabled";
    public static final String KEY_LOGCAT_MEASURE = "logcat_measure";
    public static final String KEY_LOGCAT_NMEA = "logcat_nmea";
    public static final String KEY_INJECT_ASSIST = "inject_assist";

    private boolean isRecording = false;
    private long recordingStartTime;
    private String currentFileName = "";
    private long currentFileSize = 0;
    private int epochCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final IBinder binder = new LocalBinder();
    private BroadcastReceiver statsReceiver;

    public class LocalBinder extends Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    public boolean isRecording() { return isRecording; }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel();

        // Listen for stats updates from DataFragment
        statsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BROADCAST_STATS_UPDATE.equals(intent.getAction())) {
                    currentFileName = intent.getStringExtra("file_name");
                    currentFileSize = intent.getLongExtra("file_size", 0);
                    epochCount = intent.getIntExtra("epoch_count", 0);
                    sendStateUpdate();
                }
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(statsReceiver, new IntentFilter(BROADCAST_STATS_UPDATE));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_FROM_NOTIFICATION.equals(intent.getAction())) {
            stopRecording();
            return START_NOT_STICKY;
        }

        if (!isRecording) {
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            currentFileName = "Starting...";
            epochCount = 0;

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, buildNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                                    | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "前台服务权限被拒绝(MIUI/HyperOS常见): " + e.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            } catch (RuntimeException e) {
                Log.e(TAG, "前台服务启动失败: " + e.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            }
            startPeriodicUpdate();

            // 清理旧文件，但保留 PNAV(RINEX) 文件——后处理模式下创建时为空文件
            File folder = getStorageFolder();
            if (folder != null) {
                File[] pnavFiles = folder.listFiles(f -> f.getName().startsWith("PNAV"));
                FileCleanupUtil.deleteOldFiles(folder,
                        pnavFiles != null ? pnavFiles : new File[0]);
            }
            // 同时清理 CSV 日志目录
            File csvDir = new File(getExternalFilesDir(null), "gnss_log");
            if (csvDir.exists()) {
                FileCleanupUtil.deleteOldFiles(csvDir);
            }

            Log.d(TAG, "Recording service started (foreground)");
        }
        return START_STICKY;
    }

    public void stopRecording() {
        if (!isRecording) return;
        Log.d(TAG, "stopRecording");
        isRecording = false;
        stopPeriodicUpdate();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ==================== Notification ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "GNSS Recording",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Ongoing GNSS data recording");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        long elapsed = System.currentTimeMillis() - recordingStartTime;
        long sec = elapsed / 1000;
        String content = String.format("%02d:%02d:%02d  |  Epochs: %d  |  %s",
                sec / 3600, (sec % 3600) / 60, sec % 60, epochCount, currentFileName);

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP_FROM_NOTIFICATION);
        PendingIntent stopPI = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_data)
                .setContentTitle("P-Nav Recording")
                .setContentText(content)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPI)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPI)
                .build();
    }

    // ==================== Periodic Updates ====================

    private void startPeriodicUpdate() {
        stopPeriodicUpdate();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.notify(NOTIFICATION_ID, buildNotification());
                }
                sendStateUpdate();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(updateRunnable, 2000);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    private void sendStateUpdate() {
        Intent intent = new Intent(BROADCAST_STATE_UPDATE);
        intent.putExtra(EXTRA_IS_RECORDING, isRecording);
        intent.putExtra(EXTRA_ELAPSED_SECONDS, (System.currentTimeMillis() - recordingStartTime) / 1000);
        intent.putExtra(EXTRA_EPOCH_COUNT, epochCount);
        intent.putExtra(EXTRA_FILE_SIZE, currentFileSize);
        intent.putExtra(EXTRA_FILE_NAME, currentFileName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private File getStorageFolder() {
        return StorageUtil.getDataDir(this);
    }

    @Override
    public void onDestroy() {
        stopPeriodicUpdate();
        if (statsReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(statsReceiver);
        }
        super.onDestroy();
    }
}
