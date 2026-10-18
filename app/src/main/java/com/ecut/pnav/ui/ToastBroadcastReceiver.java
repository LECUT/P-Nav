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
package com.ecut.pnav.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ToastBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.ecut.pnav.SHOW_TOAST".equals(intent.getAction())) {
            String message = intent.getStringExtra("toastMessage");
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}
