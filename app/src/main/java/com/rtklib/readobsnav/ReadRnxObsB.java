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
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.sigind_t;
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.decodeObsData;
import com.rtklib.readobsnav.decodeObsEpoch;
import com.rtklib.readobsnav.decodeObsh;
import com.rtklib.readobsnav.setIndex;
import com.rtklib.readobsnav.setSysmask;
import java.io.BufferedReader;
import java.io.IOException;

public class ReadRnxObsB {
    static final int NUMSYS = 7;

    public static int read_rnxobsb(BufferedReader reader, char[] opt, double[] ver, int[] tsys, char[][][] tobs, int[] flag, obsd_t[] data, sta_t sta) throws IOException {
        String line;
        gtime_t[] time = new gtime_t[1];
        sigind_t[] index = new sigind_t[7];
        for (int i = 0; i < 7; ++i) {
            index[i] = new sigind_t();
        }
        int i = 0;
        int n = 0;
        int nsat = 0;
        int nsys = 7;
        int mask = 0;
        int[] sats = new int[96];
        mask = setSysmask.set_sysmask(opt);
        setIndex.set_index(ver, 1, opt, tobs[0], index[0]);
        setIndex.set_index(ver, 4, opt, tobs[1], index[1]);
        setIndex.set_index(ver, 8, opt, tobs[2], index[2]);
        setIndex.set_index(ver, 16, opt, tobs[3], index[3]);
        setIndex.set_index(ver, 2, opt, tobs[4], index[4]);
        setIndex.set_index(ver, 32, opt, tobs[5], index[5]);
        while ((line = reader.readLine()) != null) {
            char[] buff = line.toCharArray();
            if (i == 0) {
                nsat = decodeObsEpoch.decode_obsepoch(reader, buff, ver, time, flag, sats);
                if (nsat <= 0) {
                    continue;
                }
            } else if ((flag[0] <= 2 || flag[0] == 6) && n < 96) {
                if (data[n] == null) {
                    data[n] = new obsd_t();
                }
                data[n] = new obsd_t();
                data[n].time.time = time[0].getTime();
                data[n].time.sec = time[0].getSec();
                data[n].sat[0] = sats[i - 1];
                if (decodeObsData.decode_obsdata(reader, buff, ver, mask, index, data[n]) == 1) {
                    ++n;
                }
            } else if (flag[0] == 3 || flag[0] == 4) {
                decodeObsh.decode_obsh(reader, buff, ver, tsys, tobs, null, sta);
            }
            if (++i <= nsat) continue;
            return n;
        }
        return -1;
    }
}

