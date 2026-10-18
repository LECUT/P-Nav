/*
 * Derived from the open-source myGNSS application.
 *
 *     Copyright (c) 2024, z1Yang-1, All rights reserved.
 *     Licensed under the BSD 2-Clause License.
 *     https://github.com/z1Yang-1/myGNSS
 *
 * Substantially modified and extended for P-Nav.
 * See THIRD-PARTY-NOTICES.md for details.
 */
package com.ecut.pnav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class BitmapUtils {
    public static Bitmap loadAndScaleBitmap(Context context, int resourceId, int width, int height) {
        // 加载原始位图
        Bitmap originalBitmap = BitmapFactory.decodeResource(context.getResources(), resourceId);

        // 缩放位图
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);

        // 回收原始位图以释放内存
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }

        return scaledBitmap;
    }
}
