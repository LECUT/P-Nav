package com.ecut.pnav.ui.DGNSS;

import static com.ecut.pnav.MainActivity.checked;
import static com.ecut.pnav.MainActivity.outputFilePath;

import com.ecut.pnav.ui.AccuracyTracker;
import com.ecut.pnav.ui.SolutionMetrics;
import com.ecut.pnav.ui.Sol.SolViewModel;
import static com.ecut.pnav.ui.Spp.SppViewModel.out_head;
import static com.ecut.pnav.ui.measure_util.bds_b1_lamda;
import static com.ecut.pnav.ui.measure_util.calculateSelectedValue;
import static com.ecut.pnav.ui.measure_util.gps_l1_lamda;
import static com.ecut.pnav.ui.measure_util.isSpp;
import static com.ecut.pnav.ui.measure_util.out_sol;
import static com.ecut.pnav.ui.measure_util.prepareEpochLargestTows;
import static com.ecut.pnav.ui.measure_util.prcopt_default;
import static com.ecut.pnav.ui.measure_util.readobs;
import static com.ecut.pnav.ui.measure_util.resetobs;
import static com.ecut.pnav.ui.measure_util.solopt_default;
import static com.ecut.pnav.ui.measure_util.sortobs;
import static com.ecut.pnav.positiondatabase.insertDataAsync;
import static com.ecut.pnav.positiondatabase.insertFileRecordAsync;
import static com.ecut.pnav.ui.data.DataViewModel.outLogHead;
import static com.rtklib.bean.nav_t.MAXRCV;
import static com.rtklib.bean.nav_t.getMaxSat;
import static com.rtklib.pntpos.pntPos.SOLQ_NONE;
import static com.rtklib.pntpos.pntPos.pnt_pos;
import static com.rtklib.pntpos.pntPos.PMODE_SINGLE;
import static com.rtklib.pntpos.rescode.R2D;
import static com.rtklib.pntpos.rescode.ecef2pos;
import static com.rtklib.postpos.outSol.covenu;
import static com.rtklib.postpos.outSol.deg2dms;
import static com.rtklib.postpos.outSol.soltocov;
import static com.rtklib.postpos.procPos.MAXERRMSG;
import static com.rtklib.postpos.procPos.pppnx;
import static com.rtklib.readobsnav.readObsNav.read_obsnav;
import static com.rtklib.readobsnav.readrnxh.SYS_CMP;
import static com.rtklib.readobsnav.satSys.sat_sys;
import static com.rtklib.rtkpos.rtkPos.D2R;
import static com.rtklib.rtkpos.rtkPos.PMODE_FIXED;
import static com.rtklib.rtkpos.rtkPos.rel_pos;
import static com.rtklib.rtkpos.udState.NR;
import static com.rtklib.rtkpos.udState.NX;
import static com.rtklib.util.math.zeros;
import static com.rtklib.util.util1.time2epoch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import com.ecut.pnav.MainActivity;
import com.ecut.pnav.positiondatabase;
import com.ecut.pnav.ui.MyService;
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
import com.rtklib.bean.ssat_t;
import com.rtklib.bean.sta_t;

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

public class DgnssViewModel extends ViewModel {
    private static final boolean DIAG = false;
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> rtcm_num;
    private final MutableLiveData<String> latvalue;
    private final MutableLiveData<String> lonvalue;
    private final MutableLiveData<String> height;
    private final MutableLiveData<String> dgnsstips;
    private final MutableLiveData<String> updateEAccuracy;
    private final MutableLiveData<String> updateNAccuracy;
    private final MutableLiveData<String> updateUAccuracy;
    public final AccuracyTracker accuracyTracker = new AccuracyTracker();
    private final MutableLiveData<String> solHistoryLine = new MutableLiveData<>();
    private final MutableLiveData<String> solstate;
    private final MutableLiveData<Integer> textColor;
    // RTCM 诊断 LiveData
    private final MutableLiveData<String> rtcmConnStatus;
    private final MutableLiveData<String> rtcmBytesRx;
    private final MutableLiveData<String> rtcmFramesObs;
    private final MutableLiveData<String> rtcmFramesOther;
    private final MutableLiveData<String> rtcmLastSatCount;
    private final MutableLiveData<String> rtcmHasStaPos;
    private final MutableLiveData<String> rtcmDiagText;

    private final DecimalFormat df = new DecimalFormat("#.###"); // 保留3位小数
    private final DecimalFormat df1 = new DecimalFormat("#.#####");//保留5位小数
    private StringBuilder msg;
    LocationManager mLocationManager;
    private static obsd_t[] obs = new obsd_t[getMaxSat()];
    static nav_t navs = new nav_t();          /* navigation data */
    static obs_t obss = new obs_t();
    gtime_t ts = new gtime_t(0, 0);
    gtime_t te = new gtime_t(0, 0);
    double ti = 0.0;
    int[] index = {0};
    //    prcopt_t prcopt = prcopt_default;
    public static sta_t[] stas = new sta_t[MAXRCV];
    static StringBuilder tips = new StringBuilder();

    // 环形缓冲区：最多保留 500 行，增量推送避免全量重绘
    private static final int MAX_LOG_LINES = 500;
    private final List<String> logRing = new ArrayList<>(MAX_LOG_LINES + 10);
    private final MutableLiveData<String> logLine = new MutableLiveData<>();
    private int lastLogLength = 0;
    int n = 0;//流动站数据个数
    int nn = 0;
    int nr = 0;//基准站数据个数
//    int rtcm_gps_obs_n = 0;
    private rtk_t rtk = new rtk_t();
    private prcopt_t prcopt;   // 实例级副本，避免与 RtkViewModel 共享 mode 冲突
    static boolean support_CarrierPhase = false;
    private static boolean carrierPhaseChecked = false;
    private static int carrierPhaseEpochCount = 0;
    double[] pos = new double[3];
    double[] dms1 = new double[3];
    double[] dms2 = new double[3];
    private int result;
    BufferedWriter bufferedWriter;
    static BufferedWriter obsWriter;
    static BufferedWriter baseWriter;
    pcv_t[] pcv = new pcv_t[3];
    double[] current_lat = new double[3];
    double[] current_lon = new double[3];
    double elevation = 0;
    SQLiteDatabase db;
    static SQLiteDatabase sDb;
    String msg1 = "";


    public DgnssViewModel(Context context) {
        String formattedTime = StorageUtil.getTimestamp();
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mText = new MutableLiveData<>();
        rtcm_num = new MutableLiveData<>();
        latvalue = new MutableLiveData<>();
        lonvalue = new MutableLiveData<>();
        height = new MutableLiveData<>();
        dgnsstips = new MutableLiveData<>();
        updateEAccuracy = new MutableLiveData<>();
        updateNAccuracy = new MutableLiveData<>();
        updateUAccuracy = new MutableLiveData<>();
        solstate = new MutableLiveData<>("");
        textColor = new MutableLiveData<>(android.graphics.Color.rgb(255, 59, 48));

        positiondatabase dbHelper = new positiondatabase(context);
        db = dbHelper.getWritableDatabase();
        sDb = db;

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
        mText.setValue("0");
        rtcm_num.setValue("0");
        latvalue.setValue(current_lat[0] + "°" + current_lat[1] + "′" + current_lat[2] + "″");
        lonvalue.setValue(current_lon[0] + "°" + current_lon[1] + "′" + current_lon[2] + "″");
        height.setValue(elevation + "m");
        tips.append("Start");
        Log.e("DGNSS_DIAG", "══════ SESSION START " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + " ══════");
        prcopt = new prcopt_t(SolViewModel.prcopt); // 实例副本，避免 mode 与 RTK 互相覆盖（必须在 readdata 之前初始化）
        SessionLogger.sol("DGNSS", "SESSION START — navsys=" + prcopt.getNavsys() + " mode=" + prcopt.getMode());
        //读取星历文件
        readdata(outputFilePath);
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
            flushLog();
            prcopt.mode = 1;
            prcopt.refpos = 1;
            File newFolder = StorageUtil.getDataDir(context);
            try {
                File out_folder = new File(newFolder, "DGNSS" + formattedTime + ".pos");
                FileWriter fileWriter = new FileWriter(out_folder);
                bufferedWriter = new BufferedWriter(fileWriter);
                out_head(bufferedWriter, prcopt, solopt_default);
                insertFileRecordAsync(db, out_folder.getAbsolutePath(), out_folder.getName(), "POS", "DGNSS", formattedTime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            rtkinit(rtk, prcopt);
    }

    static void write_log_head_baseandobs(Context context) {
        String formattedTime = StorageUtil.getTimestamp();
        try {
            File dir = StorageUtil.getDataDir(context);
            int yy = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).getYear() % 100;
            File out_folder_obs = new File(dir, "(user)dgnss_gnss_log" + formattedTime + "." + yy + "o");
            File out_folder_base = new File(dir, "(base)dgnss_gnss_log" + formattedTime + "." + yy + "o");
            FileWriter obsWriter1 = new FileWriter(out_folder_obs);
            FileWriter baseWriter1 = new FileWriter(out_folder_base);
            obsWriter = new BufferedWriter(obsWriter1);
            baseWriter = new BufferedWriter(baseWriter1);
            outLogHead(obsWriter, support_CarrierPhase);
            out_loghead_base(baseWriter);
            insertFileRecordAsync(sDb, out_folder_obs.getAbsolutePath(), out_folder_obs.getName(), "OBS", "DGNSS", formattedTime);
            insertFileRecordAsync(sDb, out_folder_base.getAbsolutePath(), out_folder_base.getName(), "OBS", "DGNSS", formattedTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("DefaultLocale")
    public static void out_loghead_base(BufferedWriter bufferedWriter) throws IOException {
        Date now = new Date();
        //RINEX Version Type
        bufferedWriter.write(String.format("     %3.2f           OBSERVATION DATA    M                   RINEX VERSION / TYPE", 3.04));
        bufferedWriter.newLine();
        bufferedWriter.write("G = GPS  R = GLONASS  E = GALILEO  J = QZSS  C = BDS        COMMENT             ");
        bufferedWriter.newLine();
        bufferedWriter.write("S = SBAS payload  M = Mixed                                 COMMENT             ");
        bufferedWriter.newLine();
        String PGM = String.format("%-20s", "GRitz Logger");
        String RUNBY = String.format("%-20s", Build.MODEL);
        String DATE = String.format("%-20s", now.getTime());
        bufferedWriter.write(PGM + RUNBY + DATE + "UTC PGM / RUN BY / DATE");
        bufferedWriter.newLine();
        bufferedWriter.write("                                                            MARKER NAME         ");
        bufferedWriter.newLine();
        bufferedWriter.write("                                                            MARKER NUMBER       ");
        bufferedWriter.newLine();
        bufferedWriter.write("                                                            OBSERVER / AGENCY   ");
        bufferedWriter.newLine();
        bufferedWriter.write("                                                            REC # / TYPE / VERS ");
        bufferedWriter.newLine();
        bufferedWriter.write("                                                            ANT # / TYPE        ");
        bufferedWriter.newLine();
        String X = String.format("%14.4f", MyService.stapos[0]);
        String Y = String.format("%14.4f", MyService.stapos[1]);
        String Z = String.format("%14.4f", MyService.stapos[2]);
        bufferedWriter.write(X + Y + Z + "                  " + "APPROX POSITION XYZ");
        bufferedWriter.newLine();
        bufferedWriter.write("        0.0000        0.0000        0.0000                  ANTENNA: DELTA H/E/N");
        bufferedWriter.newLine();
        bufferedWriter.write("     0                                                      RCV CLOCK OFFS APPL ");
        bufferedWriter.newLine();
        if (support_CarrierPhase) {
            bufferedWriter.write("G    8 C1C L1C D1C S1C C2P L2P D2P S2P                      SYS / # / OBS TYPES ");
            bufferedWriter.newLine();
        }
        if (support_CarrierPhase) {
            bufferedWriter.write("R    8 C1C L1C D1C S1C C2P L2P D2P S2P                      SYS / # / OBS TYPES ");
            bufferedWriter.newLine();
        }
        if (support_CarrierPhase) {
            bufferedWriter.write("J    6 C1C L1C S1C C5X L5X S5X                              SYS / # / OBS TYPES ");
            bufferedWriter.newLine();
        }
        if (support_CarrierPhase) {
            bufferedWriter.write("E    8 C1X L1X D1X S1X C5X L5X D5X S5X                      SYS / # / OBS TYPES ");
            bufferedWriter.newLine();
        }
        if (support_CarrierPhase) {
            bufferedWriter.write("C    4 C1I L1I D1I S1I                                      SYS / # / OBS TYPES ");
            bufferedWriter.newLine();
        }
        bufferedWriter.write("                                                            END OF HEADER");
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private static void rtkinit(rtk_t rtk, prcopt_t opt) {
        int i;
        rtk.sol = new sol_t();
        for (i = 0; i < 6; i++) rtk.rb[i] = 0.0;
        rtk.nx = opt.mode <= PMODE_FIXED ? NX(opt) : pppnx(opt);
        rtk.na = opt.mode <= PMODE_FIXED ? NR(opt) : pppnx(opt);
        rtk.tt = 0.0;
        rtk.x = zeros(rtk.nx, 1);
        rtk.P = zeros(rtk.nx, rtk.nx);
        rtk.xa = zeros(rtk.na, 1);
        rtk.Pa = zeros(rtk.na, rtk.na);
        rtk.nfix = rtk.neb = 0;
        for (i = 0; i < getMaxSat(); i++) {
            rtk.ambc[i] = new ambc_t();
            rtk.ssat[i] = new ssat_t();
        }
        for (i = 0; i < MAXERRMSG; i++) rtk.errbuf[i] = 0;
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
        if (infile == null || infile[0] == null) {
            tips.append("\n").append("File paths not initialized, skipping ephemeris read.");
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
                // ★ read_obsnav() 在纯星历模式下因 obs.n==0 返回0，但星历已正确填充到 navs
                read_obsnav(ts, te, ti, navFiles, navIndex, navFiles.length, prcopt, obss, navs, stas);
                if (navs.n > 0 || navs.ng > 0 || navs.ns > 0) {
                    tips.append("\n").append("Eph file read successfully.");
                    tips.append("\n").append("Current CN0 Threshold:").append(MainActivity.Cn0DbHz);
                    Log.d("RTD", "Read successfully. n=" + navs.n + " ng=" + navs.ng + " ns=" + navs.ns);
                } else {
                    tips.append("\n").append("Eph file read failed.");
                }
            } catch (IOException | CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        } else {
            tips.append("\n").append("No eph file available, please update the eph.");
        }
        //读取精密星历文件和钟差文件
//        if(prec.exists()&&clk.exists()){
//            try {
//                readpreceph(infile,infile.length,prcopt,navs,sbss);
//                if(navs.peph!=null){
//                    tips.append("\n").append("精密星历文件读取成功");
//                    tips.append("\n").append("钟差文件读取成功");
//                    requireActivity().runOnUiThread(() -> mTextView.setText(tips));
//                }else {
//                    tips.append("\n").append("精密星历文件读取失败");
//                    requireActivity().runOnUiThread(() -> mTextView.setText(tips));
//                }
//            } catch (IOException | CloneNotSupportedException e) {
//                throw new RuntimeException(e);
//            }
//        }else {
//            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
//            builder.setTitle("需要更新精密星历文件")
//                    .setCancelable(false)
//                    .setPositiveButton("确定", (dialogInterface, i) -> {
//                        // 点击“是”时执行以下代码
//                        ProgressDialog progressDialog = new ProgressDialog(requireActivity());
//                        progressDialog.setMessage("正在更新精密星历...");
//                        progressDialog.setCancelable(false); // 设置为不可取消
//                        progressDialog.show();
//                        // 在子线程中执行FTP操作
//                        new Thread(() -> {
//                            try {
//                                client = FtpConnection.getFTPClient();
//                                // .gz文件的路径
//                                assert client != null;
//                                if (client.isConnected()){
//                                    downloadFile(requireActivity(), client, remoteDirPath, infile, option);
//                                    client.logout();
//                                    client.disconnect();
//                                    FtpConnection.decompressGzipFile(infile, outputFilePath);
//                                }
//                                // 可以在这里处理FTPClient的其他操作
//                                // 执行完成后关闭加载圈
//                                cn.runOnUiThread(() -> {
//                                    Toast.makeText(requireActivity(), "更新成功", Toast.LENGTH_SHORT).show();
//                                    progressDialog.dismiss();
//                                });
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                                // 如果发生异常也关闭加载圈
//                                cn.runOnUiThread(() -> {
//                                    Toast.makeText(requireActivity(), "更新失败", Toast.LENGTH_SHORT).show();
//                                    progressDialog.dismiss();
//                                });
//                            }
//                        }).start();
//                    });
//            builder.show();
//        }
    }

    static void log_obs_base(obsd_t[] obs, obsd_t[] obsd, int nr) {
        gtime_t firstObsTime = null; // 初始化为 null，表示尚未设置
        int num_sat = 0;
        for (int i = 0; i < getMaxSat(); i++) {
            if (obs[i].sat[0] != 0) {
                num_sat++;
                if (firstObsTime == null) {
                    firstObsTime = obs[i].time; // 更新为第一次有效观测的时间
                }
            }
        }
        if (firstObsTime != null) {
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
                    } else if (obs[i].sat[0] >= 106 && obs[i].sat[0] <= 168) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("C%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obs[i].sat[0] >= 33 && obs[i].sat[0] <= 59) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("R%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obs[i].sat[0] >= 60 && obs[i].sat[0] <= 95) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("E%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
                                prn[0], obs[i].P[0], " ", " ", obs[i].L[0], " ", " ", obs[i].D[0], " ", " ", (double) obs[i].SNR[0], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obs[i].sat[0] >= 96 && obs[i].sat[0] <= 105) {
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

        if (nr > 0) {
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
                        String obsmeasurements = String.format("G%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", obsd[i].SNR[0], " ", " ",
                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", obsd[i].SNR[1], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obsd[i].sat[0] >= 106 && obsd[i].sat[0] <= 168) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("C%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", obsd[i].SNR[0], " ", " ",
                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", obsd[i].SNR[1], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obsd[i].sat[0] >= 33 && obsd[i].sat[0] <= 59) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("R%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", obsd[i].SNR[0], " ", " ",
                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", obsd[i].SNR[1], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obsd[i].sat[0] >= 60 && obsd[i].sat[0] <= 95) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("E%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", obsd[i].SNR[0], " ", " ",
                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", obsd[i].SNR[1], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    } else if (obsd[i].sat[0] >= 96 && obsd[i].sat[0] <= 105) {
                        @SuppressLint("DefaultLocale")
                        String obsmeasurements = String.format("J%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%3df%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%3d%s%s",
                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", obsd[i].SNR[0], " ", " ",
                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", obsd[i].SNR[1], " ", " ");
                        sb.append(obsmeasurements).append("\n");
                    }
                }
            }
//            for (int i = 0; i < getMaxSat(); i++) {
//                if (obsd[i].sat[0] != 0) {
//                    int[] prn = new int[1];
//                    sat_sys(obsd[i].sat[0], prn);
//                    if (obsd[i].sat[0] <= 32) {
//                        @SuppressLint("DefaultLocale")
//                        String obsmeasurements = String.format("G%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
//                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ",
//                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", (double) obsd[i].SNR[1], " ", " ");
//                        sb.append(obsmeasurements).append("\n");
//                    }else if(obsd[i].sat[0]>=106&&obsd[i].sat[0]<=168){
//                        @SuppressLint("DefaultLocale")
//                        String obsmeasurements = String.format("C%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
//                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ",
//                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", (double) obsd[i].SNR[1], " ", " ");
//                        sb.append(obsmeasurements).append("\n");
//                    }else if(obsd[i].sat[0]>=33&&obsd[i].sat[0]<=59){
//                        @SuppressLint("DefaultLocale")
//                        String obsmeasurements = String.format("R%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
//                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ",
//                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", (double) obsd[i].SNR[1], " ", " ");
//                        sb.append(obsmeasurements).append("\n");
//                    } else if(obsd[i].sat[0]>=60&&obsd[i].sat[0]<=95){
//                        @SuppressLint("DefaultLocale")
//                        String obsmeasurements = String.format("E%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
//                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ",
//                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", (double) obsd[i].SNR[1], " ", " ");
//                        sb.append(obsmeasurements).append("\n");
//                    }else if(obsd[i].sat[0]>=96&&obsd[i].sat[0]<=105){
//                        @SuppressLint("DefaultLocale")
//                        String obsmeasurements = String.format("J%02d%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s%14.3f%s%s",
//                                prn[0], obsd[i].P[0], " ", " ", obsd[i].L[0], " ", " ", obsd[i].D[0], " ", " ", (double) obsd[i].SNR[0], " ", " ",
//                                obsd[i].P[1], " ", " ", obsd[i].L[1], " ", " ", obsd[i].D[1], " ", " ", (double) obsd[i].SNR[1], " ", " ");
//                        sb.append(obsmeasurements).append("\n");
//                    }
//                }
//            }
            resetobs(obsd);
            try {
                baseWriter.write(sb.toString());
                baseWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void processGnssMeasurements(GnssMeasurementsEvent eventArgs) {
        msg = new StringBuilder("");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date currentTime = new Date();
        String currentTimeString = sdf.format(currentTime);
        List<GnssMeasurement> measurements = new ArrayList<>(eventArgs.getMeasurements());
        resetobs(obs);
        prepareEpochLargestTows(measurements);
        for (GnssMeasurement measurement : measurements) {
            readobs(measurement, eventArgs.getClock(), obs);
            if (!carrierPhaseChecked && (measurement.getAccumulatedDeltaRangeState() & GnssMeasurement.ADR_STATE_VALID) != 0) {
                support_CarrierPhase = true;
                carrierPhaseChecked = true;
            }
        }
        if (!carrierPhaseChecked) {
            carrierPhaseEpochCount++;
            if (carrierPhaseEpochCount >= 5) carrierPhaseChecked = true;
        }

        // ===== DIAGNOSTIC LOG =====
        int roverObsCount = (int) Arrays.stream(obs).filter(o -> o.sat[0] != 0).count();
        nr = (int) Arrays.stream(MyService.obssd).filter(obsdT -> (obsdT.sat[0] != 0)).count();
        boolean hasStaPos = (MyService.stapos[0] != 0 || MyService.stapos[1] != 0 || MyService.stapos[2] != 0);
        if (DIAG) {
            Log.d("DGNSS_DIAG", "=== Epoch ===");
            Log.d("DGNSS_DIAG", "Rover obs count: " + roverObsCount + ", Base obs count(nr): " + nr + ", hasStaPos: " + hasStaPos);
            Log.d("DGNSS_DIAG", "MyService.stapos: [" + MyService.stapos[0] + ", " + MyService.stapos[1] + ", " + MyService.stapos[2] + "]");
            Log.d("DGNSS_DIAG", "MyService.rtcm null?: " + (MyService.rtcm == null));
            Log.d("DGNSS_DIAG", "navs.n=" + navs.n + " navs.ng=" + navs.ng + " prcopt.mode=" + prcopt.getMode() + " navsys=" + prcopt.getNavsys() + " ionoopt=" + prcopt.getIonoopt() + " tropopt=" + prcopt.getTropopt());
        }
        // ★ 结构化日志
        SessionLogger.sol("DGNSS", "EPOCH " + currentTimeString + " | rover=" + roverObsCount + " base=" + nr + " | staPos=" + (hasStaPos?"Y":"N") + " | nav.n=" + navs.n + " nav.ng=" + navs.ng + " mode=" + prcopt.getMode());
        // ★ DIAG: 列出流动站和基准站卫星
        StringBuilder satDiag = new StringBuilder("rover_sats: ");
        for (obsd_t ob : obs) {
            if (ob.sat[0] != 0) satDiag.append("PRN").append(ob.sat[0]).append("(P=").append(String.format("%.1f", ob.P[0])).append(") ");
        }
        if (DIAG) {
            Log.d("DGNSS_DIAG", satDiag.toString());
        }
        StringBuilder baseDiag = new StringBuilder("base_sats: ");
        for (obsd_t ob : MyService.obssd) {
            if (ob.sat[0] != 0) baseDiag.append("PRN").append(ob.sat[0]).append(" ");
        }
        if (DIAG) {
            Log.d("DGNSS_DIAG", baseDiag.toString());
        }
        if (MyService.rtcm != null && MyService.rtcm.getObs() != null) {
            if (DIAG)
                Log.d("DGNSS_DIAG", "MyService.rtcm.getObs().data null?: " + (MyService.rtcm.getObs().data == null));
        }
        // Also write to UI tips
        tips.append("\n").append("[诊断] 流动站:").append(roverObsCount).append(" 基准站:").append(nr).append(" 基站坐标:").append(hasStaPos ? "有" : "无");
        // ===== END DIAGNOSTIC LOG =====

        rtcm_num.postValue(String.valueOf(nr));
        for (int i = 0; i < nr; i++) {
            if (MyService.obssd[0].time.time != MyService.obssd[i].time.time) {
                tips.append("\n").append(currentTimeString).append(":").append("Reference station:").append("The").append(i + 1).append("th one has an anomaly：").append("Satellite:").append(MyService.obssd[i].sat[0]).append("error occurred");
                flushLog();
                MyService.obssd[i].sat[0] = 0;
            }
        }
        if (isSpp(obs, navs) >= 4 && nr != 0) {
            if (DIAG) Log.d("DGNSS_DIAG", "Entering DGNSS computation: SPP valid=" + isSpp(obs, navs) + ", nr=" + nr);
            mText.postValue(String.valueOf(isSpp(obs, navs)));
            nn = 0;
//            rtcm_gps_obs_n = 0;
            try {
                //计算流动站数据个数
                n = (int) Arrays.stream(obs).filter(obsdT -> (obsdT.sat[0] != 0)).count();
                obsd_t[] temp = sortobs(obs);
                //判断流动站的数据来自同一个历元
                for (int i = 0; i < n; i++) {
                    if (temp[0].time.time == temp[i].time.time) {
                        nn++;
                    } else {
                        double diff = temp[0].time.time - temp[i].time.time;
                        tips.append("\n").append(currentTimeString).append(":").append("Rover:").append("The").append(i + 1).append("th one has an anomaly:").append("Satellite:").append(temp[i].sat[0]).append("error occurred").append("\n").append("The time difference is:").append(diff);
                        flushLog();
                    }
                }
                if (nn == n && n >= 4) {
                    obsd_t[] tempobssd = new obsd_t[nr];
                    for (int i = 0; i < nr; i++) {
                        tempobssd[i] = new obsd_t();
                    }
                    int ii = 0;
                    for (obsd_t obsdT : MyService.obssd) {
                        if (obsdT.sat[0] != 0) {
                            tempobssd[ii] = (obsd_t) obsdT.clone();
                            tempobssd[ii].rcv = 2;
                            ii++;
                        }
                    }
                    if (MyService.rtcm != null && MyService.rtcm.getObs() != null && MyService.rtcm.getObs().data != null) {
                        for (int iu = 0; iu < MyService.rtcm.getObs().data.length; iu++) {
                            if (MyService.rtcm.getObs().data[iu] != null)
                                MyService.rtcm.getObs().data[iu].rcv = 2;
                        }
                    }
                    //将两个站的数据赋值道dgnss_obs当中，用于计算
                    obsd_t[] dgnss_obs = new obsd_t[n + nr];
                    for (int i = 0; i < n + nr; i++) {
                        dgnss_obs[i] = new obsd_t();
                    }
                    for (int i = 0; i < n + nr; i++) {
                        if (i < n)
                            dgnss_obs[i] = (obsd_t) temp[i].clone();
                        else
                            dgnss_obs[i] = (obsd_t) tempobssd[i - n].clone();
                    }
                    prcopt.setRb(MyService.stapos);
                    rtk.opt = prcopt;
                    rtk.rb = MyService.stapos;
                    // ★ SPP 需要 mode=0，临时切换（mode!=0 会强制 ionoopt=1,tropopt=1 导致解算失败）
                    int savedMode = rtk.opt.getMode();
                    rtk.opt.setMode(PMODE_SINGLE);
                    int state = pnt_pos(temp, n, navs, rtk.opt, rtk.sol, null, rtk.ssat, msg1);
                    rtk.opt.setMode(savedMode);  // 恢复 mode 给 rel_pos 使用
                    if (DIAG) Log.d("DGNSS_DIAG", "SPP pnt_pos result: " + state + ", n=" + n + ", nr=" + nr + " savedMode=" + savedMode + " msg1=" + msg1);
                    tips.append("\n").append("[诊断] SPP解算=").append(state != 0 ? "成功" : "失败").append(" 流动站=").append(n).append(" 基准站=").append(nr);
                    flushLog();
                    if (state != 0) {
                        rtk.sol.stat = SOLQ_NONE;
                        //tips.append("\n").append(currentTimeString).append(":").append("Single-point positioning successful, proceeding to differential positioning...");
                        flushLog();
                        //计算差分龄期，大于某个阈值不予计算
                        double diff = local_timediff(dgnss_obs[0].time, dgnss_obs[n].time);
                        rtk.sol.age = (float) diff;
                        prcopt.setMaxtdiff(30); // DGPS 容许30秒龄期，原来1秒太紧导致不稳定
                        tips.append("\n").append("[诊断] 差分龄期=").append(String.format("%.2f", diff)).append("s (阈值30s)");
                        flushLog();
                        if (Math.abs(diff) >= 30.0) {
                            if (DIAG) Log.d("DGNSS_DIAG", "Age of diff too large: " + diff + "s (threshold 2.1s)");
                            SessionLogger.sol("DGNSS", "FAIL " + currentTimeString + " | age_diff=" + String.format("%.1f", diff) + "s");
                            tips.append("\n").append(currentTimeString).append(":").append("age of differential error:").append(diff);
                            flushLog();
                            resetobs(dgnss_obs);
                        } else {
                            //这里将配置结构体opt内基准站的坐标赋值给解算结构体rtk内基准站的坐标
                            result = rel_pos(rtk, dgnss_obs, n, nr, navs);
                            if (DIAG) Log.d("DGNSS_DIAG", "rel_pos result: " + result + " (>=1 means success)");
                            if (result >= 1) {
                                ecef2pos(rtk.sol.rr, pos);
                                deg2dms(pos[0] * R2D, dms1, 5);
                                deg2dms(pos[1] * R2D, dms2, 5);
                                if (DIAG) Log.d("DGNSS_DIAG", "SUCCESS lat=" + String.format("%.6f", pos[0]*R2D) + " lon=" + String.format("%.6f", pos[1]*R2D) + " h=" + String.format("%.2f", pos[2]) + " sol.stat=" + rtk.sol.stat + " ratio=" + rtk.sol.ratio);
                                // ★ 先写基础 RESULT（不依赖 sol.qr，避免数组越界阻断整个 SUCCESS 路径）
                                SessionLogger.sol("DGNSS", "RESULT " + currentTimeString
                                    + " | lat=" + String.format("%.8f", pos[0]*R2D)
                                    + " lon=" + String.format("%.8f", pos[1]*R2D)
                                    + " h=" + String.format("%.3f", pos[2])
                                    + " | stat=" + rtk.sol.stat + " ratio=" + String.format("%.1f", rtk.sol.ratio));

                                current_lat = dms1;
                                current_lon = dms2;

                                String strdms11 = dmsArrayToString(current_lat);
                                String strdms22 = dmsArrayToString(current_lon);


                                String formattedNumber_lat = df.format(current_lat[2]);
                                String formattedNumber_lon = df.format(current_lon[2]);

                                latvalue.postValue(current_lat[0] + "°" + current_lat[1] + "′" + formattedNumber_lat + "″");
                                lonvalue.postValue(current_lon[0] + "°" + current_lon[1] + "′" + formattedNumber_lon + "″");
                                String height1 = df.format(pos[2]);
                                height.postValue(height1);
                                float[] temp_qr = new float[rtk.sol.qr.length];
                                // 遍历原始数组，计算每个元素的开根号
                                for (int i = 0; i < rtk.sol.qr.length; i++) {
                                    temp_qr[i] = (float) Math.sqrt(Math.abs(rtk.sol.qr[i]));
                                }
                                String strqr = Arrays.toString(temp_qr);
                                // 写入定位数据库
                                insertDataAsync(db, currentTimeString, strdms11, strdms22, height1, strqr, String.valueOf(2));
                                StringBuilder sb = new StringBuilder();
                                solopt_default.timef = 0;
                                double[] P = new double[9];
                                double[] Q = new double[9];
                                soltocov(rtk.sol,P);
                                covenu(pos,P,Q);
                                updateEAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[0])))+"m");
                                updateNAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[4])))+"m");
                                updateUAccuracy.postValue(df1.format(Math.sqrt(Math.abs(Q[8])))+"m");
                                accuracyTracker.record(Math.sqrt(Math.abs(Q[0])), Math.sqrt(Math.abs(Q[4])), Math.sqrt(Math.abs(Q[8])));
                                SolutionMetrics.post("DGNSS",
                                        Math.sqrt(Math.abs(Q[0]) + Math.abs(Q[4])),
                                        Math.sqrt(Math.abs(Q[8])), n,
                                        pos[0] * R2D, pos[1] * R2D);
                                // ★ 精度信息单独追加（Q 来自 covenu，安全）
                                SessionLogger.sol("DGNSS", "PREC " + currentTimeString
                                    + " | sde=" + df1.format(Math.sqrt(Math.abs(Q[0])))
                                    + " sdn=" + df1.format(Math.sqrt(Math.abs(Q[4])))
                                    + " sdu=" + df1.format(Math.sqrt(Math.abs(Q[8]))));
                                solHistoryLine.postValue(String.format("#%d %s DGNSS %.5f° %.5f° %s",
                                        accuracyTracker.epochCount.getValue(), currentTimeString,
                                        pos[0] * R2D, pos[1] * R2D, height1));
                                solstate.postValue("DGNSS");
                                changeTextColor(android.graphics.Color.rgb(0, 122, 255)); // blue = float/dgps
                                out_sol(rtk.sol, solopt_default, sb);
                                try {
                                    bufferedWriter.write(sb.toString());
                                    bufferedWriter.flush();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                tips.append(String.format("\n%s DGNSS OK %dsat %.6f %.6f %s E:%s N:%s U:%s",
                                        currentTimeString.substring(11), n,
                                        pos[0] * R2D, pos[1] * R2D, height1,
                                        df1.format(Math.sqrt(Math.abs(Q[0]))),
                                        df1.format(Math.sqrt(Math.abs(Q[4]))),
                                        df1.format(Math.sqrt(Math.abs(Q[8])))));
                                flushLog();
                            } else {
                                if (DIAG) Log.d("DGNSS_DIAG", "rel_pos FAILED. result=" + result + ", n=" + n + ", nr=" + nr);
                                SessionLogger.sol("DGNSS", "FAIL " + currentTimeString + " | rel_pos=" + result + " | rover=" + n + " base=" + nr);
                                updateEAccuracy.postValue(0+"m");
                                updateNAccuracy.postValue(0+"m");
                                updateUAccuracy.postValue(0+"m");
                                resetobs(dgnss_obs);
                                tips.append("\n").append(currentTimeString.substring(11))
                                        .append(" DGNSS FAIL ").append(n).append("sat ")
                                        .append(msg.toString().replace("\n"," "));
                                flushLog();
                            }
                        }
                    } else {
                        if (DIAG) Log.d("DGNSS_DIAG", "SPP pnt_pos FAILED. state=" + state);
                        SessionLogger.sol("DGNSS", "FAIL " + currentTimeString + " | SPP_base state=" + state + " | rover=" + n);
                        updateEAccuracy.postValue(0+"m");
                        updateNAccuracy.postValue(0+"m");
                        updateUAccuracy.postValue(0+"m");
                        tips.append("\n").append(currentTimeString.substring(11))
                                .append(" DGNSS FAIL ").append(n).append("sat SPP:")
                                .append(msg.toString().replace("\n"," "));
                        flushLog();
                    }
                } else {
                    if (DIAG) Log.d("DGNSS_DIAG", "Rover data check failed: nn=" + nn + ", n=" + n + " (need nn==n or n>=4)");
                    SessionLogger.sol("DGNSS", "FAIL " + currentTimeString + " | epoch_check nn=" + nn + " n=" + n);
                    updateEAccuracy.postValue(0+"m");
                    updateNAccuracy.postValue(0+"m");
                    updateUAccuracy.postValue(0+"m");
                    tips.append("\n").append(currentTimeString)
                            .append(":").append("Rover data error.");
                    flushLog();
                }
            } catch (CloneNotSupportedException | NullPointerException e) {
                Log.e("DGNSS_DIAG", "Exception in DGNSS: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } else {
            int spp = isSpp(obs, navs);
            if (DIAG) Log.d("DGNSS_DIAG", "Skipping DGNSS: SPP=" + spp + " (<4 needed) or nr=" + nr + " (==0)");
            SessionLogger.sol("DGNSS", "SKIP " + currentTimeString + " | spp=" + spp + " nr=" + nr);
            tips.append("\n").append("[诊断] 跳过差分: SPP有效=").append(spp).append("(需>=4) 基准站=").append(nr).append("(需>0)");
            flushLog();
            mText.postValue(String.valueOf(spp));
        }
    }

    // 将 DMS 数组转换为字符串
    private String dmsArrayToString(double[] dms) {
        return Arrays.stream(dms)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    public static double local_timediff(gtime_t t1, gtime_t t2) {
        double diffInSeconds = t1.getTime() - t2.getTime();
        double diffInFractionalSeconds = t1.getSec() - t2.getSec();
        return diffInSeconds + diffInFractionalSeconds;
    }

    void closeWriter() {
        if (obsWriter != null) {
            try {
                obsWriter.close();
            } catch (IOException e) {
                Log.e("DGNSS_DIAG", "Failed to close obs writer", e);
            }
            obsWriter = null;
        }
        if (baseWriter != null) {
            try {
                baseWriter.close();
            } catch (IOException e) {
                Log.e("DGNSS_DIAG", "Failed to close base writer", e);
            }
            baseWriter = null;
        }
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
        return dgnsstips;
    }
    public MutableLiveData<String> getUpdateEAccuracy() {
        return updateEAccuracy;
    }
    public MutableLiveData<String> getUpdateNAccuracy() {
        return updateNAccuracy;
    }
    public MutableLiveData<String> getUpdateUAccuracy() {
        return updateUAccuracy;
    }
    public MutableLiveData<String> getsolstate() {
        return solstate;
    }
    public LiveData<Integer> getTextColor() {
        return textColor;
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
        SessionLogger.sol("DGNSS", "SESSION END");
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

}