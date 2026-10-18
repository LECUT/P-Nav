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
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;

public class seleph {
    public static final double MAXDTOE = 7200.0;
    public static final double MAXDTOE_QZS = 7200.0;
    public static final double MAXDTOE_GAL = 14400.0;
    public static final double MAXDTOE_CMP = 21600.0;
    public static final double MAXDTOE_GLO = 1800.0;
    public static final double MAXDTOE_IRN = 7200.0;
    public static final double MAXDTOE_SBS = 360.0;
    public static final double MAXDTOE_S = 86400.0;
    static int[] eph_sel = new int[]{0, 0, 0, 0, 0, 0, 0};

    public static double eph2clk(gtime_t time, eph_t eph) {
        double ts;
        double t = ts = util1.timediff(time, eph.getToc());
        for (int i = 0; i < 2; ++i) {
            t = ts - (eph.getF0() + eph.getF1() * t + eph.getF2() * t * t);
        }
        return eph.getF0() + eph.getF1() * t + eph.getF2() * t * t;
    }

    public static double geph2clk(gtime_t time, geph_t geph) {
        double ts;
        double t = ts = util1.timediff(time, geph.getToe());
        for (int i = 0; i < 2; ++i) {
            t = ts - (-geph.getTaun() + geph.getGamn() * t);
        }
        return -geph.getTaun() + geph.getGamn() * t;
    }

    public static double seph2clk(gtime_t time, seph_t seph) {
        double t = util1.timediff(time, seph.getT0());
        for (int i = 0; i < 2; ++i) {
            t -= seph.getAf0() + seph.getAf1() * t;
        }
        return seph.getAf0() + seph.getAf1() * t;
    }

    public static int get_seleph(int sys) {
        switch (sys) {
            case 1: {
                return eph_sel[0];
            }
            case 4: {
                return eph_sel[1];
            }
            case 8: {
                return eph_sel[2];
            }
            case 16: {
                return eph_sel[3];
            }
            case 32: {
                return eph_sel[4];
            }
            case 64: {
                return eph_sel[5];
            }
            case 2: {
                return eph_sel[6];
            }
        }
        return 0;
    }

    public static eph_t sel_eph(gtime_t time, int[] sat, int iode, nav_t nav) {
        int sel;
        double tmax;
        int j = -1;
        int sys = satSys.sat_sys(sat[0], null);
        switch (sys) {
            case 1: {
                tmax = 7201.0;
                sel = eph_sel[0];
                break;
            }
            case 8: {
                tmax = 14400.0;
                sel = eph_sel[2];
                break;
            }
            case 16: {
                tmax = 7201.0;
                sel = eph_sel[3];
                break;
            }
            case 32: {
                tmax = 21601.0;
                sel = eph_sel[4];
                break;
            }
            case 64: {
                tmax = 7201.0;
                sel = eph_sel[5];
                break;
            }
            default: {
                tmax = 7201.0;
            }
        }
        double tmin = tmax + 1.0;
        for (int i = 0; i < nav.n; ++i) {
            double d;
            if (nav.eph[i].getSat() != sat[0] || iode >= 0 && nav.eph[i].getIode() != iode || sys == 8 && ((sel = seleph.get_seleph(8)) == 0 && (nav.eph[i].getCode() & 0x200) == 0 || sel == 1 && (nav.eph[i].getCode() & 0x100) == 0 || util1.timediff(nav.eph[i].getToe(), time) >= 0.0)) continue;
            double t = Math.abs(util1.timediff(nav.eph[i].getToe(), time));
            if (t > tmax) continue;
            if (iode >= 0) {
                return nav.eph[i];
            }
            if (!(t <= tmin)) continue;
            j = i;
            tmin = t;
        }
        if (iode >= 0 || j < 0) {
            return null;
        }
        return nav.eph[j];
    }

    public static geph_t sel_geph(gtime_t time, int[] sat, int iode, nav_t nav) {
        double tmax = 1800.0;
        double tmin = tmax + 1.0;
        int j = -1;
        for (int i = 0; i < nav.ng; ++i) {
            double d;
            if (nav.geph[i].getSat() != sat[0] || iode >= 0 && nav.geph[i].getIode() != iode) continue;
            double t = Math.abs(util1.timediff(nav.geph[i].getToe(), time));
            if (t > tmax) continue;
            if (iode >= 0) {
                return nav.geph[i];
            }
            if (!(t <= tmin)) continue;
            j = i;
            tmin = t;
        }
        if (iode >= 0 || j < 0) {
            return null;
        }
        return nav.geph[j];
    }

    public static seph_t sel_seph(gtime_t time, int[] sat, nav_t nav) {
        double tmax = 360.0;
        double tmin = tmax + 1.0;
        int j = -1;
        for (int i = 0; i < nav.ns; ++i) {
            double d;
            if (nav.seph[i].getSat() != sat[0]) continue;
            double t = Math.abs(util1.timediff(nav.seph[i].getT0(), time));
            if (t > tmax || !(t <= tmin)) continue;
            j = i;
            tmin = t;
        }
        if (j < 0) {
            return null;
        }
        return nav.seph[j];
    }
}

