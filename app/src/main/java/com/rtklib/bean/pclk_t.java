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

public class pclk_t {
    gtime_t time;
    int index;
    public double[][] clk = new double[nav_t.getMaxSat()][1];
    public float[][] std = new float[nav_t.getMaxSat()][1];

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

    public double[][] getClk() {
        return this.clk;
    }

    public void setClk(double[][] clk) {
        this.clk = clk;
    }

    public float[][] getStd() {
        return this.std;
    }

    public void setStd(float[][] std) {
        this.std = std;
    }
}

