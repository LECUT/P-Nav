package com.ecut.pnav.ui;

import androidx.lifecycle.MutableLiveData;

/**
 * 解算指标共享通道 — 各模式 ViewModel 产出，PositioningFragment 底部栏消费。
 * 用静态 MutableLiveData 避免在子 Fragment 间传 ViewModel 引用。
 */
public class SolutionMetrics {
    public static final MutableLiveData<Entry> liveData = new MutableLiveData<>();

    public static void post(String mode, double hAcc, double vAcc, int satCount, double lat, double lon) {
        liveData.postValue(new Entry(mode, hAcc, vAcc, satCount, lat, lon));
    }

    public static class Entry {
        public final String mode;
        public final double hAcc;
        public final double vAcc;
        public final int satCount;
        public final double lat;
        public final double lon;

        Entry(String mode, double hAcc, double vAcc, int satCount, double lat, double lon) {
            this.mode = mode;
            this.hAcc = hAcc;
            this.vAcc = vAcc;
            this.satCount = satCount;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
