/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.nav_t;

public class satSys {
    public static int sat_sys(int sat, int[] prn) {
        int sys = 0;
        if (sat <= 0 || nav_t.getMaxSat() < sat) {
            sat = 0;
        } else if (sat <= 32) {
            sys = 1;
            sat += 0;
        } else if ((sat -= 32) <= 27) {
            sys = 4;
            sat += 0;
        } else if ((sat -= 27) <= 36) {
            sys = 8;
            sat += 0;
        } else if ((sat -= 36) <= 10) {
            sys = 16;
            sat += 192;
        } else if ((sat -= 10) <= 63) {
            sys = 32;
            sat += 0;
        } else if ((sat -= 63) <= 14) {
            sys = 64;
            sat += 0;
        } else if ((sat -= 14) <= 10) {
            sys = 128;
            sat += 0;
        } else if ((sat -= 10) <= 39) {
            sys = 2;
            sat += 119;
        } else {
            sat = 0;
        }
        if (prn != null) {
            prn[0] = sat;
        }
        return sys;
    }
}

