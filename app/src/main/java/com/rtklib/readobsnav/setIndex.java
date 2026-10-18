/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.sigind_t;
import com.rtklib.util.util2;
import java.util.Arrays;
import java.util.Scanner;
import jdk.internal.org.jline.utils.Log;

public class setIndex {
    public static void set_index(double[] ver, int sys, char[] opt, char[][] tobs, sigind_t ind) {
        int j;
        int k;
        char[] p;
        String obscodes = "CLDS";
        String optstr = "";
        double shift = 0.0;
        boolean hasContent = false;
        int length = 0;
        for (char[] tob : tobs) {
            if (tob[0] == '\u0000') continue;
            ++length;
        }
        int n = 0;
        int i = 0;
        while (i < length && !String.valueOf(tobs[i]).isEmpty()) {
            ind.code[i] = (byte)util2.obs2code(tobs[i]);
            ind.type[i] = (byte)(obscodes.indexOf(tobs[i][0]) != -1 ? obscodes.indexOf(tobs[i][0]) : 0);
            if (ind.type[i] < 0) {
                ind.type[i] = 0;
            }
            ind.idx[i] = util2.code2idx(sys, ind.code[i]);
            ind.pri[i] = (byte)util2.getcodepri(sys, ind.code[i], String.valueOf(opt));
            ind.pos[i] = -1;
            ++i;
            ++n;
        }
        optstr = switch (sys) {
            case 1 -> "-GL%2s=%lf";
            case 4 -> "-RL%2s=%lf";
            case 8 -> "-EL%2s=%lf";
            case 16 -> "-JL%2s=%lf";
            case 2 -> "-SL%2s=%lf";
            case 32 -> "-CL%2s=%lf";
            case 64 -> "-IL%2s=%lf";
            default -> optstr;
        };
        for (char c : p = opt) {
            if (c == '\u0000') continue;
            hasContent = true;
            break;
        }
        if (hasContent) {
            p = util2.strchr(String.valueOf(p), '-').toCharArray();
            for (int index = 0; index < String.valueOf(p).length() && !String.valueOf(p).isEmpty(); ++index) {
                Scanner scanner = new Scanner(optstr);
                scanner.useDelimiter("=|-GL");
                char[] str = scanner.next().toCharArray();
                if (scanner.hasNextDouble()) {
                    shift = scanner.nextDouble();
                }
                for (i = 0; i < n; ++i) {
                    if (Arrays.equals(util2.code2obs(ind.code[i]).toCharArray(), str)) continue;
                    ind.shift[i] = shift;
                    Log.trace((Object[])new Object[]{2, "phase shift: sys=%2d tobs=%s shift=%.3f\n", sys, tobs[i], shift});
                }
            }
        }
        for (i = 0; i < 3; ++i) {
            k = -1;
            for (j = 0; j < n; ++j) {
                if (ind.idx[j] != i || ind.pri[j] <= 0 || k >= 0 && ind.pri[j] <= ind.pri[k]) continue;
                k = j;
            }
            if (k < 0) continue;
            for (j = 0; j < n; ++j) {
                if (ind.code[j] != ind.code[k]) continue;
                ind.pos[j] = i;
            }
        }
        for (i = 0; i < 0; ++i) {
            for (j = 0; j < n && (ind.code[j] <= 0 || ind.pri[j] <= 0 || ind.pos[j] >= 0); ++j) {
            }
            if (j >= n) break;
            for (k = 0; k < n; ++k) {
                if (ind.code[k] != ind.code[j]) continue;
                ind.pos[k] = 3 + i;
            }
        }
        for (i = 0; i < n; ++i) {
            if (ind.code[i] != 0 && ind.pri[i] != 0 && ind.pos[i] < 0) continue;
        }
        ind.n = n;
    }
}

