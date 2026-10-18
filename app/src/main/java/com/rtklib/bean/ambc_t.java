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

public class ambc_t {
    gtime_t[] epoch = new gtime_t[4];
    int[] n = new int[4];
    double[] LC = new double[4];
    double[] LCv = new double[4];
    int fixcnt;
    public char[] flags = new char[nav_t.getMaxSat()];
}

