/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.postpos;

import com.rtklib.bean.filopt_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.pcvs_t;
import com.rtklib.bean.peph_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sbs_t;
import com.rtklib.bean.seph_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.bean.sta_t;
import com.rtklib.postpos.antPos;
import com.rtklib.postpos.outHead;
import com.rtklib.postpos.procPos;
import com.rtklib.readobsnav.readObsNav;
import com.rtklib.readobsnav.readRnxfp;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class postpos {
    static obs_t obss = new obs_t();
    static nav_t navs = new nav_t();
    static sbs_t sbss = new sbs_t();
    public static sta_t[] stas = new sta_t[64];
    static int aborts = 0;
    static pcvs_t pcvss = new pcvs_t();
    static pcvs_t pcvsr = new pcvs_t();
    public static final int MAXINFILE = 1000;
    public static final int MAXPRCDAYS = 100;
    static int iobsu = 0;
    static int iobsr = 0;
    static int isbs = 0;
    static int revs = 0;

    public static int post_pos(gtime_t ts, gtime_t te, double ti, double tu, prcopt_t popt, solopt_t sopt, filopt_t fopt, String[] infile, int n, String outfile, String rov, char base) throws IOException, CloneNotSupportedException {
        nav_t navs = new nav_t();
        pcvs_t pcvss = new pcvs_t();
        pcvs_t pcvsr = new pcvs_t();
        obs_t obss = new obs_t();
        sbs_t sbss = new sbs_t();
        sta_t[] stas = new sta_t[64];
        int stat = 0;
        boolean week = false;
        boolean flag = true;
        int[] index = new int[1000];
        String[] ifile = new String[1000];
        char[] ofile = new char[1024];
        char[] proc_rov = new char[64];
        char[] proc_base = new char[64];
        for (int i = 0; i < n; ++i) {
            index[i] = i;
        }
        stat = postpos.execses_b(ts, te, ti, popt, sopt, fopt, 1, infile, index, n, outfile, rov, base);
        return stat;
    }

    static int execses(gtime_t ts, gtime_t te, double ti, prcopt_t popt, solopt_t sopt, filopt_t fopt, int flag, String[] infile, int[] index, int n, String outfile) throws IOException, CloneNotSupportedException {
        int i;
        prcopt_t popt_ = new prcopt_t(popt);
        sta_t[] tempstas = new sta_t[64];
        for (i = 0; i < 64; ++i) {
            postpos.stas[i] = new sta_t();
        }
        if (readObsNav.read_obsnav(ts, te, ti, infile, index, n, popt_, obss, navs, stas) == 0) {
            return 0;
        }
        if (popt_.mode != 0) {
            postpos.setpcv(postpos.obss.n > 0 ? postpos.obss.data[0].time : util1.timeget(), popt_, navs, pcvss, pcvsr, stas);
        } else {
            for (i = 0; i < nav_t.getMaxSat(); ++i) {
                postpos.navs.pcvs[i] = new pcv_t();
            }
        }
        if (1 <= popt_.mode && popt_.mode <= 3 && antPos.antpos(popt_, 2, obss, navs, tempstas, fopt.stapos) == 0) {
            return 0;
        }
        aborts = 0;
        revs = 0;
        isbs = 0;
        iobsr = 0;
        iobsu = 0;
        try {
            File file = new File(outfile);
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            if (flag == 1 && outHead.outhead(bufferedWriter, infile, n, popt_, sopt) == 0) {
                return 0;
            }
            bufferedWriter.flush();
            if (popt_.mode == 0 || popt_.getSoltype() == 0) {
                procPos.procpos(bufferedWriter, popt_, sopt, 0);
            }
            bufferedWriter.flush();
            bufferedWriter.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return aborts == 0 ? 1 : 0;
    }

    private static void setpcv(gtime_t time, prcopt_t popt, nav_t nav, pcvs_t pcvs, pcvs_t pcvr, sta_t[] sta) {
        int i;
        int i2;
        for (i2 = 0; i2 < nav_t.getMaxSat(); ++i2) {
            nav.pcvs[i2] = new pcv_t();
        }
        for (i2 = 0; i2 < 2; ++i2) {
            popt.pcvr[i2] = new pcv_t();
        }
        double[] pos = new double[3];
        double[] del = new double[3];
        boolean mode = 1 <= popt.mode && popt.mode <= 5;
        char[] id = new char[64];
        for (i = 0; i < nav_t.getMaxSat(); ++i) {
            nav.pcvs[i] = new pcv_t();
            if ((satSys.sat_sys(i + 1, null) & popt.navsys) != 0) continue;
        }
        for (i = 0; i < (mode ? 2 : 1); ++i) {
            popt.pcvr[i] = new pcv_t();
            if (!Arrays.toString(popt.getAnttype()[i]).contains("*")) continue;
        }
    }

    static int execses_r(gtime_t ts, gtime_t te, double ti, prcopt_t popt, solopt_t sopt, filopt_t fopt, int flag, String[] infile, int[] index, int n, String outfile, String rov) throws IOException, CloneNotSupportedException {
        int stat = 0;
        stat = postpos.execses(ts, te, ti, popt, sopt, fopt, flag, infile, index, n, outfile);
        return stat;
    }

    public static int execses_b(gtime_t ts, gtime_t te, double ti, prcopt_t popt, solopt_t sopt, filopt_t fopt, int flag, String[] infile, int[] index, int n, String outfile, String rov, char base) throws IOException, CloneNotSupportedException {
        int stat = 0;
        postpos.readpreceph(infile, n, popt, navs, sbss);
        stat = postpos.execses_r(ts, te, ti, popt, sopt, fopt, flag, infile, index, n, outfile, rov);
        return stat;
    }

    public static void readpreceph(String[] infile, int n, prcopt_t prcopt, nav_t nav, sbs_t sbs) throws IOException, CloneNotSupportedException {
        // 对nav对象加锁，防止多线程并发修改导致ArrayIndexOutOfBoundsException
        synchronized (nav) {
        int i;
        seph_t seph0 = new seph_t();
        nav.setNe(0);
        nav.setNemax(0);
        nav.setNc(0);
        nav.setNcmax(0);
        sbs.setN(0);
        sbs.setNmax(0);
        for (i = 0; i < n; ++i) {
            postpos.readsp3(infile[i], nav, 0);
        }
        for (i = 0; i < n; ++i) {
            if (infile[i].contains("r") || infile[i].contains("b")) continue;
            postpos.readrnxc(infile[i], nav);
        }
        }
    }

    private static void readsp3(String file, nav_t nav, int opt) throws IOException, CloneNotSupportedException {
        gtime_t[] time = new gtime_t[]{new gtime_t(0.0, 0.0)};
        double[] bfact = new double[]{0.0, 0.0};
        int[] sats = new int[nav_t.getMaxSat()];
        String efiles = "";
        char[] type = new char[1];
        char[] tsys = new char[4];
        BufferedReader reader = new BufferedReader(new FileReader(file));
        int j = 0;
        for (int i = 0; i < 1; ++i) {
            if (!file.contains(".sp3") && !file.contains(".SP3") && !file.contains(".eph") && !file.contains(".EPH")) continue;
            int ns = postpos.readsp3h(reader, time, type, sats, bfact, tsys);
            postpos.readsp3b(reader, type[0], sats, ns, bfact, tsys, j++, opt, nav);
        }
        if (nav.getNe() > 0) {
            postpos.combpeph(nav, opt);
        }
    }

    private static int readrnxc(String file, nav_t nav) throws IOException, CloneNotSupportedException {
        gtime_t t = new gtime_t(0.0, 0.0);
        int index = 0;
        boolean stat = true;
        char[] type = new char[1];
        if (postpos.readrnxfile(file, t, t, 0.0, new char[]{' '}, 1, index++, type, null, nav, null) == 0) {
            return 0;
        }
        return nav.nc;
    }

    private static int readrnxfile(String file, gtime_t ts, gtime_t te, double tint, char[] opt, int flag, int index, char[] type, obs_t obs, nav_t nav, sta_t sta) throws IOException, CloneNotSupportedException {
        int stat = readRnxfp.read_rnxfp(new File(file), ts, te, tint, opt, flag, index, type, obs, nav, sta);
        return stat;
    }

    private static void combpeph(nav_t nav, int opt) {
        Arrays.sort(Arrays.copyOfRange(nav.peph, 0, nav.getNe()), (p1, p2) -> {
            double tt = util1.timediff(p1.getTime(), p2.getTime());
            return tt < -1.0E-9 ? -1 : (tt > 1.0E-9 ? 1 : p1.getIndex() - p2.getIndex());
        });
        if ((opt & 4) != 0) {
            return;
        }
        int i = 0;
        for (int j = 1; j < nav.getNe(); ++j) {
            if (Math.abs(util1.timediff(nav.peph[i].getTime(), nav.peph[j].getTime())) < 1.0E-9) {
                for (int k = 0; k < nav_t.getMaxSat(); ++k) {
                    int m;
                    if (math.norm(nav.peph[j].getPos()[k], 4) <= 0.0) continue;
                    for (m = 0; m < 4; ++m) {
                        nav.peph[i].getPos()[k][m] = nav.peph[j].getPos()[k][m];
                    }
                    for (m = 0; m < 4; ++m) {
                        nav.peph[i].getStd()[k][m] = nav.peph[j].getStd()[k][m];
                    }
                    for (m = 0; m < 4; ++m) {
                        nav.peph[i].getVel()[k][m] = nav.peph[j].getVel()[k][m];
                    }
                    for (m = 0; m < 4; ++m) {
                        nav.peph[i].getVst()[k][m] = nav.peph[j].getVst()[k][m];
                    }
                }
                continue;
            }
            if (++i >= j) continue;
            nav.peph[i] = nav.peph[j];
        }
        nav.setNe(i + 1);
    }

    private static void readsp3b(BufferedReader reader, char type, int[] sats, int ns, double[] bfact, char[] tsys, int index, int opt, nav_t nav) throws IOException, CloneNotSupportedException {
        String temp;
        peph_t peph = new peph_t();
        gtime_t[] time = new gtime_t[1];
        int n = ns * (type == 'P' ? 1 : 2);
        boolean v = false;
        while ((temp = reader.readLine()) != null && !temp.contains("EOF")) {
            int j;
            int i;
            if (temp.toCharArray()[0] != '*' || util1.str2time(temp.toCharArray(), 3, 28, time) != 0) continue;
            if (Arrays.toString(tsys).contains("UTC")) {
                time[0] = util1.utc2gpst(time[0]);
            }
            peph.setTime(time[0]);
            peph.setIndex(index);
            for (i = 0; i < nav_t.getMaxSat(); ++i) {
                for (j = 0; j < 4; ++j) {
                    peph.getPos()[i][j] = 0.0;
                    peph.getStd()[i][j] = 0.0f;
                    peph.getVel()[i][j] = 0.0;
                    peph.getVst()[i][j] = 0.0f;
                }
                for (j = 0; j < 3; ++j) {
                    peph.getCov()[i][j] = 0.0f;
                    peph.getVco()[i][j] = 0.0f;
                }
            }
            v = false;
            boolean pred_c = false;
            boolean pred_o = false;
            for (i = 0; i < n; ++i) {
                temp = reader.readLine();
                if (temp == null) break; // 文件意外结束（可能正在被并发写入覆盖），安全退出
                if (temp.length() < 4 || temp.toCharArray()[0] != 'P' && temp.toCharArray()[0] != 'V') continue;
                int sys = temp.toCharArray()[1] == ' ' ? 1 : postpos.code2sys(temp.toCharArray()[1]);
                int prn = (int)util1.str2num(temp.toCharArray(), 2, 2);
                if (sys == 2) {
                    prn += 100;
                } else if (sys == 16) {
                    prn += 192;
                }
                int sat = util1.satno(sys, prn);
                if (sat == 0) continue;
                if (temp.toCharArray()[0] == 'P') {
                    pred_c = temp.length() >= 76 && temp.toCharArray()[75] == 'P';
                    pred_o = temp.length() >= 80 && temp.toCharArray()[79] == 'P';
                }
                for (j = 0; j < 4; ++j) {
                    double d;
                    double base;
                    if (j < 3 && (opt & 1) != 0 && pred_o || j < 3 && (opt & 2) != 0 && !pred_o || j == 3 && (opt & 1) != 0 && pred_c || j == 3 && (opt & 2) != 0 && !pred_c) continue;
                    double val = util1.str2num(temp.toCharArray(), 4 + j * 14, 14);
                    double std = util1.str2num(temp.toCharArray(), 61 + j * 3, j < 3 ? 2 : 3);
                    if (temp.toCharArray()[0] == 'P') {
                        double d2;
                        if (val != 0.0 && Math.abs(val - 999999.999999) >= 1.0E-6) {
                            peph.getPos()[sat - 1][j] = val * (j < 3 ? 1000.0 : 1.0E-6);
                            v = true;
                        }
                        base = bfact[j < 3 ? 0 : 1];
                        if (!(std > 0.0)) continue;
                        peph.getStd()[sat - 1][j] = (float)(Math.pow(base, std) * (j < 3 ? 0.001 : 1.0E-12));
                        continue;
                    }
                    if (!v) continue;
                    if (val != 0.0 && Math.abs(val - 999999.999999) >= 1.0E-6) {
                        peph.getVel()[sat - 1][j] = val * (j < 3 ? 0.1 : 1.0E-10);
                    }
                    base = bfact[j < 3 ? 0 : 1];
                    if (!(std > 0.0)) continue;
                    peph.getVst()[sat - 1][j] = (float)(Math.pow(base, std) * (j < 3 ? 1.0E-7 : 1.0E-16));
                }
            }
            if (!v || postpos.addpeph(nav, peph)) continue;
            return;
        }
    }

    private static boolean addpeph(nav_t nav, peph_t peph) throws CloneNotSupportedException {
        int i = nav.getNe();
        if (nav.getNe() >= nav.getNemax()) {
            int newMax = nav.getNemax() + 256;
            nav.setNemax(newMax);
            peph_t[] newArray = new peph_t[nav.getNemax()];
            for (int k = 0; k < nav.getNemax(); ++k) {
                newArray[k] = new peph_t();
            }
            System.arraycopy(nav.peph, 0, newArray, 0, nav.getNe());
            nav.peph = newArray;
        }
        nav.peph[nav.getNe()] = (peph_t)peph.clone();
        nav.setNe(++i);
        return true;
    }

    private static int readsp3h(BufferedReader reader, gtime_t[] time, char[] type, int[] sats, double[] bfact, char[] tsys) throws FileNotFoundException {
        int k = 0;
        int ns = 0;
        try {
            String temp;
            while ((temp = reader.readLine()) != null) {
                if (temp.contains("#") && !temp.contains("##")) {
                    type[0] = temp.toCharArray()[2];
                    if (util1.str2time(temp.toCharArray(), 3, 28, time) == 0) continue;
                    return 0;
                }
                if (temp.contains("+ ")) {
                    if (ns == 0) {
                        ns = (int)util1.str2num(temp.toCharArray(), 3, 3);
                    }
                    for (int j = 0; j < 17 && k < ns; ++j) {
                        int sys = postpos.code2sys(temp.toCharArray()[9 + 3 * j]);
                        int prn = (int)util1.str2num(temp.toCharArray(), 10 + 3 * j, 2);
                        if (k >= nav_t.getMaxSat()) continue;
                        sats[k++] = util1.satno(sys, prn);
                    }
                    continue;
                }
                if (temp.contains("++")) continue;
                if (temp.contains("%c")) {
                    util2.strncpy(tsys, Arrays.copyOfRange(temp.toCharArray(), 9, temp.length()), 3);
                    tsys[3] = '\u0000';
                    continue;
                }
                if (temp.contains("%f") && bfact[0] == 0.0) {
                    bfact[0] = util1.str2num(temp.toCharArray(), 3, 10);
                    bfact[1] = util1.str2num(temp.toCharArray(), 14, 12);
                    continue;
                }
                if (temp.contains("%i") || temp.contains("/*") || !temp.contains("* ")) continue;
                break;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ns;
    }

    private static int code2sys(char code) {
        if (code == 'G' || code == ' ') {
            return 1;
        }
        if (code == 'R') {
            return 4;
        }
        if (code == 'E') {
            return 8;
        }
        if (code == 'J') {
            return 16;
        }
        if (code == 'C') {
            return 32;
        }
        if (code == 'I') {
            return 64;
        }
        if (code == 'L') {
            return 128;
        }
        return 0;
    }

    public static void addpcv(pcv_t[] pcv, pcvs_t pcvs) {
        if (pcvs.nmax <= pcvs.n) {
            pcvs.nmax += 256;
            pcv_t[] pcvs_pcv = new pcv_t[pcvs.nmax];
            pcvs.pcv = pcvs_pcv;
        }
        pcvs.pcv[pcvs.n++] = pcv[0];
    }

    public static int decodef(char[] p, int n, double[] v) {
        int i;
        for (i = 0; i < n; ++i) {
            v[i] = 0.0;
        }
        return i;
    }

    public static int readantex(char[] file, pcvs_t pcvs) throws FileNotFoundException {
        pcv_t[] pcv = new pcv_t[1];
        pcv_t pcv0 = new pcv_t();
        double[] neu = new double[3];
        int freq = 0;
        boolean state = false;
        int[] freqs = new int[]{1, 2, 5, 0};
        char[] buff = new char[256];
        try {
            String line;
            BufferedReader br = new BufferedReader(new FileReader(String.valueOf(file)));
            while ((line = br.readLine()) != null) {
                int i;
                if (line.length() < 60 || line.contains("COMMENT")) continue;
                if (line.contains("START OF ANTENNA")) {
                    pcv[0] = pcv0;
                    state = true;
                }
                if (line.contains("END OF ANTENNA")) {
                    postpos.addpcv(pcv, pcvs);
                    state = false;
                }
                if (!state) continue;
                if (line.contains("TYPE / SERIAL NO")) {
                    util2.strncpy(pcv[0].type, buff, 20);
                    pcv[0].type[20] = '\u0000';
                    util2.strncpy(pcv[0].code, String.valueOf(buff).substring(20).toCharArray(), 20);
                    pcv[0].code[20] = '\u0000';
                    if (!String.valueOf(pcv[0].code).substring(3, 12).contains("        ")) continue;
                    pcv[0].sat = util1.satid2no(pcv[0].code);
                    continue;
                }
                if (line.contains("VALID FROM")) {
                    if (util1.str2time(buff, 0, 43, pcv[0].ts) == 0) continue;
                    continue;
                }
                if (String.valueOf(buff).contains("VALID UNTIL")) {
                    if (util1.str2time(buff, 0, 43, pcv[0].te) == 0) continue;
                    continue;
                }
                if (line.contains("START OF FREQUENCY")) {
                    String tempf = null;
                    if (pcv[0].sat != 0 && buff[3] != 'G' || util1.sscanf(String.valueOf(buff).substring(4), "//d") != null) continue;
                    int f = Integer.parseInt(tempf);
                    i = 0;
                    while (freqs[i] != 0 && freqs[i] != f) {
                        ++i;
                    }
                    if (freqs[i] == 0) continue;
                    freq = i + 1;
                    continue;
                }
                if (String.valueOf(buff).contains("END OF FREQUENCY")) {
                    freq = 0;
                    continue;
                }
                if (line.contains("NORTH / EAST / UP")) {
                    if (freq < 1 || 3 < freq || postpos.decodef(buff, 3, neu) < 3) continue;
                    pcv[0].off[freq - 1][0] = neu[pcv[0].sat == 1 ? 0 : 1];
                    pcv[0].off[freq - 1][1] = neu[pcv[0].sat == 0 ? 1 : 0];
                    pcv[0].off[freq - 1][2] = neu[2];
                    continue;
                }
                if (!line.contains("NOAZI") || freq < 1 || 3 < freq || (i = postpos.decodef(String.valueOf(buff).substring(8).toCharArray(), 19, pcv[0].var[freq - 1])) <= 0) continue;
                while (i < 19) {
                    pcv[0].var[freq - 1][i] = pcv[0].var[freq - 1][i - 1];
                    ++i;
                }
            }
            br.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 1;
    }

    public static int readpcv(char[] file, pcvs_t pcvs) throws FileNotFoundException {
        char[] ext = new char[10];
        if (util1.newstrrchr(String.valueOf(file), '.', ext) == 0) {
            return 0;
        }
        if (String.valueOf(ext).contains(".atx") || String.valueOf(ext).contains(".ATX")) {
            int n = postpos.readantex(file, pcvs);
        }
        for (int i = 0; i < pcvs.n; ++i) {
            pcv_t pcv = pcvs.pcv[i];
            for (int j = 2; j < 3; ++j) {
                if (math.norm(pcv.off[j], 3) > 0.0) continue;
                pcv.off[j] = Arrays.copyOf(pcv.off[1], 3);
                pcv.off[j] = Arrays.copyOf(pcv.var[1], 19);
            }
        }
        return 0;
    }

    public static int openses(prcopt_t popt, solopt_t sopt, filopt_t fopt, nav_t nav, pcvs_t pcvs, pcvs_t pcvr) throws FileNotFoundException {
        if (fopt.satantp == null && postpos.readpcv(fopt.satantp, pcvs) == 0) {
            System.out.printf("error : no sat ant pcv in %s", String.valueOf(fopt.satantp));
            return 0;
        }
        if (fopt.rcvantp == null && postpos.readpcv(fopt.rcvantp, pcvr) == 0) {
            System.out.printf("error : no rec ant pcv in %s", String.valueOf(fopt.rcvantp));
            return 0;
        }
        if (sopt.geoid <= 0 || fopt.geoid != null) {
            // empty if block
        }
        return 1;
    }
}

