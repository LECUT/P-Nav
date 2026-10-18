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
import com.rtklib.pntpos.rescode;
import com.rtklib.util.math;

public class estvel {
    public static void est_vel(obsd_t[] obs, int n, double[] rs, double[] dts, nav_t nav, prcopt_t opt, sol_t sol, double[] azel, int[] vsat) {
        int nv;
        double[] x = new double[4];
        double[] dx = new double[4];
        double[] Q = new double[16];
        double err = opt.getErr()[5];
        double[] v = math.mat(n, 1);
        double[] H = math.mat(4, n);
        for (int i = 0; i < 10 && (nv = estvel.resdop(obs, n, rs, dts, nav, sol.rr, x, azel, vsat, err, v, H)) >= 4 && math.lsq(H, v, 4, nv, dx, Q) == 0; ++i) {
            for (int j = 0; j < 4; ++j) {
                int n2 = j;
                x[n2] = x[n2] + dx[j];
            }
            if (!(math.norm(dx, 4) < 1.0E-6)) continue;
            System.arraycopy(x, 0, sol.rr, 3, 3);
            sol.qv[0] = (float)Q[0];
            sol.qv[1] = (float)Q[5];
            sol.qv[2] = (float)Q[10];
            sol.qv[3] = (float)Q[1];
            sol.qv[4] = (float)Q[6];
            sol.qv[5] = (float)Q[2];
            break;
        }
    }

    public static int resdop(obsd_t[] obs, int n, double[] rs, double[] dts, nav_t nav, double[] rr, double[] x, double[] azel, int[] vsat, double err, double[] v, double[] H) {
        int i;
        int nv = 0;
        double[] pos = new double[3];
        double[] E = new double[9];
        double[] a = new double[3];
        double[] e = new double[3];
        double[] vs = new double[3];
        rescode.ecef2pos(rr, pos);
        rescode.xyz2enu(pos, E);
        double[] temp_rs = new double[n];
        for (i = 0; i < n && i < 96; ++i) {
            temp_rs[i] = rs[3 + i * 6];
        }
        for (i = 0; i < n && i < 96; ++i) {
            int j;
            double freq = rescode.sat2freq(obs[i].sat, obs[i].code[0], nav);
            if ((double)obs[i].D[0] == 0.0 || freq == 0.0 || vsat[i] == 0 || math.norm(temp_rs, 3) <= 0.0) continue;
            double cosel = Math.cos(azel[1 + i * 2]);
            a[0] = Math.sin(azel[i * 2]) * cosel;
            a[1] = Math.cos(azel[i * 2]) * cosel;
            a[2] = Math.sin(azel[1 + i * 2]);
            math.matmul("TN", 3, 1, 3, 1.0, E, a, 0.0, e);
            for (j = 0; j < 3; ++j) {
                vs[j] = rs[j + 3 + i * 6] - x[j];
            }
            double rate = math.dot(vs, e, 3) + 2.4323877909897254E-13 * (rs[4 + i * 6] * rr[0] + rs[1 + i * 6] * x[0] - rs[3 + i * 6] * rr[1] - rs[i * 6] * x[1]);
            double sig = err <= 0.0 ? 1.0 : err * 2.99792458E8 / freq;
            v[nv] = ((double)(-obs[i].D[0]) * 2.99792458E8 / freq - (rate + x[3] - 2.99792458E8 * dts[1 + i * 2])) / sig;
            for (j = 0; j < 4; ++j) {
                H[j + nv * 4] = (j < 3 ? -e[j] : 1.0) / sig;
            }
            ++nv;
        }
        return nv;
    }
}

