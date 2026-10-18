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

public class pcv_t {
    public int sat;
    public char[] type = new char[64];
    public char[] code = new char[64];
    public gtime_t[] ts;
    public gtime_t[] te;
    public double[][] off = new double[3][3];
    public double[][] var = new double[3][19];

    public pcv_t(int sat, char[] type, char[] code, gtime_t[] ts, gtime_t[] te, double[][] off, double[][] var) {
        this.sat = sat;
        this.type = type;
        this.code = code;
        this.ts = ts;
        this.te = te;
        this.off = off;
        this.var = var;
    }

    public pcv_t() {
    }

    public int getSat() {
        return this.sat;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public char[] getType() {
        return this.type;
    }

    public void setType(char[] type) {
        this.type = type;
    }

    public char[] getCode() {
        return this.code;
    }

    public void setCode(char[] code) {
        this.code = code;
    }

    public gtime_t[] getTs() {
        return this.ts;
    }

    public void setTs(gtime_t[] ts) {
        this.ts = ts;
    }

    public gtime_t[] getTe() {
        return this.te;
    }

    public void setTe(gtime_t[] te) {
        this.te = te;
    }

    public double[][] getOff() {
        return this.off;
    }

    public void setOff(double[][] off) {
        this.off = off;
    }

    public double[][] getVar() {
        return this.var;
    }

    public void setVar(double[][] var) {
        this.var = var;
    }
}

