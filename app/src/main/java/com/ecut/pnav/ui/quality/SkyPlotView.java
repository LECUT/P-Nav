package com.ecut.pnav.ui.quality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 极坐标卫星信号星空图 — 纯 Canvas 自绘
 * 圆心=天顶，外圈=地平线，同心圆 0°/30°/60°/90°
 * 统一圆形，C/N0 颜色渐变表示信号强度，绿圈=参与定位
 * 轨迹线 + 方向箭头显示卫星运动趋势
 */
public class SkyPlotView extends View {

    private static final float MIN_CN0 = 10f;
    private static final float MAX_CN0 = 45f;
    private static final int SAT_RADIUS_DP = 6;
    private static final int MAX_TRAIL_LENGTH = 120;

    private final Paint mBgFillPaint;
    private final Paint mHorizonStrokePaint;
    private final Paint mGridPaint;
    private final Paint mNorthPaint;
    private final Paint mNorthFillPaint;
    private final Paint mPrnPaint;
    private final Paint mSatStrokeUsed;
    private final Paint mSatStrokeUnused;
    private final Paint mTrailPaint;
    private final Paint mTrailUsedPaint;
    private final Paint mArrowPaint;

    private final float[] mCn0Thresholds = {MIN_CN0, 21.67f, 33.3f, MAX_CN0};
    private final int[] mCn0Colors = {
            Color.rgb(0x8E, 0x8E, 0x93), // 灰 — 弱
            Color.rgb(0xFF, 0x3B, 0x30), // 红
            Color.rgb(0xFF, 0x95, 0x00), // 橙
            Color.rgb(0x34, 0xC7, 0x59)  // 绿 — 强
    };

    private final int mSatRadiusPx;
    private List<SkySatInfo> mSatellites = new java.util.ArrayList<>();
    private SparseArray<ArrayDeque<float[]>> mTrails = new SparseArray<>();

    public SkyPlotView(Context context) {
        super(context);
        mSatRadiusPx = dp(SAT_RADIUS_DP);
        mBgFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgFillPaint.setStyle(Paint.Style.FILL);

        mHorizonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHorizonStrokePaint.setStyle(Paint.Style.STROKE);
        mHorizonStrokePaint.setColor(0xFFB0B0B0);
        mHorizonStrokePaint.setStrokeWidth(2f);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setColor(0xFFD8D8D8);
        mGridPaint.setStrokeWidth(0.8f);

        mNorthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthPaint.setStyle(Paint.Style.STROKE);
        mNorthPaint.setColor(0xFF666666);
        mNorthPaint.setStrokeWidth(2.5f);

        mNorthFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthFillPaint.setStyle(Paint.Style.FILL);
        mNorthFillPaint.setColor(0xFF666666);

        mPrnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPrnPaint.setColor(0xFF444444);
        mPrnPaint.setTextSize(sp(8f));
        mPrnPaint.setTextAlign(Paint.Align.CENTER);

        mSatStrokeUsed = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUsed.setStyle(Paint.Style.STROKE);
        mSatStrokeUsed.setColor(0xFF4CAF50);
        mSatStrokeUsed.setStrokeWidth(dp(2.5f));

        mSatStrokeUnused = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUnused.setStyle(Paint.Style.STROKE);
        mSatStrokeUnused.setColor(0x33000000);
        mSatStrokeUnused.setStrokeWidth(dp(1f));

        mTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrailPaint.setStyle(Paint.Style.STROKE);
        mTrailPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrailPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrailPaint.setColor(0x60000000);
        mTrailPaint.setStrokeWidth(dp(1f));

        mTrailUsedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrailUsedPaint.setStyle(Paint.Style.STROKE);
        mTrailUsedPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrailUsedPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrailUsedPaint.setColor(0x90000000);
        mTrailUsedPaint.setStrokeWidth(dp(1.5f));

        mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mArrowPaint.setStyle(Paint.Style.FILL);
        mArrowPaint.setColor(0xAA333333);
    }

    public SkyPlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSatRadiusPx = dp(SAT_RADIUS_DP);
        mBgFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgFillPaint.setStyle(Paint.Style.FILL);

        mHorizonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHorizonStrokePaint.setStyle(Paint.Style.STROKE);
        mHorizonStrokePaint.setColor(0xFFB0B0B0);
        mHorizonStrokePaint.setStrokeWidth(2f);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setColor(0xFFD8D8D8);
        mGridPaint.setStrokeWidth(0.8f);

        mNorthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthPaint.setStyle(Paint.Style.STROKE);
        mNorthPaint.setColor(0xFF666666);
        mNorthPaint.setStrokeWidth(2.5f);

        mNorthFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthFillPaint.setStyle(Paint.Style.FILL);
        mNorthFillPaint.setColor(0xFF666666);

        mPrnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPrnPaint.setColor(0xFF444444);
        mPrnPaint.setTextSize(sp(8f));
        mPrnPaint.setTextAlign(Paint.Align.CENTER);

        mSatStrokeUsed = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUsed.setStyle(Paint.Style.STROKE);
        mSatStrokeUsed.setColor(0xFF4CAF50);
        mSatStrokeUsed.setStrokeWidth(dp(2.5f));

        mSatStrokeUnused = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUnused.setStyle(Paint.Style.STROKE);
        mSatStrokeUnused.setColor(0x33000000);
        mSatStrokeUnused.setStrokeWidth(dp(1f));

        mTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrailPaint.setStyle(Paint.Style.STROKE);
        mTrailPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrailPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrailPaint.setColor(0x60000000);
        mTrailPaint.setStrokeWidth(dp(1f));

        mTrailUsedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrailUsedPaint.setStyle(Paint.Style.STROKE);
        mTrailUsedPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrailUsedPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrailUsedPaint.setColor(0x90000000);
        mTrailUsedPaint.setStrokeWidth(dp(1.5f));

        mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mArrowPaint.setStyle(Paint.Style.FILL);
        mArrowPaint.setColor(0xAA333333);
    }

    public void setSatellites(List<SkySatInfo> data) {
        mSatellites = data != null ? data : new java.util.ArrayList<>();

        Set<Integer> currentKeys = new HashSet<>();
        for (SkySatInfo s : mSatellites) {
            if (s.elevation < 0 || s.elevation > 90) continue;
            int key = (s.constellationType << 8) | s.svid;
            currentKeys.add(key);
            ArrayDeque<float[]> trail = mTrails.get(key);
            if (trail == null) {
                trail = new ArrayDeque<>();
                mTrails.put(key, trail);
            }
            trail.addLast(new float[]{s.azimuth, s.elevation});
            if (trail.size() > MAX_TRAIL_LENGTH) trail.removeFirst();
        }
        for (int i = mTrails.size() - 1; i >= 0; i--) {
            if (!currentKeys.contains(mTrails.keyAt(i))) {
                mTrails.removeAt(i);
            }
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int size = resolveSize(w, widthMeasureSpec);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = size / 2f - mSatRadiusPx - dp(6f);

        // 径向渐变背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{0xFFF0F4FA, 0xFFF8F9FB, Color.WHITE},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, bgPaint);

        // 网格：十字线 + 同心圆
        canvas.drawLine(cx - radius, cy, cx + radius, cy, mGridPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, mGridPaint);
        canvas.drawCircle(cx, cy, elevToRadius(radius, 60f), mGridPaint);
        canvas.drawCircle(cx, cy, elevToRadius(radius, 30f), mGridPaint);

        // 高度角标签
        Paint elevLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        elevLabelPaint.setColor(0xFF9E9E9E);
        elevLabelPaint.setTextSize(sp(9f));
        elevLabelPaint.setTextAlign(Paint.Align.LEFT);
        float r60 = elevToRadius(radius, 60f);
        float r30 = elevToRadius(radius, 30f);
        canvas.drawText("60°", cx + dp(3f), cy - r60 + dp(3f), elevLabelPaint);
        canvas.drawText("30°", cx + dp(3f), cy - r30 + dp(3f), elevLabelPaint);

        // 外圈
        canvas.drawCircle(cx, cy, radius, mHorizonStrokePaint);

        // 方向标注 N/E/S/W
        Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dirPaint.setColor(0xFF616161);
        dirPaint.setTextSize(sp(11f));
        dirPaint.setTextAlign(Paint.Align.CENTER);
        dirPaint.setFakeBoldText(true);
        float labelR = radius + dp(12f);
        canvas.drawText("N", cx, cy - labelR + dp(4f), dirPaint);
        canvas.drawText("S", cx, cy + labelR + dp(4f), dirPaint);
        canvas.drawText("E", cx + labelR, cy + dp(5f), dirPaint);
        canvas.drawText("W", cx - labelR, cy + dp(5f), dirPaint);

        // N 方向箭头
        drawNorthArrow(canvas, cx, cy, radius);

        // 轨迹线 + 方向箭头
        drawTrails(canvas, cx, cy, radius);

        // 卫星
        for (SkySatInfo s : mSatellites) {
            if (s.elevation < 0 || s.elevation > 90) continue;
            drawSatellite(canvas, cx, cy, radius, s);
        }
    }

    private float elevToRadius(float maxR, float elev) {
        return maxR * (1f - elev / 90f);
    }

    private void drawNorthArrow(Canvas c, float cx, float cy, float radius) {
        float tipY = cy - radius;
        float h = radius * 0.06f;
        float halfW = radius * 0.04f;
        Path path = new Path();
        path.moveTo(cx, tipY);
        path.lineTo(cx - halfW, tipY + h);
        path.lineTo(cx + halfW, tipY + h);
        path.close();
        c.drawPath(path, mNorthPaint);
        c.drawPath(path, mNorthFillPaint);
    }

    private void drawSatellite(Canvas c, float cx, float cy, float radius, SkySatInfo s) {
        float[] xy = projectSatellite(cx, cy, radius, s.azimuth, s.elevation);
        if (xy == null) return;
        float x = xy[0], y = xy[1];

        // 填充：C/N0 颜色
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(s.cn0 > 0 ? getCn0Color(s.cn0) : Color.LTGRAY);

        // 统一圆形
        c.drawCircle(x, y, mSatRadiusPx, fill);

        // 边框：参与定位 = 绿圈，否则浅灰
        Paint stroke = s.usedInFix ? mSatStrokeUsed : mSatStrokeUnused;
        c.drawCircle(x, y, mSatRadiusPx, stroke);

        // PRN 标签
        c.drawText(String.valueOf(s.svid), x, y + mSatRadiusPx + sp(8f) * 1.2f, mPrnPaint);
    }

    private int getCn0Color(float cn0) {
        int n = mCn0Thresholds.length;
        if (cn0 <= mCn0Thresholds[0]) return mCn0Colors[0];
        if (cn0 >= mCn0Thresholds[n - 1]) return mCn0Colors[n - 1];
        for (int i = 0; i < n - 1; i++) {
            if (cn0 >= mCn0Thresholds[i] && cn0 <= mCn0Thresholds[i + 1]) {
                float frac = (cn0 - mCn0Thresholds[i]) / (mCn0Thresholds[i + 1] - mCn0Thresholds[i]);
                int c1 = mCn0Colors[i], c2 = mCn0Colors[i + 1];
                int r = (int) (Color.red(c2) * frac + Color.red(c1) * (1 - frac));
                int g = (int) (Color.green(c2) * frac + Color.green(c1) * (1 - frac));
                int b = (int) (Color.blue(c2) * frac + Color.blue(c1) * (1 - frac));
                return Color.rgb(r, g, b);
            }
        }
        return Color.MAGENTA;
    }

    /* ── 轨迹 + 方向箭头 ── */

    private void drawTrails(Canvas c, float cx, float cy, float radius) {
        if (mTrails.size() == 0) return;

        Set<Integer> usedKeys = new HashSet<>();
        for (SkySatInfo s : mSatellites) {
            if (s.usedInFix) usedKeys.add((s.constellationType << 8) | s.svid);
        }

        for (int i = 0; i < mTrails.size(); i++) {
            ArrayDeque<float[]> trail = mTrails.valueAt(i);
            if (trail.size() < 2) continue;

            int key = mTrails.keyAt(i);
            boolean used = usedKeys.contains(key);
            Paint paint = used ? mTrailUsedPaint : mTrailPaint;

            // 画轨迹线
            Path path = new Path();
            boolean first = true;
            float lastX = 0, lastY = 0, prevX = 0, prevY = 0;
            int ptIdx = 0;
            for (float[] pt : trail) {
                float[] xy = projectSatellite(cx, cy, radius, pt[0], pt[1]);
                if (xy == null) continue;
                if (first) { path.moveTo(xy[0], xy[1]); first = false; }
                else path.lineTo(xy[0], xy[1]);
                prevX = lastX; prevY = lastY;
                lastX = xy[0]; lastY = xy[1];
                ptIdx++;
            }
            c.drawPath(path, paint);

            // 在轨迹末端画方向箭头（最后两点方向）
            if (ptIdx >= 2 && (lastX != prevX || lastY != prevY)) {
                drawArrowHead(c, prevX, prevY, lastX, lastY, used);
            }
        }
    }

    private void drawArrowHead(Canvas c, float fromX, float fromY, float toX, float toY, boolean used) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 2f) return;
        float ux = dx / len, uy = dy / len;

        float arrowLen = dp(6f);
        float arrowAngle = 0.6f; // ~35°
        float tipX = toX + arrowLen * 0.4f * ux;
        float tipY = toY + arrowLen * 0.4f * uy;

        float cosA = (float) Math.cos(arrowAngle);
        float sinA = (float) Math.sin(arrowAngle);
        float lx1 = -ux * cosA + uy * sinA;
        float ly1 = -uy * cosA - ux * sinA;
        float lx2 = -ux * cosA - uy * sinA;
        float ly2 = -uy * cosA + ux * sinA;

        Path arrow = new Path();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(tipX + lx1 * arrowLen, tipY + ly1 * arrowLen);
        arrow.lineTo(tipX + lx2 * arrowLen, tipY + ly2 * arrowLen);
        arrow.close();

        mArrowPaint.setAlpha(used ? 200 : 120);
        c.drawPath(arrow, mArrowPaint);
    }

    private float[] projectSatellite(float cx, float cy, float radius, float azimuth, float elevation) {
        if (elevation < 0 || elevation > 90) return null;
        double azimRad = Math.toRadians(azimuth);
        double r = elevToRadius(radius, elevation);
        return new float[]{
                (float) (cx + r * Math.sin(azimRad)),
                (float) (cy - r * Math.cos(azimRad))
        };
    }

    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /* ── 卫星信息数据类 ── */

    public static class SkySatInfo {
        public final int constellationType;
        public final int svid;
        public final float elevation;
        public final float azimuth;
        public final float cn0;
        public final boolean usedInFix;
        public final boolean hasEphemeris;
        public final boolean hasAlmanac;

        public SkySatInfo(int constellationType, int svid, float elevation, float azimuth,
                          float cn0, boolean usedInFix,
                          boolean hasEphemeris, boolean hasAlmanac) {
            this.constellationType = constellationType;
            this.svid = svid;
            this.elevation = elevation;
            this.azimuth = azimuth;
            this.cn0 = cn0;
            this.usedInFix = usedInFix;
            this.hasEphemeris = hasEphemeris;
            this.hasAlmanac = hasAlmanac;
        }
    }
}
