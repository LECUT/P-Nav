/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.obsd_t;
import com.rtklib.bean.sigind_t;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;
import java.io.BufferedReader;
import java.io.IOException;

public class decodeObsData {
    public static final double SNR_UNIT = 1.0;     /* EX: SNR stored as float dBHz directly */

    public static int decode_obsdata(BufferedReader reader, char[] buff, double[] ver, int mask, sigind_t[] index, obsd_t obs) throws IOException {
        int j;
        double[] val = new double[64];
        byte[] lli = new byte[64];
        char[] satid = new char[8];
        boolean stat = true;
        int[] p = new int[64];
        int[] k = new int[16];
        int[] l = new int[16];
        if (ver[0] > 2.99) {
            util1.sprintf(satid, "%.3s", new String(buff));
            obs.sat[0] = util1.satid2no(satid);
        }
        if (obs.sat[0] == 0) {
            stat = false;
        } else if ((satSys.sat_sys(obs.sat[0], null) & mask) == 0) {
            stat = false;
        }
        sigind_t ind = switch (satSys.sat_sys(obs.sat[0], null)) {
            case 4 -> index[1];
            case 8 -> index[2];
            case 16 -> index[3];
            case 2 -> index[4];
            case 32 -> index[5];
            case 64 -> index[6];
            default -> index[0];
        };
        int i = 0;
        int n = j = ver[0] <= 2.99 ? 0 : 3;
        while (i < ind.n) {
            if (ver[0] <= 2.99 && j >= 80) {
                String line = reader.readLine();
                if (line != null && (buff = line.toCharArray()) == null) break;
                j = 0;
            }
            if (stat) {
                val[i] = util1.str2num(buff, j, 14) + ind.shift[i];
                lli[i] = (byte)((int)util1.str2num(buff, j + 14, 1) & 3);
            }
            ++i;
            j += 16;
        }
        if (!stat) {
            return 0;
        }
        for (i = 0; i < 3; ++i) {
            obs.L[i] = 0.0;
            obs.P[i] = 0.0;
            obs.D[i] = 0.0f;
            obs.code[i] = 0;
            obs.LLI[i] = 0;
            obs.SNR[i] = 0;
        }
        int m = 0;
        int n2 = 0;
        for (i = 0; i < ind.n; ++i) {
            int n3 = p[i] = ver[0] <= 2.11 ? ind.idx[i] : ind.pos[i];
            if (ind.type[i] == 0 && p[i] == 0) {
                k[n2++] = i;
            }
            if (ind.type[i] != 0 || p[i] != 1) continue;
            l[m++] = i;
        }
        if (ver[0] <= 2.11) {
            if (n2 >= 2) {
                if (val[k[0]] == 0.0 && val[k[1]] == 0.0) {
                    p[k[0]] = -1;
                    p[k[1]] = -1;
                } else if (val[k[0]] != 0.0 && val[k[1]] == 0.0) {
                    p[k[0]] = 0;
                    p[k[1]] = -1;
                } else if (val[k[0]] == 0.0 && val[k[1]] != 0.0) {
                    p[k[0]] = -1;
                    p[k[1]] = 0;
                } else if (ind.pri[k[1]] > ind.pri[k[0]]) {
                    p[k[1]] = 0;
                    p[k[0]] = -1;
                } else {
                    p[k[0]] = 0;
                    p[k[1]] = -1;
                }
            }
            if (m >= 2) {
                if (val[l[0]] == 0.0 && val[l[1]] == 0.0) {
                    p[l[0]] = -1;
                    p[l[1]] = -1;
                } else if (val[l[0]] != 0.0 && val[l[1]] == 0.0) {
                    p[l[0]] = 1;
                    p[l[1]] = -1;
                } else if (val[l[0]] == 0.0 && val[l[1]] != 0.0) {
                    p[l[0]] = -1;
                    p[l[1]] = 1;
                } else if (ind.pri[l[1]] > ind.pri[l[0]]) {
                    p[l[1]] = 1;
                    p[l[0]] = -1;
                } else {
                    p[l[0]] = 1;
                    p[l[1]] = -1;
                }
            }
        }
        block17: for (i = 0; i < ind.n; ++i) {
            if (p[i] < 0 || val[i] == 0.0) continue;
            switch (ind.type[i]) {
                case 0: {
                    obs.P[p[i]] = val[i];
                    obs.code[p[i]] = ind.code[i];
                    continue block17;
                }
                case 1: {
                    obs.L[p[i]] = val[i];
                    obs.LLI[p[i]] = lli[i];
                    continue block17;
                }
                case 2: {
                    obs.D[p[i]] = (float)val[i];
                    continue block17;
                }
                case 3: {
                    obs.SNR[p[i]] = (float)val[i];
                }
            }
        }
        return 1;
    }
}

