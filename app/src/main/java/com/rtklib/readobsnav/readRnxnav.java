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
import com.rtklib.bean.nav_t;
import com.rtklib.bean.seph_t;
import com.rtklib.readobsnav.readRnxnavb;
import com.rtklib.util.util1;
import java.io.BufferedReader;
import java.io.IOException;

public class readRnxnav {
    public static int read_rnxnav(BufferedReader reader, char[] opt, double[] ver, int sys, nav_t nav) throws IOException {
        eph_t[] eph = new eph_t[1];
        geph_t[] geph = new geph_t[1];
        seph_t[] seph = new seph_t[1];
        int stat = 0;
        int[] type = new int[1];
        while ((stat = readRnxnavb.read_rnxnavb(reader, opt, ver, sys, type, eph, geph, seph)) >= 0) {
            if (stat == 0) continue;
            if ((stat = (switch (type[0]) {
                case 1 -> util1.add_geph(nav, geph);
                case 2 -> util1.add_seph(nav, seph);
                default -> util1.add_eph(nav, eph);
            })) != 0) continue;
            return 0;
        }
        return 1;
    }
}

