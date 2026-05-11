package com.gamecenter.app.games.reaction;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ReactionView extends View {

    private static final int COLOR_WAITING = 0xFFE53935;
    private static final int COLOR_READY = 0xFF43A047;
    private static final int COLOR_IDLE = 0xFF1E88E5;
    private static final int COLOR_TAPPED = 0xFF1E88E5;
    private static final int COLOR_TOO_SOON = 0xFFFF8F00;

    private ReactionGame game;
    private Paint bgPaint;
    private Paint textPaint;
    private Paint subTextPaint;

    public ReactionView(Context context) {
        super(context);
        init();
    }

    public ReactionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(72f);
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(36f);
    }

    public void setGame(ReactionGame game) {
        this.game = game;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        int color;
        String mainText = "";
        String subText = "";

        switch (game.getState()) {
            case IDLE:
                color = COLOR_IDLE;
                mainText = "反应力挑战";
                subText = "点击屏幕开始";
                break;
            case WAITING:
                color = COLOR_WAITING;
                mainText = "等待...";
                subText = "变绿后立即点击!";
                break;
            case READY:
                color = COLOR_READY;
                mainText = "点击!";
                subText = "";
                break;
            case TAPPED:
                color = COLOR_TAPPED;
                long ms = game.getCurrentResult();
                mainText = ms + " ms";
                subText = "点击继续下一轮 (第" + (game.getRound() + 1) + "轮)";
                break;
            case TOO_SOON:
                color = COLOR_TOO_SOON;
                mainText = "太早了!";
                subText = "点击重新等待";
                break;
            default:
                color = COLOR_IDLE;
                break;
        }

        bgPaint.setColor(color);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        float mainY = getHeight() / 2 - textPaint.getTextSize() / 3;
        canvas.drawText(mainText, getWidth() / 2, mainY, textPaint);

        if (!subText.isEmpty()) {
            float subY = mainY + textPaint.getTextSize() * 0.7f;
            canvas.drawText(subText, getWidth() / 2, subY, subTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && game != null) {
            game.onTap();
            invalidate();
            if (listener != null) listener.onStateChange();
        }
        return true;
    }

    public void refresh() {
        invalidate();
    }

    private OnStateChangeListener listener;

    public interface OnStateChangeListener {
        void onStateChange();
    }

    public void setOnStateChangeListener(OnStateChangeListener l) {
        this.listener = l;
    }
}
