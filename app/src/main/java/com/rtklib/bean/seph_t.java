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

public class seph_t {
    private int sat;
    private gtime_t t0;
    private gtime_t tof;
    private int sva;
    private int svh;
    public double[] pos;
    public double[] vel;
    public double[] acc;
    private double af0;
    private double af1;

    public int getSat() {
        return this.sat;
    }

    public gtime_t getT0() {
        return this.t0;
    }

    public gtime_t getTof() {
        return this.tof;
    }

    public int getSva() {
        return this.sva;
    }

    public int getSvh() {
        return this.svh;
    }

    public double[] getPos() {
        return this.pos;
    }

    public double[] getVel() {
        return this.vel;
    }

    public double[] getAcc() {
        return this.acc;
    }

    public double getAf0() {
        return this.af0;
    }

    public double getAf1() {
        return this.af1;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void setT0(gtime_t t0) {
        this.t0 = t0;
    }

    public void setTof(gtime_t tof) {
        this.tof = tof;
    }

    public void setSva(int sva) {
        this.sva = sva;
    }

    public void setSvh(int svh) {
        this.svh = svh;
    }

    public void setPos(double[] pos) {
        this.pos = pos;
    }

    public void setVel(double[] vel) {
        this.vel = vel;
    }

    public void setAcc(double[] acc) {
        this.acc = acc;
    }

    public void setAf0(double af0) {
        this.af0 = af0;
    }

    public void setAf1(double af1) {
        this.af1 = af1;
    }
}

