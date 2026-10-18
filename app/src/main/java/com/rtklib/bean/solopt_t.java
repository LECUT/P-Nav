/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

public class solopt_t {
    public int posf;
    public int times;
    public int timef;
    public int timeu;
    public int degf;
    public int outhead;
    public int outopt;
    public int outvel;
    public int datum;
    public int height;
    public int geoid;
    public int solstatic;
    int sstat;
    int trace;
    public double[] nmeaintv = new double[2];
    public char[] sep = new char[64];
    public char[] prog = new char[64];
    public double maxsolstd;

    public solopt_t(int posf, int times, int timef, int timeu, int degf, int outhead, int outopt, int outvel, int datum, int height, int geoid, int solstatic, int sstat, int trace, double[] nmeaintv, char[] sep, char[] prog) {
        this.posf = posf;
        this.times = times;
        this.timef = timef;
        this.timeu = timeu;
        this.degf = degf;
        this.outhead = outhead;
        this.outopt = outopt;
        this.outvel = outvel;
        this.datum = datum;
        this.height = height;
        this.geoid = geoid;
        this.solstatic = solstatic;
        this.sstat = sstat;
        this.trace = trace;
        this.nmeaintv = nmeaintv;
        this.sep = sep;
        this.prog = prog;
        this.maxsolstd = this.maxsolstd;
    }
}

