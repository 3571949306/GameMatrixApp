package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorHueBar extends View {

    private Paint paint;
    private Paint cursorPaint;
    private Paint cursorStroke;
    private float hue = 0f;
    private OnHueChangedListener listener;

    public interface OnHueChangedListener {
        void onHueChanged(float hue);
    }

    public ColorHueBar(Context context) {
        super(context);
        init();
    }

    public ColorHueBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorHueBar(Context context, AttributeSet attrs, int defStyleAttr) {
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

    public float getHue() { return hue; }

    public void setOnHueChangedListener(OnHueChangedListener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int[] colors = new int[7];
        float[] hsv = new float[]{0f, 1f, 1f};
        for (int i = 0; i < 7; i++) {
            hsv[0] = i * 60f;
            colors[i] = Color.HSVToColor(hsv);
        }
        LinearGradient gradient = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);

        float cx = (hue / 360f) * w;
        int cursorH = h + 8;
        int cursorW = 6;

        int currentColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        int brightness = (Color.red(currentColor) * 299 + Color.green(currentColor) * 587 + Color.blue(currentColor) * 114) / 1000;
        cursorPaint.setColor(brightness > 128 ? Color.BLACK : Color.WHITE);

        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, cursorH, cursorPaint);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, cursorH, cursorStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            float x = Math.max(0, Math.min(event.getX(), getWidth()));
            hue = (x / getWidth()) * 360f;
            invalidate();
            if (listener != null) {
                listener.onHueChanged(hue);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
