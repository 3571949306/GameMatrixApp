package com.gamecenter.app.games.snake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇游戏视图。
 *
 * <p>Canvas 绘制贪吃蛇游戏，使用定时器驱动蛇的移动。
 * 支持手势滑动控制方向，实时绘制蛇身、食物和网格。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 Handler + Runnable 实现定时器驱动的 game loop</li>
 *   <li>手势检测使用 GestureDetector，支持上下左右滑动</li>
 *   <li>蛇身使用 List&lt;Point&gt; 存储，头部为 index 0</li>
 *   <li>护眼主题：深色背景 + 绿色蛇身 + 红色食物</li>
 *   <li>速度通过 difficultyFactor 调节（0.3-1.0）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class SnakeView extends View {

    // ==================== 常量 ====================

    /** 网格列数 */
    private static final int GRID_COLS = 20;

    /** 网格行数 */
    private static final int GRID_ROWS = 30;

    /** 基础速度间隔（毫秒） */
    private static final int BASE_SPEED_MS = 200;

    // ==================== 方向 ====================

    private static final int DIR_UP = 0;
    private static final int DIR_RIGHT = 1;
    private static final int DIR_DOWN = 2;
    private static final int DIR_LEFT = 3;

    // ==================== 颜色 ====================

    private static final int COLOR_BG = 0xFF1A1A2E;
    private static final int COLOR_GRID = 0xFF2A2A4E;
    private static final int COLOR_SNAKE_HEAD = 0xFF4CAF50;
    private static final int COLOR_SNAKE_BODY = 0xFF81C784;
    private static final int COLOR_FOOD = 0xFFF44336;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_OVERLAY = 0xCC000000;

    // ==================== 回调接口 ====================

    /** 分数变化监听器 */
    public interface OnScoreChangeListener {
        void onScoreChanged(int score);
    }

    /** 游戏结束监听器 */
    public interface OnGameOverListener {
        void onGameOver(int finalScore);
    }

    /** 食物被吃监听器 */
    public interface OnFoodEatenListener {
        void onFoodEaten(int snakeLength);
    }

    // ==================== 成员变量 ====================

    /** 蛇身坐标列表（头部为 index 0） */
    private final List<Point> snake = new ArrayList<>();

    /** 食物坐标 */
    private Point food;

    /** 当前方向 */
    private int direction = DIR_RIGHT;

    /** 下一个方向（防止同一帧内多次转向） */
    private int nextDirection = DIR_RIGHT;

    /** 当前分数 */
    private int score = 0;

    /** 游戏是否运行中 */
    private boolean running = false;

    /** 游戏是否结束 */
    private boolean gameOver = false;

    /** 速度因子（0.3-1.0） */
    private float speedFactor = 0.5f;

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 定时器 Handler */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 游戏循环 Runnable */
    private Runnable gameLoopRunnable;

    /** 手势检测器 */
    private GestureDetector gestureDetector;

    // ==================== 绘制工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintGrid = new Paint();
    private final Paint paintSnakeHead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSnakeBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFood = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOverlay = new Paint();

    // ==================== 监听器 ====================

    private OnScoreChangeListener scoreChangeListener;
    private OnGameOverListener gameOverListener;
    private OnFoodEatenListener foodEatenListener;

    // ==================== 构造函数 ====================

    public SnakeView(@NonNull Context context) {
        super(context);
        init();
    }

    public SnakeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SnakeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // ==================== 初始化 ====================

    private void init() {
        // 背景
        paintBg.setColor(COLOR_BG);
        paintBg.setStyle(Paint.Style.FILL);

        // 网格线
        paintGrid.setColor(COLOR_GRID);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f);

        // 蛇头
        paintSnakeHead.setColor(COLOR_SNAKE_HEAD);
        paintSnakeHead.setStyle(Paint.Style.FILL);

        // 蛇身
        paintSnakeBody.setColor(COLOR_SNAKE_BODY);
        paintSnakeBody.setStyle(Paint.Style.FILL);

        // 食物
        paintFood.setColor(COLOR_FOOD);
        paintFood.setStyle(Paint.Style.FILL);

        // 文字
        paintText.setColor(COLOR_TEXT);
        paintText.setTextAlign(Paint.Align.CENTER);

        // 遮罩
        paintOverlay.setColor(COLOR_OVERLAY);
        paintOverlay.setStyle(Paint.Style.FILL);

        // 手势检测
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dx) > Math.abs(dy)) {
                    // 水平滑动
                    if (dx > 0 && direction != DIR_LEFT) {
                        nextDirection = DIR_RIGHT;
                    } else if (dx < 0 && direction != DIR_RIGHT) {
                        nextDirection = DIR_LEFT;
                    }
                } else {
                    // 垂直滑动
                    if (dy > 0 && direction != DIR_UP) {
                        nextDirection = DIR_DOWN;
                    } else if (dy < 0 && direction != DIR_DOWN) {
                        nextDirection = DIR_UP;
                    }
                }
                return true;
            }
        });
    }

    // ==================== 监听器设置 ====================

    public void setOnScoreChangeListener(OnScoreChangeListener listener) {
        this.scoreChangeListener = listener;
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    public void setOnFoodEatenListener(OnFoodEatenListener listener) {
        this.foodEatenListener = listener;
    }

    public void setSpeedFactor(float factor) {
        this.speedFactor = Math.max(0.1f, Math.min(1.0f, factor));
    }

    // ==================== 游戏控制 ====================

    /**
     * 开始游戏
     */
    public void startGame() {
        // 重置状态
        snake.clear();
        score = 0;
        direction = DIR_RIGHT;
        nextDirection = DIR_RIGHT;
        gameOver = false;
        running = true;

        // 初始化蛇（从中间开始，长度 3）
        int startX = GRID_COLS / 2;
        int startY = GRID_ROWS / 2;
        snake.add(new Point(startX, startY));
        snake.add(new Point(startX - 1, startY));
        snake.add(new Point(startX - 2, startY));

        // 生成食物
        spawnFood();

        // 通知分数
        if (scoreChangeListener != null) {
            scoreChangeListener.onScoreChanged(score);
        }

        // 启动游戏循环
        scheduleNextTick();
        invalidate();
    }

    /**
     * 暂停游戏
     */
    public void pauseGame() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * 恢复游戏
     */
    public void resumeGame() {
        if (!gameOver) {
            running = true;
            scheduleNextTick();
        }
    }

    /**
     * 停止游戏
     */
    public void stopGame() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    // ==================== 游戏循环 ====================

    /**
     * 安排下一次 tick
     */
    private void scheduleNextTick() {
        if (!running || gameOver) return;
        handler.removeCallbacksAndMessages(null);
        int delay = (int) (BASE_SPEED_MS * (1.0f - speedFactor * 0.6f));
        delay = Math.max(50, delay);
        gameLoopRunnable = this::tick;
        handler.postDelayed(gameLoopRunnable, delay);
    }

    /**
     * 游戏逻辑更新
     */
    private void tick() {
        if (!running || gameOver) return;

        // 应用方向
        direction = nextDirection;

        // 计算新头部位置
        Point head = snake.get(0);
        int newX = head.x;
        int newY = head.y;

        switch (direction) {
            case DIR_UP:    newY--; break;
            case DIR_DOWN:  newY++; break;
            case DIR_LEFT:  newX--; break;
            case DIR_RIGHT: newX++; break;
        }

        // 边界检测
        if (newX < 0 || newX >= GRID_COLS || newY < 0 || newY >= GRID_ROWS) {
            onGameOver();
            return;
        }

        // 自身碰撞检测
        Point newHead = new Point(newX, newY);
        for (Point segment : snake) {
            if (segment.equals(newHead.x, newHead.y)) {
                onGameOver();
                return;
            }
        }

        // 移动蛇
        snake.add(0, newHead);

        // 检查是否吃到食物
        if (newHead.equals(food.x, food.y)) {
            score += 10;
            if (scoreChangeListener != null) {
                scoreChangeListener.onScoreChanged(score);
            }
            if (foodEatenListener != null) {
                foodEatenListener.onFoodEaten(snake.size());
            }
            spawnFood();
        } else {
            // 没吃到食物，移除尾部
            snake.remove(snake.size() - 1);
        }

        invalidate();
        scheduleNextTick();
    }

    /**
     * 游戏结束
     */
    private void onGameOver() {
        gameOver = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        invalidate();

        if (gameOverListener != null) {
            gameOverListener.onGameOver(score);
        }
    }

    /**
     * 生成食物（避免生成在蛇身上）
     */
    private void spawnFood() {
        List<Point> emptyCells = new ArrayList<>();
        for (int x = 0; x < GRID_COLS; x++) {
            for (int y = 0; y < GRID_ROWS; y++) {
                boolean occupied = false;
                for (Point segment : snake) {
                    if (segment.x == x && segment.y == y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    emptyCells.add(new Point(x, y));
                }
            }
        }
        if (!emptyCells.isEmpty()) {
            food = emptyCells.get(random.nextInt(emptyCells.size()));
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        // 计算单元格大小
        float cellW = (float) viewWidth / GRID_COLS;
        float cellH = (float) viewHeight / GRID_ROWS;
        float cellSize = Math.min(cellW, cellH);

        // 计算偏移量（居中）
        float offsetX = (viewWidth - cellSize * GRID_COLS) / 2f;
        float offsetY = (viewHeight - cellSize * GRID_ROWS) / 2f;

        // 绘制背景
        canvas.drawRect(0, 0, viewWidth, viewHeight, paintBg);

        // 绘制网格线
        for (int i = 0; i <= GRID_COLS; i++) {
            float x = offsetX + i * cellSize;
            canvas.drawLine(x, offsetY, x, offsetY + GRID_ROWS * cellSize, paintGrid);
        }
        for (int i = 0; i <= GRID_ROWS; i++) {
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + GRID_COLS * cellSize, y, paintGrid);
        }

        // 绘制食物
        if (food != null) {
            float fx = offsetX + food.x * cellSize;
            float fy = offsetY + food.y * cellSize;
            float padding = cellSize * 0.15f;
            canvas.drawRoundRect(
                    new RectF(fx + padding, fy + padding, fx + cellSize - padding, fy + cellSize - padding),
                    cellSize * 0.3f, cellSize * 0.3f, paintFood);
        }

        // 绘制蛇身
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

        // 绘制分数
        paintText.setTextSize(cellSize * 1.2f);
        canvas.drawText("分数: " + score, viewWidth / 2f, offsetY - 10, paintText);

        // 游戏结束覆盖层
        if (gameOver) {
            canvas.drawRect(0, 0, viewWidth, viewHeight, paintOverlay);
            paintText.setTextSize(cellSize * 2f);
            canvas.drawText("游戏结束", viewWidth / 2f, viewHeight / 2f - cellSize, paintText);
            paintText.setTextSize(cellSize * 1.2f);
            canvas.drawText("最终分数: " + score, viewWidth / 2f, viewHeight / 2f + cellSize, paintText);
            canvas.drawText("点击重新开始", viewWidth / 2f, viewHeight / 2f + cellSize * 3, paintText);
        }
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver && event.getAction() == MotionEvent.ACTION_DOWN) {
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
