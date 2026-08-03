package com.gamecenter.app.flappy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 飞翔的小鸟游戏自定义 View（独立 APK 模块版本）。
 *
 * <p>由宿主 com.gamecenter.app.games.flappy.FlappyView 迁移而来。
 * 移除了对宿主 R 资源的依赖，背景色支持浅色/深色主题。</p>
 *
 * <p>小鸟受重力影响下落，点击屏幕上升。需要穿过管道间隙得分。</p>
 *
 * <p>改进（相对旧版）：
 * <ul>
 *   <li>引入 {@code density}，把小鸟/管道/文字等"绝对像素"尺寸换算成与屏幕匹配的 dp 视觉尺寸，
 *       高密度屏上不再过小。</li>
 *   <li>新增 gameOver 状态并在 onDraw 中绘制"游戏结束"结算遮罩，死亡后不再整屏空白。</li>
 * </ul>
 * </p>
 */
public class FlappyView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score);
    }

    // ==================== 常量（mdpi 下的 dp 基准，运行时按 density 放大） ====================
    private static final float BIRD_SIZE = 24f;
    private static final float GRAVITY = 0.5f;
    private static final float JUMP_FORCE = -8f;
    private static final float PIPE_WIDTH = 60f;
    private float pipeGap = 180f;
    private float pipeSpeed = 3f;
    private static final float PIPE_INTERVAL = 300f;

    // ==================== 游戏状态 ====================
    private Paint paint;
    private float viewWidth;
    private float viewHeight;
    private float birdX;
    private float birdY;
    private float birdVelocity = 0;
    private int score = 0;
    private boolean gameRunning = false;
    private boolean gameOver = false;
    private boolean gamePaused = false;
    private boolean gameStarted = false;

    // 设备密度（px = dp * density），用于把"绝对像素"尺寸换算为与屏幕匹配的 dp 视觉尺寸
    private float density = 1f;
    private float birdSize;
    private float pipeWidth;

    // 管道列表：每根管道由 topHeight 和 xPos 表示
    private List<float[]> pipes = new ArrayList<>(); // [xPos, gapCenterY]
    private float nextPipeX;
    private Random random = new Random();
    private OnGameListener listener;

    // ==================== 构造方法 ====================

    public FlappyView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        density = getResources().getDisplayMetrics().density;
        birdSize = BIRD_SIZE * density;
        pipeWidth = PIPE_WIDTH * density;
        setBackgroundColor(isNightMode() ? 0xFF0D47A1 : 0xFF81D4FA);
    }

    private boolean isNightMode() {
        int nightMode = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    /**
     * 设置管道速度与间隙（由 Fragment 根据难度调用）。
     */
    public void setPipeConfig(float speed, float gap) {
        this.pipeSpeed = speed;
        this.pipeGap = gap;
    }

    // ==================== 游戏控制 ====================

    public void startGame() {
        this.score = 0;
        this.gameRunning = true;
        this.gameOver = false;
        this.gamePaused = false;
        this.gameStarted = false;
        initGame();
        invalidate();
    }

    public void pauseGame() {
        gamePaused = true;
    }

    public void resumeGame() {
        gamePaused = false;
    }

    public void stopGame() {
        gameRunning = false;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public int getScore() {
        return score;
    }

    // ==================== 初始化 ====================

    private void initGame() {
        birdX = viewWidth / 4;
        birdY = viewHeight / 2;
        birdVelocity = 0;
        pipes.clear();
        nextPipeX = viewWidth;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (gameRunning) {
            initGame();
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 游戏结束后绘制结算遮罩，避免整屏空白（重开由 Fragment 的按钮负责）
        if (gameOver) {
            drawGameOver(canvas);
            return;
        }
        if (!gameRunning) return;

        // 绘制云朵装饰
        paint.setColor(0xAAFFFFFF);
        canvas.drawOval(new RectF(viewWidth * 0.1f, viewHeight * 0.15f,
                viewWidth * 0.3f, viewHeight * 0.22f), paint);
        canvas.drawOval(new RectF(viewWidth * 0.6f, viewHeight * 0.08f,
                viewWidth * 0.85f, viewHeight * 0.15f), paint);

        // 绘制管道
        paint.setColor(0xFF4CAF50);
        for (float[] pipe : pipes) {
            float x = pipe[0];
            float gapCenter = pipe[1];

            // 上管道
            canvas.drawRect(x, 0, x + pipeWidth, gapCenter - pipeGap / 2, paint);
            // 下管道
            canvas.drawRect(x, gapCenter + pipeGap / 2, x + pipeWidth, viewHeight, paint);

            // 管道边缘装饰
            paint.setColor(0xFF388E3C);
            canvas.drawRect(x - 4 * density, gapCenter - pipeGap / 2 - 16 * density,
                    x + pipeWidth + 4 * density, gapCenter - pipeGap / 2, paint);
            canvas.drawRect(x - 4 * density, gapCenter + pipeGap / 2,
                    x + pipeWidth + 4 * density, gapCenter + pipeGap / 2 + 16 * density, paint);
            paint.setColor(0xFF4CAF50);
        }

        // 绘制小鸟
        paint.setColor(0xFFFFEB3B);
        canvas.drawCircle(birdX, birdY, birdSize, paint);

        // 眼睛
        paint.setColor(Color.WHITE);
        canvas.drawCircle(birdX + 8 * density, birdY - 6 * density, 8 * density, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(birdX + 10 * density, birdY - 6 * density, 4 * density, paint);

        // 嘴巴
        paint.setColor(0xFFFF9800);
        canvas.drawRect(birdX + birdSize, birdY - 2 * density,
                birdX + birdSize + 12 * density, birdY + 4 * density, paint);

        // 绘制分数
        paint.setColor(Color.WHITE);
        paint.setTextSize(48 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(score), viewWidth / 2, 70 * density, paint);

        // 未开始时显示提示
        if (!gameStarted) {
            paint.setTextSize(24 * density);
            canvas.drawText("点击屏幕开始飞翔！", viewWidth / 2, viewHeight / 2 + 80 * density, paint);
        }
    }

    /** 游戏结束结算遮罩（死亡后不再空白）。重开逻辑由 Fragment 的"重新开始"按钮负责。 */
    private void drawGameOver(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(170, 0, 0, 0));
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(30 * density);
        canvas.drawText("游戏结束", viewWidth / 2f, viewHeight / 2f - 30 * density, paint);
        paint.setTextSize(18 * density);
        canvas.drawText("得分 " + score, viewWidth / 2f, viewHeight / 2f + 10 * density, paint);
        paint.setTextSize(15 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText("点击下方按钮重新开始", viewWidth / 2f, viewHeight / 2f + 50 * density, paint);
    }

    // ==================== 游戏循环 ====================

    public void update() {
        if (!gameRunning || gamePaused || !gameStarted) return;

        // 物理更新
        birdVelocity += GRAVITY;
        birdY += birdVelocity;

        // 管道移动
        for (int i = pipes.size() - 1; i >= 0; i--) {
            pipes.get(i)[0] -= pipeSpeed;
            if (pipes.get(i)[0] + pipeWidth < 0) {
                pipes.remove(i);
            }
        }

        // 生成新管道
        nextPipeX -= pipeSpeed;
        if (nextPipeX <= viewWidth) {
            float gapCenter = pipeGap / 2 + random.nextFloat() * (viewHeight - pipeGap - 100);
            pipes.add(new float[]{viewWidth, gapCenter});
            nextPipeX = viewWidth + PIPE_INTERVAL;
        }

        // 得分判定
        for (float[] pipe : pipes) {
            if (pipe[0] + pipeWidth < birdX && pipe[0] + pipeWidth + pipeSpeed >= birdX) {
                score++;
                if (listener != null) {
                    listener.onScoreChanged(score);
                }
            }
        }

        // 碰撞检测 - 地面/天花板
        if (birdY - birdSize <= 0 || birdY + birdSize >= viewHeight) {
            gameOver();
            return;
        }

        // 碰撞检测 - 管道
        for (float[] pipe : pipes) {
            float x = pipe[0];
            float gapCenter = pipe[1];
            float topPipeBottom = gapCenter - pipeGap / 2;
            float bottomPipeTop = gapCenter + pipeGap / 2;

            if (birdX + birdSize > x && birdX - birdSize < x + pipeWidth) {
                if (birdY - birdSize < topPipeBottom || birdY + birdSize > bottomPipeTop) {
                    gameOver();
                    return;
                }
            }
        }

        invalidate();
    }

    private void gameOver() {
        gameRunning = false;
        gameOver = true;
        if (listener != null) {
            listener.onGameOver(score);
        }
        invalidate();
    }

    public void jump() {
        if (!gameRunning || gamePaused) return;
        if (!gameStarted) {
            gameStarted = true;
        }
        birdVelocity = JUMP_FORCE;
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            jump();
        }
        return true;
    }
}
