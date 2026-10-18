/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;

public class peph_t
implements Cloneable {
    gtime_t time;
    int index;
    double[][] pos = new double[nav_t.getMaxSat()][4];
    float[][] std = new float[nav_t.getMaxSat()][4];
    double[][] vel = new double[nav_t.getMaxSat()][4];
    float[][] vst = new float[nav_t.getMaxSat()][4];
    float[][] cov = new float[nav_t.getMaxSat()][3];
    float[][] vco = new float[nav_t.getMaxSat()][3];

    public Object clone() throws CloneNotSupportedException {
        int index;
        gtime_t time;
        gtime_t newtime = time = this.time;
        int newindex = index = this.index;
        double[][] pos = this.pos;
        double[][] newpos = new double[nav_t.getMaxSat()][4];
        for (int i = 0; i < pos.length; ++i) {
            System.arraycopy(pos[i], 0, newpos[i], 0, pos[i].length);
        }
        float[][] std = this.std;
        float[][] newstd = new float[nav_t.getMaxSat()][4];
        for (int i = 0; i < std.length; ++i) {
            System.arraycopy(std[i], 0, newstd[i], 0, std[i].length);
        }
        double[][] vel = this.vel;
        double[][] newvel = new double[nav_t.getMaxSat()][4];
        for (int i = 0; i < pos.length; ++i) {
            System.arraycopy(vel[i], 0, newvel[i], 0, vel[i].length);
        }
        float[][] vst = this.vst;
        float[][] newvst = new float[nav_t.getMaxSat()][4];
        for (int i = 0; i < vst.length; ++i) {
            System.arraycopy(vst[i], 0, newvst[i], 0, vst[i].length);
        }
        float[][] cov = this.cov;
        float[][] newcov = new float[nav_t.getMaxSat()][3];
        for (int i = 0; i < cov.length; ++i) {
            System.arraycopy(cov[i], 0, newcov[i], 0, cov[i].length);
        }
        float[][] vco = this.vco;
        float[][] newvco = new float[nav_t.getMaxSat()][3];
        for (int i = 0; i < vco.length; ++i) {
            System.arraycopy(vco[i], 0, newvco[i], 0, vco[i].length);
        }
        peph_t newpeph = (peph_t)super.clone();
        newpeph.time = newtime;
        newpeph.index = newindex;
        newpeph.pos = newpos;
        newpeph.std = newstd;
        newpeph.vel = newvel;
        newpeph.vst = newvst;
        newpeph.cov = newcov;
        newpeph.vco = newvco;
        return newpeph;
    }

    public peph_t(gtime_t time, int index, double[][] pos, float[][] std, double[][] vel, float[][] vst, float[][] cov, float[][] vco) {
        this.time = time;
        this.index = index;
        this.pos = pos;
        this.std = std;
        this.vel = vel;
        this.vst = vst;
        this.cov = cov;
        this.vco = vco;
    }

    public peph_t() {
    }

    public gtime_t getTime() {
        return this.time;
    }

    public void setTime(gtime_t time) {
        this.time = time;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public double[][] getPos() {
        return this.pos;
    }

    public void setPos(double[][] pos) {
        this.pos = pos;
    }

    public float[][] getStd() {
        return this.std;
    }

    public void setStd(float[][] std) {
        this.std = std;
    }

    public double[][] getVel() {
        return this.vel;
    }

    public void setVel(double[][] vel) {
        this.vel = vel;
    }

    public float[][] getVst() {
        return this.vst;
    }

    public void setVst(float[][] vst) {
        this.vst = vst;
    }

    public float[][] getCov() {
        return this.cov;
    }

    public void setCov(float[][] cov) {
        this.cov = cov;
    }

    public float[][] getVco() {
        return this.vco;
    }

    public void setVco(float[][] vco) {
        this.vco = vco;
    }
}

