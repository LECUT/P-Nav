/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.readobsnav;

import com.rtklib.util.util2;

public class exPath {
    public static int ex_path(String path, String[] paths, int nmax) {
        int n = 0;
        char[] tmp = new char[1024];
        int p = 0;
        char[] dir = new char[1024];
        p = util2.strrchr(path, '\\');
        if (p != 0) {
            util2.strncpy(dir, path.toCharArray(), p);
        }
        return n;
    }
}

