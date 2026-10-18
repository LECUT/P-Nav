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
import com.rtklib.bean.ssat_t;
import com.rtklib.pntpos.estpos;
import com.rtklib.pntpos.estvel;
import com.rtklib.pntpos.raimfde;
import com.rtklib.pntpos.satposs;
import com.rtklib.util.math;
import com.rtklib.util.util2;

public class pntPos {
    public static final int SOLQ_NONE = 0;
    public static final int IONOOPT_OFF = 0;
    public static final int IONOOPT_BRDC = 1;
    public static final int PMODE_SINGLE = 0;
    public static final int TROPOPT_SAAS = 1;

    public static int pnt_pos(obsd_t[] obs, int n, nav_t nav, prcopt_t opt, sol_t sol, double[] azel, ssat_t[] ssat, String msg) {
        int i;
        prcopt_t opt_ = new prcopt_t(opt);
        int[] vsat = new int[96];
        int[] svh = new int[96];
        sol.stat = 0;
        if (n == 0) {
            util2.strcpy(msg, "no observation data");
            return 0;
        }
        double[] rs = math.mat(6, n);
        double[] dts = math.mat(2, n);
        double[] var = math.mat(1, n);
        double[] azel_ = math.zeros(2, n);
        double[] resp = math.mat(1, n);
        sol.time = obs[0].time;
        if (opt_.getMode() != 0) {
            opt_.ionoopt = 1;
            opt_.tropopt = 1;
        }
        satposs.sat_poss(sol.time, obs, n, nav, opt_.getSateph(), rs, dts, var, svh);
        int stat = estpos.est_pos(obs, n, rs, dts, var, svh, nav, opt_, sol, azel_, vsat, resp, msg);
        if (stat == 0 && n >= 6 && opt.getPosopt()[4] == 1) {
            stat = raimfde.raim_fde(obs, n, rs, dts, var, svh, nav, opt_, sol, azel_, vsat, resp, msg);
        }
        if (stat != 0) {
            estvel.est_vel(obs, n, rs, dts, nav, opt_, sol, azel_, vsat);
        }
        if (azel != null) {
            for (i = 0; i < n * 2; ++i) {
                azel[i] = azel_[i];
            }
        }
        if (ssat != null) {
            for (i = 0; i < nav_t.getMaxSat(); ++i) {
                ssat[i].vs = 0;
                ssat[i].azel[1] = 0.0;
                ssat[i].azel[0] = 0.0;
                ssat[i].resc[0] = 0.0;
                ssat[i].resp[0] = 0.0;
                ssat[i].snr_rover[0] = 0;
            }
            for (i = 0; i < n; ++i) {
                ssat[obs[i].sat[0] - 1].azel[0] = azel_[i * 2];
                ssat[obs[i].sat[0] - 1].azel[1] = azel_[i * 2 + 1];
                ssat[obs[i].sat[0] - 1].snr_rover[0] = obs[i].SNR[0];
                if (vsat[i] == 0) continue;
                ssat[obs[i].sat[0] - 1].vs = 1;
                ssat[obs[i].sat[0] - 1].resp[0] = resp[i];
            }
        }
        return stat;
    }
}

