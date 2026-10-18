/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.readRnxt;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.io.IOException;
import jdk.internal.org.jline.utils.Log;

public class readObsNav {
    public static int nepoch = 0;

    public static int read_obsnav(gtime_t ts, gtime_t te, double ti, String[] infile, int[] index, int n, prcopt_t prcopt, obs_t obs, nav_t nav, sta_t[] sta) throws IOException, CloneNotSupportedException {
        int i;
        int ind = 0;
        int nobs = 0;
        int rcv = 1;
        obs.data = null;
        obs.setN(0);
        obs.setNmax(0);
        nav.eph = null;
        nav.nmax = 0;
        nav.n = 0;
        nav.geph = null;
        nav.ngmax = 0;
        nav.ng = 0;
        nav.seph = null;
        nav.nsmax = 0;
        nav.ns = 0;
        nepoch = 0;
        for (i = 0; i < n; ++i) {
            if (index[i] != ind) {
                if (obs.getN() > nobs) {
                    ++rcv;
                }
                ind = index[i];
                nobs = obs.getN();
            }
            if (readRnxt.read_rnxt(infile[i], rcv, ts, te, ti, prcopt.getRnxopt()[rcv <= 1 ? 0 : 1], obs, nav, rcv <= 2 ? sta[rcv - 1] : null) >= 0) continue;
            util2.checkbrk("error : insufficient memory", new Object[0]);
            Log.trace((Object[])new Object[]{1, "insufficient memory\n"});
            return 0;
        }
        if (obs.getN() <= 0) {
            util2.checkbrk("error : no obs data", new Object[0]);
            return 0;
        }
        if (nav.n <= 0 && nav.ng <= 0 && nav.ns <= 0) {
            util2.checkbrk("error : no nav data", new Object[0]);
            return 0;
        }
        nepoch = util1.sortobs(obs);
        if (ts.time == 0.0 || te.time == 0.0) {
            int j;
            for (i = 0; i < obs.n && obs.data[i].rcv != 1; ++i) {
            }
            for (j = obs.n - 1; j >= 0 && obs.data[j].rcv != 1; --j) {
            }
            if (i < j) {
                if (ts.time == 0.0) {
                    ts = obs.data[i].time;
                }
                if (te.time == 0.0) {
                    te = obs.data[j].time;
                }
            }
        }
        return 1;
    }
}

