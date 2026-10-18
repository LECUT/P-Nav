package com.ecut.pnav.ui.status;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GnssStatus;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.ecut.pnav.R;

import java.util.ArrayList;
import java.util.List;

public class GnssCn0View extends View {
    private Paint mBarPaint;
    private Paint mTextPaint;
    private Paint mBgPaint;
    private Paint mLabelPaint;
    private Paint mValuePaint;
    private List<Cn0Info> cn0List = new ArrayList<>();

    private static final float MAX_CN0 = 50f;
    private static final int BAR_HEIGHT = 36;
    private static final int BAR_MARGIN = 4;
    private static final int LABEL_WIDTH = 120;

    public GnssCn0View(Context context) {
        super(context);
        init();
    }

    public GnssCn0View(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Context ctx = getContext();

        mBarPaint = new Paint();
        mBarPaint.setStyle(Paint.Style.FILL);
        mBarPaint.setAntiAlias(true);

        mBgPaint = new Paint();
        mBgPaint.setColor(ContextCompat.getColor(ctx, R.color.surfaceVariant));
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        mTextPaint.setTextSize(28);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setAntiAlias(true);

        mLabelPaint = new Paint();
        mLabelPaint.setTextSize(26);
        mLabelPaint.setStyle(Paint.Style.FILL);
        mLabelPaint.setFakeBoldText(true);
        mLabelPaint.setAntiAlias(true);
        mLabelPaint.setColor(ContextCompat.getColor(ctx, R.color.text_primary));

        mValuePaint = new Paint();
        mValuePaint.setColor(Color.WHITE);
        mValuePaint.setTextSize(26);
        mValuePaint.setStyle(Paint.Style.FILL);
        mValuePaint.setFakeBoldText(true);
        mValuePaint.setAntiAlias(true);
    }

    public void setCn0Data(List<Cn0Info> data) {
        this.cn0List = data;
        requestLayout();
        postInvalidate();
    }

    private int getColor(int constellationType) {
        switch (constellationType) {
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

    private String getLabel(int constellationType) {
        switch (constellationType) {
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = cn0List.size();
        int h = getPaddingTop() + getPaddingBottom();
        if (count > 0) {
            h += count * (BAR_HEIGHT + BAR_MARGIN) + 8;
        } else {
            h += 120;
        }
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cn0List.isEmpty()) {
            mTextPaint.setColor(Color.GRAY);
            canvas.drawText("No satellite data", getWidth() / 2f, getHeight() / 2f + 10, mTextPaint);
            return;
        }

        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        float barAreaWidth = w - LABEL_WIDTH - 16;
        float top = getPaddingTop() + 8;

        for (int i = 0; i < cn0List.size(); i++) {
            Cn0Info info = cn0List.get(i);
            float y = top + i * (BAR_HEIGHT + BAR_MARGIN);

            // Background bar
            canvas.drawRoundRect(LABEL_WIDTH, y, LABEL_WIDTH + barAreaWidth, y + BAR_HEIGHT, 6, 6, mBgPaint);

            // Colored bar
            int color = getColor(info.constellationType);
            float barW = Math.max(4, (info.cn0 / MAX_CN0) * barAreaWidth);
            mBarPaint.setColor(color);
            canvas.drawRoundRect(LABEL_WIDTH, y, LABEL_WIDTH + barW, y + BAR_HEIGHT, 6, 6, mBarPaint);

            // Label: system prefix + svid + frequency
            mLabelPaint.setColor(color);
            String freqLabel = info.getFreqLabel();
            String label = getLabel(info.constellationType) + String.format("%02d", info.svid)
                    + (freqLabel.isEmpty() ? "" : " " + freqLabel);
            canvas.drawText(label, 8, y + BAR_HEIGHT - 8, mLabelPaint);

            // C/N0 value on bar
            mValuePaint.setColor(Color.WHITE);
            String cn0Str = String.format("%.0f", info.cn0);
            if (barW > 80) {
                canvas.drawText(cn0Str, LABEL_WIDTH + barW - 56, y + BAR_HEIGHT - 8, mValuePaint);
            }
        }
    }
}
