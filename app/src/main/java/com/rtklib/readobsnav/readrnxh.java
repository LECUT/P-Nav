/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.nav_t;
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.decodeGnavh;
import com.rtklib.readobsnav.decodeHnavh;
import com.rtklib.readobsnav.decodeNavh;
import com.rtklib.readobsnav.decodeObsh;
import com.rtklib.util.util1;
import java.io.BufferedReader;

public class readrnxh {
    public static final int SYS_GPS = 1;
    public static final int SYS_GLO = 4;
    public static final int SYS_GAL = 8;
    public static final int SYS_SBS = 2;
    public static final int SYS_QZS = 16;
    public static final int SYS_CMP = 32;
    public static final int SYS_ALL = 255;
    public static final int SYS_IRN = 64;
    public static final int SYS_LEO = 128;
    public static final int SYS_NONE = 0;
    static final int TSYS_GPS = 0;
    static final int TSYS_UTC = 1;
    static final int TSYS_GAL = 3;
    static final int TSYS_QZS = 4;
    static final int TSYS_CMP = 5;
    static final int TSYS_IRN = 6;
    public static final int MAXOBSTYPE = 64;
    static final int MAXPRNGLO = 27;
    static final int MAXRNXLEN = 1028;
    static final int MAXPOSHEAD = 1024;

    public static int read_rnxh(BufferedReader reader, double[] ver, char[] type, int[] sys, int[] tsys, char[][][] tobs, nav_t nav, sta_t sta) {
        int i = 0;
        ver[0] = 2.1;
        sys[0] = 1;
        try {
            String temp;
            block18: while ((temp = reader.readLine()) != null) {
                char[] buff = temp.toCharArray();
                if (temp.length() <= 60) continue;
                if (temp.contains("RINEX VERSION / TYPE")) {
                    ver[0] = util1.str2num(buff, 0, 9);
                    type[0] = buff[20];
                    switch (buff[40]) {
                        case ' ': 
                        case 'G': {
                            sys[0] = 1;
                            tsys[0] = 0;
                            continue block18;
                        }
                        case 'R': {
                            sys[0] = 4;
                            tsys[0] = 1;
                            continue block18;
                        }
                        case 'E': {
                            sys[0] = 8;
                            tsys[0] = 3;
                            continue block18;
                        }
                        case 'S': {
                            sys[0] = 2;
                            tsys[0] = 0;
                            continue block18;
                        }
                        case 'J': {
                            sys[0] = 16;
                            tsys[0] = 4;
                            continue block18;
                        }
                        case 'C': {
                            sys[0] = 32;
                            tsys[0] = 5;
                            continue block18;
                        }
                        case 'I': {
                            sys[0] = 64;
                            tsys[0] = 6;
                            continue block18;
                        }
                        case 'M': {
                            sys[0] = 0;
                            tsys[0] = 0;
                            continue block18;
                        }
                    }
                    System.out.println("Not supported satellite system:");
                    continue;
                }
                if (temp.contains("PGM / RUN BY / DATE") || temp.contains("COMMENT")) continue;
                switch (type[0]) {
                    case 'O': {
                        decodeObsh.decode_obsh(reader, buff, ver, tsys, tobs, nav, sta);
                        break;
                    }
                    case 'J': 
                    case 'L': 
                    case 'N': {
                        decodeNavh.decode_navh(buff, nav);
                        break;
                    }
                    case 'G': {
                        decodeGnavh.decode_gnavh(buff, nav);
                        break;
                    }
                    case 'H': {
                        decodeHnavh.decode_hnavh(buff, nav);
                    }
                }
                if (temp.contains("END OF HEADER")) {
                    return 1;
                }
                if (++i < 1024 || type[0] != ' ') continue;
                break;
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}

