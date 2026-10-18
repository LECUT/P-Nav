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
import com.rtklib.util.util1;
import com.rtklib.util.util2;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

public class decodeObsh {
    public static void decode_obsh(BufferedReader reader, char[] buff, double[] ver, int[] tsys, char[][][] tobs, nav_t nav, sta_t sta) throws IOException {
        String syscodes = "GREJSCI";
        String frqcodes = "1256789";
        String[] str1 = new String[1];
        char[][] defcodes = new char[][]{{'C', 'W', 'X', ' ', ' ', ' ', ' '}, {'C', 'C', 'X', 'X', ' ', 'X', ' '}, {'C', ' ', 'X', 'X', 'X', 'X', ' '}, {'C', 'X', 'X', 'X', ' ', ' ', ' '}, {'C', ' ', 'X', ' ', ' ', ' ', ' '}, {'X', 'I', 'X', 'I', 'I', 'X', ' '}, {' ', ' ', 'A', ' ', ' ', ' ', 'A'}};
        double[] del = new double[3];
        double[] pos = new double[3];
        String label = String.valueOf(buff).substring(60);
        if (label.contains("MARKER NAME")) {
            sta.setName(String.valueOf(buff).substring(0, 60));
        } else if (label.contains("MARKER NUMBER")) {
            sta.setMarker(Arrays.toString(buff).substring(0, 20));
        } else if (label.contains("REC # / TYPE / VERS")) {
            sta.setRecsno(String.valueOf(buff).substring(0, 20));
            sta.setRectype(String.valueOf(buff).substring(20, 40));
            sta.setRecver(String.valueOf(buff).substring(40, 60));
        } else if (label.contains("ANT # / TYPE")) {
            sta.setAntsno(String.valueOf(buff).substring(0, 20));
            sta.setAntdes(String.valueOf(buff).substring(20, 40));
        } else if (label.contains("APPROX POSITION XYZ")) {
            int i = 0;
            int j = 0;
            while (i < 3) {
                pos[i] = util1.str2num(buff, j, 14);
                ++i;
                j += 14;
            }
            sta.setPos(pos);
        } else if (label.contains("ANTENNA: DELTA H/E/N")) {
            int i = 0;
            int j = 0;
            while (i < 3) {
                del[i] = util1.str2num(buff, j, 14);
                ++i;
                j += 14;
            }
            sta.setDel(del);
        } else if (label.contains("SYS / # / OBS TYPES")) {
            char ch = buff[0];
            String temp = new String(buff);
            int index = "GREJSCI".indexOf(ch);
            int n = (int)util1.str2num(buff, 3, 3);
            int nt = 0;
            int j = 0;
            int k = 7;
            while (j < n) {
                if (k > 58) {
                    temp = reader.readLine();
                    if (temp == null) break;
                    k = 7;
                }
                if (nt < 63 && temp != null) {
                    tobs[index][nt] = temp.substring(k, k + 3).toCharArray();
                    ++nt;
                }
                ++j;
                k += 4;
            }
            if (index == 5 && Math.abs(ver[0] - 3.02) < 0.001) {
                for (j = 0; j < nt; ++j) {
                    if (tobs[index][j][1] != '1') continue;
                    tobs[index][j][1] = 50;
                }
            }
            for (j = 0; j < nt; ++j) {
                if (tobs[index][j][2] != ' ' || !"1256789".contains(String.valueOf(tobs[index][j][1]))) continue;
                int startIndex = "1256789".indexOf(tobs[index][j][1]);
                tobs[index][j][2] = defcodes[index][startIndex];
            }
        } else if (label.contains("# / TYPES OF OBSERV")) {
            int n = (int)util1.str2num(buff, 0, 6);
            int nt = 0;
            int i = 0;
            int j = 10;
            while (i < n) {
                if (j > 58) {
                    if (reader.readLine() == null) break;
                    j = 10;
                }
                if (nt < 63) {
                    if (ver[0] <= 2.99) {
                        String str = String.valueOf(buff).substring(j, j + 2);
                        tobs[0][nt] = util2.convcode(ver[0], 1, str, String.valueOf(tobs[0][nt])).toCharArray();
                        tobs[1][nt] = util2.convcode(ver[0], 4, str, String.valueOf(tobs[1][nt])).toCharArray();
                        tobs[2][nt] = util2.convcode(ver[0], 8, str, String.valueOf(tobs[2][nt])).toCharArray();
                        tobs[3][nt] = util2.convcode(ver[0], 16, str, String.valueOf(tobs[3][nt])).toCharArray();
                        tobs[4][nt] = util2.convcode(ver[0], 2, str, String.valueOf(tobs[4][nt])).toCharArray();
                        tobs[5][nt] = util2.convcode(ver[0], 32, str, String.valueOf(tobs[5][nt])).toCharArray();
                    }
                    ++nt;
                }
                ++i;
                j += 6;
            }
        } else if (label.contains("TIME OF FIRST OBS")) {
            if (new String(buff).startsWith("GPS", 48)) {
                tsys[0] = 0;
            } else if (String.valueOf(buff).startsWith("GLO", 48)) {
                tsys[0] = 1;
            } else if (String.valueOf(buff).startsWith("GAL", 48)) {
                tsys[0] = 3;
            } else if (String.valueOf(buff).startsWith("QZS", 48)) {
                tsys[0] = 4;
            } else if (String.valueOf(buff).startsWith("BDT", 48)) {
                tsys[0] = 5;
            } else if (String.valueOf(buff).startsWith("IRN", 48)) {
                tsys[0] = 6;
            }
        } else if (label.contains("GLONASS SLOT / FRQ #")) {
            for (int i = 0; i < 8; ++i) {
                if (buff[4 + i * 7] != 'R') continue;
                int prn = (int)util1.str2num(buff, 5 + i * 7, 2);
                int fcn = (int)util1.str2num(buff, 8 + i * 7, 2);
                if (prn < 1 || prn > 27 || fcn < -7 || fcn > 6 || nav == null) continue;
                nav.glo_fcn[prn - 1] = fcn + 8;
            }
        } else if (label.contains("GLONASS COD/PHS/BIS")) {
            if (sta != null) {
                sta.getGlo_cp_bias()[0] = util1.str2num(buff, 5, 8);
                sta.getGlo_cp_bias()[1] = util1.str2num(buff, 18, 8);
                sta.getGlo_cp_bias()[2] = util1.str2num(buff, 31, 8);
                sta.getGlo_cp_bias()[3] = util1.str2num(buff, 44, 8);
            }
        } else if (label.contains("LEAP SECONDS") && sta != null) {
            nav.utc_gps[4] = util1.str2num(buff, 0, 6);
            nav.utc_gps[7] = util1.str2num(buff, 6, 6);
            nav.utc_gps[5] = util1.str2num(buff, 12, 6);
            nav.utc_gps[6] = util1.str2num(buff, 18, 6);
        }
    }
}

