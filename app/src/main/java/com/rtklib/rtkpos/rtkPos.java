/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.rtkpos;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.rtk_t;
import com.rtklib.pntpos.pntPos;
import com.rtklib.pntpos.rescode;
import com.rtklib.pntpos.satposs;
import com.rtklib.readobsnav.satSys;
import com.rtklib.rtkpos.ppPos;
import com.rtklib.rtkpos.udState;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.util.Arrays;

public class rtkPos {
    public static double gps_l1_lamda = 0.19029366893311156;
    public static final int POSOPT_RINEX = 3;
    public static final int ARMODE_OFF = 0;
    static final int PMODE_MOVEB = 5;           /* EX: was 4 */
    static final int PMODE_PPP_KINEMA = 7;  /* EX: was 6 */
    static final int PMODE_DGPS = 1;
    static final int SOLQ_FLOAT = 2;
    static final int SOLQ_FIX = 1;
    public static final double D2R = Math.PI / 180;
    public static final int PMODE_FIXED = 6;    /* EX: was 5 */
    private static final double HION = 350000.0;
    private static final int LOOPMAX = 10000;
    private static final double VAR_HOLDAMB = 0.001;
    static int nb = 0;
    static obsd_t[] obsb = new obsd_t[96];
    static double[] yb = new double[576];
    static double[] rs = new double[576];
    static double[] dts = new double[192];
    static double[] var = new double[96];
    static double[] e = new double[288];
    static double[] azel = new double[192];
    static double[] freq = new double[288];
    static int[] svh = new int[192];

    public static int NF(prcopt_t opt) {
        if (opt.ionoopt == 3) {
            return 1;
        }
        return opt.getNf();
    }

    public static int rtk_pos(rtk_t rtk, obsd_t[] obs, int n, nav_t nav) {
        prcopt_t opt = rtk.opt;
        int nu = 0;
        int nr = 0;
        String msg = null;
        if (opt.refpos <= 3 && opt.mode != 0 && opt.mode != PMODE_MOVEB) {
            for (int i = 0; i < 6; ++i) {
                rtk.rb[i] = i < 3 ? opt.getRb()[i] : 0.0;
            }
        }
        while (nu < n && obs[nu].rcv == 1) {
            ++nu;
        }
        while (nu + nr < n && obs[nu + nr].rcv == 2) {
            ++nr;
        }
        gtime_t time = rtk.sol.time;
        if (pntPos.pnt_pos(obs, nu, nav, rtk.opt, rtk.sol, null, rtk.ssat, msg) == 0) {
            System.out.println("point pos error in rtk_pos:" + rtk.sol.time.time);
            if (rtk.opt.getDynamics() == 0) {
                return 0;
            }
        }
        if (time.time != 0.0) {
            rtk.tt = util1.timediff(rtk.sol.time, time);
        }
        if (opt.mode == 0) {
            return 1;
        }
        if (opt.getOutsingle() == 0) {
            rtk.sol.stat = 0;
        }
        if (opt.mode >= PMODE_PPP_KINEMA) {
            ppPos.pppos(rtk, obs, nu, nav);
            return 1;
        }
        if (nr == 0) {
            System.out.println("no base station observation data for rtk");
            return 1;
        }
        if (opt.mode != PMODE_MOVEB) {
            rtk.sol.age = (float)util1.timediff(obs[0].time, obs[nu].time);
            if ((double)Math.abs(rtk.sol.age) > opt.getMaxtdiff()) {
                System.out.println("age of differential error");
                return 1;
            }
        }
        rtkPos.rel_pos(rtk, obs, nu, nr, nav);
        return 1;
    }

    public static double intpres(gtime_t time, obsd_t[] obs, int n, nav_t nav, rtk_t rtk, double[] y) {
        prcopt_t opt = rtk.opt;
        double tt = util1.timediff(time, obs[0].time);
        int nf = rtkPos.NF(opt);
        if (nb == 0 || Math.abs(tt) < 0.025) {
            nb = n;
            for (int i = 0; i < n; ++i) {
                rtkPos.obsb[i] = obs[i];
            }
            return tt;
        }
        double ttb = util1.timediff(time, rtkPos.obsb[0].time);
        if (Math.abs(ttb) > opt.getMaxtdiff() * 2.0 || ttb == tt) {
            return tt;
        }
        satposs.sat_poss(time, obsb, nb, nav, opt.getSateph(), rs, dts, var, svh);
        if (rtkPos.zdres(1, obsb, nb, rs, dts, var, svh, nav, rtk.rb, opt, 1, yb, e, azel, freq) == 0) {
            return tt;
        }
        for (int i = 0; i < n; ++i) {
            int j;
            for (j = 0; j < nb && rtkPos.obsb[j].sat != obs[i].sat; ++j) {
            }
            if (j >= nb) continue;
            int k = 0;
            double p = y[i * nf * 2];
            double q = yb[j * nf * 2];
            while (k < nf * 2) {
                p = p == 0.0 || q == 0.0 ? 0.0 : (ttb * p - tt * q) / (ttb - tt);
                ++k;
                p += 1.0;
                q += 1.0;
            }
        }
        return Math.abs(ttb) > Math.abs(tt) ? ttb : tt;
    }

    public static int selsat(obsd_t[] obs, double[] azel, int nu, int nr, prcopt_t opt, int[] sat, int[] iu, int[] ir) {
        int k = 0;
        int i = 0;
        for (int j = nu; i < nu && j < nu + nr; ++i, ++j) {
            if (obs[i].sat[0] < obs[j].sat[0]) {
                --j;
                continue;
            }
            if (obs[i].sat[0] > obs[j].sat[0]) {
                --i;
                continue;
            }
            if (!(azel[1 + j * 2] >= opt.getElmin())) continue;
            sat[k] = obs[i].sat[0];
            iu[k] = i;
            ir[k] = j;
            ++k;
        }
        return k;
    }

    public static double ionmapf(double[] pos, double[] azel) {
        if (pos[2] >= 350000.0) {
            return 1.0;
        }
        return 1.0 / Math.cos(Math.asin((6378137.0 + pos[2]) / 6728137.0 * Math.sin(1.5707963267948966 - azel[1])));
    }

    public static double prectrop(gtime_t time, double[] pos, int r, double[] azel, prcopt_t opt, double[] x, double[] dtdx) {
        int i = udState.IT(r, opt);
        double[] m_w = new double[1];
        rescode.tropmapf(time, pos, azel, m_w);
        if (opt.tropopt >= 4 && azel[1] > 0.0) {
            double cotz = 1.0 / Math.tan(azel[1]);
            double grad_n = m_w[0] * cotz * Math.cos(azel[0]);
            double grad_e = m_w[0] * cotz * Math.sin(azel[0]);
            m_w[0] = m_w[0] + (grad_n * x[i + 1] + grad_e * x[i + 2]);
            dtdx[1] = grad_n * x[i];
            dtdx[2] = grad_e * x[i];
        } else {
            dtdx[2] = 0.0;
            dtdx[1] = 0.0;
        }
        dtdx[0] = m_w[0];
        return m_w[0] * x[i];
    }

    public static boolean test_sys(int sys, int m) {
        switch (sys) {
            case 1: {
                return m == 0;
            }
            case 2: {
                return m == 0;
            }
            case 4: {
                return m == 1;
            }
            case 8: {
                return m == 2;
            }
            case 32: {
                return m == 3;
            }
            case 16: {
                return m == 4;
            }
            case 64: {
                return m == 5;
            }
        }
        return false;
    }

    public static boolean validobs(int i, int j, int f, int nf, double[] y) {
        return y[f + i * nf * 2] != 0.0 && y[f + j * nf * 2] != 0.0 && (f < nf || y[f - nf + i * nf * 2] != 0.0 && y[f - nf + j * nf * 2] != 0.0);
    }

    public static boolean dgps_validobs(int i, int j, int f, int nf, double[] y) {
        return y[f + i * nf * 2] != 0.0 && y[f + j * nf * 2] != 0.0;
    }

    public static double varerr(int sat, int sys, double el, double bl, double dt, int f, double snr, prcopt_t opt) {
        double c = opt.getErr()[4] * bl / 10000.0;
        double d = 2.99792458E8 * opt.getSclkstab() * dt;
        double fact = 1.0;
        double sinel = Math.sin(el);
        int nf = rtkPos.NF(opt);
        if (f >= nf) {
            fact = opt.getEratio()[f - nf];
        }
        if (fact <= 0.0) {
            fact = opt.getEratio()[0];
        }
        fact *= sys == 4 ? rescode.EFACT_GLO : (sys == 2 ? rescode.EFACT_SBS : rescode.EFACT_GPS);
        double a = fact * opt.getErr()[2];
        double b = fact * opt.getErr()[3];
        double elVar;
        if (opt.getVarmodel() == 1 && snr > 0.0) {
            /* SNR-based stochastic model */
            elVar = b * b * Math.pow(10, (50.0 - snr) / 10.0);
        } else {
            /* height-angle model (default) */
            elVar = b * b / sinel / sinel;
        }
        return 2.0 * (opt.ionoopt == 3 ? 3.0 : 1.0) * (a * a + elVar + c * c) + d * d;
    }

    public static int constbl(rtk_t rtk, double[] x, double[] P, double[] v, double[] H, double[] Ri, double[] Rj, int index) {
        int i;
        double thres = 0.1;
        double var = 0.0;
        double[] xb = new double[3];
        double[] b = new double[3];
        if (rtk.opt.getBaseline()[0] <= 0.0) {
            return 0;
        }
        for (i = 0; i < 3; ++i) {
            xb[i] = rtk.rb[i];
            b[i] = x[i] - xb[i];
        }
        double bb = math.norm(b, 3);
        if (P != null) {
            for (i = 0; i < 3; ++i) {
                var += P[i + i * rtk.nx];
            }
            var /= 3.0;
        }
        if (var > util2.SQR(thres * bb)) {
            return 0;
        }
        v[index] = rtk.opt.getBaseline()[0] - bb;
        if (H != null) {
            for (i = 0; i < 3; ++i) {
                H[i + index * rtk.nx] = b[i] / bb;
            }
        }
        Ri[index] = 0.0;
        Rj[index] = util2.SQR(rtk.opt.getBaseline()[1]);
        return 1;
    }

    public static void ddcov(int[] nb, int n, double[] Ri, double[] Rj, int nv, double[] R) {
        int i;
        int k = 0;
        for (i = 0; i < nv * nv; ++i) {
            R[i] = 0.0;
        }
        int b = 0;
        while (b < n) {
            for (i = 0; i < nb[b]; ++i) {
                for (int j = 0; j < nb[b]; ++j) {
                    R[k + i + (k + j) * nv] = Ri[k + i] + (i == j ? Rj[k + i] : 0.0);
                }
            }
            k += nb[b++];
        }
    }

    public static int ddres(rtk_t rtk, nav_t nav, double dt, double[] x, double[] P, int[] sat, double[] y, double[] e, double[] azel, double[] freq, int[] iu, int[] ir, int ns, double[] v, double[] H, double[] R, int[] vflg) {
        int j;
        int i;
        prcopt_t opt = rtk.opt;
        double didxi = 0.0;
        double didxj = 0.0;
        double[] dr = new double[3];
        double[] posu = new double[3];
        double[] posr = new double[3];
        double[] Hi = null;
        int nv = 0;
        int b = 0;
        int nf = rtkPos.NF(opt);
        int[] nb = new int[26];
        int startIndex = 0;
        double bl = udState.baseline(x, rtk.rb, dr);
        rescode.ecef2pos(x, posu);
        rescode.ecef2pos(rtk.rb, posr);
        double[] Ri = math.mat(ns * nf * 2 + 2, 1);
        double[] Rj = math.mat(ns * nf * 2 + 2, 1);
        double[] im = math.mat(ns, 1);
        double[] tropu = math.mat(ns, 1);
        double[] tropr = math.mat(ns, 1);
        double[] dtdxu = math.mat(ns, 3);
        double[] dtdxr = math.mat(ns, 3);
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < 3; ++j) {
                rtk.ssat[i].resc[j] = 0.0;
                rtk.ssat[i].resp[j] = 0.0;
            }
        }
        for (i = 0; i < ns; ++i) {
            if (opt.ionoopt >= 4) {
                im[i] = (rtkPos.ionmapf(posu, Arrays.copyOfRange(azel, iu[i] * 2, iu[i] * 2 + 2)) + rtkPos.ionmapf(posr, Arrays.copyOfRange(azel, ir[i] * 2, ir[i] * 2 + 2))) / 2.0;
            }
            if (opt.tropopt < 3) continue;
            double[] tempdtdxu = Arrays.copyOfRange(dtdxu, i * 3, i * 3 + 3);
            double[] tempdtdxr = Arrays.copyOfRange(dtdxr, i * 3, i * 3 + 3);
            tropu[i] = rtkPos.prectrop(rtk.sol.time, posu, 0, Arrays.copyOfRange(azel, iu[i] * 2, iu[i] * 2 + 2), opt, x, tempdtdxu);
            tropr[i] = rtkPos.prectrop(rtk.sol.time, posr, 1, Arrays.copyOfRange(azel, ir[i] * 2, ir[i] * 2 + 2), opt, x, tempdtdxr);
            for (int k1 = 0; k1 < 3; ++k1) {
                dtdxu[i * 3 + k1] = tempdtdxu[k1];
                dtdxr[i * 3 + k1] = tempdtdxu[k1];
            }
        }
        for (int m = 0; m < 6; ++m) {
            int f;
            int n = f = opt.mode > 1 ? 0 : nf;
            while (f < nf * 2) {
                byte sysi;
                i = -1;
                for (j = 0; j < ns; ++j) {
                    sysi = rtk.ssat[sat[j] - 1].sys;
                    if (!rtkPos.test_sys(sysi, m) || (opt.mode != 1 ? !rtkPos.validobs(iu[j], ir[j], f, nf, y) : !rtkPos.dgps_validobs(iu[j], ir[j], f, nf, y))) continue;
                    if (i >= 0 && !(azel[1 + iu[j] * 2] >= azel[1 + iu[i] * 2])) continue;
                    i = j;
                }
                if (i >= 0) {
                    for (j = 0; j < ns; ++j) {
                        int k;
                        if (i == j) continue;
                        sysi = rtk.ssat[sat[i] - 1].sys;
                        byte sysj = rtk.ssat[sat[j] - 1].sys;
                        double freqi = freq[f % nf + iu[i] * nf];
                        double freqj = freq[f % nf + iu[j] * nf];
                        if (!rtkPos.test_sys(sysj, m) || (opt.mode != 1 ? !rtkPos.validobs(iu[j], ir[j], f, nf, y) : !rtkPos.dgps_validobs(iu[j], ir[j], f, nf, y))) continue;
                        if (H != null) {
                            Hi = H;
                            startIndex = nv * rtk.nx;
                            for (k = 0; k < rtk.nx; ++k) {
                                Hi[startIndex + k] = 0.0;
                            }
                        }
                        v[nv] = y[f + iu[i] * nf * 2] - y[f + ir[i] * nf * 2] - (y[f + iu[j] * nf * 2] - y[f + ir[j] * nf * 2]);
                        if (H != null) {
                            for (k = 0; k < 3; ++k) {
                                if (Hi == null) continue;
                                Hi[startIndex + k] = -e[k + iu[i] * 3] + e[k + iu[j] * 3];
                            }
                        }
                        if (opt.ionoopt == 4) {
                            didxi = (f < nf ? -1.0 : 1.0) * im[i] * util2.SQR(1.57542E9 / freqi);
                            didxj = (f < nf ? -1.0 : 1.0) * im[j] * util2.SQR(1.57542E9 / freqj);
                            int n2 = nv;
                            v[n2] = v[n2] - (didxi * x[udState.II(sat[i], opt)] - didxj * x[udState.II(sat[j], opt)]);
                            if (H != null) {
                                Hi[startIndex + udState.II((int)sat[i], (prcopt_t)opt)] = didxi;
                                Hi[startIndex + udState.II((int)sat[j], (prcopt_t)opt)] = -didxj;
                            }
                        }
                        if (opt.tropopt == 3 || opt.tropopt == 4) {
                            int n3 = nv;
                            v[n3] = v[n3] - (tropu[i] - tropu[j] - (tropr[i] - tropr[j]));
                            for (k = 0; k < (opt.tropopt < 4 ? 1 : 3); ++k) {
                                if (H == null) continue;
                                Hi[startIndex + udState.IT((int)0, (prcopt_t)opt) + k] = dtdxu[k + i * 3] - dtdxu[k + j * 3];
                                Hi[startIndex + udState.IT((int)1, (prcopt_t)opt) + k] = -(dtdxr[k + i * 3] - dtdxr[k + j * 3]);
                            }
                        }
                        if (f < nf) {
                            if (opt.ionoopt != 3) {
                                int n4 = nv;
                                v[n4] = v[n4] - (2.99792458E8 / freqi * x[udState.IB(sat[i], f, opt)] - 2.99792458E8 / freqj * x[udState.IB(sat[j], f, opt)]);
                                if (H != null) {
                                    Hi[startIndex + udState.IB((int)sat[i], (int)f, (prcopt_t)opt)] = 2.99792458E8 / freqi;
                                    Hi[startIndex + udState.IB((int)sat[j], (int)f, (prcopt_t)opt)] = -2.99792458E8 / freqj;
                                }
                            } else {
                                int n5 = nv;
                                v[n5] = v[n5] - (x[udState.IB(sat[i], f, opt)] - x[udState.IB(sat[j], f, opt)]);
                                if (H != null) {
                                    Hi[startIndex + udState.IB((int)sat[i], (int)f, (prcopt_t)opt)] = 1.0;
                                    Hi[startIndex + udState.IB((int)sat[j], (int)f, (prcopt_t)opt)] = -1.0;
                                }
                            }
                        }
                        if (f < nf) {
                            rtk.ssat[sat[j] - 1].resc[f] = v[nv];
                        } else {
                            rtk.ssat[sat[j] - 1].resp[f - nf] = v[nv];
                        }
                        if (opt.getMaxinnoPhase() > 0.0 && Math.abs(v[nv]) > opt.getMaxinnoPhase()) {
                            if (f < nf) {
                                int n6 = f;
                                rtk.ssat[sat[i] - 1].rejc[n6] = rtk.ssat[sat[i] - 1].rejc[n6] + 1L;
                                int n7 = f;
                                rtk.ssat[sat[j] - 1].rejc[n7] = rtk.ssat[sat[j] - 1].rejc[n7] + 1L;
                            }
                            System.out.println("outlier rejected");
                            continue;
                        }
                        Ri[nv] = rtkPos.varerr(sat[i], sysi, azel[1 + iu[i] * 2], bl, dt, f, 0.0, opt);
                        Rj[nv] = rtkPos.varerr(sat[j], sysj, azel[1 + iu[j] * 2], bl, dt, f, 0.0, opt);
                        if (opt.mode > 1) {
                            if (f < nf) {
                                rtk.ssat[sat[j] - 1].vsat[f] = 1;
                                rtk.ssat[sat[i] - 1].vsat[f] = 1;
                            }
                        } else {
                            rtk.ssat[sat[j] - 1].vsat[f - nf] = 1;
                            rtk.ssat[sat[i] - 1].vsat[f - nf] = 1;
                        }
                        vflg[nv++] = sat[i] << 16 | sat[j] << 8 | (f < nf ? 0 : 1) << 4 | f % nf;
                        int n8 = b;
                        nb[n8] = nb[n8] + 1;
                    }
                    ++b;
                }
                ++f;
            }
        }
        if (opt.mode == PMODE_MOVEB && rtkPos.constbl(rtk, x, P, v, H, Ri, Rj, nv) == 1) {
            vflg[nv++] = 48;
            int n = b++;
            nb[n] = nb[n] + 1;
        }
        rtkPos.ddcov(nb, b, Ri, Rj, nv, R);
        return nv;
    }

    public static int filter_(double[] x, double[] P, double[] H, double[] v, double[] R, int n, int m, double[] xp, double[] Pp) {
        double[] F = math.mat(n, m);
        double[] Q = math.mat(m, m);
        double[] K = math.mat(n, m);
        double[] I = math.eye(n);
        System.arraycopy(R, 0, Q, 0, m * m);
        System.arraycopy(x, 0, xp, 0, n);
        math.matmul("NN", n, m, n, 1.0, P, H, 0.0, F);
        math.matmul("TN", m, m, n, 1.0, H, F, 1.0, Q);
        int info = math.matinv(Q, m);
        if (info == 0) {
            math.matmul("NN", n, m, m, 1.0, F, Q, 0.0, K);
            math.matmul("NN", n, 1, m, 1.0, K, v, 1.0, xp);
            math.matmul("NT", n, n, m, -1.0, K, H, 1.0, I);
            math.matmul("NN", n, n, n, 1.0, I, P, 0.0, Pp);
        }
        return info;
    }

    public static int filter(double[] x, double[] P, double[] H, double[] v, double[] R, int n, int m) {
        int j;
        int i;
        int[] ix = math.imat(n, 1);
        int k = 0;
        for (i = 0; i < n; ++i) {
            if (x[i] == 0.0 || !(P[i + i * n] > 0.0)) continue;
            ix[k++] = i;
        }
        double[] x_ = math.mat(k, 1);
        double[] xp_ = math.mat(k, 1);
        double[] P_ = math.mat(k, k);
        double[] Pp_ = math.mat(k, k);
        double[] H_ = math.mat(k, m);
        for (i = 0; i < k; ++i) {
            x_[i] = x[ix[i]];
            for (j = 0; j < k; ++j) {
                P_[i + j * k] = P[ix[i] + ix[j] * n];
            }
            for (j = 0; j < m; ++j) {
                H_[i + j * k] = H[ix[i] + j * n];
            }
        }
        int info = rtkPos.filter_(x_, P_, H_, v, R, k, m, xp_, Pp_);
        for (i = 0; i < k; ++i) {
            x[ix[i]] = xp_[i];
            for (j = 0; j < k; ++j) {
                P[ix[i] + ix[j] * n] = Pp_[i + j * k];
            }
        }
        return info;
    }

    public static int valpos(rtk_t rtk, double[] v, double[] R, int[] vflg, int nv, double thres) {
        double fact = thres * thres;
        int stat = 1;
        for (int i = 0; i < nv; ++i) {
            if (v[i] * v[i] <= fact * R[i + i * nv]) continue;
            int sat1 = vflg[i] >> 16 & 0xFF;
            int sat2 = vflg[i] >> 8 & 0xFF;
            int type = vflg[i] >> 4 & 0xF;
            int freq = vflg[i] & 0xF;
            char[] stype = (type == 0 ? "L" : (type == 1 ? "L" : "C")).toCharArray();
            System.out.println("large residual");
        }
        return stat;
    }

    public static int rel_pos(rtk_t rtk, obsd_t[] obs, int nu, int nr, nav_t nav) {
        int nv;
        int ns;
        int j;
        int i;
        prcopt_t opt = rtk.opt;
        gtime_t time = obs[0].time;
        int n = nu + nr;
        int[] tempsat = new int[1];
        int[] sat = new int[nav_t.getMaxSat()];
        int[] iu = new int[nav_t.getMaxSat()];
        int[] ir = new int[nav_t.getMaxSat()];
        int[] vflg = new int[577];
        int[] svh = new int[192];
        int stat = rtk.opt.mode <= 1 ? 4 : 2;
        int nf = opt.ionoopt == 3 ? 1 : opt.getNf();
        double dt = util1.timediff(time, obs[nu].time);
        double[] rs = math.mat(6, n);
        double[] dts = math.mat(2, n);
        double[] var = math.mat(1, n);
        double[] y = math.mat(nf * 2, n);
        double[] e = math.mat(3, n);
        double[] azel = math.zeros(2, n);
        double[] freq = math.zeros(nf, n);
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            tempsat[0] = i + 1;
            rtk.ssat[i].sys = (byte)satSys.sat_sys(tempsat[0], null);
            for (j = 0; j < 3; ++j) {
                rtk.ssat[i].vsat[j] = 0;
            }
            for (j = 1; j < 3; ++j) {
                rtk.ssat[i].snr_rover[j] = 0;
            }
        }
        satposs.sat_poss(time, obs, n, nav, opt.getSateph(), rs, dts, var, svh);
        double[] tempy = Arrays.copyOfRange(y, nu * nf * 2, y.length);
        double[] tempe = Arrays.copyOfRange(e, nu * 3, e.length);
        double[] tempazel = Arrays.copyOfRange(azel, nu * 2, azel.length);
        double[] tempfreq = Arrays.copyOfRange(freq, nu * nf, freq.length);
        if (rtkPos.zdres(1, Arrays.copyOfRange(obs, nu, obs.length), nr, Arrays.copyOfRange(rs, nu * 6, rs.length), Arrays.copyOfRange(dts, nu * 2, dts.length), Arrays.copyOfRange(var, nu, var.length), Arrays.copyOfRange(svh, nu, svh.length), nav, rtk.rb, opt, 1, tempy, tempe, tempazel, tempfreq) == 0) {
            System.out.println("initial base station position error");
            return 0;
        }
        System.arraycopy(tempazel, 0, azel, nu * 2, tempazel.length);
        System.arraycopy(tempfreq, 0, freq, nu * nf, tempfreq.length);
        System.arraycopy(tempy, 0, y, nu * nf * 2, tempy.length);
        System.arraycopy(tempe, 0, e, nu * 3, tempe.length);
        if (opt.getIntpref() == 1) {
            dt = rtkPos.intpres(time, Arrays.copyOfRange(obs, nu, obs.length), nr, nav, rtk, Arrays.copyOfRange(y, nu * nf * 2, y.length));
        }
        if ((ns = rtkPos.selsat(obs, azel, nu, nr, opt, sat, iu, ir)) <= 0) {
            System.out.println("no common satellite");
            return 0;
        }
        udState.udstate(rtk, obs, sat, iu, ir, ns, nav);
        double[] xp = math.mat(rtk.nx, 1);
        double[] Pp = math.zeros(rtk.nx, rtk.nx);
        double[] xa = math.mat(rtk.nx, 1);
        System.arraycopy(rtk.x, 0, xp, 0, rtk.nx);
        int ny = ns * nf * 2 + 2;
        double[] v = math.mat(ny, 1);
        double[] H = math.zeros(rtk.nx, ny);
        double[] R = math.mat(ny, ny);
        double[] bias = math.mat(rtk.nx, 1);
        int niter = opt.getNiter() + (opt.mode == PMODE_MOVEB && opt.getBaseline()[0] > 0.0 ? 2 : 0);
        for (i = 0; i < niter; ++i) {
            if (rtkPos.zdres(0, obs, nu, rs, dts, var, svh, nav, xp, opt, 0, y, e, azel, freq) == 0) {
                System.out.println("rover initial position error");
                stat = 0;
                break;
            }
            nv = rtkPos.ddres(rtk, nav, dt, xp, Pp, sat, y, e, azel, freq, iu, ir, ns, v, H, R, vflg);
            if (nv < 1) {
                System.out.println("no double-differenced residual");
                stat = 0;
                break;
            }
            System.arraycopy(rtk.P, 0, Pp, 0, rtk.nx * rtk.nx);
            if (rtkPos.filter(xp, Pp, H, v, R, rtk.nx, nv) != 1) continue;
            System.out.println("filter error");
            stat = 0;
            break;
        }
        if (stat != 0 && rtkPos.zdres(0, obs, nu, rs, dts, var, svh, nav, xp, opt, 0, y, e, azel, freq) != 0) {
            nv = rtkPos.ddres(rtk, nav, dt, xp, Pp, sat, y, e, azel, freq, iu, ir, ns, v, null, R, vflg);
            if (rtkPos.valpos(rtk, v, R, vflg, nv, 4.0) == 1) {
                System.arraycopy(xp, 0, rtk.x, 0, rtk.nx);
                System.arraycopy(Pp, 0, rtk.P, 0, rtk.nx * rtk.nx);
                rtk.sol.ns = 0;
                for (i = 0; i < ns; ++i) {
                    for (int f = 0; f < nf; ++f) {
                        if (rtk.ssat[sat[i] - 1].vsat[f] == 0) continue;
                        int n2 = f;
                        rtk.ssat[sat[i] - 1].lock[n2] = rtk.ssat[sat[i] - 1].lock[n2] + 1;
                        rtk.ssat[sat[i] - 1].outc[f] = 0L;
                        if (f != 0) continue;
                        rtk.sol.ns = (byte)(rtk.sol.ns + 1);
                    }
                }
                if (rtk.sol.ns < 4) {
                    stat = 0;
                }
            } else {
                stat = 0;
            }
        }
        if (stat != 0 && rtkPos.resamb_LAMBDA(rtk, bias, xa) > 1 && rtkPos.zdres(0, obs, nu, rs, dts, var, svh, nav, xa, opt, 0, y, e, azel, freq) != 0 && rtkPos.valpos(rtk, v, R, vflg, nv = rtkPos.ddres(rtk, nav, dt, xa, null, sat, y, e, azel, freq, iu, ir, ns, v, null, R, vflg), 4.0) != 0) {
            if (++rtk.nfix >= rtk.opt.getMinfix() && (double)rtk.opt.getModear() == 3.0) {
                rtkPos.holdamb(rtk, xa);
            }
            stat = 1;
        }
        if (stat == 1) {
            for (i = 0; i < 3; ++i) {
                rtk.sol.rr[i] = rtk.xa[i];
                rtk.sol.qr[i] = (float)rtk.Pa[i + i * rtk.na];
            }
            rtk.sol.qr[3] = (float)rtk.Pa[1];
            rtk.sol.qr[4] = (float)rtk.Pa[1 + 2 * rtk.na];
            rtk.sol.qr[5] = (float)rtk.Pa[2];
            if (rtk.opt.getDynamics() != 0) {
                for (i = 3; i < 6; ++i) {
                    rtk.sol.rr[i] = rtk.xa[i];
                    rtk.sol.qv[i - 3] = (float)rtk.Pa[i + i * rtk.na];
                }
                rtk.sol.qv[3] = (float)rtk.Pa[4 + 3 * rtk.na];
                rtk.sol.qv[4] = (float)rtk.Pa[5 + 4 * rtk.na];
                rtk.sol.qv[5] = (float)rtk.Pa[5 + 3 * rtk.na];
            }
        } else {
            for (i = 0; i < 3; ++i) {
                rtk.sol.rr[i] = rtk.x[i];
                rtk.sol.qr[i] = (float)rtk.P[i + i * rtk.nx];
            }
            rtk.sol.qr[3] = (float)rtk.P[1];
            rtk.sol.qr[4] = (float)rtk.P[1 + 2 * rtk.nx];
            rtk.sol.qr[5] = (float)rtk.P[2];
            if (rtk.opt.getDynamics() == 1) {
                for (i = 3; i < 6; ++i) {
                    rtk.sol.rr[i] = rtk.x[i];
                    rtk.sol.qv[i - 3] = (float)rtk.P[i + i * rtk.nx];
                }
                rtk.sol.qv[3] = (float)rtk.P[4 + 3 * rtk.nx];
                rtk.sol.qv[4] = (float)rtk.P[5 + 4 * rtk.nx];
                rtk.sol.qv[5] = (float)rtk.P[5 + 3 * rtk.nx];
            }
            rtk.nfix = 0;
        }
        for (i = 0; i < n; ++i) {
            for (j = 0; j < nf; ++j) {
                if (obs[i].L[j] == 0.0) continue;
                rtk.ssat[obs[i].sat[0] - 1].pt[obs[i].rcv - 1][j] = obs[i].time;
                rtk.ssat[obs[i].sat[0] - 1].ph[obs[i].rcv - 1][j] = obs[i].L[j];
            }
        }
        for (i = 0; i < ns; ++i) {
            for (j = 0; j < nf; ++j) {
                rtk.ssat[sat[i] - 1].snr_rover[j] = obs[iu[i]].SNR[j];
            }
        }
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < nf; ++j) {
                if (rtk.ssat[i].fix[j] == 2 && stat != 1) {
                    rtk.ssat[i].fix[j] = 1;
                }
                if ((rtk.ssat[i].slip[j] & 1) != 1) continue;
                int n3 = j;
                rtk.ssat[i].slipc[n3] = rtk.ssat[i].slipc[n3] + 1L;
            }
        }
        if (stat != 0) {
            rtk.sol.stat = (byte)stat;
        }
        if (stat != 0) {
            return 1;
        }
        return 0;
    }

    private static void holdamb(rtk_t rtk, double[] xa) {
        int i;
        int nb = rtk.nx - rtk.na;
        int nv = 0;
        int nf = rtkPos.NF(rtk.opt);
        int[] index = new int[nav_t.getMaxSat()];
        double[] v = math.mat(nb, 1);
        double[] H = math.zeros(nb, rtk.nx);
        for (int m = 0; m < 5; ++m) {
            for (int f = 0; f < nf; ++f) {
                int n = 0;
                for (i = 0; i < nav_t.getMaxSat(); ++i) {
                    if (!rtkPos.test_sys(rtk.ssat[i].sys, m) || rtk.ssat[i].fix[f] != 2 || rtk.ssat[i].azel[1] < rtk.opt.getElmaskhold()) continue;
                    index[n++] = udState.IB(i + 1, f, rtk.opt);
                    rtk.ssat[i].fix[f] = 3;
                }
                for (i = 1; i < n; ++i) {
                    v[nv] = xa[index[0]] - xa[index[i]] - (rtk.x[index[0]] - rtk.x[index[i]]);
                    H[index[0] + nv * rtk.nx] = 1.0;
                    H[index[i] + nv * rtk.nx] = -1.0;
                    ++nv;
                }
            }
        }
        if (nv > 0) {
            double[] R = math.zeros(nv, nv);
            for (i = 0; i < nv; ++i) {
                R[i + i * nv] = 0.001;
            }
            int info = rtkPos.filter(rtk.x, rtk.P, H, v, R, rtk.nx, nv);
            if (info != 0) {
                // empty if block
            }
        }
    }

    private static int resamb_LAMBDA(rtk_t rtk, double[] bias, double[] xa) {
        int j;
        int i;
        prcopt_t opt = rtk.opt;
        int nx = rtk.nx;
        int na = rtk.na;
        double[] s = new double[2];
        rtk.sol.ratio = 0.0f;
        if (rtk.opt.mode <= 1 || rtk.opt.getModear() == 0 || rtk.opt.getThresar()[0] < 1.0) {
            return 0;
        }
        int[] ix = math.imat(nx, 2);
        int nb = rtkPos.ddidx(rtk, ix);
        if (nb <= 0) {
            return 0;
        }
        double[] y = math.mat(nb, 1);
        double[] DP = math.mat(nb, nx - na);
        double[] b = math.mat(nb, 2);
        double[] db = math.mat(nb, 1);
        double[] Qb = math.mat(nb, nb);
        double[] Qab = math.mat(na, nb);
        double[] QQ = math.mat(na, nb);
        for (i = 0; i < nb; ++i) {
            assert (ix != null);
            y[i] = rtk.x[ix[i * 2]] - rtk.x[ix[i * 2 + 1]];
        }
        for (j = 0; j < nx - na; ++j) {
            for (i = 0; i < nb; ++i) {
                DP[i + j * nb] = rtk.P[ix[i * 2] + (na + j) * nx] - rtk.P[ix[i * 2 + 1] + (na + j) * nx];
            }
        }
        for (j = 0; j < nb; ++j) {
            for (i = 0; i < nb; ++i) {
                Qb[i + j * nb] = DP[i + (ix[j * 2] - na) * nb] - DP[i + (ix[j * 2 + 1] - na) * nb];
            }
        }
        for (j = 0; j < nb; ++j) {
            for (i = 0; i < na; ++i) {
                Qab[i + j * na] = rtk.P[i + ix[j * 2] * nx] - rtk.P[i + ix[j * 2 + 1] * nx];
            }
        }
        if (rtkPos.lambda(nb, 2, y, Qb, b, s) == 0) {
            float f = rtk.sol.ratio = s[0] > 0.0 ? (float)(s[1] / s[0]) : 0.0f;
            if ((double)rtk.sol.ratio > 999.9) {
                rtk.sol.ratio = 999.9f;
            }
            if (s[0] <= 0.0 || s[1] / s[0] >= opt.getThresar()[0]) {
                for (i = 0; i < na; ++i) {
                    rtk.xa[i] = rtk.x[i];
                    for (j = 0; j < na; ++j) {
                        rtk.Pa[i + j * na] = rtk.P[i + j * nx];
                    }
                }
                for (i = 0; i < nb; ++i) {
                    bias[i] = b[i];
                    int n = i;
                    y[n] = y[n] - b[i];
                }
                if (math.matinv(Qb, nb) == 0) {
                    math.matmul("NN", nb, 1, nb, 1.0, Qb, y, 0.0, db);
                    math.matmul("NN", na, 1, nb, -1.0, Qab, db, 1.0, rtk.xa);
                    math.matmul("NN", na, nb, nb, 1.0, Qab, Qb, 0.0, QQ);
                    math.matmul("NT", na, na, nb, -1.0, QQ, Qab, 1.0, rtk.Pa);
                    rtkPos.restamb(rtk, bias, nb, xa);
                } else {
                    nb = 0;
                }
            } else {
                nb = 0;
            }
        } else {
            nb = 0;
        }
        return nb;
    }

    private static void restamb(rtk_t rtk, double[] bias, int nb, double[] xa) {
        int i;
        int nv = 0;
        int nf = rtkPos.NF(rtk.opt);
        int[] index = new int[nav_t.getMaxSat()];
        for (i = 0; i < rtk.nx; ++i) {
            xa[i] = rtk.x[i];
        }
        for (i = 0; i < rtk.na; ++i) {
            xa[i] = rtk.xa[i];
        }
        for (int m = 0; m < 5; ++m) {
            for (int f = 0; f < nf; ++f) {
                int n = 0;
                for (i = 0; i < nav_t.getMaxSat(); ++i) {
                    if (!rtkPos.test_sys(rtk.ssat[i].sys, m) || rtk.ssat[i].fix[f] != 2) continue;
                    index[n++] = udState.IB(i + 1, f, rtk.opt);
                }
                if (n < 2) continue;
                xa[index[0]] = rtk.x[index[0]];
                for (i = 1; i < n; ++i) {
                    xa[index[i]] = xa[index[0]] - bias[nv++];
                }
            }
        }
    }

    private static int lambda(int n, int m, double[] a, double[] Q, double[] F, double[] s) {
        char[] temp = new char[2];
        temp[0] = 84;
        if (n <= 0 || m <= 0) {
            return -1;
        }
        double[] L = math.zeros(n, n);
        double[] D = math.mat(n, 1);
        double[] Z = math.eye(n);
        double[] z = math.mat(n, 1);
        double[] E = math.mat(n, m);
        int info = rtkPos.LD(n, Q, L, D);
        if (info == 0) {
            rtkPos.reduction(n, L, D, Z);
            math.matmul("TN", n, 1, n, 1.0, Z, a, 0.0, z);
            info = rtkPos.search(n, m, L, D, z, E, s);
            if (info == 0) {
                info = rtkPos.solve(temp, Z, E, n, m, F);
            }
        }
        return info;
    }

    private static int solve(char[] tr, double[] A, double[] Y, int n, int m, double[] X) {
        double[] B = math.mat(n, n);
        math.matcpy(B, A, n, n);
        int info = math.matinv(B, n);
        if (info == 0) {
            math.matmul(tr[0] == 'N' ? "NN" : "TN", n, m, n, 1.0, B, Y, 0.0, X);
        }
        return info;
    }

    private static int search(int n, int m, double[] L, double[] D, double[] zs, double[] zn, double[] s) {
        int i;
        int c;
        int nn = 0;
        int imax = 0;
        double maxdist = 1.0E99;
        double[] S = math.zeros(n, n);
        double[] dist = math.mat(n, 1);
        double[] zb = math.mat(n, 1);
        double[] z = math.mat(n, 1);
        double[] step = math.mat(n, 1);
        int k = n - 1;
        dist[k] = 0.0;
        zb[k] = zs[k];
        z[k] = rtkPos.ROUND(zb[k]);
        double y = zb[k] - z[k];
        step[k] = rtkPos.SGN(y);
        for (c = 0; c < 10000; ++c) {
            double newdist = dist[k] + y * y / D[k];
            if (newdist < maxdist) {
                if (k != 0) {
                    dist[--k] = newdist;
                    for (i = 0; i <= k; ++i) {
                        S[k + i * n] = S[k + 1 + i * n] + (z[k + 1] - zb[k + 1]) * L[k + 1 + i * n];
                    }
                    zb[k] = zs[k] + S[k + k * n];
                    z[k] = rtkPos.ROUND(zb[k]);
                    y = zb[k] - z[k];
                    step[k] = rtkPos.SGN(y);
                    continue;
                }
                if (nn < m) {
                    if (nn == 0 || newdist > s[imax]) {
                        imax = nn;
                    }
                    for (i = 0; i < n; ++i) {
                        zn[i + nn * n] = z[i];
                    }
                    s[nn++] = newdist;
                } else {
                    if (newdist < s[imax]) {
                        for (i = 0; i < n; ++i) {
                            zn[i + imax * n] = z[i];
                        }
                        s[imax] = newdist;
                        imax = 0;
                        for (i = 0; i < m; ++i) {
                            if (!(s[imax] < s[i])) continue;
                            imax = i;
                        }
                    }
                    maxdist = s[imax];
                }
                z[0] = z[0] + step[0];
                y = zb[0] - z[0];
                step[0] = -step[0] - rtkPos.SGN(step[0]);
                continue;
            }
            if (k == n - 1) break;
            int n2 = ++k;
            z[n2] = z[n2] + step[k];
            y = zb[k] - z[k];
            step[k] = -step[k] - rtkPos.SGN(step[k]);
        }
        for (i = 0; i < m - 1; ++i) {
            for (int j = i + 1; j < m; ++j) {
                if (s[i] < s[j]) continue;
                rtkPos.SWAP(s, i, j);
                for (k = 0; k < n; ++k) {
                    rtkPos.SWAP(zn, k + i * n, k + j * n);
                }
            }
        }
        if (c >= 10000) {
            return -1;
        }
        return 0;
    }

    private static double SGN(double x) {
        return x <= 0.0 ? -1.0 : 1.0;
    }

    private static void reduction(int n, double[] L, double[] D, double[] Z) {
        int j = n - 2;
        int k = n - 2;
        while (j >= 0) {
            double del;
            if (j <= k) {
                for (int i = j + 1; i < n; ++i) {
                    rtkPos.gauss(n, L, Z, i, j);
                }
            }
            if ((del = D[j] + L[j + 1 + j * n] * L[j + 1 + j * n] * D[j + 1]) + 1.0E-6 < D[j + 1]) {
                rtkPos.perm(n, L, D, j, del, Z);
                k = j;
                j = n - 2;
                continue;
            }
            --j;
        }
    }

    private static void perm(int n, double[] L, double[] D, int j, double del, double[] Z) {
        int k;
        double eta = D[j] / del;
        double lam = D[j + 1] * L[j + 1 + j * n] / del;
        D[j] = eta * D[j + 1];
        D[j + 1] = del;
        for (k = 0; k <= j - 1; ++k) {
            double a0 = L[j + k * n];
            double a1 = L[j + 1 + k * n];
            L[j + k * n] = -L[j + 1 + j * n] * a0 + a1;
            L[j + 1 + k * n] = eta * a0 + lam * a1;
        }
        L[j + 1 + j * n] = lam;
        for (k = j + 2; k < n; ++k) {
            rtkPos.SWAP(L, k + j * n, k + (j + 1) * n);
        }
        for (k = 0; k < n; ++k) {
            rtkPos.SWAP(Z, k + j * n, k + (j + 1) * n);
        }
    }

    private static void SWAP(double[] array, int index1, int index2) {
        double tmp = array[index1];
        array[index1] = array[index2];
        array[index2] = tmp;
    }

    private static void gauss(int n, double[] L, double[] Z, int i, int j) {
        int mu = (int)rtkPos.ROUND(L[i + j * n]);
        if (mu != 0) {
            int k;
            for (k = i; k < n; ++k) {
                int n2 = k + n * j;
                L[n2] = L[n2] - (double)mu * L[k + i * n];
            }
            for (k = 0; k < n; ++k) {
                int n3 = k + n * j;
                Z[n3] = Z[n3] - (double)mu * Z[k + i * n];
            }
        }
    }

    public static double ROUND(double x) {
        return Math.floor(x + 0.5);
    }

    private static int LD(int n, double[] Q, double[] L, double[] D) {
        int info = 0;
        double[] A = math.mat(n, n);
        System.arraycopy(Q, 0, A, 0, n * n);
        for (int i = n - 1; i >= 0; --i) {
            int j;
            double d;
            D[i] = A[i + i * n];
            if (D[i] <= 0.0) {
                info = -1;
                break;
            }
            double a = Math.sqrt(D[i]);
            for (j = 0; j <= i; ++j) {
                L[i + j * n] = A[i + j * n] / a;
            }
            for (j = 0; j <= i - 1; ++j) {
                for (int k = 0; k <= j; ++k) {
                    int n2 = j + k * n;
                    A[n2] = A[n2] - L[i + k * n] * L[i + j * n];
                }
            }
            for (j = 0; j <= i; ++j) {
                int n3 = i + j * n;
                L[n3] = L[n3] / L[i + i * n];
            }
        }
        return info;
    }

    private static int ddidx(rtk_t rtk, int[] ix) {
        int j;
        int i;
        int nb = 0;
        int na = rtk.na;
        int nf = rtkPos.NF(rtk.opt);
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            for (j = 0; j < 3; ++j) {
                rtk.ssat[i].fix[j] = 0;
            }
        }
        for (int m = 0; m < 6; ++m) {
            boolean nofix = m == 1 && rtk.opt.glomodear == 0 || m == 3 && rtk.opt.getBdsmodear() == 0;
            int f = 0;
            int k = na;
            while (f < nf) {
                for (i = k; i < k + nav_t.getMaxSat(); ++i) {
                    if (rtk.x[i] == 0.0 || !rtkPos.test_sys(rtk.ssat[i - k].sys, m) || rtk.ssat[i - k].vsat[f] == 0 || rtk.ssat[i - k].half[f] == 0) continue;
                    if (rtk.ssat[i - k].lock[f] > 0 && (rtk.ssat[i - k].slip[f] & 2) == 0 && rtk.ssat[i - k].azel[1] >= rtk.opt.getElmaskar() && !nofix) {
                        rtk.ssat[i - k].fix[f] = 2;
                        break;
                    }
                    rtk.ssat[i - k].fix[f] = 1;
                }
                for (j = k; j < k + nav_t.getMaxSat(); ++j) {
                    if (i == j || rtk.x[j] == 0.0 || !rtkPos.test_sys(rtk.ssat[j - k].sys, m) || rtk.ssat[j - k].vsat[f] == 0) continue;
                    if (rtk.ssat[j - k].lock[f] > 0 && (rtk.ssat[j - k].slip[f] & 2) == 0 && rtk.ssat[i - k].vsat[f] != 0 && rtk.ssat[j - k].azel[1] >= rtk.opt.getElmaskar() && !nofix) {
                        ix[nb * 2] = i;
                        ix[nb * 2 + 1] = j;
                        ++nb;
                        rtk.ssat[j - k].fix[f] = 2;
                        continue;
                    }
                    rtk.ssat[j - k].fix[f] = 1;
                }
                ++f;
                k += nav_t.getMaxSat();
            }
        }
        return nb;
    }

    public static double interpvar(double ang, double[] var) {
        double a = ang / 5.0;
        int i = (int)a;
        if (i < 0) {
            return var[0];
        }
        if (i >= 18) {
            return var[18];
        }
        return var[i] * (1.0 - a + (double)i) + var[i + 1] * (a - (double)i);
    }

    public static void antmodel(pcv_t[] pcv, double[] del, double[] azel, int opt, double[] dant) {
        double[] e = new double[3];
        double[] off = new double[3];
        double cosel = Math.cos(azel[1]);
        e[0] = Math.sin(azel[0]) * cosel;
        e[1] = Math.cos(azel[0]) * cosel;
        e[2] = Math.sin(azel[1]);
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                off[j] = pcv[i].off[i][j] + del[j];
            }
            dant[i] = -math.dot(off, e, 3) + (opt == 1 ? rtkPos.interpvar(90.0 - azel[1] * 57.29577951308232, pcv[i].var[i]) : 0.0);
        }
    }

    public static void zdres_sat(int base, double r, obsd_t obs, nav_t nav, double[] azel, double[] dant, prcopt_t opt, double[] y, double[] freq) {
        int nf = rtkPos.NF(opt);
        if (opt.ionoopt == 3) {
            double freq1 = rescode.sat2freq(obs.sat, obs.code[0], nav);
            double freq2 = rescode.sat2freq(obs.sat, obs.code[1], nav);
            if (freq1 == 0.0 || freq2 == 0.0) {
                return;
            }
            if (rescode.testsnr(base, 0, azel[1], obs.SNR[0], opt.getSnrmask()) || rescode.testsnr(base, 1, azel[1], obs.SNR[1], opt.getSnrmask())) {
                return;
            }
            double C1 = util2.SQR(freq1) / (util2.SQR(freq1) - util2.SQR(freq2));
            double C2 = -util2.SQR(freq2) / (util2.SQR(freq1) - util2.SQR(freq2));
            double dant_if = C1 * dant[0] + C2 * dant[1];
            if (obs.L[0] != 0.0 && obs.L[1] != 0.0) {
                y[0] = C1 * obs.L[0] * 2.99792458E8 / freq1 + C2 * obs.L[1] * 2.99792458E8 / freq2 - r - dant_if;
            }
            if (obs.P[0] != 0.0 & obs.P[1] != 0.0) {
                y[1] = C1 * obs.P[0] + C2 * obs.P[1] - r - dant_if;
            }
            freq[0] = 1.0;
        } else {
            for (int i = 0; i < nf; ++i) {
                freq[i] = rescode.sat2freq(obs.sat, obs.code[i], nav);
                if (freq[i] == 0.0 || rescode.testsnr(base, i, azel[1], obs.SNR[i], opt.getSnrmask())) continue;
                if (obs.L[i] != 0.0) {
                    y[i] = obs.L[i] * 2.99792458E8 / freq[i] - r - dant[i];
                }
                if (obs.P[i] == 0.0) continue;
                y[i + nf] = obs.P[i] - r - dant[i];
            }
        }
    }

    public static int zdres(int base, obsd_t[] obs, int n, double[] rs, double[] dts, double[] var, int[] svh, nav_t nav, double[] rr, prcopt_t opt, int index, double[] y, double[] e, double[] azel, double[] freq) {
        int i;
        double[] rr_ = new double[3];
        double[] pos = new double[3];
        double[] dant = new double[3];
        double[] disp = new double[3];
        double[] zazel = new double[]{0.0, 1.5707963267948966};
        int nf = rtkPos.NF(opt);
        for (i = 0; i < n * nf * 2; ++i) {
            y[i] = 0.0;
        }
        if (math.norm(rr, 3) <= 0.0) {
            return 0;
        }
        for (i = 0; i < 3; ++i) {
            rr_[i] = rr[i];
        }
        if (opt.getTidecorr() != 0) {
            for (i = 0; i < 3; ++i) {
                int n2 = i;
                rr_[n2] = rr_[n2] + disp[i];
            }
        }
        rescode.ecef2pos(rr_, pos);
        for (i = 0; i < n; ++i) {
            double d;
            double[] tempe = Arrays.copyOfRange(e, i * 3, i * 3 + 3);
            double r = rescode.geodist(Arrays.copyOfRange(rs, i * 6, i * 6 + 6), rr_, tempe);
            if (r <= 0.0) {
                e[i * 3] = tempe[0];
                e[i * 3 + 1] = tempe[1];
                e[i * 3 + 2] = tempe[2];
                continue;
            }
            e[i * 3] = tempe[0];
            e[i * 3 + 1] = tempe[1];
            e[i * 3 + 2] = tempe[2];
            double[] tempazel = Arrays.copyOfRange(azel, i * 2, i * 2 + 2);
            if (rescode.satazel(pos, Arrays.copyOfRange(e, i * 3, i * 3 + 3), tempazel) < opt.getElmin()) {
                azel[i * 2] = tempazel[0];
                azel[i * 2 + 1] = tempazel[1];
                continue;
            }
            azel[i * 2] = tempazel[0];
            azel[i * 2 + 1] = tempazel[1];
            if (rescode.satexclude(obs[i].sat, var[i], svh[i], opt) == 1) continue;
            r += -2.99792458E8 * dts[i * 2];
            double zhd = rescode.tropmodel(obs[0].time, pos, zazel, 0.0);
            double[] tempy = Arrays.copyOfRange(y, i * nf * 2, i * nf * 2 + nf * 2);
            double[] tempfreq = Arrays.copyOfRange(freq, i * nf, i * nf + nf);
            rtkPos.zdres_sat(base, r += rescode.tropmapf(obs[i].time, pos, Arrays.copyOfRange(azel, i * 2, i * 2 + 2), null) * zhd, obs[i], nav, Arrays.copyOfRange(azel, i * 2, i * 2 + 2), dant, opt, tempy, tempfreq);
            if (nf * 2 >= 0) {
                System.arraycopy(tempy, 0, y, i * nf * 2, nf * 2);
            }
            if (i * nf < 0) continue;
            System.arraycopy(tempfreq, 0, freq, i * nf, nf);
        }
        return 1;
    }
}

