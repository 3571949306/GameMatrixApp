package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorSVPanel extends View {

    private Paint paint;
    private Paint cursorPaint;
    private Paint cursorStroke;
    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;
    private OnColorChangedListener listener;

    public interface OnColorChangedListener {
        void onColorChanged(float hue, float sat, float val);
    }

    public ColorSVPanel(Context context) {
        super(context);
        init();
    }

    public ColorSVPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorSVPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(3f);
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeWidth(1.5f);
        cursorStroke.setColor(Color.BLACK);
    }

    public void setHue(float h) {
        hue = h;
        invalidate();
    }

    public void setSV(float s, float v) {
        sat = s;
        val = v;
        invalidate();
    }

    public void setOnColorChangedListener(OnColorChangedListener l) {
        listener = l;
    }

    public float getHue() { return hue; }
    public float getSat() { return sat; }
    public float getVal() { return val; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float[] hsv = new float[]{hue, 1f, 1f};
        int pureColor = Color.HSVToColor(hsv);

        LinearGradient satGradient = new LinearGradient(0, 0, w, 0,
                Color.WHITE, pureColor, Shader.TileMode.CLAMP);
        paint.setShader(satGradient);
        canvas.drawRect(0, 0, w, h, paint);

        LinearGradient valGradient = new LinearGradient(0, 0, 0, h,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        paint.setShader(valGradient);
        canvas.drawRect(0, 0, w, h, paint);

        float cx = sat * w;
        float cy = (1f - val) * h;
        int radius = (int) (Math.min(w, h) * 0.06f);
        radius = Math.max(radius, 8);

        int currentColor = Color.HSVToColor(new float[]{hue, sat, val});
        int brightness = (Color.red(currentColor) * 299 + Color.green(currentColor) * 587 + Color.blue(currentColor) * 114) / 1000;
        cursorPaint.setColor(brightness > 128 ? Color.BLACK : Color.WHITE);

        canvas.drawCircle(cx, cy, radius, cursorPaint);
        canvas.drawCircle(cx, cy, radius, cursorStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            float x = Math.max(0, Math.min(event.getX(), getWidth()));
            float y = Math.max(0, Math.min(event.getY(), getHeight()));
            sat = x / getWidth();
            val = 1f - y / getHeight();
            invalidate();
            if (listener != null && event.getAction() != MotionEvent.ACTION_UP) {
                listener.onColorChanged(hue, sat, val);
            }
            if (listener != null && event.getAction() == MotionEvent.ACTION_UP) {
                listener.onColorChanged(hue, sat, val);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
