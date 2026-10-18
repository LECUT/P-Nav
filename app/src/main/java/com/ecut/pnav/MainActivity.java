/*
 * Derived from the open-source myGNSS application.
 *
 *     Copyright (c) 2024, z1Yang-1, All rights reserved.
 *     Licensed under the BSD 2-Clause License.
 *     https://github.com/z1Yang-1/myGNSS
 *
 * Substantially modified and extended for P-Nav.
 * See THIRD-PARTY-NOTICES.md for details.
 */
package com.ecut.pnav;

import static com.ecut.pnav.ui.FtpConnection.downloadFile;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.ecut.pnav.databinding.ActivityMainBinding;
import com.ecut.pnav.ui.FtpConnection;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.Ntrip.OnRtcmDataReceivedListener;
import com.ecut.pnav.ui.Ntrip.rtcm_t;
import com.ecut.pnav.ui.data.DataFragment;
import com.ecut.pnav.ui.data.FileCleanupUtil;
import com.ecut.pnav.ui.status.HomeFragment;
import com.ecut.pnav.ui.quality.QualityFragment;
import com.ecut.pnav.ui.base.LocaleAwareActivity;

import com.ecut.pnav.util.LocaleHelper;
import com.ecut.pnav.util.StorageUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends LocaleAwareActivity implements OnRtcmDataReceivedListener {
    private final String[] strCn0DbHz = {"0","25","30","35","40","45"};
    private ActivityMainBinding binding;
    public static int Cn0DbHz = 0;  /* 0=no filter (GPSTest-style), record all signals incl. L5/E5a/B2a */
    private FTPClient client;
    public static int sateph=0;
    public static int ephForceMode = 0; // 0=自动 1=广播星历 2=NRT精密
    // 时间相关字段由 rebuildRemoteDirPath() 动态更新，不再使用类加载时冻结的值
    public static int Year;
    public static int dayOfYear;
    private static int dayofyear;
    private static int utc_hour;
    public static String[] infile = new String[3];
    public static String[] outputFilePath = new String[3];
    public static String[] navFilePaths = new String[4];  // GPS(.n) GLO(.g) BDS(.c) GAL(.l)
    // remoteDirPath 初始值由 rebuildRemoteDirPath() 设置
    public static String[] remoteDirPath = new String[5];

    public static void rebuildRemoteDirPath() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        Year = nowUtc.getYear();
        dayofyear = nowUtc.getDayOfYear();
        dayOfYear = nowUtc.getDayOfYear();
        utc_hour = nowUtc.getHour();
        String dataBase = FtpConnection.getSelectedDataBasePath();
        String prodBase = FtpConnection.getSelectedProductsBasePath();
        remoteDirPath = new String[]{
            dataBase + "/data/hourly/" + Year + "/" + dayofyear + "/",
            prodBase + "/products/mgex",
            dataBase + "/data/daily/" + Year + "/brdc",
            dataBase + "/data/hourly/" + Year + "/" + dayofyear,
            dataBase + "/data/" + Year + "/" + dayofyear    // RINEX 3 多系统广播星历
        };
    }
    private positiondatabase mydb;
    public static boolean[] checked = {false, false, false, false};
    private static final String PREFS_NAME = "gnss_prefs";
    private static final String PREF_GPS = "pref_gps";
    private static final String PREF_BDS = "pref_bds";
    private static final String PREF_GLONASS = "pref_glonass";
    private static final String PREF_GALILEO = "pref_galileo";
    private static final int REQUEST_LOCATION_PERMISSION = 100;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    public static boolean if_auto_record = false;
    public static boolean auto_record_mode = false;
    public static boolean if_setting = true;
    public static boolean if_record_trajectory = false;

    private HomeFragment homeFragment;
    private DataFragment dataFragment;
    private PositioningFragment positioningFragment;
    private SettingsFragment settingsFragment;
    private QualityFragment qualityFragment;
    private Fragment activeFragment;

    /* ── 统一 GnssMeasurementsEvent 分发 ─────────────────────────── */
    private final CopyOnWriteArrayList<GnssMeasurementListener> measurementListeners = new CopyOnWriteArrayList<>();
    private volatile GnssMeasurementListener priorityListener = null;
    private volatile boolean gnssCallbackRegistered = false;
    private volatile boolean gnssStatusRegistered = false;
    private volatile boolean nmeaRegistered = false;
    private final GnssMeasurementsEvent.Callback gnssMeasurementCallback =
            new GnssMeasurementsEvent.Callback() {
                private int cbCount = 0;
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                    cbCount++;
                    GnssMeasurementListener prio = priorityListener;
                    if (prio != null) {
                        // 定位解算模式：只投递给当前解算模块，确保获取完整原始数据
                        if (cbCount % 30 == 1) Log.d("MAIN", "GNSS cb #" + cbCount + " PRIORITY=" + prio.getClass().getSimpleName() + " meas=" + event.getMeasurements().size());
                        try { prio.onGnssMeasurementsReceived(event); }
                        catch (Exception e) { /* 监听器崩溃不影响 */ }
                    } else {
                        // 普通模式：投递给所有订阅者
                        if (cbCount % 30 == 1) Log.d("MAIN", "GNSS cb #" + cbCount + " listeners=" + measurementListeners.size() + " meas=" + event.getMeasurements().size());
                        for (GnssMeasurementListener l : measurementListeners) {
                            try { l.onGnssMeasurementsReceived(event); }
                            catch (Exception e) { /* 一个监听器崩溃不影响其他 */ }
                        }
                    }
                }
                @Override
                public void onStatusChanged(int status) {}
            };

    /* ── 统一 GnssStatus + NMEA 分发 ─────────────────────────── */
    private final GnssStatus.Callback mGnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            GnssMeasurementListener prio = priorityListener;
            if (prio != null) {
                try { prio.onGnssStatusChanged(status); } catch (Exception ignored) {}
            } else {
                for (GnssMeasurementListener l : measurementListeners) {
                    try { l.onGnssStatusChanged(status); } catch (Exception ignored) {}
                }
            }
        }
    };

    private final OnNmeaMessageListener mNmeaListener = (nmea, timestampMs) -> {
        GnssMeasurementListener prio = priorityListener;
        if (prio != null) {
            try { prio.onNmeaReceived(timestampMs, nmea); } catch (Exception ignored) {}
        } else {
            for (GnssMeasurementListener l : measurementListeners) {
                try { l.onNmeaReceived(timestampMs, nmea); } catch (Exception ignored) {}
            }
        }
    };

    public void addMeasurementListener(GnssMeasurementListener listener) {
        if (listener != null && !measurementListeners.contains(listener)) {
            measurementListeners.add(listener);
        }
        ensureGnssRegistered();
    }

    /** 定位解算开始时调用：设置当前解算模块为最高优先级，其他模块暂停接收数据 */
    public void setPriorityListener(GnssMeasurementListener listener) {
        priorityListener = listener;
        Log.d("MAIN", "Priority listener set: " + (listener != null ? listener.getClass().getSimpleName() : "null"));
    }

    /** 定位解算结束时调用：恢复所有模块的数据投递。
     * 传入 null 则无条件清除优先级。 */
    public void clearPriorityListener(GnssMeasurementListener listener) {
        if (listener == null || priorityListener == listener) {
            priorityListener = null;
            Log.d("MAIN", "Priority listener cleared");
        }
    }

    public void removeMeasurementListener(GnssMeasurementListener listener) {
        if (listener != null) measurementListeners.remove(listener);
    }

    @SuppressLint("MissingPermission")
    private void registerGnssMeasurements() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            showGnssNotSupportedDialog();
            return;
        }

        /* 必须确保权限已授予，否则 SecurityException 后不会再重试 */
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("MAIN", "GNSS registration deferred — no location permission yet");
            return;
        }

        /* 先检查 GNSS 硬件特性 */
        boolean hasGnssFeature = getPackageManager().hasSystemFeature("android.hardware.location.gnss");
        boolean hasGpsFeature = getPackageManager().hasSystemFeature("android.hardware.location.gps");
        Log.d("MAIN", "GNSS feature=" + hasGnssFeature + " GPS feature=" + hasGpsFeature);

        if (!hasGnssFeature && !hasGpsFeature) {
            showGnssNotSupportedDialog();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+ (Android 12+): fullTracking + GnssMeasurementRequest
                GnssMeasurementRequest request = new GnssMeasurementRequest.Builder()
                        .setFullTracking(true)
                        .build();
                lm.registerGnssMeasurementsCallback(request, getMainExecutor(), gnssMeasurementCallback);
                Log.d("MAIN", "GNSS registered with fullTracking");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30 (Android 11): Executor 双参版本可用
                lm.registerGnssMeasurementsCallback(getMainExecutor(), gnssMeasurementCallback);
            } else {
                // API 24–29 (Android 7–10): 仅原始单参版本
                @SuppressWarnings("deprecation")
                boolean ok = lm.registerGnssMeasurementsCallback(gnssMeasurementCallback);
                if (!ok) {
                    throw new UnsupportedOperationException("registerGnssMeasurementsCallback returned false");
                }
            }
            gnssCallbackRegistered = true;
            Log.d("MAIN", "GNSS measurements callback registered OK");

        } catch (UnsupportedOperationException e) {
            Log.e("MAIN", "GNSS measurements NOT supported by device", e);
            showGnssNotSupportedDialog();
        } catch (SecurityException e) {
            Log.e("MAIN", "No location permission for GNSS", e);
        } catch (Exception e) {
            Log.e("MAIN", "register gnss meas error", e);
        }
    }

    /* 供 Fragment 在获取权限后调用，确保 GNSS callback 被注册 */
    public void ensureGnssRegistered() {
        if (!gnssCallbackRegistered) {
            Log.d("MAIN", "ensureGnssRegistered: retrying registration");
            registerGnssMeasurements();
        }
        if (!gnssStatusRegistered) {
            registerGnssStatus();
        }
        if (!nmeaRegistered) {
            registerNmea();
        }
    }

    private void showGnssNotSupportedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.gnss_unsupported_title)
                .setMessage(R.string.gnss_unsupported_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void unregisterGnssMeasurements() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            try { lm.unregisterGnssMeasurementsCallback(gnssMeasurementCallback); }
            catch (Exception e) { Log.e("MAIN", "unregister gnss meas error", e); }
        }
        unregisterGnssStatus();
        unregisterNmea();
    }

    @SuppressLint("MissingPermission")
    private void registerGnssStatus() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(getMainExecutor(), mGnssStatusCallback);
            } else {
                @SuppressWarnings("deprecation")
                boolean ok = lm.registerGnssStatusCallback(mGnssStatusCallback);
                if (!ok) { Log.w("MAIN", "registerGnssStatusCallback returned false"); return; }
            }
            gnssStatusRegistered = true;
            Log.d("MAIN", "GnssStatus callback registered OK");
        } catch (Exception e) { Log.e("MAIN", "register gnss status error", e); }
    }

    private void unregisterGnssStatus() {
        if (!gnssStatusRegistered) return;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            try { lm.unregisterGnssStatusCallback(mGnssStatusCallback); }
            catch (Exception e) { Log.e("MAIN", "unregister gnss status error", e); }
        }
        gnssStatusRegistered = false;
    }

    @SuppressLint("MissingPermission")
    private void registerNmea() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lm.addNmeaListener(getMainExecutor(), mNmeaListener);
            } else {
                @SuppressWarnings("deprecation")
                boolean ok = lm.addNmeaListener(mNmeaListener);
                if (!ok) { Log.w("MAIN", "addNmeaListener returned false"); return; }
            }
            nmeaRegistered = true;
            Log.d("MAIN", "NMEA listener registered OK");
        } catch (Exception e) { Log.e("MAIN", "register nmea error", e); }
    }

    private void unregisterNmea() {
        if (!nmeaRegistered) return;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            try { lm.removeNmeaListener(mNmeaListener); }
            catch (Exception e) { Log.e("MAIN", "remove nmea error", e); }
        }
        nmeaRegistered = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ★ 提前初始化导航文件路径，确保所有 Fragment 启动时 navFilePaths 已就绪
        File ephBase = getExternalFilesDir(null);
        if (ephBase != null) {
            setOutputFilePath(ephBase.getAbsolutePath(), outputFilePath);
        }

        // 初始化星座选择状态，避免冷启动时 checked[] 全为 false
        checked = loadCheckboxState();

        try {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), 0);
                binding.bottomNavView.setPadding(0, 0, 0, bars.bottom);
                return insets;
            });

            homeFragment = new HomeFragment();
            dataFragment = new DataFragment();
            positioningFragment = new PositioningFragment();
            settingsFragment = new SettingsFragment();
            qualityFragment = new QualityFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
                    .add(R.id.fragment_container, qualityFragment, "quality").hide(qualityFragment)
                    .add(R.id.fragment_container, positioningFragment, "positioning").hide(positioningFragment)
                    .add(R.id.fragment_container, dataFragment, "data").hide(dataFragment)
                    .add(R.id.fragment_container, homeFragment, "home")
                    .commit();
            getSupportFragmentManager().executePendingTransactions();
            activeFragment = homeFragment;

            BottomNavigationView bottomNav = binding.bottomNavView;
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    switchFragment(homeFragment);
                    return true;
                } else if (id == R.id.nav_data) {
                    switchFragment(dataFragment);
                    return true;
                } else if (id == R.id.nav_positioning) {
                    switchFragment(positioningFragment);
                    return true;
                } else if (id == R.id.nav_quality) {
                    switchFragment(qualityFragment);
                    return true;
                } else if (id == R.id.nav_settings) {
                    switchFragment(settingsFragment);
                    return true;
                }
                return false;
            });

            bottomNav.setSelectedItemId(R.id.nav_home);

            // 首次安装：先让用户选语言，选完再跑其余启动提示，
            // 否则后面的 GPS/权限弹窗会用默认语言渲染
            if (!LocaleHelper.hasChosenLanguage(this)) {
                showFirstRunLanguageDialog();
            } else {
                startupPrompts();
            }

            // 启动时清理旧日志文件（后台线程）
            new Thread(() -> {
                try {
                    File logDir = new File(getExternalFilesDir(null), "gnss_log");
                    FileCleanupUtil.deleteOldFiles(logDir);
                } catch (Exception e) { Log.w("MAIN", "startup cleanup error", e); }
            }, "file-cleanup").start();
        } catch (Exception e) {
            Log.e("MAIN", "Startup error", e);
            Toast.makeText(this, "Startup error: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /** 位置相关的启动提示：GPS 未开提醒 + 权限申请 */
    private void startupPrompts() {
        checkAndPromptForGps();
        checkAndRequestLocationPermission();
        registerGnssMeasurements();
    }

    /**
     * 首次安装的语言选择 —— 唯一的启动提示，其余设置沿用默认值。
     * 选择后立刻落盘并重建界面使新语言生效。
     */
    private void showFirstRunLanguageDialog() {
        String[] languages = {getString(R.string.lang_en), getString(R.string.lang_zh)};
        String[] codes = {"en", "zh"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.lang_choose_title)
                .setSingleChoiceItems(languages, -1, (dialog, which) -> {
                    LocaleHelper.persistLanguage(this, codes[which]);
                    // 标记先于 recreate 落盘，重建后不会再弹
                    LocaleHelper.markLanguageChosen(this);
                    dialog.dismiss();
                    recreate();
                })
                // 不可取消：避免绕过选择导致每次启动都弹
                .setCancelable(false)
                .show();
    }

    private void switchFragment(Fragment target) {
        if (target == activeFragment) return;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        tx.hide(activeFragment);
        tx.show(target);
        tx.commit();
        activeFragment = target;
    }

    /** 供子 Fragment 调用，切换到指定 Tab */
    public void selectTab(int tabId) {
        binding.bottomNavView.setSelectedItemId(tabId);
    }

    public static void setOutputFilePath(String outpath, String[] outputFilePath){
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        int yy = nowUtc.getYear() % 100;
        int doy = nowUtc.getDayOfYear();
        String doyPadded = String.format("%03d", doy);
        int yyyy = nowUtc.getYear();
        // 确保 myFolder 目录存在，防止 buildNavFileArray() 返回空数组
        java.io.File ephDir = new java.io.File(outpath + "/myFolder");
        if (!ephDir.exists()) {
            ephDir.mkdirs();
        }
        outputFilePath[0] = outpath+"/myFolder/brdm"+doyPadded+"0."+yy+"n";
        outputFilePath[1] = outpath+"/myFolder/WUM0MGXULT_"+yyyy+doyPadded+"0.sp3";
        outputFilePath[2] = outpath+"/myFolder/WUM0MGXULT_"+yyyy+doyPadded+"0.clk";
        // 4 系统导航文件路径: GPS(.n) GLO(.g) BDS(.c) GAL(.l)
        String ext = outpath+"/myFolder/brdc"+doyPadded+"0."+yy;
        navFilePaths[0] = ext + "n";  // GPS
        navFilePaths[1] = ext + "g";  // GLONASS
        navFilePaths[2] = ext + "c";  // BDS
        navFilePaths[3] = ext + "l";  // Galileo
    }

    public static boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @SuppressWarnings("deprecation")
    public static void openGpsSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        context.startActivity(intent);
    }

    private void checkAndPromptForGps() {
        if (!isGpsEnabled(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.enable_location);
            builder.setMessage(R.string.location_required);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> openGpsSettings(MainActivity.this));
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> MainActivity.this.finishAffinity());
            builder.show();
        }
    }

    /**
     * 供设置页面调用的公共星历下载方法
     * @param option 1=广播星历, 2=精密SP3, 3=钟差CLK, 4=全部
     */
    public void downloadEphemeris(int option) {
        File outpath = getExternalFilesDir(null);
        if (outpath != null) {
            setOutputFilePath(outpath.getPath(), outputFilePath);
        }
        udEph(option);
    }

    private void udEph(int option) {
        runOnUiThread(() -> {
            ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage(getString(R.string.dl_connecting));
            progressDialog.setCancelable(false);
            progressDialog.show();
            // 注册进度回调，显示正在下载的文件名 + 大小
            FtpConnection.setProgressListener((name, size) -> runOnUiThread(() -> {
                String sz = size < 1024 ? size + " B"
                        : size < 1024 * 1024 ? String.format("%.1f KB", size / 1024.0)
                        : String.format("%.1f MB", size / (1024.0 * 1024.0));
                progressDialog.setMessage(getString(R.string.dl_downloading, name, sz));
            }));
            new Thread(() -> {
                try {
                    client = FtpConnection.getFTPClient();
                    if (client.isConnected()){
                        rebuildRemoteDirPath();
                        downloadFile(MainActivity.this, client, remoteDirPath, infile, option);
                        client.logout();
                        client.disconnect();
                        FtpConnection.decompressGzipFile(infile, outputFilePath);
                        // 下载解压成功后清理过期星历（保留今天+昨天）
                        FtpConnection.cleanupExpiredEphemeris(new File(getExternalFilesDir(null), "myFolder"));
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, R.string.update_successful, Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                    });
                } catch (IOException e) {
                    Log.e("MAIN", "Ephemeris update failed", e);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, R.string.update_failed, Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                    });
                } finally {
                    FtpConnection.setProgressListener(null);
                }
            }).start();
        });
    }

    public void showLanguageDialog() {
        String[] languages = {getString(R.string.lang_en), getString(R.string.lang_zh)};
        String[] codes = {"en", "zh"};
        String current = LocaleHelper.getPersistedLanguage(this);
        int checkedItem = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(current)) checkedItem = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    String newLang = codes[which];
                    if (!newLang.equals(current)) {
                        LocaleHelper.persistLanguage(this, newLang);
                        recreate();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void cleanFilesRecursive(File directory, Calendar now) {
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                cleanFilesRecursive(file, now);
            } else {
                Calendar fileModifiedTime = Calendar.getInstance();
                fileModifiedTime.setTimeInMillis(file.lastModified());
                if (fileModifiedTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        fileModifiedTime.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                        fileModifiedTime.get(Calendar.DAY_OF_MONTH) < now.get(Calendar.DAY_OF_MONTH)) {
                    file.delete();
                }
            }
        }
    }

    private boolean[] loadCheckboxState() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new boolean[]{
                preferences.getBoolean(PREF_GPS, true),
                preferences.getBoolean(PREF_BDS, false),
                preferences.getBoolean(PREF_GLONASS, false),
                preferences.getBoolean(PREF_GALILEO, false)
        };
    }

    private void saveCheckboxState(boolean[] checkedItems) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_GPS, checkedItems[0]);
        editor.putBoolean(PREF_BDS, checkedItems[1]);
        editor.putBoolean(PREF_GLONASS, checkedItems[2]);
        editor.putBoolean(PREF_GALILEO, checkedItems[3]);
        editor.apply();
    }

    /**
     * 清理 PNav/temp/ 中过期的临时文件（每天之前的文件），保留当天文件。
     */
    private int cleanFiles() {
        File folderToClean = new File(StorageUtil.getDataDir(this), "temp");
        if (!folderToClean.exists()) return 1;
        if (folderToClean.isDirectory()) {
            Calendar now = Calendar.getInstance();
            for (File file : Objects.requireNonNull(folderToClean.listFiles())) {
                if (file.isDirectory()) {
                    cleanFilesRecursive(file, now);
                } else {
                    Calendar fileModifiedTime = Calendar.getInstance();
                    fileModifiedTime.setTimeInMillis(file.lastModified());
                    if (fileModifiedTime.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
                        fileModifiedTime.get(Calendar.MONTH) != now.get(Calendar.MONTH) ||
                        fileModifiedTime.get(Calendar.DAY_OF_MONTH) != now.get(Calendar.DAY_OF_MONTH)) {
                        file.delete();
                    }
                }
            }
        }
        return 1;
    }

    @Override
    public void onRtcmDataReceived(rtcm_t rtcmData) {}

    private boolean isMyServiceRunning() {
        return MyService.isServiceRunning;
    }

    private void checkServiceStatus() {
        boolean isServiceRunning = isMyServiceRunning();
        showNotification(isServiceRunning ? "Service is running" : "Service is not running");
    }

    private void showNotification(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "my_service_channel";
        NotificationChannel channel = new NotificationChannel(channelId, "Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Service Status")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_satellite)
                .build();
        notificationManager.notify(1, notification);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterGnssMeasurements();
    }

    /** 统一请求位置权限 — 整个 App 只有这一个入口 */
    private void checkAndRequestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            // 权限已授予，直接通知各 Fragment
            notifyFragmentsLocationPermissionGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MAIN", "位置权限已授予，通知所有 Fragment");
                notifyFragmentsLocationPermissionGranted();
                ensureGnssRegistered();
                // 权限授予后再请求通知权限 (Android 13+)
                requestNotificationPermissionIfNeeded();
            }
        }
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            Log.d("MAIN", "通知权限结果: " + (grantResults.length > 0 ? grantResults[0] : "无"));
        }
    }

    /** 通知所有需要位置权限的 Fragment 注册回调 */
    private void notifyFragmentsLocationPermissionGranted() {
        if (homeFragment != null) homeFragment.onLocationPermissionGranted();
        if (dataFragment != null) dataFragment.onLocationPermissionGranted();
        if (qualityFragment != null) qualityFragment.onLocationPermissionGranted();
    }

    /** Android 13+ 通知权限请求，在位置权限授予后执行 */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }
}
