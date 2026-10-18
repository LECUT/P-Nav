/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.eph_t;
import com.rtklib.bean.geph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.seph_t;
import com.rtklib.readobsnav.decodeEph;
import com.rtklib.readobsnav.decodeGeph;
import com.rtklib.readobsnav.decodeSeph;
import com.rtklib.readobsnav.satSys;
import com.rtklib.readobsnav.setSysmask;
import com.rtklib.util.util1;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

public class readRnxnavb {
    public static int read_rnxnavb(BufferedReader reader, char[] opt, double[] ver, int sys, int[] type, eph_t[] eph, geph_t[] geph, seph_t[] seph) throws IOException {
        String line;
        gtime_t[] toc = new gtime_t[1];
        double[] data = new double[64];
        int i = 0;
        int sp = 3;
        int mask = 0;
        int[] sat = new int[1];
        mask = setSysmask.set_sysmask(opt);
        while ((line = reader.readLine()) != null) {
            int index;
            int j;
            char[] p;
            char[] buff = line.toCharArray();
            if (i == 0) {
                if (ver[0] >= 3.0) {
                    char[] id = String.format("%.3s", line).toCharArray();
                    sat[0] = util1.satid2no(id);
                    sp = 4;
                    if (ver[0] >= 3.0 && (sys = satSys.sat_sys(sat[0], null)) == 0) {
                        sys = id[0] == 'S' ? 2 : (id[0] == 'R' ? 4 : 1);
                    }
                } else {
                    int prn = (int)util1.str2num(buff, 0, 2);
                    sat[0] = sys == 2 ? util1.satno(2, prn + 100) : (sys == 4 ? util1.satno(4, prn) : (sys == 32 ? util1.satno(32, prn) : (sys == 8 ? util1.satno(8, prn) : (93 <= prn && prn <= 97 ? util1.satno(16, prn + 100) : util1.satno(1, prn)))));
                }
                try {
                    buff = Arrays.copyOfRange(buff, sp, buff.length);
                }
                catch (IllegalArgumentException e) {
                    System.err.println("Invalid index: " + e.getMessage());
                }
                if (util1.str2time(buff, 0, 19, toc) != 0) {
                    return 0;
                }
                if (buff == null || buff.length == 0 || sp < 0 || sp + 15 >= buff.length) {
                    return 0;
                }
                p = ver[0] >= 3.0 ? Arrays.copyOfRange(buff, sp + 15, buff.length) : Arrays.copyOfRange(buff, sp + 16, buff.length);
                j = 0;
                index = 0;
                while (j < 3) {
                    data[i++] = util1.str2num(Arrays.copyOfRange(p, index, buff.length), 0, 19);
                    ++j;
                    index += 19;
                }
                continue;
            }
            j = 0;
            index = 0;
            p = Arrays.copyOfRange(buff, sp, buff.length);
            while (j < 4) {
                if (j == 1) {
                    if (p.length >= 19) {
                        p = Arrays.copyOfRange(p, index, buff.length);
                        data[i++] = util1.str2num(p, 0, 19);
                    }
                } else if (j == 2) {
                    if (p.length >= 19) {
                        p = Arrays.copyOfRange(p, 19, buff.length);
                        data[i++] = util1.str2num(p, 0, 19);
                    }
                } else if (j == 3) {
                    if (p.length >= 19) {
                        p = Arrays.copyOfRange(p, 19, buff.length);
                        data[i++] = util1.str2num(p, 0, 19);
                    }
                } else {
                    p = Arrays.copyOfRange(p, index, buff.length);
                    data[i++] = util1.str2num(p, 0, 19);
                }
                ++j;
                index += 19;
            }
            if (sys == 4 && i >= 15) {
                if (mask == 0) {
                    return 0;
                }
                type[0] = 1;
                return decodeGeph.decode_geph(ver, sat, toc, data, geph);
            }
            if (sys == 2 && i >= 15) {
                if (mask == 0) {
                    return 0;
                }
                type[0] = 2;
                return decodeSeph.decode_seph(ver, sat, toc, data, seph);
            }
            if (i + 2 < 31) continue;
            if (mask == 0) {
                return 0;
            }
            type[0] = 0;
            return decodeEph.decode_eph(ver, sat, toc, data, eph);
        }
        return -1;
    }
}

