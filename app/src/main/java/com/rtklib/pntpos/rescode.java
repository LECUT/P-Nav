/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.pntpos;

import android.util.Log;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.snrmask_t;
import com.rtklib.pntpos.seleph;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.util.Arrays;

public class rescode {
    public static final double RE_WGS84 = 6378137.0;
    public static final double FE_WGS84 = 0.0033528106647474805;
    public static final double MAX_VAR_EPH = util2.SQR(300.0);
    public static final int IONOOPT_IFLC = 3;
    public static final double R2D = 57.29577951308232;
    public static final double ERR_ION = 5.0;
    public static final double ERR_TROP = 3.0;
    public static final double ERR_SAAS = 0.3;
    public static final double ERR_BRDCI = 0.5;
    public static final double ERR_CBIAS = 0.3;
    public static final double REL_HUMI = 0.7;
    public static final int IONOOPT_OFF = 0;
    public static final int IONOOPT_BRDC = 1;
    public static final int IONOOPT_SBAS = 2;
    public static final int IONOOPT_EST = 4;
    public static final int IONOOPT_TEC = 5;
    public static final int IONOOPT_QZS = 6;
    public static final int IONOOPT_STEC = 8;
    public static final int TROPOPT_EST = 3;
    public static final int TROPOPT_ESTG = 4;
    public static final int TROPOPT_OFF = 0;
    public static final int TROPOPT_SAAS = 1;
    public static final int TROPOPT_SBAS = 2;
    public static final int TROPOPT_ZTD = 5;
    public static final int CODE_NONE = 0;
    public static final int CODE_L1C = 1;
    public static final int CODE_L1P = 2;
    public static final int CODE_L2C = 14;
    public static final int CODE_L2I = 40;
    public static double EFACT_GPS = 1.0;
    public static double EFACT_GLO = 1.5;
    public static double EFACT_GPS_L5 = 10.0;
    double EFACT_GAL = 1.0;
    double EFACT_QZS = 1.0;
    double EFACT_CMP = 1.0;
    double EFACT_IRN = 1.5;
    public static final double MIN_EL = 0.08726646259971647;
    public static final double MIN_HGT = -1000.0;
    public static final double VAR_NOTEC = util2.SQR(30.0);
    public static final double VAR_GLO_IFB = util2.SQR(0.6);
    public static final double THRES_REJECT = 4.0;
    public static double EFACT_SBS = 3.0;

    public static int res_code(int iter, obsd_t[] obs, int n, double[] rs, double[] dts, double[] vare, int[] svh, nav_t nav, double[] x, prcopt_t opt, double[] v, double[] H, double[] var, double[] azel, int[] vsat, double[] resp, int[] ns, int nx) {
        int j;
        int i;
        double[] dion = new double[1];
        double[] vion = new double[1];
        double[] vmeas = new double[1];
        double[] dtrp = new double[1];
        double[] vtrp = new double[1];
        double[] rr = new double[3];
        double[] pos = new double[3];
        double[] e = new double[3];
        int nv = 0;
        int[] mask = new int[5];
        // ★ DIAG 计数器：每颗卫星被过滤的原因
        int cntSys0=0, cntDup=0, cntSatex=0, cntGeodist=0;
        int cntElmin=0, cntIono=0, cntFreq=0, cntTrop=0, cntPrange=0, cntOk=0;
        int[] lastFailSat = new int[1];  String lastFailReason = "";
        for (i = 0; i < 3; ++i) {
            rr[i] = x[i];
        }
        double dtr = x[3];
        rescode.ecef2pos(rr, pos);
        ns[0] = 0;
        for (i = 0; i < n && i < 96; ++i) {
            double P;
            double d;
            vsat[i] = 0;
            resp[i] = 0.0;
            azel[i * 2 + 1] = 0.0;
            azel[i * 2] = 0.0;
            gtime_t time = obs[i].time;
            int[] sat = obs[i].sat;
            int sys = satSys.sat_sys(sat[0], null);
            if (sys == 0) { cntSys0++; continue; }
            if (i < n - 1 && i < 95 && sat == obs[i + 1].sat) {
                ++i;
                continue;
            }
            if (rescode.satexclude(sat, vare[i], svh[i], opt) == 1) { cntSatex++; lastFailSat[0]=sat[0]; lastFailReason="satexclude"; continue; }
            double r = rescode.geodist(Arrays.copyOfRange(rs, i * 6, i * 6 + 6), rr, e);
            if (r <= 0.0) { cntGeodist++; lastFailSat[0]=sat[0]; lastFailReason="geodist"; continue; }
            if (iter > 0) {
                double freq;
                double[] tempazel = Arrays.copyOfRange(azel, i * 2, i * 2 + 2);
                if (rescode.satazel(pos, e, tempazel) < opt.getElmin()) {
                    azel[i * 2] = tempazel[0];
                    azel[i * 2 + 1] = tempazel[1];
                    cntElmin++; lastFailSat[0]=sat[0]; lastFailReason="elmin";
                    continue;
                }
                azel[i * 2] = tempazel[0];
                azel[i * 2 + 1] = tempazel[1];
                if (rescode.ionocorr(time, nav, sat, pos, Arrays.copyOfRange(azel, i * 2, i * 2 + 3), opt.ionoopt, dion, vion) == 0 || (freq = rescode.sat2freq(sat, obs[i].code[0], nav)) == 0.0) { cntIono++; lastFailSat[0]=sat[0]; lastFailReason="iono/freq"; continue; }
                dion[0] = dion[0] * util2.SQR(1.57542E9 / freq);
                vion[0] = vion[0] * util2.SQR(1.57542E9 / freq);
                if (rescode.tropcorr(time, nav, pos, Arrays.copyOfRange(azel, i * 2, i * 2 + 3), opt.tropopt, dtrp, vtrp) != 1) { cntTrop++; lastFailSat[0]=sat[0]; lastFailReason="trop"; continue; }
            }
            if ((P = rescode.prange(obs[i], nav, opt, vmeas)) == 0.0) { cntPrange++; lastFailSat[0]=sat[0]; lastFailReason="prange"; continue; }
            v[nv] = P - (r + dtr - 2.99792458E8 * dts[i * 2] + dion[0] + dtrp[0]);
            for (j = 0; j < nx; ++j) {
                H[j + nv * nx] = j < 3 ? -e[j] : (j == 3 ? 1.0 : 0.0);
            }
            if (sys == 4) {
                int n2 = nv;
                v[n2] = v[n2] - x[4];
                H[4 + nv * nx] = 1.0;
                mask[1] = 1;
            } else if (sys == 8) {
                int n3 = nv;
                v[n3] = v[n3] - x[5];
                H[5 + nv * nx] = 1.0;
                mask[2] = 1;
            } else if (sys == 32) {
                int n4 = nv;
                v[n4] = v[n4] - x[6];
                H[6 + nv * nx] = 1.0;
                mask[3] = 1;
            } else if (sys == 64) {
                int n5 = nv;
                v[n5] = v[n5] - x[7];
                H[7 + nv * nx] = 1.0;
                mask[4] = 1;
            } else if (sys == 16) {
                int n6 = nv;
                v[n6] = v[n6] - x[7];
                H[7 + nv * nx] = 1.0;
                mask[4] = 1;
            } else {
                mask[0] = 1;
            }
            vsat[i] = 1;
            resp[i] = v[nv];
            ns[0] = ns[0] + 1;
            cntOk++;
            var[nv++] = rescode.varerr(opt, azel[1 + i * 2], obs[i].SNR[0], sys) + vare[i] + vmeas[0] + vion[0] + vtrp[0];
        }
        // ★ 填充未使用的星座钟差约束行（RK 标准流程）
        for (i = 0; i < 5 && i + 3 < nx; ++i) {
            if (mask[i] != 0) continue;
            v[nv] = 0.0;
            for (j = 0; j < nx; ++j) {
                H[j + nv * nx] = j == i + 3 ? 1.0 : 0.0;
            }
            var[nv++] = 0.01;
        }
        // ★ 每个历元输出一次诊断（仅在 iter==0 时，避免循环内刷屏）
        if (iter == 0) {
            int totalFail = cntSys0+cntDup+cntSatex+cntGeodist+cntElmin+cntIono+cntTrop+cntPrange;
            Log.d("DIAG_RESCODE", String.format(
                "rescode n=%d nx=%d nv=%d ok=%d | fail: sys0=%d satex=%d geodist=%d elmin=%d iono=%d trop=%d prange=%d | lastFail=PRN%d(%s)",
                n, nx, nv, cntOk, cntSys0, cntSatex, cntGeodist, cntElmin, cntIono, cntTrop, cntPrange,
                lastFailSat[0], lastFailReason));
            // ★ nv < nx 时额外输出排查提示
            if (nv < nx) {
                Log.w("DIAG_RESCODE", String.format(
                    "nv(%d) < nx(%d) — 解算将跳过。排查: n=%d nav.n(eph)=? satex=%d geodist=%d prange=%d",
                    nv, nx, n, cntSatex, cntGeodist, cntPrange));
            }
        }
        return nv;
    }

    public static void ecef2pos(double[] r, double[] pos) {
        double e2 = 0.0066943799901413165;
        double r2 = math.dot(r, r, 2);
        double v = 6378137.0;
        double z = r[2];
        double zk = 0.0;
        while (Math.abs(z - zk) >= 1.0E-4) {
            zk = z;
            double sinp = z / Math.sqrt(r2 + z * z);
            v = 6378137.0 / Math.sqrt(1.0 - e2 * sinp * sinp);
            z = r[2] + v * e2 * sinp;
        }
        pos[0] = r2 > 1.0E-12 ? Math.atan(z / Math.sqrt(r2)) : (r[2] > 0.0 ? 1.5707963267948966 : -1.5707963267948966);
        pos[1] = r2 > 1.0E-12 ? Math.atan2(r[1], r[0]) : 0.0;
        pos[2] = Math.sqrt(r2 + z * z) - v;
    }

    public static void ecef2enu(double[] pos, double[] r, double[] e) {
        double[] E = new double[9];
        rescode.xyz2enu(pos, E);
        math.matmul("NN", 3, 1, 3, 1.0, E, r, 0.0, e);
    }

    public static void xyz2enu(double[] pos, double[] E) {
        double sinp = Math.sin(pos[0]);
        double cosp = Math.cos(pos[0]);
        double sinl = Math.sin(pos[1]);
        double cosl = Math.cos(pos[1]);
        E[0] = -sinl;
        E[3] = cosl;
        E[6] = 0.0;
        E[1] = -sinp * cosl;
        E[4] = -sinp * sinl;
        E[7] = cosp;
        E[2] = cosp * cosl;
        E[5] = cosp * sinl;
        E[8] = sinp;
    }

    public static int satexclude(int[] sat, double var, int svh, prcopt_t opt) {
        int sys = satSys.sat_sys(sat[0], null);
        if (svh < 0) {
            return 1;
        }
        if (sys == 16) {
            svh &= 0xFE;
        }
        if (svh != 0) {
            return 1;
        }
        if (var > MAX_VAR_EPH) {
            return 1;
        }
        return 0;
    }

    public static double geodist(double[] rs, double[] rr, double[] e) {
        int i;
        if (math.norm(rs, 3) < 6378137.0) {
            return -1.0;
        }
        for (i = 0; i < 3; ++i) {
            e[i] = rs[i] - rr[i];
        }
        double r = math.norm(e, 3);
        i = 0;
        while (i < 3) {
            int n = i++;
            e[n] = e[n] / r;
        }
        return r + 7.2921151467E-5 * (rs[0] * rr[1] - rs[1] * rr[0]) / 2.99792458E8;
    }

    public static double satazel(double[] pos, double[] e, double[] azel, int index) {
        double az = 0.0;
        double el = 1.5707963267948966;
        double[] enu = new double[3];
        if (pos[2] > -6378137.0) {
            rescode.ecef2enu(pos, e, enu);
            double d = az = math.dot(enu, enu, 2) < 1.0E-12 ? 0.0 : Math.atan2(enu[0], enu[1]);
            if (az < 0.0) {
                az += Math.PI * 2;
            }
            el = Math.asin(enu[2]);
        }
        if (azel != null) {
            azel[index] = az;
            azel[index + 1] = el;
        }
        return el;
    }

    public static double satazel(double[] pos, double[] e, double[] azel) {
        double az = 0.0;
        double el = 1.5707963267948966;
        double[] enu = new double[3];
        if (pos[2] > -6378137.0) {
            rescode.ecef2enu(pos, e, enu);
            double d = az = math.dot(enu, enu, 2) < 1.0E-12 ? 0.0 : Math.atan2(enu[0], enu[1]);
            if (az < 0.0) {
                az += Math.PI * 2;
            }
            el = Math.asin(enu[2]);
        }
        if (azel != null) {
            azel[0] = az;
            azel[1] = el;
        }
        return el;
    }

    public static int snrmask(obsd_t obs, double[] azel, prcopt_t opt) {
        if (rescode.testsnr(0, 0, azel[1], obs.SNR[0], opt.getSnrmask())) {
            return 0;
        }
        if (opt.ionoopt == 3 && rescode.testsnr(0, 1, azel[1], obs.SNR[1], opt.getSnrmask())) {
            return 0;
        }
        return 1;
    }

    public static boolean testsnr(int base, int idx, double el, double snr, snrmask_t mask) {
        if (mask.ena[base] == 0 || idx < 0 || idx >= 3) {
            return Boolean.FALSE;
        }
        double a = (el * 57.29577951308232 + 5.0) / 10.0;
        int i = (int)Math.floor(a);
        double minsnr = i < 1 ? mask.mask[idx][0] : (i > 8 ? mask.mask[idx][8] : (1.0 - (a -= (double)i)) * mask.mask[idx][i - 1] + a * mask.mask[idx][i]);
        return snr < minsnr;
    }

    public static int ionocorr(gtime_t time, nav_t nav, int[] sat, double[] pos, double[] azel, int ionoopt, double[] ion, double[] var) {
        if (ionoopt == 1) {
            ion[0] = rescode.ionmodel(time, nav.ion_gps, pos, azel);
            var[0] = util2.SQR(ion[0] * 0.5);
            return 1;
        }
        if (ionoopt == 2) {
            // empty if block
        }
        if (ionoopt == 5) {
            // empty if block
        }
        if (ionoopt == 6 && math.norm(nav.ion_qzs, 8) > 0.0) {
            ion[0] = rescode.ionmodel(time, nav.ion_qzs, pos, azel);
            var[0] = util2.SQR(ion[0] * 0.5);
            return 1;
        }
        ion[0] = 0.0;
        var[0] = ionoopt == 0 ? util2.SQR(5.0) : 0.0;
        return 1;
    }

    public static double ionmodel(gtime_t t, double[] ion, double[] pos, double[] azel) {
        double psi;
        double phi;
        double[] ion_default = new double[]{1.118E-8, -7.451E-9, -5.961E-8, 1.192E-7, 116700.0, -229400.0, -131100.0, 1049000.0};
        int[] week = new int[1];
        if (pos[2] < -1000.0 || azel[1] <= 0.0) {
            return 0.0;
        }
        if (math.norm(ion, 8) <= 0.0) {
            ion = ion_default;
        }
        if ((phi = pos[0] / Math.PI + (psi = 0.0137 / (azel[1] / Math.PI + 0.11) - 0.022) * Math.cos(azel[0])) > 0.416) {
            phi = 0.416;
        } else if (phi < -0.416) {
            phi = -0.416;
        }
        double lam = pos[1] / Math.PI + psi * Math.sin(azel[0]) / Math.cos(phi * Math.PI);
        double tt = 43200.0 * lam + util1.time2gpst(t, week);
        tt -= Math.floor(tt / 86400.0) * 86400.0;
        double f = 1.0 + 16.0 * Math.pow(0.53 - azel[1] / Math.PI, 3.0);
        double amp = ion[0] + (phi += 0.064 * Math.cos((lam - 1.617) * Math.PI)) * (ion[1] + phi * (ion[2] + phi * ion[3]));
        double per = ion[4] + phi * (ion[5] + phi * (ion[6] + phi * ion[7]));
        amp = Math.max(amp, 0.0);
        per = Math.max(per, 72000.0);
        double x = Math.PI * 2 * (tt - 50400.0) / per;
        return 2.99792458E8 * f * (Math.abs(x) < 1.57 ? 5.0E-9 + amp * (1.0 + x * x * (-0.5 + x * x / 24.0)) : 5.0E-9);
    }

    public static double sat2freq(int[] sat, int code, nav_t nav) {
        int fcn = 0;
        int[] prn = new int[1];
        int sys = satSys.sat_sys(sat[0], prn);
        if (sys == 4) {
            int i;
            if (nav == null) {
                return 0.0;
            }
            for (i = 0; i < nav.ng && nav.geph[i].getSat() != sat[0]; ++i) {
            }
            if (i < nav.ng) {
                fcn = nav.geph[i].frq;
            } else if (nav.glo_fcn[prn[0] - 1] > 0) {
                fcn = nav.glo_fcn[prn[0] - 1] - 8;
            } else {
                return 0.0;
            }
        }
        return rescode.code2freq(sys, code, fcn);
    }

    public static double sat2freq(int sat, int code, nav_t nav) {
        int fcn = 0;
        int[] prn = new int[1];
        int sys = satSys.sat_sys(sat, prn);
        if (sys == 4) {
            int i;
            if (nav == null) {
                return 0.0;
            }
            for (i = 0; i < nav.ng && nav.geph[i].getSat() != sat; ++i) {
            }
            if (i < nav.ng) {
                fcn = nav.geph[i].frq;
            } else if (nav.glo_fcn[prn[0] - 1] > 0) {
                fcn = nav.glo_fcn[prn[0] - 1] - 8;
            } else {
                return 0.0;
            }
        }
        return rescode.code2freq(sys, code, fcn);
    }

    public static double code2freq(int sys, int code, int fcn) {
        double[] freq = new double[1];
        switch (sys) {
            case 1: {
                util2.code2freq_GPS(code, freq);
                break;
            }
            case 4: {
                util2.code2freq_GLO(code, fcn, freq);
                break;
            }
            case 8: {
                util2.code2freq_GAL(code, freq);
                break;
            }
            case 16: {
                util2.code2freq_QZS(code, freq);
                break;
            }
            case 2: {
                util2.code2freq_SBS(code, freq);
                break;
            }
            case 32: {
                util2.code2freq_BDS(code, freq);
                break;
            }
            case 64: {
                util2.code2freq_IRN(code, freq);
            }
        }
        return freq[0];
    }

    public static int tropcorr(gtime_t time, nav_t nav, double[] pos, double[] azel, int tropopt, double[] trp, double[] var) {
        if (tropopt == 1 || tropopt == 3 || tropopt == 4) {
            trp[0] = rescode.tropmodel(time, pos, azel, 0.7);
            var[0] = util2.SQR(0.3 / (Math.sin(azel[1]) + 0.1));
            return 1;
        }
        if (tropopt == 2) {
            trp[0] = rescode.sbstropcorr(time, pos, azel, var);
            return 1;
        }
        trp[0] = 0.0;
        var[0] = tropopt == 0 ? util2.SQR(3.0) : 0.0;
        return 1;
    }

    public static double mapf(double el, double a, double b, double c) {
        double sinel = Math.sin(el);
        return (1.0 + a / (1.0 + b / (1.0 + c))) / (sinel + a / (sinel + b / (sinel + c)));
    }

    public static double interpc(double[] coef, double lat) {
        int i = (int)(lat / 15.0);
        if (i < 1) {
            return coef[0];
        }
        if (i > 4) {
            return coef[4];
        }
        return coef[i - 1] * (1.0 - lat / 15.0 + (double)i) + coef[i] * (lat / 15.0 - (double)i);
    }

    public static double nmf(gtime_t time, double[] pos, double[] azel, double[] mapfw) {
        double[][] coef = new double[][]{{0.0012769934, 0.001268323, 0.0012465397, 0.0012196049, 0.0012045996}, {0.0029153695, 0.0029152299, 0.0029288445, 0.0029022565, 0.0029024912}, {0.062610505, 0.062837393, 0.063721774, 0.063824265, 0.064258455}, {0.0, 1.2709626E-5, 2.6523662E-5, 3.4000452E-5, 4.1202191E-5}, {0.0, 2.1414979E-5, 3.0160779E-5, 7.2562722E-5, 1.1723375E-4}, {0.0, 9.01284E-5, 4.3497037E-5, 8.4795348E-4, 0.0017037206}, {5.8021897E-4, 5.6794847E-4, 5.8118019E-4, 5.9727542E-4, 6.1641693E-4}, {0.0014275268, 0.0015138625, 0.0014572752, 0.0015007428, 0.0017599082}, {0.043472961, 0.04672951, 0.043908931, 0.044626982, 0.054736038}};
        double[] aht = new double[]{2.53E-5, 0.00549, 0.00114};
        double[] ah = new double[3];
        double[] aw = new double[3];
        double el = azel[1];
        double lat = pos[0] * 57.29577951308232;
        double hgt = pos[2];
        if (el <= 0.0) {
            if (mapfw != null) {
                mapfw[0] = 0.0;
            }
            return 0.0;
        }
        double y = (util1.time2doy(time) - 28.0) / 365.25 + (lat < 0.0 ? 0.5 : 0.0);
        double cosy = Math.cos(Math.PI * 2 * y);
        lat = Math.abs(lat);
        for (int i = 0; i < 3; ++i) {
            ah[i] = rescode.interpc(coef[i], lat) - rescode.interpc(coef[i + 3], lat) * cosy;
            aw[i] = rescode.interpc(coef[i + 6], lat);
        }
        double dm = (1.0 / Math.sin(el) - rescode.mapf(el, aht[0], aht[1], aht[2])) * hgt / 1000.0;
        if (mapfw != null) {
            mapfw[0] = rescode.mapf(el, aw[0], aw[1], aw[2]);
        }
        return rescode.mapf(el, ah[0], ah[1], ah[2]) + dm;
    }

    public static double tropmapf(gtime_t time, double[] pos, double[] azel, double[] mapfw) {
        if (pos[2] < -1000.0 || pos[2] > 20000.0) {
            if (mapfw != null) {
                mapfw[0] = 0.0;
            }
            return 0.0;
        }
        return rescode.nmf(time, pos, azel, mapfw);
    }

    public static double tropmodel(gtime_t time, double[] pos, double[] azel, double humi) {
        double temp0 = 15.0;
        if (pos[2] < -100.0 || 10000.0 < pos[2] || azel[1] <= 0.0) {
            return 0.0;
        }
        double hgt = Math.max(pos[2], 0.0);
        double pres = 1013.25 * Math.pow(1.0 - 2.2557E-5 * hgt, 5.2568);
        double temp = 15.0 - 0.0065 * hgt + 273.16;
        double e = 6.108 * humi * Math.exp((17.15 * temp - 4684.0) / (temp - 38.45));
        double z = 1.5707963267948966 - azel[1];
        double trph = 0.0022768 * pres / (1.0 - 0.00266 * Math.cos(2.0 * pos[0]) - 2.8E-4 * hgt / 1000.0) / Math.cos(z);
        double trpw = 0.002277 * (1255.0 / temp + 0.05) * e / Math.cos(z);
        return trph + trpw;
    }

    public static double sbstropcorr(gtime_t time, double[] pos, double[] azel, double[] var) {
        double k1 = 77.604;
        double k2 = 382000.0;
        double rd = 287.054;
        double gm = 9.784;
        double g = 9.80665;
        double zh = 0.0;
        double zw = 0.0;
        double[] pos_ = new double[3];
        double[] met = new double[10];
        double sinel = Math.sin(azel[1]);
        double h = pos[2];
        if (pos[2] < -100.0 || 10000.0 < pos[2] || azel[1] <= 0.0) {
            var[0] = 0.0;
            return 0.0;
        }
        if (zh == 0.0 || Math.abs(pos[0] - pos_[0]) > 1.0E-7 || Math.abs(pos[1] - pos_[1]) > 1.0E-7 || Math.abs(pos[2] - pos_[2]) > 1.0) {
            int i;
            rescode.getmet(pos[0] * 57.29577951308232, met);
            double c = Math.cos(Math.PI * 2 * (util1.time2doy(time) - (pos[0] >= 0.0 ? 28.0 : 211.0)) / 365.25);
            for (i = 0; i < 5; ++i) {
                int n = i;
                met[n] = met[n] - met[i + 5] * c;
            }
            zh = 0.022276538615999996 * met[0] / 9.784;
            zw = 109.65462799999999 / (9.784 * (met[4] + 1.0) - met[3] * 287.054) * met[2] / met[1];
            zh *= Math.pow(1.0 - met[3] * h / met[1], 9.80665 / (287.054 * met[3]));
            zw *= Math.pow(1.0 - met[3] * h / met[1], (met[4] + 1.0) * 9.80665 / (287.054 * met[3]) - 1.0);
            for (i = 0; i < 3; ++i) {
                pos_[i] = pos[i];
            }
        }
        double m = 1.001 / Math.sqrt(0.002001 + sinel * sinel);
        var[0] = 0.0144 * m * m;
        return (zh + zw) * m;
    }

    public static void getmet(double lat, double[] met) {
        double[][] metprm = new double[][]{{1013.25, 299.65, 26.31, 0.0063, 2.77, 0.0, 0.0, 0.0, 0.0, 0.0}, {1017.25, 294.15, 21.79, 0.00605, 3.15, -3.75, 7.0, 8.85, 2.5E-4, 0.33}, {1015.75, 283.15, 11.66, 0.00558, 2.57, -2.25, 11.0, 7.24, 3.2E-4, 0.46}, {1011.75, 272.15, 6.78, 0.00539, 1.81, -1.75, 15.0, 5.36, 8.1E-4, 0.74}, {1013.0, 263.65, 4.11, 0.00453, 1.55, -0.5, 14.5, 3.39, 6.2E-4, 0.3}};
        if ((lat = Math.abs(lat)) <= 15.0) {
            for (int i = 0; i < 10; ++i) {
                met[i] = metprm[0][i];
            }
        } else if (lat >= 75.0) {
            for (int i = 0; i < 10; ++i) {
                met[i] = metprm[4][i];
            }
        } else {
            int j = (int)(lat / 15.0);
            double a = (lat - (double)j * 15.0) / 15.0;
            for (int i = 0; i < 10; ++i) {
                met[i] = (1.0 - a) * metprm[j - 1][i] + a * metprm[j][i];
            }
        }
    }

    public static double gettgd(int[] sat, nav_t nav, int type) {
        int i;
        int sys = satSys.sat_sys(sat[0], null);
        if (sys == 4) {
            int i2;
            for (i2 = 0; i2 < nav.ng && nav.geph[i2].getSat() != sat[0]; ++i2) {
            }
            return i2 >= nav.ng ? 0.0 : -nav.geph[i2].getDtaun() * 2.99792458E8;
        }
        for (i = 0; i < nav.n && nav.eph[i].getSat() != sat[0]; ++i) {
        }
        return i >= nav.n ? 0.0 : nav.eph[i].getTgd()[type] * 2.99792458E8;
    }

    public static double prange(obsd_t obs, nav_t nav, prcopt_t opt, double[] var) {
        int[] sat = new int[]{obs.sat[0]};
        int sys = satSys.sat_sys(sat[0], null);
        double P1 = obs.P[0];
        double P2 = obs.P[1];
        if (P1 == 0.0 || opt.ionoopt == 3 && P2 == 0.0) {
            return 0.0;
        }
        if (sys == 1 || sys == 4) {
            if (obs.code[0] == 1) {
                P1 += nav.cbias[sat[0] - 1][1];
            }
            if (obs.code[1] == 14) {
                P2 += nav.cbias[sat[0] - 1][2];
            }
        }
        if (opt.ionoopt == 3) {
            if (sys == 1 || sys == 16) {
                double gamma = util2.SQR(1.2833333333333334);
                return (P2 - gamma * P1) / (1.0 - gamma);
            }
            if (sys == 4) {
                double gamma = util2.SQR(1.2857142857142858);
                return (P2 - gamma * P1) / (1.0 - gamma);
            }
            if (sys == 8) {
                double gamma = util2.SQR(1.305084745762712);
                if (seleph.get_seleph(8) != 0) {
                    P2 -= rescode.gettgd(sat, nav, 0) - rescode.gettgd(sat, nav, 1);
                }
                return (P2 - gamma * P1) / (1.0 - gamma);
            }
            if (sys == 32) {
                double gamma = util2.SQR((obs.code[0] == 40 ? 1.561098E9 : 1.57542E9) / 1.20714E9);
                double b1 = obs.code[0] == 40 ? rescode.gettgd(sat, nav, 0) : (obs.code[0] == 2 ? rescode.gettgd(sat, nav, 2) : rescode.gettgd(sat, nav, 2) + rescode.gettgd(sat, nav, 4));
                double b2 = rescode.gettgd(sat, nav, 1);
                return (P2 - gamma * P1 - (b2 - gamma * b1)) / (1.0 - gamma);
            }
            if (sys == 64) {
                double gamma = util2.SQR(0.4720853858784893);
                return (P2 - gamma * P1) / (1.0 - gamma);
            }
        } else {
            var[0] = util2.SQR(0.3);
            if (sys == 1 || sys == 16) {
                double b1 = rescode.gettgd(sat, nav, 0);
                return P1 - b1;
            }
            if (sys == 4) {
                double gamma = util2.SQR(1.2857142857142858);
                double b1 = rescode.gettgd(sat, nav, 0);
                return P1 - b1 / (gamma - 1.0);
            }
            if (sys == 8) {
                double b1 = seleph.get_seleph(8) != 0 ? rescode.gettgd(sat, nav, 0) : rescode.gettgd(sat, nav, 1);
                return P1 - b1;
            }
            if (sys == 32) {
                double b1 = obs.code[0] == 40 ? rescode.gettgd(sat, nav, 0) : (obs.code[0] == 2 ? rescode.gettgd(sat, nav, 2) : rescode.gettgd(sat, nav, 2) + rescode.gettgd(sat, nav, 4));
                return P1 - b1;
            }
            if (sys == 64) {
                double gamma = util2.SQR(2.1182608695652174);
                double b1 = rescode.gettgd(sat, nav, 0);
                return P1 - gamma * b1;
            }
        }
        return P1;
    }

    public static double varerr(prcopt_t opt, double el, double snr, int sys) {
        double fact = sys == 4 ? EFACT_GLO : (sys == 2 ? EFACT_SBS : EFACT_GPS);
        double varr;
        if (opt.getVarmodel() == 1 && snr > 0.0) {
            /* SNR-based stochastic model — replaces elevation dependence */
            double snrTerm = Math.pow(10, (50.0 - snr) / 10.0);
            varr = util2.SQR(opt.getErr()[1]) * (util2.SQR(opt.getErr()[2]) + util2.SQR(opt.getErr()[3]) * snrTerm);
        } else {
            /* height-angle model (default) */
            double elClamp = Math.max(el, 0.08726646259971647); // ≥ 5°
            varr = util2.SQR(opt.getErr()[1]) * (util2.SQR(opt.getErr()[2]) + util2.SQR(opt.getErr()[3]) / Math.sin(elClamp));
        }
        if (opt.ionoopt == 3) {
            varr *= util2.SQR(3.0);
        }
        return util2.SQR(fact) * varr;
    }
}

