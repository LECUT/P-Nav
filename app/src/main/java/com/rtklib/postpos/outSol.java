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
import com.rtklib.bean.sol_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.pntpos.estpos;
import com.rtklib.pntpos.rescode;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class outSol {
    static int TIMES_UTC = 1;
    static int TIMES_JST = 2;
    public static final double[] range = new double[]{0.0, 360.0, -90.0, 90.0};
    public static final int GEOID_EMBEDDED = 0;
    public static int MAXSOLMSG = 8191;
    public static int SOLF_NMEA = 3;
    public static int SOLF_ENU = 2;
    public static final int SOLF_LLH = 0;
    public static final int SOLF_XYZ = 1;
    public static int SOLF_STAT = 4;
    public static int SOLF_GSIF = 5;
    public static int model_geoid = 0;

    public static char[] opt2sep(solopt_t opt) {
        if (opt.sep == null) {
            return " ".toCharArray();
        }
        if (opt.sep.equals("\\t")) {
            return "\t".toCharArray();
        }
        return opt.sep;
    }

    public static double sol_std(sol_t sol) {
        if (sol.qr[0] > sol.qr[1] && sol.qr[0] > sol.qr[2]) {
            return estpos.SQRT(sol.qr[0]);
        }
        if (sol.qr[1] > sol.qr[2]) {
            return estpos.SQRT(sol.qr[1]);
        }
        return estpos.SQRT(sol.qr[2]);
    }

    public static void soltocov(sol_t sol, double[] P) {
        P[0] = sol.qr[0];
        P[4] = sol.qr[1];
        P[8] = sol.qr[2];
        P[1] = P[3] = (double)sol.qr[3];
        P[5] = P[7] = (double)sol.qr[4];
        P[2] = P[6] = (double)sol.qr[5];
    }

    public static void covenu(double[] pos, double[] P, double[] Q) {
        double[] E = new double[9];
        double[] EP = new double[9];
        rescode.xyz2enu(pos, E);
        math.matmul("NN", 3, 3, 3, 1.0, E, P, 0.0, EP);
        math.matmul("NT", 3, 3, 3, 1.0, EP, E, 0.0, Q);
    }

    public static double interpb(double[] y, double a, double b) {
        return y[0] * (1.0 - a) * (1.0 - b) + y[1] * a * (1.0 - b) + y[2] * (1.0 - a) * b + y[3] * a * b;
    }

    public static double geoidh_emb(double[] pos) {
        double dlon = 1.0;
        double dlat = 1.0;
        double[] y = new double[4];
        if (pos[1] < range[0] || range[1] < pos[1] || pos[0] < range[2] || range[3] < pos[0]) {
            System.out.println("out of geoid model range. in geoidh_emb");
            return 0.0;
        }
        double a = (pos[1] - range[0]) / dlon;
        double b = (pos[0] - range[2]) / dlat;
        int i1 = (int)a;
        int i2 = i1 < 360 ? i1 + 1 : i1;
        int j1 = (int)b;
        int j2 = j1 < 180 ? j1 + 1 : j1;
        return outSol.interpb(y, a -= (double)i1, b -= (double)j1);
    }

    public static double geoidh(double[] pos) {
        double h;
        double[] posd = new double[2];
        posd[1] = pos[1] * 57.29577951308232;
        posd[0] = pos[0] * 57.29577951308232;
        if (posd[1] < 0.0) {
            posd[1] = posd[1] + 360.0;
        }
        if (posd[1] < 0.0 || 359.999999999999 < posd[1] || posd[0] < -90.0 || 90.0 < posd[0]) {
            return 0.0;
        }
        switch (model_geoid) {
            case 0: {
                h = outSol.geoidh_emb(posd);
                break;
            }
            default: {
                return 0.0;
            }
        }
        if (Math.abs(h) > 200.0) {
            return 0.0;
        }
        return h;
    }

    public static void deg2dms(double deg, double[] dms, int ndec) {
        double sign = deg < 0.0 ? -1.0 : 1.0;
        double a = Math.abs(deg);
        double unit = Math.pow(0.1, ndec);
        dms[0] = Math.floor(a);
        a = (a - dms[0]) * 60.0;
        dms[1] = Math.floor(a);
        a = (a - dms[1]) * 60.0;
        dms[2] = Math.floor(a / unit + 0.5) * unit;
        if (dms[2] >= 60.0) {
            dms[2] = 0.0;
            dms[1] = dms[1] + 1.0;
            if (dms[1] >= 60.0) {
                dms[1] = 0.0;
                dms[0] = dms[0] + 1.0;
            }
        }
        dms[0] = dms[0] * sign;
    }

    public static double sqvar(double covar) {
        return covar < 0.0 ? -Math.sqrt(-covar) : Math.sqrt(covar);
    }

    public static void soltocov_vel(sol_t sol, double[] P) {
        P[0] = sol.qv[0];
        P[4] = sol.qv[1];
        P[8] = sol.qv[2];
        P[1] = P[3] = (double)sol.qv[3];
        P[5] = P[7] = (double)sol.qv[4];
        P[2] = P[6] = (double)sol.qv[5];
    }

    public static int outpos(String[] p, char[] s, sol_t sol, solopt_t opt) {
        double[] pos = new double[3];
        double[] vel = new double[3];
        double[] dms1 = new double[3];
        double[] dms2 = new double[3];
        double[] P = new double[9];
        double[] Q = new double[9];
        char[] sep = outSol.opt2sep(opt);
        StringBuilder sb = new StringBuilder();
        rescode.ecef2pos(sol.rr, pos);
        outSol.soltocov(sol, P);
        outSol.covenu(pos, P, Q);
        if (opt.height == 1) {
            pos[2] = pos[2] - outSol.geoidh(pos);
        }
        if (opt.degf != 0) {
            outSol.deg2dms(pos[0] * 57.29577951308232, dms1, 5);
            outSol.deg2dms(pos[1] * 57.29577951308232, dms2, 5);
            String temps = String.valueOf(s).trim();
            sb.append(temps).append(sep).append(String.format(Locale.US, "%4.0f", dms1[0])).append(sep).append(String.format(Locale.US, "%02.0f", dms1[1])).append(sep).append(String.format(Locale.US, "%08.5f", dms1[2])).append(sep).append(String.format(Locale.US, "%4.0f", dms2[0])).append(sep).append(String.format(Locale.US, "%02.0f", dms2[1])).append(sep).append(String.format(Locale.US, "%08.5f", dms2[2]));
        } else {
            sb.append(s).append(sep).append(String.format(Locale.US, "%14.9f", pos[0] * 57.29577951308232)).append(sep).append(String.format(Locale.US, "%14.9f", pos[1] * 57.29577951308232));
        }
        sb.append(sep).append(String.format(Locale.US, "%10.4f", pos[2])).append(sep).append(sol.stat).append(sep).append(sol.ns).append(sep).append(String.format(Locale.US, "%8.4f", Math.sqrt(Q[4]))).append(sep).append(String.format(Locale.US, "%8.4f", Math.sqrt(Q[0]))).append(sep).append(String.format(Locale.US, "%8.4f", Math.sqrt(Q[8]))).append(sep).append(String.format(Locale.US, "%8.4f", outSol.sqvar(Q[1]))).append(sep).append(String.format(Locale.US, "%8.4f", outSol.sqvar(Q[2]))).append(sep).append(String.format(Locale.US, "%8.4f", outSol.sqvar(Q[5]))).append(sep).append(String.format(Locale.US, "%6.2f", Float.valueOf(sol.age))).append(sep).append(String.format(Locale.US, "%6.1f", Float.valueOf(sol.ratio)));
        if (opt.outvel != 0) {
            outSol.soltocov_vel(sol, P);
            rescode.ecef2enu(pos, Arrays.copyOfRange(sol.rr, 3, 6), vel);
            outSol.covenu(pos, P, Q);
            sb.append(sep).append(String.format(Locale.US, "%10.5f", vel[1])).append(sep).append(String.format(Locale.US, "%10.5f", vel[0])).append(sep).append(String.format(Locale.US, "%10.5f", vel[2])).append(sep).append(String.format(Locale.US, "%9.5f", Math.sqrt(Q[4]))).append(sep).append(String.format(Locale.US, "%8.5f", Math.sqrt(Q[0]))).append(sep).append(String.format(Locale.US, "%8.5f", Math.sqrt(Q[8]))).append(sep).append(String.format(Locale.US, "%8.5f", outSol.sqvar(Q[1]))).append(sep).append(String.format(Locale.US, "%8.5f", outSol.sqvar(Q[2]))).append(sep).append(String.format(Locale.US, "%8.5f", outSol.sqvar(Q[5])));
        }
        sb.append("\r\n");
        p[0] = sb.toString().trim();
        return sb.toString().length();
    }

    public static int outecef(String[] p, char[] s, sol_t sol, solopt_t opt) {
        char[] sep = outSol.opt2sep(opt);
        StringBuilder sb = new StringBuilder();
        String temps = String.valueOf(s).trim();
        sb.append(temps);
        sb.append(sep).append(String.format(Locale.US, "%14.4f", sol.rr[0])).append(sep).append(String.format(Locale.US, "%14.4f", sol.rr[1])).append(sep).append(String.format(Locale.US, "%14.4f", sol.rr[2])).append(sep).append(String.format(Locale.US, "%3d", sol.stat)).append(sep).append(String.format(Locale.US, "%3d", sol.ns)).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[0]))).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[1]))).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[2]))).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[3]))).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[4]))).append(sep).append(String.format(Locale.US, "%8.4f", estpos.SQRT(sol.qr[5]))).append(sep).append(String.format(Locale.US, "%6.2f", Float.valueOf(sol.age))).append(sep).append(String.format(Locale.US, "%6.1f", Float.valueOf(sol.ratio)));
        sb.append("\r\n");
        p[0] = sb.toString().trim();
        return sb.toString().length();
    }

    public static int outsols(String[] pp, sol_t sol, double[] rb, solopt_t opt) {
        gtime_t ts = new gtime_t();
        int[] week = new int[1];
        char[] sep = outSol.opt2sep(opt);
        char[] s = new char[64];
        if (opt.maxsolstd > 0.0 && outSol.sol_std(sol) > opt.maxsolstd) {
            return 0;
        }
        if (opt.posf == SOLF_NMEA) {
            if (opt.nmeaintv[0] < 0.0) {
                return 0;
            }
            if (!util1.screent(sol.time, ts, ts, opt.nmeaintv[0])) {
                return 0;
            }
        }
        if (sol.stat <= 0 || opt.posf == SOLF_ENU && math.norm(rb, 3) <= 0.0) {
            return 0;
        }
        int timeu = opt.timeu < 0 ? 0 : Math.min(opt.timeu, 20);
        gtime_t time = sol.time;
        if (opt.timef != 0) {
            util1.time2str(time, s, timeu);
        } else {
            double gpst = util1.time2gpst(time, week);
            if (604800.0 - gpst < 0.5 / Math.pow(10.0, timeu)) {
                week[0] = week[0] + 1;
                gpst = 0.0;
            }
            util1.sprintf(s, "%4d%.16s%10.3f", week[0], Character.valueOf(sep[0]), gpst);
        }
        switch (opt.posf) {
            case 0: {
                outSol.outpos(pp, s, sol, opt);
                break;
            }
            case 1: {
                outSol.outecef(pp, s, sol, opt);
            }
        }
        return pp.length;
    }

    public static void outsol(BufferedWriter bufferedWriter, sol_t sol, double[] rb, solopt_t opt) throws IOException {
        byte[] buff = new byte[MAXSOLMSG + 1];
        String[] pp = new String[1];
        if (outSol.outsols(pp, sol, rb, opt) > 0) {
            bufferedWriter.write(pp[0]);
            bufferedWriter.newLine();
        }
    }
}

