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
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.ReadRnxObsB;
import com.rtklib.readobsnav.saveSlips;
import com.rtklib.util.util1;
import java.io.BufferedReader;
import java.io.IOException;

public class ReadRnxObs {
    public static final int MAXOBS = 96;

    public static int read_rnxobs(BufferedReader reader, gtime_t ts, gtime_t te, double tint, char[] opt, int rcv, double[] ver, int[] tsys, char[][][] tobs, obs_t obs, sta_t sta) throws IOException, CloneNotSupportedException {
        int n;
        obsd_t[] data = new obsd_t[96];
        byte[][] slips = new byte[nav_t.getMaxSat()][3];
        int stat = 0;
        int[] flag = new int[1];
        if (obs == null || rcv > 64) {
            return 0;
        }
        block0: while ((n = ReadRnxObsB.read_rnxobsb(reader, opt, ver, tsys, tobs, flag, data, sta)) >= 0 && stat >= 0) {
            int i;
            for (i = 0; i < n; ++i) {
                if (tsys[0] == 1) {
                    data[i].time = util1.utc2gpst(data[i].time);
                }
                saveSlips.save_slips(slips, data[i]);
            }
            if (n > 0 && !util1.screent(data[0].time, ts, te, tint)) continue;
            for (i = 0; i < n; ++i) {
                saveSlips.rest_slips(slips, data[i]);
                data[i].rcv = (byte)rcv;
                stat = util1.add_obsdata(obs, data[i]);
                if (stat < 0) continue block0;
            }
        }
        return stat;
    }
}

