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

public class geph_t {
    private int sat;
    private int iode;
    public int frq;
    private int svh;
    private int sva;
    private int age;
    private gtime_t toe;
    private gtime_t tof;
    private double[] pos = new double[3];
    private double[] vel = new double[3];
    private double[] acc = new double[3];
    private double taun;
    private double gamn;
    private double dtaun;

    public int getSat() {
        return this.sat;
    }

    public int getIode() {
        return this.iode;
    }

    public int getFrq() {
        return this.frq;
    }

    public int getSvh() {
        return this.svh;
    }

    public int getSva() {
        return this.sva;
    }

    public int getAge() {
        return this.age;
    }

    public gtime_t getToe() {
        return this.toe;
    }

    public gtime_t getTof() {
        return this.tof;
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

    public double getTaun() {
        return this.taun;
    }

    public double getGamn() {
        return this.gamn;
    }

    public double getDtaun() {
        return this.dtaun;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void setIode(int iode) {
        this.iode = iode;
    }

    public void setFrq(int frq) {
        this.frq = frq;
    }

    public void setSvh(int svh) {
        this.svh = svh;
    }

    public void setSva(int sva) {
        this.sva = sva;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setToe(gtime_t toe) {
        this.toe = toe;
    }

    public void setTof(gtime_t tof) {
        this.tof = tof;
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

    public void setTaun(double taun) {
        this.taun = taun;
    }

    public void setGamn(double gamn) {
        this.gamn = gamn;
    }

    public void setDtaun(double dtaun) {
        this.dtaun = dtaun;
    }
}

