/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

public class sigind_t {
    public int n;
    public int[] idx = new int[64];
    public int[] pos = new int[64];
    public byte[] pri = new byte[64];
    public byte[] type = new byte[64];
    public byte[] code = new byte[64];
    public double[] shift = new double[64];
}

