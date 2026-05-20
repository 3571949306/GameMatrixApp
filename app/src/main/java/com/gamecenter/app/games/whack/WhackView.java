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
 * 打地鼠游戏视图（含绘制和游戏逻辑）
 *
 * <p>负责 3×3 网格地洞的绘制、地鼠冒出/消失的定时控制、
 * 点击检测与得分计算，以及游戏结束的判定和显示。</p>
 *
 * <p>玩法：
 * <ul>
 *   <li>3×3 网格的地洞，地鼠随机从洞中冒出</li>
 *   <li>点击地鼠得分（+10 分）</li>
 *   <li>30 秒倒计时</li>
 *   <li>地鼠冒出持续时间 800-1300ms 不等</li>
 * </ul>
 * </p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>地鼠绘制在洞的上半部分，底部嵌入洞内实现"从洞中冒出"的视觉效果</li>
 *   <li>点击区域与地鼠绘制区域一致，确保点击判定准确</li>
 *   <li>地鼠颜色可自定义，保存到 SharedPreferences 持久化</li>
 *   <li>历史分数同样存储在 SharedPreferences，最多保留 20 条</li>
 * </ul>
 * </p>
 */
public class WhackView extends View {

    /** 地洞行数 */
    private static final int ROWS = 3;
    /** 地洞列数 */
    private static final int COLS = 3;
    /** 游戏总时长（毫秒） */
    private static final int GAME_DURATION = 30000;

    /** 地洞画笔（深棕色） */
    private Paint holePaint;
    /** 地鼠画笔（默认棕色，可自定义） */
    private Paint molePaint;
    /** 被击中地鼠画笔（深橙色） */
    private Paint moleHitPaint;
    /** 背景画笔（绿色草地） */
    private Paint bgPaint;
    /** 地洞位置矩形数组，索引 = 行 * COLS + 列 */
    private RectF[] holes;
    /** 地鼠是否冒出状态数组 */
    private boolean[] moleUp;
    /** 地鼠是否被击中状态数组（当前未使用击中动画，保留扩展） */
    private boolean[] moleHit;
    /** 当前得分 */
    private int score = 0;
    /** 剩余时间（秒） */
    private int timeLeft = 30;
    /** 游戏是否正在运行 */
    private boolean gameRunning = false;
    /** 游戏是否结束 */
    private boolean gameOver = false;
    /** Handler 用于定时任务（地鼠冒出、倒计时） */
    private Handler handler;
    /** 地鼠冒出定时任务 */
    private Runnable moleRunnable;
    /** 倒计时定时任务 */
    private Runnable timerRunnable;
    /** 随机数生成器 */
    private Random random;
    /** 游戏状态回调监听器 */
    private OnGameStateListener listener;

    /** 当前地鼠颜色（默认棕色） */
    private int moleColor = 0xFF8D6E63;
    /** 地鼠被击中时的颜色 */
    private int hitColor = 0xFFFF5722;

    /**
     * 游戏状态回调接口
     *
     * <p>用于通知 Activity 得分变化、时间变化和游戏结束事件。</p>
     */
    public interface OnGameStateListener {
        /** 得分变化时回调 */
        void onScoreChanged(int score);
        /** 剩余时间变化时回调 */
        void onTimeChanged(int seconds);
        /** 游戏结束时回调 */
        void onGameOver(int finalScore);
    }

    /**
     * 单参数构造方法
     *
     * @param context 上下文
     */
    public WhackView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法（XML 布局使用）
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public WhackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔、地洞数组和地鼠状态
     *
     * <p>从 SharedPreferences 加载上次选择的地鼠颜色，
     * 初始化所有画笔和状态数组。</p>
     */
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
     * 设置地鼠颜色并持久化保存
     *
     * @param color 新的地鼠颜色（ARGB 格式）
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
     *
     * @return 地鼠颜色（ARGB 格式）
     */
    public int getMoleColor() {
        return moleColor;
    }

    /**
     * 设置游戏状态回调监听器
     *
     * @param listener 回调监听器
     */
    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 获取当前得分
     *
     * @return 得分
     */
    public int getScore() {
        return score;
    }

    /**
     * 视图尺寸变化时重新计算地洞位置
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcHoles(w, h);
    }

    /**
     * 重新计算地洞位置，使用固定边距确保地鼠和地洞对齐
     *
     * <p>布局参数：
     * <ul>
     *   <li>水平/垂直边距：视图宽/高的 8%</li>
     *   <li>地洞间距：视图宽/高的 6%</li>
     *   <li>每个地洞大小 = (可用空间) / 行列数</li>
     * </ul>
     * </p>
     *
     * @param w 视图宽度
     * @param h 视图高度
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

    /**
     * 绘制游戏画面
     *
     * <p>绘制流程：
     * <ol>
     *   <li>绘制绿色草地背景</li>
     *   <li>遍历 9 个地洞，绘制洞底和洞边缘</li>
     *   <li>如果地鼠冒出，绘制地鼠（椭圆 + 眼睛 + 鼻子）</li>
     *   <li>如果游戏结束，绘制半透明遮罩和结束文字</li>
     * </ol>
     * </p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绿色草地背景
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        for (int i = 0; i < holes.length; i++) {
            RectF hole = holes[i];
            if (hole == null) continue;

            // 绘制地洞底部（深色椭圆）
            Paint holeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
            holeBottom.setColor(0xFF3E2723);
            canvas.drawOval(hole, holeBottom);

            // 绘制地洞边缘（稍亮的描边）
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

        // 游戏结束遮罩
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
     *
     * <p>地鼠绘制为椭圆形身体，底部在洞的 40% 高度处，
     * 上方露出部分为地鼠身体。还绘制两只眼睛和一个鼻子。</p>
     *
     * @param canvas 画布
     * @param hole   地洞矩形区域
     * @param index  地洞索引
     */
    private void drawMole(Canvas canvas, RectF hole, int index) {
        float moleW = hole.width() * 0.55f;
        float moleH = hole.height() * 0.75f;

        // 地鼠底部在洞的 40% 高度处，上半部分露出洞外
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

    /**
     * 处理触摸事件，检测是否点击到地鼠
     *
     * <p>仅在游戏运行时处理点击。遍历所有冒出的地鼠，
     * 检查点击坐标是否在地鼠的点击区域内。
     * 点击区域与地鼠绘制区域一致，确保判定准确。
     * 击中地鼠后得 10 分，地鼠立即消失。</p>
     *
     * @param event 触摸事件
     * @return 是否消费了该事件（始终返回 true）
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && gameRunning) {
            float x = event.getX();
            float y = event.getY();

            for (int i = 0; i < holes.length; i++) {
                if (holes[i] != null && moleUp[i]) {
                    // 计算地鼠的点击区域（与绘制区域一致）
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

    /**
     * 开始游戏
     *
     * <p>重置得分和时间，启动两个定时任务：
     * <ul>
     *   <li>地鼠冒出任务：每 600-1000ms 随机选一个洞让地鼠冒出，
     *       地鼠持续 800-1300ms 后自动缩回</li>
     *   <li>倒计时任务：每秒减少 1 秒，到 0 时游戏结束</li>
     * </ul>
     * </p>
     */
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

        // 地鼠冒出定时任务
        moleRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    // 随机选一个洞让地鼠冒出
                    int idx = random.nextInt(ROWS * COLS);
                    moleUp[idx] = true;
                    moleHit[idx] = false;
                    invalidate();
                    // 地鼠冒出 800-1300ms 后自动缩回
                    handler.postDelayed(() -> {
                        moleUp[idx] = false;
                        invalidate();
                    }, 800 + random.nextInt(500));
                    // 每 600-1000ms 冒出一只新地鼠
                    handler.postDelayed(this, 600 + random.nextInt(400));
                }
            }
        };

        // 倒计时定时任务
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    timeLeft--;
                    if (listener != null) listener.onTimeChanged(timeLeft);
                    if (timeLeft <= 0) {
                        // 时间到，游戏结束
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
     *
     * <p>将本次得分和时间戳追加到历史记录头部，
     * 最多保留最近 20 条记录。存储格式为 "分数,时间戳;分数,时间戳;..."。</p>
     *
     * @param s 得分
     */
    private void saveScore(int s) {
        SharedPreferences prefs = getContext().getSharedPreferences("whack_settings", Context.MODE_PRIVATE);
        String history = prefs.getString("score_history", "");
        String entry = s + "," + System.currentTimeMillis();
        String newHistory = entry + (history.isEmpty() ? "" : ";" + history);
        // 只保留最近 20 条记录
        String[] parts = newHistory.split(";");
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(parts.length, 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(";");
            sb.append(parts[i]);
        }
        prefs.edit().putString("score_history", sb.toString()).apply();
    }

    /**
     * 停止游戏
     *
     * <p>停止所有定时任务，将游戏标记为非运行状态。</p>
     */
    public void stopGame() {
        gameRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        moleRunnable = null;
        timerRunnable = null;
    }

    /**
     * 释放资源
     *
     * <p>停止游戏后释放所有引用，避免内存泄漏。
     * 应在 Activity 的 onDestroy 中调用。</p>
     */
    public void releaseResources() {
        stopGame();
        handler = null;
        random = null;
        holes = null;
        moleUp = null;
        moleHit = null;
    }
}
