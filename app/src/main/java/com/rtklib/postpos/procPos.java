/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.postpos;

import com.rtklib.bean.ambc_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.rtk_t;
import com.rtklib.bean.sbs_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.bean.ssat_t;
import com.rtklib.postpos.outSol;
import com.rtklib.postpos.postpos;
import com.rtklib.readobsnav.readObsNav;
import com.rtklib.readobsnav.satSys;
import com.rtklib.rtkpos.ppPos;
import com.rtklib.rtkpos.rtkPos;
import com.rtklib.rtkpos.udState;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import java.io.BufferedWriter;
import java.io.IOException;

public class procPos {
    static double[] rbf = new double[6];
    static double[] rbb;
    private static int isolf;
    private static int isolb;
    static sol_t[] solf;
    static sol_t[] solb;
    public static final int MAXERRMSG = 4096;
    public static int[] iobsu;
    public static int[] iobsr;
    public static int revs;
    public static int isbs;
    static sbs_t sbss;

    public static int pppnx(prcopt_t opt) {
        return ppPos.PNX(opt);
    }

    private static void rtkinit(rtk_t rtk, prcopt_t opt) {
        int i;
        rtk.sol = new sol_t();
        for (i = 0; i < 6; ++i) {
            rtk.rb[i] = 0.0;
        }
        rtk.nx = opt.mode <= 5 ? udState.NX(opt) : procPos.pppnx(opt);
        rtk.na = opt.mode <= 5 ? udState.NR(opt) : procPos.pppnx(opt);
        rtk.tt = 0.0;
        rtk.x = math.zeros(rtk.nx, 1);
        rtk.P = math.zeros(rtk.nx, rtk.nx);
        rtk.xa = math.zeros(rtk.na, 1);
        rtk.Pa = math.zeros(rtk.na, rtk.na);
        rtk.neb = 0;
        rtk.nfix = 0;
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            rtk.ambc[i] = new ambc_t();
            rtk.ssat[i] = new ssat_t();
            for (int k = 0; k < 2; ++k) {
                for (int j = 0; j < 3; ++j) {
                    rtk.ssat[i].pt[k][j] = new gtime_t(0.0, 0.0);
                }
            }
        }
        for (i = 0; i < 4096; ++i) {
            rtk.errbuf[i] = '\u0000';
        }
        rtk.opt = opt;
    }

    public static int inputobs(obsd_t[] obs, int solq, prcopt_t popt) {
        gtime_t time = new gtime_t();
        int n = 0;
        int[] i = new int[1];
        if (0 <= iobsu[0] && iobsu[0] < postpos.obss.n) {
            time = postpos.obss.data[procPos.iobsu[0]].time;
        }
        if (revs == 0) {
            int nr;
            int nu = util1.nextobsf(postpos.obss, iobsu, 1);
            if (nu <= 0) {
                return -1;
            }
            if (popt.getIntpref() == 1) {
                while ((nr = util1.nextobsf(postpos.obss, iobsr, 2)) > 0 && !(util1.timediff(postpos.obss.data[procPos.iobsr[0]].time, postpos.obss.data[procPos.iobsu[0]].time) > -0.025)) {
                    iobsr[0] = iobsr[0] + nr;
                }
            } else {
                i[0] = iobsr[0];
                while ((nr = util1.nextobsf(postpos.obss, i, 2)) > 0 && !(util1.timediff(postpos.obss.data[i[0]].time, postpos.obss.data[procPos.iobsu[0]].time) > 0.025)) {
                    procPos.iobsr[0] = i[0];
                    i[0] = i[0] + nr;
                }
            }
            if ((nr = util1.nextobsf(postpos.obss, iobsr, 2)) <= 0) {
                nr = util1.nextobsf(postpos.obss, iobsr, 2);
            }
            i[0] = 0;
            while (i[0] < nu && n < 192) {
                obs[n++] = postpos.obss.data[iobsu[0] + i[0]];
                i[0] = i[0] + 1;
            }
            i[0] = 0;
            while (i[0] < nr && n < 192) {
                obs[n++] = postpos.obss.data[iobsr[0] + i[0]];
                i[0] = i[0] + 1;
            }
            iobsu[0] = iobsu[0] + nu;
        }
        return n;
    }

    public static void procpos(BufferedWriter fp, prcopt_t popt, solopt_t sopt, int mode) throws IOException {
        int nobs;
        gtime_t time = new gtime_t();
        sol_t sol = new sol_t();
        rtk_t rtk = new rtk_t();
        obsd_t[] obs = new obsd_t[192];
        double[] rb = new double[3];
        int[] pri = new int[]{6, 1, 2, 3, 4, 5, 1, 6};
        boolean solstatic = sopt.solstatic == 1 && (popt.mode == 3 || popt.mode == 7);
        procPos.rtkinit(rtk, popt);
        while ((nobs = procPos.inputobs(obs, rtk.sol.stat, popt)) >= 0) {
            int i;
            int n = 0;
            for (i = 0; i < nobs; ++i) {
                if ((satSys.sat_sys(obs[i].sat[0], null) & popt.navsys) == 0 || popt.exsats[obs[i].sat[0] - 1] == 1) continue;
                obs[n++] = obs[i];
            }
            if (n <= 0 || rtkPos.rtk_pos(rtk, obs, n, postpos.navs) == 0) continue;
            if (mode == 0) {
                if (!solstatic) {
                    outSol.outsol(fp, rtk.sol, rtk.rb, sopt);
                    continue;
                }
                if (time.time != 0.0 && pri[rtk.sol.stat] > pri[sol.stat]) continue;
                sol = rtk.sol;
                for (i = 0; i < 3; ++i) {
                    rb[i] = rtk.rb[i];
                }
                if (time.time != 0.0 && !(util1.timediff(rtk.sol.time, time) < 0.0)) continue;
                time = rtk.sol.time;
                continue;
            }
            if (revs == 0) {
                if (isolf >= readObsNav.nepoch) {
                    return;
                }
                procPos.solf[procPos.isolf] = rtk.sol;
                for (i = 0; i < 3; ++i) {
                    procPos.rbf[i + procPos.isolf * 3] = rtk.rb[i];
                }
                ++isolf;
                continue;
            }
            if (isolb >= readObsNav.nepoch) {
                return;
            }
            procPos.solb[procPos.isolb] = rtk.sol;
            for (i = 0; i < 3; ++i) {
                procPos.rbb[i + procPos.isolb * 3] = rtk.rb[i];
            }
            ++isolb;
        }
        if (mode == 0 && solstatic && time.time != 0.0) {
            sol.time = time;
            outSol.outsol(fp, sol, rb, sopt);
        }
    }

    static {
        isolf = 0;
        isolb = 0;
        iobsu = new int[1];
        iobsr = new int[1];
        revs = 0;
        isbs = 0;
        sbss = new sbs_t();
    }
}

