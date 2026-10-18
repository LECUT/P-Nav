/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.ambc_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sol_t;
import com.rtklib.bean.ssat_t;

/**
 * RTK 控制/结果类型 — EX 2.5.0 版本
 * 新增 excsat/nb_ar/holdamb/initial_mode/epoch/intpres_nb/vtec_used/intpres_obsb
 */
public class rtk_t {
    final int MAXERRMSG = 4096;
    public sol_t sol = new sol_t();
    public double[] rb = new double[6];
    public int nx;
    public int na;
    public double tt;
    public double[] x;
    public double[] P;
    public double[] xa;
    public double[] Pa;
    public int nfix;
    public int excsat;                         /* index of next satellite to be excluded for partial AR */
    public int nb_ar;                          /* number of ambiguities used for AR last epoch */
    public byte holdamb;                       /* set if fix-and-hold has occurred at least once */
    public ambc_t[] ambc = new ambc_t[nav_t.getMaxSat()];
    public ssat_t[] ssat = new ssat_t[nav_t.getMaxSat()];
    public int neb;
    public char[] errbuf = new char[4096];
    public prcopt_t opt;
    public int initial_mode;                   /* initial positioning mode */
    public int epoch;                          /* epoch number */
    public int intpres_nb;                     /* number of previous base observations for interpolation */
    public int vtec_used;                      /* indicates VTEC coeffs have been used to init ion states */
    public obsd_t[] intpres_obsb = new obsd_t[96]; /* previous base observations for interpolation, MAXOBS=96 */
}
