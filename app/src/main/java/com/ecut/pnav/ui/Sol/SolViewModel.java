package com.ecut.pnav.ui.Sol;

import static com.ecut.pnav.ui.measure_util.prcopt_default;

import android.content.SharedPreferences;

import androidx.lifecycle.ViewModel;

import com.rtklib.bean.prcopt_t;

public class SolViewModel extends ViewModel {
    /* 动态生成的选项（保持在这里，数值不依赖语言） */
    public static String[] azel = new String[19];   // 0° ~ 90°, 每5°一档
    public static String[] cn0 = new String[10];    // 0 ~ 45 dBHz, 每5一档

    public static prcopt_t prcopt = prcopt_default;
    SharedPreferences solSettingPrefer;

    public SolViewModel() {
        for (int i = 0; i < azel.length; i++) {
            azel[i] = i * 5 + "°";
        }
        for (int i = 0; i < cn0.length; i++) {
            cn0[i] = i * 5 + " dBHz";
        }
    }
}
