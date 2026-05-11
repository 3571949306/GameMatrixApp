package com.gamecenter.app.games.flappy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class FlappyView extends View {

    private FlappyGame game;
    private Paint skyPaint;
    private Paint birdPaint;
    private Paint birdEyePaint;
    private Paint pipePaint;
    private Paint pipeCapPaint;
    private Paint groundPaint;
    private Paint scorePaint;
    private Paint titlePaint;
    private Paint subPaint;

    private float viewWidth;
    private float viewHeight;

    public FlappyView(Context context) {
        super(context);
        init();
    }

    public FlappyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        skyPaint.setColor(Color.parseColor("#87CEEB"));

        birdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdPaint.setColor(Color.parseColor("#FFD700"));
        birdPaint.setStyle(Paint.Style.FILL);

        birdEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdEyePaint.setColor(Color.BLACK);
        birdEyePaint.setStyle(Paint.Style.FILL);

        pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipePaint.setColor(Color.parseColor("#4CAF50"));
        pipePaint.setStyle(Paint.Style.FILL);

        pipeCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeCapPaint.setColor(Color.parseColor("#388E3C"));
        pipeCapPaint.setStyle(Paint.Style.FILL);

        groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        groundPaint.setColor(Color.parseColor("#8B4513"));

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(80);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(50);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);

        subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.WHITE);
        subPaint.setTextSize(36);
        subPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(FlappyGame game) {
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

        canvas.drawColor(Color.parseColor("#87CEEB"));

        float groundY = game.getGroundY();

        for (FlappyGame.Pipe pipe : game.getPipes()) {
            float pipeX = pipe.x;
            float pipeW = game.getPipeWidth();
            float capH = pipeW * 0.3f;

            canvas.drawRect(pipeX, 0, pipeX + pipeW, pipe.gapY - capH, pipePaint);
            canvas.drawRect(pipeX - capH * 0.3f, pipe.gapY - capH, pipeX + pipeW + capH * 0.3f, pipe.gapY, pipeCapPaint);

            float lowerTop = pipe.gapY + pipe.gapHeight;
            canvas.drawRect(pipeX - capH * 0.3f, lowerTop, pipeX + pipeW + capH * 0.3f, lowerTop + capH, pipeCapPaint);
            canvas.drawRect(pipeX, lowerTop + capH, pipeX + pipeW, groundY, pipePaint);
        }

        canvas.drawRect(0, groundY, viewWidth, viewHeight, groundPaint);
        Paint grassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grassPaint.setColor(Color.parseColor("#228B22"));
        canvas.drawRect(0, groundY, viewWidth, groundY + 6, grassPaint);

        float birdX = game.getBirdX();
        float birdY = game.getBirdY();
        float birdR = game.getBirdRadius();

        if (!game.isStarted() || !game.isGameOver() || game.isGameOver()) {
            canvas.drawCircle(birdX, birdY, birdR, birdPaint);
            Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wingPaint.setColor(Color.parseColor("#FFA000"));
            Path wing = new Path();
            wing.moveTo(birdX - birdR * 0.3f, birdY);
            wing.lineTo(birdX - birdR * 1.2f, birdY - birdR * 0.5f);
            wing.lineTo(birdX - birdR * 0.3f, birdY + birdR * 0.3f);
            wing.close();
            canvas.drawPath(wing, wingPaint);
            canvas.drawCircle(birdX + birdR * 0.45f, birdY - birdR * 0.25f, birdR * 0.2f, birdEyePaint);
        }

        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("点击屏幕起飞!", viewWidth / 2, viewHeight * 0.3f, titlePaint);
            canvas.drawText("避开绿色管道", viewWidth / 2, viewHeight * 0.3f + 50, subPaint);
        }

        String scoreText = String.valueOf(game.getScore());
        scorePaint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText(scoreText, viewWidth / 2, viewHeight * 0.12f, scorePaint);

        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(56);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            overPaint.setShadowLayer(4, 2, 2, Color.BLACK);
            canvas.drawText("游戏结束!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            subPaint.setTextSize(36);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 50, subPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            if (game.isGameOver()) {
                game.reset();
                game.setGameArea(viewWidth, viewHeight);
                invalidate();
                return true;
            }
            game.jump();
            invalidate();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
