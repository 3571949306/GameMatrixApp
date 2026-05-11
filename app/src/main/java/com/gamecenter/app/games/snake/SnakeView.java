package com.gamecenter.app.games.snake;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.gamecenter.app.R;
import java.util.List;

public class SnakeView extends View {

    private SnakeGame game;
    private int cellSize;
    private float offsetX, offsetY;

    private Paint bgPaint, gridPaint, snakeHeadPaint, snakeBodyPaint;
    private Paint snakeBorderPaint, foodPaint, foodBorderPaint, textPaint;

    private int snakeColorHead;
    private int snakeColorBody;
    private int foodColor;
    private int gridColor;
    private int boardBgColor;

    public SnakeView(Context context) {
        super(context);
        init();
    }

    public SnakeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Resources res = getResources();
        boardBgColor = res.getColor(R.color.snake_bg, null);
        gridColor = res.getColor(R.color.snake_grid, null);
        snakeColorHead = res.getColor(R.color.snake_head, null);
        snakeColorBody = res.getColor(R.color.snake_body, null);
        foodColor = res.getColor(R.color.snake_food, null);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(boardBgColor);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        snakeHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeHeadPaint.setColor(snakeColorHead);

        snakeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeBodyPaint.setColor(snakeColorBody);

        snakeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        snakeBorderPaint.setStyle(Paint.Style.STROKE);
        snakeBorderPaint.setColor(Color.rgb(50, 50, 50));
        snakeBorderPaint.setStrokeWidth(2f);

        foodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foodPaint.setColor(foodColor);

        foodBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foodBorderPaint.setStyle(Paint.Style.STROKE);
        foodBorderPaint.setColor(Color.rgb(180, 50, 50));
        foodBorderPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(SnakeGame game) {
        this.game = game;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    private void recalcDimensions(int w, int h) {
        int cellW = w / (SnakeGame.COLS + 2);
        int cellH = h / (SnakeGame.ROWS + 2);
        cellSize = Math.min(cellW, cellH);
        int boardW = SnakeGame.COLS * cellSize;
        int boardH = SnakeGame.ROWS * cellSize;
        offsetX = (w - boardW) / 2f;
        offsetY = (h - boardH) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) recalcDimensions(getWidth(), getHeight());

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        drawGrid(canvas);
        drawFood(canvas);
        drawSnake(canvas);

        if (game != null && game.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    private void drawGrid(Canvas canvas) {
        for (int y = 0; y <= SnakeGame.ROWS; y++) {
            float y1 = offsetY + y * cellSize;
            canvas.drawLine(offsetX, y1, offsetX + SnakeGame.COLS * cellSize, y1, gridPaint);
        }
        for (int x = 0; x <= SnakeGame.COLS; x++) {
            float x1 = offsetX + x * cellSize;
            canvas.drawLine(x1, offsetY, x1, offsetY + SnakeGame.ROWS * cellSize, gridPaint);
        }
    }

    private void drawFood(Canvas canvas) {
        if (game == null) return;
        int[] food = game.getFood();
        float cx = offsetX + food[0] * cellSize + cellSize / 2f;
        float cy = offsetY + food[1] * cellSize + cellSize / 2f;
        float radius = cellSize * 0.4f;
        canvas.drawCircle(cx, cy, radius, foodPaint);
        canvas.drawCircle(cx, cy, radius, foodBorderPaint);
    }

    private void drawSnake(Canvas canvas) {
        if (game == null) return;
        List<int[]> snake = game.getSnake();
        if (snake == null || snake.isEmpty()) return;

        for (int i = snake.size() - 1; i >= 0; i--) {
            int[] segment = snake.get(i);
            float left = offsetX + segment[0] * cellSize + 2;
            float top = offsetY + segment[1] * cellSize + 2;
            float right = offsetX + (segment[0] + 1) * cellSize - 2;
            float bottom = offsetY + (segment[1] + 1) * cellSize - 2;

            Paint paint = (i == 0) ? snakeHeadPaint : snakeBodyPaint;
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, 8, 8, paint);
            canvas.drawRoundRect(rect, 8, 8, snakeBorderPaint);
        }
    }

    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint();
        overlay.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        int score = game.getScore();
        canvas.drawText("游戏结束", getWidth() / 2f, getHeight() / 2f - 40, textPaint);
        textPaint.setTextSize(36);
        canvas.drawText("得分: " + score, getWidth() / 2f, getHeight() / 2f + 20, textPaint);
        textPaint.setTextSize(28);
        canvas.drawText("点击重新开始", getWidth() / 2f, getHeight() / 2f + 70, textPaint);
        textPaint.setTextSize(48);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}