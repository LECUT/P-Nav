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
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.pntpos.rescode;
import com.rtklib.postpos.outSol;
import com.rtklib.postpos.postpos;
import com.rtklib.util.util1;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class outHead {
    static final String COMMENTH = "%";

    public static void outrpos(BufferedWriter bufferedWriter, double[] r, solopt_t opt) throws IOException {
        double[] pos = new double[3];
        double[] dms1 = new double[3];
        double[] dms2 = new double[3];
        char sep = opt.sep[0];
        StringBuilder sb = new StringBuilder();
        if (opt.posf == 0 || opt.posf == outSol.SOLF_ENU) {
            rescode.ecef2pos(r, pos);
            if (opt.degf != 0) {
                outSol.deg2dms(pos[0] * 57.29577951308232, dms1, 5);
                outSol.deg2dms(pos[1] * 57.29577951308232, dms2, 5);
                sb.append(String.format(Locale.US, "%3.0f", dms1[0])).append(sep).append(String.format(Locale.US, "%02.0f", dms1[1])).append(sep).append(String.format(Locale.US, "%08.5f", dms1[2])).append(sep).append(String.format(Locale.US, "%4.0f", dms2[0])).append(sep).append(String.format(Locale.US, "%02.0f", dms2[1])).append(sep).append(String.format(Locale.US, "%08.5f", dms2[2])).append(sep).append(String.format(Locale.US, "%10.4f\n", pos[2]));
                bufferedWriter.write(String.valueOf(sb));
            }
        } else if (opt.posf == 1) {
            // empty if block
        }
    }

    public static int outsolheads(StringBuilder sb, solopt_t opt) {
        int timeu;
        String[] s1 = new String[]{"WGS84", "Tokyo"};
        String[] s2 = new String[]{"ellipsoidal", "geodetic"};
        String[] s3 = new String[]{"GPST", "UTC ", "JST "};
        char[] sep = outSol.opt2sep(opt);
        String leg1 = "Q=1:fix,2:float,3:sbas,4:dgps,5:single,6:ppp";
        String leg2 = "ns=# of satellites";
        int n = timeu = opt.timeu < 0 ? 0 : Math.min(opt.timeu, 20);
        if (opt.posf == outSol.SOLF_NMEA || opt.posf == outSol.SOLF_STAT || opt.posf == outSol.SOLF_GSIF) {
            return 0;
        }
        if (opt.outhead != 0) {
            sb.append(String.format("%s (", COMMENTH));
            if (opt.posf == 1) {
                sb.append("x/y/z-ecef=WGS84");
            } else if (opt.posf == outSol.SOLF_ENU) {
                sb.append("e/n/u-baseline=WGS84");
            } else {
                sb.append(String.format("lat/lon/height=%s/%s", s1[opt.datum], s2[opt.height]));
            }
            sb.append(String.format(",%s,%s)\r\n", leg1, leg2));
        }
        sb.append(String.format("%s  %s%s     ", COMMENTH, (opt.timef != 0 ? 16 : 8) + timeu + 1, s3[opt.times])).append(sep[0]);
        if (opt.posf == 0) {
            if (opt.degf != 0) {
                sb.append(String.format("%16s%s%16s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s", "latitude(d'\")", Character.valueOf(sep[0]), "longitude(d'\")", Character.valueOf(sep[0]), "height(m)", Character.valueOf(sep[0]), "Q", Character.valueOf(sep[0]), "ns", Character.valueOf(sep[0]), "sdn(m)", Character.valueOf(sep[0]), "sde(m)", Character.valueOf(sep[0]), "sdu(m)", Character.valueOf(sep[0]), "sdne(m)", Character.valueOf(sep[0]), "sdeu(m)", Character.valueOf(sep[0]), "sdue(m)", Character.valueOf(sep[0]), "age(s)", Character.valueOf(sep[0]), "ratio")).append("\n");
            } else {
                sb.append(String.format("%14s%s%14s%s%10s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s", "latitude(deg)", Character.valueOf(sep[0]), "longitude(deg)", Character.valueOf(sep[0]), "height(m)", Character.valueOf(sep[0]), "Q", Character.valueOf(sep[0]), "ns", Character.valueOf(sep[0]), "sdn(m)", Character.valueOf(sep[0]), "sde(m)", Character.valueOf(sep[0]), "sdu(m)", Character.valueOf(sep[0]), "sdne(m)", Character.valueOf(sep[0]), "sdeu(m)", Character.valueOf(sep[0]), "sdun(m)", Character.valueOf(sep[0]), "age(s)", Character.valueOf(sep[0]), "ratio")).append("\n");
            }
            if (opt.outvel != 0) {
                sb.append(String.format("%s%10s%s%10s%s%10s%s%9s%s%8s%s%8s%s%8s%s%8s%s%8s", Character.valueOf(sep[0]), "vn(m/s)", Character.valueOf(sep[0]), "ve(m/s)", Character.valueOf(sep[0]), "vu(m/s)", Character.valueOf(sep[0]), "sdvn", Character.valueOf(sep[0]), "sdve", Character.valueOf(sep[0]), "sdvu", Character.valueOf(sep[0]), "sdvne", Character.valueOf(sep[0]), "sdveu", Character.valueOf(sep[0]), "sdvun")).append("\n");
            }
        } else if (opt.posf == 1) {
            sb.append(String.format("%14s%s%14s%s%14s%s%3s%s%3s%s%8s%s%8s%s%8s%s%8s%s%8s%s%8s%s%6s%s%6s", "x-ecef(m)", Character.valueOf(sep[0]), "y-ecef(m)", Character.valueOf(sep[0]), "z-ecef(m)", Character.valueOf(sep[0]), "Q", Character.valueOf(sep[0]), "ns", Character.valueOf(sep[0]), "sdx(m)", Character.valueOf(sep[0]), "sdy(m)", Character.valueOf(sep[0]), "sdz(m)", Character.valueOf(sep[0]), "sdxy(m)", Character.valueOf(sep[0]), "sdyz(m)", Character.valueOf(sep[0]), "sdzx(m)", Character.valueOf(sep[0]), "age(s)", Character.valueOf(sep[0]), "ratio"));
            sb.append("\n");
        }
        return 1;
    }

    public static void outsolhead(BufferedWriter bufferedWriter, solopt_t opt) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (outHead.outsolheads(sb, opt) == 1) {
            bufferedWriter.write(String.valueOf(sb));
        }
    }

    public static void outheader(BufferedWriter bufferedWriter, String[] file, int n, prcopt_t popt, solopt_t sopt) throws IOException {
        String[] s1 = new String[]{"GPST", "UTC", "JST"};
        int[] w1 = new int[1];
        int[] w2 = new int[1];
        char[] s2 = new char[32];
        char[] s3 = new char[32];
        if (sopt.posf == outSol.SOLF_NMEA || sopt.posf == outSol.SOLF_STAT) {
            return;
        }
        if (sopt.outhead != 0) {
            int j;
            int i;
            String out;
            if (sopt.prog == null) {
                out = "% program   : RTKLIB(Java) ver.1.0";
                bufferedWriter.write(out);
                bufferedWriter.newLine();
            } else {
                out = "% program   : RTKLIB(Java) ver.1.0";
                bufferedWriter.write(out);
                bufferedWriter.newLine();
            }
            for (i = 0; i < n; ++i) {
                out = String.format("%s inp file  : %s", COMMENTH, file[i]);
                bufferedWriter.write(out);
                bufferedWriter.newLine();
            }
            for (i = 0; i < postpos.obss.n && postpos.obss.data[i].rcv != 1; ++i) {
            }
            for (j = postpos.obss.n - 1; j >= 0 && postpos.obss.data[j].rcv != 1; --j) {
            }
            if (j < i) {
                out = String.format("%s no rover obs data", COMMENTH);
                bufferedWriter.write(out);
                bufferedWriter.newLine();
                return;
            }
            gtime_t ts = postpos.obss.data[i].time;
            gtime_t te = postpos.obss.data[j].time;
            double t1 = util1.time2gpst(ts, w1);
            double t2 = util1.time2gpst(te, w2);
            if (sopt.times >= 1) {
                ts = util1.gpst2utc(ts);
            }
            if (sopt.times >= 1) {
                te = util1.gpst2utc(te);
            }
            if (sopt.times == 2) {
                ts = util1.timeadd(ts, 32400.0);
            }
            if (sopt.times == 2) {
                te = util1.timeadd(te, 32400.0);
            }
            util1.time2str(ts, s2, 1);
            util1.time2str(te, s3, 1);
            String temps2 = String.valueOf(s2).trim();
            String temps3 = String.valueOf(s3).trim();
            String out1 = String.format("%s obs start : %s %s (week%04d %8.1fs)\n", COMMENTH, temps2, s1[sopt.times], w1[0], t1);
            bufferedWriter.write(out1);
            String out2 = String.format("%s obs end   : %s %s (week%04d %8.1fs)\n", COMMENTH, temps3, s1[sopt.times], w2[0], t2);
            bufferedWriter.write(out2);
            if (sopt.outopt != 0) {
                outHead.outprcopt(bufferedWriter, popt);
            }
            if (1 <= popt.mode && popt.mode <= 5 && popt.mode != 4) {
                String out3 = String.format("%s ref pos   :", COMMENTH);
                bufferedWriter.write(out3);
                outHead.outrpos(bufferedWriter, popt.getRb(), sopt);
            }
            outHead.outsolhead(bufferedWriter, sopt);
        }
    }

    private static void outprcopt(BufferedWriter bufferedWriter, prcopt_t opt) throws IOException {
        StringBuilder sb = new StringBuilder();
        outHead.outprcopts(sb, opt);
        bufferedWriter.write(String.valueOf(sb));
    }

    private static void outprcopts(StringBuilder sb, prcopt_t opt) {
        int[] sys = new int[]{1, 4, 8, 16, 32, 64, 2, 0};
        String[] s1 = new String[]{"Single", "DGNSS", "Kinematic", "Static", "Moving-Base", "Fixed", "PPP Kinematic", "PPP Static", "PPP Fixed", "", "", ""};
        String[] s2 = new String[]{"L1", "L1+2", "L1+2+3", "L1+2+3+4", "L1+2+3+4+5", "L1+2+3+4+5+6", "", "", ""};
        String[] s3 = new String[]{"Forward", "Backward", "Combined", "", "", ""};
        String[] s4 = new String[]{"OFF", "Broadcast", "SBAS", "Iono-Free LC", "Estimate TEC", "IONEX TEC", "QZSS Broadcast", "", "", "", ""};
        String[] s5 = new String[]{"OFF", "Saastamoinen", "SBAS", "Estimate ZTD", "Estimate ZTD+Grad", "", "", ""};
        String[] s6 = new String[]{"Broadcast", "Precise", "Broadcast+SBAS", "Broadcast+SSR APC", "Broadcast+SSR CoM", "", "", ""};
        String[] s7 = new String[]{"GPS", "GLONASS", "Galileo", "QZSS", "BDS", "NavIC", "SBAS", "", "", ""};
        String[] s8 = new String[]{"OFF", "Continuous", "Instantaneous", "Fix and Hold", "", "", ""};
        String[] s9 = new String[]{"OFF", "ON", "", "", ""};
        sb.append(String.format("%s pos mode  : %s\r\n", COMMENTH, s1[opt.mode]));
        if (1 <= opt.mode && opt.mode <= 5) {
            sb.append(String.format("%s freqs     : %s\r\n", COMMENTH, s2[opt.getNf() - 1]));
        }
        if (opt.mode > 0) {
            sb.append(String.format("%s solution  : %s\r\n", COMMENTH, s3[opt.getSoltype()]));
        }
        sb.append(String.format("%s elev mask : %.2f deg\r\n", COMMENTH, opt.getElmin() * 57.29577951308232));
        if (opt.mode > 0) {
            sb.append(String.format("%s dynamics  : %s\r\n", COMMENTH, opt.getDynamics() == 1 ? "on" : "off"));
            sb.append(String.format("%s tidecorr  : %s\r\n", COMMENTH, opt.getTidecorr() == 1 ? "on" : "off"));
        }
        if (opt.mode <= 5) {
            sb.append(String.format("%s ionos opt : %s\r\n", COMMENTH, s4[opt.ionoopt]));
        }
        sb.append(String.format("%s tropo opt : %s\r\n", COMMENTH, s5[opt.tropopt]));
        sb.append(String.format("%s ephemeris : %s\r\n", COMMENTH, s6[opt.getSateph()]));
        sb.append(String.format("%s navi sys  :", COMMENTH));
        int i = 0;
        while (sys[i] == 1) {
            if ((opt.navsys & sys[i]) == 1) {
                sb.append(String.format(" %s", s7[i]));
            }
            ++i;
        }
        sb.append("\r\n");
        if (2 <= opt.mode && opt.mode <= 5) {
            sb.append(String.format("%s amb res   : %s\r\n", COMMENTH, s8[opt.getModear()]));
            if ((opt.navsys & 4) == 1) {
                sb.append(String.format("%s amb glo   : %s\r\n", COMMENTH, s9[opt.glomodear]));
            }
            if (opt.getThresar()[0] > 0.0) {
                sb.append(String.format("%s val thres : %.1f\r\n", COMMENTH, opt.getThresar()[0]));
            }
        }
        if (opt.mode == 4 && opt.getBaseline()[0] > 0.0) {
            sb.append(String.format("%s baseline  : %.4f %.4f m\r\n", COMMENTH, opt.getBaseline()[0], opt.getBaseline()[1]));
        }
        for (i = 0; i < 2; ++i) {
            if (opt.mode == 0 || i >= 1 && opt.mode > 5) continue;
            sb.append(String.format("%s antenna%d  : %-21s (%7.4f %7.4f %7.4f)\r\n", COMMENTH, i + 1, Arrays.toString(opt.getAnttype()[i]), opt.getAntdel()[i][0], opt.getAntdel()[i][1], opt.getAntdel()[i][2]));
        }
    }

    public static int outhead(BufferedWriter bufferedWriter, String[] infile, int n, prcopt_t popt, solopt_t sopt) throws IOException {
        outHead.outheader(bufferedWriter, infile, n, popt, sopt);
        return 1;
    }
}

