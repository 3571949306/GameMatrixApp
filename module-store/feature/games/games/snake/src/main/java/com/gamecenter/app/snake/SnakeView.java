package com.gamecenter.app.snake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 贪吃蛇游戏视图（模块独立版本）。
 *
 * <p>渲染由本类负责，游戏逻辑委托给 {@link SnakeGame}。
 * 手势滑动控制方向，定时器驱动蛇的移动。</p>
 */
public class SnakeView extends View {

    private static final int BASE_SPEED_MS = 200;

    private static final int COLOR_BG = 0xFF1A1A2E;
    private static final int COLOR_GRID = 0xFF2A2A4E;
    private static final int COLOR_SNAKE_HEAD = 0xFF4CAF50;
    private static final int COLOR_SNAKE_BODY = 0xFF81C784;
    private static final int COLOR_FOOD = 0xFFF44336;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_OVERLAY = 0xCC000000;

    public interface OnScoreChangeListener { void onScoreChanged(int score); }
    public interface OnGameOverListener { void onGameOver(int finalScore); }
    public interface OnFoodEatenListener { void onFoodEaten(int snakeLength); }

    private final SnakeGame game = new SnakeGame();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable gameLoopRunnable;
    private GestureDetector gestureDetector;
    private float speedFactor = 0.5f;

    private final Paint paintBg = new Paint();
    private final Paint paintGrid = new Paint();
    private final Paint paintSnakeHead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSnakeBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFood = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOverlay = new Paint();

    private OnScoreChangeListener scoreChangeListener;
    private OnGameOverListener gameOverListener;
    private OnFoodEatenListener foodEatenListener;

    public SnakeView(@NonNull Context context) {
        super(context);
        init();
    }

    public SnakeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintBg.setColor(COLOR_BG);
        paintBg.setStyle(Paint.Style.FILL);

        paintGrid.setColor(COLOR_GRID);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f);

        paintSnakeHead.setColor(COLOR_SNAKE_HEAD);
        paintSnakeHead.setStyle(Paint.Style.FILL);

        paintSnakeBody.setColor(COLOR_SNAKE_BODY);
        paintSnakeBody.setStyle(Paint.Style.FILL);

        paintFood.setColor(COLOR_FOOD);
        paintFood.setStyle(Paint.Style.FILL);

        paintText.setColor(COLOR_TEXT);
        paintText.setTextAlign(Paint.Align.CENTER);

        paintOverlay.setColor(COLOR_OVERLAY);
        paintOverlay.setStyle(Paint.Style.FILL);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) game.setNextDirection(SnakeGame.DIR_RIGHT);
                    else if (dx < 0) game.setNextDirection(SnakeGame.DIR_LEFT);
                } else {
                    if (dy > 0) game.setNextDirection(SnakeGame.DIR_DOWN);
                    else if (dy < 0) game.setNextDirection(SnakeGame.DIR_UP);
                }
                return true;
            }
        });
    }

    public void setOnScoreChangeListener(OnScoreChangeListener l) { this.scoreChangeListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }
    public void setOnFoodEatenListener(OnFoodEatenListener l) { this.foodEatenListener = l; }

    public void setSpeedFactor(float factor) {
        this.speedFactor = Math.max(0.1f, Math.min(1.0f, factor));
    }

    public SnakeGame getGame() { return game; }

    public void startGame() {
        game.reset();
        if (scoreChangeListener != null) scoreChangeListener.onScoreChanged(game.getScore());
        scheduleNextTick();
        invalidate();
    }

    public void pauseGame() {
        game.pause();
        handler.removeCallbacksAndMessages(null);
    }

    public void resumeGame() {
        game.resume();
        scheduleNextTick();
    }

    public void stopGame() {
        game.stop();
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleNextTick() {
        if (!game.isRunning() || game.isGameOver()) return;
        handler.removeCallbacksAndMessages(null);
        int delay = (int) (BASE_SPEED_MS * (1.0f - speedFactor * 0.6f));
        delay = Math.max(50, delay);
        gameLoopRunnable = this::tick;
        handler.postDelayed(gameLoopRunnable, delay);
    }

    private void tick() {
        if (!game.isRunning() || game.isGameOver()) return;
        int result = game.tick();
        if (result == SnakeGame.TICK_ATE) {
            if (scoreChangeListener != null) scoreChangeListener.onScoreChanged(game.getScore());
            if (foodEatenListener != null) foodEatenListener.onFoodEaten(game.getSnake().size());
        } else if (result == SnakeGame.TICK_DIED) {
            invalidate();
            if (gameOverListener != null) gameOverListener.onGameOver(game.getScore());
            return;
        }
        invalidate();
        scheduleNextTick();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        int cols = SnakeGame.GRID_COLS;
        int rows = SnakeGame.GRID_ROWS;
        float cellW = (float) viewWidth / cols;
        float cellH = (float) viewHeight / rows;
        float cellSize = Math.min(cellW, cellH);
        float offsetX = (viewWidth - cellSize * cols) / 2f;
        float offsetY = (viewHeight - cellSize * rows) / 2f;

        canvas.drawRect(0, 0, viewWidth, viewHeight, paintBg);

        for (int i = 0; i <= cols; i++) {
            float x = offsetX + i * cellSize;
            canvas.drawLine(x, offsetY, x, offsetY + rows * cellSize, paintGrid);
        }
        for (int i = 0; i <= rows; i++) {
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + cols * cellSize, y, paintGrid);
        }

        Point food = game.getFood();
        if (food != null) {
            float fx = offsetX + food.x * cellSize;
            float fy = offsetY + food.y * cellSize;
            float padding = cellSize * 0.15f;
            canvas.drawRoundRect(
                    new RectF(fx + padding, fy + padding, fx + cellSize - padding, fy + cellSize - padding),
                    cellSize * 0.3f, cellSize * 0.3f, paintFood);
        }

        List<Point> snake = game.getSnake();
        for (int i = snake.size() - 1; i >= 0; i--) {
            Point p = snake.get(i);
            float px = offsetX + p.x * cellSize;
            float py = offsetY + p.y * cellSize;
            float padding = cellSize * 0.05f;
            Paint paint = (i == 0) ? paintSnakeHead : paintSnakeBody;
            float radius = (i == 0) ? cellSize * 0.2f : cellSize * 0.15f;
            canvas.drawRoundRect(
                    new RectF(px + padding, py + padding, px + cellSize - padding, py + cellSize - padding),
                    radius, radius, paint);
        }

        paintText.setTextSize(cellSize * 1.2f);
        canvas.drawText("分数: " + game.getScore(), viewWidth / 2f, offsetY - 10, paintText);

        if (game.isGameOver()) {
            canvas.drawRect(0, 0, viewWidth, viewHeight, paintOverlay);
            paintText.setTextSize(cellSize * 2f);
            canvas.drawText("游戏结束!", viewWidth / 2f, viewHeight / 2f - cellSize, paintText);
            paintText.setTextSize(cellSize * 1.2f);
            canvas.drawText("最终分数: " + game.getScore(), viewWidth / 2f, viewHeight / 2f + cellSize, paintText);
            canvas.drawText("点击屏幕重新开始", viewWidth / 2f, viewHeight / 2f + cellSize * 3, paintText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game.isGameOver() && event.getAction() == MotionEvent.ACTION_DOWN) {
            startGame();
            return true;
        }
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }
}
