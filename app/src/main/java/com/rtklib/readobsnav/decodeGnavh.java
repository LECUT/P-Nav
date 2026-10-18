/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.nav_t;
import java.util.Arrays;

public class decodeGnavh {
    public static void decode_gnavh(char[] buff, nav_t nav) {
        char[] label = Arrays.toString(buff).substring(60).toCharArray();
        if (Arrays.toString(label).contains("CORR TO SYTEM TIME") || Arrays.toString(label).contains("LEAP SECONDS")) {
            // empty if block
        }
    }
}

