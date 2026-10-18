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
import com.rtklib.bean.seph_t;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;

public class decodeSeph {
    public static int decode_seph(double[] ver, int[] sat, gtime_t[] toc, double[] data, seph_t[] seph) {
        seph_t seph0 = new seph_t();
        int[] week = new int[1];
        if (satSys.sat_sys(sat[0], null) != 2) {
            return 0;
        }
        seph[0] = seph0;
        seph[0].setSat(sat[0]);
        seph[0].setT0(toc[0]);
        util1.time2gpst(toc[0], week);
        seph[0].setTof(util1.adjweek(util1.gpst2time(week, data[2]), toc[0]));
        seph[0].setAf0(data[0]);
        seph[0].setAf1(data[1]);
        double[] temppos = new double[3];
        double[] tempvel = new double[3];
        double[] tempacc = new double[3];
        temppos[0] = data[3] * 1000.0;
        temppos[1] = data[7] * 1000.0;
        temppos[2] = data[11] * 1000.0;
        seph[0].setPos(temppos);
        tempvel[0] = data[4] * 1000.0;
        tempvel[1] = data[8] * 1000.0;
        tempvel[2] = data[12] * 1000.0;
        seph[0].setVel(tempvel);
        tempacc[0] = data[5] * 1000.0;
        tempacc[1] = data[9] * 1000.0;
        tempacc[2] = data[13] * 1000.0;
        seph[0].setAcc(tempacc);
        seph[0].setSvh((int)data[6]);
        seph[0].setSva(util1.uraIndex(data[10]));
        return 1;
    }
}

