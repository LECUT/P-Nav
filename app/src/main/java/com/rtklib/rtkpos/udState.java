/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.rtkpos;

import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.rtk_t;
import com.rtklib.pntpos.rescode;
import com.rtklib.rtkpos.rtkPos;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;

public class udState {
    public static final double VAR_POS = Math.pow(30.0, 2.0);
    public static final double VAR_VEL = Math.pow(10.0, 2.0);
    public static final double VAR_ACC = Math.pow(10.0, 2.0);
    public static final double VAR_CLK = util2.SQR(60.0);
    public static final int PMODE_SINGLE = 0;
    public static final int PMODE_DGPS = 1;
    public static final int PMODE_KINEMA = 2;
    public static final int PMODE_STATIC = 3;
    public static final int PMODE_STATIC_START = 4;  /* EX: new */
    public static final int PMODE_MOVEB = 5;          /* EX: was 4 */
    public static final int PMODE_FIXED = 6;          /* EX: was 5 */
    public static final int PMODE_PPP_KINEMA = 7;     /* EX: was 6 */
    public static final int PMODE_PPP_STATIC = 8;     /* EX: was 7 */
    public static final int PMODE_PPP_FIXED = 9;      /* EX: was 8 */
    public static final int GAP_RESION = 120;
    public static final double INIT_ZWD = 0.15;
    public static final double VAR_GRA = util2.SQR(0.001);
    public static final int NFREQGLO = 2;
    public static final double VAR_HWBIAS = 1.0;
    public static final double PRN_HWBIAS = 1.0E-6;

    public static void initx(rtk_t rtk, double xi, double var, int i) {
        rtk.x[i] = xi;
        for (int j = 0; j < rtk.nx; ++j) {
            double d = i == j ? var : 0.0;
            rtk.P[j + i * rtk.nx] = d;
            rtk.P[i + j * rtk.nx] = d;
        }
    }

    public static void udpos(rtk_t rtk, double tt) {
        int j;
        int i;
        double var = 0.0;
        double[] pos = new double[3];
        double[] Q = new double[9];
        double[] Qv = new double[9];
        if (rtk.opt.mode == PMODE_FIXED) {
            for (int i2 = 0; i2 < 3; ++i2) {
                udState.initx(rtk, rtk.opt.getRu()[i2], 1.0E-8, i2);
            }
            return;
        }
        if (math.norm(rtk.x, 3) <= 0.0) {
            for (i = 0; i < 3; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], VAR_POS, i);
            }
            if (rtk.opt.getDynamics() == 1) {
                for (i = 3; i < 6; ++i) {
                    udState.initx(rtk, rtk.sol.rr[i], VAR_VEL, i);
                }
                for (i = 6; i < 9; ++i) {
                    udState.initx(rtk, 1.0E-6, VAR_ACC, i);
                }
            }
        }
        if (rtk.opt.mode == 3) {
            return;
        }
        if (rtk.opt.getDynamics() == 0) {
            for (i = 0; i < 3; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], VAR_POS, i);
            }
            return;
        }
        for (i = 0; i < 3; ++i) {
            var += rtk.P[i + i * rtk.nx];
        }
        if ((var /= 3.0) > VAR_POS) {
            for (i = 0; i < 3; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], VAR_POS, i);
            }
            for (i = 3; i < 6; ++i) {
                udState.initx(rtk, rtk.sol.rr[i], VAR_VEL, i);
            }
            for (i = 6; i < 9; ++i) {
                udState.initx(rtk, 1.0E-6, VAR_ACC, i);
            }
            return;
        }
        int[] ix = math.imat(rtk.nx, 1);
        int nx = 0;
        for (i = 0; i < rtk.nx; ++i) {
            if (rtk.x[i] == 0.0 || !(rtk.P[i + i * rtk.nx] > 0.0) || ix == null) continue;
            ix[nx++] = i;
        }
        if (nx < 9) {
            return;
        }
        double[] F = math.eye(nx);
        double[] P = math.mat(nx, nx);
        double[] FP = math.mat(nx, nx);
        double[] x = math.mat(nx, 1);
        double[] xp = math.mat(nx, 1);
        for (i = 0; i < 6; ++i) {
            if (F == null) continue;
            F[i + (i + 3) * nx] = tt;
        }
        for (i = 0; i < 3; ++i) {
            if (F == null) continue;
            F[i + (i + 6) * nx] = util2.SQR(tt) / 2.0;
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
        Q[0] = Q[4] = util2.SQR(rtk.opt.getPrn()[3]) * Math.abs(tt);
        Q[8] = util2.SQR(rtk.opt.getPrn()[4]) * Math.abs(tt);
        rescode.ecef2pos(rtk.x, pos);
        util2.covecef(pos, Q, Qv);
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 3; ++j) {
                int n = i + 6 + (j + 6) * rtk.nx;
                rtk.P[n] = rtk.P[n] + Qv[i + j * 3];
            }
        }
    }

    public static double baseline(double[] ru, double[] rb, double[] dr) {
        for (int i = 0; i < 3; ++i) {
            dr[i] = ru[i] - rb[i];
        }
        return math.norm(dr, 3);
    }

    public static int NP(prcopt_t opt) {
        return opt.getDynamics() == 0 ? 3 : 9;
    }

    public static int NR(prcopt_t opt) {
        return udState.NP(opt) + udState.NI(opt) + udState.NT(opt) + udState.NL(opt);
    }

    public static int NL(prcopt_t opt) {
        return opt.glomodear != 2 ? 0 : 2;
    }

    public static int NX(prcopt_t opt) {
        return udState.NR(opt) + udState.NB(opt);
    }

    public static int NB(prcopt_t opt) {
        return opt.mode <= 1 ? 0 : nav_t.getMaxSat() * rtkPos.NF(opt);
    }

    public static int NI(prcopt_t opt) {
        return opt.ionoopt != 4 ? 0 : nav_t.getMaxSat();
    }

    public static int NT(prcopt_t opt) {
        return opt.tropopt < 3 ? 0 : (opt.tropopt < 4 ? 2 : 6);
    }

    public static int II(int s, prcopt_t opt) {
        return udState.NP(opt) + s - 1;
    }

    public static int IT(int r, prcopt_t opt) {
        return udState.NP(opt) + udState.NI(opt) + udState.NT(opt) / 2 * r;
    }

    public static int IL(int f, prcopt_t opt) {
        return udState.NP(opt) + udState.NI(opt) + udState.NT(opt) + f;
    }

    public static int IB(int s, int f, prcopt_t opt) {
        return udState.NR(opt) + nav_t.getMaxSat() * f + s - 1;
    }

    public static void udion(rtk_t rtk, double tt, double bl, int[] sat, int ns) {
        int j;
        int i;
        for (i = 1; i <= nav_t.getMaxSat(); ++i) {
            j = udState.II(i, rtk.opt);
            if (rtk.x[j] == 0.0 || rtk.ssat[i - 1].outc[0] <= 120L || rtk.ssat[i - 1].outc[1] <= 120L) continue;
            rtk.x[j] = 0.0;
        }
        for (i = 0; i < ns; ++i) {
            j = udState.II(sat[i], rtk.opt);
            if (rtk.x[j] == 0.0) {
                udState.initx(rtk, 1.0E-6, util2.SQR(rtk.opt.getStd()[1] * bl / 10000.0), j);
                continue;
            }
            double el = rtk.ssat[sat[i] - 1].azel[1];
            double fact = Math.cos(el);
            int n = j + j * rtk.nx;
            rtk.P[n] = rtk.P[n] + util2.SQR(rtk.opt.getPrn()[1] * bl / 10000.0 * fact) * Math.abs(tt);
        }
    }

    public static void udtrop(rtk_t rtk, double tt, double bl) {
        for (int i = 0; i < 2; ++i) {
            int k;
            int j = udState.IT(i, rtk.opt);
            if (rtk.x[j] == 0.0) {
                udState.initx(rtk, 0.15, util2.SQR(rtk.opt.getStd()[2]), j);
                if (rtk.opt.tropopt < 4) continue;
                for (k = 0; k < 2; ++k) {
                    udState.initx(rtk, 1.0E-6, VAR_GRA, ++j);
                }
                continue;
            }
            int n = j + j * rtk.nx;
            rtk.P[n] = rtk.P[n] + util2.SQR(rtk.opt.getPrn()[2]) * Math.abs(tt);
            if (rtk.opt.tropopt < 4) continue;
            for (k = 0; k < 2; ++k) {
                int n2 = ++j * (1 + rtk.nx);
                rtk.P[n2] = rtk.P[n2] + util2.SQR(rtk.opt.getPrn()[2] * 0.3) * Math.abs(tt);
            }
        }
    }

    public static void udrcvbias(rtk_t rtk, double tt) {
        for (int i = 0; i < 2; ++i) {
            int j = udState.IL(i, rtk.opt);
            if (rtk.x[j] == 0.0) {
                udState.initx(rtk, 1.0E-6, 1.0, j);
                continue;
            }
            if (rtk.nfix >= rtk.opt.getMinfix() && (double)rtk.sol.ratio > rtk.opt.getThresar()[0]) {
                udState.initx(rtk, rtk.xa[j], rtk.Pa[j + j * rtk.na], j);
                continue;
            }
            int n = j + j * rtk.nx;
            rtk.P[n] = rtk.P[n] + util2.SQR(1.0E-6) * Math.abs(tt);
        }
    }

    public static int getbitu(byte[] buff, int pos, int len) {
        int bits = 0;
        for (int i = pos; i < pos + len; ++i) {
            bits = (bits << 1) + (buff[i / 8] >> 7 - i % 8 & 1);
        }
        return bits;
    }

    private static void setbitu(byte[] buff, int pos, int len, int data) {
        int mask = 1 << len - 1;
        if (len <= 0 || 32 < len) {
            return;
        }
        int i = pos;
        while (i < pos + len) {
            if ((data & mask) != 0) {
                int n = i / 8;
                buff[n] = (byte)(buff[n] | (byte)(1 << 7 - i % 8));
            } else {
                int n = i / 8;
                buff[n] = (byte)(buff[n] & (byte)(~(1 << 7 - i % 8)));
            }
            ++i;
            mask >>= 1;
        }
    }

    public static void detslp_ll(rtk_t rtk, obsd_t[] obs, int i, int rcv) {
        int slip = 0;
        int sat = obs[i].sat[0];
        for (int f = 0; f < rtk.opt.getNf(); ++f) {
            if (obs[i].L[f] == 0.0 || Math.abs(util1.timediff(obs[i].time, rtk.ssat[sat - 1].pt[rcv - 1][f])) < 0.025) continue;
            int LLI = rcv == 1 ? udState.getbitu(rtk.ssat[sat - 1].slip, 0, 2) : udState.getbitu(rtk.ssat[sat - 1].slip, 2, 2);
            if (rtk.tt >= 0.0) {
                if ((obs[i].LLI[f] & 1) == 1) {
                    System.out.println("slip detected forward");
                }
                slip = obs[i].LLI[f];
            } else {
                if ((LLI & 1) == 1) {
                    System.out.println("slip detected backward");
                }
                slip = LLI;
            }
            if ((LLI & 2) == 2 && (obs[i].LLI[f] & 2) == 0 || (LLI & 2) == 0 && (obs[i].LLI[f] & 2) == 2) {
                System.out.println("slip detected half-cyc");
                slip |= 1;
            }
            if (rcv == 1) {
                udState.setbitu(rtk.ssat[sat - 1].slip, 0, 2, obs[i].LLI[f]);
            } else {
                udState.setbitu(rtk.ssat[sat - 1].slip, 2, 2, obs[i].LLI[f]);
            }
            int n = f;
            rtk.ssat[sat - 1].slip[n] = (byte)(rtk.ssat[sat - 1].slip[n] | (byte)slip);
            rtk.ssat[sat - 1].half[f] = (byte)((obs[i].LLI[f] & 2) != 2 ? 1 : 0);
        }
    }

    static void detslp_gf(rtk_t rtk, obsd_t[] obs, int i, int j, nav_t nav) {
        int sat = obs[i].sat[0];
        for (int k = 1; k < rtk.opt.getNf(); ++k) {
            double g1 = udState.gfobs(obs, i, j, k, nav);
            if (g1 == 0.0) {
                return;
            }
            double g0 = rtk.ssat[sat - 1].gf[k - 1];
            rtk.ssat[sat - 1].gf[k - 1] = g1;
            if (g0 == 0.0 || !(Math.abs(g1 - g0) > rtk.opt.getThresslip())) continue;
            rtk.ssat[sat - 1].slip[0] = (byte)(rtk.ssat[sat - 1].slip[0] | 1);
            int n = k;
            rtk.ssat[sat - 1].slip[n] = (byte)(rtk.ssat[sat - 1].slip[n] | 1);
        }
    }

    static double gfobs(obsd_t[] obs, int i, int j, int k, nav_t nav) {
        double freq1 = rescode.sat2freq(obs[i].sat, obs[i].code[0], nav);
        double freq2 = rescode.sat2freq(obs[i].sat, obs[i].code[k], nav);
        double L1 = udState.sdobs(obs, i, j, 0);
        double L2 = udState.sdobs(obs, i, j, k);
        if (freq1 == 0.0 || freq2 == 0.0 || L1 == 0.0 || L2 == 0.0) {
            return 0.0;
        }
        return L1 * 2.99792458E8 / freq1 - L2 * 2.99792458E8 / freq2;
    }

    static double sdobs(obsd_t[] obs, int i, int j, int k) {
        double pi = k < 3 ? obs[i].L[k] : obs[i].P[k - 3];
        double pj = k < 3 ? obs[j].L[k] : obs[j].P[k - 3];
        return pi == 0.0 || pj == 0.0 ? 0.0 : pi - pj;
    }

    public static void udbias(rtk_t rtk, double tt, obsd_t[] obs, int[] sat, int[] iu, int[] ir, int ns, nav_t nav) {
        int k;
        int i;
        int nf = rtkPos.NF(rtk.opt);
        for (i = 0; i < ns; ++i) {
            k = 0;
            while (k < rtk.opt.getNf()) {
                int n = k++;
                rtk.ssat[sat[i] - 1].slip[n] = (byte)(rtk.ssat[sat[i] - 1].slip[n] & 0xFFFFFFFC);
            }
            udState.detslp_ll(rtk, obs, iu[i], 1);
            udState.detslp_ll(rtk, obs, ir[i], 2);
            udState.detslp_gf(rtk, obs, iu[i], ir[i], nav);
            for (int kk = 0; kk < nf; ++kk) {
                rtk.ssat[sat[i] - 1].half[kk] = (byte)((obs[iu[i]].LLI[kk] & 2) == 0 && (obs[ir[i]].LLI[kk] & 2) == 0 ? 1 : 0);
            }
        }
        for (k = 0; k < nf; ++k) {
            int j;
            for (i = 1; i <= nav_t.getMaxSat(); ++i) {
                boolean reset;
                int n = k;
                long l = rtk.ssat[i - 1].outc[n] + 1L;
                rtk.ssat[i - 1].outc[n] = l;
                boolean bl = reset = l > (long)rtk.opt.getMaxout();
                if ((double)rtk.opt.getModear() == 2.0 && rtk.x[udState.IB(i, k, rtk.opt)] != 0.0) {
                    udState.initx(rtk, 0.0, 0.0, udState.IB(i, k, rtk.opt));
                } else if (reset && rtk.x[udState.IB(i, k, rtk.opt)] != 0.0) {
                    udState.initx(rtk, 0.0, 0.0, udState.IB(i, k, rtk.opt));
                    rtk.ssat[i - 1].outc[k] = 0L;
                }
                if ((double)rtk.opt.getModear() == 2.0 || !reset) continue;
                rtk.ssat[i - 1].lock[k] = -rtk.opt.getMinlock();
            }
            for (i = 0; i < ns; ++i) {
                j = udState.IB(sat[i], k, rtk.opt);
                int n = j + j * rtk.nx;
                rtk.P[n] = rtk.P[n] + rtk.opt.getPrn()[0] * rtk.opt.getPrn()[0] * Math.abs(tt);
                int slip = rtk.ssat[sat[i] - 1].slip[k];
                if (rtk.opt.ionoopt == 3) {
                    slip |= rtk.ssat[sat[i] - 1].slip[1];
                }
                if ((double)rtk.opt.getModear() == 2.0 || (slip & 1) == 0) continue;
                rtk.x[j] = 0.0;
                rtk.ssat[sat[i] - 1].lock[k] = -rtk.opt.getMinlock();
            }
            double[] bias = math.zeros(ns, 1);
            j = 0;
            double offset = 0.0;
            for (i = 0; i < ns; ++i) {
                if (rtk.opt.ionoopt != 3) {
                    double cp = udState.sdobs(obs, iu[i], ir[i], k);
                    double pr = udState.sdobs(obs, iu[i], ir[i], k + 3);
                    double freqi = rescode.sat2freq(sat[i], obs[iu[i]].code[k], nav);
                    if (cp == 0.0 || pr == 0.0 || freqi == 0.0) continue;
                    bias[i] = cp - pr * freqi / 2.99792458E8;
                } else {
                    double cp1 = udState.sdobs(obs, iu[i], ir[i], 0);
                    double cp2 = udState.sdobs(obs, iu[i], ir[i], 1);
                    double pr1 = udState.sdobs(obs, iu[i], ir[i], 3);
                    double pr2 = udState.sdobs(obs, iu[i], ir[i], 4);
                    double freq1 = rescode.sat2freq(sat[i], obs[iu[i]].code[0], nav);
                    double freq2 = rescode.sat2freq(sat[i], obs[iu[i]].code[1], nav);
                    if (cp1 == 0.0 || cp2 == 0.0 || pr1 == 0.0 || pr2 == 0.0 || freq1 == 0.0 || freq2 <= 0.0) continue;
                    double C1 = util2.SQR(freq1) / (util2.SQR(freq1) - util2.SQR(freq2));
                    double C2 = -util2.SQR(freq2) / (util2.SQR(freq1) - util2.SQR(freq2));
                    bias[i] = C1 * cp1 * 2.99792458E8 / freq1 + C2 * cp2 * 2.99792458E8 / freq2 - (C1 * pr1 + C2 * pr2);
                }
                if (rtk.x[udState.IB(sat[i], k, rtk.opt)] == 0.0) continue;
                offset += bias[i] - rtk.x[udState.IB(sat[i], k, rtk.opt)];
                ++j;
            }
            if (j > 0) {
                for (i = 1; i <= nav_t.getMaxSat(); ++i) {
                    if (rtk.x[udState.IB(i, k, rtk.opt)] == 0.0) continue;
                    int n = udState.IB(i, k, rtk.opt);
                    rtk.x[n] = rtk.x[n] + offset / (double)j;
                }
            }
            for (i = 0; i < ns; ++i) {
                if (bias[i] == 0.0 || rtk.x[udState.IB(sat[i], k, rtk.opt)] != 0.0) continue;
                udState.initx(rtk, bias[i], util2.SQR(rtk.opt.getStd()[0]), udState.IB(sat[i], k, rtk.opt));
            }
        }
    }

    public static void udstate(rtk_t rtk, obsd_t[] obs, int[] sat, int[] iu, int[] ir, int ns, nav_t nav) {
        double tt = rtk.tt;
        double bl = 0.0;
        double[] dr = new double[3];
        udState.udpos(rtk, tt);
        if (rtk.opt.ionoopt >= 4) {
            bl = udState.baseline(rtk.x, rtk.rb, dr);
            udState.udion(rtk, tt, bl, sat, ns);
        }
        if (rtk.opt.tropopt >= 3) {
            udState.udtrop(rtk, tt, bl);
        }
        if (rtk.opt.glomodear == 2 && (rtk.opt.navsys & 4) != 0) {
            udState.udrcvbias(rtk, tt);
        }
        if (rtk.opt.mode > 1) {
            udState.udbias(rtk, tt, obs, sat, iu, ir, ns, nav);
        }
    }
}

