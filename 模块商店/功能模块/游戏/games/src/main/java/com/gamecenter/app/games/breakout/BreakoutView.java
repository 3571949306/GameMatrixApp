package com.gamecenter.app.games.breakout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Random;

/**
 * 打砖块游戏的自定义视图，包含游戏逻辑和渲染。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理球、挡板和砖块的状态与碰撞逻辑</li>
 *   <li>通过 onDraw 绘制所有游戏元素</li>
 *   <li>通过 postDelayed 实现游戏主循环（约 30fps）</li>
 *   <li>通过 OnGameStateListener 回调通知 Activity 状态变化</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>游戏逻辑与渲染合并在同一个 View 中（与 Brotato 的分离架构不同），
 *       因为打砖块逻辑较简单，无需拆分</li>
 *   <li>球与砖块碰撞使用 RectF.contains() 检测，简化实现</li>
 *   <li>挡板碰撞时根据击球位置调整水平速度，增加可控性</li>
 * </ul>
 */
public class BreakoutView extends View {

    /** 挡板中心 X 坐标（像素） */
    private int paddleX;

    /** 球心 X 坐标（像素） */
    private int ballX, ballY;

    /** 球的 X/Y 方向速度（像素/帧） */
    private int ballDX, ballDY;

    /** 当前得分 */
    private int score = 0;

    /** 剩余生命数 */
    private int lives = 3;

    /** 游戏是否正在运行 */
    private boolean gameRunning = false;

    /** 游戏是否结束（失败） */
    private boolean gameOver = false;

    /** 游戏是否通关 */
    private boolean gameWon = false;

    /** 砖块矩形数组，null 表示已被击碎 */
    private RectF[] bricks;

    /** 每个砖块对应的颜色 */
    private int[] brickColors;

    /** 砖块行数 */
    private int rows = 5;

    /** 砖块列数 */
    private int cols = 7;

    /** 通用画笔（复用以减少对象创建） */
    private Paint paint;

    /** 随机数生成器，用于球的重置方向 */
    private Random random;

    /** 游戏状态变化监听器 */
    private OnGameStateListener listener;

    /**
     * 游戏状态变化监听接口。
     * <p>
     * Activity 实现此接口以接收游戏状态更新通知。
     */
    public interface OnGameStateListener {
        /** 分数变化时回调 */
        void onScoreChanged(int score);
        /** 生命值变化时回调 */
        void onLivesChanged(int lives);
        /** 游戏失败时回调 */
        void onGameOver(int score);
        /** 游戏通关时回调 */
        void onGameWon(int score);
    }

    public BreakoutView(Context context) {
        super(context);
        init();
    }

    public BreakoutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔和随机数生成器。
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();
    }

    /**
     * 设置游戏状态变化监听器。
     *
     * @param listener 监听器实现
     */
    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetGame();
    }

    /**
     * 重置游戏到初始状态。
     * <p>
     * 重置球位置和速度、分数、生命值，重新创建砖块，
     * 并通知监听器更新 UI。
     */
    public void resetGame() {
        int w = getWidth();
        int h = getHeight();
        paddleX = w / 2;
        ballX = w / 2;
        ballY = h - 100;
        ballDX = 8;
        ballDY = -8;
        score = 0;
        lives = 3;
        gameRunning = true;
        gameOver = false;
        gameWon = false;
        createBricks();
        if (listener != null) {
            listener.onScoreChanged(score);
            listener.onLivesChanged(lives);
        }
        invalidate();
    }

    /**
     * 创建砖块阵列。
     * <p>
     * 砖块按行列排列，每行使用不同颜色（循环使用 10 种颜色）。
     * 砖块之间有 10px 间距，顶部留 100px 偏移。
     */
    private void createBricks() {
        int w = getWidth();
        int h = getHeight();
        int brickWidth = (w - 40) / cols;
        int brickHeight = 50;
        int padding = 10;

        bricks = new RectF[rows * cols];
        brickColors = new int[rows * cols];

        int[] colors = {
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
            0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
            0xFF03A9F4, 0xFF00BCD4, 0xFF009688,
            0xFF4CAF50
        };

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                bricks[idx] = new RectF(
                    20 + c * brickWidth,
                    100 + r * (brickHeight + padding),
                    20 + c * brickWidth + brickWidth,
                    100 + r * (brickHeight + padding) + brickHeight
                );
                brickColors[idx] = colors[r % colors.length];
            }
        }
    }

    /**
     * 主绘制方法，按层次绘制背景、砖块、挡板、球和游戏结束/通关覆盖层。
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // 深色背景
        paint.setColor(0xFF212121);
        canvas.drawRect(0, 0, w, h, paint);

        // 绘制砖块（圆角矩形 + 白色描边）
        for (int i = 0; i < bricks.length; i++) {
            if (bricks[i] != null) {
                paint.setColor(brickColors[i]);
                canvas.drawRoundRect(bricks[i], 8, 8, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                canvas.drawRoundRect(bricks[i], 8, 8, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        // 绘制挡板（绿色矩形）
        paint.setColor(0xFF4CAF50);
        canvas.drawRect(paddleX - 60, h - 60, paddleX + 60, h - 40, paint);

        // 绘制球（白色圆形）
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(ballX, ballY, 12, paint);

        // 游戏结束覆盖层
        if (gameOver) {
            paint.setColor(0xCC000000);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(60);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("游戏结束", w / 2f, h / 2f - 40, paint);
            paint.setTextSize(30);
            canvas.drawText("得分: " + score, w / 2f, h / 2f + 20, paint);
        } else if (gameWon) {
            // 通关覆盖层
            paint.setColor(0xCC000000);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setColor(0xFF4CAF50);
            paint.setTextSize(60);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("恭喜通关!", w / 2f, h / 2f - 40, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(30);
            canvas.drawText("得分: " + score, w / 2f, h / 2f + 20, paint);
        }
    }

    /**
     * 启动游戏循环。
     * <p>
     * 重置游戏状态并开始以 30ms 间隔执行的游戏循环。
     */
    public void startGame() {
        gameRunning = true;
        gameOver = false;
        gameWon = false;
        gameLoop();
    }

    /**
     * 游戏主循环，每帧执行一次。
     * <p>
     * 更新球位置，处理墙壁反弹、挡板碰撞、球出界、砖块碰撞，
     * 然后触发重绘并调度下一帧。
     */
    private void gameLoop() {
        if (!gameRunning) return;

        int w = getWidth();
        int h = getHeight();

        // 更新球位置
        ballX += ballDX;
        ballY += ballDY;

        // 左右墙壁反弹
        if (ballX <= 12 || ballX >= w - 12) ballDX = -ballDX;
        // 顶部墙壁反弹
        if (ballY <= 12) ballDY = -ballDY;

        // 挡板碰撞检测：球在挡板范围内时反弹
        if (ballY >= h - 60 && ballX >= paddleX - 60 && ballX <= paddleX + 60) {
            ballDY = -Math.abs(ballDY);
            // 根据击球位置调整水平速度，偏心击球产生角度变化
            ballDX = (ballX - paddleX) / 3;
        }

        // 球出界（底部）
        if (ballY > h) {
            lives--;
            if (listener != null) listener.onLivesChanged(lives);
            if (lives <= 0) {
                // 生命耗尽，游戏结束
                gameRunning = false;
                gameOver = true;
                if (listener != null) listener.onGameOver(score);
            } else {
                // 重置球位置，随机水平方向
                ballX = w / 2;
                ballY = h - 100;
                ballDX = 8 * (random.nextBoolean() ? 1 : -1);
                ballDY = -8;
            }
        }

        // 砖块碰撞检测
        for (int i = 0; i < bricks.length; i++) {
            if (bricks[i] != null && bricks[i].contains(ballX, ballY)) {
                bricks[i] = null;
                score += 10;
                if (listener != null) listener.onScoreChanged(score);
                ballDY = -ballDY;

                // 检查是否所有砖块都被击碎（通关条件）
                boolean allDestroyed = true;
                for (RectF brick : bricks) {
                    if (brick != null) {
                        allDestroyed = false;
                        break;
                    }
                }
                if (allDestroyed) {
                    gameRunning = false;
                    gameWon = true;
                    if (listener != null) listener.onGameWon(score);
                }
                break;
            }
        }

        invalidate();
        // 约 33fps 的循环间隔
        postDelayed(this::gameLoop, 30);
    }

    /**
     * 处理触摸事件，移动挡板位置。
     * <p>
     * 挡板中心跟随手指 X 坐标，并限制在视图范围内。
     *
     * @param event 触摸事件
     * @return 始终返回 true 以消费事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            paddleX = (int) event.getX();
            // 限制挡板不超出视图边界
            paddleX = Math.max(60, Math.min(getWidth() - 60, paddleX));
            invalidate();
        }
        return true;
    }
}
