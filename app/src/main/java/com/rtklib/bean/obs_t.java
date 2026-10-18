/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.obsd_t;

public class obs_t {
    public int n;
    public int nmax;
    public obsd_t[] data;

    public int getN() {
        return this.n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void setNmax(int nmax) {
        this.nmax = nmax;
    }

    public obs_t() {
    }

    public obs_t(int n, int nmax, obsd_t[] data) {
        this.n = n;
        this.nmax = nmax;
        this.data = data;
    }
}

