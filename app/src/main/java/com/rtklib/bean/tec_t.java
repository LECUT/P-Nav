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

public class tec_t {
    gtime_t time;
    int[] ndata = new int[3];
    double rb;
    double[] lats = new double[3];
    double[] lons = new double[3];
    double[] hgts = new double[3];
    double[] data;
    float[] rms;

    public tec_t(gtime_t time, int[] ndata, double rb, double[] lats, double[] lons, double[] hgts, double[] data, float[] rms) {
        this.time = time;
        this.ndata = ndata;
        this.rb = rb;
        this.lats = lats;
        this.lons = lons;
        this.hgts = hgts;
        this.data = data;
        this.rms = rms;
    }

    public gtime_t getTime() {
        return this.time;
    }

    public void setTime(gtime_t time) {
        this.time = time;
    }

    public int[] getNdata() {
        return this.ndata;
    }

    public void setNdata(int[] ndata) {
        this.ndata = ndata;
    }

    public double getRb() {
        return this.rb;
    }

    public void setRb(double rb) {
        this.rb = rb;
    }

    public double[] getLats() {
        return this.lats;
    }

    public void setLats(double[] lats) {
        this.lats = lats;
    }

    public double[] getLons() {
        return this.lons;
    }

    public void setLons(double[] lons) {
        this.lons = lons;
    }

    public double[] getHgts() {
        return this.hgts;
    }

    public void setHgts(double[] hgts) {
        this.hgts = hgts;
    }

    public double[] getData() {
        return this.data;
    }

    public void setData(double[] data) {
        this.data = data;
    }

    public float[] getRms() {
        return this.rms;
    }

    public void setRms(float[] rms) {
        this.rms = rms;
    }
}

