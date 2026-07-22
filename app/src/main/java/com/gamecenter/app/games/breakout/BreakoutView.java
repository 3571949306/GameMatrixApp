package com.gamecenter.app.games.breakout;

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
 * 打砖块游戏自定义 View。
 *
 * <p>包含挡板、球和砖块墙。球反弹消除砖块，挡板随手指移动。
 * 支持关卡递增（砖块行数增加、球速加快）。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class BreakoutView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(boolean win);
        void onLevelComplete(int level);
    }

    // ==================== 常量 ====================
    private static final int BRICK_ROWS_INIT = 3;
    private static final int BRICK_COLS = 8;
    private static final int BALL_RADIUS = 8;
    private static final int PADDLE_HEIGHT = 16;
    private static final int BRICK_HEIGHT = 24;
    private static final int BRICK_GAP = 4;
    private static final int[] ROW_COLORS = {
            0xFFE53935, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0
    };

    // ==================== 游戏状态 ====================
    private Paint paint;
    private float viewWidth;
    private float viewHeight;
    private float paddleX;
    private float paddleWidth;
    private float ballX;
    private float ballY;
    private float ballVx;
    private float ballVy;
    private boolean ballLaunched = false;
    private int score = 0;
    private int level = 1;
    private int lives = 3;
    private int brickRows = BRICK_ROWS_INIT;
    private boolean gameRunning = false;
    private boolean gamePaused = false;
    private List<RectF> bricks = new ArrayList<>();
    private List<Integer> brickColors = new ArrayList<>();
    private List<Boolean> brickAlive = new ArrayList<>();
    private Random random = new Random();
    private OnGameListener listener;

    // ==================== 构造方法 ====================

    public BreakoutView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.game_screen_bg));
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    // ==================== 游戏控制 ====================

    public void startGame(int level) {
        this.level = level;
        this.brickRows = Math.min(BRICK_ROWS_INIT + level - 1, 6);
        this.score = 0;
        this.lives = 3;
        this.gameRunning = true;
        this.gamePaused = false;
        initLevel();
        invalidate();
    }

    public void pauseGame() {
        gamePaused = true;
    }

    public void resumeGame() {
        gamePaused = false;
        invalidate();
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

    public int getLevel() {
        return level;
    }

    // ==================== 初始化 ====================

    private void initLevel() {
        bricks.clear();
        brickColors.clear();
        brickAlive.clear();

        float brickWidth = (viewWidth - (BRICK_COLS + 1) * BRICK_GAP) / BRICK_COLS;

        for (int r = 0; r < brickRows; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                float left = BRICK_GAP + c * (brickWidth + BRICK_GAP);
                float top = BRICK_GAP + r * (BRICK_HEIGHT + BRICK_GAP);
                float right = left + brickWidth;
                float bottom = top + BRICK_HEIGHT;
                bricks.add(new RectF(left, top, right, bottom));
                brickColors.add(ROW_COLORS[r % ROW_COLORS.length]);
                brickAlive.add(true);
            }
        }

        paddleWidth = viewWidth / 4;
        paddleX = (viewWidth - paddleWidth) / 2;
        float paddleY = viewHeight - 60;

        ballX = viewWidth / 2;
        ballY = paddleY - BALL_RADIUS - 2;
        ballLaunched = false;

        float speed = 4 + level * 0.5f;
        ballVx = speed * (random.nextBoolean() ? 1 : -1);
        ballVy = -speed;
    }

    // ==================== 绘制 ====================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (gameRunning) {
            initLevel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!gameRunning) return;

        // 绘制砖块
        for (int i = 0; i < bricks.size(); i++) {
            if (brickAlive.get(i)) {
                paint.setColor(brickColors.get(i));
                canvas.drawRect(bricks.get(i), paint);
            }
        }

        // 绘制挡板
        float paddleY = viewHeight - 60;
        paint.setColor(0xFF4CAF50);
        canvas.drawRoundRect(new RectF(paddleX, paddleY, paddleX + paddleWidth, paddleY + PADDLE_HEIGHT),
                4, 4, paint);

        // 绘制球
        paint.setColor(Color.WHITE);
        canvas.drawCircle(ballX, ballY, BALL_RADIUS, paint);

        // 绘制生命
        paint.setColor(Color.WHITE);
        paint.setTextSize(24);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("❤ × " + lives, 16, viewHeight - 16, paint);

        // 绘制分数
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getContext().getString(R.string.game_breakout_score_label, score), viewWidth - 16, viewHeight - 16, paint);

        // 绘制关卡
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(18);
        canvas.drawText(getContext().getString(R.string.game_breakout_level_label, level), viewWidth / 2, viewHeight - 16, paint);
    }

    // ==================== 游戏循环 ====================

    public void update() {
        if (!gameRunning || gamePaused || !ballLaunched) return;

        ballX += ballVx;
        ballY += ballVy;

        // 墙壁反弹
        if (ballX - BALL_RADIUS <= 0 || ballX + BALL_RADIUS >= viewWidth) {
            ballVx = -ballVx;
        }
        if (ballY - BALL_RADIUS <= 0) {
            ballVy = -ballVy;
        }

        // 挡板反弹
        float paddleY = viewHeight - 60;
        if (ballY + BALL_RADIUS >= paddleY && ballY + BALL_RADIUS <= paddleY + PADDLE_HEIGHT + 4
                && ballX >= paddleX && ballX <= paddleX + paddleWidth) {
            ballVy = -Math.abs(ballVy);
            // 根据击中位置改变反弹角度
            float hitPos = (ballX - paddleX) / paddleWidth;
            float speed = (float) Math.sqrt(ballVx * ballVx + ballVy * ballVy);
            ballVx = speed * (hitPos - 0.5f) * 2;
        }

        // 砖块碰撞
        for (int i = 0; i < bricks.size(); i++) {
            if (!brickAlive.get(i)) continue;
            RectF brick = bricks.get(i);
            if (ballX + BALL_RADIUS > brick.left && ballX - BALL_RADIUS < brick.right
                    && ballY + BALL_RADIUS > brick.top && ballY - BALL_RADIUS < brick.bottom) {
                brickAlive.set(i, false);
                ballVy = -ballVy;
                score += 10;
                if (listener != null) {
                    listener.onScoreChanged(score);
                }

                // 检查是否全部消除
                boolean allDestroyed = true;
                for (Boolean alive : brickAlive) {
                    if (alive) {
                        allDestroyed = false;
                        break;
                    }
                }
                if (allDestroyed) {
                    gameRunning = false;
                    if (listener != null) {
                        listener.onLevelComplete(level);
                    }
                    return;
                }
                break;
            }
        }

        // 球掉出底部
        if (ballY + BALL_RADIUS > viewHeight) {
            lives--;
            if (lives <= 0) {
                gameRunning = false;
                if (listener != null) {
                    listener.onGameOver(false);
                }
                return;
            }
            // 重置球
            ballX = viewWidth / 2;
            ballY = viewHeight - 80;
            ballLaunched = false;
            float speed = 4 + level * 0.5f;
            ballVx = speed * (random.nextBoolean() ? 1 : -1);
            ballVy = -speed;
        }

        invalidate();
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!gameRunning || gamePaused) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                paddleX = event.getX() - paddleWidth / 2;
                paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));

                if (!ballLaunched) {
                    ballX = paddleX + paddleWidth / 2;
                }
                invalidate();
                break;

            case MotionEvent.ACTION_DOWN:
                if (!ballLaunched) {
                    ballLaunched = true;
                    float speed = 4 + level * 0.5f;
                    ballVx = speed * (random.nextBoolean() ? 1 : -1);
                    ballVy = -speed;
                }
                break;
        }

        return true;
    }
}
