package com.gamecenter.app.games.flappy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 飞翔的小鸟游戏自定义 View。
 *
 * <p>小鸟受重力影响下落，点击屏幕上升。需要穿过管道间隙得分。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class FlappyView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score);
    }

    // ==================== 常量 ====================
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
    private boolean gamePaused = false;
    private boolean gameStarted = false;

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
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.game_flappy_color_bg));
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    /**
     * 设置管道速度与间隙（由 Activity 根据难度调用）。
     */
    public void setPipeConfig(float speed, float gap) {
        this.pipeSpeed = speed;
        this.pipeGap = gap;
    }

    // ==================== 游戏控制 ====================

    public void startGame() {
        this.score = 0;
        this.gameRunning = true;
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
            canvas.drawRect(x, 0, x + PIPE_WIDTH, gapCenter - pipeGap / 2, paint);
            // 下管道
            canvas.drawRect(x, gapCenter + pipeGap / 2, x + PIPE_WIDTH, viewHeight, paint);

            // 管道边缘装饰
            paint.setColor(0xFF388E3C);
            canvas.drawRect(x - 4, gapCenter - pipeGap / 2 - 16, x + PIPE_WIDTH + 4, gapCenter - pipeGap / 2, paint);
            canvas.drawRect(x - 4, gapCenter + pipeGap / 2, x + PIPE_WIDTH + 4, gapCenter + pipeGap / 2 + 16, paint);
            paint.setColor(0xFF4CAF50);
        }

        // 绘制小鸟
        paint.setColor(0xFFFFEB3B);
        canvas.drawCircle(birdX, birdY, BIRD_SIZE, paint);

        // 眼睛
        paint.setColor(Color.WHITE);
        canvas.drawCircle(birdX + 8, birdY - 6, 8, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(birdX + 10, birdY - 6, 4, paint);

        // 嘴巴
        paint.setColor(0xFFFF9800);
        canvas.drawRect(birdX + BIRD_SIZE, birdY - 2, birdX + BIRD_SIZE + 12, birdY + 4, paint);

        // 绘制分数
        paint.setColor(Color.WHITE);
        paint.setTextSize(48);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(score), viewWidth / 2, 80, paint);

        // 未开始时显示提示
        if (!gameStarted) {
            paint.setTextSize(24);
            canvas.drawText(getContext().getString(R.string.game_flappy_tap_to_start), viewWidth / 2, viewHeight / 2 + 80, paint);
        }
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
            if (pipes.get(i)[0] + PIPE_WIDTH < 0) {
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
            if (pipe[0] + PIPE_WIDTH < birdX && pipe[0] + PIPE_WIDTH + pipeSpeed >= birdX) {
                score++;
                if (listener != null) {
                    listener.onScoreChanged(score);
                }
            }
        }

        // 碰撞检测 - 地面/天花板
        if (birdY - BIRD_SIZE <= 0 || birdY + BIRD_SIZE >= viewHeight) {
            gameOver();
            return;
        }

        // 碰撞检测 - 管道
        for (float[] pipe : pipes) {
            float x = pipe[0];
            float gapCenter = pipe[1];
            float topPipeBottom = gapCenter - pipeGap / 2;
            float bottomPipeTop = gapCenter + pipeGap / 2;

            if (birdX + BIRD_SIZE > x && birdX - BIRD_SIZE < x + PIPE_WIDTH) {
                if (birdY - BIRD_SIZE < topPipeBottom || birdY + BIRD_SIZE > bottomPipeTop) {
                    gameOver();
                    return;
                }
            }
        }

        invalidate();
    }

    private void gameOver() {
        gameRunning = false;
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
