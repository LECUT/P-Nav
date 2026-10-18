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
package com.ecut.pnav;

import android.content.Context;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DraggableFloatingActionButton extends FloatingActionButton implements View.OnTouchListener {

    private float downRawX, downRawY;
    private float downX, downY;
    private float originalX, originalY;
    private boolean isDragging = false;
    private Vibrator vibrator;

    // 移动超过此阈值即进入拖拽模式 (dp)
    private static final float DRAG_THRESHOLD_DP = 12f;
    private float dragThresholdPx;

    public DraggableFloatingActionButton(Context context) {
        super(context);
        init(context);
    }

    public DraggableFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DraggableFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        dragThresholdPx = DRAG_THRESHOLD_DP * context.getResources().getDisplayMetrics().density;
        setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = rawX;
                downRawY = rawY;
                downX = getX();
                downY = getY();
                originalX = getX();
                originalY = getY();
                isDragging = false;
                return false; // 不消费，让系统可以触发点击

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(rawX - downRawX);
                float dy = Math.abs(rawY - downRawY);

                if (!isDragging && (dx > dragThresholdPx || dy > dragThresholdPx)) {
                    isDragging = true;
                    // 震动反馈
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(50);
                    }
                }

                if (isDragging) {
                    float newX = originalX + (rawX - downRawX);
                    float newY = originalY + (rawY - downRawY);

                    View parent = (View) getParent();
                    if (parent != null) {
                        newX = Math.max(0, Math.min(newX, parent.getWidth() - getWidth()));
                        newY = Math.max(0, Math.min(newY, parent.getHeight() - getHeight()));
                    }

                    setX(newX);
                    setY(newY);
                    return true; // 拖拽中消费事件
                }
                // 还没超过阈值，不消费，让系统继续处理
                return false;

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    // 拖拽结束，回到原位不触发点击
                    isDragging = false;
                    return true;
                }
                // 不是拖拽，让系统触发点击
                return false;

            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return false;
        }

        return false;
    }
}
