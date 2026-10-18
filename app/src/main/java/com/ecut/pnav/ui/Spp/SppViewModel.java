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
package com.ecut.pnav.ui.Spp;

import static com.ecut.pnav.MainActivity.auto_record_mode;
import static com.ecut.pnav.MainActivity.checked;
import static com.ecut.pnav.MainActivity.if_auto_record;
import static com.ecut.pnav.MainActivity.outputFilePath;
import static com.ecut.pnav.MainActivity.sateph;
import static com.ecut.pnav.positiondatabase.insertDataAsync;
import static com.ecut.pnav.positiondatabase.insertFileRecordAsync;
import static com.ecut.pnav.ui.FtpConnection.downloadFile;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.ecut.pnav.ui.AccuracyTracker;
import com.ecut.pnav.ui.SolutionMetrics;
import com.ecut.pnav.ui.Sol.SolViewModel;
import static com.ecut.pnav.ui.measure_util.calculateSelectedValue;
import static com.ecut.pnav.ui.measure_util.out_sol;
import static com.ecut.pnav.ui.measure_util.resetobs;
import static com.ecut.pnav.ui.measure_util.solopt_default;
import static com.rtklib.bean.nav_t.MAXRCV;
import static com.rtklib.bean.nav_t.getMaxSat;
import static com.rtklib.pntpos.pntPos.PMODE_SINGLE;
import static com.rtklib.pntpos.pntPos.pnt_pos;
import static com.rtklib.pntpos.rescode.R2D;
import static com.rtklib.pntpos.rescode.ecef2pos;
import static com.rtklib.postpos.outSol.SOLF_ENU;
import static com.rtklib.postpos.outSol.SOLF_LLH;
import static com.rtklib.postpos.outSol.SOLF_XYZ;
import static com.rtklib.postpos.outSol.covenu;
import static com.rtklib.postpos.outSol.deg2dms;
import static com.rtklib.postpos.outSol.opt2sep;
import static com.rtklib.postpos.outSol.soltocov;
import static com.rtklib.readobsnav.readObsNav.read_obsnav;
import static com.rtklib.readobsnav.readrnxh.SYS_CMP;
import static com.rtklib.readobsnav.readrnxh.SYS_GAL;
import static com.rtklib.readobsnav.readrnxh.SYS_GLO;
import static com.rtklib.readobsnav.readrnxh.SYS_GPS;
import static com.rtklib.readobsnav.readrnxh.SYS_IRN;
import static com.rtklib.readobsnav.readrnxh.SYS_QZS;
import static com.rtklib.readobsnav.readrnxh.SYS_SBS;
import static com.rtklib.rtkpos.rtkPos.D2R;
import static com.rtklib.rtkpos.rtkPos.PMODE_FIXED;
import static com.rtklib.rtkpos.udState.PMODE_DGPS;

import static java.lang.Math.abs;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import com.ecut.pnav.MainActivity;
import com.ecut.pnav.positiondatabase;
import com.ecut.pnav.ui.FtpConnection;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.Ntrip.NtripFragment;
import com.ecut.pnav.ui.measure_util;
import com.ecut.pnav.util.SessionLogger;
import com.ecut.pnav.util.StorageUtil;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sbs_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.bean.sta_t;

import com.rtklib.postpos.postpos;

import org.apache.commons.net.ftp.FTPClient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SppViewModel extends ViewModel {
    // 星历更新 Toast 状态 token —— Fragment 观察端映射为本地化文案
    public static final String EPH_CORS_UPDATED = "eph_cors_updated";
    public static final String EPH_PATH_NULL = "eph_path_null";
    public static final String EPH_BRDC_READY = "eph_brdc_ready";
    public static final String EPH_NRT_TRYING = "eph_nrt_trying";
    public static final String EPH_NRT_READY = "eph_nrt_ready";
    public static final String EPH_UNAVAILABLE = "eph_unavailable";
    public static final String EPH_UPDATE_FAILED = "eph_update_failed";
    public static final String EPH_MODE_SAVED = "eph_mode_saved";
    private static final boolean DIAG = true;
    private static final String TAG = "SPP_DIAG"; // 统一诊断标签，方便 logcat -s SPP_DIAG
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> satcountText;
    private final MutableLiveData<String> latvalue;
    private final MutableLiveData<String> lonvalue;
    private final MutableLiveData<String> height;
    private final MutableLiveData<String> updateStatus;
    private final MutableLiveData<String> updateVAccuracy;
    private final MutableLiveData<String> updateNAccuracy;
    private final MutableLiveData<String> updateUAccuracy;
    public final AccuracyTracker accuracyTracker = new AccuracyTracker();
    private final MutableLiveData<String> solHistoryLine = new MutableLiveData<>();
    private final MutableLiveData<String> solstate;
    private final MutableLiveData<Integer> textColor;
    private final MutableLiveData<String> ephModeLabel;
    private final MutableLiveData<Integer> ephSelection;
    private final DecimalFormat df = new DecimalFormat("#.###"); // 保留3位小数
    private final DecimalFormat df1 = new DecimalFormat("#.#####");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean mEphTimerRunning = false; // 防止重复调度
    private prcopt_t prcopt; // 实例级副本，避免污染 SolViewModel 共享配置
    LocationManager mLocationManager;
    BufferedWriter bufferedWriter;
    obsd_t[] obs = new obsd_t[getMaxSat()];
    nav_t navs = new nav_t();
    nav_t tempnavs = new nav_t();
    sol_t sol = new sol_t();
    StringBuilder msg;
    int n = 0;
    int nn = 0;
    double[] pos = new double[3];//用于存储纬度、经度、高程
    double[] dms1 = new double[3];//用于存储纬度（° ′ ″）
    double[] dms2 = new double[3];//用于存储经度（° ′ ″）
    gtime_t ts = new gtime_t(0, 0);
    gtime_t te = new gtime_t(0, 0);
    double ti = 0.0;
    int[] index = {0};
    static obs_t obss = new obs_t();
    public static sta_t[] stas = new sta_t[MAXRCV];
    private static sbs_t sbss = new sbs_t();
    static StringBuilder tips = new StringBuilder();

    // 环形缓冲区：最多保留 500 行，增量推送避免全量重绘
    private static final int MAX_LOG_LINES = 500;
    private final List<String> logRing = new ArrayList<>(MAX_LOG_LINES + 10);
    private final MutableLiveData<String> logLine = new MutableLiveData<>();
    private int lastLogLength = 0;

    private static final String COMMENTH = "%";
    double[] current_lat = new double[3];
    double[] current_lon = new double[3];
    double elevation = 0;
    SQLiteDatabase db;
    String msg1 = "";
    private ArrayList<String> ave_result = new ArrayList<>();

    /** 已按用户所选语言包装的 Context（来自 SPPActivity），用于星历文案本地化 */
    private final Context mContext;

    public SppViewModel(Context context) {
        mContext = context;
        mText = new MutableLiveData<>();
        satcountText = new MutableLiveData<>();
        latvalue = new MutableLiveData<>();
        lonvalue = new MutableLiveData<>();
        height = new MutableLiveData<>();
        updateStatus = new MutableLiveData<>();
        updateVAccuracy = new MutableLiveData<>();
        updateNAccuracy = new MutableLiveData<>();
        updateUAccuracy = new MutableLiveData<>();
        solstate = new MutableLiveData<>("");
        textColor = new MutableLiveData<>(android.graphics.Color.rgb(255, 59, 48)); // red = none
        ephModeLabel = new MutableLiveData<>("");
        ephSelection = new MutableLiveData<>(0); // 默认自动

        positiondatabase dbHelper = new positiondatabase(context);
        db = dbHelper.getWritableDatabase();

        String formattedTime = StorageUtil.getTimestamp();
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Initialize prcopt fields safely
        try {
            if (prcopt != null && prcopt.pcvr != null) {
                for (int i = 0; i < prcopt.pcvr.length && i < 2; i++) {
                    prcopt.pcvr[i] = new pcv_t();
                }
            }
        } catch (Exception e) {
            Log.e("SppViewModel", "Failed to init prcopt.pcvr", e);
        }

        for (int i = 0; i < getMaxSat(); i++) {
            navs.pcvs[i] = new pcv_t();
        }
        for (int i = 0; i < getMaxSat(); i++) {
            obs[i] = new obsd_t();
        }
        tips.append("Start......");
        Log.e(TAG, "══════ SESSION START " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + " ══════");
        Log.e(TAG, "INIT: sateph=" + sateph + " ephForceMode=" + MainActivity.ephForceMode
            + " SP3存在=" + (outputFilePath[1] != null && new File(outputFilePath[1]).exists())
            + " navFiles=" + java.util.Arrays.toString(buildNavFileArray()));
        prcopt = new prcopt_t(SolViewModel.prcopt); // 实例副本，避免污染共享配置
        // ★ SPP 只用四大系统解算，排除 QZSS/IRNSS/SBAS，避免其无卫星时 H 矩阵奇异导致 LSQ 失败
        prcopt.setNavsys(SYS_GPS | SYS_GLO | SYS_GAL | SYS_CMP);
        SessionLogger.sol("SPP", "SESSION START — navsys=" + prcopt.getNavsys() + " ionoopt=" + prcopt.getIonoopt() + " tropopt=" + prcopt.getTropopt() + " elmin=" + String.format("%.1f", prcopt.getElmin()/com.rtklib.rtkpos.rtkPos.D2R) + "°");
        // 启动时优先尝试 CORS 实时星历
        boolean corsHasEph = false;
        if (MyService.isServiceRunning) {
            nav_t corsNav = NtripFragment.extractRtcmNav();
            if (corsNav != null && (corsNav.n > 0 || corsNav.ng > 0)) {
                navs = corsNav;
                corsHasEph = true;
                tips.append("\n").append("CORS实时星历已加载 (").append(corsNav.n).append("eph/").append(corsNav.ng).append("geph)");
                Log.d("SPP", "CORS实时星历已加载 n=" + corsNav.n + " ng=" + corsNav.ng);
                measure_util.getEphDetail(mContext, navs, "CORS RTCM");
            }
        }
        // CORS已连接 → 星历由RTCM流持续更新，跳过所有文件读取和FTP下载
        Log.d(TAG, "INIT CORS: isRunning=" + MyService.isServiceRunning + " corsHasEph=" + corsHasEph
            + " initSateph=" + ((sateph == 1 && outputFilePath[1] != null && new File(outputFilePath[1]).exists()) ? 1 : 0));
        if (!corsHasEph) {
            final int initSateph = (sateph == 1
                    && outputFilePath[1] != null && new File(outputFilePath[1]).exists()) ? 1 : 0;
            final String[] paths = outputFilePath.clone();
            // ★ 精密星历 readpreceph 是纯 Java 解析，不能阻塞主线程
            Log.d(TAG, "INIT: 启动后台线程读取星历 initSateph=" + initSateph);
            new Thread(() -> {
                readdata(paths, initSateph);
                // FTP星历若缺少GLONASS且文件是今天的小文件，触发重新下载
                if (!MyService.isServiceRunning && navs.n > 0 && navs.ng == 0) {
                    String[] existingNav = buildNavFileArray();
                    File f = (existingNav.length > 0) ? new File(existingNav[0]) : null;
                    String todayDoy = String.format("%03d", ZonedDateTime.now(ZoneOffset.UTC).getDayOfYear());
                    if (f.exists() && f.getName().contains(todayDoy)) {
                        Log.w("SPP", "星历缺少GLONASS(ng=0)，触发重新下载");
                        tips.append("\n").append("星历缺少多系统数据，正在重新下载...");
                        flushLog();
                        startScheduledTask(context, MainActivity.infile, outputFilePath);
                    } else {
                        Log.w("SPP", "星历缺少GLONASS但非今天文件，等待正常更新周期");
                    }
                }
            }, "spp-init-readdata").start();
        } else if (!MyService.isServiceRunning) {
            // CORS刚断开但构造时拿到了缓存RTCM数据，仍然读文件兜底
            new Thread(() -> readdata(outputFilePath, 0), "spp-init-readdata2").start();
        }
        flushLog();
        latvalue.setValue(current_lat[0]+"°"+current_lat[1]+"′"+current_lat[2]+"″");
        lonvalue.setValue(current_lon[0]+"°"+current_lon[1]+"′"+current_lon[2]+"″");
        height.setValue(elevation +"m");
        updateVAccuracy.setValue("0"+"m");
        updateNAccuracy.setValue("0"+"m");
        updateUAccuracy.setValue("0"+"m");
        solopt_default.times = 1;

        File newFolder = StorageUtil.getDataDir(context);

        if (newFolder != null) {
            try {
                File out_folder = new File(newFolder, "spp" + formattedTime + ".txt");
                FileWriter fileWriter = new FileWriter(out_folder);
                bufferedWriter = new BufferedWriter(fileWriter);
                out_head(bufferedWriter, prcopt, solopt_default);
                insertFileRecordAsync(db, out_folder.getAbsolutePath(), out_folder.getName(), "POS", "SPP", formattedTime);
            } catch (IOException e) {
                Log.e("SppViewModel", "Failed to create output file", e);
                tips.append("File error: ").append(e.getMessage()).append("\n");
            }
        } else {
            tips.append("Storage not available\n");
        }
    }
    void startScheduledTask(Context context, String[] infile, String[] outputFilePath) {
        // 防止多次调用导致多个定时器链并发执行
        Log.d(TAG, "SCHEDULE 入口: ephForceMode=" + MainActivity.ephForceMode + " CORS=" + MyService.isServiceRunning + " sateph=" + sateph);
        if (mEphTimerRunning) {
            Log.d(TAG, "SCHEDULE: 已被跳过(定时任务已在运行)");
            return;
        }
        mEphTimerRunning = true;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        // ★ CORS 已连接 → 优先从 RTCM 实时解码获取星历；若CORS不发电文，回退到文件星历
                        if (MyService.isServiceRunning) {
                            nav_t corsNav = NtripFragment.extractRtcmNav();
                            if (corsNav != null && (corsNav.n > 0 || corsNav.ng > 0)) {
                                navs = corsNav;
                                Log.d("SPP", "CORS星历更新成功 n=" + corsNav.n + " ng=" + corsNav.ng);
                                updateStatus.postValue(EPH_CORS_UPDATED);
                                measure_util.getEphDetail(mContext, navs, "CORS RTCM");
                                handler.postDelayed(this, 30 * 1000);
                                return;
                            }
                            // CORS无星历 → 不return，继续用文件星历
                            Log.w("SPP", "CORS无星历，回退到文件星历");
                        }

                        // 无 CORS → FTP 下载星历（按 ephForceMode 策略）
                        if (outputFilePath == null) {
                            updateStatus.postValue(EPH_PATH_NULL);
                            Log.e("Decompress", "Output path is null");
                            return;
                        }

                        boolean brdcOk = false;
                        boolean nrtOk  = false;

                        // ── 模式 0/1：下载并检查广播星历 ──
                        if (MainActivity.ephForceMode == 0 || MainActivity.ephForceMode == 1) {
                            // ★ 本地缓存检查 — 用 navFilePaths（实际文件），不用 outputFilePath[0]（brdm 路径）
                            String[] cachedNav = buildNavFileArray();
                            if (cachedNav.length > 0) {
                                if (FtpConnection.isBroadcastFresh(cachedNav)) {
                                    readdata(outputFilePath, sateph);
                                    if (navs.n > 0 || navs.ng > 0) {
                                        brdcOk = true;
                                        Log.d("SPP", "广播星历缓存有效 files=" + cachedNav.length);
                                    }
                                } else if (FtpConnection.isBroadcastUsable(cachedNav)) {
                                    readdata(outputFilePath, sateph);
                                    if (navs.n > 0 || navs.ng > 0) {
                                        brdcOk = true;
                                        Log.d("SPP", "广播星历(昨天)缓存有效");
                                    }
                                }
                            }

                            // 缓存不可用 → FTP下载广播星历
                            if (!brdcOk) {
                                Log.d(TAG, "SCHEDULE: 启动FTP下载广播星历");
                                FTPClient client = FtpConnection.getFTPClient();
                                if (client.isConnected()) {
                                    MainActivity.rebuildRemoteDirPath();
                                    downloadFile(context, client, MainActivity.remoteDirPath, infile, 1);
                                    client.logout();
                                    client.disconnect();
                                    FtpConnection.decompressGzipFile(infile, outputFilePath);
                                    FtpConnection.cleanupExpiredEphemeris(new File(context.getExternalFilesDir(null), "myFolder"));
                                }
                                File brdcCheck = (buildNavFileArray().length > 0) ? new File(buildNavFileArray()[0]) : null;
                                if (brdcCheck != null && brdcCheck.exists() && brdcCheck.length() >= 1000) {
                                    brdcOk = true;
                                    sateph = 0;
                                    prcopt.setSateph(0);
                                    updateStatus.postValue(EPH_BRDC_READY);
                                    Log.d(TAG, "SCHEDULE: FTP广播下载成功 " + brdcCheck.getName() + " " + brdcCheck.length() + "B");
                                } else {
                                    Log.w(TAG, "SCHEDULE: FTP广播下载失败 brdcCheck=" + (brdcCheck != null ? brdcCheck.getAbsolutePath() + " exists=" + brdcCheck.exists() + " size=" + brdcCheck.length() : "null"));
                                }
                            }
                        }

                        // ── 模式 0：广播失败时回退到NRT ──
                        // ── 模式 2：直接下载NRT ──
                        if (MainActivity.ephForceMode == 2 || (MainActivity.ephForceMode == 0 && !brdcOk)) {
                            Log.d(TAG, "SCHEDULE: 进入NRT分支 ephForceMode=" + MainActivity.ephForceMode + " brdcOk=" + brdcOk);
                            // 先检查本地NRT缓存
                            if (FtpConnection.isNrtFresh(outputFilePath[1])) {
                                nrtOk = true;
                                sateph = 1;
                                prcopt.setSateph(1);
                                Log.d("SPP", "NRT缓存有效");
                            }

                            if (!nrtOk) {
                                Log.d("SPP", "尝试NRT超快速星历 (mode=" + MainActivity.ephForceMode + " brdcOk=" + brdcOk + ")");
                                updateStatus.postValue(EPH_NRT_TRYING);

                                try {
                                    FTPClient nrtClient = FtpConnection.getFTPClient();
                                    if (nrtClient.isConnected()) {
                                        MainActivity.rebuildRemoteDirPath();
                                        downloadFile(context, nrtClient, MainActivity.remoteDirPath, infile, 6);
                                        nrtClient.logout();
                                        nrtClient.disconnect();
                                        FtpConnection.decompressGzipFile(infile, outputFilePath);
                                        FtpConnection.cleanupExpiredEphemeris(new File(context.getExternalFilesDir(null), "myFolder"));
                                    }
                                } catch (IOException nrtEx) {
                                    Log.e("SPP", "NRT FTP连接失败: " + nrtEx.getMessage());
                                }

                                File sp3File = new File(outputFilePath[1]);
                                if (sp3File.exists()) {
                                    nrtOk = true;
                                    sateph = 1;
                                    prcopt.setSateph(1);
                                    Log.d("SPP", "NRT星历下载成功 SP3=" + sp3File.length() + "B");
                                    updateStatus.postValue(EPH_NRT_READY);
                                } else {
                                    Log.w("SPP", "NRT星历不可用 SP3=" + sp3File.exists());
                                }
                            }
                        }

                        if (!brdcOk && !nrtOk) {
                            updateStatus.postValue(EPH_UNAVAILABLE);
                            Log.w(TAG, "SCHEDULE: 最终状态 brdcOk=" + brdcOk + " nrtOk=" + nrtOk + " — 星历不可用!");
                        }

                        Log.d(TAG, "SCHEDULE: 最终readdata sateph=" + sateph + " brdcOk=" + brdcOk + " nrtOk=" + nrtOk);
                        readdata(outputFilePath, sateph);
                    } catch (IOException e) {
                        Log.e("SppViewModel", "Failed to update ephemeris", e);
                        updateStatus.postValue(EPH_UPDATE_FAILED);
                    }
                }).start();
                handler.postDelayed(this, 30 * 60 * 1000);
            }
        };
        handler.post(runnable);
    }



    // ★ 构建多系统导航文件列表，过滤掉不存在或null的文件
    private String[] buildNavFileArray() {
        String[] src = com.ecut.pnav.MainActivity.navFilePaths;
        if (src == null) return new String[0];
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (String f : src) {
            if (f != null && new java.io.File(f).exists()) {
                list.add(f);
            }
        }
        return list.toArray(new String[0]);
    }

    private void readdata(String[] infile, int sateph) {
        Log.d(TAG, "READDATA 入口: sateph=" + sateph + " infile[0]=" + (infile != null && infile[0] != null ? new File(infile[0]).getName() : "null")
            + " infile[1]=" + (infile != null && infile[1] != null ? new File(infile[1]).getName() : "null"));
        if (infile == null) {
            tips.append("\n").append("File paths not initialized, skipping ephemeris read.");
            return;
        }
        // NRT模式下允许广播文件缺失，但至少要有一个文件路径
        if (infile[0] == null && (sateph != 1 || (infile[1] == null && infile[2] == null))) {
            tips.append("\n").append("File paths not initialized, skipping ephemeris read.");
            return;
        }

        // ★ 构建多系统导航文件列表 (GPS/GLO/BDS/GAL)
        String[] navFiles = buildNavFileArray();
        int[] navIndex = new int[navFiles.length]; // 全部为0，同属一个接收机
        boolean hasNavFile = navFiles.length > 0;

        if (!hasNavFile) {
            Log.w("SPP", "星历文件不存在 navFilePaths=" + java.util.Arrays.toString(MainActivity.navFilePaths) + " | myFolder目录存在=" + new File(com.ecut.pnav.MainActivity.outputFilePath[0]).getParentFile().exists());
        }

        File brdc = (infile[0] != null) ? new File(infile[0]) : new File("/nonexistent");
        File prec = (infile[1] != null) ? new File(infile[1]) : new File("/nonexistent");
        File clk  = (infile[2] != null) ? new File(infile[2]) : new File("/nonexistent");
        //读取广播星历文件
        if (hasNavFile) {
            try {
                // ★ read_obsnav() 在纯星历模式下因 obs.n==0 返回0，但星历已正确填充到 tempnavs
                read_obsnav(ts, te, ti, navFiles, navIndex, navFiles.length, prcopt, obss, tempnavs, stas);
                if (tempnavs.n > 0 || tempnavs.ng > 0 || tempnavs.ns > 0) {
                    navs = tempnavs;
                    tips.append("\n").append("Eph file read successfully.");
                    // ★ 诊断广播星历新鲜度
                    String freshness = measure_util.checkEphFreshness(navs);
                    tips.append("\n").append("星历新鲜度: ").append(freshness);
                    Log.d("SPP", "星历新鲜度: " + freshness);
                    mText.postValue(String.valueOf(tips));
                    Log.d("SPP", "Read successfully. n=" + navs.n + " ng=" + navs.ng + " ns=" + navs.ns + " ne=" + navs.getNe());
                    // 更新星历详情到定位页面信息栏
                    measure_util.getEphDetail(mContext, navs, navFiles[0]);
                } else {
                    tips.append("\n").append("Eph file read failed.");
                    mText.postValue(String.valueOf(tips));
                }
            } catch (IOException | CloneNotSupportedException e) {
                Log.e("SppViewModel", "Failed to read ephemeris", e);
                tips.append("\n").append("Eph read error: ").append(e.getMessage());
            }
        } else {
            tips.append("\n").append("No eph file available, please update the eph.");
        }
        // ── 读取精密星历文件（SP3格式，自带钟差，不需要CLK） ──
        if (sateph == 1) {
            if (prec.exists()) {
                try {
                    String[] precInfile = new String[]{infile[1]};
                    postpos.readpreceph(precInfile, 1, prcopt, navs, sbss);
                    if (navs.getNe() > 0) {
                        tips.append("\n").append("NRT精密星历加载成功 ne=").append(navs.getNe())
                                .append(" nc=").append(navs.getNc());
                        Log.d("SPP", "NRT精密星历加载成功 ne=" + navs.getNe() + " nc=" + navs.getNc());
                        // ★ 精密星历可用 → 切换解算到精密模式，否则 pnt_pos 仍用空广播星历
                        prcopt.setSateph(1);
                        MainActivity.sateph = 1;
                        measure_util.getEphDetail(mContext, navs, infile[1]); // 更新星历详情
                    } else {
                        tips.append("\n").append("NRT精密星历加载失败(ne=0)，回退广播星历");
                        Log.w("SPP", "NRT精密星历加载失败 ne=0，回退广播");
                        prcopt.setSateph(0);
                        MainActivity.sateph = 0;
                    }
                } catch (IOException | CloneNotSupportedException e) {
                    Log.e("SPP", "NRT精密星历读取异常", e);
                    tips.append("\n").append("NRT精密星历读取异常: ").append(e.getMessage());
                    prcopt.setSateph(0);
                    MainActivity.sateph = 0;
                }
            } else {
                tips.append("\n").append("NRT SP3文件不存在，回退广播星历");
                Log.w("SPP", "NRT SP3缺失，回退广播");
                prcopt.setSateph(0);
                MainActivity.sateph = 0;
            }
        }
        Log.d(TAG, "READDATA 出口: nav.n=" + navs.n + " nav.ng=" + navs.ng + " nav.ne=" + navs.getNe()
            + " prcopt.sateph=" + prcopt.getSateph() + " MainActivity.sateph=" + MainActivity.sateph);
    }

    public void processGnssMeasurements(GnssMeasurementsEvent eventArgs){
        msg = new StringBuilder("");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date currentTime = new Date();
        String currentTimeString = sdf.format(currentTime);
        List<GnssMeasurement> measurements = new ArrayList<>(eventArgs.getMeasurements());
        if (DIAG)
            Log.d(TAG, "EPOCH " + currentTimeString + " rawMeas=" + measurements.size() + " nav.n=" + navs.n + " nav.ng=" + navs.ng + " ne=" + navs.getNe() + " sateph=" + prcopt.getSateph());
        // ★ DIAG: 原始测量数量
        if (DIAG)
            Log.d("DIAG_SPP", "=== Epoch " + currentTimeString + " === rawMeas=" + measurements.size());
        measure_util.prepareEpochLargestTows(measurements);
        for (GnssMeasurement measurement : measurements) {
            measure_util.readobs(measurement, eventArgs.getClock(), obs);
        }
        int count = measure_util.isSpp(obs, navs);
        // ★ DIAG: 有效卫星（有星历匹配的）
        if (DIAG)
            Log.d("DIAG_SPP", "isSpp valid_sat=" + count + " navs.n=" + navs.n + " navs.ng=" + navs.ng + " ne=" + navs.getNe() + " nc=" + navs.getNc() + " sateph=" + prcopt.getSateph() + " prcopt.mode=" + prcopt.getMode() + " navsys=" + prcopt.getNavsys());
        // ★ 结构化日志：历元输入摘要
        StringBuilder satLog = new StringBuilder();
        int loggedSats = 0;
        for (obsd_t ob : obs) {
            if (ob.sat[0] != 0 && loggedSats < 20) {
                if (loggedSats > 0) satLog.append("; ");
                satLog.append("PRN").append(ob.sat[0])
                      .append(":P=").append(String.format("%.1f", ob.P[0]))
                      .append(",L=").append(String.format("%.3f", ob.L[0]))
                      .append(",D=").append(String.format("%.1f", ob.D[0]));
                loggedSats++;
            }
        }
        SessionLogger.sol("SPP", "EPOCH " + currentTimeString + " | raw=" + measurements.size() + " valid=" + count + " nav.n=" + navs.n + " nav.ng=" + navs.ng);
        SessionLogger.sol("SPP", "SATS " + satLog.toString());
        // ★ DIAG: 列出前6颗卫星的 PRN+伪距
        StringBuilder satDiag = new StringBuilder("obs_sats: ");
        int satCnt = 0;
        for (obsd_t ob : obs) {
            if (ob.sat[0] != 0 && satCnt < 10) {
                satDiag.append("PRN").append(ob.sat[0]).append("(P=").append(String.format("%.1f", ob.P[0])).append(") ");
                satCnt++;
            }
        }
        if (DIAG)
            Log.d("DIAG_SPP", satDiag.toString());
        if (count >= 4) {
            satcountText.postValue(String.valueOf(count));
            nn = 0;//判断观测数据是否来自同一个历元
            try {
                n = (int) Arrays.stream(obs)
                        .filter(o -> o.sat[0] != 0)
                        .count();
                obsd_t[] sortobss = measure_util.sortobs(obs);
                for (int i = 1; i < n; i++) { // 从 1 开始，因为 0 已经被比较
                    if (sortobss[0].time.time != sortobss[i].time.time) {
                        tips.append("\n")
                                .append(currentTimeString)
                                .append(": The ")
                                .append(i)
                                .append("th one has an anomaly: Satellite: ")
                                .append(sortobss[i].sat[0])
                                .append(" error occurred");
                        flushLog();
                        break; // 找到异常后立即退出循环
                    }else{
                        nn++;
                    }
                }
                if (nn == n-1) {
                    //进行单点定位
                    // ★ DIAG: 统计各系统卫星数
                    int gpsCnt=0, gloCnt=0, galCnt=0, bdsCnt=0;
                    for (int si=0; si<n; si++) {
                        int s = sortobss[si].sat[0];
                        if (s>=1 && s<=32) gpsCnt++;
                        else if (s>=33 && s<=59) gloCnt++;
                        else if (s>=60 && s<=96) galCnt++;
                        else if (s>=97 && s<=159) bdsCnt++;
                    }
                    if (DIAG) {
                        Log.d("DIAG_SPP", "pnt_pos: n=" + n + " GPS=" + gpsCnt + " GLO=" + gloCnt + " GAL=" + galCnt + " BDS=" + bdsCnt
                            + " | mode=" + prcopt.getMode() + " navsys=" + prcopt.getNavsys() + " sateph=" + prcopt.getSateph()
                            + " iono=" + prcopt.getIonoopt() + " trop=" + prcopt.getTropopt() + " elmin=" + String.format("%.1f", prcopt.getElmin()/D2R) + "°"
                            + " | sample_sat=" + sortobss[0].sat[0] + " time=" + sortobss[0].time.time + " P[0]=" + String.format("%.1f", sortobss[0].P[0]));
                    }
                    if (DIAG)
                        Log.d("DIAG_SPP", "sol.rr init: [" + String.format("%.1f", sol.rr[0]) + ", " + String.format("%.1f", sol.rr[1]) + ", " + String.format("%.1f", sol.rr[2]) + "]");
                    int state;
                    try {
                        state = pnt_pos(sortobss, n, navs, prcopt, sol, null, null, msg1);
                    } catch (Exception e) {
                        Log.e("DIAG_SPP", "pnt_pos CRASH: " + e.getClass().getName() + " - " + e.getMessage(), e);
                        SessionLogger.sol("SPP", "CRASH " + e.getClass().getName() + ": " + e.getMessage());
                        state = 0;
                        msg1 = "EX:" + e.getClass().getName() + ":" + e.getMessage();
                    }
                    if (DIAG)
                        Log.d(TAG, "pnt_pos: state=" + state + " sol.stat=" + sol.stat + " rr=[" + String.format("%.1f", sol.rr[0]) + "," + String.format("%.1f", sol.rr[1]) + "," + String.format("%.1f", sol.rr[2]) + "]");
                    if (DIAG)
                        Log.d("DIAG_SPP", "pnt_pos result=" + state + " sol.stat=" + sol.stat + " sol.rr=[" + String.format("%.1f", sol.rr[0]) + "," + String.format("%.1f", sol.rr[1]) + "," + String.format("%.1f", sol.rr[2]) + "]");
                    if (state == 0) {
                        updateVAccuracy.postValue(0+"m");
                        updateNAccuracy.postValue(0+"m");
                        updateUAccuracy.postValue(0+"m");
                        resetobs(obs);
                        tips.append("\n").append(currentTimeString.substring(11)).append(" SPP FAIL ").append(count).append("sat ").append(msg.toString().replace("\n"," "));
                        // ★ 详细诊断：系统分布 + 星历状态 + 观测数，定位失败原因
                        SessionLogger.sol("SPP", "FAIL " + currentTimeString
                            + " | nsat=" + count
                            + " | obs_n=" + n
                            + " | GPS=" + gpsCnt + " GLO=" + gloCnt + " GAL=" + galCnt + " BDS=" + bdsCnt
                            + " | nav.n=" + navs.n + " nav.ng=" + navs.ng + " nav.ne=" + navs.getNe()
                            + " | sateph=" + prcopt.getSateph()
                            + " | err=" + msg1.replace("\n"," "));
                        flushLog();
                    } else {
                        ecef2pos(sol.rr, pos);
                        deg2dms(pos[0] * R2D, dms1, 5);
                        deg2dms(pos[1] * R2D, dms2, 5);
                        if (DIAG)
                            Log.d("DIAG_SPP", "SUCCESS lat=" + String.format("%.6f", pos[0]*R2D) + " lon=" + String.format("%.6f", pos[1]*R2D) + " h=" + String.format("%.2f", pos[2]));
                        // ★ 先写基础 RESULT（不依赖 sol.qr，避免 NPE 阻断整个 SUCCESS 路径）
                        SessionLogger.sol("SPP", "RESULT " + currentTimeString
                            + " | lat=" + String.format("%.8f", pos[0]*R2D)
                            + " lon=" + String.format("%.8f", pos[1]*R2D)
                            + " h=" + String.format("%.3f", pos[2])
                            + " | n=" + n);
                        dms1[0] = (int)dms1[0];
                        dms1[1] = (int)dms1[1];
                        dms2[0] = (int)dms2[0];
                        dms2[1] = (int)dms2[1];
                        current_lat = dms1;
                        current_lon = dms2;

                        String formattedNumber_lat = df.format(current_lat[2]);
                        String formattedNumber_lon = df.format(current_lon[2]);
                        latvalue.postValue(current_lat[0]+"°"+current_lat[1]+"′"+formattedNumber_lat+"″");
                        lonvalue.postValue(current_lon[0]+"°"+current_lon[1]+"′"+formattedNumber_lon+"″");
                        String strdms1 = Arrays.stream(dms1)
                                .mapToObj(String::valueOf) // 将 int 转换为 String
                                .collect(Collectors.joining(", ")); // 使用逗号连接
                        String strdms2 = Arrays.stream(dms2)
                                .mapToObj(String::valueOf) // 将 int 转换为 String
                                .collect(Collectors.joining(", ")); // 使用逗号连接
                        String height1 = df.format(pos[2]);
                        height.postValue(height1+"m");
                        // ★ sol.qr 可能未初始化，null 安全访问
                        double[] P = new double[9];
                        double[] Q = new double[9];
                        soltocov(sol,P);
                        covenu(pos,P,Q);
                        updateVAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[0])))+"m");
                        updateNAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[4])))+"m");
                        updateUAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[8])))+"m");
                        accuracyTracker.record(Math.sqrt(Math.abs(Q[0])), Math.sqrt(Math.abs(Q[4])), Math.sqrt(Math.abs(Q[8])));
                        SolutionMetrics.post("SPP",
                                Math.sqrt(Math.abs(Q[0]) + Math.abs(Q[4])),
                                Math.sqrt(Math.abs(Q[8])), count,
                                pos[0] * R2D, pos[1] * R2D);
                        // ★ 精度信息单独写入（Q 来自 covenu，9元素安全）
                        SessionLogger.sol("SPP", "PREC " + currentTimeString
                            + " | sde=" + df1.format(Math.sqrt(Math.abs(Q[0])))
                            + " sdn=" + df1.format(Math.sqrt(Math.abs(Q[4])))
                            + " sdu=" + df1.format(Math.sqrt(Math.abs(Q[8]))));
                        // 解算历史行
                        solHistoryLine.postValue(String.format("#%d %s SPP %.5f° %.5f° %s",
                                accuracyTracker.epochCount.getValue(), currentTimeString,
                                pos[0] * R2D, pos[1] * R2D, height1));
                        solstate.postValue("SPP");
                        changeTextColor(android.graphics.Color.rgb(255, 149, 0)); // orange = single
                        String strqr = "[]";
                        if (sol.qr != null) {
                            float[] temp_qr = new float[sol.qr.length];
                            for (int i = 0; i < sol.qr.length; i++) {
                                temp_qr[i] = (float) Math.sqrt(Math.abs(sol.qr[i]));
                            }
                            strqr = Arrays.toString(temp_qr);
                        }
                        // 写入定位数据库供地图显示
                        insertDataAsync(db, currentTimeString, strdms1, strdms2, height1, strqr, "5");
                        StringBuilder sb = new StringBuilder();
                        solopt_default.timef=0;

                        if(auto_record_mode){
                            out_sol(sol, solopt_default, sb);
                            try {
                                if (bufferedWriter != null) {
                                    bufferedWriter.write(sb.toString());
                                    bufferedWriter.flush();
                                }
                            } catch (IOException e) {
                                Log.e("SppViewModel", "Failed to write solution", e);
                            }
                        }else {
                            if (if_auto_record) {
                                out_sol(sol, solopt_default, sb);
                                try {
                                    if (bufferedWriter != null) {
                                        bufferedWriter.write(sb.toString());
                                        bufferedWriter.flush();
                                    }
                                } catch (IOException e) {
                                    Log.e("SppViewModel", "Failed to write solution", e);
                                }
                            }
                            if_auto_record = false;
                        }
                        tips.append(String.format("\n%s SPP OK %dsat %.6f %.6f %s E:%s N:%s U:%s",
                                currentTimeString.substring(11), count,
                                pos[0] * R2D, pos[1] * R2D, height1,
                                df1.format(Math.sqrt(Math.abs(Q[0]))),
                                df1.format(Math.sqrt(Math.abs(Q[4]))),
                                df1.format(Math.sqrt(Math.abs(Q[8])))));
                        n = 0;
                        flushLog();
                    }
                } else {
                    resetobs(obs);
                }
            } catch (CloneNotSupportedException e) {
                Log.e("SppViewModel", "CloneNotSupported in processGnssMeasurements", e);
                resetobs(obs);
            }
        }else{
            satcountText.postValue(String.valueOf(measure_util.isSpp(obs, navs)));
            // ★ 卫星数不足 4，输出诊断（含星历状态，判断是观测不足还是星历缺失）
            Log.w(TAG, "EPOCH SKIP " + currentTimeString + " | count=" + count + " nav.n=" + navs.n + " nav.ng=" + navs.ng + " ne=" + navs.getNe() + " sateph=" + prcopt.getSateph() + " — 卫星数不足4颗");
            SessionLogger.sol("SPP", "SKIP " + currentTimeString
                + " | nsat=" + count + " (<4)"
                + " | nav.n=" + navs.n + " nav.ng=" + navs.ng + " nav.ne=" + navs.getNe()
                + " | sateph=" + prcopt.getSateph());
        }
    }
    public static void out_head(BufferedWriter bufferedWriter, prcopt_t opt, solopt_t sopt) {
        int i;
        int[] sys = {SYS_GPS, SYS_GLO, SYS_GAL, SYS_QZS, SYS_CMP, SYS_IRN, SYS_SBS, 0};
        String[] s1 = {"Single", "DGNSS", "Kinematic", "Static", "Moving-Base", "Fixed",
                "PPP Kinematic", "PPP Static", "PPP Fixed", "", "", ""};
        String[] s2 = {"L1", "L1+2", "L1+2+3", "L1+2+3+4", "L1+2+3+4+5", "L1+2+3+4+5+6", "", "", ""};
        String[] s3 = {"Forward", "Backward", "Combined", "", "", ""};
        String[] s4 = {"OFF", "Broadcast", "SBAS", "Iono-Free LC", "Estimate TEC", "IONEX TEC",
                "QZSS Broadcast", "", "", "", ""};
        String[] s5 = {"OFF", "Saastamoinen", "SBAS", "Estimate ZTD", "Estimate ZTD+Grad", "", "", ""};
        String[] s6 = {"Broadcast", "Precise", "Broadcast+SBAS", "Broadcast+SSR APC",
                "Broadcast+SSR CoM", "", "", ""};
//        String[] s7 = {"GPS", "GLONASS", "Galileo", "QZSS", "BDS", "NavIC", "SBAS", "", "", ""};
        String[] s7 = {"GPS", "BDS", "GLONASS", "Galileo"};
        String out = "% program   : RTKLIB(Java) ver.1.0";

        String[] s11 = {"WGS84", "Tokyo"};
        String[] s22 = {"ellipsoidal", "geodetic"};
        String[] s33 = {"GPST", "UTC ", "JST "};
        char[] sep = opt2sep(sopt);
        String leg1 = "Q=1:fix,2:float,3:sbas,4:dgps,5:single,6:ppp";
        String leg2 = "ns=# of satellites";

        StringBuilder sb2 = new StringBuilder();

        if (sopt.outhead != 0) {
            sb2.append(String.format("%s (", COMMENTH));
            if (sopt.posf == SOLF_XYZ)
                sb2.append("x/y/z-ecef=WGS84");
            else if (sopt.posf == SOLF_ENU)
                sb2.append("e/n/u-baseline=WGS84");
            else
                sb2.append(String.format("lat/lon/height=%s/%s", s11[sopt.datum], s22[sopt.height]));
            sb2.append(String.format(",%s,%s)\r\n", leg1, leg2));
        }
        sb2.append(String.format("%s  %s       ", COMMENTH, s33[sopt.times]))
                .append(sep[0]);
        if (sopt.posf == SOLF_LLH) { /* lat/lon/hgt */
            if (sopt.degf != 0) {
                sb2.append(String.format("%16s%s%16s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s"
                                , "latitude(d'\")", sep[0], "longitude(d'\")", sep[0], "height(m)",
                                sep[0], "Q", sep[0], "ns", sep[0], "sdn(m)", sep[0], "sde(m)", sep[0], "sdu(m)",
                                sep[0], "sdne(m)", sep[0], "sdeu(m)", sep[0], "sdue(m)", sep[0], "age(s)",
                                sep[0], "ratio"))
                        .append("\n");
            } else {
                sb2.append(String.format("%14s%s%14s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s",
                                "latitude(deg)", sep[0], "longitude(deg)", sep[0], "height(m)", sep[0],
                                "Q", sep[0], "ns", sep[0], "sdn(m)", sep[0], "sde(m)", sep[0], "sdu(m)", sep[0],
                                "sdne(m)", sep[0], "sdeu(m)", sep[0], "sdun(m)", sep[0], "age(s)", sep[0],
                                "ratio"))
                        .append("\n");
            }
            if (sopt.outvel != 0) {
                sb2.append(String.format("%s%10s%s%10s%s%10s%s%9s%s%8s%s%8s%s%8s%s%8s%s%8s",
                                sep[0], "vn(m/s)", sep[0], "ve(m/s)", sep[0], "vu(m/s)", sep[0], "sdvn", sep[0],
                                "sdve", sep[0], "sdvu", sep[0], "sdvne", sep[0], "sdveu", sep[0], "sdvun"))
                        .append("\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s pos mode  : %s\r\n", COMMENTH, s1[opt.mode]));
        if (PMODE_DGPS <= opt.mode && opt.mode <= PMODE_FIXED) {
            sb.append(String.format("%s freqs     : %s\r\n", COMMENTH, s2[opt.getNf() - 1]));
        }
        if (opt.mode > PMODE_SINGLE) {
            sb.append(String.format("%s solution  : %s\r\n", COMMENTH, s3[opt.getSoltype()]));
        }
        sb.append(String.format("%s elev mask : %.2f deg\r\n", COMMENTH, opt.getElmin() * R2D));
        if (opt.mode > PMODE_SINGLE) {
            sb.append(String.format("%s dynamics  : %s\r\n", COMMENTH, opt.getDynamics() == 1 ? "on" : "off"));
            sb.append(String.format("%s tidecorr  : %s\r\n", COMMENTH, opt.getTidecorr() == 1 ? "on" : "off"));
        }
        if (opt.mode <= PMODE_FIXED) {
            sb.append(String.format("%s ionos opt : %s\r\n", COMMENTH, s4[opt.ionoopt]));
        }
        sb.append(String.format("%s tropo opt : %s\r\n", COMMENTH, s5[opt.tropopt]));
        sb.append(String.format("%s ephemeris : %s\r\n", COMMENTH, s6[opt.getSateph()]));
        sb.append(String.format("%s navi sys  :", COMMENTH));
//        for (i = 0; sys[i] == 1; i++) {
//            if ((opt.navsys & sys[i]) == 1)
//                sb.append(String.format(" %s", s7[i]));
//        }
//        for(int ii=0;ii<7;ii++){
//            if(opt.navsys == sys[ii]){
//                sb.append(String.format(" %s", s7[ii]));
//            }
//        }
        for(int ii=0;ii<checked.length;ii++){
            if(checked[ii])
                sb.append(String.format(" %s", s7[ii]));
        }
        sb.append("\r\n");
        try {
            bufferedWriter.write(out);
            bufferedWriter.newLine();//换行写入
            bufferedWriter.write(sb.toString());
            bufferedWriter.newLine();//换行写入
            bufferedWriter.write(sb2.toString());
            bufferedWriter.flush();
        } catch (IOException e) {
            Log.e("SppViewModel", "Failed to write output header", e);
        }
    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<String> getsatcountText() {
        return satcountText;
    }
    public MutableLiveData<String> getlatvalue() {
        return latvalue;
    }
    public MutableLiveData<String> getlonvalue() {
        return lonvalue;
    }
    public MutableLiveData<String> getelevation() {
        return height;
    }
    public LiveData<String> getUpdateStatus() {
        return updateStatus;
    }
    public LiveData<String> getUpdateVAccuracy() {
        return updateVAccuracy;
    }
    public LiveData<String> getUpdateNAccuracy() {
        return updateNAccuracy;
    }
    public LiveData<String> getUpdateUAccuracy() {
        return updateUAccuracy;
    }
    public LiveData<String> getsolstate() {
        return solstate;
    }
    public LiveData<Integer> getTextColor() {
        return textColor;
    }
    public void changeTextColor(int color) {
        textColor.postValue(color);
    }

    /** 将上次 post 后新增的日志行推送到 logLine（增量），同时维护环形缓冲区 */
    private void flushLog() {
        String full = tips.toString();
        if (full.length() <= lastLogLength) return;
        String newText = full.substring(lastLogLength);
        lastLogLength = full.length();
        String[] newLines = newText.split("\n");
        for (String line : newLines) {
            if (line.isEmpty()) continue;
            if (logRing.size() >= MAX_LOG_LINES) {
                logRing.remove(0);
            }
            logRing.add(line);
            logLine.postValue(line);
        }
        // 超出上限时重建 tips
        if (logRing.size() >= MAX_LOG_LINES) {
            tips.setLength(0);
            for (String l : logRing) {
                tips.append(l).append("\n");
            }
            lastLogLength = tips.length();
        }
    }

    public LiveData<String> getLogLine() { return logLine; }
    public LiveData<String> getSolHistoryLine() { return solHistoryLine; }

    public void clearLog() {
        tips.setLength(0);
        logRing.clear();
        lastLogLength = 0;
    }

    public LiveData<String> getEphModeLabel() { return ephModeLabel; }
    public LiveData<Integer> getEphSelection() { return ephSelection; }

    /** 用户选择星历模式 */
    public void setEphMode(int mode) {
        if (mode < 0 || mode > 2) return;
        MainActivity.ephForceMode = mode;
        ephSelection.setValue(mode);
        switch (mode) {
            case 0: ephModeLabel.setValue("自动"); break;
            case 1: ephModeLabel.setValue("广播星历"); break;
            case 2: ephModeLabel.setValue("NRT精密"); break;
        }
        Log.d("SPP", "星历模式切换: " + ephModeLabel.getValue() + " → 下次刷新时生效");
        updateStatus.postValue(EPH_MODE_SAVED);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        SessionLogger.sol("SPP", "SESSION END — total epochs=" + accuracyTracker.epochCount.getValue());
        handler.removeCallbacksAndMessages(null);
        try {
            if (bufferedWriter != null) {
                bufferedWriter.flush();
                bufferedWriter.close();
            }
        } catch (IOException e) {
            Log.e("SppViewModel", "Error closing writer in onCleared", e);
        }
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

}