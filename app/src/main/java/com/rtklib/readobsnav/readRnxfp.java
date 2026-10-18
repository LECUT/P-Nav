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
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.ReadRnxObs;
import com.rtklib.readobsnav.readRnxClk;
import com.rtklib.readobsnav.readRnxnav;
import com.rtklib.readobsnav.readrnxh;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class readRnxfp {
    public static int read_rnxfp(File fp, gtime_t ts, gtime_t te, double tint, char[] opt, int flag, int index, char[] type, obs_t obs, nav_t nav, sta_t sta) throws IOException, CloneNotSupportedException {
        BufferedReader reader = new BufferedReader(new FileReader(fp));
        double[] ver = new double[1];
        int[] sys = new int[1];
        int[] tsys = new int[1];
        char[][][] tobs = new char[7][64][4];
        if (readrnxh.read_rnxh(reader, ver, type, sys, tsys, tobs, nav, sta) == 0) {
            return 0;
        }
        if (flag == 0 && type[0] == 'C' || flag != 0 && type[0] != 'C') {
            return 0;
        }
        return switch (type[0]) {
            case 'O' -> ReadRnxObs.read_rnxobs(reader, ts, te, tint, opt, index, ver, tsys, tobs, obs, sta);
            case 'N' -> readRnxnav.read_rnxnav(reader, opt, ver, sys[0], nav);
            case 'G' -> readRnxnav.read_rnxnav(reader, opt, ver, 4, nav);
            case 'H' -> readRnxnav.read_rnxnav(reader, opt, ver, 2, nav);
            case 'J' -> readRnxnav.read_rnxnav(reader, opt, ver, 16, nav);
            case 'L' -> readRnxnav.read_rnxnav(reader, opt, ver, 8, nav);
            case 'C' -> readRnxClk.read_rnxclk(reader, opt, index, nav);
            default -> 0;
        };
    }
}

