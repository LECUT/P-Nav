package com.ecut.pnav.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/**
 * 精度趋势折线图 — 手绘三条 ENU 线，最近 N 个历元，无第三方图表库依赖。
 */
public class PrecisionChartView extends View {

    private static final int LINE_E = 0xFF007AFF; // 东 — 蓝色
    private static final int LINE_N = 0xFF34C759; // 北 — 绿色
    private static final int LINE_U = 0xFFFF9500; // 天 — 橙色
    private static final int GRID_COLOR = 0x22000000;
    private static final int TEXT_COLOR = 0x99000000;
    private static final float TEXT_SIZE = 22f;    // px ≈ 8sp on xxhdpi
    private static final float STROKE = 3f;
    private static final int MAX_POINTS = 30;

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLegendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<float[]> mHistory;
    private float mMaxVal = 1.0f;
    private float mMinVal = 0f;

    // 图例文本
    private static final String[] LEGENDS = {"E(m)", "N(m)", "U(m)"};
    private static final int[] LEGEND_COLORS = {LINE_E, LINE_N, LINE_U};

    public PrecisionChartView(Context context) {
        super(context);
        init();
    }

    public PrecisionChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(STROKE);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);

        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1f);
        mGridPaint.setColor(GRID_COLOR);
        mGridPaint.setPathEffect(new DashPathEffect(new float[]{6f, 4f}, 0));

        mTextPaint.setColor(TEXT_COLOR);
        mTextPaint.setTextSize(TEXT_SIZE);
        mTextPaint.setTextAlign(Paint.Align.RIGHT);

        mLegendPaint.setColor(TEXT_COLOR);
        mLegendPaint.setTextSize(TEXT_SIZE);
        mLegendPaint.setTextAlign(Paint.Align.LEFT);
        mLegendPaint.setAntiAlias(true);
    }

    public void setData(List<float[]> history) {
        mHistory = history;
        if (history == null || history.isEmpty()) {
            mMaxVal = 1.0f;
            mMinVal = 0f;
        } else {
            mMaxVal = 0.1f;
            for (float[] p : history) {
                for (int i = 0; i < 3; i++) {
                    if (p[i] > mMaxVal) mMaxVal = p[i];
                }
            }
            mMaxVal = niceCeil(mMaxVal);
            mMinVal = 0f;
        }
        invalidate();
    }

    /** 上取整到合理刻度 */
    private static float niceCeil(float v) {
        if (v <= 0.2f) return 0.2f;
        if (v <= 0.5f) return 0.5f;
        if (v <= 1.0f) return 1.0f;
        if (v <= 2.0f) return 2.0f;
        if (v <= 5.0f) return 5.0f;
        return (float) Math.ceil(v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padLeft = 40f;
        float padTop = 32f;
        float padRight = 12f;
        float padBottom = 8f;
        float chartW = w - padLeft - padRight;
        float chartH = h - padTop - padBottom;

        // ── 图例 ──
        float legX = padLeft;
        for (int i = 0; i < 3; i++) {
            mLegendPaint.setColor(LEGEND_COLORS[i]);
            mLegendPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(LEGENDS[i], legX, padTop - 6f, mLegendPaint);
            legX += mLegendPaint.measureText(LEGENDS[i]) + 28f;
        }

        // ── 网格 + Y 刻度 ──
        mTextPaint.setTextAlign(Paint.Align.RIGHT);
        float range = mMaxVal - mMinVal;
        if (range <= 0) range = 1f;
        int nTicks = 4;
        for (int i = 0; i <= nTicks; i++) {
            float val = mMinVal + range * i / nTicks;
            float y = padTop + chartH * (1f - (val - mMinVal) / range);
            // 网格线
            canvas.drawLine(padLeft, y, w - padRight, y, mGridPaint);
            // Y 标签
            canvas.drawText(String.format("%.1f", val), padLeft - 4f, y + 5f, mTextPaint);
        }

        if (mHistory == null || mHistory.size() < 2) return;

        // ── 裁剪区域 ──
        canvas.save();
        canvas.clipRect(padLeft, padTop, w - padRight, h - padBottom);

        // 三条线
        for (int series = 0; series < 3; series++) {
            mLinePaint.setColor(series == 0 ? LINE_E : series == 1 ? LINE_N : LINE_U);
            Path path = new Path();
            boolean first = true;
            int count = mHistory.size();
            for (int j = 0; j < count; j++) {
                float x = padLeft + chartW * j / (MAX_POINTS - 1);
                float val = mHistory.get(j)[series];
                float y = padTop + chartH * (1f - (val - mMinVal) / range);
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, mLinePaint);
        }

        canvas.restore();
    }
}
