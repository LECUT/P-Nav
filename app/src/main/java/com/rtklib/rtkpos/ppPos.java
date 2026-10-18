/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.rtkpos;

import com.rtklib.bean.erp_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.rtk_t;
import com.rtklib.bean.sbsigp_t;
import com.rtklib.bean.sbsion_t;
import com.rtklib.bean.tec_t;
import com.rtklib.pntpos.rescode;
import com.rtklib.pntpos.satposs;
import com.rtklib.readobsnav.satSys;
import com.rtklib.rtkpos.rtkPos;
import com.rtklib.rtkpos.udState;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import jdk.internal.org.jline.utils.Log;

public class ppPos {
    public static final boolean IERS_MODEL = false;
    public static final int SOLQ_PPP = 6;
    public static final double MAX_STD_FIX = 0.15;
    public static final double VAR_IONO = util2.SQR(60.0);
    public static final double VAR_BIAS = util2.SQR(60.0);
    public static final double VAR_DCB = util2.SQR(30.0);
    public static final double THRES_MW_JUMP = 10.0;
    public static final double GMS = 1.327124E20;
    public static final double AS2R = 4.84813681109536E-6;
    public static final double GMM = 4.902801E12;
    public static final double GME = 3.986004415E14;
    public static final double ARMODE_INST = 2.0;
    public static final double ARMODE_FIXHOLD = 3.0;
    public static final double AU = 1.49597870691E11;
    private static final int MAX_ITER = 8;

    private static int PNF(prcopt_t opt) {
        if (opt.ionoopt == 3) {
            return 1;
        }
        return opt.getNf();
    }

    private static int PNP(prcopt_t opt) {
        if (opt.getDynamics() != 0) {
            return 9;
        }
        return 3;
    }

    private static int NC(prcopt_t opt) {
        return nav_t.NSYS;
    }

    private static int NT(prcopt_t opt) {
        if (opt.tropopt < 3) {
            return 0;
        }
        if (opt.tropopt == 3) {
            return 1;
        }
        return 3;
    }

    private static int NI(prcopt_t opt) {
        if (opt.ionoopt == 4) {
            return nav_t.getMaxSat();
        }
        return 0;
    }

    private static int ND(prcopt_t opt) {
        if (opt.getNf() >= 3) {
            return 1;
        }
        return 0;
    }

    private static int NR(prcopt_t opt) {
        return ppPos.PNP(opt) + ppPos.NC(opt) + ppPos.NT(opt) + ppPos.NI(opt) + ppPos.ND(opt);
    }

    private static int NB(prcopt_t opt) {
        return ppPos.PNF(opt) * nav_t.getMaxSat();
    }

    public static int PNX(prcopt_t opt) {
        return ppPos.NR(opt) + ppPos.NB(opt);
    }

    private static int IC(int s, prcopt_t opt) {
        return ppPos.PNP(opt) + s;
    }

    private static int IT(prcopt_t opt) {
        return ppPos.PNP(opt) + ppPos.NC(opt);
    }

    private static int II(int s, prcopt_t opt) {
        return ppPos.PNP(opt) + ppPos.NC(opt) + ppPos.NT(opt) + s - 1;
    }

    private static int ID(prcopt_t opt) {
        return ppPos.PNP(opt) + ppPos.NC(opt) + ppPos.NT(opt) + ppPos.NI(opt);
    }

    private static int IB(int s, int f, prcopt_t opt) {
        return ppPos.NR(opt) + nav_t.getMaxSat() * f + s - 1;
    }

    public static void udpos_ppp(rtk_t rtk) {
        int j;
        int i;
        double[] pos = new double[3];
        double[] Q = new double[9];
        double[] Qv = new double[9];
        if (rtk.opt.mode == udState.PMODE_PPP_FIXED) {
            for (int i2 = 0; i2 < 3; ++i2) {
                udState.initx(rtk, rtk.opt.getRu()[i2], 1.0E-8, i2);
            }
            return;
        }
        if (math.norm(rtk.x, 3) <= 0.0) {
            for (i = 0; i < 3; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], udState.VAR_POS, i);
            }
            if (rtk.opt.getDynamics() != 0) {
                for (i = 3; i < 6; ++i) {
                    udState.initx(rtk, rtk.sol.rr[i], udState.VAR_VEL, i);
                }
                for (i = 6; i < 9; ++i) {
                    udState.initx(rtk, 1.0E-6, udState.VAR_ACC, i);
                }
            }
        }
        if (rtk.opt.mode == udState.PMODE_PPP_STATIC) {
            for (i = 0; i < 3; ++i) {
                int n = i * (1 + rtk.nx);
                rtk.P[n] = rtk.P[n] + util2.SQR(rtk.opt.getPrn()[5]) * Math.abs(rtk.tt);
            }
            return;
        }
        if (rtk.opt.getDynamics() == 0) {
            for (i = 0; i < 3; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], udState.VAR_POS, i);
            }
            return;
        }
        int[] ix = math.imat(rtk.nx, 1);
        int nx = 0;
        for (i = 0; i < rtk.nx; ++i) {
            if (rtk.x[i] == 0.0 || !(rtk.P[i + i * rtk.nx] > 0.0)) continue;
            ix[nx++] = i;
        }
        double[] F = math.eye(nx);
        double[] P = math.mat(nx, nx);
        double[] FP = math.mat(nx, nx);
        double[] x = math.mat(nx, 1);
        double[] xp = math.mat(nx, 1);
        for (i = 0; i < 6; ++i) {
            F[i + (i + 3) * nx] = rtk.tt;
        }
        for (i = 0; i < 3; ++i) {
            F[i + (i + 6) * nx] = util2.SQR(rtk.tt) / 2.0;
        }
        for (i = 0; i < nx; ++i) {
            x[i] = rtk.x[ix[i]];
            for (j = 0; j < nx; ++j) {
                P[i + j * nx] = rtk.P[ix[i] + ix[j] * rtk.nx];
            }
        }
        math.matmul("NN", nx, 1, nx, 1.0, F, x, 0.0, xp);
        math.matmul("NN", nx, nx, nx, 1.0, F, P, 0.0, FP);
        math.matmul("NT", nx, nx, nx, 1.0, FP, F, 0.0, P);
        for (i = 0; i < nx; ++i) {
            rtk.x[ix[i]] = xp[i];
            for (j = 0; j < nx; ++j) {
                rtk.P[ix[i] + ix[j] * rtk.nx] = P[i + j * nx];
            }
        }
        Q[0] = Q[4] = util2.SQR(rtk.opt.getPrn()[3]) * Math.abs(rtk.tt);
        Q[8] = util2.SQR(rtk.opt.getPrn()[4]) * Math.abs(rtk.tt);
        rescode.ecef2pos(rtk.x, pos);
        util2.covecef(pos, Q, Qv);
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 3; ++j) {
                int n = i + 6 + (j + 6) * rtk.nx;
                rtk.P[n] = rtk.P[n] + Qv[i + j * 3];
            }
        }
    }

    public static void udclk_ppp(rtk_t rtk) {
        for (int i = 0; i < nav_t.NSYS; ++i) {
            double dtr = rtk.opt.getSateph() == 1 ? rtk.sol.dtr[0] : (i == 0 ? rtk.sol.dtr[0] : rtk.sol.dtr[0] + rtk.sol.dtr[i]);
            udState.initx(rtk, 2.99792458E8 * dtr, udState.VAR_CLK, ppPos.IC(i, rtk.opt));
        }
    }

    public static void udtrop_ppp(rtk_t rtk) {
        block4: {
            int i;
            block3: {
                double[] pos = new double[3];
                double[] azel = new double[]{0.0, 1.5707963267948966};
                double[] var = new double[1];
                i = ppPos.IT(rtk.opt);
                if (rtk.x[i] != 0.0) break block3;
                rescode.ecef2pos(rtk.sol.rr, pos);
                double ztd = rescode.sbstropcorr(rtk.sol.time, pos, azel, var);
                udState.initx(rtk, ztd, var[0], i);
                if (rtk.opt.tropopt < 4) break block4;
                for (int j = i + 1; j < i + 3; ++j) {
                    udState.initx(rtk, 1.0E-6, udState.VAR_GRA, j);
                }
                break block4;
            }
            int n = i + i * rtk.nx;
            rtk.P[n] = rtk.P[n] + util2.SQR(rtk.opt.getPrn()[2]) * Math.abs(rtk.tt);
            if (rtk.opt.tropopt >= 4) {
                for (int j = i + 1; j < i + 3; ++j) {
                    int n2 = j + j * rtk.nx;
                    rtk.P[n2] = rtk.P[n2] + util2.SQR(rtk.opt.getPrn()[2] * 0.1) * Math.abs(rtk.tt);
                }
            }
        }
    }

    public static void udiono_ppp(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        int j;
        int i;
        double[] pos = new double[3];
        int gap_resion = 120;
        int index = Arrays.toString(rtk.opt.getPppopt()).indexOf("-GAP_RESION=");
        if (index != -1) {
            char[] p = Arrays.toString(rtk.opt.getPppopt()).substring(index).toCharArray();
            Scanner scanner = new Scanner(Arrays.toString(p));
            gap_resion = scanner.nextInt();
        }
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            j = ppPos.II(i + 1, rtk.opt);
            if (rtk.x[j] == 0.0 || (int)rtk.ssat[i].outc[0] <= gap_resion) continue;
            rtk.x[j] = 0.0;
        }
        for (i = 0; i < n; ++i) {
            j = ppPos.II(obs[i].sat[0], rtk.opt);
            if (rtk.x[j] == 0.0) {
                double freq1 = rescode.sat2freq(obs[i].sat, obs[i].code[0], nav);
                double freq2 = rescode.sat2freq(obs[i].sat, obs[i].code[1], nav);
                if (obs[i].P[0] == 0.0 || obs[i].P[1] == 0.0 || freq1 == 0.0 || freq2 == 0.0) continue;
                double ion = (obs[i].P[0] - obs[i].P[1]) / (util2.SQR(1.57542E9 / freq1) - util2.SQR(1.57542E9 / freq2));
                rescode.ecef2pos(rtk.sol.rr, pos);
                double[] azel = rtk.ssat[obs[i].sat[0] - 1].azel;
                udState.initx(rtk, ion /= rtkPos.ionmapf(pos, azel), VAR_IONO, j);
                continue;
            }
            double sinel = Math.sin(Math.max(rtk.ssat[obs[i].sat[0] - 1].azel[1], 0.08726646259971647));
            int n2 = j + j * rtk.nx;
            rtk.P[n2] = rtk.P[n2] + util2.SQR(rtk.opt.getPrn()[1] / sinel) * Math.abs(rtk.tt);
        }
    }

    public static void udstate_ppp(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        ppPos.udpos_ppp(rtk);
        ppPos.udclk_ppp(rtk);
        if (rtk.opt.tropopt == 3 || rtk.opt.tropopt == 4) {
            ppPos.udtrop_ppp(rtk);
        }
        if (rtk.opt.ionoopt == 4) {
            ppPos.udiono_ppp(rtk, obs, n, nav);
        }
        if (rtk.opt.getNf() >= 3) {
            ppPos.uddcb_ppp(rtk);
        }
        ppPos.udbias_ppp(rtk, obs, n, nav);
    }

    private static void udbias_ppp(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        int j;
        int i;
        double[] L = new double[3];
        double[] P = new double[3];
        double[] Lc = new double[1];
        double[] Pc = new double[1];
        double[] bias = new double[96];
        double offset = 0.0;
        double[] pos = new double[]{0.0, 0.0, 0.0};
        double[] dantr = new double[3];
        double[] dants = new double[3];
        int[] sat = new int[1];
        int[] slip = new int[96];
        boolean clk_jump = false;
        if (rtk.opt.getPosopt()[5] != 0) {
            clk_jump = ppPos.ROUND(util1.time2gpst(obs[0].time, null) * 10.0) % 864000 == 0;
        }
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < rtk.opt.getNf(); ++j) {
                rtk.ssat[i].slip[j] = 0;
            }
        }
        ppPos.detslp_gf(rtk, obs, n, nav);
        ppPos.detslp_mw(rtk, obs, n, nav);
        rescode.ecef2pos(rtk.sol.rr, pos);
        for (int f = 0; f < rtkPos.NF(rtk.opt); ++f) {
            for (i = 0; i < nav_t.getMaxSat(); ++i) {
                int p = nav_t.getMaxSat();
                int n2 = f;
                rtk.ssat[i].outc[n2] = rtk.ssat[i].outc[n2] + 1L;
                if (rtk.ssat[i].outc[n2] <= (long)rtk.opt.getMaxout() && (double)rtk.opt.getModear() != 2.0 && !clk_jump) continue;
                udState.initx(rtk, 0.0, 0.0, ppPos.IB(i + 1, f, rtk.opt));
            }
            int k = 0;
            for (i = 0; i < n && i < 96; ++i) {
                sat = obs[i].sat;
                j = ppPos.IB(sat[0], f, rtk.opt);
                ppPos.corr_meas(obs[i], nav, rtk.ssat[sat[0] - 1].azel, rtk.opt, dantr, dants, new double[]{0.0}, L, P, Lc, Pc);
                bias[i] = 0.0;
                if (rtk.opt.ionoopt == 3) {
                    bias[i] = Lc[0] - Pc[0];
                    slip[i] = rtk.ssat[sat[0] - 1].slip[0] != 0 ? rtk.ssat[sat[0] - 1].slip[0] : rtk.ssat[sat[0] - 1].slip[1];
                } else if (L[f] != 0.0 && P[f] != 0.0) {
                    double freq1 = rescode.sat2freq(sat, obs[i].code[0], nav);
                    double freq2 = rescode.sat2freq(sat, obs[i].code[f], nav);
                    slip[i] = rtk.ssat[sat[0] - 1].slip[f];
                    if (obs[i].P[0] == 0.0 || obs[i].P[1] == 0.0 || freq1 == 0.0 || freq2 == 0.0) continue;
                    double ion = (obs[i].P[0] - obs[i].P[f]) / (1.0 - util2.SQR(freq1 / freq2));
                    bias[i] = L[f] - P[f] + 2.0 * ion * util2.SQR(freq1 / freq2);
                }
                if (rtk.x[j] == 0.0 || slip[i] != 0 || bias[i] == 0.0) continue;
                offset += bias[i] - rtk.x[j];
                ++k;
            }
            if (k >= 2 && Math.abs(offset / (double)k) > 149896.229) {
                for (i = 0; i < nav_t.getMaxSat(); ++i) {
                    j = ppPos.IB(i + 1, f, rtk.opt);
                    if (rtk.x[j] == 0.0) continue;
                    int n3 = j;
                    rtk.x[n3] = rtk.x[n3] + offset / (double)k;
                }
            }
            for (i = 0; i < n && i < 96; ++i) {
                sat = obs[i].sat;
                j = ppPos.IB(sat[0], f, rtk.opt);
                int n4 = j + j * rtk.nx;
                rtk.P[n4] = rtk.P[n4] + util2.SQR(rtk.opt.getPrn()[0]) * Math.abs(rtk.tt);
                if (bias[i] == 0.0 || rtk.x[j] != 0.0 && slip[i] == 0) continue;
                udState.initx(rtk, bias[i], VAR_BIAS, ppPos.IB(sat[0], f, rtk.opt));
                ArrayList<String> arrayList5 = new ArrayList<String>();
                for (int kk = 0; kk < rtk.P.length; ++kk) {
                    if (rtk.P[kk] == 0.0) continue;
                    arrayList5.add(String.valueOf(rtk.P[kk]));
                }
                for (k = 0; k < nav_t.getMaxSat(); ++k) {
                    rtk.ambc[sat[0] - 1].flags[k] = '\u0000';
                }
            }
        }
    }

    private static void corr_meas(obsd_t obs, nav_t nav, double[] azel, prcopt_t opt, double[] dantr, double[] dants, double[] phw, double[] L, double[] P, double[] Lc, double[] Pc) {
        double[] freq = new double[3];
        int sys = satSys.sat_sys(obs.sat[0], null);
        for (int i = 0; i < 3; ++i) {
            P[i] = 0.0;
            L[i] = 0.0;
            freq[i] = rescode.sat2freq(obs.sat, obs.code[i], nav);
            if (freq[i] == 0.0 || obs.L[i] == 0.0 || obs.P[i] == 0.0 || rescode.testsnr(0, 0, azel[1], obs.SNR[i], opt.getSnrmask())) continue;
            L[i] = obs.L[i] * 2.99792458E8 / freq[i] - dants[i] - dantr[i] - phw[0] * 2.99792458E8 / freq[i];
            P[i] = obs.P[i] - dants[i] - dantr[i];
            if (sys != 1 && sys != 4) continue;
            if (obs.code[i] == 1) {
                int n = i;
                P[n] = P[n] + nav.cbias[obs.sat[0] - 1][1];
            }
            if (obs.code[i] != 14) continue;
            int n = i;
            P[n] = P[n] + nav.cbias[obs.sat[0] - 1][2];
        }
        Pc[0] = 0.0;
        Lc[0] = 0.0;
        if (freq[0] == 0.0 || freq[1] == 0.0) {
            return;
        }
        double C1 = util2.SQR(freq[0]) / (util2.SQR(freq[0]) - util2.SQR(freq[1]));
        double C2 = -util2.SQR(freq[1]) / (util2.SQR(freq[0]) - util2.SQR(freq[1]));
        if (L[0] != 0.0 && L[1] != 0.0) {
            Lc[0] = C1 * L[0] + C2 * L[1];
        }
        if (P[0] != 0.0 && P[1] != 0.0) {
            Pc[0] = C1 * P[0] + C2 * P[1];
        }
    }

    private static void detslp_mw(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        for (int i = 0; i < n && i < 96; ++i) {
            double w1 = ppPos.mwmeas(obs[i], nav);
            if (w1 == 0.0) continue;
            double w0 = rtk.ssat[obs[i].sat[0] - 1].mw[0];
            rtk.ssat[obs[i].sat[0] - 1].mw[0] = w1;
            if (w0 == 0.0 || !(Math.abs(w1 - w0) > 10.0)) continue;
            int j = 0;
            while (j < rtk.opt.getNf()) {
                int n2 = j++;
                rtk.ssat[obs[i].sat[0] - 1].slip[n2] = (byte)(rtk.ssat[obs[i].sat[0] - 1].slip[n2] | 1);
            }
        }
    }

    private static double mwmeas(obsd_t obs, nav_t nav) {
        double freq1 = rescode.sat2freq(obs.sat, obs.code[0], nav);
        double freq2 = rescode.sat2freq(obs.sat, obs.code[1], nav);
        if (freq1 == 0.0 || freq2 == 0.0 || obs.L[0] == 0.0 || obs.L[1] == 0.0 || obs.P[0] == 0.0 || obs.P[1] == 0.0) {
            return 0.0;
        }
        return (obs.L[0] - obs.L[1]) * 2.99792458E8 / (freq1 - freq2) - (freq1 * obs.P[0] + freq2 * obs.P[1]) / (freq1 + freq2);
    }

    private static void detslp_gf(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        for (int i = 0; i < n && i < 96; ++i) {
            double g1 = ppPos.gfmeas(obs[i], nav);
            if (g1 == 0.0) continue;
            double g0 = rtk.ssat[obs[i].sat[0] - 1].gf[0];
            rtk.ssat[obs[i].sat[0] - 1].gf[0] = g1;
            if (g0 == 0.0 || !(Math.abs(g1 - g0) > rtk.opt.getThresslip())) continue;
            int j = 0;
            while (j < rtk.opt.getNf()) {
                int n2 = j++;
                rtk.ssat[obs[i].sat[0] - 1].slip[n2] = (byte)(rtk.ssat[obs[i].sat[0] - 1].slip[n2] | 1);
            }
        }
    }

    private static double gfmeas(obsd_t obs, nav_t nav) {
        double freq1 = rescode.sat2freq(obs.sat, obs.code[0], nav);
        double freq2 = rescode.sat2freq(obs.sat, obs.code[1], nav);
        if (freq1 == 0.0 || freq2 == 0.0 || obs.L[0] == 0.0 || obs.L[1] == 0.0) {
            return 0.0;
        }
        return (obs.L[0] / freq1 - obs.L[1] / freq2) * 2.99792458E8;
    }

    private static int ROUND(double v) {
        return (int)Math.floor(v + 0.5);
    }

    private static void uddcb_ppp(rtk_t rtk) {
        int i = ppPos.ID(rtk.opt);
        Log.trace((Object[])new Object[]{"uddcb_ppp:"});
        if (rtk.x[i] == 0.0) {
            udState.initx(rtk, 1.0E-6, VAR_DCB, i);
        }
    }

    public static void pppos(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        int i;
        prcopt_t opt = rtk.opt;
        double[] dr = new double[3];
        double[] std = new double[3];
        char[] str = new char[32];
        int[] svh = new int[96];
        int[] exc = new int[96];
        int stat = 5;
        util1.time2str(obs[0].time, str, 2);
        double[] rs = math.mat(6, n);
        double[] dts = math.mat(2, n);
        double[] var = math.mat(1, n);
        double[] azel = math.zeros(2, n);
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (int j = 0; j < opt.getNf(); ++j) {
                rtk.ssat[i].fix[j] = 0;
            }
        }
        ppPos.udstate_ppp(rtk, obs, n, nav);
        satposs.sat_poss(obs[0].time, obs, n, nav, rtk.opt.getSateph(), rs, dts, var, svh);
        if (rtk.opt.getPosopt()[3] != 0) {
            ppPos.testeclipse(obs, n, nav, rs);
        }
        if (opt.getTidecorr() != 0) {
            ppPos.tidedisp(util1.gpst2utc(obs[0].time), rtk.x, opt.getTidecorr() == 1 ? 1 : 7, nav.erp, opt.getOdisp()[0], dr);
        }
        int nv = n * rtk.opt.getNf() * 2 + nav_t.getMaxSat() + 3;
        double[] xp = math.mat(rtk.nx, 1);
        double[] Pp = math.zeros(rtk.nx, rtk.nx);
        double[] v = math.mat(nv, 1);
        double[] H = math.mat(rtk.nx, nv);
        double[] R = math.mat(nv, nv);
        for (i = 0; i < 8; ++i) {
            int info;
            math.matcpy(xp, rtk.x, rtk.nx, 1);
            math.matcpy(Pp, rtk.P, rtk.nx, rtk.nx);
            nv = ppPos.ppp_res(0, obs, n, rs, dts, var, svh, dr, exc, nav, xp, rtk, v, H, R, azel);
            if (nv == 0 || (info = rtkPos.filter(xp, Pp, H, v, R, rtk.nx, nv)) != 0) break;
            if (ppPos.ppp_res(i + 1, obs, n, rs, dts, var, svh, dr, exc, nav, xp, rtk, v, H, R, azel) == 0) continue;
            math.matcpy(rtk.x, xp, rtk.nx, 1);
            math.matcpy(rtk.P, Pp, rtk.nx, rtk.nx);
            stat = 6;
            break;
        }
        if (i >= 8) {
            // empty if block
        }
        if (stat == 6) {
            rtk.nfix = 0;
            ppPos.update_stat(rtk, obs, n, stat);
            if (stat == 1 && ppPos.test_hold_amb(rtk)) {
                math.matcpy(rtk.x, xp, rtk.nx, 1);
                math.matcpy(rtk.P, Pp, rtk.nx, rtk.nx);
                rtk.nfix = 0;
            }
        }
    }

    private static boolean test_hold_amb(rtk_t rtk) {
        boolean stat = false;
        if ((double)rtk.opt.getModear() != 3.0) {
            return false;
        }
        for (int i = 0; i < nav_t.getMaxSat(); ++i) {
            if (rtk.ssat[i].fix[0] != 2 && rtk.ssat[i].fix[1] != 2) continue;
            for (int j = 0; j < nav_t.getMaxSat(); ++j) {
                if (rtk.ssat[j].fix[0] != 2 && rtk.ssat[j].fix[1] != 2) continue;
                if (rtk.ambc[j].flags[i] == '\u0000' || rtk.ambc[i].flags[j] == '\u0000') {
                    stat = true;
                }
                rtk.ambc[i].flags[j] = '\u0001';
                rtk.ambc[j].flags[i] = '\u0001';
            }
        }
        if (stat) {
            rtk.nfix = 0;
            return false;
        }
        return ++rtk.nfix >= rtk.opt.getMinfix();
    }

    private static void update_stat(rtk_t rtk, obsd_t[] obs, int n, int stat) {
        int j;
        int i;
        prcopt_t opt = rtk.opt;
        rtk.sol.ns = 0;
        for (i = 0; i < n && i < 96; ++i) {
            for (j = 0; j < opt.getNf(); ++j) {
                if (rtk.ssat[obs[i].sat[0] - 1].vsat[j] == 0) continue;
                int n2 = j;
                rtk.ssat[obs[i].sat[0] - 1].lock[n2] = rtk.ssat[obs[i].sat[0] - 1].lock[n2] + 1;
                rtk.ssat[obs[i].sat[0] - 1].outc[j] = 0L;
                if (j != 0) continue;
                rtk.sol.ns = (byte)(rtk.sol.ns + 1);
            }
        }
        rtk.sol.stat = (byte)(rtk.sol.ns < 4 ? 0 : stat);
        if (rtk.sol.stat == 1) {
            for (i = 0; i < 3; ++i) {
                rtk.sol.rr[i] = rtk.xa[i];
                rtk.sol.qr[i] = (float)rtk.Pa[i + i * rtk.na];
            }
            rtk.sol.qr[3] = (float)rtk.Pa[1];
            rtk.sol.qr[4] = (float)rtk.Pa[1 + 2 * rtk.na];
            rtk.sol.qr[5] = (float)rtk.Pa[2];
        } else {
            for (i = 0; i < 3; ++i) {
                rtk.sol.rr[i] = rtk.x[i];
                rtk.sol.qr[i] = (float)rtk.P[i + i * rtk.nx];
            }
            rtk.sol.qr[3] = (float)rtk.P[1];
            rtk.sol.qr[4] = (float)rtk.P[2 + rtk.nx];
            rtk.sol.qr[5] = (float)rtk.P[2];
        }
        rtk.sol.dtr[0] = rtk.x[ppPos.IC(0, opt)];
        rtk.sol.dtr[1] = rtk.x[ppPos.IC(1, opt)] - rtk.x[ppPos.IC(0, opt)];
        for (i = 0; i < n && i < 96; ++i) {
            for (j = 0; j < opt.getNf(); ++j) {
                rtk.ssat[obs[i].sat[0] - 1].snr_rover[j] = obs[i].SNR[j];
            }
        }
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < opt.getNf(); ++j) {
                if ((rtk.ssat[i].slip[j] & 3) != 0) {
                    int n3 = j;
                    rtk.ssat[i].slipc[n3] = rtk.ssat[i].slipc[n3] + 1L;
                }
                if (rtk.ssat[i].fix[j] != 2 || stat == 1) continue;
                rtk.ssat[i].fix[j] = 1;
            }
        }
    }

    private static int ppp_res(int post, obsd_t[] obs, int n, double[] rs, double[] dts, double[] var_rs, int[] svh, double[] dr, int[] exc, nav_t nav, double[] x, rtk_t rtk, double[] v, double[] H, double[] R, double[] azel) {
        int j;
        int i;
        prcopt_t opt = rtk.opt;
        double C = 0.0;
        double[] rr = new double[3];
        double[] pos = new double[3];
        double[] e = new double[3];
        double[] dtdx = new double[3];
        double[] L = new double[3];
        double[] P = new double[3];
        double[] Lc = new double[1];
        double[] Pc = new double[1];
        double[] var = new double[192];
        double[] dtrp = new double[]{0.0};
        double[] dion = new double[]{0.0};
        double[] vart = new double[]{0.0};
        double[] vari = new double[]{0.0};
        double[] dantr = new double[3];
        double[] dants = new double[3];
        double[] ve = new double[576];
        double vmax = 0.0;
        char[] str = new char[32];
        int ne = 0;
        int[] obsi = new int[576];
        int[] frqi = new int[576];
        int nv = 0;
        int nx = rtk.nx;
        int stat = 1;
        int[] sat = new int[1];
        util1.time2str(obs[0].time, str, 2);
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < opt.getNf(); ++j) {
                rtk.ssat[i].vsat[j] = 0;
            }
        }
        for (i = 0; i < 3; ++i) {
            rr[i] = x[i] + dr[i];
        }
        rescode.ecef2pos(rr, pos);
        for (i = 0; i < n && i < 96; ++i) {
            sat[0] = obs[i].sat[0];
            double r = rescode.geodist(Arrays.copyOfRange(rs, i * 6, rs.length), rr, e);
            if (r <= 0.0 || rescode.satazel(pos, e, azel, i * 2) < opt.getElmin()) {
                exc[i] = 1;
                continue;
            }
            int sys = satSys.sat_sys(sat[0], null);
            if (sys == 0 || rtk.ssat[sat[0] - 1].vs == 0 || rescode.satexclude(obs[i].sat, var_rs[i], svh[i], opt) != 0 || exc[i] != 0) {
                exc[i] = 1;
                continue;
            }
            if (!ppPos.model_trop(obs[i].time, pos, Arrays.copyOfRange(azel, i * 2, azel.length), opt, x, dtdx, nav, dtrp, vart) || !ppPos.model_iono(obs[i].time, pos, Arrays.copyOfRange(azel, i * 2, azel.length), opt, sat, x, nav, dion, vari)) continue;
            if (opt.getPosopt()[0] != 0) {
                ppPos.satantpcv(Arrays.copyOfRange(rs, i * 6, rs.length), rr, nav.pcvs[sat[0] - 1], dants);
            }
            double[] phw_ = new double[]{rtk.ssat[sat[0] - 1].phw};
            if (!ppPos.model_phw(rtk.sol.time, sat, nav.pcvs[sat[0] - 1].type, opt.getPosopt()[2] != 0 ? 2 : 0, Arrays.copyOfRange(rs, i * 6, rs.length), rr, phw_)) continue;
            ppPos.corr_meas(obs[i], nav, Arrays.copyOfRange(azel, i * 2, azel.length), rtk.opt, dantr, dants, phw_, L, P, Lc, Pc);
            rtk.ssat[sat[0] - 1].phw = phw_[0];
            for (j = 0; j < 2 * rtkPos.NF(opt); ++j) {
                int k;
                double y;
                double bias = 0.0;
                double dcb = 0.0;
                if (opt.ionoopt == 3) {
                    y = j % 2 == 0 ? Lc[0] : Pc[0];
                    if (y == 0.0) {
                        continue;
                    }
                } else {
                    double freq;
                    y = j % 2 == 0 ? L[j / 2] : P[j / 2];
                    if (y == 0.0 || (freq = rescode.sat2freq(sat, obs[i].code[j / 2], nav)) == 0.0) continue;
                    C = util2.SQR(1.57542E9 / freq) * rtkPos.ionmapf(pos, Arrays.copyOfRange(azel, i * 2, azel.length)) * (j % 2 == 0 ? -1.0 : 1.0);
                }
                for (k = 0; k < nx; ++k) {
                    H[k + nx * nv] = k < 3 ? -e[k] : 0.0;
                }
                k = switch (sys) {
                    case 4 -> 1;
                    case 8 -> 2;
                    case 32 -> 3;
                    case 64 -> 4;
                    default -> 0;
                };
                double cdtr = x[ppPos.IC(k, opt)];
                H[ppPos.IC((int)k, (prcopt_t)opt) + nx * nv] = 1.0;
                if (opt.tropopt == 3 || opt.tropopt == 4) {
                    for (k = 0; k < (opt.tropopt >= 4 ? 3 : 1); ++k) {
                        H[ppPos.IT((prcopt_t)opt) + k + nx * nv] = dtdx[k];
                    }
                }
                if (opt.ionoopt == 4) {
                    if (rtk.x[ppPos.II(sat[0], opt)] == 0.0) continue;
                    H[ppPos.II((int)sat[0], (prcopt_t)opt) + nx * nv] = C;
                }
                if (j / 2 == 2 && j % 2 == 1) {
                    dcb += rtk.x[ppPos.ID(opt)];
                    H[ppPos.ID((prcopt_t)opt) + nx * nv] = 1.0;
                }
                if (j % 2 == 0) {
                    bias = x[ppPos.IB(sat[0], j / 2, opt)];
                    if (bias == 0.0) continue;
                    H[ppPos.IB((int)sat[0], (int)(j / 2), (prcopt_t)opt) + nx * nv] = 1.0;
                }
                v[nv] = y - (r + cdtr - 2.99792458E8 * dts[i * 2] + dtrp[0] + C * dion[0] + dcb + bias);
                if (j % 2 == 0) {
                    rtk.ssat[sat[0] - 1].resc[j / 2] = v[nv];
                } else {
                    rtk.ssat[sat[0] - 1].resp[j / 2] = v[nv];
                }
                var[nv] = ppPos.varerr(obs[i].sat[0], sys, azel[1 + i * 2], obs[i].SNR[j / 2], j / 2, j % 2, opt) + vart[0] + util2.SQR(C) * vari[0] + var_rs[i];
                if (sys == 4 && j % 2 == 1) {
                    int n2 = nv;
                    var[n2] = var[n2] + rescode.VAR_GLO_IFB;
                }
                if (post == 0 && opt.getMaxinnoPhase() > 0.0 && Math.abs(v[nv]) > opt.getMaxinnoPhase()) {
                    exc[i] = 1;
                    int n3 = j % 2;
                    rtk.ssat[sat[0] - 1].rejc[n3] = rtk.ssat[sat[0] - 1].rejc[n3] + 1L;
                    continue;
                }
                if (post != 0 && Math.abs(v[nv]) > Math.sqrt(var[nv]) * 4.0) {
                    obsi[ne] = i;
                    frqi[ne] = j;
                    ve[ne] = v[nv];
                    ++ne;
                }
                if (j % 2 == 0) {
                    rtk.ssat[sat[0] - 1].vsat[j / 2] = 1;
                }
                ++nv;
            }
        }
        if (post != 0 && ne > 0) {
            vmax = ve[0];
            int maxobs = obsi[0];
            int maxfrq = frqi[0];
            int rej = 0;
            for (j = 1; j < ne; ++j) {
                if (Math.abs(vmax) >= Math.abs(ve[j])) continue;
                vmax = ve[j];
                maxobs = obsi[j];
                maxfrq = frqi[j];
                rej = j;
            }
            sat = obs[maxobs].sat;
            exc[maxobs] = 1;
            int n4 = maxfrq % 2;
            rtk.ssat[sat[0] - 1].rejc[n4] = rtk.ssat[sat[0] - 1].rejc[n4] + 1L;
            stat = 0;
            ve[rej] = 0.0;
        }
        for (i = 0; i < nv; ++i) {
            for (j = 0; j < nv; ++j) {
                R[i + j * nv] = i == j ? var[i] : 0.0;
            }
        }
        return post != 0 ? stat : nv;
    }

    private static double varerr(int sat, int sys, double el, double snr, int idx, int type, prcopt_t opt) {
        double fact = 1.0;
        double sinel = Math.sin(el);
        if (type == 1) {
            fact *= opt.getEratio()[idx == 0 ? 0 : 1];
        }
        fact *= sys == 4 ? rescode.EFACT_GLO : (sys == 2 ? rescode.EFACT_SBS : rescode.EFACT_GPS);
        if ((sys == 1 || sys == 16) && idx == 2) {
            fact *= rescode.EFACT_GPS_L5;
        }
        if (opt.ionoopt == 3) {
            fact *= 3.0;
        }
        double elVar;
        if (opt.getVarmodel() == 1 && snr > 0.0) {
            /* SNR-based stochastic model */
            elVar = util2.SQR(fact * opt.getErr()[3]) * Math.pow(10, (50.0 - snr) / 10.0);
        } else {
            /* height-angle model (default) */
            elVar = util2.SQR(fact * opt.getErr()[3] / sinel);
        }
        return util2.SQR(fact * opt.getErr()[2]) + elVar;
    }

    private static boolean model_phw(gtime_t time, int[] sat, char[] type, int opt, double[] rs, double[] rr, double[] phw) {
        int i;
        double[] exs = new double[3];
        double[] eys = new double[3];
        double[] ek = new double[3];
        double[] exr = new double[3];
        double[] eyr = new double[3];
        double[] eks = new double[3];
        double[] ekr = new double[3];
        double[] E = new double[9];
        double[] dr = new double[3];
        double[] ds = new double[3];
        double[] drs = new double[3];
        double[] r = new double[3];
        double[] pos = new double[3];
        if (opt <= 0) {
            return true;
        }
        if (!ppPos.sat_yaw(time, sat, type, opt, rs, exs, eys)) {
            return false;
        }
        for (i = 0; i < 3; ++i) {
            r[i] = rr[i] - rs[i];
        }
        if (!ppPos.normv3(r, ek)) {
            return false;
        }
        rescode.ecef2pos(rr, pos);
        rescode.xyz2enu(pos, E);
        exr[0] = E[1];
        exr[1] = E[4];
        exr[2] = E[7];
        eyr[0] = -E[0];
        eyr[1] = -E[3];
        eyr[2] = -E[6];
        ppPos.cross3(ek, eys, eks);
        ppPos.cross3(ek, eyr, ekr);
        for (i = 0; i < 3; ++i) {
            ds[i] = exs[i] - ek[i] * math.dot(ek, exs, 3) - eks[i];
            dr[i] = exr[i] - ek[i] * math.dot(ek, exr, 3) + ekr[i];
        }
        double cosp = math.dot(ds, dr, 3) / math.norm(ds, 3) / math.norm(dr, 3);
        if (cosp < -1.0) {
            cosp = -1.0;
        } else if (cosp > 1.0) {
            cosp = 1.0;
        }
        double ph = Math.acos(cosp) / 2.0 / Math.PI;
        ppPos.cross3(ds, dr, drs);
        if (math.dot(ek, drs, 3) < 0.0) {
            ph = -ph;
        }
        phw[0] = ph + Math.floor(phw[0] - ph + 0.5);
        return true;
    }

    private static boolean sat_yaw(gtime_t time, int[] sat, char[] type, int opt, double[] rs, double[] exs, double[] eys) {
        double[] rsun = new double[3];
        double[] ri = new double[6];
        double[] es = new double[3];
        double[] esun = new double[3];
        double[] n = new double[3];
        double[] p = new double[3];
        double[] en = new double[3];
        double[] ep = new double[3];
        double[] ex = new double[3];
        double[] yaw = new double[1];
        double[] erpv = new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
        ppPos.sunmoonpos(util1.gpst2utc(time), erpv, rsun, null, null);
        math.matcpy(ri, rs, 6, 1);
        ri[3] = ri[3] - 7.2921151467E-5 * ri[1];
        ri[4] = ri[4] + 7.2921151467E-5 * ri[0];
        ppPos.cross3(ri, Arrays.copyOfRange(rs, 3, rs.length), n);
        ppPos.cross3(rsun, n, p);
        if (!(ppPos.normv3(rs, es) && ppPos.normv3(rsun, esun) && ppPos.normv3(n, en) && ppPos.normv3(p, ep))) {
            return false;
        }
        double beta = 1.5707963267948966 - Math.acos(math.dot(esun, en, 3));
        double E = Math.acos(math.dot(es, ep, 3));
        double mu = 1.5707963267948966 + (math.dot(es, esun, 3) <= 0.0 ? -E : E);
        if (mu < -1.5707963267948966) {
            mu += Math.PI * 2;
        } else if (mu >= 1.5707963267948966) {
            mu -= Math.PI * 2;
        }
        if (!ppPos.yaw_angle(sat, type, opt, beta, mu, yaw)) {
            return false;
        }
        ppPos.cross3(en, es, ex);
        double cosy = Math.cos(yaw[0]);
        double siny = Math.sin(yaw[0]);
        for (int i = 0; i < 3; ++i) {
            exs[i] = -siny * en[i] + cosy * ex[i];
            eys[i] = -cosy * en[i] - siny * ex[i];
        }
        return true;
    }

    private static boolean yaw_angle(int[] sat, char[] type, int opt, double beta, double mu, double[] yaw) {
        yaw[0] = ppPos.yaw_nominal(beta, mu);
        return true;
    }

    private static double yaw_nominal(double beta, double mu) {
        if (Math.abs(beta) < 1.0E-12 && Math.abs(mu) < 1.0E-12) {
            return Math.PI;
        }
        return Math.atan2(-Math.tan(beta), Math.sin(mu)) + Math.PI;
    }

    public static void cross3(double[] a, double[] b, double[] c) {
        c[0] = a[1] * b[2] - a[2] * b[1];
        c[1] = a[2] * b[0] - a[0] * b[2];
        c[2] = a[0] * b[1] - a[1] * b[0];
    }

    private static void satantpcv(double[] rs, double[] rr, pcv_t pcv, double[] dant) {
        double[] ru = new double[3];
        double[] rz = new double[3];
        double[] eu = new double[3];
        double[] ez = new double[3];
        for (int i = 0; i < 3; ++i) {
            ru[i] = rr[i] - rs[i];
            rz[i] = -rs[i];
        }
        if (!ppPos.normv3(ru, eu) || !ppPos.normv3(rz, ez)) {
            return;
        }
        double cosa = math.dot(eu, ez, 3);
        cosa = cosa < -1.0 ? -1.0 : Math.min(cosa, 1.0);
        double nadir = Math.acos(cosa);
        ppPos.antmodel_s(pcv, nadir, dant);
    }

    private static void antmodel_s(pcv_t pcv, double nadir, double[] dant) {
        for (int i = 0; i < 3; ++i) {
            dant[i] = rtkPos.interpvar(nadir * 57.29577951308232 * 5.0, pcv.var[i]);
        }
    }

    public static boolean normv3(double[] a, double[] b) {
        double d;
        double r = math.norm(a, 3);
        if (r <= 0.0) {
            return false;
        }
        b[0] = a[0] / r;
        b[1] = a[1] / r;
        b[2] = a[2] / r;
        return true;
    }

    private static boolean model_iono(gtime_t time, double[] pos, double[] azel, prcopt_t opt, int[] sat, double[] x, nav_t nav, double[] dion, double[] var) {
        if (opt.ionoopt == 2) {
            return ppPos.sbsioncorr(time, nav, pos, azel, dion, var);
        }
        if (opt.ionoopt == 5) {
            return ppPos.iontec(time, nav, pos, azel, 1, dion, var);
        }
        if (opt.ionoopt == 1) {
            dion[0] = rescode.ionmodel(time, nav.ion_gps, pos, azel);
            var[0] = util2.SQR(dion[0] * 0.5);
            return true;
        }
        if (opt.ionoopt == 4) {
            dion[0] = x[ppPos.II(sat[0], opt)];
            var[0] = 0.0;
            return true;
        }
        if (opt.ionoopt == 3) {
            var[0] = 0.0;
            dion[0] = 0.0;
            return true;
        }
        return false;
    }

    private static boolean iontec(gtime_t time, nav_t nav, double[] pos, double[] azel, int opt, double[] delay, double[] var) {
        int i;
        double[] dels = new double[2];
        double[] vars = new double[2];
        int[] stat = new int[2];
        if (azel[1] < 0.08726646259971647 || pos[2] < -1000.0) {
            delay[0] = 0.0;
            var[0] = rescode.VAR_NOTEC;
            return true;
        }
        for (i = 0; i < nav.getNt() && !(util1.timediff(nav.getTec()[i].getTime(), time) > 0.0); ++i) {
        }
        if (i == 0 || i >= nav.getNt()) {
            return false;
        }
        double tt = util1.timediff(nav.getTec()[i].getTime(), nav.getTec()[i - 1].getTime());
        if (tt == 0.0) {
            return false;
        }
        stat[0] = ppPos.iondelay(time, nav.getTec()[i - 1], pos, azel, opt, dels, vars, 0);
        stat[1] = ppPos.iondelay(time, nav.getTec()[i], pos, azel, opt, dels, vars, 1);
        if (stat[0] == 0 && stat[1] == 0) {
            return false;
        }
        if (stat[0] != 0 && stat[1] != 0) {
            double a = util1.timediff(time, nav.getTec()[i - 1].getTime()) / tt;
            delay[0] = dels[0] * (1.0 - a) + dels[1] * a;
            var[0] = vars[0] * (1.0 - a) + vars[1] * a;
        } else if (stat[0] != 0) {
            delay[0] = dels[0];
            var[0] = vars[0];
        } else {
            delay[0] = dels[1];
            var[0] = vars[1];
        }
        return true;
    }

    private static int iondelay(gtime_t time, tec_t tec, double[] pos, double[] azel, int opt, double[] delay, double[] var, int k) {
        double fact = 0.16237244751199473;
        double[] posp = new double[]{0.0, 0.0, 0.0};
        double[] vtec = new double[1];
        double[] rms = new double[1];
        var[k] = 0.0;
        delay[k] = 0.0;
        for (int i = 0; i < tec.getNdata()[2]; ++i) {
            double hion = tec.getHgts()[0] + tec.getHgts()[2] * (double)i;
            double fs = ppPos.ionppp(pos, azel, tec.getRb(), hion, posp);
            if ((opt & 2) != 0) {
                double rp = tec.getRb() / (tec.getRb() + hion) * Math.sin(0.9782 * (1.5707963267948966 - azel[1]));
                fs = 1.0 / Math.sqrt(1.0 - rp * rp);
            }
            if ((opt & 1) != 0) {
                posp[1] = posp[1] + Math.PI * 2 * util1.timediff(time, tec.getTime()) / 86400.0;
            }
            if (!ppPos.interptec(tec, i, posp, vtec, rms)) {
                return 0;
            }
            int n = k;
            delay[n] = delay[n] + 0.16237244751199473 * fs * vtec[0];
            int n2 = k;
            var[n2] = var[n2] + 0.026364811711035482 * fs * fs * rms[0] * rms[0];
        }
        return 1;
    }

    private static boolean interptec(tec_t tec, int k, double[] posp, double[] value, double[] rms) {
        int n;
        double[] d = new double[]{0.0, 0.0, 0.0, 0.0};
        double[] r = new double[]{0.0, 0.0, 0.0, 0.0};
        rms[0] = 0.0;
        value[0] = 0.0;
        if (tec.getLats()[2] == 0.0 || tec.getLons()[2] == 0.0) {
            return false;
        }
        double dlat = posp[0] * 57.29577951308232 - tec.getLats()[0];
        double dlon = posp[1] * 57.29577951308232 - tec.getLons()[0];
        dlon = tec.getLons()[2] > 0.0 ? (dlon -= Math.floor(dlon / 360.0) * 360.0) : (dlon += Math.floor(-dlon / 360.0) * 360.0);
        double a = dlat / tec.getLats()[2];
        double b = dlon / tec.getLons()[2];
        int i = (int)Math.floor(a);
        a -= (double)i;
        int j = (int)Math.floor(b);
        b -= (double)j;
        for (n = 0; n < 4; ++n) {
            int index = ppPos.dataindex(i + n % 2, j + (n < 2 ? 0 : 1), k, tec.getNdata());
            if (index < 0) continue;
            d[n] = tec.getData()[index];
            r[n] = tec.getRms()[index];
        }
        if (d[0] > 0.0 && d[1] > 0.0 && d[2] > 0.0 && d[3] > 0.0) {
            value[0] = (1.0 - a) * (1.0 - b) * d[0] + a * (1.0 - b) * d[1] + (1.0 - a) * b * d[2] + a * b * d[3];
            rms[0] = (1.0 - a) * (1.0 - b) * r[0] + a * (1.0 - b) * r[1] + (1.0 - a) * b * r[2] + a * b * r[3];
        } else if (a <= 0.5 && b <= 0.5 && d[0] > 0.0) {
            value[0] = d[0];
            rms[0] = r[0];
        } else if (a > 0.5 && b <= 0.5 && d[1] > 0.0) {
            value[0] = d[1];
            rms[0] = r[1];
        } else if (a <= 0.5 && b > 0.5 && d[2] > 0.0) {
            value[0] = d[2];
            rms[0] = r[2];
        } else if (a > 0.5 && b > 0.5 && d[3] > 0.0) {
            value[0] = d[3];
            rms[0] = r[3];
        } else {
            i = 0;
            for (n = 0; n < 4; ++n) {
                if (!(d[n] > 0.0)) continue;
                ++i;
                value[0] = value[0] + d[n];
                rms[0] = rms[0] + r[n];
            }
            if (i == 0) {
                return false;
            }
            value[0] = value[0] / (double)i;
            rms[0] = rms[0] / (double)i;
        }
        return true;
    }

    private static int dataindex(int i, int j, int k, int[] ndata) {
        if (i < 0 || ndata[0] <= i || j < 0 || ndata[1] <= j || k < 0 || ndata[2] <= k) {
            return -1;
        }
        return i + ndata[0] * (j + ndata[1] * k);
    }

    private static boolean sbsioncorr(gtime_t time, nav_t nav, double[] pos, double[] azel, double[] delay, double[] var) {
        double re = 6378.1363;
        double hion = 350.0;
        boolean err = false;
        double[] posp = new double[2];
        double[] x = new double[]{0.0};
        double[] y = new double[]{0.0};
        double[] w = new double[4];
        sbsigp_t[] igp = new sbsigp_t[4];
        var[0] = 0.0;
        delay[0] = 0.0;
        if (pos[2] < -100.0 || azel[1] <= 0.0) {
            return true;
        }
        double fp = ppPos.ionppp(pos, azel, 6378.1363, 350.0, posp);
        ppPos.searchigp(time, posp, nav.getSbsion(), igp, x, y);
        if (igp[0] != null && igp[1] != null && igp[2] != null && igp[3] != null) {
            w[0] = (1.0 - x[0]) * (1.0 - y[0]);
            w[1] = (1.0 - x[0]) * y[0];
            w[2] = x[0] * (1.0 - y[0]);
            w[3] = x[0] * y[0];
        } else if (igp[0] != null && igp[1] != null && igp[2] != null) {
            double d;
            w[1] = y[0];
            w[2] = x[0];
            w[0] = 1.0 - w[1] - w[2];
            if (w[0] < 0.0) {
                err = true;
            }
        } else if (igp[0] != null && igp[2] != null && igp[3] != null) {
            double d;
            w[0] = 1.0 - x[0];
            w[3] = y[0];
            w[2] = 1.0 - w[0] - w[3];
            if (w[2] < 0.0) {
                err = true;
            }
        } else if (igp[0] != null && igp[1] != null && igp[3] != null) {
            double d;
            w[0] = 1.0 - y[0];
            w[3] = x[0];
            w[1] = 1.0 - w[0] - w[3];
            if (w[1] < 0.0) {
                err = true;
            }
        } else if (igp[1] != null && igp[2] != null && igp[3] != null) {
            double d;
            w[1] = 1.0 - x[0];
            w[2] = 1.0 - y[0];
            w[3] = 1.0 - w[1] - w[2];
            if (w[3] < 0.0) {
                err = true;
            }
        } else {
            err = true;
        }
        if (err) {
            return false;
        }
        for (int i = 0; i < 4; ++i) {
            if (igp[i] == null) continue;
            double t = util1.timediff(time, igp[i].getT0());
            delay[0] = delay[0] + w[i] * (double)igp[i].getDelay();
            var[0] = var[0] + w[i] * ppPos.varicorr(igp[i].getGive()) * 9.0E-8 * Math.abs(t);
        }
        delay[0] = delay[0] * fp;
        var[0] = var[0] * (fp * fp);
        return true;
    }

    private static double varicorr(int give) {
        double[] var = new double[]{0.0084, 0.0333, 0.0749, 0.1331, 0.2079, 0.2994, 0.4075, 0.5322, 0.6735, 0.8315, 1.1974, 1.8709, 3.326, 20.787, 187.0826};
        return 0 < give && give <= 15 ? var[give - 1] : 0.0;
    }

    private static void searchigp(gtime_t time, double[] pos, sbsion_t[] ion, sbsigp_t[] igp, double[] x, double[] y) {
        int i;
        int[] latp = new int[2];
        int[] lonp = new int[4];
        double lat = pos[0] * 57.29577951308232;
        double lon = pos[1] * 57.29577951308232;
        int j = 0;
        if (lon >= 180.0) {
            lon -= 360.0;
        }
        if (-55.0 <= lat && lat < 55.0) {
            latp[0] = (int)Math.floor(lat / 5.0) * 5;
            latp[1] = latp[0] + 5;
            lonp[0] = lonp[1] = (int)Math.floor(lon / 5.0) * 5;
            lonp[2] = lonp[3] = lonp[0] + 5;
            x[0] = (lon - (double)lonp[0]) / 5.0;
            y[0] = (lat - (double)latp[0]) / 5.0;
        } else {
            latp[0] = (int)Math.floor((lat - 5.0) / 10.0) * 10 + 5;
            latp[1] = latp[0] + 10;
            lonp[0] = lonp[1] = (int)Math.floor(lon / 10.0) * 10;
            lonp[2] = lonp[3] = lonp[0] + 10;
            x[0] = (lon - (double)lonp[0]) / 10.0;
            y[0] = (lat - (double)latp[0]) / 10.0;
            if (75.0 <= lat && lat < 85.0) {
                lonp[1] = (int)Math.floor(lon / 90.0) * 90;
                lonp[3] = lonp[1] + 90;
            } else if (-85.0 <= lat && lat < -75.0) {
                lonp[0] = (int)Math.floor((lon - 50.0) / 90.0) * 90 + 40;
                lonp[2] = lonp[0] + 90;
            } else if (lat >= 85.0) {
                for (i = 0; i < 4; ++i) {
                    lonp[i] = (int)Math.floor(lon / 90.0) * 90;
                }
            } else if (lat < -85.0) {
                for (i = 0; i < 4; ++i) {
                    lonp[i] = (int)Math.floor((lon - 50.0) / 90.0) * 90 + 40;
                }
            }
        }
        for (i = 0; i < 4; ++i) {
            if (lonp[i] != 180) continue;
            lonp[i] = -180;
        }
        for (i = 0; i <= 10; ++i) {
            sbsigp_t[] p = ion[i].getIgp();
            for (j = 0; j < ion[i].getNigp(); ++j) {
                if (p[j].getT0().time == 0.0) continue;
                if (p[j].getLat() == latp[0] && p[j].getLon() == lonp[0] && p[j].getGive() > 0) {
                    igp[0] = p[j];
                } else if (p[j].getLat() == latp[1] && p[j].getLon() == lonp[1] && p[j].getGive() > 0) {
                    igp[1] = p[j];
                } else if (p[j].getLat() == latp[0] && p[j].getLon() == lonp[2] && p[j].getGive() > 0) {
                    igp[2] = p[j];
                } else if (p[j].getLat() == latp[1] && p[j].getLon() == lonp[3] && p[j].getGive() > 0) {
                    igp[3] = p[j];
                }
                if (igp[0] == null || igp[1] == null || igp[2] == null || igp[3] == null) continue;
                return;
            }
        }
    }

    private static double ionppp(double[] pos, double[] azel, double re, double hion, double[] posp) {
        double rp = re / (re + hion) * Math.cos(azel[1]);
        double ap = 1.5707963267948966 - azel[1] - Math.asin(rp);
        double sinap = Math.sin(ap);
        double tanap = Math.tan(ap);
        double cosaz = Math.cos(azel[0]);
        posp[0] = Math.asin(Math.sin(pos[0]) * Math.cos(ap) + Math.cos(pos[0]) * sinap * cosaz);
        posp[1] = pos[0] > 1.2217304763960306 && tanap * cosaz > Math.tan(1.5707963267948966 - pos[0]) || pos[0] < -1.2217304763960306 && -tanap * cosaz > Math.tan(1.5707963267948966 + pos[0]) ? pos[1] + Math.PI - Math.asin(sinap * Math.sin(azel[0]) / Math.cos(posp[0])) : pos[1] + Math.asin(sinap * Math.sin(azel[0]) / Math.cos(posp[0]));
        return 1.0 / Math.sqrt(1.0 - rp * rp);
    }

    private static boolean model_trop(gtime_t time, double[] pos, double[] azel, prcopt_t opt, double[] x, double[] dtdx, nav_t nav, double[] dtrp, double[] var) {
        double[] trp = new double[3];
        if (opt.tropopt == 1) {
            dtrp[0] = rescode.tropmodel(time, pos, azel, 0.7);
            var[0] = util2.SQR(0.3);
            return true;
        }
        if (opt.tropopt == 2) {
            dtrp[0] = rescode.sbstropcorr(time, pos, azel, var);
            return true;
        }
        if (opt.tropopt == 3 || opt.tropopt == 4) {
            math.matcpy(trp, Arrays.copyOfRange(x, ppPos.IT(opt), x.length), opt.tropopt == 3 ? 1 : 3, 1);
            dtrp[0] = ppPos.trop_model_prec(time, pos, azel, trp, dtdx, var);
            return true;
        }
        return false;
    }

    private static double trop_model_prec(gtime_t time, double[] pos, double[] azel, double[] x, double[] dtdx, double[] var) {
        double[] zazel = new double[]{0.0, 1.5707963267948966};
        double[] m_w = new double[1];
        double zhd = rescode.tropmodel(time, pos, zazel, 0.0);
        double m_h = rescode.tropmapf(time, pos, azel, m_w);
        if (azel[1] > 0.0) {
            double cotz = 1.0 / Math.tan(azel[1]);
            double grad_n = m_w[0] * cotz * Math.cos(azel[0]);
            double grad_e = m_w[0] * cotz * Math.sin(azel[0]);
            m_w[0] = m_w[0] + (grad_n * x[1] + grad_e * x[2]);
            dtdx[1] = grad_n * (x[0] - zhd);
            dtdx[2] = grad_e * (x[0] - zhd);
        }
        dtdx[0] = m_w[0];
        var[0] = util2.SQR(0.01);
        return m_h * zhd + m_w[0] * (x[0] - zhd);
    }

    private static void tidedisp(gtime_t tutc, double[] rr, int opt, erp_t erp, double[] odisp, double[] dr) {
        int i;
        double[] pos = new double[2];
        double[] E = new double[9];
        double[] drt = new double[3];
        double[] denu = new double[3];
        double[] rs = new double[3];
        double[] rm = new double[3];
        double[] gmst = new double[1];
        double[] erpv = new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
        if (erp != null) {
            ppPos.geterp(erp, util1.utc2gpst(tutc), erpv);
        }
        gtime_t tut = util1.timeadd(tutc, erpv[2]);
        dr[2] = 0.0;
        dr[1] = 0.0;
        dr[0] = 0.0;
        if (math.norm(rr, 3) <= 0.0) {
            return;
        }
        pos[0] = Math.asin(rr[2] / math.norm(rr, 3));
        pos[1] = Math.atan2(rr[1], rr[0]);
        rescode.xyz2enu(pos, E);
        if ((opt & 1) != 0) {
            ppPos.sunmoonpos(tutc, erpv, rs, rm, gmst);
            ppPos.tide_solid(rs, rm, pos, E, gmst, opt, drt);
            for (i = 0; i < 3; ++i) {
                int n = i;
                dr[n] = dr[n] + drt[i];
            }
        }
        if ((opt & 2) != 0 && odisp != null) {
            ppPos.tide_oload(tut, odisp, denu);
            math.matmul("TN", 3, 1, 3, 1.0, E, denu, 0.0, drt);
            for (i = 0; i < 3; ++i) {
                int n = i;
                dr[n] = dr[n] + drt[i];
            }
        }
        if ((opt & 4) != 0 && erp != null) {
            ppPos.tide_pole(tut, pos, erpv, denu);
            math.matmul("TN", 3, 1, 3, 1.0, E, denu, 0.0, drt);
            for (i = 0; i < 3; ++i) {
                int n = i;
                dr[n] = dr[n] + drt[i];
            }
        }
    }

    private static void tide_pole(gtime_t tut, double[] pos, double[] erpv, double[] denu) {
        double[] xp_bar = new double[1];
        double[] yp_bar = new double[1];
        ppPos.iers_mean_pole(tut, xp_bar, yp_bar);
        double m1 = erpv[0] / 4.84813681109536E-6 - xp_bar[0] * 0.001;
        double m2 = -erpv[1] / 4.84813681109536E-6 + yp_bar[0] * 0.001;
        double cosl = Math.cos(pos[1]);
        double sinl = Math.sin(pos[1]);
        denu[0] = 0.009 * Math.sin(pos[0]) * (m1 * sinl - m2 * cosl);
        denu[1] = -0.009 * Math.cos(2.0 * pos[0]) * (m1 * cosl + m2 * sinl);
        denu[2] = -0.033 * Math.sin(2.0 * pos[0]) * (m1 * cosl + m2 * sinl);
    }

    private static void iers_mean_pole(gtime_t tut, double[] xp_bar, double[] yp_bar) {
        double[] dArray = new double[]{2000.0, 1.0, 1.0, 0.0, 0.0, 0.0};
        double[] ep2000 = dArray;
        double y = util1.timediff(tut, util1.epoch2time(ep2000)) / 86400.0 / 365.25;
        if (y < 10.001368925393566) {
            double y2 = y * y;
            double y3 = y2 * y;
            xp_bar[0] = 55.974 + 1.8243 * y + 0.18413 * y2 + 0.007024 * y3;
            yp_bar[0] = 346.346 + 1.7896 * y - 0.10729 * y2 - 9.08E-4 * y3;
        } else {
            xp_bar[0] = 23.513 + 7.6141 * y;
            yp_bar[0] = 358.891 - 0.6287 * y;
        }
    }

    private static void tide_oload(gtime_t tut, double[] odisp, double[] denu) {
        double[][] args = new double[][]{{1.40519E-4, 2.0, -2.0, 0.0, 0.0}, {1.45444E-4, 0.0, 0.0, 0.0, 0.0}, {1.3788E-4, 2.0, -3.0, 1.0, 0.0}, {1.45842E-4, 2.0, 0.0, 0.0, 0.0}, {7.2921E-5, 1.0, 0.0, 0.0, 0.25}, {6.7598E-5, 1.0, -2.0, 0.0, -0.25}, {7.2523E-5, -1.0, 0.0, 0.0, -0.25}, {6.4959E-5, 1.0, -3.0, 1.0, -0.25}, {5.3234E-6, 0.0, 2.0, 0.0, 0.0}, {2.6392E-6, 0.0, 1.0, -1.0, 0.0}, {3.982E-7, 2.0, 0.0, 0.0, 0.0}};
        double[] ep1975 = new double[]{1975.0, 1.0, 1.0, 0.0, 0.0, 0.0};
        double[] ep = new double[6];
        double[] a = new double[5];
        double[] dp = new double[3];
        util1.time2epoch(tut, ep);
        double fday = ep[3] * 3600.0 + ep[4] * 60.0 + ep[5];
        ep[5] = 0.0;
        ep[4] = 0.0;
        ep[3] = 0.0;
        double days = util1.timediff(util1.epoch2time(ep), util1.epoch2time(ep1975)) / 86400.0 + 1.0;
        double t = (27392.500528 + 1.000000035 * days) / 36525.0;
        double t2 = t * t;
        double t3 = t2 * t;
        a[0] = fday;
        a[1] = (279.69668 + 36000.768930485 * t + 3.03E-4 * t2) * (Math.PI / 180);
        a[2] = (270.434358 + 481267.88314137 * t - 0.001133 * t2 + 1.9E-6 * t3) * (Math.PI / 180);
        a[3] = (334.329653 + 4069.0340329577 * t - 0.010325 * t2 - 1.2E-5 * t3) * (Math.PI / 180);
        a[4] = Math.PI * 2;
        for (int i = 0; i < 11; ++i) {
            int j;
            double ang = 0.0;
            for (j = 0; j < 5; ++j) {
                ang += a[j] * args[i][j];
            }
            for (j = 0; j < 3; ++j) {
                int n = j;
                dp[n] = dp[n] + odisp[j + i * 6] * Math.cos(ang - odisp[j + 3 + i * 6] * (Math.PI / 180));
            }
        }
        denu[0] = -dp[1];
        denu[1] = -dp[2];
        denu[2] = dp[0];
    }

    private static int geterp(erp_t erp, gtime_t time, double[] erpv) {
        double[] ep = new double[]{2000.0, 1.0, 1.0, 12.0, 0.0, 0.0};
        if (erp.n <= 0) {
            return 0;
        }
        double mjd = 51544.5 + util1.timediff(util1.gpst2utc(time), util1.epoch2time(ep)) / 86400.0;
        if (mjd <= erp.data[0].getMjd()) {
            double day = mjd - erp.data[0].getMjd();
            erpv[0] = erp.data[0].getXp() + erp.data[0].getXpr() * day;
            erpv[1] = erp.data[0].getYp() + erp.data[0].getYpr() * day;
            erpv[2] = erp.data[0].getUt1_utc() - erp.data[0].getLod() * day;
            erpv[3] = erp.data[0].getLod();
            return 1;
        }
        if (mjd >= erp.data[erp.n - 1].getMjd()) {
            double day = mjd - erp.data[erp.n - 1].getMjd();
            erpv[0] = erp.data[erp.n - 1].getXp() + erp.data[erp.n - 1].getXpr() * day;
            erpv[1] = erp.data[erp.n - 1].getYp() + erp.data[erp.n - 1].getYpr() * day;
            erpv[2] = erp.data[erp.n - 1].getUt1_utc() - erp.data[erp.n - 1].getLod() * day;
            erpv[3] = erp.data[erp.n - 1].getLod();
            return 1;
        }
        int j = 0;
        int k = erp.n - 1;
        while (j < k - 1) {
            int i = (j + k) / 2;
            if (mjd < erp.data[i].getMjd()) {
                k = i;
                continue;
            }
            j = i;
        }
        double a = erp.data[j].getMjd() == erp.data[j + 1].getMjd() ? 0.5 : (mjd - erp.data[j].getMjd()) / (erp.data[j + 1].getMjd() - erp.data[j].getMjd());
        erpv[0] = (1.0 - a) * erp.data[j].getXp() + a * erp.data[j + 1].getXp();
        erpv[1] = (1.0 - a) * erp.data[j].getYp() + a * erp.data[j + 1].getYp();
        erpv[2] = (1.0 - a) * erp.data[j].getUt1_utc() + a * erp.data[j + 1].getUt1_utc();
        erpv[3] = (1.0 - a) * erp.data[j].getLod() + a * erp.data[j + 1].getLod();
        return 1;
    }

    private static void tide_solid(double[] rsun, double[] rmoon, double[] pos, double[] E, double[] gmst, int opt, double[] dr) {
        double[] dr1 = new double[3];
        double[] dr2 = new double[3];
        double[] eu = new double[]{E[2], E[5], E[8]};
        ppPos.tide_pl(eu, rsun, 1.327124E20, pos, dr1);
        ppPos.tide_pl(eu, rmoon, 4.902801E12, pos, dr2);
        double sin2l = Math.sin(2.0 * pos[0]);
        double du = -0.012 * sin2l * Math.sin(gmst[0] + pos[1]);
        dr[0] = dr1[0] + dr2[0] + du * E[2];
        dr[1] = dr1[1] + dr2[1] + du * E[5];
        dr[2] = dr1[2] + dr2[2] + du * E[8];
        if ((opt & 8) != 0) {
            double sinl = Math.sin(pos[0]);
            du = 0.1196 * (1.5 * sinl * sinl - 0.5);
            double dn = 0.0247 * sin2l;
            dr[0] = dr[0] + (du * E[2] + dn * E[1]);
            dr[1] = dr[1] + (du * E[5] + dn * E[4]);
            dr[2] = dr[2] + (du * E[8] + dn * E[7]);
        }
    }

    private static void tide_pl(double[] eu, double[] rp, double GMp, double[] pos, double[] dr) {
        double d;
        double H3 = 0.292;
        double L3 = 0.015;
        double[] ep = new double[3];
        double r = math.norm(rp, 3);
        if (r <= 0.0) {
            return;
        }
        for (int i = 0; i < 3; ++i) {
            ep[i] = rp[i] / r;
        }
        double K2 = GMp / 3.986004415E14 * util2.SQR(6378137.0) * util2.SQR(6378137.0) / (r * r * r);
        double K3 = K2 * 6378137.0 / r;
        double latp = Math.asin(ep[2]);
        double lonp = Math.atan2(ep[1], ep[0]);
        double cosp = Math.cos(latp);
        double sinl = Math.sin(pos[0]);
        double cosl = Math.cos(pos[0]);
        double p = (3.0 * sinl * sinl - 1.0) / 2.0;
        double H2 = 0.6078 - 6.0E-4 * p;
        double L2 = 0.0847 + 2.0E-4 * p;
        double a = math.dot(ep, eu, 3);
        double dp = K2 * 3.0 * L2 * a;
        double du = K2 * (H2 * (1.5 * a * a - 0.5) - 3.0 * L2 * a * a);
        du += K3 * (0.292 * (2.5 * a * a * a - 1.5 * a) - 0.015 * (7.5 * a * a - 1.5) * a);
        du += 0.001875 * K2 * Math.sin(2.0 * latp) * Math.sin(2.0 * pos[0]) * Math.sin(pos[1] - lonp);
        dr[0] = (dp += K3 * 0.015 * (7.5 * a * a - 1.5)) * ep[0] + (du += 0.00165 * K2 * cosp * cosp * cosl * cosl * Math.sin(2.0 * (pos[1] - lonp))) * eu[0];
        dr[1] = dp * ep[1] + du * eu[1];
        dr[2] = dp * ep[2] + du * eu[2];
    }

    private static void testeclipse(obsd_t[] obs, int n, nav_t nav, double[] rs) {
        double[] rsun = new double[3];
        double[] esun = new double[3];
        double[] erpv = new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
        ppPos.sunmoonpos(util1.gpst2utc(obs[0].time), erpv, rsun, null, null);
        ppPos.normv3(rsun, esun);
        for (int i = 0; i < n; ++i) {
            double cosa;
            double ang;
            double d;
            char[] type = nav.pcvs[obs[i].sat[0] - 1].type;
            double r = math.norm(Arrays.copyOfRange(rs, i * 6, rs.length), 3);
            if (r <= 0.0 || type != null && !Arrays.toString(type).contains("BLOCK IIA") || (ang = Math.acos(cosa = (cosa = math.dot(Arrays.copyOfRange(rs, i * 6, rs.length), esun, 3) / r) < -1.0 ? -1.0 : Math.min(cosa, 1.0))) < 1.5707963267948966 || r * Math.sin(ang) > 6378137.0) continue;
            for (int j = 0; j < 3; ++j) {
                rs[j + i * 6] = 0.0;
            }
        }
    }

    public static void sunmoonpos(gtime_t tutc, double[] erpv, double[] rsun, double[] rmoon, double[] gmst) {
        double[] rs = new double[3];
        double[] rm = new double[3];
        double[] U = new double[9];
        double[] gmst_ = new double[1];
        gtime_t tut = util1.timeadd(tutc, erpv[2]);
        ppPos.sunmoonpos_eci(tut, (double[])(rsun != null ? rs : null), (double[])(rmoon != null ? rm : null));
        ppPos.eci2ecef(tutc, erpv, U, gmst_);
        if (rsun != null) {
            math.matmul("NN", 3, 1, 3, 1.0, U, rs, 0.0, rsun);
        }
        if (rmoon != null) {
            math.matmul("NN", 3, 1, 3, 1.0, U, rm, 0.0, rmoon);
        }
        if (gmst != null) {
            gmst[0] = gmst_[0];
        }
    }

    private static void eci2ecef(gtime_t tutc, double[] erpv, double[] U, double[] gmst) {
        double[] ep2000 = new double[]{2000.0, 1.0, 1.0, 12.0, 0.0, 0.0};
        gtime_t tutc_ = new gtime_t(0.0, 0.0);
        double[] U_ = new double[9];
        double gmst_ = 0.0;
        double[] f = new double[5];
        double[] R1 = new double[9];
        double[] R2 = new double[9];
        double[] R3 = new double[9];
        double[] R = new double[9];
        double[] W = new double[9];
        double[] N = new double[9];
        double[] P = new double[9];
        double[] NP = new double[9];
        double[] deps = new double[1];
        double[] dpsi = new double[1];
        if (Math.abs(util1.timediff(tutc, tutc_)) < 0.01) {
            for (int i = 0; i < 9; ++i) {
                U[i] = U_[i];
            }
            if (gmst != null) {
                gmst[0] = gmst_;
            }
            return;
        }
        tutc_ = tutc;
        gtime_t tgps = util1.utc2gpst(tutc_);
        double t = (util1.timediff(tgps, util1.epoch2time(ep2000)) + 19.0 + 32.184) / 86400.0 / 36525.0;
        double t2 = t * t;
        double t3 = t2 * t;
        ppPos.ast_args(t, f);
        double ze = (2306.2181 * t + 0.30188 * t2 + 0.017998 * t3) * 4.84813681109536E-6;
        double th = (2004.3109 * t - 0.42665 * t2 - 0.041833 * t3) * 4.84813681109536E-6;
        double z = (2306.2181 * t + 1.09468 * t2 + 0.018203 * t3) * 4.84813681109536E-6;
        double eps = (84381.448 - 46.815 * t - 5.9E-4 * t2 + 0.001813 * t3) * 4.84813681109536E-6;
        math.Rz(-z, R1);
        math.Ry(th, R2);
        math.Rz(-ze, R3);
        math.matmul("NN", 3, 3, 3, 1.0, R1, R2, 0.0, R);
        math.matmul("NN", 3, 3, 3, 1.0, R, R3, 0.0, P);
        ppPos.nut_iau1980(t, f, dpsi, deps);
        math.Rx(-eps - deps[0], R1);
        math.Rz(-dpsi[0], R2);
        math.Rx(eps, R3);
        math.matmul("NN", 3, 3, 3, 1.0, R1, R2, 0.0, R);
        math.matmul("NN", 3, 3, 3, 1.0, R, R3, 0.0, N);
        gmst_ = util1.utc2gmst(tutc_, erpv[2]);
        double gast = gmst_ + dpsi[0] * Math.cos(eps);
        math.Ry(-erpv[0], R1);
        math.Rx(-erpv[1], R2);
        math.Rz(gast += (0.00264 * Math.sin(f[4]) + 6.3E-5 * Math.sin(2.0 * f[4])) * 4.84813681109536E-6, R3);
        math.matmul("NN", 3, 3, 3, 1.0, R1, R2, 0.0, W);
        math.matmul("NN", 3, 3, 3, 1.0, W, R3, 0.0, R);
        math.matmul("NN", 3, 3, 3, 1.0, N, P, 0.0, NP);
        math.matmul("NN", 3, 3, 3, 1.0, R, NP, 0.0, U_);
        for (int i = 0; i < 9; ++i) {
            U[i] = U_[i];
        }
        if (gmst != null) {
            gmst[0] = gmst_;
        }
    }

    private static void nut_iau1980(double t, double[] f, double[] dpsi, double[] deps) {
        double[][] nut = new double[][]{{0.0, 0.0, 0.0, 0.0, 1.0, -6798.4, -171996.0, -174.2, 92025.0, 8.9}, {0.0, 0.0, 2.0, -2.0, 2.0, 182.6, -13187.0, -1.6, 5736.0, -3.1}, {0.0, 0.0, 2.0, 0.0, 2.0, 13.7, -2274.0, -0.2, 977.0, -0.5}, {0.0, 0.0, 0.0, 0.0, 2.0, -3399.2, 2062.0, 0.2, -895.0, 0.5}, {0.0, -1.0, 0.0, 0.0, 0.0, -365.3, -1426.0, 3.4, 54.0, -0.1}, {1.0, 0.0, 0.0, 0.0, 0.0, 27.6, 712.0, 0.1, -7.0, 0.0}, {0.0, 1.0, 2.0, -2.0, 2.0, 121.7, -517.0, 1.2, 224.0, -0.6}, {0.0, 0.0, 2.0, 0.0, 1.0, 13.6, -386.0, -0.4, 200.0, 0.0}, {1.0, 0.0, 2.0, 0.0, 2.0, 9.1, -301.0, 0.0, 129.0, -0.1}, {0.0, -1.0, 2.0, -2.0, 2.0, 365.2, 217.0, -0.5, -95.0, 0.3}, {-1.0, 0.0, 0.0, 2.0, 0.0, 31.8, 158.0, 0.0, -1.0, 0.0}, {0.0, 0.0, 2.0, -2.0, 1.0, 177.8, 129.0, 0.1, -70.0, 0.0}, {-1.0, 0.0, 2.0, 0.0, 2.0, 27.1, 123.0, 0.0, -53.0, 0.0}, {1.0, 0.0, 0.0, 0.0, 1.0, 27.7, 63.0, 0.1, -33.0, 0.0}, {0.0, 0.0, 0.0, 2.0, 0.0, 14.8, 63.0, 0.0, -2.0, 0.0}, {-1.0, 0.0, 2.0, 2.0, 2.0, 9.6, -59.0, 0.0, 26.0, 0.0}, {-1.0, 0.0, 0.0, 0.0, 1.0, -27.4, -58.0, -0.1, 32.0, 0.0}, {1.0, 0.0, 2.0, 0.0, 1.0, 9.1, -51.0, 0.0, 27.0, 0.0}, {-2.0, 0.0, 0.0, 2.0, 0.0, -205.9, -48.0, 0.0, 1.0, 0.0}, {-2.0, 0.0, 2.0, 0.0, 1.0, 1305.5, 46.0, 0.0, -24.0, 0.0}, {0.0, 0.0, 2.0, 2.0, 2.0, 7.1, -38.0, 0.0, 16.0, 0.0}, {2.0, 0.0, 2.0, 0.0, 2.0, 6.9, -31.0, 0.0, 13.0, 0.0}, {2.0, 0.0, 0.0, 0.0, 0.0, 13.8, 29.0, 0.0, -1.0, 0.0}, {1.0, 0.0, 2.0, -2.0, 2.0, 23.9, 29.0, 0.0, -12.0, 0.0}, {0.0, 0.0, 2.0, 0.0, 0.0, 13.6, 26.0, 0.0, -1.0, 0.0}, {0.0, 0.0, 2.0, -2.0, 0.0, 173.3, -22.0, 0.0, 0.0, 0.0}, {-1.0, 0.0, 2.0, 0.0, 1.0, 27.0, 21.0, 0.0, -10.0, 0.0}, {0.0, 2.0, 0.0, 0.0, 0.0, 182.6, 17.0, -0.1, 0.0, 0.0}, {0.0, 2.0, 2.0, -2.0, 2.0, 91.3, -16.0, 0.1, 7.0, 0.0}, {-1.0, 0.0, 0.0, 2.0, 1.0, 32.0, 16.0, 0.0, -8.0, 0.0}, {0.0, 1.0, 0.0, 0.0, 1.0, 386.0, -15.0, 0.0, 9.0, 0.0}, {1.0, 0.0, 0.0, -2.0, 1.0, -31.7, -13.0, 0.0, 7.0, 0.0}, {0.0, -1.0, 0.0, 0.0, 1.0, -346.6, -12.0, 0.0, 6.0, 0.0}, {2.0, 0.0, -2.0, 0.0, 0.0, -1095.2, 11.0, 0.0, 0.0, 0.0}, {-1.0, 0.0, 2.0, 2.0, 1.0, 9.5, -10.0, 0.0, 5.0, 0.0}, {1.0, 0.0, 2.0, 2.0, 2.0, 5.6, -8.0, 0.0, 3.0, 0.0}, {0.0, -1.0, 2.0, 0.0, 2.0, 14.2, -7.0, 0.0, 3.0, 0.0}, {0.0, 0.0, 2.0, 2.0, 1.0, 7.1, -7.0, 0.0, 3.0, 0.0}, {1.0, 1.0, 0.0, -2.0, 0.0, -34.8, -7.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 2.0, 0.0, 2.0, 13.2, 7.0, 0.0, -3.0, 0.0}, {-2.0, 0.0, 0.0, 2.0, 1.0, -199.8, -6.0, 0.0, 3.0, 0.0}, {0.0, 0.0, 0.0, 2.0, 1.0, 14.8, -6.0, 0.0, 3.0, 0.0}, {2.0, 0.0, 2.0, -2.0, 2.0, 12.8, 6.0, 0.0, -3.0, 0.0}, {1.0, 0.0, 0.0, 2.0, 0.0, 9.6, 6.0, 0.0, 0.0, 0.0}, {1.0, 0.0, 2.0, -2.0, 1.0, 23.9, 6.0, 0.0, -3.0, 0.0}, {0.0, 0.0, 0.0, -2.0, 1.0, -14.7, -5.0, 0.0, 3.0, 0.0}, {0.0, -1.0, 2.0, -2.0, 1.0, 346.6, -5.0, 0.0, 3.0, 0.0}, {2.0, 0.0, 2.0, 0.0, 1.0, 6.9, -5.0, 0.0, 3.0, 0.0}, {1.0, -1.0, 0.0, 0.0, 0.0, 29.8, 5.0, 0.0, 0.0, 0.0}, {1.0, 0.0, 0.0, -1.0, 0.0, 411.8, -4.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 1.0, 0.0, 29.5, -4.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 0.0, -2.0, 0.0, -15.4, -4.0, 0.0, 0.0, 0.0}, {1.0, 0.0, -2.0, 0.0, 0.0, -26.9, 4.0, 0.0, 0.0, 0.0}, {2.0, 0.0, 0.0, -2.0, 1.0, 212.3, 4.0, 0.0, -2.0, 0.0}, {0.0, 1.0, 2.0, -2.0, 1.0, 119.6, 4.0, 0.0, -2.0, 0.0}, {1.0, 1.0, 0.0, 0.0, 0.0, 25.6, -3.0, 0.0, 0.0, 0.0}, {1.0, -1.0, 0.0, -1.0, 0.0, -3232.9, -3.0, 0.0, 0.0, 0.0}, {-1.0, -1.0, 2.0, 2.0, 2.0, 9.8, -3.0, 0.0, 1.0, 0.0}, {0.0, -1.0, 2.0, 2.0, 2.0, 7.2, -3.0, 0.0, 1.0, 0.0}, {1.0, -1.0, 2.0, 0.0, 2.0, 9.4, -3.0, 0.0, 1.0, 0.0}, {3.0, 0.0, 2.0, 0.0, 2.0, 5.5, -3.0, 0.0, 1.0, 0.0}, {-2.0, 0.0, 2.0, 0.0, 2.0, 1615.7, -3.0, 0.0, 1.0, 0.0}, {1.0, 0.0, 2.0, 0.0, 0.0, 9.1, 3.0, 0.0, 0.0, 0.0}, {-1.0, 0.0, 2.0, 4.0, 2.0, 5.8, -2.0, 0.0, 1.0, 0.0}, {1.0, 0.0, 0.0, 0.0, 2.0, 27.8, -2.0, 0.0, 1.0, 0.0}, {-1.0, 0.0, 2.0, -2.0, 1.0, -32.6, -2.0, 0.0, 1.0, 0.0}, {0.0, -2.0, 2.0, -2.0, 1.0, 6786.3, -2.0, 0.0, 1.0, 0.0}, {-2.0, 0.0, 0.0, 0.0, 1.0, -13.7, -2.0, 0.0, 1.0, 0.0}, {2.0, 0.0, 0.0, 0.0, 1.0, 13.8, 2.0, 0.0, -1.0, 0.0}, {3.0, 0.0, 0.0, 0.0, 0.0, 9.2, 2.0, 0.0, 0.0, 0.0}, {1.0, 1.0, 2.0, 0.0, 2.0, 8.9, 2.0, 0.0, -1.0, 0.0}, {0.0, 0.0, 2.0, 1.0, 2.0, 9.3, 2.0, 0.0, -1.0, 0.0}, {1.0, 0.0, 0.0, 2.0, 1.0, 9.6, -1.0, 0.0, 0.0, 0.0}, {1.0, 0.0, 2.0, 2.0, 1.0, 5.6, -1.0, 0.0, 1.0, 0.0}, {1.0, 1.0, 0.0, -2.0, 1.0, -34.7, -1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 0.0, 2.0, 0.0, 14.2, -1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 2.0, -2.0, 0.0, 117.5, -1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, -2.0, 2.0, 0.0, -329.8, -1.0, 0.0, 0.0, 0.0}, {1.0, 0.0, -2.0, 2.0, 0.0, 23.8, -1.0, 0.0, 0.0, 0.0}, {1.0, 0.0, -2.0, -2.0, 0.0, -9.5, -1.0, 0.0, 0.0, 0.0}, {1.0, 0.0, 2.0, -2.0, 0.0, 32.8, -1.0, 0.0, 0.0, 0.0}, {1.0, 0.0, 0.0, -4.0, 0.0, -10.1, -1.0, 0.0, 0.0, 0.0}, {2.0, 0.0, 0.0, -4.0, 0.0, -15.9, -1.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 2.0, 4.0, 2.0, 4.8, -1.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 2.0, -1.0, 2.0, 25.4, -1.0, 0.0, 0.0, 0.0}, {-2.0, 0.0, 2.0, 4.0, 2.0, 7.3, -1.0, 0.0, 1.0, 0.0}, {2.0, 0.0, 2.0, 2.0, 2.0, 4.7, -1.0, 0.0, 0.0, 0.0}, {0.0, -1.0, 2.0, 0.0, 1.0, 14.2, -1.0, 0.0, 0.0, 0.0}, {0.0, 0.0, -2.0, 0.0, 1.0, -13.6, -1.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 4.0, -2.0, 2.0, 12.7, 1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 0.0, 0.0, 2.0, 409.2, 1.0, 0.0, 0.0, 0.0}, {1.0, 1.0, 2.0, -2.0, 2.0, 22.5, 1.0, 0.0, -1.0, 0.0}, {3.0, 0.0, 2.0, -2.0, 2.0, 8.7, 1.0, 0.0, 0.0, 0.0}, {-2.0, 0.0, 2.0, 2.0, 2.0, 14.6, 1.0, 0.0, -1.0, 0.0}, {-1.0, 0.0, 0.0, 0.0, 2.0, -27.3, 1.0, 0.0, -1.0, 0.0}, {0.0, 0.0, -2.0, 2.0, 1.0, -169.0, 1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 2.0, 0.0, 1.0, 13.1, 1.0, 0.0, 0.0, 0.0}, {-1.0, 0.0, 4.0, 0.0, 2.0, 9.1, 1.0, 0.0, 0.0, 0.0}, {2.0, 1.0, 0.0, -2.0, 0.0, 131.7, 1.0, 0.0, 0.0, 0.0}, {2.0, 0.0, 0.0, 2.0, 0.0, 7.1, 1.0, 0.0, 0.0, 0.0}, {2.0, 0.0, 2.0, -2.0, 1.0, 12.8, 1.0, 0.0, -1.0, 0.0}, {2.0, 0.0, -2.0, 0.0, 1.0, -943.2, 1.0, 0.0, 0.0, 0.0}, {1.0, -1.0, 0.0, -2.0, 0.0, -29.3, 1.0, 0.0, 0.0, 0.0}, {-1.0, 0.0, 0.0, 1.0, 1.0, -388.3, 1.0, 0.0, 0.0, 0.0}, {-1.0, -1.0, 0.0, 2.0, 1.0, 35.0, 1.0, 0.0, 0.0, 0.0}, {0.0, 1.0, 0.0, 1.0, 0.0, 27.3, 1.0, 0.0, 0.0, 0.0}};
        deps[0] = 0.0;
        dpsi[0] = 0.0;
        for (int i = 0; i < 106; ++i) {
            double ang = 0.0;
            for (int j = 0; j < 5; ++j) {
                ang += nut[i][j] * f[j];
            }
            dpsi[0] = dpsi[0] + (nut[i][6] + nut[i][7] * t) * Math.sin(ang);
            deps[0] = deps[0] + (nut[i][8] + nut[i][9] * t) * Math.cos(ang);
        }
        dpsi[0] = dpsi[0] * 4.84813681109536E-10;
        deps[0] = deps[0] * 4.84813681109536E-10;
    }

    private static void sunmoonpos_eci(gtime_t tut, double[] rsun, double[] rmoon) {
        double cosl;
        double sinl;
        double[] ep2000 = new double[]{2000.0, 1.0, 1.0, 12.0, 0.0, 0.0};
        double[] f = new double[5];
        double t = util1.timediff(tut, util1.epoch2time(ep2000)) / 86400.0 / 36525.0;
        ppPos.ast_args(t, f);
        double eps = 23.439291 - 0.0130042 * t;
        double sine = Math.sin(eps * (Math.PI / 180));
        double cose = Math.cos(eps * (Math.PI / 180));
        if (rsun != null) {
            double Ms = 357.5277233 + 35999.05034 * t;
            double ls = 280.46 + 36000.77 * t + 1.914666471 * Math.sin(Ms * (Math.PI / 180)) + 0.019994643 * Math.sin(2.0 * Ms * (Math.PI / 180));
            double rs = 1.49597870691E11 * (1.000140612 - 0.016708617 * Math.cos(Ms * (Math.PI / 180)) - 1.39589E-4 * Math.cos(2.0 * Ms * (Math.PI / 180)));
            sinl = Math.sin(ls * (Math.PI / 180));
            cosl = Math.cos(ls * (Math.PI / 180));
            rsun[0] = rs * cosl;
            rsun[1] = rs * cose * sinl;
            rsun[2] = rs * sine * sinl;
        }
        if (rmoon != null) {
            double lm = 218.32 + 481267.883 * t + 6.29 * Math.sin(f[0]) - 1.27 * Math.sin(f[0] - 2.0 * f[3]) + 0.66 * Math.sin(2.0 * f[3]) + 0.21 * Math.sin(2.0 * f[0]) - 0.19 * Math.sin(f[1]) - 0.11 * Math.sin(2.0 * f[2]);
            double pm = 5.13 * Math.sin(f[2]) + 0.28 * Math.sin(f[0] + f[2]) - 0.28 * Math.sin(f[2] - f[0]) - 0.17 * Math.sin(f[2] - 2.0 * f[3]);
            double rm = 6378137.0 / Math.sin((0.9508 + 0.0518 * Math.cos(f[0]) + 0.0095 * Math.cos(f[0] - 2.0 * f[3]) + 0.0078 * Math.cos(2.0 * f[3]) + 0.0028 * Math.cos(2.0 * f[0])) * (Math.PI / 180));
            sinl = Math.sin(lm * (Math.PI / 180));
            cosl = Math.cos(lm * (Math.PI / 180));
            double sinp = Math.sin(pm * (Math.PI / 180));
            double cosp = Math.cos(pm * (Math.PI / 180));
            rmoon[0] = rm * cosp * cosl;
            rmoon[1] = rm * (cose * cosp * sinl - sine * sinp);
            rmoon[2] = rm * (sine * cosp * sinl + cose * sinp);
        }
    }

    private static void ast_args(double t, double[] f) {
        int i;
        double[][] fc = new double[][]{{134.96340251, 1.7179159232178E9, 31.8792, 0.051635, -2.447E-4}, {357.52910918, 1.295965810481E8, -0.5532, 1.36E-4, -1.149E-5}, {93.27209062, 1.7395272628478E9, -12.7512, -0.001037, 4.17E-6}, {297.85019547, 1.602961601209E9, -6.3706, 0.006593, -3.169E-5}, {125.04455501, -6962890.2665, 7.4722, 0.007702, -5.939E-5}};
        double[] tt = new double[4];
        tt[0] = t;
        for (i = 1; i < 4; ++i) {
            tt[i] = tt[i - 1] * t;
        }
        for (i = 0; i < 5; ++i) {
            f[i] = fc[i][0] * 3600.0;
            for (int j = 0; j < 4; ++j) {
                int n = i;
                f[n] = f[n] + fc[i][j + 1] * tt[j];
            }
            f[i] = f[i] * 4.84813681109536E-6 % (Math.PI * 2);
        }
    }
}

