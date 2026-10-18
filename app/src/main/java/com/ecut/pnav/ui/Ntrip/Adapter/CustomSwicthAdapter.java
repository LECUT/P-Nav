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
package com.ecut.pnav.ui.Ntrip.Adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.ecut.pnav.R;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.Ntrip.NtripClient;
import com.ecut.pnav.ui.Ntrip.NtripViewModel;
import com.ecut.pnav.ui.status.HomeViewModel;

import java.io.Serializable;
import java.util.List;

public class CustomSwicthAdapter extends BaseAdapter{
    private final Context context;
    private final String[] items;
    private NtripViewModel ntripViewModel; // 添加 ViewModelStoreOwner
    Intent serviceIntent;

    public CustomSwicthAdapter(Context context, String[] items, NtripViewModel viewModelStoreOwner) {
        this.context = context;
        this.items = items;
        this.ntripViewModel = viewModelStoreOwner;
    }

    @Override
    public int getCount() {
        return items.length;
    }

    @Override
    public Object getItem(int position) {
        return items[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_switch, parent, false);
        }


        TextView textView = convertView.findViewById(R.id.textView);
        SwitchCompat switchButton = convertView.findViewById(R.id.switchButton);
        ntripViewModel.getSwitchState().observeForever(state -> {
            switchButton.setChecked(state);
        });

        textView.setText(items[position]);
        switchButton.setVisibility(position == 0 ? View.VISIBLE : View.GONE); // 只在"opt1"显示开关按钮

        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // SwitchButton 被开启
                Log.d("SwitchButton", "Switch is ON");
                List<NtripClient> mData = ntripViewModel.mData; // 获取 mData
                double[] gga_message = ntripViewModel.getGgaMessage(); // 获取 gga_message

                // 若未手动设置坐标，自动从手机 GNSS 定位填入
                if ((gga_message[0] == 0 && gga_message[1] == 0)
                        && (HomeViewModel.latestGnssLatLon[0] != 0 || HomeViewModel.latestGnssLatLon[1] != 0)) {
                    gga_message[0] = HomeViewModel.latestGnssLatLon[0];
                    gga_message[1] = HomeViewModel.latestGnssLatLon[1];
                    ntripViewModel.saveGgaMessage();
                }

                serviceIntent = new Intent(context, MyService.class);
                serviceIntent.putExtra("NtripDataList", (Serializable) mData); // 传递 mData
                serviceIntent.putExtra("GgaMessage", gga_message); // 传递 gga_message
                context.startService(serviceIntent);
            }else {
                // SwitchButton 被关闭
                Log.d("SwitchButton", "Switch is OFF");
                // 停止 Service
                context.stopService(serviceIntent);
//                stoprtcmReceiver();
//                menuItem.setEnabled(false);
                // 执行关闭的逻辑
            }
        });
        return convertView;
    }
}

