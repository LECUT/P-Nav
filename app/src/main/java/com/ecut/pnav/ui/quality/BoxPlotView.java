package com.ecut.pnav.ui.quality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 信号强度分布 — 紧凑箱线图。
 * 每行：须线｜范围条(min→max)｜IQR条(Q1→Q3)｜中位线。
 * 右侧：min / med / max 三列。
 */
public class BoxPlotView extends View {

    private final Paint whiskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iqrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint medStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lblPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hdrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<BoxData> items = new ArrayList<>();
    private boolean hasData = false;

    public static class BoxData {
        public final String label; public final int color; public final float[] values;
        public BoxData(String l, int c, float[] v) { label = l; color = c; values = v; }
    }

    public BoxPlotView(Context ctx) { super(ctx); init(); }
    public BoxPlotView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public BoxPlotView(Context ctx, AttributeSet a, int ds) { super(ctx, a, ds); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        whiskPaint.setStyle(Paint.Style.STROKE);
        whiskPaint.setStrokeWidth(0.8f * d);
        whiskPaint.setStrokeCap(Paint.Cap.ROUND);

        rangePaint.setStyle(Paint.Style.FILL);
        iqrPaint.setStyle(Paint.Style.FILL);

        medStroke.setStyle(Paint.Style.STROKE);
        medStroke.setColor(Color.WHITE);
        medStroke.setStrokeWidth(1.5f * d);
        medStroke.setStrokeCap(Paint.Cap.ROUND);

        lblPaint.setTextSize(10f * d);
        lblPaint.setTextAlign(Paint.Align.RIGHT);
        lblPaint.setFakeBoldText(true);

        valPaint.setTextSize(9f * d);
        valPaint.setTextAlign(Paint.Align.RIGHT);
        valPaint.setFakeBoldText(true);

        hdrPaint.setTextSize(8f * d);
        hdrPaint.setColor(0xFF6B6B73);
        hdrPaint.setTextAlign(Paint.Align.RIGHT);

        refPaint.setColor(0x28FF6B6B);
        refPaint.setStrokeWidth(0.8f * d);
        refPaint.setPathEffect(new DashPathEffect(new float[]{4f * d, 3f * d}, 0));

        emptyPaint.setTextSize(11f * d);
        emptyPaint.setColor(0xFF6B7280);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<BoxData> data) {
        items.clear(); hasData = false;
        if (data != null) {
            for (BoxData b : data) {
                if (b.values != null && b.values.length >= 3) { items.add(b); hasData = true; }
            }
        }
        invalidate();
    }

    private float[] range() {
        float lo = Float.MAX_VALUE, hi = Float.MIN_VALUE;
        for (BoxData b : items)
            for (float v : b.values) { if (v < lo) lo = v; if (v > hi) hi = v; }
        if (lo == Float.MAX_VALUE) return new float[]{20, 55};
        lo = ((int)(lo / 5)) * 5f;
        hi = ((int)(hi / 5) + 1) * 5f;
        if (hi - lo < 20) hi = lo + 20;
        return new float[]{lo, hi};
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        float[] R = range();
        float rLo = R[0], rHi = R[1], rSpan = rHi - rLo;

        // 表头区 + 绘图区
        float hdrH = 12f * d;
        float bodyH = h - hdrH;

        float lPad = 4f * d, lblW = 18f * d;
        float rPad = 6f * d;
        float pL = lPad + lblW + 6f * d;
        float pR = w - rPad;
        float pW = pR - pL;

        if (!hasData) {
            c.drawText("—", w / 2f, h / 2f, emptyPaint);
            return;
        }

        // 右侧表头
        float col1 = pR - 60f * d;
        float col2 = pR - 30f * d;
        float col3 = pR - 6f * d;
        c.drawText(getResources().getString(com.ecut.pnav.R.string.box_min), col1, hdrH - 3f * d, hdrPaint);
        c.drawText(getResources().getString(com.ecut.pnav.R.string.box_median), col2, hdrH - 3f * d, hdrPaint);
        c.drawText(getResources().getString(com.ecut.pnav.R.string.box_max), col3, hdrH - 3f * d, hdrPaint);

        int n = items.size();
        float rowH = bodyH / n;
        float rangeH = rowH * 0.28f;
        float iqrH  = rowH * 0.18f;

        for (int i = 0; i < n; i++) {
            BoxData bd = items.get(i);
            float[] v = bd.values.clone(); Arrays.sort(v);

            float dMin = v[0], dMax = v[v.length - 1];
            float q1 = perc(v, 25), q3 = perc(v, 75), med = perc(v, 50);

            float cy = hdrH + rowH * i + rowH / 2f;

            float xMin = pL + ((dMin - rLo) / rSpan) * pW;
            float xMax = pL + ((dMax - rLo) / rSpan) * pW;
            float xQ1  = pL + ((q1   - rLo) / rSpan) * pW;
            float xQ3  = pL + ((q3   - rLo) / rSpan) * pW;
            float xMed = pL + ((med  - rLo) / rSpan) * pW;

            // 30dB 参考线
            if (30f > rLo && 30f < rHi) {
                float rx = pL + ((30f - rLo) / rSpan) * pW;
                c.drawLine(rx, hdrH, rx, h, refPaint);
            }

            // 须线
            whiskPaint.setColor(bd.color);
            whiskPaint.setAlpha(140);
            c.drawLine(xMin, cy, xQ1, cy, whiskPaint);
            c.drawLine(xQ3, cy, xMax, cy, whiskPaint);
            float cap = 2f * d;
            c.drawLine(xMin, cy - cap, xMin, cy + cap, whiskPaint);
            c.drawLine(xMax, cy - cap, xMax, cy + cap, whiskPaint);

            // 范围条 min→max (半透明)
            int rc = (bd.color & 0x00FFFFFF) | 0x28000000;
            rangePaint.setColor(rc);
            c.drawRect(new RectF(xMin, cy - rangeH / 2f, xMax, cy + rangeH / 2f), rangePaint);

            // IQR 条 Q1→Q3 (实色)
            iqrPaint.setColor(bd.color);
            c.drawRect(new RectF(xQ1, cy - iqrH / 2f, xQ3, cy + iqrH / 2f), iqrPaint);

            // 中位竖线 (白色)
            c.drawLine(xMed, cy - iqrH / 2f - 1.5f * d, xMed, cy + iqrH / 2f + 1.5f * d, medStroke);

            // 星座标签
            lblPaint.setColor(bd.color);
            c.drawText(bd.label, lPad + lblW, cy + 3.5f * d, lblPaint);

            // 数值
            valPaint.setColor(0xFFC8C8D0);
            c.drawText(String.format("%.0f", dMin), col1, cy + 3.5f * d, valPaint);
            valPaint.setColor(bd.color);
            c.drawText(String.format("%.0f", med),  col2, cy + 3.5f * d, valPaint);
            valPaint.setColor(0xFFC8C8D0);
            c.drawText(String.format("%.0f", dMax), col3, cy + 3.5f * d, valPaint);
        }
    }

    private static float perc(float[] s, double p) {
        double pos = p / 100.0 * (s.length - 1);
        int lo = (int)pos, hi = (int)Math.ceil(pos);
        return lo == hi ? s[lo] : (float)(s[lo] * (hi - pos) + s[hi] * (pos - lo));
    }
}
