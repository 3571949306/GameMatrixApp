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

public class BreakoutView extends View {

    private int paddleX;
    private int ballX, ballY;
    private int ballDX, ballDY;
    private int score = 0;
    private int lives = 3;
    private boolean gameRunning = false;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private RectF[] bricks;
    private int[] brickColors;
    private int rows = 5;
    private int cols = 7;
    private Paint paint;
    private Random random;
    private OnGameStateListener listener;

    public interface OnGameStateListener {
        void onScoreChanged(int score);
        void onLivesChanged(int lives);
        void onGameOver(int score);
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

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();
    }

    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetGame();
    }

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        paint.setColor(0xFF212121);
        canvas.drawRect(0, 0, w, h, paint);

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

        paint.setColor(0xFF4CAF50);
        canvas.drawRect(paddleX - 60, h - 60, paddleX + 60, h - 40, paint);

        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(ballX, ballY, 12, paint);

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

    public void startGame() {
        gameRunning = true;
        gameOver = false;
        gameWon = false;
        gameLoop();
    }

    private void gameLoop() {
        if (!gameRunning) return;

        int w = getWidth();
        int h = getHeight();

        ballX += ballDX;
        ballY += ballDY;

        if (ballX <= 12 || ballX >= w - 12) ballDX = -ballDX;
        if (ballY <= 12) ballDY = -ballDY;

        if (ballY >= h - 60 && ballX >= paddleX - 60 && ballX <= paddleX + 60) {
            ballDY = -Math.abs(ballDY);
            ballDX = (ballX - paddleX) / 3;
        }

        if (ballY > h) {
            lives--;
            if (listener != null) listener.onLivesChanged(lives);
            if (lives <= 0) {
                gameRunning = false;
                gameOver = true;
                if (listener != null) listener.onGameOver(score);
            } else {
                ballX = w / 2;
                ballY = h - 100;
                ballDX = 8 * (random.nextBoolean() ? 1 : -1);
                ballDY = -8;
            }
        }

        for (int i = 0; i < bricks.length; i++) {
            if (bricks[i] != null && bricks[i].contains(ballX, ballY)) {
                bricks[i] = null;
                score += 10;
                if (listener != null) listener.onScoreChanged(score);
                ballDY = -ballDY;

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
        postDelayed(this::gameLoop, 30);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            paddleX = (int) event.getX();
            paddleX = Math.max(60, Math.min(getWidth() - 60, paddleX));
            invalidate();
        }
        return true;
    }
}