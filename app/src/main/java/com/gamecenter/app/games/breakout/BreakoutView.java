package com.gamecenter.app.games.breakout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 打砖块游戏自定义 View（深度改进版）。
 *
 * <p>相对旧版的改进：
 * <ul>
 *   <li>明确的游戏状态机（READY / PLAYING / PAUSED / LEVEL_CLEAR / GAME_OVER），每个状态均有画面提示，
 *       不再在过关/结束时黑屏。</li>
 *   <li>修复挡板穿模漏接：基于上一帧与当前帧的"穿越平面"检测，高速球不再穿透挡板。</li>
 *   <li>修复球击中挡板正中后 Vx≈0 的"垂直死循环"：保证最小水平分量。</li>
 *   <li>分数与生命跨关累计：过关不再重置 lives，分数由本 View 统一累计（含过关奖励）。</li>
 *   <li>多球支持 + 道具系统（加长挡板 / 多球 / 减速 / 加命）。</li>
 *   <li>粒子爆裂、小球拖尾、漂浮得分、渐变背景与砖块、更精致的 HUD。</li>
 *   <li>响应式尺寸：依据视图宽高计算挡板/球/砖块尺寸，适配不同屏幕。</li>
 *   <li>首帧测量前延迟初始化，避免 viewWidth/Height 为 0 时布局错乱。</li>
 * </ul>
 * </p>
 */
public class BreakoutView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(boolean win);
        void onLevelComplete(int level);
    }

    /** 游戏状态 */
    private enum State {
        READY,        // 球停在挡板上，等待点击发射
        PLAYING,      // 进行中
        PAUSED,       // 暂停
        LEVEL_CLEAR,  // 本关完成（短暂展示）
        GAME_OVER     // 失败
    }

    // ==================== 内部实体 ====================
    private static class Ball {
        float x, y, vx, vy;
        float prevX, prevY;
        final List<float[]> trail = new ArrayList<>();
    }

    private static class Particle {
        float x, y, vx, vy;
        int life;
        int color;
    }

    private static class FloatText {
        float x, y;
        int life;
        String text;
        int color;
    }

    private enum PowerType { EXPAND, MULTI, SLOW, EXTRA_LIFE }

    private static class PowerUp {
        float x, y;
        float vy;
        PowerType type;
    }

    // ==================== 常量 ====================
    private static final int BRICK_COLS = 9;
    private static final int[] ROW_COLORS = {
            0xFFE53935, 0xFFFF7043, 0xFFFFCA28, 0xFF66BB6A, 0xFF29B6F6, 0xFFAB47BC
    };
    private static final float MIN_SPEED_FACTOR = 0.55f; // 最小水平/垂直分量占比

    // ==================== 游戏状态 ====================
    private Paint paint;
    private float viewWidth;
    private float viewHeight;
    private State state = State.READY;

    private float paddleX;
    private float paddleWidth;
    private float paddleBaseWidth;
    private float paddleY;
    private float paddleHeight;
    private float ballRadius;

    private List<Ball> balls = new ArrayList<>();
    private List<RectF> bricks = new ArrayList<>();
    private List<Integer> brickColors = new ArrayList<>();
    private List<Integer> brickHp = new ArrayList<>();
    private List<Integer> brickMaxHp = new ArrayList<>();
    private List<Boolean> brickAlive = new ArrayList<>();

    private List<Particle> particles = new ArrayList<>();
    private List<FloatText> floatTexts = new ArrayList<>();
    private List<PowerUp> powerUps = new ArrayList<>();

    private int score = 0;
    private int lastLevelScore = 0;
    private int level = 1;
    private int lives = 3;
    private int brickRows = 3;

    private boolean gameRunning = false; // 供 Activity 查询
    private boolean gamePaused = false;
    private boolean missedThisLevel = false;

    private long expandUntil = 0;
    private long slowUntil = 0;

    private final Random random = new Random();
    private OnGameListener listener;

    /** 设备密度（px = dp * density），用于把画布绘制的"绝对像素"尺寸换算成与屏幕匹配的 dp 视觉尺寸 */
    private float density = 1f;

    // 首帧测量前的延迟启动
    private int pendingStartLevel = -1;

    // 缓存的每行星渐进画笔
    private final List<Paint> rowPaints = new ArrayList<>();

    /**
     * 2026-08-23 P0-3 onDraw 对象复用：
     * 原实现每帧 new Paint/LinearGradient/RectF（背景/挡板/道具），
     * 60fps 循环下产生持续 GC 压力。改为成员复用，shader 仅在几何变化时重建。
     */
    private final Paint bgPaint = new Paint();
    private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF reusableRect = new RectF();

    // ==================== 构造 ====================
    public BreakoutView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.game_screen_bg));
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    // ==================== 游戏控制 ====================
    /** 初始开始（重置分数与生命） */
    public void startGame(int level) {
        this.level = level;
        this.score = 0;
        this.lives = 3;
        this.missedThisLevel = false;
        if (viewWidth <= 0 || viewHeight <= 0) {
            pendingStartLevel = level;
            return;
        }
        beginLevel(level, true);
    }

    /** 进入下一关（保留分数与生命） */
    public void startNextLevel(int level) {
        this.level = level;
        this.missedThisLevel = false;
        if (viewWidth <= 0 || viewHeight <= 0) {
            pendingStartLevel = level;
            return;
        }
        beginLevel(level, false);
    }

    private void beginLevel(int lvl, boolean resetEffects) {
        state = State.READY;
        gameRunning = true;
        gamePaused = false;
        brickRows = Math.min(3 + (lvl - 1) / 2, 6);
        balls.clear();
        particles.clear();
        floatTexts.clear();
        powerUps.clear();
        if (resetEffects) {
            expandUntil = 0;
            slowUntil = 0;
        }
        initBricks();
        resetPaddle();
        spawnBallOnPaddle();
        invalidate();
    }

    public void pauseGame() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            gamePaused = true;
        }
        invalidate();
    }

    public void resumeGame() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
            gamePaused = false;
        }
        invalidate();
    }

    public void stopGame() {
        gameRunning = false;
        state = State.GAME_OVER;
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

    /** 本关得分（用于成就判定），不含过关奖励 */
    public int getLastLevelScore() {
        return lastLevelScore;
    }

    /** 本关是否零失误（未丢球） */
    public boolean isLevelNoMiss() {
        return !missedThisLevel;
    }

    // ==================== 尺寸与初始化 ====================
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        // 2026-08-23 P0-3：背景渐变 shader 仅依赖视图高度，尺寸变化时重建一次
        bgPaint.setShader(new LinearGradient(0, 0, 0, viewHeight,
                0xFF0B1026, 0xFF05060F, Shader.TileMode.CLAMP));
        if (pendingStartLevel > 0) {
            int lvl = pendingStartLevel;
            pendingStartLevel = -1;
            beginLevel(lvl, true);
        } else if (gameRunning && state != State.GAME_OVER) {
            // 尺寸变化（如旋转/重布局）：重新计算挡板与球位置，但保留砖块存活状态
            recomputeGeometry();
        }
    }

    private void recomputeGeometry() {
        paddleHeight = Math.max(18, viewHeight * 0.022f);
        paddleBaseWidth = Math.max(90, viewWidth * 0.26f);
        paddleWidth = (expandUntil > now()) ? paddleBaseWidth * 1.6f : paddleBaseWidth;
        paddleY = viewHeight - Math.max(50, viewHeight * 0.08f);
        ballRadius = Math.max(9, viewWidth * 0.015f);
        paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));
        rebuildPaddleShader();
        // 把停在挡板上的球重新贴回挡板
        for (Ball b : balls) {
            if (state == State.READY) {
                b.x = paddleX + paddleWidth / 2f;
                b.y = paddleY - ballRadius - 2;
            }
        }
        invalidate();
    }

    private void resetPaddle() {
        paddleHeight = Math.max(18, viewHeight * 0.022f);
        paddleBaseWidth = Math.max(90, viewWidth * 0.26f);
        paddleWidth = (expandUntil > now()) ? paddleBaseWidth * 1.6f : paddleBaseWidth;
        paddleY = viewHeight - Math.max(50, viewHeight * 0.08f);
        paddleX = (viewWidth - paddleWidth) / 2f;
        ballRadius = Math.max(9, viewWidth * 0.015f);
        rebuildPaddleShader();
    }

    /** 2026-08-23 P0-3：挡板渐变为垂直方向（x 无关），仅在几何变化时重建 shader */
    private void rebuildPaddleShader() {
        paddlePaint.setShader(new LinearGradient(0, paddleY, 0, paddleY + paddleHeight,
                0xFF8BC34A, 0xFF33691E, Shader.TileMode.CLAMP));
    }

    private void initBricks() {
        bricks.clear();
        brickColors.clear();
        brickHp.clear();
        brickMaxHp.clear();
        brickAlive.clear();
        rowPaints.clear();

        float gap = Math.max(4, viewWidth * 0.014f);
        float brickW = (viewWidth - (BRICK_COLS + 1) * gap) / BRICK_COLS;
        float brickH = Math.max(24, viewHeight * 0.03f);
        float top = Math.max(80, viewHeight * 0.09f);

        int maxHp = Math.min(1 + (level - 1) / 2, 3);

        for (int r = 0; r < brickRows; r++) {
            int baseColor = ROW_COLORS[r % ROW_COLORS.length];
            // 渐变：顶部更亮
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            int light = lighten(baseColor, 0.35f);
            gp.setShader(new LinearGradient(0, top + r * (brickH + gap), 0,
                    top + r * (brickH + gap) + brickH, light, baseColor, Shader.TileMode.CLAMP));
            rowPaints.add(gp);

            for (int c = 0; c < BRICK_COLS; c++) {
                float left = gap + c * (brickW + gap);
                float t = top + r * (brickH + gap);
                bricks.add(new RectF(left, t, left + brickW, t + brickH));
                brickColors.add(baseColor);
                int hp = Math.min(maxHp, 1 + (brickRows - 1 - r) / 2);
                brickHp.add(hp);
                brickMaxHp.add(hp);
                brickAlive.add(true);
            }
        }
    }

    private void spawnBallOnPaddle() {
        Ball b = new Ball();
        b.x = paddleX + paddleWidth / 2f;
        b.y = paddleY - ballRadius - 2;
        b.prevX = b.x;
        b.prevY = b.y;
        float speed = baseSpeed();
        b.vx = 0;
        b.vy = 0;
        balls.clear();
        balls.add(b);
    }

    private float baseSpeed() {
        float s = Math.max(3.5f, viewHeight * 0.006f) + (level - 1) * 0.7f;
        return Math.min(s, viewHeight * 0.018f);
    }

    /** 发射球（READY -> PLAYING） */
    private void launchBalls() {
        state = State.PLAYING;
        gameRunning = true;
        float speed = baseSpeed();
        for (Ball b : balls) {
            if (b.vx == 0 && b.vy == 0) {
                float ang = (random.nextFloat() * 0.5f - 0.25f); // -0.25..0.25 rad 偏上
                b.vx = (float) (speed * Math.sin(ang));
                b.vy = (float) (-speed * Math.cos(ang));
            }
        }
        invalidate();
    }

    // ==================== 绘制 ====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        if (state == State.GAME_OVER) {
            drawScene(canvas, false);
            drawGameOver(canvas);
            return;
        }
        drawScene(canvas, true);
        drawHud(canvas);
        if (state == State.READY) drawReady(canvas);
        else if (state == State.PAUSED) drawPaused(canvas);
        else if (state == State.LEVEL_CLEAR) drawLevelClear(canvas);
    }

    private void drawBackground(Canvas canvas) {
        // 2026-08-23 P0-3：复用 bgPaint（shader 在 onSizeChanged 时重建）
        canvas.drawRect(0, 0, viewWidth, viewHeight, bgPaint);
    }

    private void drawScene(Canvas canvas, boolean withEntities) {
        // 砖块
        for (int i = 0; i < bricks.size(); i++) {
            if (!brickAlive.get(i)) continue;
            RectF b = bricks.get(i);
            Paint gp = rowPaints.get(i / BRICK_COLS);
            canvas.drawRoundRect(b, 6, 6, gp);
            int hp = brickHp.get(i);
            int max = brickMaxHp.get(i);
            if (hp < max) {
                // 受损：叠加半透明黑
                paint.setColor(0x55000000);
                canvas.drawRoundRect(b, 6, 6, paint);
            }
        }

        // 道具
        for (PowerUp p : powerUps) {
            float hs = 18f * density; // 道具半边长（按密度缩放）
            paint.setColor(p.type == PowerType.EXPAND ? 0xFF42A5F5
                    : p.type == PowerType.MULTI ? 0xFFAB47BC
                    : p.type == PowerType.SLOW ? 0xFF26C6DA : 0xFF66BB6A);
            // 2026-08-23 P0-3：复用 RectF，消除每帧分配
            reusableRect.set(p.x - hs, p.y - hs, p.x + hs, p.y + hs);
            canvas.drawRoundRect(reusableRect, 8 * density, 8 * density, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(15 * density);
            paint.setTextAlign(Paint.Align.CENTER);
            String sym = p.type == PowerType.EXPAND ? "↔"
                    : p.type == PowerType.MULTI ? "✦"
                    : p.type == PowerType.SLOW ? "❄" : "♥";
            canvas.drawText(sym, p.x, p.y + 5 * density, paint);
        }

        if (!withEntities) return;

        // 粒子
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha(Math.max(0, Math.min(255, p.life * 12)));
            canvas.drawCircle(p.x, p.y, 3.5f * density, paint);
        }
        paint.setAlpha(255);

        // 漂浮得分
        for (FloatText ft : floatTexts) {
            paint.setColor(ft.color);
            paint.setAlpha(Math.max(0, Math.min(255, ft.life * 12)));
            paint.setTextSize(17 * density);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(ft.text, ft.x, ft.y, paint);
        }
        paint.setAlpha(255);

        // 挡板
        // 2026-08-23 P0-3：复用 paddlePaint（shader 在几何变化时重建）与 RectF
        reusableRect.set(paddleX, paddleY, paddleX + paddleWidth, paddleY + paddleHeight);
        canvas.drawRoundRect(reusableRect, 8, 8, paddlePaint);

        // 球 + 拖尾
        for (Ball b : balls) {
            for (int t = 0; t < b.trail.size(); t++) {
                float[] pt = b.trail.get(t);
                paint.setColor(0x66FFFFFF);
                paint.setAlpha((int) (80 * ((float) t / b.trail.size())));
                canvas.drawCircle(pt[0], pt[1], ballRadius * (0.4f + 0.6f * t / b.trail.size()), paint);
            }
            paint.setAlpha(255);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(b.x, b.y, ballRadius, paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(b.x - ballRadius * 0.3f, b.y - ballRadius * 0.3f, ballRadius * 0.4f, paint);
        }
    }

    private void drawHud(Canvas canvas) {
        float margin = 14 * density;
        float baseline = viewHeight - 18 * density;
        paint.setColor(Color.WHITE);
        paint.setTextSize(19 * density);
        paint.setTextAlign(Paint.Align.LEFT);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < Math.max(lives, 0); i++) hearts.append("♥ ");
        canvas.drawText(hearts.toString(), margin, baseline, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getContext().getString(R.string.game_breakout_score_label, score),
                viewWidth - margin, baseline, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16 * density);
        canvas.drawText(getContext().getString(R.string.game_breakout_level_label, level),
                viewWidth / 2, baseline, paint);
    }

    private void drawCenterText(Canvas canvas, String title, String sub, int titleColor) {
        float half = 95f * density;
        paint.setColor(0xCC000000);
        canvas.drawRect(0, viewHeight / 2f - half, viewWidth, viewHeight / 2f + half, paint);
        paint.setColor(titleColor);
        paint.setTextSize(30 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, viewWidth / 2f, viewHeight / 2f - 12 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(18 * density);
        canvas.drawText(sub, viewWidth / 2f, viewHeight / 2f + 40 * density, paint);
    }

    private void drawReady(Canvas canvas) {
        drawCenterText(canvas, "准备", "点击屏幕发射小球", 0xFFFFEB3B);
    }

    private void drawPaused(Canvas canvas) {
        drawCenterText(canvas, "暂停", "返回继续游戏", 0xFFFFFFFF);
    }

    private void drawLevelClear(Canvas canvas) {
        drawCenterText(canvas, "第 " + level + " 关 完成!", "+" + (level * 50) + " 奖励分", 0xFF66BB6A);
    }

    private void drawGameOver(Canvas canvas) {
        drawCenterText(canvas, "游戏结束", "点击重新开始", 0xFFE53935);
    }

    // ==================== 游戏循环 ====================
    public void update() {
        if (state != State.PLAYING) {
            invalidate();
            return;
        }

        long now = now();
        float speedScale = (slowUntil > now) ? 0.6f : 1f;

        // 更新球
        Iterator<Ball> it = balls.iterator();
        while (it.hasNext()) {
            Ball b = it.next();
            b.prevX = b.x;
            b.prevY = b.y;
            b.x += b.vx * speedScale;
            b.y += b.vy * speedScale;

            // 拖尾
            b.trail.add(new float[]{b.x, b.y});
            if (b.trail.size() > 8) b.trail.remove(0);

            // 墙壁
            if (b.x - ballRadius <= 0) { b.x = ballRadius; b.vx = Math.abs(b.vx); }
            else if (b.x + ballRadius >= viewWidth) { b.x = viewWidth - ballRadius; b.vx = -Math.abs(b.vx); }
            if (b.y - ballRadius <= 0) { b.y = ballRadius; b.vy = Math.abs(b.vy); }

            // 挡板（穿越平面检测，杜绝穿模）
            if (b.vy > 0) {
                float crossTop = paddleY;
                if (b.prevY + ballRadius <= crossTop && b.y + ballRadius >= crossTop
                        && b.x >= paddleX - ballRadius && b.x <= paddleX + paddleWidth + ballRadius) {
                    b.y = crossTop - ballRadius;
                    b.vy = -Math.abs(b.vy);
                    float hit = (b.x - paddleX) / paddleWidth;        // 0..1
                    hit = Math.max(0, Math.min(1, hit));
                    float ang = (hit - 0.5f) * 1.1f;                  // -0.55..0.55 rad
                    float sp = (float) Math.hypot(b.vx, b.vy);
                    b.vx = (float) (sp * Math.sin(ang));
                    b.vy = (float) (-sp * Math.cos(ang));
                    enforceMinComponent(b);
                }
            }

            // 砖块碰撞
            for (int i = 0; i < bricks.size(); i++) {
                if (!brickAlive.get(i)) continue;
                RectF br = bricks.get(i);
                if (b.x + ballRadius > br.left && b.x - ballRadius < br.right
                        && b.y + ballRadius > br.top && b.y - ballRadius < br.bottom) {
                    // 依据最小穿透轴反弹
                    float oL = (b.x + ballRadius) - br.left;
                    float oR = br.right - (b.x - ballRadius);
                    float oT = (b.y + ballRadius) - br.top;
                    float oB = br.bottom - (b.y - ballRadius);
                    float min = Math.min(Math.min(oL, oR), Math.min(oT, oB));
                    if (min == oT || min == oB) b.vy = -b.vy;
                    else b.vx = -b.vx;
                    enforceMinComponent(b);

                    int hp = brickHp.get(i) - 1;
                    if (hp <= 0) {
                        brickAlive.set(i, false);
                        int gained = 15;
                        score += gained;
                        lastLevelScore += gained;
                        spawnParticles(br.centerX(), br.centerY(), brickColors.get(i));
                        maybeDropPowerUp(br.centerX(), br.centerY());
                        addFloatText(br.centerX(), br.centerY(), "+" + gained, 0xFFFFEB3B);
                        if (listener != null) listener.onScoreChanged(score);
                    } else {
                        brickHp.set(i, hp);
                        int gained = 5;
                        score += gained;
                        lastLevelScore += gained;
                        if (listener != null) listener.onScoreChanged(score);
                    }

                    if (allDestroyed()) {
                        onLevelCleared();
                        return;
                    }
                    break; // 每球每帧最多消一块，避免连环穿透
                }
            }

            // 掉出底部
            if (b.y - ballRadius > viewHeight) {
                it.remove();
            }
        }

        // 所有球掉光 -> 丢命
        if (balls.isEmpty()) {
            loseLife();
            if (state == State.GAME_OVER) return;
        }

        // 道具下落
        Iterator<PowerUp> pit = powerUps.iterator();
        while (pit.hasNext()) {
            PowerUp p = pit.next();
            p.y += 3.2f;
            if (p.y >= paddleY && p.y <= paddleY + paddleHeight
                    && p.x >= paddleX && p.x <= paddleX + paddleWidth) {
                applyPowerUp(p);
                pit.remove();
            } else if (p.y > viewHeight) {
                pit.remove();
            }
        }

        // 粒子 / 漂浮文字
        Iterator<Particle> partIt = particles.iterator();
        while (partIt.hasNext()) {
            Particle p = partIt.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life--;
            if (p.life <= 0) partIt.remove();
        }
        Iterator<FloatText> ftIt = floatTexts.iterator();
        while (ftIt.hasNext()) {
            FloatText ft = ftIt.next();
            ft.y -= 0.6f; ft.life--;
            if (ft.life <= 0) ftIt.remove();
        }

        // 加长挡板到期
        if (expandUntil > 0 && now > expandUntil) {
            expandUntil = 0;
            shrinkPaddle();
        }

        invalidate();
    }

    private void enforceMinComponent(Ball b) {
        float sp = (float) Math.hypot(b.vx, b.vy);
        if (sp < 0.001f) { b.vy = -baseSpeed(); return; }
        float minV = sp * MIN_SPEED_FACTOR;
        if (Math.abs(b.vx) < minV) {
            b.vx = (b.vx >= 0 ? 1 : -1) * minV;
            // 重新归一化保持速度
            float k = sp / (float) Math.hypot(b.vx, b.vy);
            b.vx *= k; b.vy *= k;
        }
    }

    private void loseLife() {
        lives--;
        missedThisLevel = true;
        if (lives <= 0) {
            state = State.GAME_OVER;
            gameRunning = false;
            if (listener != null) listener.onGameOver(false);
            invalidate();
            return;
        }
        resetPaddle();
        spawnBallOnPaddle();
        state = State.READY;
        invalidate();
    }

    private void onLevelCleared() {
        int bonus = level * 50;
        score += bonus;
        lastLevelScore += bonus;
        state = State.LEVEL_CLEAR;
        if (listener != null) {
            listener.onScoreChanged(score);
            listener.onLevelComplete(level);
        }
        invalidate();
    }

    private boolean allDestroyed() {
        for (Boolean a : brickAlive) if (a) return false;
        return true;
    }

    // ==================== 道具 ====================
    private void maybeDropPowerUp(float x, float y) {
        if (random.nextFloat() < 0.14f) {
            PowerType[] types = PowerType.values();
            PowerUp p = new PowerUp();
            p.x = x; p.y = y;
            p.type = types[random.nextInt(types.length)];
            powerUps.add(p);
        }
    }

    private void applyPowerUp(PowerUp p) {
        long now = now();
        switch (p.type) {
            case EXPAND:
                expandUntil = now + 10000;
                expandPaddle();
                addFloatText(p.x, paddleY - 30, "挡板加长", 0xFF42A5F5);
                break;
            case MULTI:
                spawnMultiBall();
                addFloatText(p.x, paddleY - 30, "多球!", 0xFFAB47BC);
                break;
            case SLOW:
                slowUntil = now + 8000;
                addFloatText(p.x, paddleY - 30, "减速", 0xFF26C6DA);
                break;
            case EXTRA_LIFE:
                lives++;
                addFloatText(p.x, paddleY - 30, "+1命", 0xFF66BB6A);
                break;
        }
    }

    private void expandPaddle() {
        paddleWidth = paddleBaseWidth * 1.6f;
        paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));
    }

    private void shrinkPaddle() {
        paddleWidth = paddleBaseWidth;
        paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));
    }

    private void spawnMultiBall() {
        List<Ball> extra = new ArrayList<>();
        for (Ball b : balls) {
            for (int k = 0; k < 2; k++) {
                Ball nb = new Ball();
                nb.x = b.x; nb.y = b.y; nb.prevX = b.x; nb.prevY = b.y;
                float sp = (float) Math.hypot(b.vx, b.vy);
                if (sp < 0.001f) sp = baseSpeed();
                float ang = (k == 0 ? -0.4f : 0.4f);
                nb.vx = (float) (sp * Math.sin(ang));
                nb.vy = (float) (-sp * Math.cos(ang));
                enforceMinComponent(nb);
                extra.add(nb);
            }
        }
        balls.addAll(extra);
    }

    // ==================== 特效 ====================
    private void spawnParticles(float x, float y, int color) {
        for (int i = 0; i < 10; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y;
            float a = random.nextFloat() * (float) Math.PI * 2;
            float s = 1 + random.nextFloat() * 3;
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s;
            p.life = 20 + random.nextInt(10);
            p.color = color;
            particles.add(p);
        }
    }

    private void addFloatText(float x, float y, String text, int color) {
        FloatText ft = new FloatText();
        ft.x = x; ft.y = y; ft.text = text; ft.color = color;
        ft.life = 22;
        floatTexts.add(ft);
    }

    // ==================== 工具 ====================
    private long now() {
        return System.currentTimeMillis();
    }

    private static int lighten(int color, float f) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return Color.argb(255, r, g, b);
    }

    // ==================== 触摸事件 ====================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (state == State.GAME_OVER) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startGame(1);
            }
            return true;
        }
        if (state == State.PAUSED) return true;
        if (state == State.LEVEL_CLEAR) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (state == State.READY) {
                    launchBalls();
                }
                paddleX = event.getX() - paddleWidth / 2f;
                paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));
                if (state == State.READY) {
                    for (Ball b : balls) { b.x = paddleX + paddleWidth / 2f; }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                paddleX = event.getX() - paddleWidth / 2f;
                paddleX = Math.max(0, Math.min(paddleX, viewWidth - paddleWidth));
                if (state == State.READY) {
                    for (Ball b : balls) { b.x = paddleX + paddleWidth / 2f; }
                }
                invalidate();
                break;
        }
        return true;
    }
}
