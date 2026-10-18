/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.sbsigp_t;

public class sbsion_t {
    public static final int MAXNIGP = 201;
    int iodi;
    int nigp;
    sbsigp_t[] igp = new sbsigp_t[201];

    public sbsion_t(int iodi, int nigp, sbsigp_t[] igp) {
        this.iodi = iodi;
        this.nigp = nigp;
        this.igp = igp;
    }

    public int getIodi() {
        return this.iodi;
    }

    public void setIodi(int iodi) {
        this.iodi = iodi;
    }

    public int getNigp() {
        return this.nigp;
    }

    public void setNigp(int nigp) {
        this.nigp = nigp;
    }

    public sbsigp_t[] getIgp() {
        return this.igp;
    }

    public void setIgp(sbsigp_t[] igp) {
        this.igp = igp;
    }
}

