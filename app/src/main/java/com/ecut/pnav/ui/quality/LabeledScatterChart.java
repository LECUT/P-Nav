package com.ecut.pnav.ui.quality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.renderer.ScatterChartRenderer;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * 带卫星编号标签的散点图 — 绕过 MPAndroidChart ValueFormatter 的兼容性问题，
 * 在 onDraw 后直接用 Canvas 绘制每个点的标签文字。
 * 注入空实现 drawValues 的 Renderer，避免库默认值绘制在数据动态变化时触发
 * NegativeArraySizeException。
 */
public class LabeledScatterChart extends ScatterChart {

    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mDensity;

    public LabeledScatterChart(Context context) { super(context); initLabels(); }
    public LabeledScatterChart(Context context, AttributeSet attrs) { super(context, attrs); initLabels(); }
    public LabeledScatterChart(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); initLabels(); }

    private void initLabels() {
        mDensity = getResources().getDisplayMetrics().density;
        mLabelPaint.setTextSize(10f * mDensity);
        mLabelPaint.setFakeBoldText(true);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        // 库默认值绘制在数据变化时（Transformer.generateTransformedValuesScatter）
        // 会触发 NegativeArraySizeException，故注入空实现 drawValues 的 Renderer，
        // 值标签统一由 drawLabels 自己绘制
        setRenderer(new NoValueRenderer(this, getAnimator(), getViewPortHandler()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawLabels(canvas);
    }

    private void drawLabels(Canvas canvas) {
        if (canvas == null || getData() == null) return;
        for (IScatterDataSet ds : getData().getDataSets()) {
            mLabelPaint.setColor(ds.getColor());
            float offsetY = -ds.getScatterShapeSize() / 2f - 6f * mDensity;

            for (int i = 0; i < ds.getEntryCount(); i++) {
                Entry e = ds.getEntryForIndex(i);
                if (e == null) continue;
                Object tag = e.getData();
                if (!(tag instanceof String)) continue;
                float[] pt = getPixelForEntry(e);
                if (pt == null || !Float.isFinite(pt[0]) || !Float.isFinite(pt[1])) continue;
                canvas.drawText((String) tag, pt[0], pt[1] + offsetY, mLabelPaint);
            }
        }
    }

    private float[] getPixelForEntry(Entry e) {
        float[] pts = new float[]{e.getX(), e.getY()};
        getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(pts);
        return pts;
    }

    /** 空实现库的值绘制，标签由 drawLabels 自己画 */
    private static class NoValueRenderer extends ScatterChartRenderer {
        NoValueRenderer(ScatterChart chart, ChartAnimator animator, ViewPortHandler vph) {
            super(chart, animator, vph);
        }

        @Override
        public void drawValues(Canvas c) {
            // no-op
        }
    }
}
