package com.gamecenter.app.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class BubbleLevelView extends View {

    private float angleX = 0f;
    private float angleY = 0f;

    private final Paint bubblePaint;
    private final Paint circlePaint;
    private final Paint gridPaint;
    private final Paint bgPaint;

    public BubbleLevelView(Context context) {
        this(context, null);
    }

    public BubbleLevelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleLevelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubblePaint.setColor(0xFFFF9800);
        bubblePaint.setAlpha(180);
        bubblePaint.setStyle(Paint.Style.FILL);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(0xFF333333);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(2f);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0xFFCCCCCC);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFF5F5F5);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setAngles(float x, float y) {
        angleX = x;
        angleY = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float padding = 8f;
        float left = padding;
        float top = padding;
        float right = w - padding;
        float bottom = h - padding;
        float radiusBg = 12f;

        RectF bgRect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(bgRect, radiusBg, radiusBg, bgPaint);

        float cx = w / 2f;
        float cy = h / 2f;

        canvas.drawLine(cx, top + padding, cx, bottom - padding, gridPaint);
        canvas.drawLine(left + padding, cy, right - padding, cy, gridPaint);

        float radius = Math.min(w, h) / 3f;
        canvas.drawCircle(cx, cy, radius, circlePaint);

        float bubbleX = cx + (angleX / 90f) * radius;
        float bubbleY = cy + (angleY / 90f) * radius;

        float dx = bubbleX - cx;
        float dy = bubbleY - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > radius) {
            bubbleX = cx + dx / dist * radius;
            bubbleY = cy + dy / dist * radius;
        }

        float bubbleRadius = radius * 0.18f;
        canvas.drawCircle(bubbleX, bubbleY, bubbleRadius, bubblePaint);
    }
}
