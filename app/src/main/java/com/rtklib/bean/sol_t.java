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
 * 解算结果类型 — EX 2.5.0 版本
 * 新增 prev_ratio1/prev_ratio2/refstationid/eventime
 */
public class sol_t {
    public gtime_t time = new gtime_t();
    public gtime_t eventime = new gtime_t();   /* time of event (GPST) */
    public double[] rr = new double[6];
    public float[] qr = new float[6];
    public float[] qv = new float[6];
    public double[] dtr = new double[6];
    public byte type;
    public byte stat;
    public byte ns;
    public float age;
    public float ratio;
    public float prev_ratio1;                  /* previous initial AR ratio */
    public float prev_ratio2;                  /* previous final AR ratio */
    public float thres;
    public int refstationid;                   /* ref station ID */
}
