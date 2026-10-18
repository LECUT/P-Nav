/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import java.util.Arrays;

public class setSysmask {
    public static int set_sysmask(char[] opt) {
        int mask = 0;
        boolean index = Arrays.toString(opt).contains("-SYS=");
        if (!index) {
            return 255;
        }
        block9: for (int i = String.valueOf(opt).indexOf("-SYS=") + 5; i < Arrays.toString(opt).length() && Arrays.toString(opt).charAt(i) != ' '; ++i) {
            switch (String.valueOf(opt).charAt(i)) {
                case 'G': {
                    mask |= 1;
                    continue block9;
                }
                case 'R': {
                    mask |= 4;
                    continue block9;
                }
                case 'E': {
                    mask |= 8;
                    continue block9;
                }
                case 'J': {
                    mask |= 0x10;
                    continue block9;
                }
                case 'C': {
                    mask |= 0x20;
                    continue block9;
                }
                case 'I': {
                    mask |= 0x40;
                    continue block9;
                }
                case 'S': {
                    mask |= 2;
                    continue block9;
                }
                default: {
                    mask = 0;
                }
            }
        }
        return mask;
    }
}

