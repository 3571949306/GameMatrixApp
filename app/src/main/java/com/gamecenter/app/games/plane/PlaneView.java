package com.gamecenter.app.games.plane;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class PlaneView extends View {

    private PlaneGame game;
    private Paint bgPaint;
    private Paint planePaint;
    private Paint bulletPaint;
    private Paint enemyPaint;
    private Paint scorePaint;
    private Paint textPaint;

    private float viewWidth;
    private float viewHeight;

    public PlaneView(Context context) {
        super(context);
        init();
    }

    public PlaneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#0D0D2B"));

        planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint.setColor(Color.parseColor("#00BCD4"));
        planePaint.setStyle(Paint.Style.FILL);

        bulletPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bulletPaint.setColor(Color.parseColor("#FFEB3B"));
        bulletPaint.setStyle(Paint.Style.FILL);

        enemyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enemyPaint.setColor(Color.parseColor("#E53935"));
        enemyPaint.setStyle(Paint.Style.FILL);

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(50);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(PlaneGame game) {
        this.game = game;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (game != null) {
            game.setGameArea(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#0D0D2B"));

        drawStars(canvas);

        for (PlaneGame.Bullet b : game.getBullets()) {
            canvas.drawCircle(b.x, b.y, 5, bulletPaint);
        }

        for (PlaneGame.Enemy e : game.getEnemies()) {
            Path path = new Path();
            path.moveTo(e.x, e.y - e.h / 2);
            path.lineTo(e.x + e.w / 2, e.y + e.h / 2);
            path.lineTo(e.x, e.y + e.h / 3);
            path.lineTo(e.x - e.w / 2, e.y + e.h / 2);
            path.close();
            canvas.drawPath(path, enemyPaint);
        }

        float px = game.getPlaneX();
        float py = game.getPlaneY();
        float pw = game.getPlaneW();
        float ph = game.getPlaneH();

        Path planePath = new Path();
        planePath.moveTo(px, py - ph / 2);
        planePath.lineTo(px + pw / 2, py + ph / 2);
        planePath.lineTo(px, py + ph / 3);
        planePath.lineTo(px - pw / 2, py + ph / 2);
        planePath.close();
        canvas.drawPath(planePath, planePaint);

        Paint flamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flamePaint.setColor(Color.parseColor("#FF9800"));
        canvas.drawRect(px - pw * 0.15f, py + ph * 0.25f, px + pw * 0.15f, py + ph * 0.55f, flamePaint);

        canvas.drawText("" + game.getScore(), viewWidth / 2, viewHeight * 0.08f, scorePaint);

        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("滑动屏幕移动飞机", viewWidth / 2, viewHeight * 0.35f, textPaint);
        }

        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(50);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("游戏结束!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 46, textPaint);
        }
    }

    private void drawStars(Canvas canvas) {
        Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.WHITE);
        int starSeed = 42;
        for (int i = 0; i < 40; i++) {
            starSeed = starSeed * 1103515245 + 12345;
            float sx = ((starSeed >> 16) & 0x7FFF) / 32767f * viewWidth;
            starSeed = starSeed * 1103515245 + 12345;
            float sy = ((starSeed >> 16) & 0x7FFF) / 32767f * viewHeight;
            float r = (Math.abs(starSeed % 3) + 1) * 1f;
            canvas.drawCircle(sx, sy, r, starPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null) return true;
        if (game.isGameOver()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                game.reset();
                game.setGameArea(viewWidth, viewHeight);
                invalidate();
            }
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                game.setPlaneX(event.getX());
                invalidate();
                break;
        }
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
