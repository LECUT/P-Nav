/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.util;

import com.rtklib.bean.sta_t;
import com.rtklib.pntpos.rescode;
import com.rtklib.util.math;
import java.util.Objects;

public class util2 {
    private static final int MAXCODE = 68;
    private static final int CODE_NONE = 0;
    public static final double FREQ1 = 1.57542E9;
    public static final double FREQ2 = 1.2276E9;
    public static final double FREQ5 = 1.17645E9;
    static final double FREQ6 = 1.27875E9;
    public static final double FREQ7 = 1.20714E9;
    static final double FREQ8 = 1.191795E9;
    public static final double FREQ9 = 2.492028E9;
    public static final double FREQ1_CMP = 1.561098E9;
    public static final double FREQ2_CMP = 1.20714E9;
    static final double FREQ3_CMP = 1.26852E9;
    public static final double FREQ1_GLO = 1.602E9;
    static final double DFRQ1_GLO = 562500.0;
    public static final double FREQ2_GLO = 1.246E9;
    static final double DFRQ2_GLO = 437500.0;
    static final double FREQ3_GLO = 1.202025E9;
    static final double FREQ1a_GLO = 1.600995E9;
    static final double FREQ2a_GLO = 1.24806E9;
    static String[] obscodes = new String[]{"", "1C", "1P", "1W", "1Y", "1M", "1N", "1S", "1L", "1E", "1A", "1B", "1X", "1Z", "2C", "2D", "2S", "2L", "2X", "2P", "2W", "2Y", "2M", "2N", "5I", "5Q", "5X", "7I", "7Q", "7X", "6A", "6B", "6C", "6X", "6Z", "6S", "6L", "8L", "8Q", "8X", "2I", "2Q", "6I", "6Q", "3I", "3Q", "3X", "1I", "1Q", "5A", "5B", "5C", "9A", "9B", "9C", "9X", "1D", "5D", "5P", "5Z", "6E", "7D", "7P", "7Z", "8D", "8P", "4A", "4B", "4X", ""};
    private static final String[][] codepris = new String[][]{{"CPYWMNSL", "PYWCMNDLSX", "IQX", "", "", "", ""}, {"CPABX", "PCABX", "IQX", "", "", "", ""}, {"CABXZ", "IQX", "IQX", "ABCXZ", "IQX", "", ""}, {"CLSXZ", "LSX", "IQXDPZ", "LSXEZ", "", "", ""}, {"C", "IQX", "", "", "", "", ""}, {"IQXDPAN", "IQXDPZ", "DPX", "IQXA", "DPX", "", ""}, {"ABCX", "ABCX", "", "", "", "", ""}};
    static String proc_rov = "";
    static String proc_base = "";

    public static void strcpy(String dest, String sour) {
        dest = (String)dest + sour;
    }

    public static void pos2ecef(double[] pos, double[] r) {
        double sinp = Math.sin(pos[0]);
        double cosp = Math.cos(pos[0]);
        double sinl = Math.sin(pos[1]);
        double cosl = Math.cos(pos[1]);
        double e2 = 0.0066943799901413165;
        double v = 6378137.0 / Math.sqrt(1.0 - e2 * sinp * sinp);
        r[0] = (v + pos[2]) * cosp * cosl;
        r[1] = (v + pos[2]) * cosp * sinl;
        r[2] = (v * (1.0 - e2) + pos[2]) * sinp;
    }

    public static int checkbrk(String format, Object ... args) {
        StringBuilder buff = new StringBuilder();
        if (format.isEmpty()) {
            System.out.println("");
            return 1;
        }
        String message = String.format(format, args);
        buff.append(message);
        if (!proc_rov.isEmpty() && !proc_base.isEmpty()) {
            buff.append(" (").append(proc_rov).append("-").append(proc_base).append(")");
        } else if (!proc_rov.isEmpty()) {
            buff.append(" (").append(proc_rov).append(")");
        } else if (!proc_base.isEmpty()) {
            buff.append(" (").append(proc_base).append(")");
        }
        System.out.println(buff.toString());
        return 0;
    }

    public static double SQR(double x) {
        return x * x;
    }

    public static int strrchr(String str, char ch) {
        for (int i = str.length() - 1; i >= 0; --i) {
            if (str.charAt(i) != ch) continue;
            return i;
        }
        return -1;
    }

    public static void init_sta(sta_t sta) {
        sta.setName("");
        sta.setMarker("");
        sta.setAntdes("");
        sta.setAntsno("");
        sta.setRectype("");
        sta.setRecver("");
        sta.setRecsno("");
        sta.setAntsetup(0);
        sta.setItrf(0);
        sta.setDeltype(0);
        for (int i = 0; i < 3; ++i) {
            sta.getPos()[i] = 0.0;
            sta.getDel()[i] = 0.0;
        }
        sta.setHgt(0.0);
    }

    public static void strncpy(char[] dest, char[] src, int n) {
        System.arraycopy(src, 0, dest, 0, n);
    }

    public static String strchr(String str, char c) {
        int index = str.indexOf(c);
        String substr = "";
        if (index != -1) {
            substr = str.substring(index);
        } else {
            System.out.println("The character '" + c + "' is not found in the string");
        }
        return substr;
    }

    public static String code2obs(int code) {
        if (code <= 0 || 68 < code) {
            return "";
        }
        return obscodes[code];
    }

    public static int getcodepri(int sys, byte code, String opt) {
        char obsChar;
        int i;
        char[] str = new char[8];
        String[] codeOpts = new String[]{"-GL%2s", "-RL%2s", "-EL%2s", "-JL%2s", "-SL%2s", "-CL%2s", "-IL%2s"};
        switch (sys) {
            case 1: {
                i = 0;
                String optstr = "-GL%2s";
                break;
            }
            case 4: {
                i = 1;
                String optstr = "-RL%2s";
                break;
            }
            case 8: {
                i = 2;
                String optstr = "-EL%2s";
                break;
            }
            case 16: {
                i = 3;
                String optstr = "-JL%2s";
                break;
            }
            case 2: {
                i = 4;
                String optstr = "-SL%2s";
                break;
            }
            case 32: {
                i = 5;
                String optstr = "-CL%2s";
                break;
            }
            case 64: {
                i = 6;
                String optstr = "-IL%2s";
                break;
            }
            default: {
                return 0;
            }
        }
        int j = util2.code2idx(sys, code);
        if (j < 0) {
            return 0;
        }
        String obs = util2.code2obs(code);
        for (int k = 0; k < opt.length(); ++k) {
            String str1;
            if (opt.charAt(k) != '-' || !(str1 = opt.substring(k + 1, k + 3)).equals(String.format(codeOpts[i], obs))) continue;
            return str1.charAt(1) == obs.charAt(1) ? 15 : 0;
        }
        char[] code1 = codepris[i][j].toCharArray();
        int index = new String(code1).indexOf(obsChar = obs.toCharArray()[1]);
        if (index != -1) {
            return 14 - index;
        }
        return 0;
    }

    public static void covecef(double[] pos, double[] Q, double[] P) {
        double[] E = new double[9];
        double[] EQ = new double[9];
        rescode.xyz2enu(pos, E);
        math.matmul("TN", 3, 3, 3, 1.0, E, Q, 0.0, EQ);
        math.matmul("NN", 3, 3, 3, 1.0, EQ, E, 0.0, P);
    }

    public static int code2idx(int sys, byte code) {
        double[] freq = new double[1];
        return switch (sys) {
            case 1 -> util2.code2freq_GPS(code, freq);
            case 4 -> util2.code2freq_GLO(code, 0, freq);
            case 8 -> util2.code2freq_GAL(code, freq);
            case 16 -> util2.code2freq_QZS(code, freq);
            case 2 -> util2.code2freq_SBS(code, freq);
            case 32 -> util2.code2freq_BDS(code, freq);
            case 64 -> util2.code2freq_IRN(code, freq);
            default -> -1;
        };
    }

    public static int code2freq_GPS(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            return switch (obs.toCharArray()[0]) {
                case '1' -> {
                    freq[0] = 1.57542E9;
                    yield 0;
                }
                case '2' -> {
                    freq[0] = 1.2276E9;
                    yield 1;
                }
                case '5' -> {
                    freq[0] = 1.17645E9;
                    yield 2;
                }
                default -> -1;
            };
        }
        return -1;
    }

    public static int code2freq_GLO(int code, int fcn, double[] freq) {
        String obs = util2.code2obs(code);
        if (fcn < -7 || fcn > 6) {
            return -1;
        }
        if (!Objects.equals(obs, "")) {
            return switch (obs.toCharArray()[0]) {
                case '1' -> {
                    freq[0] = 1.602E9 + 562500.0 * (double)fcn;
                    yield 0;
                }
                case '2' -> {
                    freq[0] = 1.246E9 + 437500.0 * (double)fcn;
                    yield 1;
                }
                case '3' -> {
                    freq[0] = 1.202025E9;
                    yield 2;
                }
                case '4' -> {
                    freq[0] = 1.600995E9;
                    yield 0;
                }
                case '6' -> {
                    freq[0] = 1.24806E9;
                    yield 1;
                }
                default -> -1;
            };
        }
        return -1;
    }

    public static int code2freq_GAL(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            return switch (obs.toCharArray()[0]) {
                case '1' -> {
                    freq[0] = 1.57542E9;
                    yield 0;
                }
                case '7' -> {
                    freq[0] = 1.20714E9;
                    yield 1;
                }
                case '5' -> {
                    freq[0] = 1.17645E9;
                    yield 2;
                }
                case '6' -> {
                    freq[0] = 1.27875E9;
                    yield 3;
                }
                case '8' -> {
                    freq[0] = 1.191795E9;
                    yield 4;
                }
                default -> -1;
            };
        }
        return -1;
    }

    public static int code2freq_QZS(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            return switch (obs.toCharArray()[0]) {
                case '1' -> {
                    freq[0] = 1.57542E9;
                    yield 0;
                }
                case '2' -> {
                    freq[0] = 1.2276E9;
                    yield 1;
                }
                case '5' -> {
                    freq[0] = 1.17645E9;
                    yield 2;
                }
                case '6' -> {
                    freq[0] = 1.27875E9;
                    yield 3;
                }
                default -> -1;
            };
        }
        return -1;
    }

    public static int code2freq_SBS(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            switch (obs.toCharArray()[0]) {
                case '1': {
                    freq[0] = 1.57542E9;
                    return 0;
                }
                case '5': {
                    freq[0] = 1.17645E9;
                    return 1;
                }
            }
        }
        return -1;
    }

    public static int code2freq_BDS(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            switch (obs.toCharArray()[0]) {
                case '1': {
                    freq[0] = 1.57542E9;
                    return 0;
                }
                case '2': {
                    freq[0] = 1.561098E9;
                    return 0;
                }
                case '7': {
                    freq[0] = 1.20714E9;
                    return 1;
                }
                case '5': {
                    freq[0] = 1.17645E9;
                    return 2;
                }
                case '6': {
                    freq[0] = 1.26852E9;
                    return 3;
                }
                case '8': {
                    freq[0] = 1.191795E9;
                    return 4;
                }
            }
        }
        return -1;
    }

    public static int code2freq_IRN(int code, double[] freq) {
        String obs = util2.code2obs(code);
        if (!Objects.equals(obs, "")) {
            switch (obs.toCharArray()[0]) {
                case '5': {
                    freq[0] = 1.17645E9;
                    return 0;
                }
                case '9': {
                    freq[0] = 2.492028E9;
                    return 1;
                }
            }
        }
        return -1;
    }

    public static int obs2code(char[] obs) {
        for (int i = 1; i < obscodes.length && !obscodes[i].isEmpty(); ++i) {
            if (!String.valueOf(obs).contains(obscodes[i])) continue;
            return i;
        }
        return 0;
    }

    public static String convcode(double ver, int sys, String str, String type) {
        type = "   ";
        if (str.compareTo("P1") == 0) {
            if (sys == 1) {
                type = String.format("%c1W", Character.valueOf('C'));
            } else if (sys == 4) {
                type = String.format("%c1P", Character.valueOf('C'));
            }
        } else if (str.compareTo("P2") == 0) {
            if (sys == 1) {
                type = String.format("%c2W", Character.valueOf('C'));
            } else if (sys == 4) {
                type = String.format("%c2P", Character.valueOf('C'));
            }
        } else if (str.compareTo("C1") == 0) {
            if (!(ver >= 2.12)) {
                if (sys == 1) {
                    type = String.format("%c1C", Character.valueOf('C'));
                } else if (sys == 4) {
                    type = String.format("%c1C", Character.valueOf('C'));
                } else if (sys == 8) {
                    type = String.format("%c1X", Character.valueOf('C'));
                } else if (sys == 16) {
                    type = String.format("%c1C", Character.valueOf('C'));
                } else if (sys == 2) {
                    type = String.format("%c1C", Character.valueOf('C'));
                }
            }
        } else if (str.compareTo("C2") == 0) {
            if (sys == 1) {
                type = ver > 2.12 ? String.format("%c2W", Character.valueOf('C')) : String.format("%c2X", Character.valueOf('C'));
            } else if (sys == 4) {
                type = String.format("%c2C", Character.valueOf('C'));
            } else if (sys == 16) {
                type = String.format("%c2X", Character.valueOf('C'));
            } else if (sys == 32) {
                type = String.format("%c2X", Character.valueOf('C'));
            }
        } else if (ver >= 2.12 && str.toCharArray()[1] == 'A') {
            if (sys == 1) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 4) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 2) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (ver >= 2.12 && str.toCharArray()[1] == 'B') {
            if (sys == 1) {
                type = String.format("%c1X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c1X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (ver >= 2.12 && str.toCharArray()[1] == 'C') {
            if (sys == 1) {
                type = String.format("%c2X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c2X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (ver >= 2.12 && str.toCharArray()[1] == 'D') {
            if (sys == 4) {
                type = String.format("%c2C", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (ver >= 2.12 && str.toCharArray()[1] == '1') {
            if (sys == 1) {
                type = String.format("%c1W", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 4) {
                type = String.format("%c1P", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 8) {
                type = String.format("%c1X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 32) {
                type = String.format("%c2X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (ver < 2.12 && str.toCharArray()[1] == '1') {
            if (sys == 1) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 4) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 8) {
                type = String.format("%c1X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 2) {
                type = String.format("%c1C", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (str.toCharArray()[1] == '2') {
            if (sys == 1) {
                type = String.format("%c2W", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 4) {
                type = String.format("%c2P", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c2X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 32) {
                type = String.format("%c2X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (str.toCharArray()[1] == '5') {
            if (sys == 1) {
                type = String.format("%c5X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 8) {
                type = String.format("%c5X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c5X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 2) {
                type = String.format("%c5X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (str.toCharArray()[1] == '6') {
            if (sys == 8) {
                type = String.format("%c6X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 16) {
                type = String.format("%c6X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 32) {
                type = String.format("%c6X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (str.toCharArray()[1] == '7') {
            if (sys == 8) {
                type = String.format("%c7X", Character.valueOf(str.toCharArray()[0]));
            } else if (sys == 32) {
                type = String.format("%c7X", Character.valueOf(str.toCharArray()[0]));
            }
        } else if (str.toCharArray()[1] == '8' && sys == 8) {
            type = String.format("%c8X", Character.valueOf(str.toCharArray()[0]));
        }
        return type;
    }
}

