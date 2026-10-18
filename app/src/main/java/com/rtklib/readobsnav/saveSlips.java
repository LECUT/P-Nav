/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.bean.obsd_t;

public class saveSlips {
    public static final int LLI_SLIP = 1;

    public static void save_slips(byte[][] slips, obsd_t data) {
        for (int i = 0; i < 3; ++i) {
            if ((data.LLI[i] & 1) == 0) continue;
            byte[] byArray = slips[data.sat[0] - 1];
            int n = i;
            byArray[n] = (byte)(byArray[n] | 1);
        }
    }

    public static void rest_slips(byte[][] slips, obsd_t data) {
        for (int i = 0; i < 3; ++i) {
            if ((slips[data.sat[0] - 1][i] & 1) != 0) {
                int n = i;
                data.LLI[n] = data.LLI[n] | 1;
            }
            slips[data.sat[0] - 1][i] = 0;
        }
    }
}

