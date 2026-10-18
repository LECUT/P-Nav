/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.pntpos;

import com.rtklib.bean.eph_t;
import com.rtklib.bean.geph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.seph_t;
import com.rtklib.pntpos.seleph;
import com.rtklib.readobsnav.satSys;

public class ephclk {
    public static int eph_clk(gtime_t time, gtime_t teph, int[] sat, nav_t nav, double[] dts) {
        int sys = satSys.sat_sys(sat[0], null);
        if (sys == 1 || sys == 8 || sys == 16 || sys == 32 || sys == 64) {
            eph_t eph = seleph.sel_eph(teph, sat, -1, nav);
            if (eph == null) {
                return 0;
            }
            dts[0] = seleph.eph2clk(time, eph);
        } else if (sys == 4) {
            geph_t geph = seleph.sel_geph(teph, sat, -1, nav);
            if (geph == null) {
                return 0;
            }
            dts[0] = seleph.geph2clk(time, geph);
        } else if (sys == 2) {
            seph_t seph = seleph.sel_seph(teph, sat, nav);
            if (seph == null) {
                return 0;
            }
            dts[0] = seleph.seph2clk(time, seph);
        } else {
            return 0;
        }
        return 1;
    }
}

