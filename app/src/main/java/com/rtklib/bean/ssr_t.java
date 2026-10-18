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

public class ssr_t {
    public static final int MAXCODE = 68;
    gtime_t[] t0 = new gtime_t[6];
    double[] udi = new double[6];
    int[] iod = new int[6];
    int iode;
    int iodcrc;
    int ura;
    int refd;
    double[] deph = new double[3];
    double[] ddeph = new double[3];
    double[] dclk = new double[3];
    double hrclk;
    float[] cbias = new float[68];
    double[] pbias = new double[68];
    float[] stdpb = new float[68];
    double yaw_ang;
    double yaw_rate;
    byte update;
}

