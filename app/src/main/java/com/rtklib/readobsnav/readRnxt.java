/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.sta_t;
import com.rtklib.readobsnav.readRnxfp;
import java.io.File;
import java.io.IOException;

public class readRnxt {
    public static final int MAXEXFILE = 1024;

    public static int read_rnxt(String file, int rcv, gtime_t ts, gtime_t te, double tint, char[] opt, obs_t obs, nav_t nav, sta_t sta) throws IOException, CloneNotSupportedException {
        boolean stat = false;
        char[] type = new char[1];
        String[] files = new String[1024];
        return readRnxfp.read_rnxfp(new File(file), ts, te, tint, opt, 0, rcv, type, obs, nav, sta);
    }
}

