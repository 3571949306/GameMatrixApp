package com.gamecenter.app.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight compass canvas with no image or network dependency. */
public final class CompassView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float heading;
    private boolean available = true;

    public CompassView(Context context) { this(context, null); }

    public CompassView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public CompassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(0xFF607D8B);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(0xFF90A4AE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        labelPaint.setTextSize(dp(18));
        labelPaint.setColor(0xFF37474F);
        needlePaint.setStyle(Paint.Style.FILL);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(0xFF37474F);
        setContentDescription("指南针");
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    public void setHeading(float heading) {
        this.heading = (heading % 360f + 360f) % 360f;
        invalidate();
    }

    public void setAvailable(boolean available) {
        this.available = available;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.max(0f, Math.min(getWidth(), getHeight()) / 2f - dp(18));
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius - dp(8), ringPaint);

        canvas.save();
        canvas.rotate(-heading, cx, cy);
        for (int i = 0; i < 36; i++) {
            float angle = (float) Math.toRadians(i * 10f - 90f);
            float outer = radius - dp(2);
            float inner = outer - (i % 3 == 0 ? dp(12) : dp(6));
            canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer, tickPaint);
        }
        drawLabel(canvas, "N", cx, cy - radius + dp(28), 0xFFD32F2F);
        drawLabel(canvas, "E", cx + radius - dp(28), cy + dp(6), 0xFF37474F);
        drawLabel(canvas, "S", cx, cy + radius - dp(14), 0xFF37474F);
        drawLabel(canvas, "W", cx - radius + dp(28), cy + dp(6), 0xFF37474F);
        canvas.restore();

        if (available) {
            Path north = new Path();
            north.moveTo(cx, cy - radius + dp(42));
            north.lineTo(cx - dp(9), cy + dp(8));
            north.lineTo(cx, cy + dp(2));
            north.lineTo(cx + dp(9), cy + dp(8));
            north.close();
            needlePaint.setColor(0xFFE53935);
            canvas.drawPath(north, needlePaint);

            Path south = new Path();
            south.moveTo(cx, cy + radius - dp(42));
            south.lineTo(cx - dp(9), cy - dp(8));
            south.lineTo(cx, cy - dp(2));
            south.lineTo(cx + dp(9), cy - dp(8));
            south.close();
            needlePaint.setColor(0xFF90A4AE);
            canvas.drawPath(south, needlePaint);
            canvas.drawCircle(cx, cy, dp(6), centerPaint);
        }
    }

    private void drawLabel(Canvas canvas, String text, float x, float y, int color) {
        labelPaint.setColor(color);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2f, labelPaint);
    }
}
