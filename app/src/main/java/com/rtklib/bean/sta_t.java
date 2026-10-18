/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

public class sta_t {
    String name;
    String marker;
    String antdes;
    String antsno;
    String rectype;
    String recver;
    String recsno;
    int antsetup;
    int itrf;
    int deltype;
    double[] pos = new double[3];
    double[] del = new double[3];
    double hgt;
    int glo_cp_align;
    double[] glo_cp_bias = new double[4];

    public void setName(String name) {
        this.name = name;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public void setAntdes(String antdes) {
        this.antdes = antdes;
    }

    public void setAntsno(String antsno) {
        this.antsno = antsno;
    }

    public void setRectype(String rectype) {
        this.rectype = rectype;
    }

    public void setRecver(String recver) {
        this.recver = recver;
    }

    public void setRecsno(String recsno) {
        this.recsno = recsno;
    }

    public void setAntsetup(int antsetup) {
        this.antsetup = antsetup;
    }

    public void setItrf(int itrf) {
        this.itrf = itrf;
    }

    public void setDeltype(int deltype) {
        this.deltype = deltype;
    }

    public void setPos(double[] pos) {
        this.pos = pos;
    }

    public void setDel(double[] del) {
        this.del = del;
    }

    public void setHgt(double hgt) {
        this.hgt = hgt;
    }

    public void setGlo_cp_align(int glo_cp_align) {
        this.glo_cp_align = glo_cp_align;
    }

    public void setGlo_cp_bias(double[] glo_cp_bias) {
        this.glo_cp_bias = glo_cp_bias;
    }

    public String getName() {
        return this.name;
    }

    public String getMarker() {
        return this.marker;
    }

    public String getAntdes() {
        return this.antdes;
    }

    public String getAntsno() {
        return this.antsno;
    }

    public String getRectype() {
        return this.rectype;
    }

    public String getRecver() {
        return this.recver;
    }

    public String getRecsno() {
        return this.recsno;
    }

    public int getAntsetup() {
        return this.antsetup;
    }

    public int getItrf() {
        return this.itrf;
    }

    public int getDeltype() {
        return this.deltype;
    }

    public double[] getPos() {
        return this.pos;
    }

    public double[] getDel() {
        return this.del;
    }

    public double getHgt() {
        return this.hgt;
    }

    public int getGlo_cp_align() {
        return this.glo_cp_align;
    }

    public double[] getGlo_cp_bias() {
        return this.glo_cp_bias;
    }
}

