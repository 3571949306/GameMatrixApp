package com.gamecenter.app.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorAlphaBar extends View {

    private Paint paint;
    private Paint checkerPaint;
    private Paint cursorPaint;
    private Paint cursorStroke;
    private int color = Color.RED;
    private float alpha = 1f;
    private OnAlphaChangedListener listener;
    private Bitmap checkerBitmap;

    public interface OnAlphaChangedListener {
        void onAlphaChanged(float alpha);
    }

    public ColorAlphaBar(Context context) {
        super(context);
        init();
    }

    public ColorAlphaBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorAlphaBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(3f);
        cursorStroke.setStyle(Paint.Style.STROKE);
        cursorStroke.setStrokeWidth(1.5f);
        cursorStroke.setColor(Color.BLACK);
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    public void setAlpha(float a) {
        alpha = a;
        invalidate();
    }

    public float getAlpha() { return alpha; }

    public void setOnAlphaChangedListener(OnAlphaChangedListener l) {
        listener = l;
    }

    private void createCheckerBitmap(int w, int h) {
        checkerBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(checkerBitmap);
        Paint light = new Paint();
        light.setColor(Color.LTGRAY);
        Paint dark = new Paint();
        dark.setColor(Color.GRAY);
        int size = 8;
        for (int y = 0; y < h; y += size) {
            for (int x = 0; x < w; x += size) {
                boolean isLight = ((x / size) + (y / size)) % 2 == 0;
                c.drawRect(x, y, x + size, y + size, isLight ? light : dark);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (checkerBitmap == null || checkerBitmap.getWidth() != w || checkerBitmap.getHeight() != h) {
            createCheckerBitmap(w, h);
        }
        canvas.drawBitmap(checkerBitmap, 0, 0, checkerPaint);

        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int transparent = Color.argb(0, r, g, b);
        int opaque = Color.argb(255, r, g, b);

        LinearGradient gradient = new LinearGradient(0, 0, w, 0,
                transparent, opaque, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);

        float cx = alpha * w;
        int cursorW = 6;

        cursorPaint.setColor(Color.WHITE);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, h + 4, cursorPaint);
        canvas.drawRect(cx - cursorW / 2f, -4, cx + cursorW / 2f, h + 4, cursorStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            float x = Math.max(0, Math.min(event.getX(), getWidth()));
            alpha = x / getWidth();
            invalidate();
            if (listener != null) {
                listener.onAlphaChanged(alpha);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
