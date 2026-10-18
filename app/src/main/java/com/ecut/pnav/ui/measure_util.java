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
package com.ecut.pnav.ui;

import static android.location.GnssMeasurement.ADR_STATE_VALID;
import static android.location.GnssStatus.CONSTELLATION_GALILEO;
import static android.location.GnssStatus.CONSTELLATION_GLONASS;
import static android.location.GnssStatus.CONSTELLATION_GPS;

import static com.ecut.pnav.MainActivity.Cn0DbHz;
import static com.rtklib.bean.nav_t.getMaxSat;
import static com.rtklib.bean.prcopt_t.MAXANT;
import static com.rtklib.pntpos.pntPos.PMODE_SINGLE;
import static com.rtklib.pntpos.rescode.R2D;
import static com.rtklib.pntpos.rescode.ecef2enu;
import static com.rtklib.pntpos.rescode.ecef2pos;
import static com.rtklib.postpos.outSol.SOLF_LLH;
import static com.rtklib.postpos.outSol.covenu;
import static com.rtklib.postpos.outSol.deg2dms;
import static com.rtklib.postpos.outSol.geoidh;
import static com.rtklib.postpos.outSol.opt2sep;
import static com.rtklib.postpos.outSol.soltocov;
import static com.rtklib.postpos.outSol.soltocov_vel;
import static com.rtklib.postpos.outSol.sqvar;
import static com.rtklib.readobsnav.readrnxh.SYS_ALL;
import static com.rtklib.readobsnav.readrnxh.SYS_CMP;
import static com.rtklib.readobsnav.readrnxh.SYS_GAL;
import static com.rtklib.readobsnav.readrnxh.SYS_GLO;
import static com.rtklib.readobsnav.readrnxh.SYS_GPS;
import static com.rtklib.rtkpos.rtkPos.D2R;
import static com.rtklib.util.util1.gpst2utc;
import static com.rtklib.util.util1.sprintf;
import static com.rtklib.util.util1.time2gpst;
import static com.rtklib.util.util1.time2str;

import static java.lang.Math.floor;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

import android.content.Context;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssStatus;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.ecut.pnav.R;

import com.rtklib.bean.eph_t;
import com.rtklib.bean.geph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.peph_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.snrmask_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.readobsnav.satSys;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Objects;
import java.util.function.IntFunction;

public class measure_util {
    static {
        try {
            System.loadLibrary("pnav");
        } catch (UnsatisfiedLinkError e) {
            Log.e("measure_util", "Failed to load native library: " + e.getMessage());
            throw e;
        }
    }
    private static final double c = 299792458;//光速 单位：m/s;
    public static double gps_l1_lamda = c/1.575420032e9;
    public static double gps_l2_lamda = c/1.227600075E9;
    public static double gps_l5_lamda = c/1.17645005e9;
    public static double bds_b1_lamda = c/1.561097984E9;
    public static double bds_b2b_lamda = c/1.20714E9;
    public static double bds_b3_lamda = c/1.26852E9;
    public static double gal_e5b_lamda = c/1.207140E9;
    static pcv_t[] pcv = new pcv_t[2];
    public static boolean[] first_epoch = new boolean[getMaxSat()];
    public static long[] first_fullbiasnanos = new long[getMaxSat()];
    public static double[] first_biasnanos = new double[getMaxSat()];
    public static double[] first_p = new double[getMaxSat()];
    public static boolean[] first_l_epoch = new boolean[getMaxSat()];
    public static double[] first_l = new double[getMaxSat()];
    public static boolean[] first = new boolean[getMaxSat()];


    public static final prcopt_t prcopt_default;

    static {
        prcopt_t p = new prcopt_t();
        p.setMode(PMODE_SINGLE);
        p.setSoltype(0);
        p.setNf(1);
        p.setNavsys(SYS_ALL);
        p.setElmin(15.0 * D2R);
        p.setSnrmask(new snrmask_t());
        p.setSateph(0);
        p.setModear(1);
        p.setGlomodear(1);
        p.setBdsmodear(1);
        p.setMaxout(5);
        p.setMinlock(0);
        p.setMinfix(10);
        p.setArmaxiter(1);
        p.setIonoopt(1); /* Klobuchar — 参照 GnssLogger SPP 默认开启 */
        p.setTropopt(1); /* Saastamoinen */
        p.setVarmodel(0);       /* 默认高度角模型 */
        p.setDynamics(0);
        p.setTidecorr(0);
        p.setNiter(1);
        p.setCodesmooth(0);
        p.setIntpref(0);
        p.setSbascorr(0);
        p.setSbassatsel(0);
        p.setRovpos(0);
        p.setRefpos(1);
        p.setEratio(new double[]{100.0, 100.0, 100.0, 100.0, 100.0, 100.0});
        p.setErr(new double[]{0.0, 100.0, 0.003, 0.003, 0.0, 1.0, 0.0, 0.0});
        p.setStd(new double[]{30.0, 0.03, 0.3});
        p.setPrn(new double[]{1E-4, 1E-3, 1E-4, 1E-1, 1E-2, 0.0});
        p.setSclkstab(5E-12);
        p.setThresar(new double[]{3.0, 0.9999, 0.25, 0.1, 0.05});
        p.setElmaskar(0.0);
        p.setElmaskhold(0.0);
        p.setThresslip(0.05);
        p.setMaxtdiff(30.0);
        p.setMaxinnoPhase(30.0);
        p.setMaxinnoCode(30.0);
        p.setMaxgdop(30.0);
        p.setBaseline(new double[]{0, 0});
        p.setRu(new double[]{0, 0, 0});
        p.setRb(new double[]{0, 0, 0});
        p.setAnttype(new char[2][MAXANT]);
        p.setAntdel(new double[2][3]);
        p.setPcvr(pcv);
        p.setExsats(new byte[nav_t.getMaxSat()]);
        prcopt_default = p;
    }
    public static final solopt_t solopt_default = new solopt_t(/* defaults solution output options */
            SOLF_LLH, 1, 1, 3,    /* posf,times,timef,timeu */
            1, 1, 0, 0, 0, 0, 0,              /* degf,outhead,outopt,outvel,datum,height,geoid */
            0, 0, 0,                      /* solstatic,sstat,trace */
            new double[]{0.0, 0.0},                  /* nmeaintv */
            " ".toCharArray(), "".toCharArray()                      /* separator/program name */
    );
    private measure_util(){
    }
    public static void resetobs(obsd_t[] obs) {
        for (obsd_t ob : obs) {
            ob.sat[0] = 0;
        }
    }
    public static void out_sol_record(String name, sol_t sol, solopt_t opt, StringBuilder sb) {
        gtime_t time;
        double gpst;
        char[] s = new char[64];
        int timeu;
        int[] week = new int[1];
        double[] pos = new double[3];
        double[] vel = new double[3];
        double[] dms1 = new double[3];
        double[] dms2 = new double[3];
        double[] P = new double[9];
        double[] Q = new double[9];
        char[] sep = opt2sep(opt);
        ecef2pos(sol.rr, pos);
        soltocov(sol, P);
        covenu(pos, P, Q);

        time = sol.time;
        timeu = opt.timeu < 0 ? 0 : (min(opt.timeu, 20));
//        time=gpst2utc(time);
        if (opt.timef != 0) time2str(time, s, timeu);
        else {
            gpst = time2gpst(time, week);
            if (86400 * 7 - gpst < 0.5 / pow(10.0, timeu)) {
                week[0]++;
                gpst = 0.0;
            }
            sprintf(s, "%4d%.16s%10.3f", week[0], sep[0], gpst);
        }

        if (opt.height == 1) { // geodetic height
            //获取指定地理位置的大地高（geoid height）。它根据输入的经纬度坐标（以弧度表示），通过引用地球模型来计算大地高。
            pos[2] -= geoidh(pos);
        }
        if (opt.degf != 0) {
            String temps = String.valueOf(s).trim();
            deg2dms(pos[0] * R2D, dms1, 5);
            deg2dms(pos[1] * R2D, dms2, 5);
            sb.append(temps).append(sep)
                    .append(String.format(Locale.US, "%4s", name)).append(sep)
                    .append(String.format(Locale.US, "%4.0f", dms1[0])).append(sep)
                    .append(String.format(Locale.US, "%02.0f", dms1[1])).append(sep)
                    .append(String.format(Locale.US, "%08.5f", dms1[2])).append(sep)
                    .append(String.format(Locale.US, "%4.0f", dms2[0])).append(sep)
                    .append(String.format(Locale.US, "%02.0f", dms2[1])).append(sep)
                    .append(String.format(Locale.US, "%08.5f", dms2[2]));
        } else {
            sb.append(sep).append(String.format(Locale.US, "%14.9f", pos[0] * R2D)).append(sep)
                    .append(String.format(Locale.US, "%14.9f", pos[1] * R2D));
        }
        sb.append(sep).append(String.format(Locale.US, "%10.4f", pos[2])).append(sep)
                .append(sol.stat).append(sep).append(sol.ns).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[4]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[0]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[8]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[1]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[2]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[5]))).append(sep)
                .append(String.format(Locale.US, "%6.2f", sol.age)).append(sep)
                .append(String.format(Locale.US, "%6.1f", sol.ratio));
        if (opt.outvel != 0) { // output velocity
            soltocov_vel(sol, P);
            ecef2enu(pos, Arrays.copyOfRange(sol.rr, 3, 6), vel);
            covenu(pos, P, Q);
            sb.append(sep).append(String.format(Locale.US, "%10.5f", vel[1])).append(sep)
                    .append(String.format(Locale.US, "%10.5f", vel[0])).append(sep)
                    .append(String.format(Locale.US, "%10.5f", vel[2])).append(sep)
                    .append(String.format(Locale.US, "%9.5f", sqrt(Q[4]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqrt(Q[0]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqrt(Q[8]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[1]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[2]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[5])));
        }
        sb.append("\r\n");
    }
    public static void out_sol(sol_t sol, solopt_t opt, StringBuilder sb) {
        gtime_t time;
        double gpst;
        char[] s = new char[64];
        int timeu;
        int[] week = new int[1];
        double[] pos = new double[3];
        double[] vel = new double[3];
        double[] dms1 = new double[3];
        double[] dms2 = new double[3];
        double[] P = new double[9];
        double[] Q = new double[9];
        char[] sep = opt2sep(opt);
        ecef2pos(sol.rr, pos);
        soltocov(sol, P);
        covenu(pos, P, Q);

        time = sol.time;
        timeu = opt.timeu < 0 ? 0 : (min(opt.timeu, 20));
//        time=gpst2utc(time);
        if (opt.timef != 0) time2str(time, s, timeu);
        else {
            gpst = time2gpst(time, week);
            if (86400 * 7 - gpst < 0.5 / pow(10.0, timeu)) {
                week[0]++;
                gpst = 0.0;
            }
            sprintf(s, "%4d%.16s%10.3f", week[0], sep[0], gpst);
        }

        if (opt.height == 1) { // geodetic height
            //获取指定地理位置的大地高（geoid height）。它根据输入的经纬度坐标（以弧度表示），通过引用地球模型来计算大地高。
            pos[2] -= geoidh(pos);
        }
        if (opt.degf != 0) {
            String temps = String.valueOf(s).trim();
            deg2dms(pos[0] * R2D, dms1, 5);
            deg2dms(pos[1] * R2D, dms2, 5);
            sb.append(temps).append(sep)
                    .append(String.format(Locale.US, "%4.0f", dms1[0])).append(sep)
                    .append(String.format(Locale.US, "%02.0f", dms1[1])).append(sep)
                    .append(String.format(Locale.US, "%08.5f", dms1[2])).append(sep)
                    .append(String.format(Locale.US, "%4.0f", dms2[0])).append(sep)
                    .append(String.format(Locale.US, "%02.0f", dms2[1])).append(sep)
                    .append(String.format(Locale.US, "%08.5f", dms2[2]));
        } else {
            sb.append(sep).append(String.format(Locale.US, "%14.9f", pos[0] * R2D)).append(sep)
                    .append(String.format(Locale.US, "%14.9f", pos[1] * R2D));
        }
        sb.append(sep).append(String.format(Locale.US, "%10.4f", pos[2])).append(sep)
                .append(sol.stat).append(sep).append(sol.ns).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[4]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[0]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqrt(Q[8]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[1]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[2]))).append(sep)
                .append(String.format(Locale.US, "%8.4f", sqvar(Q[5]))).append(sep)
                .append(String.format(Locale.US, "%6.2f", sol.age)).append(sep)
                .append(String.format(Locale.US, "%6.1f", sol.ratio));
        if (opt.outvel != 0) { // output velocity
            soltocov_vel(sol, P);
            ecef2enu(pos, Arrays.copyOfRange(sol.rr, 3, 6), vel);
            covenu(pos, P, Q);
            sb.append(sep).append(String.format(Locale.US, "%10.5f", vel[1])).append(sep)
                    .append(String.format(Locale.US, "%10.5f", vel[0])).append(sep)
                    .append(String.format(Locale.US, "%10.5f", vel[2])).append(sep)
                    .append(String.format(Locale.US, "%9.5f", sqrt(Q[4]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqrt(Q[0]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqrt(Q[8]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[1]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[2]))).append(sep)
                    .append(String.format(Locale.US, "%8.5f", sqvar(Q[5])));
        }
        sb.append("\r\n");
    }
    public static obsd_t[] sortobs(obsd_t[] obs) throws CloneNotSupportedException {
        // 使用流过滤和收集有效的 obsd_t 对象
        return Arrays.stream(obs)
                .filter(o -> o.sat[0] != 0) // 过滤掉 sat[0] 为 0 的对象
                .toArray(obsd_t[]::new);
    }
    //
    public static int isSpp(obsd_t[] obs, nav_t navs) {
        boolean[] matched = new boolean[obs.length];
        int k = 0;
        // 广播星历 GPS/BDS/GAL (eph)
        for (int i = 0; i < obs.length; i++) {
            obsd_t ob = obs[i];
            if (ob == null || ob.sat[0] == 0) continue;
            for (int b = 0; b < navs.n; b++) {
                if (navs.eph[b] != null && ob.sat[0] == navs.eph[b].getSat()) {
                    k++; matched[i] = true; break;
                }
            }
        }
        // GLONASS 广播星历 (geph)
        if (navs.geph != null) {
            for (int i = 0; i < obs.length; i++) {
                if (matched[i]) continue;
                obsd_t ob = obs[i];
                if (ob == null || ob.sat[0] == 0) continue;
                for (int b = 0; b < navs.ng; b++) {
                    if (navs.geph[b] != null && ob.sat[0] == navs.geph[b].getSat()) {
                        k++; matched[i] = true; break;
                    }
                }
            }
        }
        // 精密星历 (peph) — NRT/IGU 等 SP3 星历
        int kPeph = 0;
        if (navs.peph != null && navs.getNe() > 0) {
            peph_t firstEpoch = navs.peph[0];
            if (firstEpoch != null) {
                double[][] pos = firstEpoch.getPos();
                for (int i = 0; i < obs.length; i++) {
                    if (matched[i]) continue;
                    obsd_t ob = obs[i];
                    if (ob == null || ob.sat[0] == 0) continue;
                    int idx = ob.sat[0] - 1;
                    if (idx >= 0 && idx < pos.length && pos[idx][0] != 0.0) {
                        k++; kPeph++; matched[i] = true;
                    }
                }
            }
        }
        android.util.Log.d("isSpp", "total=" + k + " peph=" + kPeph
            + " ne=" + navs.getNe() + " nc=" + navs.getNc() + " obsN=" + obs.length);
        return k;
    }

    /** 诊断广播星历新鲜度 — 用当前系统时间对比各卫星 TOE，区分「有效/过期/未来」 */
    public static String checkEphFreshness(nav_t nav) {
        long nowSec = System.currentTimeMillis() / 1000;
        String[] sysNames = {"GPS", "GLO", "GAL", "BDS"};
        int[] totals = new int[4];
        int[] expired = new int[4];
        double minToe = Double.MAX_VALUE, maxToe = 0;
        int tooFar = 0; // TOE 距现在太远（24h+），可能是数据损坏

        for (int i = 0; i < nav.n; i++) {
            com.rtklib.bean.eph_t e = nav.eph[i];
            if (e == null) continue;
            double toeSec = e.getToe().time + e.getToe().sec;
            if (toeSec < minToe) minToe = toeSec;
            if (toeSec > maxToe) maxToe = toeSec;
            double ageSec = nowSec - toeSec;
            int sys = com.rtklib.readobsnav.satSys.sat_sys(e.getSat(), null);
            int si;
            if (sys == SYS_GPS) si = 0;
            else if (sys == SYS_GLO) si = 1;
            else if (sys == SYS_GAL) si = 2;
            else if (sys == SYS_CMP) si = 3;
            else continue;
            totals[si]++;
            double tmax = (si == 1) ? 1800 : (si == 2) ? 14400 : (si == 3) ? 21600 : 7200;
            if (ageSec > tmax) expired[si]++;
            if (Math.abs(ageSec) > 86400) tooFar++;
        }
        for (int i = 0; i < nav.ng; i++) {
            com.rtklib.bean.geph_t g = nav.geph[i];
            if (g == null) continue;
            double toeSec = g.getToe().time + g.getToe().sec;
            if (toeSec < minToe) minToe = toeSec;
            if (toeSec > maxToe) maxToe = toeSec;
            totals[1]++; // GLO
            if (nowSec - toeSec > 1800) expired[1]++;
            if (Math.abs(nowSec - toeSec) > 86400) tooFar++;
        }

        int totalAll = totals[0] + totals[1] + totals[2] + totals[3];
        int expiredAll = expired[0] + expired[1] + expired[2] + expired[3];

        SimpleDateFormat hhmm = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        hhmm.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        StringBuilder sb = new StringBuilder();

        if (totalAll == 0) {
            sb.append("星历为空");
        } else if (expiredAll == totalAll) {
            sb.append("全部过期!");
            if (minToe < Double.MAX_VALUE) {
                sb.append(" TOE ").append(hhmm.format(new Date((long)(minToe * 1000))))
                  .append("~").append(hhmm.format(new Date((long)(maxToe * 1000))));
            }
        } else if (tooFar > totalAll / 2) {
            sb.append("文件日期异常(TOE偏离>24h)，请重新下载");
        } else if (expiredAll > totalAll * 2 / 3) {
            sb.append("大部分过期 ").append(expiredAll).append("/").append(totalAll)
              .append(" TOE ").append(hhmm.format(new Date((long)(minToe * 1000))))
              .append("~").append(hhmm.format(new Date((long)(maxToe * 1000))));
        } else {
            // 正常情况：按系统列出
            for (int si = 0; si < 4; si++) {
                if (totals[si] == 0) continue;
                sb.append(sysNames[si]).append(totals[si]);
                if (expired[si] > 0) sb.append("(").append(expired[si]).append("过期)");
                sb.append(" ");
            }
            if (sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
            if (expiredAll > 0) sb.append(" | 共").append(expiredAll).append("过期");
        }

        return sb.toString();
    }

    public static int calculateSelectedValue(boolean[] checked) {
        int value = 0;
        if (checked[0]) value |= SYS_GPS; // GPS
        if (checked[1]) value |= SYS_CMP; // BDS
        if (checked[2]) value |= SYS_GLO; // GLONASS
        if (checked[3]) value |= SYS_GAL; // Galileo
        return value;
    }
    //计算GPS时（返回Unix时间纳秒，适配gtime_t的Unix纪元语义）
    public static double getgpst(GnssClock gnssClock){
        double GPST;
        // timeNanos - (FullBiasNanos + BiasNanos) = GPS时纳秒(自1980-01-06起算)
        // gtime_t.time 使用Unix纪元(1970-01-01)，需加 315964800秒 偏移
        GPST = gnssClock.getTimeNanos()-(gnssClock.getFullBiasNanos()+gnssClock.getBiasNanos()) + 315964800 * 1e9;
        return GPST;
    }
    /**
     * 计算伪距 — Google GNSS Logger 的"公共接收时刻"相对法。
     *
     * 核心思路：不计算绝对 GPS 接收时间 tRx，而是找出同一历元内所有测量中
     * 最大的 ReceivedSvTimeNanos（公共接收时刻参照），然后用相对差值计算伪距：
     *   delta_i = largestTowNs - tTx_i
     *   pseudorange = (AVERAGE_TRAVEL_TIME + delta_i * 1e-9) * c
     *
     * 完全避免了大数（~1.47e18 ns）运算，彻底解决 double 精度丢失问题。
     * 绝对偏移量（70ms 近似值）会被最小二乘的接收机钟差吸收。
     */
    private static final double AVERAGE_TRAVEL_TIME_SECONDS = 0.070; // 平均传播时间 70ms

    // TOW 解码状态位（参照 GnssLogger）
    private static final int STATE_TOW_DECODED = 0x8;

    // 三个独立 TOW 参照系
    private static final int GROUP_GPST = 0;
    private static final int GROUP_BDT  = 1;
    private static final int GROUP_TOD  = 2;

    // 每组拆两路：TOW_DECODED（整周 TOW）和非 TOW_DECODED（亚毫秒碎片），尺度互不污染
    private static long sDecGpst = 0, sRawGpst = 0;
    private static long sDecBdt  = 0, sRawBdt  = 0;
    private static long sDecTod  = 0, sRawTod  = 0;

    private static int getTowGroup(int constellationType) {
        if (constellationType == GnssStatus.CONSTELLATION_GLONASS) return GROUP_TOD;
        if (constellationType == GnssStatus.CONSTELLATION_BEIDOU)  return GROUP_BDT;
        return GROUP_GPST;
    }

    public static void prepareEpochLargestTows(List<GnssMeasurement> measurements) {
        sDecGpst = 0; sRawGpst = 0;
        sDecBdt  = 0; sRawBdt  = 0;
        sDecTod  = 0; sRawTod  = 0;

        for (GnssMeasurement m : measurements) {
            long tTx = m.getReceivedSvTimeNanos();
            if (tTx <= 0) continue;
            int group = getTowGroup(m.getConstellationType());
            boolean decoded = (m.getState() & STATE_TOW_DECODED) != 0;

            switch (group) {
                case GROUP_GPST:
                    if (decoded && tTx > sDecGpst) sDecGpst = tTx;
                    if (!decoded && tTx > sRawGpst) sRawGpst = tTx;
                    break;
                case GROUP_BDT:
                    if (decoded && tTx > sDecBdt) sDecBdt = tTx;
                    if (!decoded && tTx > sRawBdt) sRawBdt = tTx;
                    break;
                case GROUP_TOD:
                    if (decoded && tTx > sDecTod) sDecTod = tTx;
                    if (!decoded && tTx > sRawTod) sRawTod = tTx;
                    break;
            }
        }
    }

    public static double getPseudorange(GnssMeasurement measurement, GnssClock gnssClock) {
        long tTx = measurement.getReceivedSvTimeNanos();
        if (tTx <= 0) return -1;

        boolean decoded = (measurement.getState() & STATE_TOW_DECODED) != 0;
        long largestTowNs = 0;

        switch (getTowGroup(measurement.getConstellationType())) {
            case GROUP_TOD: largestTowNs = decoded ? sDecTod : sRawTod; break;
            case GROUP_BDT: largestTowNs = decoded ? sDecBdt : sRawBdt; break;
            default:        largestTowNs = decoded ? sDecGpst : sRawGpst; break;
        }

        if (largestTowNs <= 0) return -1;

        double deltaNs = (double) (largestTowNs - tTx);
        double pr = (AVERAGE_TRAVEL_TIME_SECONDS + deltaNs * 1e-9) * c;

        Log.d("DIAG_PRS", String.format("PRS const=%d svid=%d dec=%b ref=%d tTx=%d delta=%.0f pr=%.0f ok=%b",
            measurement.getConstellationType(), measurement.getSvid(), decoded, largestTowNs, tTx, deltaNs, pr, pr >= 1e6 && pr <= 1e8));
        return (pr >= 1e6 && pr <= 1e8) ? pr : -1;
    }
    /**
     * GPSTest 风格频率容差比较：将载波频率转为 MHz，用 2 MHz 容差匹配。
     * 解决不同手机/芯片上报频率略有偏差导致精确 == 匹配失败的问题。
     */
    private static final double FREQ_TOLERANCE_MHZ = 2.0;

    private static boolean freqMatch(double carrierHz, double expectedMhz) {
        return Math.abs(carrierHz / 1e6 - expectedMhz) <= FREQ_TOLERANCE_MHZ;
    }

    // GLONASS FDMA 频段范围（参照 Sean Barbeau 双频研究）
    // G1: 1598.0625 ~ 1605.375 MHz, G2: 1242.9375 ~ 1248.625 MHz
    private static final double GLO_G1_MIN_HZ = 1598.0625e6;
    private static final double GLO_G1_MAX_HZ = 1605.375e6;
    private static final double GLO_G2_MIN_HZ = 1242.9375e6;
    private static final double GLO_G2_MAX_HZ = 1248.625e6;

    private static boolean isGloG1(double freqHz) {
        return freqHz >= GLO_G1_MIN_HZ - 2e6 && freqHz <= GLO_G1_MAX_HZ + 2e6;
    }
    private static boolean isGloG2(double freqHz) {
        return freqHz >= GLO_G2_MIN_HZ - 2e6 && freqHz <= GLO_G2_MAX_HZ + 2e6;
    }

    /**
     * 读取GNSS测量数据并更新观测数据数组。
     * 该方法根据不同的卫星系统和载波频率，计算并记录伪距、载波相位、信噪比等信息。
     *
     * @param measurement 表示接收机测量到的GNSS信号数据，包括接收到的时间、卫星类型、载波频率等信息。
     * @param gnssClock 表示GNSS接收机的时钟信息，包括时钟偏差和闰秒等。
     * @param obs 表示观测数据的数组，将根据测量数据更新相应的观测信息。
     */
    public static void readobs(GnssMeasurement measurement, GnssClock gnssClock, obsd_t[] obs) {
        // 获取卫星类型
        int constellationType = measurement.getConstellationType();
        double carrier = measurement.getCarrierFrequencyHz();
        //获取卫星ID
        int Svid = measurement.getSvid();
        double pr = getPseudorange(measurement, gnssClock);
        double cn0 = measurement.getCn0DbHz();
        boolean matched = false;
        // ★ DIAG: 记录每个原始测量
        String constName;
        switch (constellationType) {
            case 1: constName = "GPS"; break;
            case 2: constName = "SBS"; break;
            case 3: constName = "GLO"; break;
            case 4: constName = "QZS"; break;
            case 5: constName = "BDS"; break;
            case 6: constName = "GAL"; break;
            case 7: constName = "IRN"; break;
            default: constName = "UNK"; break;
        }
        Log.d("DIAG_RAW", constName + " svid=" + Svid + " freq=" + String.format("%.3f", carrier/1e6) + "MHz pr=" + String.format("%.1f", pr) + " cn0=" + String.format("%.1f", cn0) + " tTxValid=" + (measurement.getReceivedSvTimeNanos() > 0));
        //如果是GPS
        if (constellationType == 1) {
            //如果是L1
            if (freqMatch(carrier, 1575.42) && pr != -1
                    && cn0 > Cn0DbHz) {
                // GPS L1
                obs[Svid - 1].D[0] = (float) (-measurement.getPseudorangeRateMetersPerSecond() / gps_l1_lamda);
                obs[Svid - 1].sat[0] = Svid;
                obs[Svid - 1].P[0] = getPseudorange(measurement, gnssClock);//记录伪距
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)== ADR_STATE_VALID)
                {//记录载波
                    //改正后
                    if(!first_epoch[Svid - 1]&&obs[Svid - 1].P[0]!=-1)
                    {
                        first_p[Svid-1] = floor(obs[Svid - 1].P[0]);
                        first_fullbiasnanos[Svid - 1] = gnssClock.getFullBiasNanos();
                        first_biasnanos[Svid - 1] = gnssClock.getBiasNanos();
                        first_epoch[Svid - 1]=true;
                    }else
                    {
                        if(!first_l_epoch[Svid-1]){
                            first_l[Svid-1] = measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda
                                    -(c/gps_l1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[Svid - 1])*1e-9
                                    -(c/gps_l1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[Svid - 1])*1e-9;
                            first_l_epoch[Svid-1] = true;
//                        obs[Svid - 1].L[0] = measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda
//                                -(c/gps_l1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[Svid - 1])*1e-9
//                                -(c/gps_l1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[Svid - 1])*1e-9;
                        }else{
                            if(!first[Svid-1]){
                            obs[Svid - 1].L[0] = first_p[Svid-1]/gps_l1_lamda+measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda
                                    -(c/gps_l1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[Svid - 1])*1e-9
                                    -(c/gps_l1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[Svid - 1])*1e-9 - first_l[Svid-1];
                                first[Svid-1] = true;
                            }else{
                                obs[Svid - 1].L[0] = obs[Svid - 1].L[0] + measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda
                                        -(c/gps_l1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[Svid - 1])*1e-9
                                        -(c/gps_l1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[Svid - 1])*1e-9 - first_l[Svid-1];
                            }
                            first_l[Svid-1] = measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda
                                    -(c/gps_l1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[Svid - 1])*1e-9
                                    -(c/gps_l1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[Svid - 1])*1e-9;
                        }
                    }
                    //改正前
//                    obs[Svid - 1].L[0] = measurement.getAccumulatedDeltaRangeMeters()/gps_l1_lamda;
                }else{
                    obs[Svid - 1].L[0] = 0;
                }
                obs[Svid - 1].SNR[0] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time/1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[Svid - 1].time = ttRx[0];
                obs[Svid - 1].rcv = 1;
                obs[Svid - 1].code[0] = 1;
            }
            else if (freqMatch(carrier, 1227.60) && getPseudorange(measurement, gnssClock) != -1
                    && measurement.getCn0DbHz() > Cn0DbHz) {
                // GPS L2
                obs[Svid - 1].sat[0] = Svid;
                obs[Svid - 1].P[1] = getPseudorange(measurement, gnssClock);
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID)
                    obs[Svid - 1].L[1] = measurement.getAccumulatedDeltaRangeMeters() / gps_l2_lamda;
                else
                    obs[Svid - 1].L[1] = 0;
                obs[Svid - 1].SNR[1] = (float) measurement.getCn0DbHz();
                obs[Svid - 1].D[1] = (float) (-measurement.getPseudorangeRateMetersPerSecond() / gps_l2_lamda);
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[Svid - 1].time = ttRx[0];
                obs[Svid - 1].rcv = 1;
                obs[Svid - 1].code[1] = 14;
            }
            else if (freqMatch(carrier, 1176.45) && getPseudorange(measurement, gnssClock) != -1
                    && measurement.getCn0DbHz() > Cn0DbHz) {
                // GPS L5
                obs[Svid - 1].sat[0] = Svid;
                obs[Svid - 1].P[2] = getPseudorange(measurement, gnssClock);//记录伪距
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)== ADR_STATE_VALID)
                    obs[Svid - 1].L[2] = (measurement.getAccumulatedDeltaRangeMeters())/gps_l5_lamda;
                else
                    obs[Svid - 1].L[2] = 0;
                obs[Svid - 1].SNR[2] = (float) measurement.getCn0DbHz();
                obs[Svid - 1].D[2] = (float) (-measurement.getPseudorangeRateMetersPerSecond() / gps_l5_lamda);
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time/1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[Svid - 1].time = ttRx[0];
                obs[Svid - 1].rcv = 1;
                obs[Svid - 1].code[2] = 25;
            }
        }
        if(constellationType == 5) {
            if (freqMatch(carrier, 1561.098) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // BDS B1I
                Log.d("DIAG_BDS", "BDS B1I svid=" + Svid + " freq=" + String.format("%.3f", carrier/1e6) + "MHz pr=" + String.format("%.0f", pr) + " cn0=" + String.format("%.1f", cn0));
                int cor_Svid = Svid + 105;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[0] = getPseudorange(measurement, gnssClock);
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID){
                    if(!first_epoch[cor_Svid - 1] && obs[cor_Svid - 1].P[0] != -1){
                        first_p[cor_Svid-1] = floor(obs[cor_Svid - 1].P[0]);
                        first_fullbiasnanos[cor_Svid - 1] = gnssClock.getFullBiasNanos();
                        first_biasnanos[cor_Svid - 1] = gnssClock.getBiasNanos();
                        first_epoch[cor_Svid - 1]=true;
                    }else{
                        if(!first_l_epoch[cor_Svid-1]){
                            first_l[cor_Svid-1] = measurement.getAccumulatedDeltaRangeMeters()/bds_b1_lamda
                                    -(c/bds_b1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[cor_Svid - 1])*1e-9
                                    -(c/bds_b1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[cor_Svid - 1])*1e-9;
                            first_l_epoch[cor_Svid-1] = true;
                        }else{
                            if(!first[cor_Svid-1]){
                            obs[cor_Svid - 1].L[0] = first_p[cor_Svid-1]/bds_b1_lamda+measurement.getAccumulatedDeltaRangeMeters()/bds_b1_lamda
                                    -(c/bds_b1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[cor_Svid - 1])*1e-9
                                    -(c/bds_b1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[cor_Svid - 1])*1e-9 - first_l[cor_Svid-1];
                                first[cor_Svid-1] = true;
                            }else{
                                obs[cor_Svid - 1].L[0] = obs[cor_Svid - 1].L[0] + measurement.getAccumulatedDeltaRangeMeters()/bds_b1_lamda
                                        -(c/bds_b1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[cor_Svid - 1])*1e-9
                                        -(c/bds_b1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[cor_Svid - 1])*1e-9 - first_l[cor_Svid-1];
                            }
                            first_l[cor_Svid-1] = measurement.getAccumulatedDeltaRangeMeters()/bds_b1_lamda
                                    -(c/bds_b1_lamda)*(gnssClock.getFullBiasNanos()-first_fullbiasnanos[cor_Svid - 1])*1e-9
                                    -(c/bds_b1_lamda)*(gnssClock.getBiasNanos()-first_biasnanos[cor_Svid - 1])*1e-9;
                        }
                    }
                } else {
                    obs[cor_Svid - 1].L[0] = 0;
                }
                obs[cor_Svid - 1].D[0] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[0] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[0] = 40;
            }
            if (freqMatch(carrier, 1575.42) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // BDS B1C
                int cor_Svid = Svid + 105;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[1] = getPseudorange(measurement, gnssClock);
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)== ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[1] = measurement.getAccumulatedDeltaRangeMeters()/(c/carrier);
                else
                    obs[cor_Svid - 1].L[1] = 0;
                obs[cor_Svid - 1].D[1] = (float) (measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[1] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[1] = 1;
            }
            if (freqMatch(carrier, 1207.14) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // BDS B2b (31 satellites) — slot [2]（修复冲突）
                int cor_Svid = Svid + 105;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[2] = getPseudorange(measurement, gnssClock);
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)== ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[2] = measurement.getAccumulatedDeltaRangeMeters()/bds_b2b_lamda;
                else
                    obs[cor_Svid - 1].L[2] = 0;
                obs[cor_Svid - 1].D[2] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[2] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[2] = 27;
            }
            if (freqMatch(carrier, 1176.45) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // BDS B2a — slot [3]（修复冲突，原 [2]）
                int cor_Svid = Svid + 105;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[3] = getPseudorange(measurement, gnssClock);
                if((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)== ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[3] = measurement.getAccumulatedDeltaRangeMeters()/gps_l5_lamda;
                else
                    obs[cor_Svid - 1].L[3] = 0;
                obs[cor_Svid - 1].D[3] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[3] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[3] = 25;
            }
        }
        else if (constellationType == 3) {
            // GLONASS (FDMA，每颗卫星频率不同)
            Log.d("DIAG_GLO", "GLO svid=" + Svid + " freq=" + String.format("%.3f", carrier/1e6) + "MHz pr=" + String.format("%.0f", pr) + " cn0=" + String.format("%.1f", cn0)
                + " isG1=" + isGloG1(carrier) + " isG2=" + isGloG2(carrier) + " qualOk=" + (pr != -1 && cn0 > Cn0DbHz));
            if (isGloG1(carrier) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // G1 频段 (FDMA: 1598.0625~1605.375 MHz) — slot [0]
                int cor_Svid = Svid + 32;
                double wavelength = c / carrier;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[0] = getPseudorange(measurement, gnssClock);
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID) {
                    if (!first_epoch[cor_Svid - 1] && obs[cor_Svid - 1].P[0] != -1) {
                        first_p[cor_Svid - 1] = floor(obs[cor_Svid - 1].P[0]);
                        first_fullbiasnanos[cor_Svid - 1] = gnssClock.getFullBiasNanos();
                        first_biasnanos[cor_Svid - 1] = gnssClock.getBiasNanos();
                        first_epoch[cor_Svid - 1] = true;
                    } else {
                        if (!first_l_epoch[cor_Svid - 1]) {
                            first_l[cor_Svid - 1] = measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9;
                            first_l_epoch[cor_Svid - 1] = true;
                        } else {
                            if (!first[cor_Svid - 1]) {
                                obs[cor_Svid - 1].L[0] = first_p[cor_Svid - 1] / wavelength
                                    + measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                    - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                    - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9
                                    - first_l[cor_Svid - 1];
                                first[cor_Svid - 1] = true;
                            } else {
                                obs[cor_Svid - 1].L[0] = obs[cor_Svid - 1].L[0]
                                    + measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                    - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                    - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9
                                    - first_l[cor_Svid - 1];
                            }
                            first_l[cor_Svid - 1] = measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9;
                        }
                    }
                } else {
                    obs[cor_Svid - 1].L[0] = 0;
                }
                obs[cor_Svid - 1].D[0] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[0] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[0] = 1;
            }
            else if (isGloG2(carrier) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // G2 频段 (FDMA: 1242.9375~1248.625 MHz) — slot [1]（新增）
                int cor_Svid = Svid + 32;
                double wavelength = c / carrier;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[1] = getPseudorange(measurement, gnssClock);
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID) {
                    if (!first_epoch[cor_Svid - 1] && obs[cor_Svid - 1].P[1] != -1) {
                        first_p[cor_Svid - 1] = floor(obs[cor_Svid - 1].P[1]);
                        first_fullbiasnanos[cor_Svid - 1] = gnssClock.getFullBiasNanos();
                        first_biasnanos[cor_Svid - 1] = gnssClock.getBiasNanos();
                        first_epoch[cor_Svid - 1] = true;
                    } else {
                        if (!first_l_epoch[cor_Svid - 1]) {
                            first_l[cor_Svid - 1] = measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9;
                            first_l_epoch[cor_Svid - 1] = true;
                        } else {
                            if (!first[cor_Svid - 1]) {
                                obs[cor_Svid - 1].L[1] = first_p[cor_Svid - 1] / wavelength
                                    + measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                    - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                    - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9
                                    - first_l[cor_Svid - 1];
                                first[cor_Svid - 1] = true;
                            } else {
                                obs[cor_Svid - 1].L[1] = obs[cor_Svid - 1].L[1]
                                    + measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                    - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                    - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9
                                    - first_l[cor_Svid - 1];
                            }
                            first_l[cor_Svid - 1] = measurement.getAccumulatedDeltaRangeMeters() / wavelength
                                - carrier * (gnssClock.getFullBiasNanos() - first_fullbiasnanos[cor_Svid - 1]) * 1e-9
                                - carrier * (gnssClock.getBiasNanos() - first_biasnanos[cor_Svid - 1]) * 1e-9;
                        }
                    }
                } else {
                    obs[cor_Svid - 1].L[1] = 0;
                }
                obs[cor_Svid - 1].D[1] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[1] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[1] = 2;
            }
        }
        else if(constellationType == 6){
            if (freqMatch(carrier, 1575.42) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // Galileo E1
                int cor_Svid = Svid + 59;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[0] = getPseudorange(measurement, gnssClock);//记录伪距
                if(measurement.getAccumulatedDeltaRangeState()== ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[0] = measurement.getAccumulatedDeltaRangeMeters()/(c/carrier);
                else
                    obs[cor_Svid - 1].L[0] = 0;
                obs[cor_Svid - 1].D[0] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[0] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[0] = 40;
            }
            if (freqMatch(carrier, 1207.14) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // Galileo E5b — slot [1]（新增）
                int cor_Svid = Svid + 59;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[1] = getPseudorange(measurement, gnssClock);
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[1] = measurement.getAccumulatedDeltaRangeMeters() / gal_e5b_lamda;
                else
                    obs[cor_Svid - 1].L[1] = 0;
                obs[cor_Svid - 1].D[1] = (float) (-measurement.getPseudorangeRateMetersPerSecond() * measurement.getCarrierFrequencyHz() / c);
                obs[cor_Svid - 1].SNR[1] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx = new gtime_t[1];
                ttRx[0] = new gtime_t(0, 0);
                ttRx[0].time = getgpst(gnssClock);
                ttRx[0].time = ttRx[0].time / 1e9;
                ttRx[0].sec = ttRx[0].time - floor(ttRx[0].time);
                ttRx[0].time = floor(ttRx[0].time);
                obs[cor_Svid - 1].time = ttRx[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[1] = 27;
            }
            if (freqMatch(carrier, 1176.45) && pr != -1 && measurement.getCn0DbHz() > Cn0DbHz) {
                // Galileo E5a
                int cor_Svid = Svid + 59;
                obs[cor_Svid - 1].sat[0] = cor_Svid;
                obs[cor_Svid - 1].P[2] = getPseudorange(measurement, gnssClock);
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID) == ADR_STATE_VALID)
                    obs[cor_Svid - 1].L[2] = measurement.getAccumulatedDeltaRangeMeters() / gps_l5_lamda;
                else
                    obs[cor_Svid - 1].L[2] = 0;
                obs[cor_Svid - 1].D[2] = (float) (-measurement.getPseudorangeRateMetersPerSecond() / gps_l5_lamda);
                obs[cor_Svid - 1].SNR[2] = (float) measurement.getCn0DbHz();
                gtime_t[] ttRx3 = new gtime_t[1];
                ttRx3[0] = new gtime_t(0, 0);
                ttRx3[0].time = getgpst(gnssClock);
                ttRx3[0].time = ttRx3[0].time / 1e9;
                ttRx3[0].sec = ttRx3[0].time - floor(ttRx3[0].time);
                ttRx3[0].time = floor(ttRx3[0].time);
                obs[cor_Svid - 1].time = ttRx3[0];
                obs[cor_Svid - 1].rcv = 1;
                obs[cor_Svid - 1].code[2] = 25;
            }
        }
    }

    // ==================== State 质量过滤 & 观测码映射 ====================

    /** 获取星座 RINEX 字母 */
    public static char getConstellationLetter(int constellationType) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:      return 'G';
            case GnssStatus.CONSTELLATION_GLONASS:   return 'R';
            case GnssStatus.CONSTELLATION_GALILEO:   return 'E';
            case GnssStatus.CONSTELLATION_BEIDOU:    return 'C';
            case GnssStatus.CONSTELLATION_QZSS:      return 'J';
            default:                                  return 'X';
        }
    }

    /**
     * 频率→RINEX band号，参照 android_rinex get_rnx_band_from_freq()
     * 注意：BDS B1I（1561.098MHz）在 RINEX 中使用 band 2，而非按公式 round(1561.098e6/10.23e6)=153
     */
    private static int freqToRinexBand(double freqHz, int constellationType) {
        if (freqMatch(freqHz, 1575.42)) return 1;  // GPS L1, BDS B1C, GAL E1, GLO G1
        if (freqMatch(freqHz, 1227.60)) return 2;  // GPS L2
        if (freqMatch(freqHz, 1176.45)) return 5;  // GPS L5, BDS B2a, GAL E5a
        if (freqMatch(freqHz, 1561.098)) return 2; // BDS B1I
        if (freqMatch(freqHz, 1207.14)) return 7;  // BDS B2b, GAL E5b
        if (isGloG1(freqHz)) return 1;   // GLO G1 (FDMA: 1598.0625~1605.375 MHz)
        if (isGloG2(freqHz)) return 2;   // GLO G2 (FDMA: 1242.9375~1248.625 MHz)
        return 1;
    }

    /** band + 星座 → RINEX attribute 字母，参照 android_rinex get_rnx_attr() */
    private static char freqToRinexAttr(int band, int constellationType, double freqHz) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                if (band == 5) return 'Q';      // L5
                if (band == 2) return 'W';      // L2 P(Y)
                return 'C';                      // L1 C/A
            case GnssStatus.CONSTELLATION_BEIDOU:
                if (band == 1) return 'P';      // B1C pilot
                if (band == 5) return 'P';      // B2a（导频。合法码 5D/5P/5X，非 5Q）
                if (band == 7) return 'Z';      // B2b
                if (band == 2) return 'I';      // B1I
                return 'I';
            case GnssStatus.CONSTELLATION_GLONASS:
                return 'C';                      // G1/G2 C/A
            case GnssStatus.CONSTELLATION_GALILEO:
                if (band == 5) return 'Q';      // E5a
                if (band == 7) return 'Q';      // E5b
                return 'C';                      // E1
            default:
                if (band == 5) return 'Q';
                return 'C';
        }
    }

    /**
     * 频率 + 星座 → RINEX 3 观测码（如 "1C", "5Q", "2I"）。
     * 参照 android_rinex get_obscode()
     */
    public static String frequencyToObsCode(double freqHz, int constellationType) {
        int band = freqToRinexBand(freqHz, constellationType);
        char attr = freqToRinexAttr(band, constellationType, freqHz);
        return band + String.valueOf(attr);
    }

    /**
     * State 质量过滤，参照 android_rinex check_sync_state() / check_trck_state()
     * @param m      GnssMeasurement
     * @param strict true=严格同步模式(sync), false=宽松跟踪模式(trck)
     * @return true 通过质量检查
     */
    public static boolean checkSyncState(GnssMeasurement m, boolean strict) {
        int state = m.getState();
        int constellation = m.getConstellationType();
        double freq = m.getCarrierFrequencyHz();

        // L5/E5a/B2a 等 CNAV 信号用 STATE_TOW_KNOWN(16384) 代替 STATE_TOW_DECODED(8)
        final int TOW_MASK = GnssMeasurement.STATE_TOW_DECODED | 0x4000; // STATE_TOW_KNOWN=16384

        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:
            case GnssStatus.CONSTELLATION_QZSS:
            case GnssStatus.CONSTELLATION_BEIDOU:
                if ((state & GnssMeasurement.STATE_CODE_LOCK) == 0) return false;
                if ((state & TOW_MASK) == 0) return false;
                if ((state & GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0) return false;
                if (strict) {
                    if ((state & GnssMeasurement.STATE_BIT_SYNC) == 0) return false;
                    if ((state & GnssMeasurement.STATE_SUBFRAME_SYNC) == 0) return false;
                }
                return true;

            case GnssStatus.CONSTELLATION_GLONASS: {
                int codeLock = state & GnssMeasurement.STATE_CODE_LOCK;
                int todKnown = state & (GnssMeasurement.STATE_GLO_TOD_DECODED | 0x8000);
                int msecAmb = state & GnssMeasurement.STATE_MSEC_AMBIGUOUS;
                boolean pass = codeLock != 0 && todKnown != 0 && msecAmb == 0;
                if (!pass) {
                    Log.d("DIAG_GLO_SYNC", String.format("GLO svid=%d state=0x%X codeLock=%d todKnown=0x%X msecAmb=0x%X pass=%b",
                        m.getSvid(), state, codeLock, todKnown, msecAmb, pass));
                }
                if ((state & GnssMeasurement.STATE_CODE_LOCK) == 0) return false;
                if ((state & GnssMeasurement.STATE_GLO_TOD_DECODED) == 0
                        && (state & 0x8000) == 0) return false;
                if ((state & GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0) return false;
                if (strict) {
                    if ((state & GnssMeasurement.STATE_SYMBOL_SYNC) == 0) return false;
                    if ((state & GnssMeasurement.STATE_BIT_SYNC) == 0) return false;
                    if ((state & GnssMeasurement.STATE_GLO_STRING_SYNC) == 0) return false;
                }
                return true;
            }

            case GnssStatus.CONSTELLATION_GALILEO:
                if (freqMatch(freq, 1575.42)) {
                    // E1 频段：需要 GAL_E1BC_CODE_LOCK
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if ((state & GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK) == 0) return false;
                        if ((state & GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0) return false;
                        if ((state & GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK) != 0) {
                            // E1C 跟踪：仅需 !MSEC_AMBIGUOUS（已检查）
                        } else {
                            // E1B 跟踪：需要完整导航同步
                            if ((state & GnssMeasurement.STATE_TOW_DECODED) == 0) return false;
                            if (strict) {
                                if ((state & GnssMeasurement.STATE_BIT_SYNC) == 0) return false;
                                if ((state & GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC) == 0) return false;
                            }
                        }
                    }
                    return true;
                } else {
                    // E5a/E5b：CNAV 用 TOW_KNOWN
                    if ((state & GnssMeasurement.STATE_CODE_LOCK) == 0) return false;
                    if ((state & TOW_MASK) == 0) return false;
                    if ((state & GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0) return false;
                    if (strict) {
                        if ((state & GnssMeasurement.STATE_BIT_SYNC) == 0) return false;
                        if ((state & GnssMeasurement.STATE_SUBFRAME_SYNC) == 0) return false;
                    }
                    return true;
                }

            default:
                if ((state & GnssMeasurement.STATE_CODE_LOCK) == 0) return false;
                if ((state & TOW_MASK) == 0) return false;
                if ((state & GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0) return false;
                return true;
        }
    }

    /**
     * 为 RINEX SYS / # / OBS TYPES 头生成一行。
     * @param sysLetter    星座字母 (G/R/E/C/J)
     * @param obscodes     该星座的观测码列表，如 {"1C","2W","5Q"}
     * @param supportCP    是否输出载波相位(L)
     * @return 格式化后的头行
     */
    public static String buildObsTypesLine(char sysLetter, String[] obscodes, boolean supportCP) {
        // 每个 obscode 展开: Cxx (Lxx) Dxx Sxx
        int count = obscodes.length * (supportCP ? 4 : 3);
        StringBuilder sb = new StringBuilder();
        sb.append(sysLetter).append(String.format("  %3d", count));
        for (String code : obscodes) {
            sb.append(String.format(" C%s", code));
            if (supportCP) sb.append(String.format(" L%s", code));
            sb.append(String.format(" D%s", code));
            sb.append(String.format(" S%s", code));
        }
        // 补齐到 60 字符 + 尾部标签
        while (sb.length() < 60) sb.append(' ');
        sb.append("SYS / # / OBS TYPES ");
        return sb.toString();
    }

    // ═══════════════════ 星历详情 ═══════════════════

    /** 星历详情 LiveData，供 PositioningFragment 观察 */
    public static final MutableLiveData<String> ephDetail = new MutableLiveData<>();
    /** 星历摘要 LiveData（文件名 + 大小），收起态显示 */
    public static final MutableLiveData<String> ephSummary = new MutableLiveData<>();

    /**
     * 生成星历详情字符串（精简版），更新 ephDetail / ephSummary LiveData。
     * @param ctx 须为已按用户所选语言包装的 Context（如 SppViewModel 持有的 Activity context），
     *            否则文案会回落到系统语言而非 App 内选定的语言。
     */
    public static String getEphDetail(Context ctx, nav_t nav, String ephFilePath) {
        if (nav == null || (nav.n == 0 && nav.ng == 0 && nav.getNe() == 0)) {
            String noData = ctx.getString(R.string.eph_no_data);
            ephDetail.postValue(noData);
            ephSummary.postValue(ctx.getString(R.string.eph_not_loaded));
            return noData;
        }

        SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.US);
        hhmm.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        SimpleDateFormat ymdhm = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        ymdhm.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        StringBuilder sb = new StringBuilder();
        long nowSec = System.currentTimeMillis() / 1000;

        // ── 广播星历 ──
        if (nav.n > 0 || nav.ng > 0) {
            // 按系统统计数量 + TOE窗口
            String[] sysNames = {"G", "R", "E", "C"};
            int[] sysCnt = new int[4];
            int[] sysIds = {SYS_GPS, SYS_GLO, SYS_GAL, SYS_CMP};
            double globalToeMin = Double.MAX_VALUE, globalToeMax = Double.MIN_VALUE;

            for (int i = 0; i < nav.n; i++) {
                eph_t e = nav.eph[i];
                int[] prn = new int[1];
                int s = satSys.sat_sys(e.getSat(), prn);
                double toe = e.getToe().time + e.getToe().sec;
                if (toe < globalToeMin) globalToeMin = toe;
                if (toe > globalToeMax) globalToeMax = toe;
                for (int si = 0; si < sysIds.length; si++) {
                    if (s == sysIds[si]) { sysCnt[si]++; break; }
                }
            }
            for (int i = 0; i < nav.ng; i++) {
                geph_t g = nav.geph[i];
                double toe = g.getToe().time + g.getToe().sec;
                if (toe < globalToeMin) globalToeMin = toe;
                if (toe > globalToeMax) globalToeMax = toe;
                sysCnt[1]++; // GLO
            }

            sb.append(ctx.getString(R.string.eph_broadcast_title)).append("\n ");
            for (int si = 0; si < 4; si++) {
                if (sysCnt[si] > 0) sb.append(" ").append(sysNames[si]).append(sysCnt[si]);
            }
            if (globalToeMin < Double.MAX_VALUE) {
                boolean valid = (nowSec >= globalToeMin - 7200) && (nowSec <= globalToeMax + 21600);
                sb.append("  ").append(ctx.getString(valid ? R.string.eph_valid : R.string.eph_expired))
                  .append(" · TOE ").append(hhmm.format(new Date((long)(globalToeMin * 1000))))
                  .append("~").append(hhmm.format(new Date((long)(globalToeMax * 1000))));
            }
            sb.append("\n\n");
        }

        // ── 精密星历 ──
        if (nav.getNe() > 0) {
            double first = nav.peph[0].getTime().time + nav.peph[0].getTime().sec;
            double last  = nav.peph[nav.getNe()-1].getTime().time + nav.peph[nav.getNe()-1].getTime().sec;
            int[] pc = new int[4];
            int[] sysIds = {SYS_GPS, SYS_GLO, SYS_GAL, SYS_CMP};
            String[] sysNames = {"G", "R", "E", "C"};
            double[][] pos0 = nav.peph[0].getPos();
            for (int sid = 1; sid <= nav_t.getMaxSat(); sid++) {
                if (pos0[sid-1][0] != 0.0) {
                    int[] prn = new int[1];
                    int sys = satSys.sat_sys(sid, prn);
                    for (int si = 0; si < sysIds.length; si++) {
                        if (sys == sysIds[si]) { pc[si]++; break; }
                    }
                }
            }
            sb.append(ctx.getString(R.string.eph_precise_title)).append("\n ");
            for (int si = 0; si < 4; si++) {
                if (pc[si] > 0) sb.append(" ").append(sysNames[si]).append(pc[si]);
            }
            sb.append("  ").append(ymdhm.format(new Date((long)(first*1000))))
              .append("→").append(ymdhm.format(new Date((long)(last*1000))));
            // 来源标记
            if (ephFilePath != null) {
                String fname = new java.io.File(ephFilePath).getName().toUpperCase();
                if (fname.contains("NRT")) sb.append("  NRT");
                else if (fname.contains("ULT")) sb.append("  ULT");
            }
            sb.append(" · ").append(ctx.getString(R.string.eph_epochs, nav.getNe())).append("\n\n");
        }

        String detail = sb.toString().trim();
        ephDetail.postValue(detail);

        // 摘要（始终可见行）
        StringBuilder sum = new StringBuilder();
        int brdcTotal = nav.n + nav.ng;
        if (brdcTotal > 0) sum.append(ctx.getString(R.string.eph_sum_broadcast, brdcTotal));
        if (nav.getNe() > 0) {
            if (sum.length() > 0) sum.append(" + ");
            sum.append(ctx.getString(R.string.eph_sum_precise, nav.getNe()));
        }
        if (sum.length() == 0) sum.append(ctx.getString(R.string.eph_not_loaded));
        ephSummary.postValue(sum.toString());
        return detail;
    }
}
