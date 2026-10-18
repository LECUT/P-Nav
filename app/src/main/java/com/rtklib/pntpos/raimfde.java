/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.pntpos;

import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sol_t;
import com.rtklib.pntpos.estpos;
import com.rtklib.util.math;
import com.rtklib.util.util2;
import java.util.Arrays;
import jdk.internal.org.jline.utils.Log;

public class raimfde {
    public static int raim_fde(obsd_t[] obs, int n, double[] rs, double[] dts, double[] vare, int[] svh, nav_t nav, prcopt_t opt, sol_t sol, double[] azel, int[] vsat, double[] resp, String msg) {
        obsd_t[] obs_e = new obsd_t[n];
        sol_t sol_e = null;
        char[] tstr = new char[32];
        char[] name = new char[16];
        char[] msg_e = new char[128];
        double rms = 100.0;
        int stat = 0;
        int sat = 0;
        double[] rs_e = math.mat(6, n);
        double[] dts_e = math.mat(2, n);
        double[] vare_e = math.mat(1, n);
        double[] azel_e = math.zeros(2, n);
        int[] svh_e = math.imat(1, n);
        int[] vsat_e = math.imat(1, n);
        double[] resp_e = math.mat(1, n);
        for (int i = 0; i < n; ++i) {
            int j;
            int k = 0;
            for (j = 0; j < n; ++j) {
                if (j == i) continue;
                obs_e[k] = obs[j];
                System.arraycopy(rs, 6 * j, rs_e, 6 * k, 6);
                System.arraycopy(dts, 2 * j, dts_e, 2 * k, 2);
                vare_e[k] = vare[j];
                svh_e[k++] = svh[j];
            }
            if (estpos.est_pos(obs_e, n - 1, rs_e, dts_e, vare_e, svh_e, nav, opt, sol_e, azel_e, vsat_e, resp_e, Arrays.toString(msg_e)) == 0) {
                Log.trace((Object[])new Object[]{3, "raim_fde: exsat=%2d (%s)\n", obs[i].sat, msg});
                continue;
            }
            int nvsat = 0;
            double rms_e = 0.0;
            for (j = 0; j < n - 1; ++j) {
                if (vsat_e[j] == 0) continue;
                rms_e += util2.SQR(resp_e[j]);
                ++nvsat;
            }
            if (nvsat < 5) {
                Log.trace((Object[])new Object[]{3, "raim_fde: exsat=%2d lack of satellites nvsat=%2d\n", obs[i].sat, nvsat});
                continue;
            }
            rms_e = Math.sqrt(rms_e / (double)nvsat);
            Log.trace((Object[])new Object[]{3, "raim_fde: exsat=%2d rms=%8.3f\n", obs[i].sat, rms_e});
            if (rms_e > rms) continue;
            k = 0;
            for (j = 0; j < n; ++j) {
                if (j == i) continue;
                System.arraycopy(azel, 2 * j, azel_e, 2 * k, 2);
                vsat[j] = vsat_e[k];
                resp[j] = resp_e[k++];
            }
            stat = 1;
            sol = sol_e;
            sat = obs[i].sat[0];
            rms = rms_e;
            vsat[i] = 0;
            util2.strcpy(msg, Arrays.toString(msg_e));
        }
        return stat;
    }
}

