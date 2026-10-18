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
package com.ecut.pnav.ui.status;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.ecut.pnav.R;

import java.util.ArrayList;
import java.util.List;

public class GnssSkyView extends View {
    private static final float MIN_CN0 = 10.0f;
    private static final float MAX_CN0 = 45.0f;
    private static final float PRN_TEXT_SCALE = 0.52f;

    private int mSatRadius;
    private float mHorizonMargin;
    private int mBgColor;
    private float[] mCn0Thresholds;
    private int[] mCn0Colors;

    private Paint mHorizonActiveFillPaint, mHorizonInactiveFillPaint, mHorizonStrokePaint,
            mGridStrokePaint,
            mNorthPaint, mNorthFillPaint, mPrnIdPaint, mNotInViewPaint;
    private Paint mSatelliteFillPaint;
    private Paint mSatStrokeUsed, mSatStrokeUnused;
    private Paint mElevLabelPaint, mAzimLabelPaint, mTickPaint;

    private Bitmap mFlagUs, mFlagCn, mFlagRu, mFlagEu, mFlagJp, mFlagIn;
    private final RectF mFlagDst = new RectF();

    private double mOrientation = 0;
    private boolean mStarted;
    private List<SatelliteInfo> satelliteInfoList = new ArrayList<>();
    private OnSatelliteClickListener mSatelliteClickListener;

    /** 卫星点击监听器 */
    public interface OnSatelliteClickListener {
        void onSatelliteClick(SatelliteInfo info, float screenX, float screenY);
    }

    public void setOnSatelliteClickListener(OnSatelliteClickListener listener) {
        this.mSatelliteClickListener = listener;
    }

    public GnssSkyView(Context context) { super(context); init(); }
    public GnssSkyView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        Context ctx = getContext();
        mSatRadius = dpToPx(9);
        // 圆盘外预留环带：容纳 30° 刻度、北向箭头与 N/E/S/W 标签，避免文字被画布裁切
        mHorizonMargin = dpToPx(24f);
        // 整块背景与所在卡片同色(浅色/深色随主题)，圆内与四周不再割裂
        mBgColor = ContextCompat.getColor(ctx, R.color.surfaceVariant);

        mHorizonActiveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHorizonActiveFillPaint.setColor(Color.WHITE);
        mHorizonActiveFillPaint.setStyle(Paint.Style.FILL);

        mHorizonInactiveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHorizonInactiveFillPaint.setColor(Color.LTGRAY);
        mHorizonInactiveFillPaint.setStyle(Paint.Style.FILL);

        mHorizonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHorizonStrokePaint.setColor(Color.parseColor("#757575"));
        mHorizonStrokePaint.setStyle(Paint.Style.STROKE);
        mHorizonStrokePaint.setStrokeWidth(dpToPx(1.5f));

        mGridStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridStrokePaint.setColor(Color.parseColor("#BDBDBD"));
        mGridStrokePaint.setStyle(Paint.Style.STROKE);

        mSatelliteFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatelliteFillPaint.setColor(Color.YELLOW);
        mSatelliteFillPaint.setStyle(Paint.Style.FILL);

        // 可用卫星 — 绿色边框
        mSatStrokeUsed = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUsed.setColor(Color.parseColor("#4CAF50"));
        mSatStrokeUsed.setStyle(Paint.Style.STROKE);
        mSatStrokeUsed.setStrokeWidth(dpToPx(1.5f));

        // 不可用卫星 — 红色边框
        mSatStrokeUnused = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSatStrokeUnused.setColor(Color.parseColor("#F44336"));
        mSatStrokeUnused.setStyle(Paint.Style.STROKE);
        mSatStrokeUnused.setStrokeWidth(dpToPx(1.5f));

        mCn0Thresholds = new float[]{MIN_CN0, 21.67f, 33.3f, MAX_CN0};
        mCn0Colors = new int[]{
                Color.parseColor("#888888"),  // 灰（低信号）
                Color.parseColor("#D50000"),  // 红
                Color.parseColor("#FFB300"),  // 黄
                Color.parseColor("#009D00")   // 绿
        };

        mNorthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthPaint.setColor(Color.BLACK);
        mNorthPaint.setStyle(Paint.Style.STROKE);
        mNorthPaint.setStrokeWidth(4.0f);

        mNorthFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNorthFillPaint.setColor(Color.GRAY);
        mNorthFillPaint.setStyle(Paint.Style.FILL);
        mNorthFillPaint.setStrokeWidth(4.0f);

        mPrnIdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPrnIdPaint.setColor(ContextCompat.getColor(ctx, R.color.text_primary));
        mPrnIdPaint.setStyle(Paint.Style.FILL);
        mPrnIdPaint.setTextSize(dpToPx(mSatRadius * PRN_TEXT_SCALE));
        mPrnIdPaint.setTextAlign(Paint.Align.CENTER);

        mNotInViewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNotInViewPaint.setColor(0x11000000);
        mNotInViewPaint.setStyle(Paint.Style.FILL);
        mNotInViewPaint.setStrokeWidth(4.0f);

        // 高度角标签(30°/60°)
        mElevLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mElevLabelPaint.setColor(Color.parseColor("#9E9E9E"));
        mElevLabelPaint.setTextSize(dpToPx(10));
        mElevLabelPaint.setTextAlign(Paint.Align.LEFT);

        // 方位角标签(N/E/S/W)
        mAzimLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAzimLabelPaint.setColor(ContextCompat.getColor(ctx, R.color.text_primary));
        mAzimLabelPaint.setTextSize(dpToPx(12));
        mAzimLabelPaint.setTextAlign(Paint.Align.CENTER);
        mAzimLabelPaint.setFakeBoldText(true);

        // 方位刻度线
        mTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTickPaint.setColor(ContextCompat.getColor(ctx, R.color.outlineVariant));
        mTickPaint.setStyle(Paint.Style.STROKE);
        mTickPaint.setStrokeWidth(dpToPx(1));

        // 加载国旗矢量图(缩放到适合圆形内部的大小)
        int flagSize = dpToPx(18);
        mFlagUs = decodeAndScale(ctx, R.drawable.flag_us, flagSize);
        mFlagCn = decodeAndScale(ctx, R.drawable.flag_cn, flagSize);
        mFlagRu = decodeAndScale(ctx, R.drawable.flag_ru, flagSize);
        mFlagEu = decodeAndScale(ctx, R.drawable.flag_eu, flagSize);
        mFlagJp = decodeAndScale(ctx, R.drawable.flag_jp, flagSize);
        mFlagIn = decodeAndScale(ctx, R.drawable.flag_in, flagSize);

        setFocusable(true);
    }

    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }
    private float dpToPx(float dp) { return dp * getResources().getDisplayMetrics().density; }

    public void setOrientation(double orientationDeg) {
        mOrientation = orientationDeg;
        invalidate();
    }

    public void setSatelliteInfo(List<SatelliteInfo> infoList) {
        this.satelliteInfoList = infoList;
        mStarted = true;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        drawHorizon(c, size);
        drawNorthIndicator(c, size);

        for (SatelliteInfo info : satelliteInfoList) {
            drawSatellite(c, size, info.elevation, info.azimuth, info.cn0,
                    info.prn, info.constellationType, info.usedInFix);
        }
    }

    private void drawHorizon(Canvas c, int s) {
        float cx = s / 2f, cy = cx;
        float maxR = s / 2f - mHorizonMargin;

        // 整块背景与卡片同色(surfaceVariant)——圆内与四周一致，不再渐变割裂
        c.drawColor(mBgColor);

        // 十字线(随方位角旋转)
        drawLine(c, cx - maxR, cy, cx + maxR, cy);
        drawLine(c, cx, cy - maxR, cx, cy + maxR);

        // 高度角网格圆
        float r60 = elevationToRadius(maxR, 60);
        float r30 = elevationToRadius(maxR, 30);
        float r0 = elevationToRadius(maxR, 0);
        c.drawCircle(cx, cy, r60, mGridStrokePaint);
        c.drawCircle(cx, cy, r30, mGridStrokePaint);
        c.drawCircle(cx, cy, r0, mGridStrokePaint);

        // 高度角数字标签
        drawElevLabel(c, cx, cy, r60, "60°");
        drawElevLabel(c, cx, cy, r30, "30°");

        // 方位角刻度(每30°) + N/E/S/W 标签
        drawAzimuthScale(c, cx, cy, r0);

        // 外圈边框
        c.drawCircle(cx, cy, maxR, mHorizonStrokePaint);
    }

    /** 沿北向轴线绘制高度角标签(随方位角旋转) */
    private void drawElevLabel(Canvas c, float cx, float cy, float r, String text) {
        double a = Math.toRadians(-mOrientation);
        float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        float x = cx + r * sin;
        float y = cy - r * cos;
        // 标签向右偏移3dp避免遮挡轴线
        float offX = dpToPx(3) * cos;
        float offY = dpToPx(3) * sin;
        c.drawText(text, x + offX, y - offY + dpToPx(4), mElevLabelPaint);
    }

    /** 在外圈外侧绘制30°刻度线和N/E/S/W标签 */
    private void drawAzimuthScale(Canvas c, float cx, float cy, float r0) {
        final int[] CARDINALS = {0, 90, 180, 270};
        final String[] CARD_NAMES = {"N", "E", "S", "W"};
        float longTick = dpToPx(8), shortTick = dpToPx(4);
        // 标签放在箭头尖外侧：箭头尖 = maxR+12dp, 标签中心离箭头尖再留6dp
        float labelGap = dpToPx(14);

        for (int az = 0; az < 360; az += 30) {
            double a = Math.toRadians(az - mOrientation);
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            float tickLen = (az % 90 == 0) ? longTick : shortTick;
            float x1 = cx + r0 * sin;
            float y1 = cy - r0 * cos;
            float x2 = cx + (r0 + tickLen) * sin;
            float y2 = cy - (r0 + tickLen) * cos;
            c.drawLine(x1, y1, x2, y2, az % 90 == 0 ? mHorizonStrokePaint : mTickPaint);
        }

        // 标签统一用 outerR(mHorizonMargin) 为基准，保证在箭头外侧
        float outerR = cx - mHorizonMargin;
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(CARDINALS[i] - mOrientation);
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            float lx = cx + (outerR + labelGap) * sin;
            float ly = cy - (outerR + labelGap) * cos;
            c.drawText(CARD_NAMES[i], lx, ly + dpToPx(5), mAzimLabelPaint);
        }
    }

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2) {
        double angle = Math.toRadians(-mOrientation);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float centerX = (x1 + x2) / 2f;
        float centerY = (y1 + y2) / 2f;
        x1 -= centerX;
        y1 = centerY - y1;
        x2 -= centerX;
        y2 = centerY - y2;

        float X1 = cos * x1 + sin * y1 + centerX;
        float Y1 = -(-sin * x1 + cos * y1) + centerY;
        float X2 = cos * x2 + sin * y2 + centerX;
        float Y2 = -(-sin * x2 + cos * y2) + centerY;

        c.drawLine(X1, Y1, X2, Y2, mGridStrokePaint);
    }

    private void drawNorthIndicator(Canvas c, int s) {
        float cx = s / 2f, cy = cx;
        float outerR = s / 2f - mHorizonMargin;
        // 箭头尖朝外(指北)，尺寸控制在视图边界内安全距离
        // 旋转到90°/270°时 tipR 不能超过 s/2(视图半径)
        float baseR = outerR + dpToPx(2);
        float tipR  = outerR + dpToPx(12);
        float halfW = dpToPx(5);

        Path path = new Path();
        path.moveTo(cx, cy - tipR);
        path.lineTo(cx + halfW, cy - baseR);
        path.lineTo(cx - halfW, cy - baseR);
        path.close();

        Matrix matrix = new Matrix();
        matrix.postRotate((float) -mOrientation, cx, cy);
        path.transform(matrix);

        c.drawPath(path, mNorthPaint);
        c.drawPath(path, mNorthFillPaint);
    }

    private void drawSatellite(Canvas c, int s, float elev, float azim, float cn0, int prn,
            int constellationType, boolean usedInFix) {
        final double PRN_X_SCALE = 1.0;
        final double PRN_Y_SCALE = 1.6;

        Paint fillPaint;
        if (cn0 == 0.0f) {
            fillPaint = mNotInViewPaint;
        } else {
            fillPaint = new Paint(mSatelliteFillPaint);
            fillPaint.setColor(getSatelliteColor(cn0));
        }

        // 绿色边框=可用, 红色边框=不可用
        Paint strokePaint = usedInFix ? mSatStrokeUsed : mSatStrokeUnused;

        float radius = elevationToRadius(s / 2f - mHorizonMargin, elev);
        float azimRotated = azim - (float) mOrientation;
        double angle = Math.toRadians(azimRotated);

        float cx = s / 2f + radius * (float) Math.sin(angle);
        float cy = s / 2f - radius * (float) Math.cos(angle);

        // 圆形底 + C/N0 颜色填充
        c.drawCircle(cx, cy, mSatRadius, fillPaint);

        // 国旗绘制在圆形内部(clip 裁剪)
        c.save();
        Path circleClip = new Path();
        circleClip.addCircle(cx, cy, mSatRadius * 0.92f, Path.Direction.CW);
        c.clipPath(circleClip);
        drawFlag(c, cx, cy, constellationType);
        c.restore();

        // 边框(绿色可用/红色不可用)
        c.drawCircle(cx, cy, mSatRadius, strokePaint);

        // PRN 编号
        c.drawText(String.valueOf(prn),
                cx - (int) (mSatRadius * PRN_X_SCALE),
                cy + (int) (mSatRadius * PRN_Y_SCALE),
                mPrnIdPaint);
    }

    private float elevationToRadius(float maxR, float elev) {
        return (maxR - mSatRadius) * (1.0f - elev / 90.0f);
    }

    private Bitmap decodeAndScale(Context ctx, int resId, int targetSize) {
        Bitmap orig = BitmapFactory.decodeResource(ctx.getResources(), resId);
        if (orig == null) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(orig, targetSize, targetSize, true);
        if (scaled != orig) orig.recycle();
        return scaled;
    }

    /** 在圆形内部绘制国旗图片。调用前已通过 clipPath 限制在圆内。 */
    private void drawFlag(Canvas c, float cx, float cy, int constellationType) {
        Bitmap flag = null;
        switch (constellationType) {
            case 1: flag = mFlagUs; break;   // GPS
            case 3: flag = mFlagRu; break;   // GLONASS
            case 5: flag = mFlagCn; break;   // 北斗
            case 6: flag = mFlagEu; break;   // Galileo
            case 4: flag = mFlagJp; break;   // QZSS
            case 7: flag = mFlagIn; break;   // IRNSS
            default: break;                  // SBAS — 无国旗
        }
        if (flag == null) return;

        float half = flag.getWidth() / 2f;
        mFlagDst.set(cx - half, cy - half, cx + half, cy + half);
        c.drawBitmap(flag, null, mFlagDst, null);
    }

    private int getSatelliteColor(float cn0) {
        float[] thresholds = mCn0Thresholds;
        int[] colors = mCn0Colors;

        if (cn0 <= thresholds[0]) return colors[0];
        if (cn0 >= thresholds[thresholds.length - 1]) return colors[thresholds.length - 1];

        for (int i = 0; i < thresholds.length - 1; i++) {
            float t = thresholds[i];
            float next = thresholds[i + 1];
            if (cn0 >= t && cn0 <= next) {
                int c1 = colors[i], c2 = colors[i + 1];
                float f = (cn0 - t) / (next - t);
                int r = (int) (Color.red(c2) * f + Color.red(c1) * (1 - f));
                int g = (int) (Color.green(c2) * f + Color.green(c1) * (1 - f));
                int b = (int) (Color.blue(c2) * f + Color.blue(c1) * (1 - f));
                return Color.rgb(r, g, b);
            }
        }
        return Color.MAGENTA;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN || mSatelliteClickListener == null) {
            return super.onTouchEvent(event);
        }
        float touchX = event.getX();
        float touchY = event.getY();
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) return super.onTouchEvent(event);

        float cx = size / 2f, cy = cx;
        float maxR = size / 2f - mHorizonMargin;
        // 命中检测距离阈值：卫星半径 + 一些容差
        float hitRadius = mSatRadius + dpToPx(6);

        SatelliteInfo closest = null;
        float closestDist = Float.MAX_VALUE;

        for (SatelliteInfo info : satelliteInfoList) {
            float radius = elevationToRadius(maxR, info.elevation);
            float azimRotated = info.azimuth - (float) mOrientation;
            double angle = Math.toRadians(azimRotated);
            float sx = cx + radius * (float) Math.sin(angle);
            float sy = cy - radius * (float) Math.cos(angle);
            float dist = (float) Math.hypot(touchX - sx, touchY - sy);
            if (dist <= hitRadius && dist < closestDist) {
                closest = info;
                closestDist = dist;
            }
        }

        if (closest != null) {
            // 计算对应屏幕坐标
            float r = elevationToRadius(maxR, closest.elevation);
            float azimR = closest.azimuth - (float) mOrientation;
            double ang = Math.toRadians(azimR);
            float sx = cx + r * (float) Math.sin(ang);
            float sy = cy - r * (float) Math.cos(ang);
            mSatelliteClickListener.onSatelliteClick(closest, sx, sy);
            return true;
        }
        return super.onTouchEvent(event);
    }
}
