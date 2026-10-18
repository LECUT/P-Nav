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

/**
 * 观测数据记录 — EX 2.5.0 版本
 * SNR 改为 float[] (dBHz)，新增 GLONASS freq/Lstd/Pstd 字段
 */
public class obsd_t
implements Cloneable {
    public static final int NFREQ = 5;
    public static final int NEXOBS = 0;
    public gtime_t time = new gtime_t();
    public int[] sat = new int[1];
    public int rcv;
    public int freq;                          /* GLONASS frequency channel (0-13) */
    public float[] SNR = new float[NFREQ+NEXOBS];   /* signal strength (dBHz) */
    public int[] LLI = new int[NFREQ+NEXOBS];
    public int[] code = new int[NFREQ+NEXOBS];
    public double[] L = new double[NFREQ+NEXOBS];
    public double[] P = new double[NFREQ+NEXOBS];
    public float[] D = new float[NFREQ+NEXOBS];
    public float[] Lstd = new float[NFREQ+NEXOBS];  /* stdev of carrier phase (cycles) */
    public float[] Pstd = new float[NFREQ+NEXOBS];  /* stdev of pseudorange (meters) */
    public int timevalid;                     /* time is valid (Valid GNSS fix) */
    public gtime_t eventime = new gtime_t();  /* time of event (GPST) */

    public Object clone() throws CloneNotSupportedException {
        int[] sat = this.sat;
        int[] newsat = new int[sat.length];
        newsat[0] = sat[0];
        float[] SNR = this.SNR;
        float[] newSNR = new float[SNR.length];
        System.arraycopy(SNR, 0, newSNR, 0, SNR.length);
        double[] L = this.L;
        double[] newL = new double[L.length];
        System.arraycopy(L, 0, newL, 0, L.length);
        double[] P = this.P;
        double[] newP = new double[P.length];
        System.arraycopy(P, 0, newP, 0, P.length);
        float[] D = this.D;
        float[] newD = new float[D.length];
        System.arraycopy(D, 0, newD, 0, D.length);
        int[] LLI = this.LLI;
        int[] newLLI = new int[LLI.length];
        System.arraycopy(LLI, 0, newLLI, 0, LLI.length);
        int[] code = this.code;
        int[] newcode = new int[code.length];
        System.arraycopy(code, 0, newcode, 0, code.length);
        float[] Lstd = this.Lstd;
        float[] newLstd = new float[Lstd.length];
        System.arraycopy(Lstd, 0, newLstd, 0, Lstd.length);
        float[] Pstd = this.Pstd;
        float[] newPstd = new float[Pstd.length];
        System.arraycopy(Pstd, 0, newPstd, 0, Pstd.length);
        obsd_t newdata = (obsd_t)super.clone();
        newdata.sat = newsat;
        newdata.freq = this.freq;
        newdata.SNR = newSNR;
        newdata.L = newL;
        newdata.P = newP;
        newdata.D = newD;
        newdata.LLI = newLLI;
        newdata.code = newcode;
        newdata.Lstd = newLstd;
        newdata.Pstd = newPstd;
        newdata.timevalid = this.timevalid;
        gtime_t newEventime = new gtime_t();
        newEventime.time = this.eventime.time;
        newEventime.sec = this.eventime.sec;
        newdata.eventime = newEventime;
        return newdata;
    }
}
