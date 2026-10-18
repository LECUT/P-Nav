package com.ecut.pnav.ui.status;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.GnssStatus;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.ecut.pnav.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 卫星详细状态表（科研风，左对齐紧凑列）。
 * 7 列：系统 / PRN / 频段 / C/N0 / 高度角 / 方位角 / 参与解算。
 *
 * 排版策略：
 *  - 列宽 = 固定权重 × 剩余宽度（列间有固定安全间隙 GAP），并以**表头实测宽度**为下限，
 *    避免英文表头（"System"）比中文（"系统"）宽时撑破列宽压到相邻列；
 *  - 表头与数值统一左对齐；颜色全部取主题色，深色模式自动适配。
 */
public class GnssSatelliteDetailView extends View {

    private Paint mHeaderPaint;
    private Paint mTextPaint;
    private Paint mDimPaint;
    private Paint mDotPaint;      // 参与解算 — 实心圆
    private Paint mHollowPaint;   // 未参与 — 空心圆
    private Paint mLinePaint;
    private final Context mCtx;

    private List<SatelliteDetail> details = new ArrayList<>();

    // 每列相对权重（决定列宽）。System/PRN 偏窄，数值列与 Fix 列给足。
    private static final float[] COL_W = {1.00f, 1.15f, 1.35f, 1.35f, 1.55f, 1.55f, 1.10f};
    private static final int COLUMNS = 7;

    private static final int ROW_HEIGHT_DP = 38;
    private static final int ROW_GAP_DP = 2;
    private static final int COL_GAP_DP = 12;      // 列间固定安全间隙
    private static final int HEADER_PAD_DP = 4;    // 列宽下限在表头实测宽度上的余量
    private static final int LEFT_PAD_DP = 6;
    private static final float HEADER_SIZE_SP = 11f;
    private static final float TEXT_SIZE_SP = 12f;

    private final int mRowH;
    private final int mRowGap;
    private final int mColGap;
    private final int mLeftPad;

    private final float mColBaseY;                 // 表头行 baseline 的 y
    private final float mFirstRowTop;              // 表头下分隔线 y
    private final float mRowCenterOff;             // 数据行中心相对行顶偏移

    public GnssSatelliteDetailView(Context context) {
        this(context, null);
    }

    public GnssSatelliteDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCtx = context;
        mRowH = dp(ROW_HEIGHT_DP);
        mRowGap = dp(ROW_GAP_DP);
        mColGap = dp(COL_GAP_DP);
        mLeftPad = dp(LEFT_PAD_DP);

        mColBaseY = dp(15f);
        mFirstRowTop = dp(24f);
        mRowCenterOff = mRowH / 2f;

        initPaints();
    }

    private void initPaints() {
        int text = ContextCompat.getColor(mCtx, R.color.text_primary);
        int outline = ContextCompat.getColor(mCtx, R.color.outlineVariant);
        int dim = ContextCompat.getColor(mCtx, R.color.onSurfaceVariant);

        mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHeaderPaint.setColor(dim);
        mHeaderPaint.setTextSize(sp(HEADER_SIZE_SP));
        mHeaderPaint.setFakeBoldText(true);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(text);
        mTextPaint.setTextSize(sp(TEXT_SIZE_SP));

        mDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDimPaint.setColor(dim);
        mDimPaint.setTextSize(sp(TEXT_SIZE_SP));

        mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDotPaint.setColor(ContextCompat.getColor(mCtx, R.color.tertiary));
        mDotPaint.setStyle(Paint.Style.FILL);

        mHollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHollowPaint.setStyle(Paint.Style.STROKE);
        mHollowPaint.setColor(outline);
        mHollowPaint.setStrokeWidth(dp(1.2f));

        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(outline);
        mLinePaint.setStrokeWidth(0.6f);
        mLinePaint.setStyle(Paint.Style.STROKE);
    }

    public void setSatelliteDetails(List<SatelliteDetail> data) {
        int old = details.size();
        details = data != null ? data : new ArrayList<>();
        if (details.size() != old) requestLayout(); else invalidate();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    private int getColor(int constellationType) {
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:       return 0xFF007AFF;
            case GnssStatus.CONSTELLATION_SBAS:       return 0xFFFF9500;
            case GnssStatus.CONSTELLATION_GLONASS:    return 0xFFAF52DE;
            case GnssStatus.CONSTELLATION_QZSS:       return 0xFF00BFA5;
            case GnssStatus.CONSTELLATION_BEIDOU:     return 0xFFFF3B30;
            case GnssStatus.CONSTELLATION_GALILEO:    return 0xFF34C759;
            case GnssStatus.CONSTELLATION_IRNSS:      return 0xFFFF6D00;
            default:                                  return 0xFF8E8E93;
        }
    }

    private static String getSysPrefix(int t) {
        switch (t) {
            case GnssStatus.CONSTELLATION_GPS:       return "G";
            case GnssStatus.CONSTELLATION_SBAS:       return "S";
            case GnssStatus.CONSTELLATION_GLONASS:    return "R";
            case GnssStatus.CONSTELLATION_QZSS:       return "J";
            case GnssStatus.CONSTELLATION_BEIDOU:     return "C";
            case GnssStatus.CONSTELLATION_GALILEO:    return "E";
            case GnssStatus.CONSTELLATION_IRNSS:      return "I";
            default:                                  return "U";
        }
    }

    private static final double FREQ_TOL = 10.0e6;

    private static String getFreqLabel(double f, int type) {
        if (f <= 0) return "";
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS:
                if (Math.abs(f - 1.57542e9) < FREQ_TOL) return "L1";
                if (Math.abs(f - 1.22760e9) < FREQ_TOL) return "L2";
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "L5";
                return "";
            case GnssStatus.CONSTELLATION_SBAS:
                if (Math.abs(f - 1.57542e9) < FREQ_TOL) return "L1";
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "L5";
                return "";
            case GnssStatus.CONSTELLATION_GLONASS:
                if (f >= 1598.0625e6 - FREQ_TOL && f <= 1605.375e6 + FREQ_TOL) return "G1";
                if (f >= 1242.9375e6 - FREQ_TOL && f <= 1248.625e6 + FREQ_TOL) return "G2";
                return "G1";
            case GnssStatus.CONSTELLATION_QZSS:
                if (Math.abs(f - 1.57542e9) < FREQ_TOL) return "L1";
                if (Math.abs(f - 1.22760e9) < FREQ_TOL) return "L2";
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "L5";
                if (Math.abs(f - 1.27875e9) < FREQ_TOL) return "LEX";
                return "";
            case GnssStatus.CONSTELLATION_BEIDOU:
                if (Math.abs(f - 1.561098e9) < FREQ_TOL) return "B1I";
                if (Math.abs(f - 1.57542e9) < FREQ_TOL) return "B1C";
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "B2a";
                if (Math.abs(f - 1.20714e9) < FREQ_TOL) return "B2b";
                return "";
            case GnssStatus.CONSTELLATION_GALILEO:
                if (Math.abs(f - 1.57542e9) < FREQ_TOL) return "E1";
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "E5a";
                if (Math.abs(f - 1.20714e9) < FREQ_TOL) return "E5b";
                if (Math.abs(f - 1.27875e9) < FREQ_TOL) return "E6";
                return "";
            case GnssStatus.CONSTELLATION_IRNSS:
                if (Math.abs(f - 1.17645e9) < FREQ_TOL) return "L5";
                if (Math.abs(f - 2.492028e9) < FREQ_TOL) return "S";
                return "";
            default:
                return "";
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = getPaddingTop() + getPaddingBottom() + dp(22);
        h += details.size() * (mRowH + mRowGap);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        float left = getPaddingLeft() + mLeftPad;
        float top = getPaddingTop();
        float avail = getWidth() - getPaddingLeft() - getPaddingRight() - mLeftPad * 2;
        if (avail <= 0) return;

        String[] headers = {
                mCtx.getString(R.string.detail_col_sys),
                mCtx.getString(R.string.detail_col_svid),
                mCtx.getString(R.string.detail_col_freq),
                mCtx.getString(R.string.detail_col_cn0),
                mCtx.getString(R.string.detail_col_elev),
                mCtx.getString(R.string.detail_col_azim),
                mCtx.getString(R.string.detail_col_fix)
        };

        // 列宽 = 权重分配 + 表头实测宽度下限，列间固定间隙。
        // 权重只是相对比例，英文表头（如 "System"）比中文（"系统"）宽得多，
        // 若只按权重分配会撑破列宽压到相邻列，故用表头实测宽度兜底。
        float totalGap = mColGap * (COLUMNS - 1);
        float wSum = 0; for (float w : COL_W) wSum += w;
        float colUnit = (avail - totalGap) / wSum;

        float[] colW = new float[COLUMNS];
        float[] colMin = new float[COLUMNS];   // 各列表头实测下限
        float need = 0;
        for (int i = 0; i < COLUMNS; i++) {
            colMin[i] = mHeaderPaint.measureText(headers[i]) + dp(HEADER_PAD_DP);
            colW[i] = Math.max(colUnit * COL_W[i], colMin[i]);
            need += colW[i];
        }

        float budget = avail - totalGap;
        if (need > budget) {
            // 只从"高于自身表头下限、有余量"的列按比例回收。
            // 不可整体等比压缩：那会把本就需要加宽的列（如英文 System）重新压回
            // 下限之下，恰好把要修的表头重叠又压出来。
            float slack = 0;
            for (int i = 0; i < COLUMNS; i++) slack += colW[i] - colMin[i];
            float deficit = need - budget;
            if (slack > 0 && deficit <= slack) {
                for (int i = 0; i < COLUMNS; i++) {
                    colW[i] -= deficit * (colW[i] - colMin[i]) / slack;
                }
            } else {
                // 极端窄屏：连所有表头下限都放不下，只能整体等比压缩兜底
                float scale = budget / need;
                for (int i = 0; i < COLUMNS; i++) colW[i] *= scale;
            }
        }

        float[] colX = new float[COLUMNS];
        float x = left;
        for (int i = 0; i < COLUMNS; i++) {
            colX[i] = x;
            x += colW[i] + mColGap;
        }
        float right = x - mColGap;

        float base = baseline(mHeaderPaint, top + mColBaseY);
        for (int i = 0; i < COLUMNS; i++) {
            c.drawText(headers[i], colX[i], base, mHeaderPaint);
        }

        float rowTop = top + mFirstRowTop;
        c.drawLine(left, rowTop, right, rowTop, mLinePaint);

        if (details.isEmpty()) {
            mDimPaint.setAlpha(150);
            c.drawText(mCtx.getString(R.string.sat_detail_empty), left,
                    baseline(mDimPaint, rowTop + mRowCenterOff), mDimPaint);
            mDimPaint.setAlpha(255);
            return;
        }

        for (int i = 0; i < details.size(); i++) {
            SatelliteDetail d = details.get(i);
            float rowY = rowTop + mRowGap + i * (mRowH + mRowGap);
            float centerY = rowY + mRowCenterOff;
            c.drawLine(left, rowY, right, rowY, mLinePaint);
            float tb = baseline(mTextPaint, centerY);

            // 系统
            mTextPaint.setColor(getColor(d.constellationType));
            mTextPaint.setFakeBoldText(true);
            c.drawText(getSysPrefix(d.constellationType), colX[0], tb, mTextPaint);

            // PRN
            mTextPaint.setColor(ContextCompat.getColor(mCtx, R.color.text_primary));
            mTextPaint.setFakeBoldText(false);
            c.drawText(String.format("%02d", d.svid), colX[1], tb, mTextPaint);

            // 频段
            String freq = getFreqLabel(d.carrierFrequencyHz, d.constellationType);
            c.drawText(freq.isEmpty() ? "-" : freq, colX[2], tb, mTextPaint);

            // C/N0
            if (d.cn0 > 0) {
                mTextPaint.setColor(colorForCn0(d.cn0));
                c.drawText(String.format("%.0f", d.cn0), colX[3], tb, mTextPaint);
            } else {
                mDimPaint.setAlpha(150);
                c.drawText("-", colX[3], baseline(mDimPaint, centerY), mDimPaint);
                mDimPaint.setAlpha(255);
            }

            // 高度角 / 方位角
            mTextPaint.setColor(ContextCompat.getColor(mCtx, R.color.text_primary));
            c.drawText(String.format("%.0f°", d.elevation), colX[4], tb, mTextPaint);
            c.drawText(String.format("%.0f°", d.azimuth), colX[5], tb, mTextPaint);

            // 参与解算（实心/空心圆，位于第6列起点+4dp）
            float dotCx = colX[6] + mColGap;
            float dotCy = centerY;
            float dotR = dp(4.5f);
            if (d.usedInFix) {
                c.drawCircle(dotCx, dotCy, dotR, mDotPaint);
            } else {
                c.drawCircle(dotCx, dotCy, dotR, mHollowPaint);
            }
        }

        float bottom = rowTop + mRowGap + details.size() * (mRowH + mRowGap);
        c.drawLine(left, bottom, right, bottom, mLinePaint);
    }

    /** 按 C/N0 分段着色（科研式阈值色），返回正文用色 */
    private int colorForCn0(float cn0) {
        int text = ContextCompat.getColor(mCtx, R.color.text_primary);
        if (cn0 < 20) return 0xFFF4511E;   // 差 — 橙红
        if (cn0 < 30) return 0xFFFB8C00;   // 中 — 橙
        if (cn0 < 40) return 0xFF43A047;   // 良 — 绿
        return text;
    }

    private float baseline(Paint p, float cy) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return cy - (fm.ascent + fm.descent) / 2f;
    }
}
