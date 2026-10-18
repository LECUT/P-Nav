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
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.io.BufferedReader;
import java.io.IOException;
import jdk.internal.org.jline.utils.Log;

public class decodeObsEpoch {
    public static int decode_obsepoch(BufferedReader reader, char[] buff, double[] ver, gtime_t[] time, int[] flag, int[] sats) throws IOException {
        int n;
        char[] satid = new char[8];
        if (ver[0] <= 2.99) {
            n = (int)util1.str2num(buff, 29, 3);
            if (n <= 0) {
                return 0;
            }
            flag[0] = (int)util1.str2num(buff, 28, 1);
            if (3 <= flag[0] && flag[0] <= 5) {
                return n;
            }
            if (util1.str2time(buff, 0, 26, time) == -1) {
                return 0;
            }
            int i = 0;
            int j = 32;
            while (i < n) {
                if (j >= 68) {
                    buff = reader.readLine().toCharArray();
                    if (buff == null) break;
                    j = 32;
                }
                if (i < 96) {
                    util2.strncpy(satid, String.valueOf(buff).substring(j).toCharArray(), 3);
                    sats[i] = util1.satid2no(satid);
                }
                ++i;
                j += 3;
            }
        } else {
            n = (int)util1.str2num(buff, 32, 3);
            if (n <= 0) {
                return 0;
            }
            flag[0] = (int)util1.str2num(buff, 31, 1);
            if (3 <= flag[0] && flag[0] <= 5) {
                return n;
            }
            if (buff[0] != '>' || util1.str2time(buff, 1, 28, time) == -1) {
                Log.trace((Object[])new Object[]{2, "rinex obs invalid epoch: epoch=%29.29s\n", buff});
                return 0;
            }
        }
        return n;
    }
}

