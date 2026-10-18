package com.ecut.pnav.ui.PPP;

import static com.ecut.pnav.MainActivity.auto_record_mode;
import static com.ecut.pnav.MainActivity.checked;
import static com.ecut.pnav.MainActivity.if_auto_record;
import static com.ecut.pnav.MainActivity.outputFilePath;
import static com.ecut.pnav.ui.measure_util.calculateSelectedValue;
import static com.ecut.pnav.ui.measure_util.isSpp;
import static com.ecut.pnav.ui.measure_util.out_sol;
import static com.ecut.pnav.ui.measure_util.out_sol_record;
import static com.ecut.pnav.ui.measure_util.prepareEpochLargestTows;
import static com.ecut.pnav.ui.measure_util.readobs;
import static com.ecut.pnav.ui.measure_util.resetobs;
import static com.ecut.pnav.ui.measure_util.solopt_default;
import static com.ecut.pnav.ui.measure_util.sortobs;
import static com.ecut.pnav.positiondatabase.insertData;
import static com.ecut.pnav.positiondatabase.insertFileRecordAsync;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import static com.rtklib.bean.nav_t.getMaxSat;
import static com.rtklib.pntpos.rescode.R2D;
import static com.rtklib.pntpos.rescode.ecef2pos;
import static com.rtklib.postpos.outSol.SOLF_LLH;
import static com.rtklib.postpos.outSol.covenu;
import static com.rtklib.postpos.outSol.deg2dms;
import static com.rtklib.postpos.outSol.opt2sep;
import static com.rtklib.postpos.outSol.soltocov;
import static com.rtklib.postpos.procPos.pppnx;
import static com.rtklib.readobsnav.readrnxh.SYS_CMP;
import static com.rtklib.readobsnav.readObsNav.read_obsnav;
import static com.rtklib.rtkpos.rtkPos.rtk_pos;
import static com.rtklib.util.math.zeros;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import com.ecut.pnav.ui.AccuracyTracker;
import com.ecut.pnav.ui.SolutionMetrics;
import com.ecut.pnav.ui.FtpConnection;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.Ntrip.NtripFragment;
import com.ecut.pnav.positiondatabase;
import com.ecut.pnav.ui.Sol.SolViewModel;
import com.ecut.pnav.util.SessionLogger;
import com.ecut.pnav.util.StorageUtil;
import com.rtklib.bean.ambc_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.rtk_t;
import com.rtklib.bean.sbs_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.bean.ssat_t;
import com.rtklib.bean.sta_t;
import com.rtklib.postpos.postpos;
import com.rtklib.rtkpos.udState;

import org.apache.commons.net.ftp.FTPClient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PppViewModel extends ViewModel {
    // 星历更新 Toast 状态 token —— Fragment 观察端映射为本地化文案
    public static final String EPH_CORS_UPDATED = "eph_cors_updated";
    public static final String EPH_PATH_NULL = "eph_path_null";
    public static final String EPH_UPTODATE = "eph_uptodate";
    public static final String EPH_UPDATE_OK = "eph_updated_ok";
    public static final String EPH_UPDATE_FAILED = "eph_update_failed";
    String str_name_value = "";
    String str_name = "";
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> latvalue;
    private final MutableLiveData<String> lonvalue;
    private final MutableLiveData<String> height;
    private final MutableLiveData<String> ppptips;
    private final MutableLiveData<String> solstate;
    private final MutableLiveData<String> updateStatus;
    private final MutableLiveData<String> updateRatio;
    private final MutableLiveData<Integer> textColor;
    private final MutableLiveData<String> updateVAccuracy;
    private final MutableLiveData<String> updateHAccuracy;
    private final MutableLiveData<String> updateUAccuracy;
    public final AccuracyTracker accuracyTracker = new AccuracyTracker();
    private final MutableLiveData<String> solHistoryLine = new MutableLiveData<>();
    private final MutableLiveData<String> updateStrName;
    private final MutableLiveData<String> updateStrNameValue;
    private final DecimalFormat df = new DecimalFormat("#.###");
    private final DecimalFormat df1 = new DecimalFormat("#.#####");
    LocationManager mLocationManager;
    private static obsd_t[] obs = new obsd_t[getMaxSat()];
    static StringBuilder tips = new StringBuilder();

    // 环形缓冲区：最多保留 500 行，增量推送避免全量重绘
    private static final int MAX_LOG_LINES = 500;
    private final List<String> logRing = new ArrayList<>(MAX_LOG_LINES + 10);
    private final MutableLiveData<String> logLine = new MutableLiveData<>();
    private int lastLogLength = 0;
    double[] current_lat = new double[3];
    double[] current_lon = new double[3];
    double elevation = 0;
    SQLiteDatabase db;
    gtime_t ts = new gtime_t(0, 0);
    gtime_t te = new gtime_t(0, 0);
    double ti = 0.0;
    int[] index = {0};
    static nav_t navs = new nav_t();
    nav_t tempnavs = new nav_t();
    static obs_t obss = new obs_t();
    sta_t[] stas = new sta_t[3];
    pcv_t[] pcv = new pcv_t[3];
    sbs_t sbss = new sbs_t();
    int n = 0;

    private rtk_t rtk = new rtk_t();
    private prcopt_t prcopt;
    double[] pos = new double[3];
    double[] dms1 = new double[3];
    double[] dms2 = new double[3];
    BufferedWriter bufferedWriter;
    StringBuilder msg;
    String msg1 = "";
    private FTPClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private static final String COMMENTH = "%";
    private ArrayList<String> ave_result = new ArrayList<>();
    private static final int PMODE_PPP_KINEMA = 7;

    public PppViewModel(Context context) {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        String formattedTime = StorageUtil.getTimestamp();
        mText = new MutableLiveData<>();
        latvalue = new MutableLiveData<>();
        lonvalue = new MutableLiveData<>();
        height = new MutableLiveData<>();
        ppptips = new MutableLiveData<>();
        solstate = new MutableLiveData<>();
        updateStatus = new MutableLiveData<>();
        updateRatio = new MutableLiveData<>();
        textColor = new MutableLiveData<>();

        positiondatabase dbHelper = new positiondatabase(context);
        db = dbHelper.getWritableDatabase();

        updateVAccuracy = new MutableLiveData<>();
        updateHAccuracy = new MutableLiveData<>();
        updateUAccuracy = new MutableLiveData<>();
        updateStrName = new MutableLiveData<>();
        updateStrNameValue = new MutableLiveData<>();
        updateVAccuracy.setValue("0m");
        updateHAccuracy.setValue("0m");
        mText.setValue("0");
        updateRatio.setValue("0");
        latvalue.setValue(current_lat[0] + "°" + current_lat[1] + "′" + current_lat[2] + "″");
        lonvalue.setValue(current_lon[0] + "°" + current_lon[1] + "′" + current_lon[2] + "″");
        height.setValue(elevation + "m");
        tips.append("PPP Session Start");
        Log.d("PPP_DIAG", "══════ PPP SESSION START " + formattedTime + " ══════");
        prcopt = new prcopt_t(SolViewModel.prcopt); // 实例副本避免冲突
        SessionLogger.sol("PPP", "SESSION START — navsys=" + prcopt.getNavsys() + " mode=" + prcopt.getMode());
        // ★ PPP 核心参数配置
        prcopt.mode = PMODE_PPP_KINEMA;       // 运动PPP
        prcopt.setSateph(1);                   // 精密星历
        prcopt.ionoopt = 3;                    // 消电离层组合 (Iono-Free LC)
        prcopt.tropopt = 3;                    // 估计ZTD
        prcopt.refpos = 1;
        prcopt.setNf(1);                      // 消电离层组合仅需单频观测值内部组合
        // 读取星历文件（广播+精密）—— 延迟到 startScheduledTask 后台线程处理，避免阻塞UI
        if (outputFilePath == null || outputFilePath.length == 0 || outputFilePath[0] == null) {
            tips.append("\n").append("No eph file path, please update eph first.");
        }
        for (int i = 0; i < getMaxSat(); i++) {
            obs[i] = new obsd_t();
        }
        // 初始化pcv
        for (int i = 0; i < getMaxSat(); i++) {
            navs.pcvs[i] = new pcv_t();
        }
        for (int i = 0; i < 3; i++) {
            pcv[i] = new pcv_t();
        }
        solstate.postValue("收敛中");
        rtkinit(rtk, prcopt);
        flushLog();

        File newFolder = StorageUtil.getDataDir(context);
        try {
            File out_folder = new File(newFolder, "PPP" + formattedTime + ".pos");
            FileWriter fileWriter = new FileWriter(out_folder);
            bufferedWriter = new BufferedWriter(fileWriter);
            if (if_auto_record) {
                out_head(bufferedWriter, prcopt, solopt_default);
            } else {
                out_head_record(bufferedWriter, prcopt, solopt_default);
            }
            insertFileRecordAsync(db, out_folder.getAbsolutePath(), out_folder.getName(), "POS", "PPP", formattedTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void startScheduledTask(Context context, String[] infile, String[] outputFilePath) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        // ★ CORS 已连接 → 直接从 RTCM 实时解码获取星历；若CORS不发电文，回退到文件星历
                        if (MyService.isServiceRunning) {
                            nav_t corsNav = NtripFragment.extractRtcmNav();
                            if (corsNav != null && (corsNav.n > 0 || corsNav.ng > 0)) {
                                navs = corsNav;
                                Log.d("PPP", "CORS星历更新成功 n=" + corsNav.n + " ng=" + corsNav.ng);
                                updateStatus.postValue(EPH_CORS_UPDATED);
                                handler.postDelayed(this, 30 * 1000);
                                return;
                            }
                            // CORS无星历 → 不return，继续尝试文件星历
                            Log.w("PPP", "CORS星历为空，回退到文件星历");
                        }
                        if (outputFilePath == null || outputFilePath.length == 0 || outputFilePath[0] == null) {
                            updateStatus.postValue(EPH_PATH_NULL);
                            Log.e("PPP", "Output path is null");
                            handler.postDelayed(this, 30 * 60 * 1000);
                            return;
                        }
                        // 星历有效期仅±2小时，每30分钟重新下载确保星历不过期
                        File existingEph = new File(outputFilePath[0]);
                        if (existingEph.exists()) {
                            if (FtpConnection.isBroadcastFresh(new String[]{outputFilePath[0]})) {
                                Log.d("PPP", "Ephemeris file is fresh, skip download");
                                updateStatus.postValue(EPH_UPTODATE);
                                readdata(outputFilePath);
                                handler.postDelayed(this, 30 * 60 * 1000);
                                return;
                            }
                            Log.d("PPP", "Ephemeris file expired, re-downloading...");
                        }
                        FTPClient client = FtpConnection.getFTPClient();
                        if (client.isConnected()) {
                            com.ecut.pnav.MainActivity.rebuildRemoteDirPath();
                            com.ecut.pnav.ui.FtpConnection.downloadFile(context, client,
                                    com.ecut.pnav.MainActivity.remoteDirPath, infile, 1);
                            client.logout();
                            client.disconnect();
                            if (outputFilePath != null && outputFilePath.length > 0 && outputFilePath[0] != null) {
                                FtpConnection.decompressGzipFile(infile, outputFilePath);
                            }
                            FtpConnection.cleanupExpiredEphemeris(new File(context.getExternalFilesDir(null), "myFolder"));
                        }
                        updateStatus.postValue(EPH_UPDATE_OK);
                        readdata(outputFilePath);
                    } catch (IOException e) {
                        Log.e("PPP", "An error occurred", e);
                        updateStatus.postValue(EPH_UPDATE_FAILED);
                    }
                }).start();
                handler.postDelayed(this, 30 * 60 * 1000);
            }
        };
        handler.post(runnable);
    }

    public static void out_head_record(BufferedWriter bufferedWriter, prcopt_t opt, solopt_t sopt) throws IOException {
        int[] sys = {1, 2, 4, 8, 16, 32, 64, 0};
        String[] s1 = {"Single", "DGNSS", "Kinematic", "Static", "Moving-Base", "Fixed",
                "PPP Kinematic", "PPP Static", "PPP Fixed", "", "", ""};
        String[] s2 = {"L1", "L1+2", "L1+2+3", "L1+2+3+4", "L1+2+3+4+5", "L1+2+3+4+5+6", "", "", ""};
        String[] s3 = {"Forward", "Backward", "Combined", "", "", ""};
        String[] s4 = {"OFF", "Broadcast", "SBAS", "Iono-Free LC", "Estimate TEC", "IONEX TEC",
                "QZSS Broadcast", "", "", "", ""};
        String[] s5 = {"OFF", "Saastamoinen", "SBAS", "Estimate ZTD", "Estimate ZTD+Grad", "", "", ""};
        String[] s6 = {"Broadcast", "Precise", "Broadcast+SBAS", "Broadcast+SSR APC",
                "Broadcast+SSR CoM", "", "", ""};
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
            if (sopt.posf == 0)
                sb2.append("x/y/z-ecef=WGS84");
            else if (sopt.posf == 1)
                sb2.append("e/n/u-baseline=WGS84");
            else
                sb2.append(String.format("lat/lon/height=%s/%s", s11[sopt.datum], s22[sopt.height]));
            sb2.append(String.format(",%s,%s)\r\n", leg1, leg2));
        }
        sb2.append(String.format("%s  %s       ", COMMENTH, s33[sopt.times])).append(sep[0]);
        if (sopt.posf == SOLF_LLH) {
            sb2.append(String.format("%14s%s%14s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s",
                            "latitude(deg)", sep[0], "longitude(deg)", sep[0], "height(m)", sep[0],
                            "Q", sep[0], "ns", sep[0], "sdn(m)", sep[0], "sde(m)", sep[0], "sdu(m)", sep[0],
                            "sdne(m)", sep[0], "sdeu(m)", sep[0], "sdun(m)", sep[0], "age(s)", sep[0],
                            "ratio"))
                    .append("\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s pos mode  : %s\r\n", COMMENTH, s1[opt.mode]));
        sb.append(String.format("%s freqs     : %s\r\n", COMMENTH, s2[opt.getNf() - 1]));
        sb.append(String.format("%s solution  : %s\r\n", COMMENTH, s3[opt.getSoltype()]));
        sb.append(String.format("%s elev mask : %.2f deg\r\n", COMMENTH, opt.getElmin() * R2D));
        sb.append(String.format("%s dynamics  : %s\r\n", COMMENTH, opt.getDynamics() == 1 ? "on" : "off"));
        sb.append(String.format("%s tidecorr  : %s\r\n", COMMENTH, opt.getTidecorr() == 1 ? "on" : "off"));
        sb.append(String.format("%s ionos opt : %s\r\n", COMMENTH, s4[opt.ionoopt]));
        sb.append(String.format("%s tropo opt : %s\r\n", COMMENTH, s5[opt.tropopt]));
        sb.append(String.format("%s ephemeris : %s\r\n", COMMENTH, s6[opt.getSateph()]));
        sb.append(String.format("%s navi sys  :", COMMENTH));
        for (int ii = 0; ii < checked.length; ii++) {
            if (checked[ii])
                sb.append(String.format(" %s", s7[ii]));
        }
        sb.append("\r\n");
        try {
            bufferedWriter.write(out);
            bufferedWriter.newLine();
            bufferedWriter.write(sb.toString());
            bufferedWriter.newLine();
            bufferedWriter.write(sb2.toString());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void out_head(BufferedWriter bufferedWriter, prcopt_t opt, solopt_t sopt) throws IOException {
        String[] s1 = {"Single", "DGNSS", "Kinematic", "Static", "Moving-Base", "Fixed",
                "PPP Kinematic", "PPP Static", "PPP Fixed", "", "", ""};
        String[] s2 = {"L1", "L1+2", "L1+2+3", "L1+2+3+4", "L1+2+3+4+5", "L1+2+3+4+5+6", "", "", ""};
        String[] s3 = {"Forward", "Backward", "Combined", "", "", ""};
        String[] s4 = {"OFF", "Broadcast", "SBAS", "Iono-Free LC", "Estimate TEC", "IONEX TEC",
                "QZSS Broadcast", "", "", "", ""};
        String[] s5 = {"OFF", "Saastamoinen", "SBAS", "Estimate ZTD", "Estimate ZTD+Grad", "", "", ""};
        String[] s6 = {"Broadcast", "Precise", "Broadcast+SBAS", "Broadcast+SSR APC",
                "Broadcast+SSR CoM", "", "", ""};
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
            if (sopt.posf == 0)
                sb2.append("x/y/z-ecef=WGS84");
            else if (sopt.posf == 1)
                sb2.append("e/n/u-baseline=WGS84");
            else
                sb2.append(String.format("lat/lon/height=%s/%s", s11[sopt.datum], s22[sopt.height]));
            sb2.append(String.format(",%s,%s)\r\n", leg1, leg2));
        }
        sb2.append(String.format("%s  %s       ", COMMENTH, s33[sopt.times])).append(sep[0]);
        if (sopt.posf == SOLF_LLH) {
            sb2.append(String.format("%14s%s%14s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s",
                            "latitude(deg)", sep[0], "longitude(deg)", sep[0], "height(m)", sep[0],
                            "Q", sep[0], "ns", sep[0], "sdn(m)", sep[0], "sde(m)", sep[0], "sdu(m)", sep[0],
                            "sdne(m)", sep[0], "sdeu(m)", sep[0], "sdun(m)", sep[0], "age(s)", sep[0],
                            "ratio"))
                    .append("\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s pos mode  : %s\r\n", COMMENTH, s1[opt.mode]));
        sb.append(String.format("%s freqs     : %s\r\n", COMMENTH, s2[opt.getNf() - 1]));
        sb.append(String.format("%s solution  : %s\r\n", COMMENTH, s3[opt.getSoltype()]));
        sb.append(String.format("%s elev mask : %.2f deg\r\n", COMMENTH, opt.getElmin() * R2D));
        sb.append(String.format("%s dynamics  : %s\r\n", COMMENTH, opt.getDynamics() == 1 ? "on" : "off"));
        sb.append(String.format("%s tidecorr  : %s\r\n", COMMENTH, opt.getTidecorr() == 1 ? "on" : "off"));
        sb.append(String.format("%s ionos opt : %s\r\n", COMMENTH, s4[opt.ionoopt]));
        sb.append(String.format("%s tropo opt : %s\r\n", COMMENTH, s5[opt.tropopt]));
        sb.append(String.format("%s ephemeris : %s\r\n", COMMENTH, s6[opt.getSateph()]));
        sb.append(String.format("%s navi sys  :", COMMENTH));
        for (int ii = 0; ii < checked.length; ii++) {
            if (checked[ii])
                sb.append(String.format(" %s", s7[ii]));
        }
        sb.append("\r\n");
        try {
            bufferedWriter.write(out);
            bufferedWriter.newLine();
            bufferedWriter.write(sb.toString());
            bufferedWriter.newLine();
            bufferedWriter.write(sb2.toString());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rtkinit(rtk_t rtk, prcopt_t opt) {
        int i;
        rtk.sol = new sol_t();
        for (i = 0; i < 6; i++) rtk.rb[i] = 0.0;
        rtk.nx = opt.mode <= udState.PMODE_FIXED ?
                com.rtklib.rtkpos.udState.NX(opt) : pppnx(opt);
        rtk.na = opt.mode <= udState.PMODE_FIXED ?
                com.rtklib.rtkpos.udState.NR(opt) : pppnx(opt);
        rtk.tt = 0.0;
        rtk.x = zeros(rtk.nx, 1);
        rtk.P = zeros(rtk.nx, rtk.nx);
        rtk.xa = zeros(rtk.na, 1);
        rtk.Pa = zeros(rtk.na, rtk.na);
        rtk.nfix = rtk.neb = 0;
        for (i = 0; i < getMaxSat(); i++) {
            rtk.ambc[i] = new ambc_t();
            rtk.ssat[i] = new ssat_t();
            for (int k = 0; k < rtk.ssat[i].pt.length; k++) {
                for (int j = 0; j < rtk.ssat[i].pt[k].length; j++) {
                    rtk.ssat[i].pt[k][j] = new gtime_t(0, 0.0);
                }
            }
        }
        for (i = 0; i < 100; i++) rtk.errbuf[i] = 0;
        rtk.opt = opt;
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

    private void readdata(String[] infile) {
        if (infile == null || infile.length < 3 || infile[0] == null) {
            tips.append("\n").append("No eph file path available, please update eph first.");
            flushLog();
            return;
        }

        // ★ 构建多系统导航文件列表 (GPS/GLO/BDS/GAL)
        String[] navFiles = buildNavFileArray();
        int[] navIndex = new int[navFiles.length];
        boolean hasNavFile = navFiles.length > 0;

        File brdc = new File(infile[0]);
        File prec = new File(infile[1]);
        File clk = new File(infile[2]);
        // 读取广播星历
        if (hasNavFile) {
            try {
                // ★ read_obsnav() 在纯星历模式下因 obs.n==0 返回0，但星历已正确填充到 tempnavs
                read_obsnav(ts, te, ti, navFiles, navIndex, navFiles.length, prcopt, obss, tempnavs, stas);
                if (tempnavs.n > 0 || tempnavs.ng > 0 || tempnavs.ns > 0) {
                    navs = tempnavs;
                    tips.append("\n").append("Broadcast eph loaded successfully.");
                } else {
                    tips.append("\n").append("Broadcast eph read failed.");
                }
            } catch (IOException | CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        } else {
            tips.append("\n").append("No broadcast eph file available.");
        }
        // ★ 读取精密星历 (SP3 + CLK)
        if (prec.exists() && clk.exists()) {
            try {
                postpos.readpreceph(infile, infile.length, prcopt, navs, sbss);
                if (navs.getNe() > 0) {
                    tips.append("\n").append("Precise eph (SP3+CLK) loaded successfully.");
                    tips.append("\n").append("  ne=").append(navs.getNe())
                            .append(" nc=").append(navs.getNc());
                } else {
                    tips.append("\n").append("Precise eph read failed (ne=0).");
                }
            } catch (IOException | CloneNotSupportedException e) {
                tips.append("\n").append("Precise eph read error: ").append(e.getMessage());
            }
        } else {
            tips.append("\n").append("SP3/CLK files not found. PPP needs precise ephemeris.");
            tips.append("\n").append("  SP3: ").append(prec.getAbsolutePath()).append(" exists=").append(prec.exists());
            tips.append("\n").append("  CLK: ").append(clk.getAbsolutePath()).append(" exists=").append(clk.exists());
        }
    }

    public void processGnssMeasurements(GnssMeasurementsEvent eventArgs) {
        msg = new StringBuilder("position failed");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date currentTime = new Date();
        String currentTimeString = sdf.format(currentTime);
        List<GnssMeasurement> measurements = new ArrayList<>(eventArgs.getMeasurements());
        resetobs(obs);
        prepareEpochLargestTows(measurements);
        for (GnssMeasurement measurement : measurements) {
            readobs(measurement, eventArgs.getClock(), obs);
        }

        int roverObsCount = (int) Arrays.stream(obs).filter(o -> o.sat[0] != 0).count();
        Log.d("PPP_DIAG", "=== Epoch ===");
        Log.d("PPP_DIAG", "Rover obs: " + roverObsCount);
        Log.d("PPP_DIAG", "navs.n=" + navs.n + " navs.ng=" + navs.ng + " navs.ne=" + navs.getNe()
                + " prcopt.mode=" + prcopt.getMode() + " navsys=" + prcopt.getNavsys()
                + " ionoopt=" + prcopt.getIonoopt() + " tropopt=" + prcopt.getTropopt()
                + " sateph=" + prcopt.getSateph()
                + " elmin=" + String.format("%.1f", prcopt.getElmin() * (180.0 / Math.PI)) + "°");
        // ★ 结构化日志
        SessionLogger.sol("PPP", "EPOCH " + currentTimeString + " | rover=" + roverObsCount + " | nav.n=" + navs.n + " nav.ng=" + navs.ng + " nav.ne=" + navs.getNe() + " | mode=" + prcopt.getMode() + " iono=" + prcopt.getIonoopt() + " sateph=" + prcopt.getSateph());
        // 列出前10颗卫星
        StringBuilder satDiag = new StringBuilder("rover_sats: ");
        int satCnt = 0;
        for (obsd_t ob : obs) {
            if (ob.sat[0] != 0 && satCnt < 10) {
                satDiag.append("PRN").append(ob.sat[0])
                        .append("(P=").append(String.format("%.1f", ob.P[0])).append(") ");
                satCnt++;
            }
        }
        Log.d("PPP_DIAG", satDiag.toString());
        tips.append("\n").append("[诊断] 流动站:").append(roverObsCount);
        flushLog();

            int valid_sat = isSpp(obs, navs);
            mText.postValue(String.valueOf(valid_sat));
            Log.d("PPP_DIAG", "PPP valid_sat=" + valid_sat + " (need >=4)");
            if (valid_sat >= 4) {
                try {
                    n = (int) Arrays.stream(obs).filter(o -> o.sat[0] != 0).count();
                    obsd_t[] temp = sortobs(obs);
                    // 验证同一历元
                    int nn = 0;
                    for (int i = 1; i < n; i++) {
                        if (temp[0].time.time == temp[i].time.time) {
                            nn++;
                        } else {
                            tips.append("\n").append(currentTimeString)
                                    .append(": Rover anomaly: sat=").append(temp[i].sat[0]);
                        }
                    }
                    if (nn == n - 1 && n >= 4) {
                        // ★ PPP 核心：调用 rtk_pos，内部 mode>=7 时分派到 pppos
                        rtk.opt = prcopt;
                        int result = rtk_pos(rtk, temp, n, navs);
                        Log.d("PPP_DIAG", "rtk_pos result: " + result
                                + " sol.stat=" + rtk.sol.stat + " ratio=" + rtk.sol.ratio);

                        if (result >= 1 && rtk.sol.stat != 0) {
                            ecef2pos(rtk.sol.rr, pos);
                            deg2dms(pos[0] * R2D, dms1, 5);
                            deg2dms(pos[1] * R2D, dms2, 5);
                            Log.d("PPP_DIAG", "PPP SUCCESS lat="
                                    + String.format("%.6f", pos[0] * R2D)
                                    + " lon=" + String.format("%.6f", pos[1] * R2D)
                                    + " h=" + String.format("%.2f", pos[2])
                                    + " sol.stat=" + rtk.sol.stat + " ratio=" + rtk.sol.ratio);

                            current_lat = dms1;
                            current_lon = dms2;
                            String formattedNumber_lat = df.format(current_lat[2]);
                            String formattedNumber_lon = df.format(current_lon[2]);
                            latvalue.postValue(current_lat[0] + "°" + current_lat[1] + "′"
                                    + formattedNumber_lat + "″");
                            lonvalue.postValue(current_lon[0] + "°" + current_lon[1] + "′"
                                    + formattedNumber_lon + "″");
                            String height1 = df.format(pos[2]);
                            height.postValue(height1 + "m");

                            // 精度计算
                            double[] P = new double[9];
                            double[] Q = new double[9];
                            soltocov(rtk.sol, P);
                            covenu(pos, P, Q);
                            updateVAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[0]))) + "m");
                            updateHAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[4]))) + "m");
                            updateUAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[8]))) + "m");
                            accuracyTracker.record(Math.sqrt(Math.abs(Q[0])), Math.sqrt(Math.abs(Q[4])), Math.sqrt(Math.abs(Q[8])));
                            SolutionMetrics.post("PPP",
                                    Math.sqrt(Math.abs(Q[0]) + Math.abs(Q[4])),
                                    Math.sqrt(Math.abs(Q[8])), n,
                                    pos[0] * R2D, pos[1] * R2D);
                            SessionLogger.sol("PPP", "RESULT " + currentTimeString
                                    + " | lat=" + String.format("%.8f", pos[0]*R2D)
                                    + " lon=" + String.format("%.8f", pos[1]*R2D)
                                    + " h=" + String.format("%.3f", pos[2])
                                    + " | stat=" + rtk.sol.stat + " ratio=" + String.format("%.1f", rtk.sol.ratio)
                                    + " | sde=" + df1.format(Math.sqrt(Math.abs(Q[0])))
                                    + " sdn=" + df1.format(Math.sqrt(Math.abs(Q[4])))
                                    + " sdu=" + df1.format(Math.sqrt(Math.abs(Q[8]))));
                            solHistoryLine.postValue(String.format("#%d %s %s %.5f° %.5f° %s",
                                    accuracyTracker.epochCount.getValue(), currentTimeString,
                                    rtk.sol.stat == 1 ? "PPP_Fix" : "PPP_Float",
                                    pos[0] * R2D, pos[1] * R2D, height1));

                            insertData(db, currentTimeString,
                                    Arrays.stream(dms1).mapToObj(String::valueOf)
                                            .collect(Collectors.joining(", ")),
                                    Arrays.stream(dms2).mapToObj(String::valueOf)
                                            .collect(Collectors.joining(", ")),
                                    height1,
                                    Arrays.toString(new float[]{
                                            (float) Math.sqrt(Math.abs(rtk.sol.qr[0])),
                                            (float) Math.sqrt(Math.abs(rtk.sol.qr[1])),
                                            (float) Math.sqrt(Math.abs(rtk.sol.qr[2]))
                                    }),
                                    String.valueOf(6));

                            // 解状态显示 (PPP 特有：6=PPP)
                            if (rtk.sol.stat == 1) {
                                solstate.postValue("PPP Fix");
                                updateRatio.postValue(String.valueOf(rtk.sol.ratio));
                                changeTextColor(Color.rgb(0, 255, 0));
                            } else if (rtk.sol.stat == 2) {
                                solstate.postValue("PPP Float");
                                updateRatio.postValue(String.valueOf(rtk.sol.ratio));
                                changeTextColor(Color.rgb(255, 255, 0));
                            } else {
                                solstate.postValue("PPP(" + rtk.sol.stat + ")");
                                updateRatio.postValue(String.valueOf(rtk.sol.ratio));
                                changeTextColor(Color.rgb(255, 255, 0));
                            }

                            // 输出文件
                            if (if_auto_record) {
                                StringBuilder sb = new StringBuilder();
                                if (auto_record_mode) {
                                    out_sol(rtk.sol, solopt_default, sb);
                                } else {
                                    out_sol_record(str_name_value, rtk.sol, solopt_default, sb);
                                }
                                try {
                                    bufferedWriter.write(sb.toString());
                                    bufferedWriter.flush();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            tips.append(String.format("\n%s PPP %s %dsat %.6f %.6f %s E:%s N:%s U:%s",
                                    currentTimeString.substring(11),
                                    rtk.sol.stat == 1 ? "Fix" : rtk.sol.stat == 2 ? "Float" : "Q"+rtk.sol.stat,
                                    n, pos[0] * R2D, pos[1] * R2D, height1,
                                    df1.format(Math.sqrt(Math.abs(Q[0]))),
                                    df1.format(Math.sqrt(Math.abs(Q[4]))),
                                    df1.format(Math.sqrt(Math.abs(Q[8])))));
                            flushLog();
                        } else {
                            Log.d("PPP_DIAG", "PPP rtk_pos FAILED or sol.stat==0");
                            SessionLogger.sol("PPP", "FAIL " + currentTimeString + " | rtk_pos=" + result + " stat=" + rtk.sol.stat + " | rover=" + n);
                            updateVAccuracy.postValue("0m");
                            updateHAccuracy.postValue("0m");
                            updateUAccuracy.postValue("0m");
                            updateRatio.postValue("0");
                            solstate.postValue("PPP failed");
                            tips.append("\n").append(currentTimeString.substring(11)).append(" PPP FAIL ").append(n).append("sat stat=").append(rtk.sol.stat);
                            flushLog();
                            changeTextColor(Color.rgb(255, 0, 0));
                        }
                    } else {
                        Log.d("PPP_DIAG", "Epoch check failed: nn=" + nn + ", n=" + n);
                        SessionLogger.sol("PPP", "FAIL " + currentTimeString + " | epoch_check nn=" + nn + " n=" + n);
                        updateVAccuracy.postValue("0m");
                        updateHAccuracy.postValue("0m");
                        updateUAccuracy.postValue("0m");
                        solstate.postValue("failed");
                        changeTextColor(Color.rgb(255, 0, 0));
                    }
                } catch (Exception e) {
                    Log.e("PPP_DIAG", "Exception: " + e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } else {
                Log.d("PPP_DIAG", "Skipping PPP: valid_sat=" + valid_sat + " (<4)");
                SessionLogger.sol("PPP", "SKIP " + currentTimeString + " | valid_sat=" + valid_sat);
                tips.append("\n").append(currentTimeString.substring(11)).append(" PPP FAIL ").append(valid_sat).append("sat (<4)");
                flushLog();
                updateVAccuracy.postValue("0m");
                updateHAccuracy.postValue("0m");
                updateUAccuracy.postValue("0m");
                updateRatio.postValue("0");
                changeTextColor(Color.rgb(255, 0, 0));
            }
    }

    public LiveData<String> getText() {
        return mText;
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
    public MutableLiveData<String> getppptips() {
        return ppptips;
    }
    public MutableLiveData<String> getsolstate() {
        return solstate;
    }
    public MutableLiveData<String> getUpdateStatus() {
        return updateStatus;
    }
    public LiveData<Integer> getTextColor() {
        return textColor;
    }
    public MutableLiveData<String> getUpdateRatio() {
        return updateRatio;
    }
    public MutableLiveData<String> getUpdateVAccuracy() {
        return updateVAccuracy;
    }
    public MutableLiveData<String> getUpdateHAccuracy() {
        return updateHAccuracy;
    }
    public MutableLiveData<String> getUpdateUAccuracy() {
        return updateUAccuracy;
    }
    public MutableLiveData<String> getUpdateStrName() {
        return updateStrName;
    }
    public MutableLiveData<String> getUpdateStrNameValue() {
        return updateStrNameValue;
    }
    public void changeTextColor(int color) {
        textColor.postValue(color);
    }

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

    @Override
    protected void onCleared() {
        super.onCleared();
        SessionLogger.sol("PPP", "SESSION END");
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}
