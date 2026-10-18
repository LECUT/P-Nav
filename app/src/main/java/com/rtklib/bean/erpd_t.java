/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

public class erpd_t {
    double mjd;
    double xp;
    double yp;
    double xpr;
    double ypr;
    double ut1_utc;
    double lod;

    public double getMjd() {
        return this.mjd;
    }

    public void setMjd(double mjd) {
        this.mjd = mjd;
    }

    public double getXp() {
        return this.xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public double getYp() {
        return this.yp;
    }

    public void setYp(double yp) {
        this.yp = yp;
    }

    public double getXpr() {
        return this.xpr;
    }

    public void setXpr(double xpr) {
        this.xpr = xpr;
    }

    public double getYpr() {
        return this.ypr;
    }

    public void setYpr(double ypr) {
        this.ypr = ypr;
    }

    public double getUt1_utc() {
        return this.ut1_utc;
    }

    public void setUt1_utc(double ut1_utc) {
        this.ut1_utc = ut1_utc;
    }

    public double getLod() {
        return this.lod;
    }

    public void setLod(double lod) {
        this.lod = lod;
    }
}

