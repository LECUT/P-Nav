/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.geph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;
import jdk.internal.org.jline.utils.Log;

public class decodeGeph {
    public static final int MINFREQ_GLO = -7;
    public static final int MAXFREQ_GLO = 13;

    public static int decode_geph(double[] ver, int[] sat, gtime_t[] toc, double[] data, geph_t[] geph) {
        geph_t geph0 = new geph_t();
        int[] week = new int[1];
        if (satSys.sat_sys(sat[0], null) != 4) {
            Log.trace((Object[])new Object[]{3, "glonass ephemeris error: invalid satellite sat=%2d\n", sat});
            return 0;
        }
        geph[0] = geph0;
        geph[0].setSat(sat[0]);
        double tow = util1.time2gpst(toc[0], week);
        toc[0] = util1.gpst2time(week, Math.floor((tow + 450.0) / 900.0) * 900.0);
        int dow = (int)Math.floor(tow / 86400.0);
        double tod = ver[0] <= 2.99 ? data[2] : data[2] % 86400.0;
        gtime_t tof = util1.gpst2time(week, tod + (double)dow * 86400.0);
        tof = util1.adjday(tof, toc[0]);
        geph[0].setToe(util1.utc2gpst(toc[0]));
        geph[0].setTof(util1.utc2gpst(tof));
        geph[0].setIode((int)((double)Math.floorMod((int)(tow + 10800.0), 86400) / 900.0 + 0.5));
        geph[0].setTaun(-data[0]);
        geph[0].setGamn(data[1]);
        geph[0].getPos()[0] = data[3] * 1000.0;
        geph[0].getPos()[1] = data[7] * 1000.0;
        geph[0].getPos()[2] = data[11] * 1000.0;
        geph[0].getVel()[0] = data[4] * 1000.0;
        geph[0].getVel()[1] = data[8] * 1000.0;
        geph[0].getVel()[2] = data[12] * 1000.0;
        geph[0].getAcc()[0] = data[5] * 1000.0;
        geph[0].getAcc()[1] = data[9] * 1000.0;
        geph[0].getAcc()[2] = data[13] * 1000.0;
        geph[0].setSvh((int)data[6]);
        geph[0].setFrq((int)data[10]);
        if (geph[0].getFrq() > 128) {
            geph[0].frq -= 256;
        }
        if (geph[0].frq < -7 || 13 < geph[0].frq) {
            System.out.printf("rinex gnav invalid freq: sat=%2d fn=%d\n", sat[0], geph[0].frq);
        }
        return 1;
    }
}

