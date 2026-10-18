/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.eph_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.util1;
import com.rtklib.util.util2;

public class decodeEph {
    public static int decode_eph(double[] ver, int[] sat, gtime_t[] toc, double[] data, eph_t[] eph) {
        eph_t eph0 = new eph_t();
        int sys = satSys.sat_sys(sat[0], null);
        if ((sys & 0x79) == 0) {
            return 0;
        }
        eph[0] = eph0;
        eph[0].setSat(sat[0]);
        eph[0].setToc(toc[0]);
        eph[0].setF0(data[0]);
        eph[0].setF1(data[1]);
        eph[0].setF2(data[2]);
        eph[0].setA(util2.SQR(data[10]));
        eph[0].setE(data[8]);
        eph[0].setI0(data[15]);
        eph[0].setOMG0(data[13]);
        eph[0].setOmg(data[17]);
        eph[0].setM0(data[6]);
        eph[0].setDeln(data[5]);
        eph[0].setOMGd(data[18]);
        eph[0].setIdot(data[19]);
        eph[0].setCrc(data[16]);
        eph[0].setCrs(data[4]);
        eph[0].setCuc(data[7]);
        eph[0].setCus(data[9]);
        eph[0].setCic(data[12]);
        eph[0].setCis(data[14]);
        if (sys == 1 || sys == 16) {
            eph[0].setIode((int)data[3]);
            eph[0].setIodc((int)data[26]);
            eph[0].setToes(data[11]);
            int[] tempweek = new int[]{(int)data[21]};
            eph[0].setWeek(tempweek);
            eph[0].setToe(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[11]), toc[0]));
            eph[0].setTtr(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[27]), toc[0]));
            eph[0].setCode((int)data[20]);
            eph[0].setSvh((int)data[24]);
            eph[0].setSva(util1.uraIndex(data[23]));
            eph[0].setFlag((int)data[22]);
            eph[0].tgd[0] = data[25];
            eph[0].tgd[1] = data[26];
            if (sys == 1) {
                eph[0].setFit(data[28]);
            } else {
                eph[0].setFit(data[28] == 0.0 ? 1.0 : 2.0);
            }
        } else if (sys == 8) {
            eph[0].setIode((int)data[3]);
            eph[0].setToes(data[11]);
            int[] tempweek = new int[]{(int)data[21]};
            eph[0].setWeek(tempweek);
            eph[0].setToe(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[11]), toc[0]));
            eph[0].setTtr(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[27]), toc[0]));
            eph[0].setCode((int)data[20]);
            eph[0].setSvh((int)data[24]);
            eph[0].setSva(util1.sisaIndex(data[23]));
            eph[0].getTgd()[0] = data[25];
            eph[0].getTgd()[1] = data[26];
        } else if (sys == 32) {
            eph[0].setToc(util1.bdt2gpst(eph[0].getToc()));
            eph[0].setIode((int)data[3]);
            eph[0].setIodc((int)data[28]);
            eph[0].setToes(data[11]);
            int[] tempweek = new int[]{(int)data[21]};
            eph[0].setWeek(tempweek);
            eph[0].setToe(util1.bdt2gpst(util1.bdt2time(eph[0].getWeek(), data[11])));
            eph[0].setTtr(util1.bdt2gpst(util1.bdt2time(eph[0].getWeek(), data[27])));
            eph[0].setToe(util1.adjweek(eph[0].getToe(), toc[0]));
            eph[0].setTtr(util1.adjweek(eph[0].getTtr(), toc[0]));
            eph[0].setSvh((int)data[24]);
            eph[0].setSva(util1.uraIndex(data[23]));
            eph[0].getTgd()[0] = data[25];
            eph[0].getTgd()[1] = data[26];
        } else if (sys == 64) {
            eph[0].setIode((int)data[3]);
            eph[0].setToes(data[11]);
            int[] tempweek = new int[]{(int)data[21]};
            eph[0].setWeek(tempweek);
            eph[0].setToe(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[11]), toc[0]));
            eph[0].setTtr(util1.adjweek(util1.gpst2time(eph[0].getWeek(), data[27]), toc[0]));
            eph[0].setSvh((int)data[24]);
            eph[0].setSva(util1.uraIndex(data[23]));
            eph[0].getTgd()[0] = data[25];
        }
        return 1;
    }
}

