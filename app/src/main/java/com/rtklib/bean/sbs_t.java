/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.sbsmsg_t;

public class sbs_t {
    public int n;
    int nmax;
    public sbsmsg_t msgs;

    public sbs_t(int n, int nmax, sbsmsg_t msgs) {
        this.n = n;
        this.nmax = nmax;
        this.msgs = msgs;
    }

    public sbs_t() {
    }

    public int getN() {
        return this.n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getNmax() {
        return this.nmax;
    }

    public void setNmax(int nmax) {
        this.nmax = nmax;
    }

    public sbsmsg_t getMsgs() {
        return this.msgs;
    }

    public void setMsgs(sbsmsg_t msgs) {
        this.msgs = msgs;
    }
}

