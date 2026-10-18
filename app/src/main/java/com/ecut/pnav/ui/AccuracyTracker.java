package com.ecut.pnav.ui;

import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * 精度追踪器 — 记录历元计数 + 最近 30 个历元的 ENU 精度历史，
 * 供 PrecisionChartView 绘制趋势折线图。
 */
public class AccuracyTracker {

    private final List<float[]> history = new ArrayList<>();
    public final MutableLiveData<List<float[]>> historyLiveData = new MutableLiveData<>();
    public final MutableLiveData<Integer> epochCount = new MutableLiveData<>(0);
    private int counter = 0;

    /** 每次解算成功后调用，e/n/u 单位统一为米 */
    public void record(double e, double n, double u) {
        epochCount.postValue(++counter);
        history.add(new float[]{(float) e, (float) n, (float) u});
        if (history.size() > 30) {
            history.remove(0);
        }
        // 传递副本以避免 RecyclerView/Chart 持有相同引用
        historyLiveData.postValue(new ArrayList<>(history));
    }
}
