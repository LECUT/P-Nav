package com.ecut.pnav.ui.status;

import android.content.Context;
import android.graphics.Color;
import android.location.GnssStatus;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.ecut.pnav.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * C/N0 柱状图视图 — 按信噪比降序排列，柱颜色对应星座
 */
public class GnssCn0BarView extends RelativeLayout {

    private BarChart barChart;

    public GnssCn0BarView(Context context) { super(context); init(context); }
    public GnssCn0BarView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_cn0_bar_chart, this, true);
        barChart = findViewById(R.id.cn0_bar_chart);

        barChart.setDrawGridBackground(false);
        barChart.setTouchEnabled(false);
        barChart.setDragEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setPinchZoom(false);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setFitBars(true);
        barChart.setExtraOffsets(4, 4, 4, 4);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(0xFF8E8E93);
        xAxis.setTextSize(9f);

        barChart.getAxisLeft().setTextColor(0xFF8E8E93);
        barChart.getAxisLeft().setTextSize(9f);
        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setGridColor(0x1A000000);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
    }

    /**
     * 更新 C/N0 柱状图数据
     */
    public void updateCn0Data(List<Cn0Info> data) {
        if (barChart == null || data == null || data.isEmpty()) {
            if (barChart != null) barChart.clear();
            return;
        }

        // 按 C/N0 降序排列
        List<Cn0Info> sorted = new ArrayList<>(data);
        Collections.sort(sorted, (a, b) -> Float.compare(b.cn0, a.cn0));

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            Cn0Info info = sorted.get(i);
            entries.add(new BarEntry(i, info.cn0));
            colors.add(constellationColor(info.constellationType));
            labels.add(constellationPrefix(info.constellationType) + info.svid);
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);
        barChart.setData(barData);

        barChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < labels.size()) ? labels.get(idx) : "";
            }
        });

        barChart.invalidate();
    }

    private static int constellationColor(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS:       return Color.rgb(0x00, 0x7A, 0xFF);
            case GnssStatus.CONSTELLATION_SBAS:       return Color.rgb(0xFF, 0x95, 0x00);
            case GnssStatus.CONSTELLATION_GLONASS:    return Color.rgb(0xAF, 0x52, 0xDE);
            case GnssStatus.CONSTELLATION_QZSS:       return Color.rgb(0x00, 0xBF, 0xA5);
            case GnssStatus.CONSTELLATION_BEIDOU:     return Color.rgb(0xFF, 0x3B, 0x30);
            case GnssStatus.CONSTELLATION_GALILEO:    return Color.rgb(0x34, 0xC7, 0x59);
            case GnssStatus.CONSTELLATION_IRNSS:      return Color.rgb(0xFF, 0x6D, 0x00);
            default:                                  return Color.parseColor("#8E8E93");
        }
    }

    private static String constellationPrefix(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS:       return "G";
            case GnssStatus.CONSTELLATION_SBAS:       return "S";
            case GnssStatus.CONSTELLATION_GLONASS:    return "R";
            case GnssStatus.CONSTELLATION_QZSS:       return "J";
            case GnssStatus.CONSTELLATION_BEIDOU:     return "C";
            case GnssStatus.CONSTELLATION_GALILEO:    return "E";
            case GnssStatus.CONSTELLATION_IRNSS:      return "I";
            default:                                  return "?";
        }
    }
}
