package com.gamecenter.app.games.whack;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 打地鼠游戏绘制 View
 *
 * 玩法：
 * - 3x3 网格的地洞
 * - 地鼠随机从洞中冒出
 * - 点击地鼠得分（+10分）
 * - 30秒倒计时
 * - 地鼠冒出时间 800-1300ms 不等
 *
 * 特性：
 * - 自适应布局，地鼠/地洞位置精确对齐
 * - 可自定义地鼠颜色
 */
public class WhackView extends View {

    private static final int ROWS = 3;
    private static final int COLS = 3;
    private static final int GAME_DURATION = 30000;

    private Paint holePaint;
    private Paint molePaint;
    private Paint moleHitPaint;
    private Paint bgPaint;
    private RectF[] holes;
    private boolean[] moleUp;
    private boolean[] moleHit;
    private int score = 0;
    private int timeLeft = 30;
    private boolean gameRunning = false;
    private boolean gameOver = false;
    private Handler handler;
    private Runnable moleRunnable;
    private Runnable timerRunnable;
    private Random random;
    private OnGameStateListener listener;

    private int moleColor = 0xFF8D6E63;
    private int hitColor = 0xFFFF5722;

    /** 游戏状态回调接口 */
    public interface OnGameStateListener {
        void onScoreChanged(int score);
        void onTimeChanged(int seconds);
        void onGameOver(int finalScore);
    }

    public WhackView(Context context) {
        super(context);
        init();
    }

    public WhackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        handler = new Handler();
        random = new Random();
        holes = new RectF[ROWS * COLS];
        moleUp = new boolean[ROWS * COLS];
        moleHit = new boolean[ROWS * COLS];

        loadMoleColor();

        holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        holePaint.setColor(0xFF4E342E);
        holePaint.setStyle(Paint.Style.FILL);

        molePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        molePaint.setColor(moleColor);
        molePaint.setStyle(Paint.Style.FILL);

        moleHitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        moleHitPaint.setColor(hitColor);
        moleHitPaint.setStyle(Paint.Style.FILL);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF4CAF50);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 从 SharedPreferences 加载地鼠颜色
     */
    private void loadMoleColor() {
        SharedPreferences prefs = getContext().getSharedPreferences("whack_settings", Context.MODE_PRIVATE);
        moleColor = prefs.getInt("mole_color", 0xFF8D6E63);
    }

    /**
     * 设置地鼠颜色并保存
     */
    public void setMoleColor(int color) {
        this.moleColor = color;
        molePaint.setColor(color);
        getContext().getSharedPreferences("whack_settings", Context.MODE_PRIVATE)
                .edit().putInt("mole_color", color).apply();
        if (gameRunning) {
            invalidate();
        }
    }

    /**
     * 获取当前地鼠颜色
     */
    public int getMoleColor() {
        return moleColor;
    }

    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 获取当前得分
     */
    public int getScore() {
        return score;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcHoles(w, h);
    }

    /**
     * 重新计算洞的位置，使用固定边距确保地鼠和地洞对齐
     */
    private void recalcHoles(int w, int h) {
        float marginX = w * 0.08f;
        float marginY = h * 0.08f;
        float gapX = w * 0.06f;
        float gapY = h * 0.06f;

        float usableW = w - 2 * marginX - (COLS - 1) * gapX;
        float usableH = h - 2 * marginY - (ROWS - 1) * gapY;
        float cellW = usableW / COLS;
        float cellH = usableH / ROWS;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                holes[idx] = new RectF(
                        marginX + c * (cellW + gapX),
                        marginY + r * (cellH + gapY),
                        marginX + c * (cellW + gapX) + cellW,
                        marginY + r * (cellH + gapY) + cellH
                );
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        for (int i = 0; i < holes.length; i++) {
            RectF hole = holes[i];
            if (hole == null) continue;

            // 绘制地洞（深色椭圆作为洞底）
            Paint holeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
            holeBottom.setColor(0xFF3E2723);
            canvas.drawOval(hole, holeBottom);

            // 绘制地洞边缘（稍亮）
            Paint holeRim = new Paint(Paint.ANTI_ALIAS_FLAG);
            holeRim.setColor(0xFF4E342E);
            holeRim.setStyle(Paint.Style.STROKE);
            holeRim.setStrokeWidth(4);
            canvas.drawOval(hole, holeRim);

            // 如果地鼠冒出，绘制地鼠
            if (moleUp[i]) {
                drawMole(canvas, hole, i);
            }
        }

        // 绘制游戏结束遮罩
        if (gameOver) {
            Paint overlay = new Paint();
            overlay.setColor(0xCC000000);
            canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(60);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("时间到!", getWidth() / 2f, getHeight() / 2f - 40, textPaint);
            textPaint.setTextSize(40);
            canvas.drawText("得分: " + score, getWidth() / 2f, getHeight() / 2f + 30, textPaint);
        }
    }

    /**
     * 绘制地鼠，底部嵌入洞内实现从洞中冒出的效果
     */
    private void drawMole(Canvas canvas, RectF hole, int index) {
        float moleW = hole.width() * 0.55f;
        float moleH = hole.height() * 0.75f;

        // 地鼠底部在洞的 top + 40% 处，这样看起来从洞里冒出来
        float moleBottom = hole.top + hole.height() * 0.4f;
        float moleTop = moleBottom - moleH;
        float moleLeft = hole.centerX() - moleW / 2;
        float moleRight = moleLeft + moleW;

        RectF moleRect = new RectF(moleLeft, moleTop, moleRight, moleBottom);
        Paint color = moleHit[index] ? moleHitPaint : molePaint;
        canvas.drawOval(moleRect, color);

        // 眼睛
        Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(Color.BLACK);
        float eyeY = moleTop + moleH * 0.35f;
        canvas.drawCircle(moleLeft + moleW * 0.3f, eyeY, moleW * 0.07f, eyePaint);
        canvas.drawCircle(moleLeft + moleW * 0.7f, eyeY, moleW * 0.07f, eyePaint);

        // 鼻子
        Paint nosePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nosePaint.setColor(0xFFFF5722);
        float noseY = moleTop + moleH * 0.55f;
        canvas.drawCircle(moleLeft + moleW * 0.5f, noseY, moleW * 0.09f, nosePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && gameRunning) {
            float x = event.getX();
            float y = event.getY();

            for (int i = 0; i < holes.length; i++) {
                if (holes[i] != null && moleUp[i]) {
                    // 扩大点击区域，地鼠上方的区域也视为可点
                    RectF hole = holes[i];
                    float moleW = hole.width() * 0.55f;
                    float moleH = hole.height() * 0.75f;
                    float hitBottom = hole.top + hole.height() * 0.4f;
                    float hitTop = hitBottom - moleH;
                    float hitLeft = hole.centerX() - moleW / 2;
                    float hitRight = hitLeft + moleW;
                    RectF hitArea = new RectF(hitLeft, hitTop, hitRight, hitBottom);

                    if (hitArea.contains(x, y)) {
                        moleUp[i] = false;
                        moleHit[i] = false;
                        score += 10;
                        if (listener != null) listener.onScoreChanged(score);
                        invalidate();
                        break;
                    }
                }
            }
        }
        return true;
    }

    /** 开始游戏 */
    public void startGame() {
        score = 0;
        timeLeft = 30;
        gameRunning = true;
        gameOver = false;
        for (int i = 0; i < moleUp.length; i++) {
            moleUp[i] = false;
            moleHit[i] = false;
        }

        if (listener != null) listener.onScoreChanged(score);
        if (listener != null) listener.onTimeChanged(timeLeft);

        moleRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    int idx = random.nextInt(ROWS * COLS);
                    moleUp[idx] = true;
                    moleHit[idx] = false;
                    invalidate();
                    handler.postDelayed(() -> {
                        moleUp[idx] = false;
                        invalidate();
                    }, 800 + random.nextInt(500));
                    handler.postDelayed(this, 600 + random.nextInt(400));
                }
            }
        };

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    timeLeft--;
                    if (listener != null) listener.onTimeChanged(timeLeft);
                    if (timeLeft <= 0) {
                        gameRunning = false;
                        gameOver = true;
                        if (listener != null) listener.onGameOver(score);
                        saveScore(score);
                        invalidate();
                    } else {
                        handler.postDelayed(this, 1000);
                    }
                }
            }
        };

        handler.post(moleRunnable);
        handler.postDelayed(timerRunnable, 1000);
    }

    /**
     * 保存得分到历史记录
     */
    private void saveScore(int s) {
        SharedPreferences prefs = getContext().getSharedPreferences("whack_settings", Context.MODE_PRIVATE);
        String history = prefs.getString("score_history", "");
        String entry = s + "," + System.currentTimeMillis();
        String newHistory = entry + (history.isEmpty() ? "" : ";" + history);
        // 只保留最近20条记录
        String[] parts = newHistory.split(";");
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(parts.length, 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(";");
            sb.append(parts[i]);
        }
        prefs.edit().putString("score_history", sb.toString()).apply();
    }

    /** 停止游戏 */
    public void stopGame() {
        gameRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        moleRunnable = null;
        timerRunnable = null;
    }

    public void releaseResources() {
        stopGame();
        handler = null;
        random = null;
        holes = null;
        moleUp = null;
        moleHit = null;
    }
}
