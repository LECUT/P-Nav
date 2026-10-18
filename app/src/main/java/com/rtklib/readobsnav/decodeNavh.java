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
import com.rtklib.util.util1;

public class decodeNavh {
    public static void decode_navh(char[] buff, nav_t nav) {
        String label = String.valueOf(buff).substring(60);
        if (label.contains("ION ALPHA")) {
            if (nav != null) {
                int i = 0;
                int j = 2;
                while (i < 4) {
                    nav.ion_gps[i] = util1.str2num(buff, j, 12);
                    ++i;
                    j += 12;
                }
            }
        } else if (label.contains("ION BETA")) {
            if (nav != null) {
                int i = 0;
                int j = 2;
                while (i < 4) {
                    nav.ion_gps[i + 4] = util1.str2num(buff, j, 12);
                    ++i;
                    j += 12;
                }
            }
        } else if (label.contains("DELTA-UTC: A0,A1,T,W")) {
            if (nav != null) {
                int i = 0;
                int j = 3;
                while (i < 2) {
                    nav.utc_gps[i] = util1.str2num(buff, j, 19);
                    ++i;
                    j += 19;
                }
                while (i < 4) {
                    nav.utc_gps[i] = util1.str2num(buff, j, 9);
                    ++i;
                    j += 9;
                }
            }
        } else if (label.contains("IONOSPHERIC CORR")) {
            if (nav != null) {
                if (String.valueOf(buff).startsWith("GPSA")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_gps[i] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("GPSB")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_gps[i + 4] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("GAL")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_gal[i] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("QZSA")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_qzs[i] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("QZSB")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_qzs[i + 4] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("BDSA")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_cmp[i] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("BDSB")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_cmp[i + 4] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("IRNA")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_irn[i] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                } else if (String.valueOf(buff).startsWith("IRNB")) {
                    int i = 0;
                    int j = 5;
                    while (i < 4) {
                        nav.ion_irn[i + 4] = util1.str2num(buff, j, 12);
                        ++i;
                        j += 12;
                    }
                }
            }
        } else if (label.contains("TIME SYSTEM CORR")) {
            if (nav != null) {
                if (String.valueOf(buff).startsWith("GPUT")) {
                    nav.utc_gps[0] = util1.str2num(buff, 5, 17);
                    nav.utc_gps[1] = util1.str2num(buff, 22, 16);
                    nav.utc_gps[2] = util1.str2num(buff, 38, 7);
                    nav.utc_gps[3] = util1.str2num(buff, 45, 5);
                } else if (String.valueOf(buff).startsWith("GLUT")) {
                    nav.utc_glo[0] = -util1.str2num(buff, 5, 17);
                } else if (String.valueOf(buff).startsWith("GLGP")) {
                    nav.utc_glo[1] = util1.str2num(buff, 5, 17);
                } else if (String.valueOf(buff).startsWith("GAUT")) {
                    nav.utc_gal[0] = util1.str2num(buff, 5, 17);
                    nav.utc_gal[1] = util1.str2num(buff, 22, 16);
                    nav.utc_gal[2] = util1.str2num(buff, 38, 7);
                    nav.utc_gal[3] = util1.str2num(buff, 45, 5);
                } else if (String.valueOf(buff).startsWith("QZUT")) {
                    nav.utc_qzs[0] = util1.str2num(buff, 5, 17);
                    nav.utc_qzs[1] = util1.str2num(buff, 22, 16);
                    nav.utc_qzs[2] = util1.str2num(buff, 38, 7);
                    nav.utc_qzs[3] = util1.str2num(buff, 45, 5);
                } else if (String.valueOf(buff).startsWith("BDUT")) {
                    nav.utc_cmp[0] = util1.str2num(buff, 5, 17);
                    nav.utc_cmp[1] = util1.str2num(buff, 22, 16);
                    nav.utc_cmp[2] = util1.str2num(buff, 38, 7);
                    nav.utc_cmp[3] = util1.str2num(buff, 45, 5);
                } else if (String.valueOf(buff).startsWith("SBUT")) {
                    nav.utc_sbs[0] = util1.str2num(buff, 5, 17);
                    nav.utc_sbs[1] = util1.str2num(buff, 22, 16);
                    nav.utc_sbs[2] = util1.str2num(buff, 38, 7);
                    nav.utc_sbs[3] = util1.str2num(buff, 45, 5);
                } else if (String.valueOf(buff).startsWith("IRUT")) {
                    nav.utc_irn[0] = util1.str2num(buff, 5, 17);
                    nav.utc_irn[1] = util1.str2num(buff, 22, 16);
                    nav.utc_irn[2] = util1.str2num(buff, 38, 7);
                    nav.utc_irn[3] = util1.str2num(buff, 45, 5);
                    nav.utc_irn[8] = 0.0;
                }
            }
        } else if (label.contains("LEAP SECONDS") && nav != null) {
            nav.utc_gps[4] = util1.str2num(buff, 0, 6);
            nav.utc_gps[7] = util1.str2num(buff, 6, 6);
            nav.utc_gps[5] = util1.str2num(buff, 12, 6);
            nav.utc_gps[6] = util1.str2num(buff, 18, 6);
        }
    }
}

