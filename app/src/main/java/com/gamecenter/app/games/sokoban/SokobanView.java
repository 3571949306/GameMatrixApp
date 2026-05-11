package com.gamecenter.app.games.sokoban;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class SokobanView extends View {

    private SokobanGame game;
    private Paint wallPaint;
    private Paint floorPaint;
    private Paint boxPaint;
    private Paint targetPaint;
    private Paint boxOnTargetPaint;
    private Paint playerPaint;
    private Paint playerOnTargetPaint;
    private float cellSize;
    private float offsetX, offsetY;
    private OnLevelCompleteListener listener;
    private float touchStartX, touchStartY;

    public interface OnLevelCompleteListener {
        void onComplete();
    }

    public SokobanView(Context context) {
        super(context);
        init();
    }

    public SokobanView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(0xFF795548);
        wallPaint.setStyle(Paint.Style.FILL);

        floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorPaint.setColor(0xFFEEEEEE);
        floorPaint.setStyle(Paint.Style.FILL);

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(0xFFFF9800);
        boxPaint.setStyle(Paint.Style.FILL);

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setColor(0xFF4CAF50);
        targetPaint.setStyle(Paint.Style.FILL);

        boxOnTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxOnTargetPaint.setColor(0xFF8BC34A);
        boxOnTargetPaint.setStyle(Paint.Style.FILL);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(0xFF2196F3);
        playerPaint.setStyle(Paint.Style.FILL);

        playerOnTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerOnTargetPaint.setColor(0xFF64B5F6);
        playerOnTargetPaint.setStyle(Paint.Style.FILL);
    }

    public void setGame(SokobanGame game) {
        this.game = game;
    }

    public void setOnLevelCompleteListener(OnLevelCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        int rows = game.getRows();
        int cols = game.getCols();
        float cellW = w / (float) cols;
        float cellH = h / (float) rows;
        cellSize = Math.min(cellW, cellH);
        offsetX = (w - cellSize * cols) / 2;
        offsetY = (h - cellSize * rows) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        int rows = game.getRows();
        int cols = game.getCols();

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                int tile = game.getTile(x, y);

                if (tile == SokobanGame.WALL) {
                    canvas.drawRect(left, top, right, bottom, wallPaint);
                } else {
                    canvas.drawRect(left, top, right, bottom, floorPaint);

                    if (tile == SokobanGame.TARGET || tile == SokobanGame.BOX_ON_TARGET || tile == SokobanGame.PLAYER_ON_TARGET) {
                        float margin = cellSize * 0.15f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, targetPaint);
                    }

                    if (tile == SokobanGame.BOX) {
                        float margin = cellSize * 0.1f;
                        canvas.drawRoundRect(new RectF(left + margin, top + margin, right - margin, bottom - margin), 8, 8, boxPaint);
                    } else if (tile == SokobanGame.BOX_ON_TARGET) {
                        float margin = cellSize * 0.1f;
                        canvas.drawRoundRect(new RectF(left + margin, top + margin, right - margin, bottom - margin), 8, 8, boxOnTargetPaint);
                    } else if (tile == SokobanGame.PLAYER) {
                        float margin = cellSize * 0.15f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, playerPaint);
                    } else if (tile == SokobanGame.PLAYER_ON_TARGET) {
                        float margin = cellSize * 0.15f;
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 2 - margin, playerOnTargetPaint);
                    }
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                if (Math.max(absDx, absDy) > 50) {
                    if (absDx > absDy) {
                        game.move(dx > 0 ? 1 : -1, 0);
                    } else {
                        game.move(0, dy > 0 ? 1 : -1);
                    }

                    if (game.isLevelComplete() && listener != null) {
                        listener.onComplete();
                    }
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}