/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.pntpos;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.pntpos.ephclk;
import com.rtklib.pntpos.ephpos;
import com.rtklib.pntpos.rescode;
import com.rtklib.readobsnav.satSys;
import com.rtklib.rtkpos.ppPos;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.util.Arrays;

public class satposs {
    public static final int EPHOPT_BRDC = 0;
    public static final int EPHOPT_PREC = 1;
    public static final int EPHOPT_SBAS = 2;
    public static final int EPHOPT_SSRAPC = 3;
    public static final int EPHOPT_SSRCOM = 4;
    public static final double CLIGHT = 2.99792458E8;
    public static final double STD_BRDCCLK = 30.0;
    private static final int NMAX = 10;
    private static final double MAXDTE = 900.0;
    private static final double EXTERR_EPH = 5.0E-7;
    private static final double EXTERR_CLK = 0.001;

    public static void sat_poss(gtime_t teph, obsd_t[] obs, int n, nav_t nav, int ephopt, double[] rs, double[] dts, double[] var, int[] svh) {
        gtime_t[] time = new gtime_t[192];
        double[] dt = new double[1];
        for (int i = 0; i < n && i < 192; ++i) {
            double[] tempdts;
            int j;
            for (j = 0; j < 6; ++j) {
                rs[j + i * 6] = 0.0;
            }
            for (j = 0; j < 2; ++j) {
                dts[j + i * 2] = 0.0;
            }
            var[i] = 0.0;
            svh[i] = 0;
            double pr = 0.0;
            for (j = 0; j < 3 && (pr = obs[i].P[j]) == 0.0; ++j) {
            }
            if (j >= 3) continue;
            gtime_t temptime = new gtime_t(obs[i].time.time, obs[i].time.sec);
            time[i] = util1.timeadd(temptime, -pr / 2.99792458E8);
            // ★ 精密模式(ephopt=1)使用SP3自带钟差，不依赖广播星历钟差
            if (ephopt != 1 && ephclk.eph_clk(time[i], teph, obs[i].sat, nav, dt) == 0) continue;
            time[i] = util1.timeadd(time[i], ephopt == 1 ? 0.0 : -dt[0]);
            double[] temprs = Arrays.copyOfRange(rs, i * 6, i * 6 + 6);
            if (satposs.sat_pos(time[i], teph, obs[i].sat, ephopt, nav, temprs, tempdts = Arrays.copyOfRange(dts, i, i + 2), var, svh, i) == 0) continue;
            // ★ 星历缺失/异常时 rs 或 dts 可能为 NaN，跳过避免污染 H 矩阵导致 LSQ 失败
            boolean bad = false;
            for (int ci = 0; ci < 6; ++ci) if (Double.isNaN(temprs[ci])) { bad = true; break; }
            if (!bad && (Double.isNaN(tempdts[0]) || Double.isNaN(tempdts[1]))) bad = true;
            if (bad) continue;
            System.arraycopy(temprs, 0, rs, i * 6, 6);
            System.arraycopy(tempdts, 0, dts, i * 2, 2);
            double[] tempdtss = Arrays.copyOfRange(dts, i * 2, i * 2 + 2);
            // ★ 钟差未计算（如广播模式）时补广播钟差；精密模式 dts 已由 SP3 提供
            if (dts[i * 2] == 0.0) {
                if (ephclk.eph_clk(time[i], teph, obs[i].sat, nav, tempdtss) == 0) continue;
                dts[i * 2] = tempdtss[0];
                dts[1 + i * 2] = 0.0;
            }
            var[i] = util2.SQR(30.0);
        }
    }

    public static int sat_pos(gtime_t time, gtime_t teph, int[] sat, int ephopt, nav_t nav, double[] rs, double[] dts, double[] var, int[] svh, int k) {
        svh[k] = 0;
        switch (ephopt) {
            case 0: {
                return ephpos.eph_pos(time, teph, sat, nav, -1, rs, dts, var, svh, k);
            }
            case 2: {
                return ephpos.satpos_sbas(time, teph, sat, nav, rs, dts, var, svh, k);
            }
            case 1: {
                if (!satposs.peph2pos(time, sat, nav, 1, rs, dts, var, k)) break;
                return 1;
            }
        }
        svh[k] = -1;
        return 0;
    }

    private static boolean peph2pos(gtime_t time, int[] sat, nav_t nav, int opt, double[] rs, double[] dts, double[] var, int k) {
        double[] rss = new double[3];
        double[] rst = new double[3];
        double[] dtss = new double[1];
        double[] dtst = new double[1];
        double[] dant = new double[]{0.0, 0.0, 0.0};
        double[] vare = new double[1];
        double[] varc = new double[1];
        double tt = 0.001;
        if (sat[0] <= 0 || nav_t.getMaxSat() < sat[0]) {
            return false;
        }
        if (!satposs.pephpos(time, sat, nav, rss, dtss, vare, varc) || !satposs.pephclk(time, sat, nav, dtss, varc)) {
            return false;
        }
        gtime_t time_tt = util1.timeadd(time, tt);
        if (!satposs.pephpos(time_tt, sat, nav, rst, dtst, null, null) || !satposs.pephclk(time_tt, sat, nav, dtst, null)) {
            return false;
        }
        if (opt != 0) {
            satposs.satantoff(time, rss, sat, nav, dant);
        }
        for (int i = 0; i < 3; ++i) {
            rs[i] = rss[i] + dant[i];
            rs[i + 3] = (rst[i] - rss[i]) / tt;
        }
        if (dtss[0] != 0.0) {
            dts[0] = dtss[0] - 2.0 * math.dot(rs, Arrays.copyOfRange(rs, 3, rs.length), 3) / 2.99792458E8 / 2.99792458E8;
            dts[1] = (dtst[0] - dtss[0]) / tt;
        } else {
            dts[1] = 0.0;
            dts[0] = 0.0;
        }
        if (var != null) {
            var[k] = vare[0] + varc[0];
        }
        return true;
    }

    private static void satantoff(gtime_t time, double[] rs, int[] sat, nav_t nav, double[] dant) {
        int i;
        pcv_t pcv = (nav.pcvs != null && sat[0] - 1 >= 0 && sat[0] - 1 < nav.pcvs.length)
                ? nav.pcvs[sat[0] - 1] : null;
        if (pcv == null || pcv.off == null) {
            dant[0] = dant[1] = dant[2] = 0.0;
            return;
        }
        double[] ex = new double[3];
        double[] ey = new double[3];
        double[] ez = new double[3];
        double[] es = new double[3];
        double[] r = new double[3];
        double[] rsun = new double[3];
        double[] gmst = new double[1];
        double[] erpv = new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
        double[] freq = new double[2];
        dant[2] = 0.0;
        dant[1] = 0.0;
        dant[0] = 0.0;
        ppPos.sunmoonpos(util1.gpst2utc(time), erpv, rsun, null, gmst);
        for (i = 0; i < 3; ++i) {
            r[i] = -rs[i];
        }
        if (!ppPos.normv3(r, ez)) {
            return;
        }
        for (i = 0; i < 3; ++i) {
            r[i] = rsun[i] - rs[i];
        }
        if (!ppPos.normv3(r, es)) {
            return;
        }
        ppPos.cross3(ez, es, r);
        if (!ppPos.normv3(r, ey)) {
            return;
        }
        ppPos.cross3(ey, ez, ex);
        int sys = satSys.sat_sys(sat[0], null);
        if (sys == 1 || sys == 16) {
            freq[0] = 1.57542E9;
            freq[1] = 1.2276E9;
        } else if (sys == 4) {
            freq[0] = rescode.sat2freq(sat, 1, nav);
            freq[1] = rescode.sat2freq(sat, 14, nav);
        } else if (sys == 8) {
            freq[0] = 1.57542E9;
            freq[1] = 1.20714E9;
        } else if (sys == 32) {
            freq[0] = 1.561098E9;
            freq[1] = 1.20714E9;
        } else if (sys == 64) {
            freq[0] = 1.17645E9;
            freq[1] = 2.492028E9;
        } else {
            return;
        }
        double C1 = util2.SQR(freq[0]) / (util2.SQR(freq[0]) - util2.SQR(freq[1]));
        double C2 = -util2.SQR(freq[1]) / (util2.SQR(freq[0]) - util2.SQR(freq[1]));
        for (i = 0; i < 3; ++i) {
            double dant1 = pcv.off[0][0] * ex[i] + pcv.off[0][1] * ey[i] + pcv.off[0][2] * ez[i];
            double dant2 = pcv.off[1][0] * ex[i] + pcv.off[1][1] * ey[i] + pcv.off[1][2] * ez[i];
            dant[i] = C1 * dant1 + C2 * dant2;
        }
    }

    private static boolean pephclk(gtime_t time, int[] sat, nav_t nav, double[] dts, double[] varc) {
        double std;
        double[] t = new double[2];
        double[] c = new double[2];
        if (nav.nc < 2 || util1.timediff(time, nav.getPclk()[0].getTime()) < -900.0 || util1.timediff(time, nav.getPclk()[nav.nc - 1].getTime()) > 900.0) {
            return true;
        }
        int i = 0;
        int j = nav.nc - 1;
        while (i < j) {
            int k = (i + j) / 2;
            if (util1.timediff(nav.getPclk()[k].getTime(), time) < 0.0) {
                i = k + 1;
                continue;
            }
            j = k;
        }
        int index = i <= 0 ? 0 : i - 1;
        t[0] = util1.timediff(time, nav.getPclk()[index].getTime());
        t[1] = util1.timediff(time, nav.getPclk()[index + 1].getTime());
        c[0] = nav.getPclk()[index].getClk()[sat[0] - 1][0];
        c[1] = nav.getPclk()[index + 1].getClk()[sat[0] - 1][0];
        if (t[0] <= 0.0) {
            dts[0] = c[0];
            if (dts[0] == 0.0) {
                return false;
            }
            std = (double)nav.getPclk()[index].getStd()[sat[0] - 1][0] * 2.99792458E8 - 0.001 * t[0];
        } else if (t[1] >= 0.0) {
            dts[0] = c[1];
            if (dts[0] == 0.0) {
                return false;
            }
            std = (double)nav.getPclk()[index + 1].getStd()[sat[0] - 1][0] * 2.99792458E8 + 0.001 * t[1];
        } else if (c[0] != 0.0 && c[1] != 0.0) {
            dts[0] = (c[1] * t[0] - c[0] * t[1]) / (t[0] - t[1]);
            i = t[0] < -t[1] ? 0 : 1;
            std = (double)nav.getPclk()[index + i].getStd()[sat[0] - 1][0] * 2.99792458E8 + 0.001 * Math.abs(t[i]);
        } else {
            return false;
        }
        if (varc[0] != 0.0) {
            varc[0] = util2.SQR(std);
        }
        return true;
    }

    private static boolean pephpos(gtime_t time, int[] sat, nav_t nav, double[] rs, double[] dts, double[] vare, double[] varc) {
        double[] t = new double[11];
        double[][] p = new double[3][11];
        double[] c = new double[2];
        double std = 0.0;
        double[] s = new double[3];
        dts[0] = 0.0;
        rs[2] = 0.0;
        rs[1] = 0.0;
        rs[0] = 0.0;
        if (nav.getNe() < 11 || util1.timediff(time, nav.peph[0].getTime()) < -900.0 || util1.timediff(time, nav.peph[nav.getNe() - 1].getTime()) > 900.0) {
            return false;
        }
        int i = 0;
        int j = nav.getNe() - 1;
        while (i < j) {
            int k = (i + j) / 2;
            if (util1.timediff(nav.peph[k].getTime(), time) < 0.0) {
                i = k + 1;
                continue;
            }
            j = k;
        }
        int index = i <= 0 ? 0 : i - 1;
        if ((i = index - 5) < 0) {
            i = 0;
        } else if (i + 10 >= nav.getNe()) {
            i = nav.getNe() - 10 - 1;
        }
        for (j = 0; j <= 10; ++j) {
            t[j] = util1.timediff(nav.peph[i + j].getTime(), time);
            if (!(math.norm(nav.peph[i + j].getPos()[sat[0] - 1], 3) <= 0.0)) continue;
            return false;
        }
        for (j = 0; j <= 10; ++j) {
            double[] pos = nav.peph[i + j].getPos()[sat[0] - 1];
            double sinl = Math.sin(7.2921151467E-5 * t[j]);
            double cosl = Math.cos(7.2921151467E-5 * t[j]);
            p[0][j] = cosl * pos[0] - sinl * pos[1];
            p[1][j] = sinl * pos[0] + cosl * pos[1];
            p[2][j] = pos[2];
        }
        for (i = 0; i < 3; ++i) {
            rs[i] = satposs.interppol(t, p[i], 11);
        }
        if (vare != null) {
            for (i = 0; i < 3; ++i) {
                s[i] = nav.peph[index].getStd()[sat[0] - 1][i];
            }
            std = math.norm(s, 3);
            if (t[0] > 0.0) {
                std += 5.0E-7 * util2.SQR(t[0]) / 2.0;
            } else if (t[10] < 0.0) {
                std += 5.0E-7 * util2.SQR(t[10]) / 2.0;
            }
            vare[0] = util2.SQR(std);
        }
        t[0] = util1.timediff(time, nav.peph[index].getTime());
        t[1] = util1.timediff(time, nav.peph[index + 1].getTime());
        c[0] = nav.peph[index].getPos()[sat[0] - 1][3];
        c[1] = nav.peph[index + 1].getPos()[sat[0] - 1][3];
        if (t[0] <= 0.0) {
            dts[0] = c[0];
            if (dts[0] != 0.0) {
                std = (double)nav.peph[index].getStd()[sat[0] - 1][3] * 2.99792458E8 - 0.001 * t[0];
            }
        } else if (t[1] >= 0.0) {
            dts[0] = c[1];
            if (dts[0] != 0.0) {
                std = (double)nav.peph[index + 1].getStd()[sat[0] - 1][3] * 2.99792458E8 + 0.001 * t[1];
            }
        } else if (c[0] != 0.0 && c[1] != 0.0) {
            dts[0] = (c[1] * t[0] - c[0] * t[1]) / (t[0] - t[1]);
            i = t[0] < -t[1] ? 0 : 1;
            std = (double)nav.peph[index + i].getStd()[sat[0] - 1][3] + 0.001 * Math.abs(t[i]);
        } else {
            dts[0] = 0.0;
        }
        if (varc != null) {
            varc[0] = util2.SQR(std);
        }
        return true;
    }

    private static double interppol(double[] x, double[] y, int n) {
        for (int j = 1; j < n; ++j) {
            for (int i = 0; i < n - j; ++i) {
                y[i] = (x[i + j] * y[i] - x[i] * y[i + 1]) / (x[i + j] - x[i]);
            }
        }
        return y[0];
    }
}

