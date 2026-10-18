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
package com.ecut.pnav.ui.RTK;

import static com.ecut.pnav.MainActivity.auto_record_mode;
import static com.ecut.pnav.MainActivity.checked;
import static com.ecut.pnav.MainActivity.if_auto_record;
import static com.ecut.pnav.MainActivity.outputFilePath;
import static com.ecut.pnav.MainActivity.sateph;
import static com.ecut.pnav.ui.DGNSS.DgnssViewModel.out_loghead_base;
import static com.ecut.pnav.ui.FtpConnection.downloadFile;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.ecut.pnav.ui.AccuracyTracker;
import com.ecut.pnav.ui.SolutionMetrics;
import com.ecut.pnav.ui.Sol.SolViewModel;
import static com.ecut.pnav.ui.measure_util.calculateSelectedValue;
import static com.ecut.pnav.ui.measure_util.isSpp;
import static com.ecut.pnav.ui.measure_util.out_sol;
import static com.ecut.pnav.ui.measure_util.out_sol_record;
import static com.ecut.pnav.ui.measure_util.prepareEpochLargestTows;
import static com.ecut.pnav.ui.measure_util.readobs;
import static com.ecut.pnav.ui.measure_util.resetobs;
import static com.ecut.pnav.ui.measure_util.solopt_default;
import static com.ecut.pnav.ui.measure_util.sortobs;
import static com.ecut.pnav.positiondatabase.insertDataAsync;
import static com.ecut.pnav.positiondatabase.insertFileRecordAsync;
import static com.ecut.pnav.ui.data.DataViewModel.outLogHead;
import static com.rtklib.bean.nav_t.MAXRCV;
import static com.rtklib.bean.nav_t.getMaxSat;
import static com.rtklib.pntpos.pntPos.PMODE_SINGLE;
import static com.rtklib.pntpos.pntPos.SOLQ_NONE;
import static com.rtklib.pntpos.pntPos.pnt_pos;
import static com.rtklib.pntpos.rescode.R2D;
import static com.rtklib.pntpos.rescode.ecef2pos;
import static com.rtklib.postpos.outHead.outrpos;
import static com.rtklib.postpos.outSol.SOLF_ENU;
import static com.rtklib.postpos.outSol.SOLF_LLH;
import static com.rtklib.postpos.outSol.SOLF_XYZ;
import static com.rtklib.postpos.outSol.covenu;
import static com.rtklib.postpos.outSol.deg2dms;
import static com.rtklib.postpos.outSol.opt2sep;
import static com.rtklib.postpos.outSol.soltocov;
import static com.rtklib.postpos.procPos.MAXERRMSG;
import static com.rtklib.postpos.procPos.pppnx;
import static com.rtklib.readobsnav.readObsNav.read_obsnav;
import static com.rtklib.readobsnav.readrnxh.SYS_CMP;
import static com.rtklib.readobsnav.readrnxh.SYS_GAL;
import static com.rtklib.readobsnav.readrnxh.SYS_GLO;
import static com.rtklib.readobsnav.readrnxh.SYS_GPS;
import static com.rtklib.readobsnav.readrnxh.SYS_IRN;
import static com.rtklib.readobsnav.readrnxh.SYS_QZS;
import static com.rtklib.readobsnav.readrnxh.SYS_SBS;
import static com.rtklib.readobsnav.satSys.sat_sys;
import static com.rtklib.rtkpos.rtkPos.D2R;
import static com.rtklib.rtkpos.rtkPos.PMODE_FIXED;
import static com.rtklib.rtkpos.rtkPos.rel_pos;
import static com.rtklib.rtkpos.udState.NR;
import static com.rtklib.rtkpos.udState.NX;
import static com.rtklib.rtkpos.udState.PMODE_DGPS;
import static com.rtklib.rtkpos.udState.PMODE_MOVEB;
import static com.rtklib.util.math.zeros;
import static com.rtklib.util.util1.time2epoch;

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


import com.ecut.pnav.MainActivity;
import com.ecut.pnav.positiondatabase;
import com.ecut.pnav.ui.FtpConnection;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.Ntrip.NtripFragment;
import com.ecut.pnav.ui.Ntrip.rtcm_t;
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
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.bean.ssat_t;
import com.rtklib.bean.sta_t;
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

public class RtkViewModel extends ViewModel {
    // 星历更新 Toast 状态 token —— Fragment 观察端映射为本地化文案
    public static final String EPH_CORS_UPDATED = "eph_cors_updated";
    public static final String EPH_PATH_NULL = "eph_path_null";
    public static final String EPH_UPTODATE = "eph_uptodate";
    public static final String EPH_UPDATE_OK = "eph_updated_ok";
    public static final String EPH_UPDATE_FAILED = "eph_update_failed";
    private static final boolean DIAG = false;
    String str_name_value = "";
    String str_name = "";
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> rtcm_num;
    private final MutableLiveData<String> latvalue;
    private final MutableLiveData<String> lonvalue;
    private final MutableLiveData<String> height;
    private final MutableLiveData<String> rtktips;
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
    // RTCM 诊断 LiveData
    private final MutableLiveData<String> rtcmConnStatus;
    private final MutableLiveData<String> rtcmBytesRx;
    private final MutableLiveData<String> rtcmFramesObs;
    private final MutableLiveData<String> rtcmFramesOther;
    private final MutableLiveData<String> rtcmLastSatCount;
    private final MutableLiveData<String> rtcmHasStaPos;
    private final MutableLiveData<String> rtcmDiagText;
    private final DecimalFormat df = new DecimalFormat("#.###"); // 保留3位小数
    private final DecimalFormat df1 = new DecimalFormat("#.#####");
    LocationManager mLocationManager;
    private static obsd_t[] obs = new obsd_t[getMaxSat()];
    static StringBuilder tips = new StringBuilder();

    // 环形缓冲区：最多保留 500 行，增量推送避免全量重绘
    private static final int MAX_LOG_LINES = 500;
    private final List<String> logRing = new ArrayList<>(MAX_LOG_LINES + 10);
    private final MutableLiveData<String> logLine = new MutableLiveData<>();
    private int lastLogLength = 0;
    static String rtcmDiagMsg = "";
    double[] current_lat = new double[3];
    double[] current_lon = new double[3];
    double elevation = 0;
    SQLiteDatabase db;
    static SQLiteDatabase sDb;
    gtime_t ts = new gtime_t(0, 0);
    gtime_t te = new gtime_t(0, 0);
    double ti = 0.0;
    int[] index = {0};
    static BufferedWriter obsWriter;
    static BufferedWriter baseWriter;
    static nav_t navs=new nav_t();          /* navigation data */
    nav_t tempnavs = new nav_t();
    static obs_t obss = new obs_t();
    public static sta_t[] stas = new sta_t[MAXRCV];
    pcv_t[] pcv = new pcv_t[3];
    int nr=0;//基准站数据个数
    int n=0;//流动站数据个数
    int nn=0;

    private rtk_t rtk = new rtk_t();
    private prcopt_t prcopt;   // 实例级副本，避免与 DgnssViewModel 共享 mode 冲突
    private int result;
    double[] pos = new double[3];
    double[] dms1 = new double[3];
    double[] dms2 = new double[3];
    BufferedWriter bufferedWriter;
    BufferedWriter bufferedWriterSpp;
    StringBuilder msg;
    String msg1 = "";
    private FTPClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private static final String COMMENTH = "%";
    private ArrayList<String> ave_result = new ArrayList<>();
    private sol_t spp_sol = new sol_t();

    public RtkViewModel(Context context){
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        String formattedTime = StorageUtil.getTimestamp();
        mText = new MutableLiveData<>();
        rtcm_num = new MutableLiveData<>();
        latvalue = new MutableLiveData<>();
        lonvalue = new MutableLiveData<>();
        height = new MutableLiveData<>();
        rtktips = new MutableLiveData<>();
        solstate = new MutableLiveData<>();
        updateStatus = new MutableLiveData<>();
        updateRatio = new MutableLiveData<>();
        textColor = new MutableLiveData<>();

        positiondatabase dbHelper = new positiondatabase(context);
        db = dbHelper.getWritableDatabase();
        sDb = db;

        updateVAccuracy = new MutableLiveData<>();
        updateHAccuracy = new MutableLiveData<>();
        updateUAccuracy = new MutableLiveData<>();
        updateStrName = new MutableLiveData<>();
        updateStrNameValue = new MutableLiveData<>();
        // RTCM 诊断
        rtcmConnStatus = new MutableLiveData<>();
        rtcmBytesRx = new MutableLiveData<>();
        rtcmFramesObs = new MutableLiveData<>();
        rtcmFramesOther = new MutableLiveData<>();
        rtcmLastSatCount = new MutableLiveData<>();
        rtcmHasStaPos = new MutableLiveData<>();
        rtcmDiagText = new MutableLiveData<>();
        rtcmConnStatus.setValue("等待连接");
        rtcmBytesRx.setValue("0 B");
        rtcmFramesObs.setValue("0");
        rtcmFramesOther.setValue("0");
        rtcmLastSatCount.setValue("0");
        rtcmHasStaPos.setValue("无");
        rtcmDiagText.setValue("");
        updateVAccuracy.setValue("0"+"m");
        updateHAccuracy.setValue("0"+"m");
        mText.setValue("0");
        rtcm_num.setValue("0");
        updateRatio.setValue("0");
        latvalue.setValue(current_lat[0]+"°"+current_lat[1]+"′"+current_lat[2]+"″");
        lonvalue.setValue(current_lon[0]+"°"+current_lon[1]+"′"+current_lon[2]+"″");
        height.setValue(elevation +"m");
        tips.append("Start");
        Log.e("RTK_DIAG", "══════ SESSION START " + formattedTime + " ══════");
        prcopt = new prcopt_t(SolViewModel.prcopt); // 实例副本，避免 mode 与 DGPS 互相覆盖（必须在 readdata 之前初始化）
        SessionLogger.sol("RTK", "SESSION START — navsys=" + prcopt.getNavsys() + " mode=" + prcopt.getMode());
        // 启动时优先尝试 CORS 实时星历
        if (MyService.isServiceRunning) {
            nav_t corsNav = NtripFragment.extractRtcmNav();
            if (corsNav != null && (corsNav.n > 0 || corsNav.ng > 0)) {
                navs = corsNav;
                tips.append("\n").append("CORS实时星历已加载 (").append(corsNav.n).append("eph/").append(corsNav.ng).append("geph)");
                Log.d("RTK", "CORS实时星历已加载 n=" + corsNav.n + " ng=" + corsNav.ng);
            }
        }
        //读取星历文件
        if (outputFilePath != null && outputFilePath.length > 0 && outputFilePath[0] != null) {
            readdata(outputFilePath, sateph);
        } else {
            tips.append("\n").append("No eph file path, please update eph first.");
        }
        for (int i = 0; i < getMaxSat(); i++) {
            obs[i] = new obsd_t();
        }
            //初始化pcv
            for (int i = 0; i < getMaxSat(); i++) {
                navs.pcvs[i] = new pcv_t();
            }
            for (int i = 0; i < 3; i++) {
                pcv[i] = new pcv_t();
            }
            solstate.postValue("0");
//            updateRatio.setValue("0");
            prcopt.mode = 2;
            prcopt.refpos = 1;
            rtkinit(rtk, prcopt);
            tips.append("\n").append("Min ratio to fix ambiguity:");
            tips.append(rtk.opt.getThresar()[0]);
            flushLog();

            File newFolder = StorageUtil.getDataDir(context);
            try {
                File out_folder = new File(newFolder, "RTK" + formattedTime + ".pos");
                File out_folder_spp = new File(newFolder, "SPP" + formattedTime + ".pos");
                FileWriter fileWriter = new FileWriter(out_folder);
                FileWriter fileWriterSpp = new FileWriter(out_folder_spp);
                bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriterSpp = new BufferedWriter(fileWriterSpp);
                if(if_auto_record){
                    out_head(bufferedWriter, prcopt, solopt_default);
                    out_head(bufferedWriterSpp, prcopt, solopt_default);
                }else{
                    out_head_record(bufferedWriter, prcopt, solopt_default);
                    out_head_record(bufferedWriterSpp, prcopt, solopt_default);
                }
                insertFileRecordAsync(db, out_folder.getAbsolutePath(), out_folder.getName(), "POS", "RTK", formattedTime);
                insertFileRecordAsync(db, out_folder_spp.getAbsolutePath(), out_folder_spp.getName(), "POS", "RTK", formattedTime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
//            changeTextColor(Color.rgb(255, 0, 0));
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
                                Log.d("RTK", "CORS星历更新成功 n=" + corsNav.n + " ng=" + corsNav.ng);
                                updateStatus.postValue(EPH_CORS_UPDATED);
                                handler.postDelayed(this, 30 * 1000);
                                return;
                            }
                            // CORS无星历 → 不return，继续尝试文件星历
                            Log.w("RTK", "CORS星历为空，回退到文件星历");
                        }
                        if (outputFilePath == null || outputFilePath.length == 0 || outputFilePath[0] == null) {
                            updateStatus.postValue(EPH_PATH_NULL);
                            Log.e("Decompress", "Output path is null");
                            handler.postDelayed(this, 30 * 60 * 1000);
                            return;
                        }
                        // 星历有效期仅±2小时，每30分钟重新下载确保星历不过期
                        File existingEph = new File(outputFilePath[0]);
                        if (existingEph.exists()) {
                            long ageMs = System.currentTimeMillis() - existingEph.lastModified();
                            long fileSize = existingEph.length();
                            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
                            String todayDoy = String.format("%03d", nowUtc.getDayOfYear());
                            boolean isToday = existingEph.getName().contains(todayDoy);
                            boolean tooSmall = fileSize < 150000;
                            if (ageMs < 3600_000 && isToday && !tooSmall) {
                                Log.d("RTK", "Ephemeris file is fresh (age=" + (ageMs/60000) + "min), skip download");
                                updateStatus.postValue(EPH_UPTODATE);
                                readdata(outputFilePath, sateph);
                                handler.postDelayed(this, 30 * 60 * 1000);
                                return;
                            }
                            if (!isToday) {
                                Log.w("RTK", "Ephemeris file is from wrong day, re-downloading...");
                            } else if (tooSmall) {
                                Log.w("RTK", "Ephemeris file too small (" + fileSize + "B, likely GPS-only), re-downloading...");
                            } else {
                                Log.d("RTK", "Ephemeris file expired (age=" + (ageMs/60000) + "min), re-downloading...");
                            }
                        }
                        FTPClient client = FtpConnection.getFTPClient();
                        if (client.isConnected()) {
                            MainActivity.rebuildRemoteDirPath();
                            downloadFile(context, client, MainActivity.remoteDirPath, infile, 1);
                            client.logout();
                            client.disconnect();
                            if (outputFilePath != null && outputFilePath.length > 0 && outputFilePath[0] != null) {
                                FtpConnection.decompressGzipFile(infile, outputFilePath);
                            } else {
                                Log.e("RTK", "Cannot decompress: output path is null");
                                updateStatus.postValue(EPH_PATH_NULL); // 输出路径未设置
                                return;
                            }
                            FtpConnection.cleanupExpiredEphemeris(new File(context.getExternalFilesDir(null), "myFolder"));
                        }
                        updateStatus.postValue(EPH_UPDATE_OK);
                        readdata(outputFilePath,sateph);
                    } catch (IOException e) {
                        Log.e("TAG", "An error occurred", e);
                        updateStatus.postValue(EPH_UPDATE_FAILED);
                    }
                }).start();
                handler.postDelayed(this, 30 * 60 * 1000);
            }
        };
        handler.post(runnable);
    }
    public static void out_head_record(BufferedWriter bufferedWriter, prcopt_t opt, solopt_t sopt) throws IOException {
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
                sb2.append(String.format("%s%16s%s%16s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s"
                                , "name","latitude(d'\")", sep[0], "longitude(d'\")", sep[0], "height(m)",
                                sep[0], "Q", sep[0], "ns", sep[0], "sdn(m)", sep[0], "sde(m)", sep[0], "sdu(m)",
                                sep[0], "sdne(m)", sep[0], "sdeu(m)", sep[0], "sdue(m)", sep[0], "age(s)",
                                sep[0], "ratio"))
                        .append("\n");
            } else {
                sb2.append(String.format("%s%14s%s%14s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s",
                                "name", "latitude(deg)", sep[0], "longitude(deg)", sep[0], "height(m)", sep[0],
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
//        if (PMODE_DGPS<=prcopt.mode&&prcopt.mode<=PMODE_FIXED&&prcopt.mode!=PMODE_MOVEB) {
////            fprintf(fp,"%s ref pos   :",COMMENTH);
//            sb.append(String.format("%s ref pos   :", COMMENTH));
//            outrpos(bufferedWriter,MyService.stapos,sopt);
////            fprintf(fp,"\n");
//        }
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
            if(PMODE_DGPS<=SolViewModel.prcopt.mode&&SolViewModel.prcopt.mode<=PMODE_FIXED&&SolViewModel.prcopt.mode!=PMODE_MOVEB){
                sb.append(String.format("%s ref pos   :", COMMENTH));
                bufferedWriter.write(sb.toString());
                outrpos(bufferedWriter,MyService.stapos,sopt);
                bufferedWriter.newLine();//换行写入
            }else{
                bufferedWriter.write(sb.toString());
                bufferedWriter.newLine();//换行写入
            }
            bufferedWriter.write(sb2.toString());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void out_head(BufferedWriter bufferedWriter, prcopt_t opt, solopt_t sopt) throws IOException {
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
//        if (PMODE_DGPS<=prcopt.mode&&prcopt.mode<=PMODE_FIXED&&prcopt.mode!=PMODE_MOVEB) {
////            fprintf(fp,"%s ref pos   :",COMMENTH);
//            sb.append(String.format("%s ref pos   :", COMMENTH));
//            outrpos(bufferedWriter,MyService.stapos,sopt);
////            fprintf(fp,"\n");
//        }
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
            if(PMODE_DGPS<=SolViewModel.prcopt.mode&&SolViewModel.prcopt.mode<=PMODE_FIXED&&SolViewModel.prcopt.mode!=PMODE_MOVEB){
                sb.append(String.format("%s ref pos   :", COMMENTH));
                bufferedWriter.write(sb.toString());
                outrpos(bufferedWriter,MyService.stapos,sopt);
                bufferedWriter.newLine();//换行写入
            }else{
                bufferedWriter.write(sb.toString());
                bufferedWriter.newLine();//换行写入
            }
            bufferedWriter.write(sb2.toString());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void rtkinit(rtk_t rtk, prcopt_t opt)
    {
        int i;
        rtk.sol=new sol_t();
        for (i=0;i<6;i++) rtk.rb[i]=0.0;
        rtk.nx=opt.mode<= udState.PMODE_FIXED?NX(opt):pppnx(opt);
        rtk.na=opt.mode<= udState.PMODE_FIXED?NR(opt):pppnx(opt);
        rtk.tt=0.0;
        rtk.x=zeros(rtk.nx,1);
        rtk.P=zeros(rtk.nx,rtk.nx);
        rtk.xa=zeros(rtk.na,1);
        rtk.Pa=zeros(rtk.na,rtk.na);
        rtk.nfix=rtk.neb=0;
        for (i=0;i<getMaxSat();i++) {
            rtk.ambc[i]=new ambc_t();
            rtk.ssat[i]=new ssat_t();
            // 初始化二维数组的每个元素
            for (int k = 0; k < rtk.ssat[i].pt.length; k++) {
                for (int j = 0; j < rtk.ssat[i].pt[k].length; j++) {
                    rtk.ssat[i].pt[k][j] = new gtime_t(0, 0.0); // 使用默认值初始化
                }
            }
        }
        for (i=0;i<MAXERRMSG;i++) rtk.errbuf[i]=0;
        rtk.opt=opt;
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
        //读取广播星历文件
        if (hasNavFile) {
            try {
                // ★ read_obsnav() 在纯星历模式下因 obs.n==0 返回0，但星历已正确填充到 tempnavs
                read_obsnav(ts, te, ti, navFiles, navIndex, navFiles.length, prcopt, obss, tempnavs, stas);
                if(tempnavs.n > 0 || tempnavs.ng > 0 || tempnavs.ns > 0){
                    navs = tempnavs;
                    tips.append("\n").append("Eph file read successfully.");
                    flushLog();
                    Log.d("RTK", "Read successfully. n=" + navs.n + " ng=" + navs.ng + " ns=" + navs.ns);
                }else{
                    tips.append("\n").append("Eph file read failed.");
                    flushLog();
                }
            } catch (IOException | CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        } else {
            tips.append("\n").append("No eph file available, please update the eph.");
        }
        //读取精密星历文件和钟差文件
//        if (sateph == 1) {
//            if (prec.exists() && clk.exists()) {
//                try {
//                    readpreceph(infile, infile.length, prcopt, navs, sbss);
//                    if (navs.peph != null) {
//                        tips.append("\n").append("精密星历文件读取成功");
//                        tips.append("\n").append("钟差文件读取成功");
//                    } else {
//                        tips.append("\n").append("精密星历文件读取失败");
//                    }
//                } catch (IOException | CloneNotSupportedException e) {
//                    throw new RuntimeException(e);
//                }
//            } else {
//                tips.append("\n").append("精密星历文件或钟差文件不存在，请更新");
//            }
//        }
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

        //计算基准站数据个数（去异常）
        int roverObsCount = (int) Arrays.stream(obs).filter(o -> o.sat[0] != 0).count();
        nr = 0;
        for (obsd_t obsdT : MyService.obssd) {
            if (obsdT.sat[0] != 0) {
                if (MyService.obssd[0].time.time != obsdT.time.time) {
                    tips.append("\n").append(currentTimeString)
                            .append(": Reference station: anomaly, Satellite: ")
                            .append(obsdT.sat[0]);
                    obsdT.sat[0] = 0;
                } else {
                    nr++;
                }
            }
        }
        boolean hasStaPos = (MyService.stapos[0] != 0 || MyService.stapos[1] != 0 || MyService.stapos[2] != 0);
        if (DIAG) Log.d("RTK_DIAG", "=== Epoch ===");
        if (DIAG) Log.d("RTK_DIAG", "Rover obs: " + roverObsCount + ", Base obs(nr): " + nr + ", hasStaPos: " + hasStaPos);
        if (DIAG) Log.d("RTK_DIAG", "MyService.stapos: [" + MyService.stapos[0] + ", " + MyService.stapos[1] + ", " + MyService.stapos[2] + "]");
        if (DIAG) Log.d("RTK_DIAG", "navs.n=" + navs.n + " navs.ng=" + navs.ng + " prcopt.mode=" + prcopt.getMode() + " navsys=" + prcopt.getNavsys() + " ionoopt=" + prcopt.getIonoopt() + " tropopt=" + prcopt.getTropopt() + " elmin=" + String.format("%.1f", prcopt.getElmin()/D2R) + "°");
        // ★ 结构化日志
        SessionLogger.sol("RTK", "EPOCH " + currentTimeString + " | rover=" + roverObsCount + " base=" + nr + " | staPos=" + (hasStaPos?"Y":"N") + " | nav.n=" + navs.n + " nav.ng=" + navs.ng + " mode=" + prcopt.getMode() + " iono=" + prcopt.getIonoopt());
        // ★ DIAG: 列出前6颗流动站卫星
        StringBuilder satDiag = new StringBuilder("rover_sats: ");
        int satCnt = 0;
        for (obsd_t ob : obs) {
            if (ob.sat[0] != 0 && satCnt < 10) {
                satDiag.append("PRN").append(ob.sat[0]).append("(P=").append(String.format("%.1f", ob.P[0])).append(") ");
                satCnt++;
            }
        }
        if (DIAG) Log.d("RTK_DIAG", satDiag.toString());
        // ★ DIAG: 列出基准站卫星
        StringBuilder baseDiag = new StringBuilder("base_sats: ");
        for (obsd_t ob : MyService.obssd) {
            if (ob.sat[0] != 0) baseDiag.append("PRN").append(ob.sat[0]).append(" ");
        }
        if (DIAG) Log.d("RTK_DIAG", baseDiag.toString());
        tips.append("\n").append("[诊断] 流动站:").append(roverObsCount).append(" 基准站:").append(nr).append(" 基站坐标:").append(hasStaPos ? "有" : "无");
        flushLog();
        //UI更新RTCM中的卫星数量
        rtcm_num.postValue(String.valueOf(nr));
        int valid_sat = isSpp(obs, navs);
        mText.postValue(String.valueOf(valid_sat));
        if (DIAG) Log.d("RTK_DIAG", "SPP valid_sat=" + valid_sat + ", nr=" + nr + ", MyService.obssd[0].sat[0]=" + (MyService.obssd.length > 0 ? MyService.obssd[0].sat[0] : "N/A"));
        if (valid_sat >= 4 && MyService.obssd[0].sat[0] != 0) {
            nn = 0;
//            rtcm_gps_obs_n = 0;
            try {
                //计算流动站数据个数
                n = (int) Arrays.stream(obs).filter(o -> o.sat[0] != 0).count(); // 使用流式计算
                obsd_t[] temp = sortobs(obs);
                //判断流动站的数据来自同一个历元
                for (int i = 1; i < n; i++) {
                    if (temp[0].time.time == temp[i].time.time) {
                        nn++;
                    } else {
                        tips.append("\n").append(currentTimeString)
                                .append(":").append("Rover:")
                                .append("The").append(i + 1)
                                .append("th one has an anomaly:")
                                .append("Satellite:").append(temp[i].sat[0])
                                .append("error occurred").append("\n")
                                .append("The time difference is:")
                                .append(temp[0].time.time - temp[i].time.time);
                    }
                }
                if (nn == n-1 && n >= 4) {
                    // 使用 ArrayList 来动态存储有效的 obsd_t 对象
                    List<obsd_t> tempObsBaseList = new ArrayList<>();
                    for (obsd_t obsdT : MyService.obssd) {
                        if (obsdT.sat[0] != 0) {
                            obsd_t clonedObs = (obsd_t) obsdT.clone();
                            clonedObs.rcv = 2; // 设置接收器值
                            tempObsBaseList.add(clonedObs); // 添加到列表中
                        }
                    }
                    // 将 ArrayList 转换为数组
                    obsd_t[] tempObsBase = tempObsBaseList.toArray(new obsd_t[0]);
                    for (int iu = 0; iu < MyService.rtcm.getObs().data.length; iu++) {
                        MyService.rtcm.getObs().data[iu].rcv = 2;
                    }
                    // 将两个站的数据赋值到 dgnss_obs 中，用于计算（深拷贝避免 resetobs 污染源数据）
                    obsd_t[] dgnss_obs = new obsd_t[n + tempObsBase.length];
                    for (int i = 0; i < n; i++) dgnss_obs[i] = (obsd_t) temp[i].clone();
                    for (int i = 0; i < tempObsBase.length; i++) dgnss_obs[n + i] = (obsd_t) tempObsBase[i].clone();
                    prcopt.setRb(MyService.stapos);
                    rtk.opt = prcopt;
                    rtk.rb = MyService.stapos;
                    // ★ SPP 需要 mode=0，临时切换（mode=2 会强制 ionoopt=1,tropopt=1 导致解算失败）
                    int savedMode = rtk.opt.getMode();
                    rtk.opt.setMode(PMODE_SINGLE);
                    int state = pnt_pos(temp, n, navs, rtk.opt, rtk.sol, null, rtk.ssat, msg1);
                    rtk.opt.setMode(savedMode);  // 恢复 mode 给 rel_pos 使用
                    if (DIAG) Log.d("RTK_DIAG", "SPP pnt_pos result: " + state + ", n=" + n + ", nr=" + nr + " savedMode=" + savedMode + " msg1=" + msg1);
                    tips.append("\n").append("[诊断] SPP解算=").append(state != 0 ? "成功" : "失败").append(" 流动站=").append(n).append(" 基准站=").append(nr);
                    flushLog();
                    if (state != 0) {
                        for (int i = 0; i < 6; i++) {
                            spp_sol.rr[i] = rtk.sol.rr[i];
                            spp_sol.qr[i] = rtk.sol.qr[i];
                        }
                        spp_sol.stat = rtk.sol.stat;
                        spp_sol.age = rtk.sol.age;
                        spp_sol.type = rtk.sol.type;
                        spp_sol.ratio = rtk.sol.ratio;
                        spp_sol.time = rtk.sol.time;
                        rtk.sol.stat = SOLQ_NONE;
                        //计算差分龄期，大于某个阈值不予计算
                        double diff = local_timediff(dgnss_obs[0].time, dgnss_obs[n].time);
                        rtk.sol.age = (float) diff;
                        prcopt.setMaxtdiff(5); // RTK 容许5秒龄期，原来1秒太紧导致难出结果
                        tips.append("\n").append("[诊断] 差分龄期=").append(String.format("%.2f", diff)).append("s (阈值5s)");
                        flushLog();
                        if (Math.abs(diff) >= 5.0) {
                            if (DIAG) Log.d("RTK_DIAG", "Age of diff too large: " + diff + "s (threshold 2.1s)");
                            SessionLogger.sol("RTK", "FAIL " + currentTimeString + " | age_diff=" + String.format("%.1f", diff) + "s");
                            tips.append("\n").append(currentTimeString).append(":").append("age of differential error:").append(diff);
                            flushLog();
                            updateRatio.postValue("0");
                            updateVAccuracy.postValue(0+"m");
                            updateHAccuracy.postValue(0+"m");
                            updateUAccuracy.postValue(0+"m");
                            solstate.postValue("failed");
                            changeTextColor(Color.rgb(255,0,0));
                            resetobs(dgnss_obs);
                        } else {
                            //这里将配置结构体opt内基准站的坐标赋值给解算结构体rtk内基准站的坐标
                            result = rel_pos(rtk, dgnss_obs, n, nr, navs);
                            if (DIAG) Log.d("RTK_DIAG", "rel_pos result: " + result + " (>=1 means success)");
                            if (result >= 1) {
                                ecef2pos(rtk.sol.rr, pos);
                                deg2dms(pos[0] * R2D, dms1, 5);
                                deg2dms(pos[1] * R2D, dms2, 5);
                                if (DIAG) Log.d("RTK_DIAG", "SUCCESS lat=" + String.format("%.6f", pos[0]*R2D) + " lon=" + String.format("%.6f", pos[1]*R2D) + " h=" + String.format("%.2f", pos[2]) + " sol.stat=" + rtk.sol.stat + " ratio=" + rtk.sol.ratio);
                                // ★ 先写基础 RESULT（不依赖 sol.qr，避免数组越界阻断整个 SUCCESS 路径）
                                SessionLogger.sol("RTK", "RESULT " + currentTimeString
                                    + " | lat=" + String.format("%.8f", pos[0]*R2D)
                                    + " lon=" + String.format("%.8f", pos[1]*R2D)
                                    + " h=" + String.format("%.3f", pos[2])
                                    + " | stat=" + rtk.sol.stat + " ratio=" + String.format("%.1f", rtk.sol.ratio));

                                String strdms11 = Arrays.stream(dms1)
                                        .mapToObj(String::valueOf) // 将 int 转换为 String
                                        .collect(Collectors.joining(", ")); // 使用逗号连接
                                String strdms22 = Arrays.stream(dms2)
                                        .mapToObj(String::valueOf) // 将 int 转换为 String
                                        .collect(Collectors.joining(", ")); // 使用逗号连接
                                current_lat = dms1;
                                current_lon = dms2;

                                String formattedNumber_lat = df.format(current_lat[2]);
                                String formattedNumber_lon = df.format(current_lon[2]);
                                latvalue.postValue(current_lat[0] + "°" + current_lat[1] + "′" + formattedNumber_lat + "″");
                                lonvalue.postValue(current_lon[0] + "°" + current_lon[1] + "′" + formattedNumber_lon + "″");
                                String height1 = df.format(pos[2]);
                                height.postValue(height1+"m");
                                float[] temp_qr = new float[rtk.sol.qr.length];
                                // 遍历原始数组，计算每个元素的开根号
                                for (int i = 0; i < rtk.sol.qr.length; i++) {
                                    temp_qr[i] = (float) Math.sqrt(Math.abs(rtk.sol.qr[i]));
                                }
                                String strqr = Arrays.toString(temp_qr);
                                // 写入定位数据库
                                insertDataAsync(db, currentTimeString, strdms11, strdms22, height1, strqr, String.valueOf(2));
                                StringBuilder sb = new StringBuilder();
                                StringBuilder sb1 = new StringBuilder();
                                solopt_default.timef = 0;
                                // 遍历原始数组，计算每个元素的开根号
                                double[] P = new double[9];
                                double[] Q = new double[9];
                                soltocov(rtk.sol,P);
                                covenu(pos,P,Q);
                                updateVAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[0])))+"m");
                                updateHAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[4])))+"m");
                                updateUAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[8])))+"m");
                                accuracyTracker.record(Math.sqrt(Math.abs(Q[0])), Math.sqrt(Math.abs(Q[4])), Math.sqrt(Math.abs(Q[8])));
                                SolutionMetrics.post("RTK",
                                        Math.sqrt(Math.abs(Q[0]) + Math.abs(Q[4])),
                                        Math.sqrt(Math.abs(Q[8])), n,
                                        pos[0] * R2D, pos[1] * R2D);
                                // ★ 精度信息单独追加（Q 来自 covenu，安全）
                                SessionLogger.sol("RTK", "PREC " + currentTimeString
                                    + " | sde=" + df1.format(Math.sqrt(Math.abs(Q[0])))
                                    + " sdn=" + df1.format(Math.sqrt(Math.abs(Q[4])))
                                    + " sdu=" + df1.format(Math.sqrt(Math.abs(Q[8]))));
                                solHistoryLine.postValue(String.format("#%d %s %s %.5f° %.5f° %s",
                                        accuracyTracker.epochCount.getValue(), currentTimeString,
                                        rtk.sol.stat == 1 ? "RTK_Fix" : "RTK_Float",
                                        pos[0] * R2D, pos[1] * R2D, height1));
                                if(if_auto_record){
                                    if(auto_record_mode){
                                        out_sol(rtk.sol, solopt_default, sb);
                                        out_sol(spp_sol, solopt_default, sb1);
                                    }else{
                                        out_sol_record(str_name_value, rtk.sol, solopt_default, sb);
                                        out_sol_record(str_name_value, spp_sol, solopt_default, sb1);
                                    }
                                    try {
                                        bufferedWriter.write(sb.toString());
                                        bufferedWriterSpp.write(sb1.toString());
                                        bufferedWriter.flush();
                                        bufferedWriterSpp.flush();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                if (rtk.sol.stat == 2) {
                                    solstate.postValue("float");
                                    updateRatio.postValue(String.valueOf(rtk.sol.ratio));
                                    changeTextColor(Color.rgb(255, 255, 0));
                                } else if (rtk.sol.stat == 1) {
                                    solstate.postValue("fix");
                                    updateRatio.postValue(String.valueOf(rtk.sol.ratio));
                                    changeTextColor(Color.rgb(0, 255, 0));
                                }
                                tips.append(String.format("\n%s RTK %s %dsat %.6f %.6f %s E:%s N:%s U:%s",
                                        currentTimeString.substring(11),
                                        rtk.sol.stat == 1 ? "Fix" : "Float",
                                        n, pos[0] * R2D, pos[1] * R2D, height1,
                                        df1.format(Math.sqrt(Math.abs(Q[0]))),
                                        df1.format(Math.sqrt(Math.abs(Q[4]))),
                                        df1.format(Math.sqrt(Math.abs(Q[8])))));
                                flushLog();
                            } else {
                                if (DIAG) Log.d("RTK_DIAG", "rel_pos FAILED. result=" + result + ", n=" + n + ", nr=" + nr);
                                SessionLogger.sol("RTK", "FAIL " + currentTimeString + " | rel_pos=" + result + " | rover=" + n + " base=" + nr);
                                updateVAccuracy.postValue(0+"m");
                                updateHAccuracy.postValue(0+"m");
                                updateUAccuracy.postValue(0+"m");
                                updateRatio.postValue("0");
//                                resetobs(obs);
                                resetobs(dgnss_obs);
                                tips.append("\n").append(currentTimeString.substring(11)).append(" RTK FAIL ").append(n).append("sat ").append(msg.toString().replace("\n"," "));
                                solstate.postValue("rtk failed");
                                flushLog();
                                changeTextColor(Color.rgb(255, 192, 203));
                            }
                        }
                    } else {
                        if (DIAG) Log.d("RTK_DIAG", "SPP pnt_pos FAILED. state=" + state);
                        SessionLogger.sol("RTK", "FAIL " + currentTimeString + " | SPP_base state=" + state + " | rover=" + n);
                        updateVAccuracy.postValue(0+"m");
                        updateHAccuracy.postValue(0+"m");
                        updateUAccuracy.postValue(0+"m");
                        updateRatio.postValue("0");
                        solstate.postValue("spp failed");
                        tips.append("\n").append(currentTimeString.substring(11)).append(" RTK FAIL ").append(n).append("sat SPP:").append(msg.toString().replace("\n"," "));
                        flushLog();
                        changeTextColor(Color.rgb(255,0,0));
                    }
                } else {
                    if (DIAG) Log.d("RTK_DIAG", "Rover data check failed: nn=" + nn + ", n=" + n + " (need nn==n-1 && n>=4)");
                    SessionLogger.sol("RTK", "FAIL " + currentTimeString + " | epoch_check nn=" + nn + " n=" + n);
                    flushLog();
                    updateVAccuracy.postValue(0+"m");
                    updateHAccuracy.postValue(0+"m");
                    updateUAccuracy.postValue(0+"m");
                    solstate.postValue("failed");
//                    resetobs(obs);
                    changeTextColor(Color.rgb(255,0,0));
                }
            } catch (CloneNotSupportedException e) {
                Log.e("RTK_DIAG", "Exception: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }else {
            if (DIAG) Log.d("RTK_DIAG", "Skipping RTK: SPP=" + valid_sat + " (<4) or MyService.obssd[0].sat[0]=" + MyService.obssd[0].sat[0] + " (==0)");
            SessionLogger.sol("RTK", "SKIP " + currentTimeString + " | valid_sat=" + valid_sat + " nr=" + nr + " base0sat=" + (MyService.obssd.length > 0 ? MyService.obssd[0].sat[0] : 0));
            tips.append("\n").append("[诊断] 跳过RTK: SPP有效=").append(valid_sat).append("(需>=4) 基准站首星=").append(MyService.obssd[0].sat[0]).append("(需≠0)");
            flushLog();
            updateVAccuracy.postValue(0+"m");
            updateHAccuracy.postValue(0+"m");
            updateUAccuracy.postValue(0+"m");
            updateRatio.postValue("0");
            changeTextColor(Color.rgb(255,0,0));
        }
    }
    static void write_log_head_baseandobs(Context context) {
        String formattedTime = StorageUtil.getTimestamp();
        try {
            File dir = StorageUtil.getDataDir(context);
            File out_folder_obs = new File(dir, "rtk_gnss_log_obs" + formattedTime + ".txt");
            File out_folder_base = new File(dir, "rtk_gnss_log_base" + formattedTime + ".txt");
            FileWriter obsWriter1 = new FileWriter(out_folder_obs);
            FileWriter baseWriter1 = new FileWriter(out_folder_base);
            obsWriter = new BufferedWriter(obsWriter1);
            baseWriter = new BufferedWriter(baseWriter1);
            outLogHead(obsWriter, com.ecut.pnav.ui.status.HomeViewModel.support_CarrierPhase);
            out_loghead_base(baseWriter);
            insertFileRecordAsync(sDb, out_folder_obs.getAbsolutePath(), out_folder_obs.getName(), "OBS", "RTK", formattedTime);
            insertFileRecordAsync(sDb, out_folder_base.getAbsolutePath(), out_folder_base.getName(), "OBS", "RTK", formattedTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    static void log_obs_base(obsd_t[] obs, obsd_t[] obsd, int nr){
        gtime_t firstObsTime = null; // 初始化为 null，表示尚未设置
        int num_sat = 0;
        for(int i=0;i<getMaxSat();i++){
            if(obs[i].sat[0]!=0){
                num_sat++;
                if (firstObsTime == null) {
                    firstObsTime = obs[i].time; // 更新为第一次有效观测的时间
                }
            }
        }
        if(firstObsTime!=null) {
            StringBuilder sb = new StringBuilder();
            double[] ep = new double[6];
            time2epoch(firstObsTime, ep);
            @SuppressLint("DefaultLocale")
            String obstime = String.format("> %4d %2d %2d %2d %2d%11.7f  0 %2d", (int) ep[0], (int) ep[1], (int) ep[2], (int) ep[3], (int) ep[4], ep[5], num_sat);
            sb.append(obstime).append("\n");
            for (int i = 0; i < getMaxSat(); i++) {
                if (obs[i].sat[0] != 0) {
                    int[] prn = new int[1];
                    sat_sys(obs[i].sat[0], prn);
                    if (obs[i].sat[0] <= 32) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("G%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obs[i].sat[0]>=106&&obs[i].sat[0]<=168){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("C%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obs[i].sat[0]>=33&&obs[i].sat[0]<=59){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("R%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if(obs[i].sat[0]>=60&&obs[i].sat[0]<=95){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("E%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obs[i].sat[0]>=96&&obs[i].sat[0]<=105){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("J%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }
                }
            }
            resetobs(obs);
            try {
                obsWriter.write(sb.toString());
                obsWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if(nr>0) {
            StringBuilder sb = new StringBuilder();
            double[] ep = new double[6];
            time2epoch(obsd[0].time, ep);
            @SuppressLint("DefaultLocale")
            String obstime = String.format("> %4d %2d %2d %2d %2d%11.7f  0 %2d", (int) ep[0], (int) ep[1], (int) ep[2], (int) ep[3], (int) ep[4], ep[5], nr);
            sb.append(obstime).append("\n");
            for (int i = 0; i < getMaxSat(); i++) {
                if (obsd[i].sat[0] != 0) {
                    int[] prn = new int[1];
                    sat_sys(obsd[i].sat[0], prn);
                    if (obsd[i].sat[0] <= 32) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("G%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obsd[i].sat[0]>=106&&obsd[i].sat[0]<=168){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("C%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obsd[i].sat[0]>=33&&obsd[i].sat[0]<=59){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("R%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if(obsd[i].sat[0]>=60&&obsd[i].sat[0]<=95){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("E%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }else if(obsd[i].sat[0]>=96&&obsd[i].sat[0]<=105){
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("J%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }
                }
            }
            resetobs(obsd);
            try {
                baseWriter.write(sb.toString());
                baseWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static double local_timediff(gtime_t t1, gtime_t t2) {
        double diffInSeconds = t1.getTime() - t2.getTime();
        double diffInFractionalSeconds = t1.getSec() - t2.getSec();
        return diffInSeconds + diffInFractionalSeconds;
    }
    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<String> getrtcmnum() {
        return rtcm_num;
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
    public MutableLiveData<String> getdgnsstips() {
        return rtktips;
    }
    public MutableLiveData<String> getsolstate(){
        return solstate;
    }
    public MutableLiveData<String> getUpdateStatus(){
        return updateStatus;
    }
    public LiveData<Integer> getTextColor() {
        return textColor;
    }
    public MutableLiveData<String> getUpdateRatio(){
        return updateRatio;
    }
    public MutableLiveData<String> getUpdateVAccuracy(){
        return updateVAccuracy;
    }
    public MutableLiveData<String> getUpdateHAccuracy(){
        return updateHAccuracy;
    }
    public MutableLiveData<String> getUpdateUAccuracy(){
        return updateUAccuracy;
    }
    public MutableLiveData<String> getUpdateStrName(){
        return updateStrName;
    }
    public MutableLiveData<String> getUpdateStrNameValue(){
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

    // RTCM 诊断 getter
    public MutableLiveData<String> getRtcmConnStatus() { return rtcmConnStatus; }
    public MutableLiveData<String> getRtcmBytesRx() { return rtcmBytesRx; }
    public MutableLiveData<String> getRtcmFramesObs() { return rtcmFramesObs; }
    public MutableLiveData<String> getRtcmFramesOther() { return rtcmFramesOther; }
    public MutableLiveData<String> getRtcmLastSatCount() { return rtcmLastSatCount; }
    public MutableLiveData<String> getRtcmHasStaPos() { return rtcmHasStaPos; }
    public MutableLiveData<String> getRtcmDiagText() { return rtcmDiagText; }

    @Override
    protected void onCleared() {
        super.onCleared();
        SessionLogger.sol("RTK", "SESSION END");
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

}