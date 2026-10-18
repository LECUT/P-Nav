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
import com.rtklib.bean.sbssatp_t;
import com.rtklib.bean.seph_t;
import com.rtklib.pntpos.seleph;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;

public class ephpos {
    public static final double RE_GLO = 6378136.0;
    public static final double MU_GPS = 3.986005E14;
    public static final double MU_GLO = 3.9860044E14;
    public static final double MU_GAL = 3.986004418E14;
    public static final double MU_CMP = 3.986004418E14;
    public static final double J2_GLO = 0.0010826257;
    public static final double OMGE_GLO = 7.292115E-5;
    public static final double OMGE_GAL = 7.2921151467E-5;
    public static final double OMGE_CMP = 7.292115E-5;
    public static final double OMGE = 7.2921151467E-5;
    public static final double RTOL_KEPLER = 1.0E-13;
    public static final int MAX_ITER_KEPLER = 30;
    public static final double SIN_5 = -0.0871557427476582;
    public static final double COS_5 = 0.9961946980917455;
    public static final double STD_GAL_NAPA = 500.0;
    public static final double TSTEP = 60.0;
    public static final double ERREPH_GLO = 5.0;

    public static int eph_pos(gtime_t time, gtime_t teph, int[] sat, nav_t nav, int iode, double[] rs, double[] dts, double[] var, int[] svh, int k) {
        double[] rst = new double[3];
        double[] dtst = new double[1];
        double tt = 0.001;
        int sys = satSys.sat_sys(sat[0], null);
        svh[k] = -1;
        if (sys == 1 || sys == 8 || sys == 16 || sys == 32 || sys == 64) {
            eph_t eph = seleph.sel_eph(teph, sat, iode, nav);
            if (eph == null) {
                return 0;
            }
            ephpos.eph2pos(time, eph, rs, dts, var, k);
            time = util1.timeadd(time, tt);
            ephpos.eph2pos(time, eph, rst, dtst, var, k);
            svh[k] = eph.getSvh();
        } else if (sys == 4) {
            geph_t geph = seleph.sel_geph(teph, sat, iode, nav);
            if (geph == null) {
                return 0;
            }
            ephpos.geph2pos(time, geph, rs, dts, var, k);
            time = util1.timeadd(time, tt);
            ephpos.geph2pos(time, geph, rst, dtst, var, k);
            svh[k] = geph.getSvh();
        } else if (sys == 2) {
            seph_t seph = seleph.sel_seph(teph, sat, nav);
            if (seph == null) {
                return 0;
            }
            ephpos.seph2pos(time, seph, rs, dts, var, k);
            time = util1.timeadd(time, tt);
            ephpos.seph2pos(time, seph, rst, dtst, var, k);
            svh[k] = seph.getSvh();
        } else {
            return 0;
        }
        for (int i = 0; i < 3; ++i) {
            rs[i + 3] = (rst[i] - rs[i]) / tt;
        }
        dts[1] = (dtst[0] - dts[0]) / tt;
        return 1;
    }

    public static int satpos_sbas(gtime_t time, gtime_t teph, int[] sat, nav_t nav, double[] rs, double[] dts, double[] var, int[] svh, int k) {
        int i;
        sbssatp_t sbs = new sbssatp_t();
        for (i = 0; i < nav.sbssat.nsat; ++i) {
            sbs = nav.sbssat.sat[i];
            if (sbs.sat == sat[0]) break;
        }
        if (i >= nav.sbssat.nsat) {
            ephpos.eph_pos(time, teph, sat, nav, -1, rs, dts, var, svh, k);
            svh[k] = -1;
            return 0;
        }
        if (ephpos.eph_pos(time, teph, sat, nav, sbs.lcorr.iode, rs, dts, var, svh, k) == 0) {
            return 0;
        }
        svh[k] = -1;
        return 0;
    }

    public static void eph2pos(gtime_t time, eph_t eph, double[] rs, double[] dts, double[] var, int k) {
        int n;
        double mu;
        int[] prn = new int[1];
        int[] sys = new int[1];
        int[] sat = new int[]{eph.getSat()};
        if (eph.getA() <= 0.0) {
            var[k] = 0.0;
            rs[2] = 0.0;
            rs[1] = 0.0;
            rs[0] = 0.0;
            return;
        }
        double tk = util1.timediff(time, eph.getToe());
        sys[0] = satSys.sat_sys(sat[0], prn);
        double omge = switch (sys[0]) {
            case 8 -> {
                mu = 3.986004418E14;
                yield 7.2921151467E-5;
            }
            case 32 -> {
                mu = 3.986004418E14;
                yield 7.292115E-5;
            }
            default -> {
                mu = 3.986005E14;
                yield 7.2921151467E-5;
            }
        };
        double M = eph.getM0() + (Math.sqrt(mu / (eph.getA() * eph.getA() * eph.getA())) + eph.getDeln()) * tk;
        double E = M;
        double Ek = 0.0;
        for (n = 0; Math.abs(E - Ek) > 1.0E-13 && n < 30; ++n) {
            Ek = E;
            E -= (E - eph.getE() * Math.sin(E) - M) / (1.0 - eph.getE() * Math.cos(E));
        }
        if (n >= 30) {
            return;
        }
        double sinE = Math.sin(E);
        double cosE = Math.cos(E);
        double u = Math.atan2(Math.sqrt(1.0 - eph.getE() * eph.getE()) * sinE, cosE - eph.getE()) + eph.getOmg();
        double r = eph.getA() * (1.0 - eph.getE() * cosE);
        double i = eph.getI0() + eph.getIdot() * tk;
        double sin2u = Math.sin(2.0 * u);
        double cos2u = Math.cos(2.0 * u);
        double x = (r += eph.getCrs() * sin2u + eph.getCrc() * cos2u) * Math.cos(u += eph.getCus() * sin2u + eph.getCuc() * cos2u);
        double y = r * Math.sin(u);
        double cosi = Math.cos(i += eph.getCis() * sin2u + eph.getCic() * cos2u);
        if (sys[0] == 32 && (prn[0] <= 5 || prn[0] >= 59)) {
            double O = eph.getOMG0() + eph.getOMGd() * tk - omge * eph.getToes();
            double sinO = Math.sin(O);
            double cosO = Math.cos(O);
            double xg = x * cosO - y * cosi * sinO;
            double yg = x * sinO + y * cosi * cosO;
            double zg = y * Math.sin(i);
            double sino = Math.sin(omge * tk);
            double coso = Math.cos(omge * tk);
            rs[0] = xg * coso + yg * sino * 0.9961946980917455 + zg * sino * -0.0871557427476582;
            rs[1] = -xg * sino + yg * coso * 0.9961946980917455 + zg * coso * -0.0871557427476582;
            rs[2] = -yg * -0.0871557427476582 + zg * 0.9961946980917455;
        } else {
            double O = eph.getOMG0() + (eph.getOMGd() - omge) * tk - omge * eph.getToes();
            double sinO = Math.sin(O);
            double cosO = Math.cos(O);
            rs[0] = x * cosO - y * cosi * sinO;
            rs[1] = x * sinO + y * cosi * cosO;
            rs[2] = y * Math.sin(i);
        }
        tk = util1.timediff(time, eph.getToc());
        dts[0] = eph.getF0() + eph.getF1() * tk + eph.getF2() * tk * tk;
        dts[0] = dts[0] - 2.0 * Math.sqrt(mu * eph.getA()) * eph.getE() * sinE / util2.SQR(2.99792458E8);
        var[k] = ephpos.var_uraeph(sys[0], eph.getSva());
    }

    public static void deq(double[] x, double[] xdot, double[] acc) {
        double r2 = math.dot(x, x, 3);
        double r3 = r2 * Math.sqrt(r2);
        double omg2 = util2.SQR(7.292115E-5);
        if (r2 <= 0.0) {
            xdot[5] = 0.0;
            xdot[4] = 0.0;
            xdot[3] = 0.0;
            xdot[2] = 0.0;
            xdot[1] = 0.0;
            xdot[0] = 0.0;
            return;
        }
        double a = 6.47302620562962E11 * util2.SQR(6378136.0) / r2 / r3;
        double b = 5.0 * x[2] * x[2] / r2;
        double c = -3.9860044E14 / r3 - a * (1.0 - b);
        xdot[0] = x[3];
        xdot[1] = x[4];
        xdot[2] = x[5];
        xdot[3] = (c + omg2) * x[0] + 1.458423E-4 * x[4] + acc[0];
        xdot[4] = (c + omg2) * x[1] - 1.458423E-4 * x[3] + acc[1];
        xdot[5] = (c - 2.0 * a) * x[2] + acc[2];
    }

    public static void glorbit(double t, double[] x, double[] acc) {
        int i;
        double[] k1 = new double[6];
        double[] k2 = new double[6];
        double[] k3 = new double[6];
        double[] k4 = new double[6];
        double[] w = new double[6];
        ephpos.deq(x, k1, acc);
        for (i = 0; i < 6; ++i) {
            w[i] = x[i] + k1[i] * t / 2.0;
        }
        ephpos.deq(w, k2, acc);
        for (i = 0; i < 6; ++i) {
            w[i] = x[i] + k2[i] * t / 2.0;
        }
        ephpos.deq(w, k3, acc);
        for (i = 0; i < 6; ++i) {
            w[i] = x[i] + k3[i] * t;
        }
        ephpos.deq(w, k4, acc);
        for (i = 0; i < 6; ++i) {
            int n = i;
            x[n] = x[n] + (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]) * t / 6.0;
        }
    }

    public static void geph2pos(gtime_t time, geph_t geph, double[] rs, double[] dts, double[] var, int k) {
        double tt;
        int i;
        double[] x = new double[6];
        double t = util1.timediff(time, geph.getToe());
        dts[0] = -geph.getTaun() + geph.getGamn() * t;
        for (i = 0; i < 3; ++i) {
            x[i] = geph.getPos()[i];
            x[i + 3] = geph.getVel()[i];
        }
        double d = tt = t < 0.0 ? -60.0 : 60.0;
        while (Math.abs(t) > 1.0E-9) {
            if (Math.abs(t) < 60.0) {
                tt = t;
            }
            ephpos.glorbit(tt, x, geph.getAcc());
            t -= tt;
        }
        for (i = 0; i < 3; ++i) {
            rs[i] = x[i];
        }
        var[k] = util2.SQR(5.0);
    }

    public static void seph2pos(gtime_t time, seph_t seph, double[] rs, double[] dts, double[] var, int k) {
        double t = util1.timediff(time, seph.getT0());
        for (int i = 0; i < 3; ++i) {
            rs[i] = seph.pos[i] + seph.vel[i] * t + seph.acc[i] * t * t / 2.0;
        }
        dts[0] = seph.getAf0() + seph.getAf1() * t;
        var[k] = ephpos.var_uraeph(2, seph.getSva());
    }

    public static double var_uraeph(int sys, int ura) {
        double[] ura_value = new double[]{2.4, 3.4, 4.85, 6.85, 9.65, 13.65, 24.0, 48.0, 96.0, 192.0, 384.0, 768.0, 1536.0, 3072.0, 6144.0};
        if (sys == 8) {
            if (ura <= 49) {
                return util2.SQR((double)ura * 0.01);
            }
            if (ura <= 74) {
                return util2.SQR(0.5 + (double)(ura - 50) * 0.02);
            }
            if (ura <= 99) {
                return util2.SQR(1.0 + (double)(ura - 75) * 0.04);
            }
            if (ura <= 125) {
                return util2.SQR(2.0 + (double)(ura - 100) * 0.16);
            }
            return util2.SQR(500.0);
        }
        return ura < 0 || 14 < ura ? util2.SQR(6144.0) : util2.SQR(ura_value[ura]);
    }
}

