package com.gamecenter.app.games.tiles;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class TilesView extends View {

    private TilesGame game;
    private Paint blackPaint;
    private Paint whitePaint;
    private Paint grayPaint;
    private Paint touchedPaint;
    private Paint scorePaint;
    private Paint textPaint;

    private float viewWidth;
    private float viewHeight;

    public TilesView(Context context) {
        super(context);
        init();
    }

    public TilesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(Color.BLACK);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);

        grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grayPaint.setColor(Color.parseColor("#424242"));

        touchedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        touchedPaint.setColor(Color.parseColor("#AAAAAA"));

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(60);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(TilesGame game) {
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

        canvas.drawColor(Color.parseColor("#1A1A2E"));

        float cellSize = game.getCellSize();
        float totalScroll = game.getTotalScroll();
        float topRowOffset = totalScroll % cellSize;
        int startRow = (int) (totalScroll / cellSize);

        for (int visibleRow = -1; visibleRow <= (int) (viewHeight / cellSize) + 1; visibleRow++) {
            int dataRow = startRow + visibleRow;
            if (dataRow < 0 || dataRow >= game.getTileRows().size()) continue;

            TilesGame.Row row = game.getTileRows().get(dataRow);
            float top = viewHeight - (visibleRow + 1) * cellSize + topRowOffset;
            float bottom = top + cellSize;

            for (int col = 0; col < TilesGame.COLUMNS; col++) {
                float left = col * cellSize;
                float right = left + cellSize;

                Paint fillPaint = row.isBlack[col] ? blackPaint : whitePaint;
                if (row.touchedCol == col) {
                    fillPaint = touchedPaint;
                }
                canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, fillPaint);

                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(Color.parseColor("#333333"));
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(1);
                canvas.drawRect(left, top, right, bottom, borderPaint);
            }
        }

        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("点击黑块开始", viewWidth / 2, viewHeight * 0.4f, textPaint);
        }

        scorePaint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText("" + game.getScore(), viewWidth / 2, viewHeight * 0.1f, scorePaint);

        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(50);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("点击了白块!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            textPaint.setTextSize(34);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 46, textPaint);
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
            int col = (int) (event.getX() / game.getCellSize());
            game.touch(col);
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
