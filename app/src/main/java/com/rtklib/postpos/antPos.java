/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.postpos;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.sta_t;
import com.rtklib.pntpos.pntPos;
import com.rtklib.postpos.postpos;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class antPos {
    public static final int POSOPT_POS = 0;
    public static final int POSOPT_SINGLE = 1;
    public static final int POSOPT_FILE = 2;
    public static final int POSOPT_RINEX = 3;
    public static final int POSOPT_RTCM = 4;

    public static int avepos(double[] ra, int rcv, obs_t obs, nav_t nav, prcopt_t opt) {
        int m;
        int i;
        obsd_t[] data = new obsd_t[96];
        gtime_t ts = new gtime_t();
        sol_t sol = new sol_t();
        int n = 0;
        int[] iobs = new int[1];
        String msg = null;
        for (i = 0; i < 3; ++i) {
            ra[i] = 0.0;
        }
        iobs[0] = 0;
        while ((m = util1.nextobsf(obs, iobs, rcv)) > 0) {
            int j = 0;
            for (i = 0; i < m && i < 96; ++i) {
                data[j] = obs.data[iobs[0] + i];
                if ((satSys.sat_sys(data[j].sat[0], null) & opt.navsys) == 0 || opt.exsats[data[j].sat[0] - 1] == 1) continue;
                ++j;
            }
            if (j > 0 && util1.screent(data[0].time, ts, ts, 1.0) && pntPos.pnt_pos(data, j, nav, opt, sol, null, null, msg) != 0) {
                for (i = 0; i < 3; ++i) {
                    int n2 = i;
                    ra[n2] = ra[n2] + sol.rr[i];
                }
                ++n;
            }
            iobs[0] = iobs[0] + m;
        }
        if (n <= 0) {
            System.out.println("no average of base station position");
            return 0;
        }
        i = 0;
        while (i < 3) {
            int n3 = i++;
            ra[n3] = ra[n3] / (double)n;
        }
        return 1;
    }

    public static int getstapos(String file, char[] name, double[] r) {
        char[] buff = new char[256];
        char[] sname = new char[256];
        double[] pos = new double[3];
        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                char[] p = util2.strchr(String.valueOf(buff), '%').toCharArray();
                if (String.valueOf(p).isEmpty()) {
                    int n = 0;
                    return n;
                }
                if (p != null) continue;
                pos[0] = pos[0] * (Math.PI / 180);
                pos[1] = pos[1] * (Math.PI / 180);
                util2.pos2ecef(pos, r);
                int n = 1;
                return n;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (br != null) {
                    br.close();
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return 0;
    }

    public static int antpos(prcopt_t opt, int rcvno, obs_t obs, nav_t nav, sta_t[] sta, char[] posfile) throws IOException {
        int postype;
        double[] rr = rcvno == 1 ? opt.getRu() : opt.getRb();
        double[] del = new double[3];
        double[] pos = new double[3];
        double[] dr = new double[3];
        int n = postype = rcvno == 1 ? opt.getRovpos() : opt.refpos;
        if (postype == 1) {
            if (antPos.avepos(rr, rcvno, obs, nav, opt) == 0) {
                System.out.println("error : station pos computation");
                return 0;
            }
        } else if (postype == 2) {
            char[] name = postpos.stas[rcvno == 1 ? 0 : 1].getName().toCharArray();
            if (antPos.getstapos(String.valueOf(posfile), name, rr) == 0) {
                System.out.println("error : no position");
                return 0;
            }
        }
        return 1;
    }
}

