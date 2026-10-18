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
import com.rtklib.readobsnav.rtkUncompress;
import com.rtklib.util.util2;
import java.io.IOException;
import jdk.internal.org.jline.utils.Log;

public class readRnxFile {
    public static int read_rnxfile(String file, gtime_t ts, gtime_t te, double tint, char[] opt, int flag, int index, char type, obs_t obs, nav_t nav, sta_t sta) throws IOException {
        char[] tmpfile = new char[1024];
        Log.trace((Object[])new Object[]{3, "readrnxfile: file=%s flag=%d index=%d\n", file, flag, index});
        util2.init_sta(sta);
        int cstat = rtkUncompress.rtk_uncompress(file, tmpfile);
        if (cstat < 0) {
            Log.trace((Object[])new Object[]{2, "rinex file uncompact error: %s\n", file});
            return 0;
        }
        return 0;
    }
}

