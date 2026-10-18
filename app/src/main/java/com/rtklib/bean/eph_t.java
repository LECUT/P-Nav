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

public class eph_t {
    private int sat;
    private int iode;
    private int iodc;
    private int sva;
    private int svh;
    private int[] week;
    private int code;
    private int flag;
    private gtime_t toe;
    private gtime_t toc;
    private gtime_t ttr;
    private double A;
    private double e;
    private double i0;
    private double OMG0;
    private double omg;
    private double M0;
    private double deln;
    private double OMGd;
    private double idot;
    private double crc;
    private double crs;
    private double cuc;
    private double cus;
    private double cic;
    private double cis;
    private double toes;
    private double fit;
    private double f0;
    private double f1;
    private double f2;
    public double[] tgd = new double[2];
    private double Adot;
    private double ndot;

    public int getSat() {
        return this.sat;
    }

    public int getIode() {
        return this.iode;
    }

    public int getIodc() {
        return this.iodc;
    }

    public int getSva() {
        return this.sva;
    }

    public int getSvh() {
        return this.svh;
    }

    public int[] getWeek() {
        return this.week;
    }

    public int getCode() {
        return this.code;
    }

    public int getFlag() {
        return this.flag;
    }

    public gtime_t getToe() {
        return this.toe;
    }

    public gtime_t getToc() {
        return this.toc;
    }

    public gtime_t getTtr() {
        return this.ttr;
    }

    public double getA() {
        return this.A;
    }

    public double getE() {
        return this.e;
    }

    public double getI0() {
        return this.i0;
    }

    public double getOMG0() {
        return this.OMG0;
    }

    public double getOmg() {
        return this.omg;
    }

    public double getM0() {
        return this.M0;
    }

    public double getDeln() {
        return this.deln;
    }

    public double getOMGd() {
        return this.OMGd;
    }

    public double getIdot() {
        return this.idot;
    }

    public double getCrc() {
        return this.crc;
    }

    public double getCrs() {
        return this.crs;
    }

    public double getCuc() {
        return this.cuc;
    }

    public double getCus() {
        return this.cus;
    }

    public double getCic() {
        return this.cic;
    }

    public double getCis() {
        return this.cis;
    }

    public double getToes() {
        return this.toes;
    }

    public double getFit() {
        return this.fit;
    }

    public double getF0() {
        return this.f0;
    }

    public double getF1() {
        return this.f1;
    }

    public double getF2() {
        return this.f2;
    }

    public double[] getTgd() {
        return this.tgd;
    }

    public double getAdot() {
        return this.Adot;
    }

    public double getNdot() {
        return this.ndot;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void setIode(int iode) {
        this.iode = iode;
    }

    public void setIodc(int iodc) {
        this.iodc = iodc;
    }

    public void setSva(int sva) {
        this.sva = sva;
    }

    public void setSvh(int svh) {
        this.svh = svh;
    }

    public void setWeek(int[] week) {
        this.week = week;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public void setToe(gtime_t toe) {
        this.toe = toe;
    }

    public void setToc(gtime_t toc) {
        this.toc = toc;
    }

    public void setTtr(gtime_t ttr) {
        this.ttr = ttr;
    }

    public void setA(double a) {
        this.A = a;
    }

    public void setE(double e) {
        this.e = e;
    }

    public void setI0(double i0) {
        this.i0 = i0;
    }

    public void setOMG0(double OMG0) {
        this.OMG0 = OMG0;
    }

    public void setOmg(double omg) {
        this.omg = omg;
    }

    public void setM0(double m0) {
        this.M0 = m0;
    }

    public void setDeln(double deln) {
        this.deln = deln;
    }

    public void setOMGd(double OMGd) {
        this.OMGd = OMGd;
    }

    public void setIdot(double idot) {
        this.idot = idot;
    }

    public void setCrc(double crc) {
        this.crc = crc;
    }

    public void setCrs(double crs) {
        this.crs = crs;
    }

    public void setCuc(double cuc) {
        this.cuc = cuc;
    }

    public void setCus(double cus) {
        this.cus = cus;
    }

    public void setCic(double cic) {
        this.cic = cic;
    }

    public void setCis(double cis) {
        this.cis = cis;
    }

    public void setToes(double toes) {
        this.toes = toes;
    }

    public void setFit(double fit) {
        this.fit = fit;
    }

    public void setF0(double f0) {
        this.f0 = f0;
    }

    public void setF1(double f1) {
        this.f1 = f1;
    }

    public void setF2(double f2) {
        this.f2 = f2;
    }

    public void setAdot(double adot) {
        this.Adot = adot;
    }

    public void setNdot(double ndot) {
        this.ndot = ndot;
    }
}

