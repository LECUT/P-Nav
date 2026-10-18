/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

public class filopt_t {
    public static final int MAXSTRPATH = 1024;
    public char[] satantp = new char[1024];
    public char[] rcvantp = new char[1024];
    public char[] stapos = new char[1024];
    public char[] geoid = new char[1024];
    char[] iono = new char[1024];
    char[] dcb = new char[1024];
    char[] eop = new char[1024];
    char[] blq = new char[1024];
    char[] tempdir = new char[1024];
    char[] geexe = new char[1024];
    char[] solstat = new char[1024];
    char[] trace = new char[1024];
}

