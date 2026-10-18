/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import static com.rtklib.bean.obsd_t.NEXOBS;
import static com.rtklib.bean.obsd_t.NFREQ;

import com.rtklib.bean.gtime_t;

/**
 * 卫星状态类型 — EX 2.5.0 版本
 * snr 从 int[] 改为 rover/base 单独的 float[]；phw 从数组改为标量
 */
public class ssat_t {
    public byte sys;
    public byte vs;
    public double[] azel = new double[2];
    public double[] resp = new double[NFREQ+NEXOBS];
    public double[] resc = new double[NFREQ+NEXOBS];
    public double[] icbias = new double[NFREQ+NEXOBS];  /* GLONASS IC bias (cycles) */
    public byte[] vsat = new byte[NFREQ+NEXOBS];
    public float[] snr_rover = new float[NFREQ+NEXOBS]; /* rover signal strength (dBHz) */
    public float[] snr_base = new float[NFREQ+NEXOBS];  /* base signal strength (dBHz) */
    public byte[] fix = new byte[NFREQ+NEXOBS];
    public int[][] code = new int[NFREQ+NEXOBS][2];     /* current code per freq [0]=rover [1]=base */
    public byte[] slip = new byte[NFREQ+NEXOBS];
    public byte[] half = new byte[NFREQ+NEXOBS];
    public int[] lock = new int[NFREQ+NEXOBS];
    public long[] outc = new long[NFREQ+NEXOBS];
    public long[] slipc = new long[NFREQ+NEXOBS];
    public long[] rejc = new long[NFREQ+NEXOBS];
    public double[] gf = new double[NFREQ-1];
    public double[] mw = new double[NFREQ-1];
    public double phw;                                   /* phase windup (cycle) — scalar in EX */
    public gtime_t[][] pt = new gtime_t[2][NFREQ+NEXOBS];
    public double[][] ph = new double[2][NFREQ+NEXOBS];
}
