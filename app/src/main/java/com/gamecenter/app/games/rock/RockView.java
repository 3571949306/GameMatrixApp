package com.gamecenter.app.games.rock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class RockView extends View {

    private RockGame game;
    private Paint bgPaint;
    private Paint choicePaint;
    private Paint resultPaint;
    private Paint scorePaint;
    private Paint labelPaint;

    private float viewWidth;
    private float viewHeight;
    private float buttonW;
    private float buttonH;
    private float buttonSpacing;

    public RockView(Context context) {
        super(context);
        init();
    }

    public RockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1E1E32"));

        choicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        choicePaint.setColor(Color.WHITE);
        choicePaint.setTextSize(50);
        choicePaint.setTextAlign(Paint.Align.CENTER);

        resultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        resultPaint.setColor(Color.parseColor("#FFD700"));
        resultPaint.setTextSize(44);
        resultPaint.setTextAlign(Paint.Align.CENTER);
        resultPaint.setFakeBoldText(true);

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(40);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#AAAACC"));
        labelPaint.setTextSize(32);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(RockGame game) {
        this.game = game;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        buttonW = w * 0.28f;
        buttonH = Math.min(h * 0.22f, buttonW * 1.4f);
        buttonSpacing = (w - buttonW * 3) / 4;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1E1E32"));

        float titleY = viewHeight * 0.06f + 40;
        scorePaint.setTextSize(36);
        canvas.drawText("你: " + game.getPlayerScore()
                + "  |  电脑: " + game.getComputerScore(), viewWidth / 2, titleY, scorePaint);

        float btnStartY = viewHeight * 0.18f;
        for (int i = 0; i < 3; i++) {
            float left = buttonSpacing + i * (buttonW + buttonSpacing);
            float top = btnStartY;

            int color;
            switch (i) {
                case RockGame.ROCK: color = Color.parseColor("#E53935"); break;
                case RockGame.SCISSORS: color = Color.parseColor("#1E88E5"); break;
                default: color = Color.parseColor("#43A047"); break;
            }

            Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnPaint.setColor(color);
            btnPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left, top, left + buttonW, top + buttonH, 20, 20, btnPaint);

            Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            emojiPaint.setColor(Color.WHITE);
            emojiPaint.setTextSize(buttonW * 0.45f);
            emojiPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(RockGame.getChoiceEmoji(i), left + buttonW / 2, top + buttonH * 0.55f, emojiPaint);

            Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setColor(Color.WHITE);
            namePaint.setTextSize(28);
            namePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(RockGame.getChoiceName(i), left + buttonW / 2, top + buttonH + 34, namePaint);
        }

        if (game.getPlayerChoice() >= 0) {
            float resultY = viewHeight * 0.6f;

            Paint vsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            vsPaint.setColor(Color.WHITE);
            vsPaint.setTextSize(36);
            vsPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("你的选择: " + RockGame.getChoiceEmoji(game.getPlayerChoice())
                    + " " + RockGame.getChoiceName(game.getPlayerChoice()),
                    viewWidth / 2, resultY, vsPaint);

            float compY = resultY + 50;
            canvas.drawText("电脑选择: " + RockGame.getChoiceEmoji(game.getComputerChoice())
                    + " " + RockGame.getChoiceName(game.getComputerChoice()),
                    viewWidth / 2, compY, vsPaint);

            float resY = compY + 70;
            if (game.getLastResult() == RockGame.WIN) {
                resultPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (game.getLastResult() == RockGame.LOSE) {
                resultPaint.setColor(Color.parseColor("#E53935"));
            } else {
                resultPaint.setColor(Color.parseColor("#FF9800"));
            }
            canvas.drawText(game.getLastResultText(), viewWidth / 2, resY, resultPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            float x = event.getX();
            float y = event.getY();
            float btnStartY = viewHeight * 0.18f;

            for (int i = 0; i < 3; i++) {
                float left = buttonSpacing + i * (buttonW + buttonSpacing);
                if (x >= left && x <= left + buttonW && y >= btnStartY && y <= btnStartY + buttonH) {
                    game.choose(i);
                    invalidate();
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
