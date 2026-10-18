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
import com.rtklib.bean.pclk_t;
import com.rtklib.readobsnav.satSys;
import com.rtklib.readobsnav.setSysmask;
import com.rtklib.util.util1;
import java.io.BufferedReader;
import java.io.IOException;

public class readRnxClk {
    public static int read_rnxclk(BufferedReader fp, char[] opt, int index, nav_t nav) throws IOException {
        String line;
        pclk_t[] nav_pclk = new pclk_t[nav.n];
        gtime_t[] time = new gtime_t[1];
        double[] data = new double[2];
        int[] sat = new int[1];
        if (nav == null) {
            return 0;
        }
        int mask = setSysmask.set_sysmask(opt);
        while ((line = fp.readLine()) != null) {
            if (util1.str2time(line.toCharArray(), 8, 26, time) == -1) continue;
            char[] satid = line.substring(3, 7).toCharArray();
            if (!line.contains("AS") || (sat[0] = util1.satid2no(satid)) == 0 || (satSys.sat_sys(sat[0], null) & mask) == 0) continue;
            int i = 0;
            int j = 40;
            while (i < 2) {
                data[i] = util1.str2num(line.toCharArray(), j, 19);
                ++i;
                j += 20;
            }
            if (nav.nc >= nav.getNcmax()) {
                int temp = nav.getNcmax();
                nav.setNcmax(temp += 1024);
                nav.setPclk(nav_pclk);
            }
            if (nav.nc <= 0 || Math.abs(util1.timediff(time[0], nav.getPclk()[nav.nc - 1].getTime())) > 1.0E-9) {
                ++nav.nc;
                nav.getPclk()[nav.nc - 1].setTime(time[0]);
                nav.getPclk()[nav.nc - 1].setIndex(index);
                for (i = 0; i < nav_t.getMaxSat(); ++i) {
                    nav.getPclk()[nav.nc - 1].clk[i][0] = 0.0;
                    nav.getPclk()[nav.nc - 1].std[i][0] = 0.0f;
                }
            }
            nav.getPclk()[nav.nc - 1].clk[sat[0] - 1][0] = data[0];
            nav.getPclk()[nav.nc - 1].std[sat[0] - 1][0] = (float)data[1];
        }
        return nav.nc > 0 ? 1 : 0;
    }
}

