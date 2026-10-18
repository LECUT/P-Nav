/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.util;

import com.rtklib.bean.eph_t;
import com.rtklib.bean.geph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.seph_t;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class util1 {
    final int MAXLEAPS = 64;
    public static final double DTTOL = 0.025;
    static final double[] gpst0 = new double[]{1980.0, 1.0, 6.0, 0.0, 0.0, 0.0};
    static final double[] gst0 = new double[]{1999.0, 8.0, 22.0, 0.0, 0.0, 0.0};
    static final double[] bdt0 = new double[]{2006.0, 1.0, 1.0, 0.0, 0.0, 0.0};
    static double timeoffset_ = 0.0;
    static final double[][] leaps = new double[][]{{2017.0, 1.0, 1.0, 0.0, 0.0, 0.0, -18.0}, {2015.0, 7.0, 1.0, 0.0, 0.0, 0.0, -17.0}, {2012.0, 7.0, 1.0, 0.0, 0.0, 0.0, -16.0}, {2009.0, 1.0, 1.0, 0.0, 0.0, 0.0, -15.0}, {2006.0, 1.0, 1.0, 0.0, 0.0, 0.0, -14.0}, {1999.0, 1.0, 1.0, 0.0, 0.0, 0.0, -13.0}, {1997.0, 7.0, 1.0, 0.0, 0.0, 0.0, -12.0}, {1996.0, 1.0, 1.0, 0.0, 0.0, 0.0, -11.0}, {1994.0, 7.0, 1.0, 0.0, 0.0, 0.0, -10.0}, {1993.0, 7.0, 1.0, 0.0, 0.0, 0.0, -9.0}, {1992.0, 7.0, 1.0, 0.0, 0.0, 0.0, -8.0}, {1991.0, 1.0, 1.0, 0.0, 0.0, 0.0, -7.0}, {1990.0, 1.0, 1.0, 0.0, 0.0, 0.0, -6.0}, {1988.0, 1.0, 1.0, 0.0, 0.0, 0.0, -5.0}, {1985.0, 7.0, 1.0, 0.0, 0.0, 0.0, -4.0}, {1983.0, 7.0, 1.0, 0.0, 0.0, 0.0, -3.0}, {1982.0, 7.0, 1.0, 0.0, 0.0, 0.0, -2.0}, {1981.0, 7.0, 1.0, 0.0, 0.0, 0.0, -1.0}, {0.0}};
    static final double[] ura_eph = new double[]{2.4, 3.4, 4.85, 6.85, 9.65, 13.65, 24.0, 48.0, 96.0, 192.0, 384.0, 768.0, 1536.0, 3072.0, 6144.0, 0.0};
    public static final int NINCOBS = 262144;

    public static int add_obsdata(obs_t obs, obsd_t data) throws CloneNotSupportedException {
        if (obs.nmax <= obs.n) {
            obs.nmax = obs.nmax <= 0 ? 262144 : (obs.nmax *= 2);
            obsd_t[] obsData = new obsd_t[obs.nmax];
            obs.data = obsData;
        }
        obs.data[obs.n++] = data;
        return 1;
    }

    public static String sscanf(String buff, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(buff);
        return matcher.group(1);
    }

    public static int add_geph(nav_t nav, geph_t[] geph) {
        if (nav.ngmax <= nav.ng) {
            nav.ngmax += 1024;
            geph_t[] nav_geph = new geph_t[nav.ngmax];
            nav.geph = nav_geph;
        }
        nav.geph[nav.ng++] = geph[0];
        return 1;
    }

    public static int add_seph(nav_t nav, seph_t[] seph) {
        if (nav.nsmax <= nav.ns) {
            nav.nsmax += 1024;
            seph_t[] nav_seph = new seph_t[nav.nsmax];
            nav.seph = nav_seph;
        }
        nav.seph[nav.ns++] = seph[0];
        return 1;
    }

    public static int add_eph(nav_t nav, eph_t[] eph) {
        if (nav.nmax <= nav.n) {
            nav.nmax += 1024;
            eph_t[] nav_eph = new eph_t[nav.nmax];
            nav.eph = nav_eph;
        }
        nav.eph[nav.n++] = eph[0];
        return 1;
    }

    public static void sprintf(char[] dest, String format, Object ... args) {
        String formattedString = String.format(format, args);
        System.arraycopy(formattedString.toCharArray(), 0, dest, 0, Math.min(dest.length, formattedString.length()));
    }

    public static double timediff(gtime_t t1, gtime_t t2) {
        if (t1 == null || t2 == null) {
            return 0.0;
        }
        double diffInSeconds = t1.getTime() - t2.getTime();
        double diffInFractionalSeconds = t1.getSec() - t2.getSec();
        return diffInSeconds + diffInFractionalSeconds;
    }

    public static boolean screent(gtime_t time, gtime_t ts, gtime_t te, double tint) {
        return (tint <= 0.0 || (util1.time2gpst(time, null) + 0.025) % tint <= 0.05) && (ts.time == 0.0 || util1.timediff(time, ts) >= -0.025) && (te.time == 0.0 || util1.timediff(time, te) < 0.025);
    }

    public static int satno(int sys, Integer prn) {
        if (prn <= 0) {
            return 0;
        }
        return switch (sys) {
            case 1 -> {
                if (prn < 1 || 32 < prn) {
                    yield 0;
                }
                yield prn - 1 + 1;
            }
            case 4 -> {
                if (prn < 1 || 27 < prn) {
                    yield 0;
                }
                yield 32 + prn - 1 + 1;
            }
            case 8 -> {
                if (prn < 1 || 36 < prn) {
                    yield 0;
                }
                yield 59 + prn - 1 + 1;
            }
            case 16 -> {
                if (prn < 193 || 202 < prn) {
                    yield 0;
                }
                yield 95 + prn - 193 + 1;
            }
            case 32 -> {
                if (prn < 1 || 63 < prn) {
                    yield 0;
                }
                int a = 105 + prn - 1 + 1;
                yield 105 + prn - 1 + 1;
            }
            case 64 -> {
                if (prn < 1 || 14 < prn) {
                    yield 0;
                }
                yield 168 + prn - 1 + 1;
            }
            case 128 -> {
                if (prn < 1 || 10 < prn) {
                    yield 0;
                }
                yield 182 + prn - 1 + 1;
            }
            case 2 -> {
                if (prn < 120 || 158 < prn) {
                    yield 0;
                }
                yield 192 + prn - 120 + 1;
            }
            default -> 0;
        };
    }

    public static int satid2no(char[] id) {
        int sys;
        int prn = 0;
        int code = 48;
        String tempid = String.valueOf(id);
        Pattern pattern = Pattern.compile("(\\D+)(\\d+)");
        Matcher matcher = pattern.matcher(tempid);
        while (matcher.find()) {
            code = matcher.group(1).charAt(0);
            prn = Integer.parseInt(matcher.group(2));
        }
        switch (code) {
            case 71: {
                sys = 1;
                prn += 0;
                break;
            }
            case 82: {
                sys = 4;
                prn += 0;
                break;
            }
            case 69: {
                sys = 8;
                prn += 0;
                break;
            }
            case 74: {
                sys = 16;
                prn += 192;
                break;
            }
            case 67: {
                sys = 32;
                prn += 0;
                break;
            }
            case 73: {
                sys = 64;
                prn += 0;
                break;
            }
            case 76: {
                sys = 128;
                prn += 0;
                break;
            }
            case 83: {
                sys = 2;
                prn += 100;
                break;
            }
            default: {
                return 0;
            }
        }
        return util1.satno(sys, prn);
    }

    public static gtime_t utc2gpst(gtime_t t) {
        int i = 0;
        while (leaps[i][0] > 0.0) {
            if (util1.timediff(t, util1.epoch2time(leaps[i])) >= 0.0) {
                return util1.timeadd(t, -leaps[i][6]);
            }
            ++i;
        }
        return t;
    }

    public static double time2doy(gtime_t t) {
        double[] ep = new double[6];
        util1.time2epoch(t, ep);
        ep[2] = 1.0;
        ep[1] = 1.0;
        ep[5] = 0.0;
        ep[4] = 0.0;
        ep[3] = 0.0;
        return util1.timediff(t, util1.epoch2time(ep)) / 86400.0 + 1.0;
    }

    public static double time2gpst(gtime_t t, int[] week) {
        gtime_t t0 = util1.epoch2time(gpst0);
        double sec = t.time - t0.time;
        int w = (int)(sec / 604800.0);
        if (week != null) {
            week[0] = w;
        }
        return sec - (double)w * 86400.0 * 7.0 + t.sec;
    }

    public static gtime_t adjweek(gtime_t t, gtime_t t0) {
        double tt = util1.timediff(t, t0);
        if (tt < -302400.0) {
            return util1.timeadd(t, 604800.0);
        }
        if (tt > 302400.0) {
            return util1.timeadd(t, -604800.0);
        }
        return t;
    }

    public static gtime_t bdt2gpst(gtime_t t) {
        return util1.timeadd(t, 14.0);
    }

    public static gtime_t gpst2utc(gtime_t t) {
        int i = 0;
        while (leaps[i][0] > 0.0) {
            gtime_t tu = util1.timeadd(t, leaps[i][6]);
            if (util1.timediff(tu, util1.epoch2time(leaps[i])) >= 0.0) {
                return tu;
            }
            ++i;
        }
        return t;
    }

    public static gtime_t bdt2time(int[] week, double sec) {
        gtime_t t = util1.epoch2time(bdt0);
        if (sec < -1.0E9 || 1.0E9 < sec) {
            sec = 0.0;
        }
        t.time += 604800.0 * (double)week[0] + (double)((int)sec);
        t.sec = sec - (double)((int)sec);
        return t;
    }

    public static gtime_t gpst2time(int[] week, double sec) {
        gtime_t t = util1.epoch2time(gpst0);
        if (sec < -1.0E9 || 1.0E9 < sec) {
            sec = 0.0;
        }
        t.time += 604800.0 * (double)week[0] + (double)((int)sec);
        t.sec = sec - (double)((int)sec);
        return t;
    }

    public static gtime_t adjday(gtime_t t, gtime_t t0) {
        double tt = util1.timediff(t, t0);
        if (tt < -43200.0) {
            return util1.timeadd(t, 86400.0);
        }
        if (tt > 43200.0) {
            return util1.timeadd(t, -86400.0);
        }
        return t;
    }

    public static gtime_t timeadd(gtime_t t, double sec) {
        gtime_t tempt = new gtime_t(t.time, t.sec);
        tempt.sec += sec;
        double tt = Math.floor(tempt.sec);
        tempt.time += (double)((int)tt);
        tempt.sec -= tt;
        return tempt;
    }

    public static gtime_t epoch2time(double[] ep) {
        int[] doy = new int[]{1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};
        gtime_t time = new gtime_t();
        int year = (int)ep[0];
        int mon = (int)ep[1];
        int day = (int)ep[2];
        if (year < 1970 || 2099 < year || mon < 1 || 12 < mon) {
            return time;
        }
        int days = (year - 1970) * 365 + (year - 1969) / 4 + doy[mon - 1] + day - 2 + (year % 4 == 0 && mon >= 3 ? 1 : 0);
        int sec = (int)Math.floor(ep[5]);
        time.setTime(days * 86400 + (int)ep[3] * 3600 + (int)ep[4] * 60 + sec);
        time.setSec(ep[5] - (double)sec);
        return time;
    }

    public static gtime_t timeget() {
        double[] ep = new double[6];
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        ep[0] = calendar.get(1);
        ep[1] = calendar.get(2) + 1;
        ep[2] = calendar.get(5);
        ep[3] = calendar.get(11);
        ep[4] = calendar.get(12);
        ep[5] = (double)calendar.get(13) + (double)calendar.get(14) * 0.001;
        gtime_t time = util1.epoch2time(ep);
        return util1.timeadd(time, timeoffset_);
    }

    public static void time2epoch(gtime_t t, double[] ep) {
        int mon;
        int[] mday = new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int days = (int)(t.time / 86400.0);
        int sec = (int)(t.time - (double)(days * 86400));
        int day = days % 1461;
        for (mon = 0; mon < 48 && day >= mday[mon]; day -= mday[mon], ++mon) {
        }
        ep[0] = 1970 + days / 1461 * 4 + mon / 12;
        ep[1] = mon % 12 + 1;
        ep[2] = day + 1;
        ep[3] = sec / 3600;
        ep[4] = sec % 3600 / 60;
        ep[5] = (double)(sec % 60) + t.sec;
    }

    public static double str2num(char[] s, int i, int n) {
        if (i < 0 || i >= s.length || n <= 0) {
            return 0.0;
        }
        StringBuilder strBuilder = new StringBuilder();
        int len = s.length;
        for (int k = i; k < len && strBuilder.length() < n; ++k) {
            char c = s[k];
            if (c == 'd' || c == 'D') {
                strBuilder.append('E');
                continue;
            }
            if (strBuilder.length() == 18) {
                if (c == '-' || c == ' ') break;
                strBuilder.append(c);
                continue;
            }
            strBuilder.append(c);
        }
        double value = 0.0;
        try {
            value = Double.parseDouble(strBuilder.toString());
        }
        catch (NumberFormatException e) {
            value = 0.0;
        }
        return value;
    }

    public static void time2str(gtime_t t, char[] s, int n) {
        double[] ep = new double[6];
        if (n < 0) {
            n = 0;
        } else if (n > 12) {
            n = 12;
        }
        if (1.0 - t.sec < 0.5 / Math.pow(10.0, n)) {
            t.time += 1.0;
            t.sec = 0.0;
        }
        util1.time2epoch(t, ep);
        util1.sprintf(s, "%04.0f/%02.0f/%02.0f %02.0f:%02.0f:%04.1f", ep[0], ep[1], ep[2], ep[3], ep[4], ep[5]);
    }

    public static int str2time(char[] s, int i, int n, gtime_t[] t) {
        char[] str;
        char[] p = str = new char[256];
        if (i < 0 || String.valueOf(s).length() < i) {
            return -1;
        }
        int x = 0;
        s = Arrays.copyOfRange(s, i, s.length);
        while (s.length > 0 && --n >= 0) {
            p[x] = String.valueOf(s).charAt(0);
            s = String.valueOf(s).substring(1).toCharArray();
            ++x;
        }
        String tempP = String.valueOf(p);
        double[] ep = Arrays.stream((tempP = tempP.replace('D', 'E')).trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        if (ep[0] < 100.0) {
            ep[0] = ep[0] + (ep[0] < 80.0 ? 2000.0 : 1900.0);
        }
        if (ep.length < 5) {
            return -1;
        }
        t[0] = util1.epoch2time(ep);
        return 0;
    }

    public static int sisaIndex(double value) {
        if (value < 0.0 || value > 6.0) {
            return 255;
        }
        if (value <= 0.5) {
            return (int)(value / 0.01);
        }
        if (value <= 1.0) {
            return (int)((value - 0.5) / 0.02) + 50;
        }
        if (value <= 2.0) {
            return (int)((value - 1.0) / 0.04) + 75;
        }
        return (int)((double)((int)(value - 2.0)) / 0.16 + 100.0);
    }

    public static int uraIndex(double value) {
        int i;
        for (i = 0; i < 15 && !(ura_eph[i] >= value); ++i) {
        }
        return i;
    }

    public static int sortobs(obs_t obs) {
        int i;
        if (obs.n <= 0) {
            return 0;
        }
        Comparator<obsd_t> comparator = (obsd_t p1, obsd_t p2) -> {
            if (p1 == null || p2 == null) {
                return 0;
            }
            double tt = util1.timediff(p1.time, p2.time);
            if (Math.abs(tt) > 0.025) {
                return tt < 0.0 ? -1 : 1;
            }
            if (p1.rcv != p2.rcv) {
                return p1.rcv - p2.rcv;
            }
            return p1.sat[0] - p2.sat[0];
        };
        Arrays.sort(obs.data, comparator);
        int j = 0;
        for (i = 0; i < obs.n; ++i) {
            if (obs.data[i].sat == obs.data[j].sat && obs.data[i].rcv == obs.data[j].rcv && util1.timediff(obs.data[i].time, obs.data[j].time) == 0.0) continue;
            obs.data[++j] = obs.data[i];
        }
        obs.n = j + 1;
        int n = 0;
        i = 0;
        while (i < obs.n) {
            for (j = i + 1; j < obs.n && !(util1.timediff(obs.data[j].time, obs.data[i].time) > 0.025); ++j) {
            }
            i = j;
            ++n;
        }
        return n;
    }

    public static int nextobsf(obs_t obs, int[] i, int rcv) {
        while (i[0] < obs.n && obs.data[i[0]].rcv != rcv) {
            i[0] = i[0] + 1;
        }
        int n = 0;
        while (i[0] + n < obs.n) {
            double tt = util1.timediff(obs.data[i[0] + n].time, obs.data[i[0]].time);
            if (obs.data[i[0] + n].rcv != rcv || tt > 0.025) break;
            ++n;
        }
        return n;
    }

    public static int newstrrchr(String file, char ch, char[] ext) {
        int lastIndex = file.lastIndexOf(ch);
        if (lastIndex != -1) {
            String tempext = file.substring(lastIndex);
            System.arraycopy(tempext.toCharArray(), 0, ext, 0, tempext.length());
            return 1;
        }
        System.out.println("Character not found in the string");
        return 0;
    }

    public static double utc2gmst(gtime_t t, double ut1_utc) {
        double[] ep2000 = new double[]{2000.0, 1.0, 1.0, 12.0, 0.0, 0.0};
        gtime_t tut0 = new gtime_t(0.0, 0.0);
        gtime_t tut = util1.timeadd(t, ut1_utc);
        double ut = util1.time2sec(tut, tut0);
        double t1 = util1.timediff(tut0, util1.epoch2time(ep2000)) / 86400.0 / 36525.0;
        double t2 = t1 * t1;
        double t3 = t2 * t1;
        double gmst0 = 24110.54841 + 8640184.812866 * t1 + 0.093104 * t2 - 6.2E-6 * t3;
        double gmst = gmst0 + 1.002737909350795 * ut;
        return gmst % 86400.0 * Math.PI / 43200.0;
    }

    private static double time2sec(gtime_t time, gtime_t day) {
        double[] ep = new double[6];
        util1.time2epoch(time, ep);
        double sec = ep[3] * 3600.0 + ep[4] * 60.0 + ep[5];
        ep[5] = 0.0;
        ep[4] = 0.0;
        ep[3] = 0.0;
        day.time = util1.epoch2time((double[])ep).time;
        day.sec = util1.epoch2time((double[])ep).sec;
        return sec;
    }
}

