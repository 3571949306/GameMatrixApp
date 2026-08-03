package com.gamecenter.app.brotato;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 土豆兄弟游戏自定义 View（独立 APK 模块版本）。
 *
 * <p>由宿主 com.gamecenter.app.games.brotato.BrotatoView 迁移而来。
 * 移除了对宿主 R 资源的依赖，背景色支持浅色/深色主题。</p>
 *
 * <p>玩家控制土豆角色移动并自动射击，敌人从四周涌来。
 * 需要在波次中生存，每波结束后可恢复生命。</p>
 *
 * <p>改进（相对旧版）：
 * <ul>
 *   <li>引入 {@code density}，把玩家/子弹/敌人等"绝对像素"尺寸换算成与屏幕匹配的 dp 视觉尺寸，
 *       在高密度屏（如 density=4 的 xxxhdpi）上不再过小、子弹不再几乎不可见。</li>
 *   <li>新增 gameOver 状态并在 onDraw 中绘制"游戏结束"结算遮罩，死亡后不再整屏空白。</li>
 * </ul>
 * </p>
 */
public class BrotatoView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score, int wave);
        void onWaveComplete(int wave);
    }

    // ==================== 常量（设计为 mdpi 下的 dp 基准，运行时按 density 放大） ====================
    private static final float PLAYER_SIZE = 32f;
    private static final float BULLET_SIZE = 6f;
    private static final float ENEMY_SIZE = 24f;
    private static final float BULLET_SPEED = 8f;
    private static final long SHOOT_INTERVAL_MS = 300;
    private static final int ENEMIES_PER_WAVE_BASE = 5;

    // ==================== 游戏状态 ====================
    private Paint paint;
    private float viewWidth;
    private float viewHeight;
    private float playerX;
    private float playerY;
    private float touchX;
    private float touchY;
    private boolean touching = false;
    private int score = 0;
    private int wave = 1;
    private int hp = 10;
    private int maxHp = 10;
    private int enemiesKilled = 0;
    private int enemiesInWave = 0;
    private int enemiesSpawned = 0;
    private boolean waveActive = false;
    private boolean gameRunning = false;
    private boolean gameOver = false;
    private boolean gamePaused = false;
    private long lastShootTime = 0;
    private long lastEnemySpawnTime = 0;
    private long waveStartTime = 0;

    // 设备密度（px = dp * density），用于把"绝对像素"尺寸换算为与屏幕匹配的 dp 视觉尺寸
    private float density = 1f;
    private float playerSize;
    private float bulletSize;
    private float enemySize;

    private List<float[]> bullets = new ArrayList<>();      // [x, y, vx, vy]
    private List<float[]> enemies = new ArrayList<>();        // [x, y, hp, speed]
    private Random random = new Random();
    private OnGameListener listener;
    private float difficultyFactor = 0.5f;

    // ==================== 构造方法 ====================

    public BrotatoView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        density = getResources().getDisplayMetrics().density;
        playerSize = PLAYER_SIZE * density;
        bulletSize = BULLET_SIZE * density;
        enemySize = ENEMY_SIZE * density;
        setBackgroundColor(isNightMode() ? 0xFF0E1016 : 0xFF1B1B1F);
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
     * 设置难度因子（由 Fragment 根据难度调用）。
     * 内部按 factor / 0.5 归一化为倍率 dm：简单 0.6 / 普通 1.0 / 困难 1.6。
     */
    public void setDifficultyFactor(float factor) {
        this.difficultyFactor = factor;
    }

    // ==================== 游戏控制 ====================

    public void startGame() {
        this.score = 0;
        this.wave = 1;
        this.hp = 10;
        this.maxHp = 10;
        this.gameRunning = true;
        this.gameOver = false;
        this.gamePaused = false;
        this.touching = false;
        bullets.clear();
        enemies.clear();
        initGame();
        startWave();
        invalidate();
    }

    public void pauseGame() { gamePaused = true; }
    public void resumeGame() { gamePaused = false; }
    public void stopGame() { gameRunning = false; }
    public boolean isGameRunning() { return gameRunning; }
    public int getScore() { return score; }
    public int getWave() { return wave; }

    private void initGame() {
        playerX = viewWidth / 2;
        playerY = viewHeight / 2;
    }

    private void startWave() {
        waveActive = true;
        enemiesKilled = 0;
        enemiesSpawned = 0;
        enemiesInWave = ENEMIES_PER_WAVE_BASE + wave * 3;
        waveStartTime = System.currentTimeMillis();
        lastEnemySpawnTime = 0;
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

        // 绘制地面网格
        paint.setColor(0x22FFFFFF);
        paint.setStrokeWidth(1);
        for (float x = 0; x < viewWidth; x += 40 * density) {
            canvas.drawLine(x, 0, x, viewHeight, paint);
        }
        for (float y = 0; y < viewHeight; y += 40 * density) {
            canvas.drawLine(0, y, viewWidth, y, paint);
        }

        // 绘制子弹
        paint.setColor(0xFFFFEB3B);
        for (float[] bullet : bullets) {
            canvas.drawCircle(bullet[0], bullet[1], bulletSize, paint);
        }

        // 绘制敌人
        for (float[] enemy : enemies) {
            if (enemy[2] > 1) {
                paint.setColor(0xFFE53935); // 强敌红色
            } else {
                paint.setColor(0xFFFF5722); // 普通敌橙色
            }
            canvas.drawCircle(enemy[0], enemy[1], enemySize, paint);
            // 敌人眼睛
            paint.setColor(Color.WHITE);
            canvas.drawCircle(enemy[0] - 5 * density, enemy[1] - 4 * density, 4 * density, paint);
            canvas.drawCircle(enemy[0] + 5 * density, enemy[1] - 4 * density, 4 * density, paint);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(enemy[0] - 4 * density, enemy[1] - 4 * density, 2 * density, paint);
            canvas.drawCircle(enemy[0] + 6 * density, enemy[1] - 4 * density, 2 * density, paint);
        }

        // 绘制玩家（土豆）
        float ps = playerSize;
        paint.setColor(0xFFD4A574);
        canvas.drawCircle(playerX, playerY, ps, paint);
        // 土豆轮廓
        paint.setColor(0xFF8D6E63);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawCircle(playerX, playerY, ps, paint);
        paint.setStyle(Paint.Style.FILL);
        // 土豆眼睛
        paint.setColor(Color.WHITE);
        canvas.drawCircle(playerX - ps * 0.25f, playerY - ps * 0.19f, ps * 0.19f, paint);
        canvas.drawCircle(playerX + ps * 0.25f, playerY - ps * 0.19f, ps * 0.19f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(playerX - ps * 0.19f, playerY - ps * 0.19f, ps * 0.09f, paint);
        canvas.drawCircle(playerX + ps * 0.31f, playerY - ps * 0.19f, ps * 0.09f, paint);
        // 土豆微笑
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawArc(new RectF(playerX - ps * 0.25f, playerY + ps * 0.06f,
                playerX + ps * 0.25f, playerY + ps * 0.38f), 0, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // 绘制武器指示（小枪）
        paint.setColor(0xFF9E9E9E);
        canvas.drawRect(playerX + ps, playerY - 3 * density,
                playerX + ps + 16 * density, playerY + 3 * density, paint);

        // 绘制生命条
        float hpBarH = 16 * density;
        float hpBarBottom = viewHeight - 34 * density;
        float hpBarTop = hpBarBottom - hpBarH;
        paint.setColor(0xFF333333);
        canvas.drawRect(16 * density, hpBarTop, viewWidth - 16 * density, hpBarBottom, paint);
        float hpRatio = (float) hp / maxHp;
        paint.setColor(hpRatio > 0.5f ? 0xFF4CAF50 : hpRatio > 0.25f ? 0xFFFF9800 : 0xFFF44336);
        canvas.drawRect(16 * density, hpBarTop,
                16 * density + (viewWidth - 32 * density) * hpRatio, hpBarBottom, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(16 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hp + "/" + maxHp, viewWidth / 2, hpBarBottom - 2 * density, paint);

        // 绘制波次和分数
        paint.setColor(Color.WHITE);
        paint.setTextSize(20 * density);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("分：" + score, 16 * density, 32 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("波次 " + wave, viewWidth - 16 * density, 32 * density, paint);

        // 波次间歇提示
        if (!waveActive) {
            float band = 40 * density;
            paint.setColor(0xAA000000);
            canvas.drawRect(0, viewHeight / 2 - band, viewWidth, viewHeight / 2 + band, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(28 * density);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("波次 " + wave + " 即将开始...", viewWidth / 2, viewHeight / 2 + 10 * density, paint);
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
        canvas.drawText("得分 " + score + "   波次 " + wave, viewWidth / 2f, viewHeight / 2f + 10 * density, paint);
        paint.setTextSize(15 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText("点击下方按钮重新开始", viewWidth / 2f, viewHeight / 2f + 50 * density, paint);
    }

    // ==================== 游戏循环 ====================

    public void update() {
        if (!gameRunning || gamePaused) return;

        if (!waveActive) {
            // 波次间歇期
            if (System.currentTimeMillis() - waveStartTime > 2000) {
                startWave();
            }
            invalidate();
            return;
        }

        long now = System.currentTimeMillis();

        // 玩家跟随触摸移动
        if (touching) {
            float dx = touchX - playerX;
            float dy = touchY - playerY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 5) {
                float speed = 5f;
                playerX += (dx / dist) * speed;
                playerY += (dy / dist) * speed;
                // 限制在屏幕内
                playerX = Math.max(playerSize, Math.min(playerX, viewWidth - playerSize));
                playerY = Math.max(playerSize, Math.min(playerY, viewHeight - playerSize - 60 * density));
            }
        }

        // 自动射击（朝最近的敌人方向）
        if (now - lastShootTime >= SHOOT_INTERVAL_MS && !enemies.isEmpty()) {
            float[] nearest = findNearestEnemy();
            if (nearest != null) {
                float dx = nearest[0] - playerX;
                float dy = nearest[1] - playerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0) {
                    float vx = (dx / dist) * BULLET_SPEED;
                    float vy = (dy / dist) * BULLET_SPEED;
                    bullets.add(new float[]{playerX, playerY, vx, vy});
                }
            }
            lastShootTime = now;
        }

        // 子弹移动
        Iterator<float[]> bulletIter = bullets.iterator();
        while (bulletIter.hasNext()) {
            float[] bullet = bulletIter.next();
            bullet[0] += bullet[2];
            bullet[1] += bullet[3];
            if (bullet[0] < 0 || bullet[0] > viewWidth || bullet[1] < 0 || bullet[1] > viewHeight) {
                bulletIter.remove();
            }
        }

        // 生成敌人
        if (enemiesSpawned < enemiesInWave) {
            float dm = difficultyFactor / 0.5f;
            float spawnInterval = Math.max(300, (1500 - wave * 100) / dm);
            if (now - lastEnemySpawnTime >= spawnInterval) {
                spawnEnemy();
                lastEnemySpawnTime = now;
            }
        }

        // 敌人移动（朝玩家方向）
        for (float[] enemy : enemies) {
            float dx = playerX - enemy[0];
            float dy = playerY - enemy[1];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                enemy[0] += (dx / dist) * enemy[3];
                enemy[1] += (dy / dist) * enemy[3];
            }
        }

        // 子弹 vs 敌人碰撞
        for (int bi = bullets.size() - 1; bi >= 0; bi--) {
            float[] bullet = bullets.get(bi);
            for (int ei = enemies.size() - 1; ei >= 0; ei--) {
                float[] enemy = enemies.get(ei);
                float dx = bullet[0] - enemy[0];
                float dy = bullet[1] - enemy[1];
                if (dx * dx + dy * dy < (enemySize + bulletSize) * (enemySize + bulletSize)) {
                    bullets.remove(bi);
                    enemy[2] -= 1;
                    if (enemy[2] <= 0) {
                        enemies.remove(ei);
                        enemiesKilled++;
                        score += 10 + wave;
                        if (listener != null) {
                            listener.onScoreChanged(score);
                        }
                    }
                    break;
                }
            }
        }

        // 敌人 vs 玩家碰撞
        Iterator<float[]> enemyIter = enemies.iterator();
        while (enemyIter.hasNext()) {
            float[] enemy = enemyIter.next();
            float dx = playerX - enemy[0];
            float dy = playerY - enemy[1];
            if (dx * dx + dy * dy < (playerSize + enemySize) * (playerSize + enemySize)) {
                hp--;
                enemyIter.remove();
                if (hp <= 0) {
                    gameRunning = false;
                    gameOver = true;
                    if (listener != null) {
                        listener.onGameOver(score, wave);
                    }
                    invalidate();
                    return;
                }
            }
        }

        // 波次完成检测
        if (enemiesKilled >= enemiesInWave && enemies.isEmpty()) {
            waveActive = false;
            waveStartTime = now;
            // 恢复生命
            hp = Math.min(hp + 3, maxHp);
            if (listener != null) {
                listener.onWaveComplete(wave);
            }
            wave++;
        }

        invalidate();
    }

    private void spawnEnemy() {
        // 从屏幕边缘生成
        float ex, ey;
        int side = random.nextInt(4);
        switch (side) {
            case 0: ex = random.nextFloat() * viewWidth; ey = -enemySize; break;
            case 1: ex = viewWidth + enemySize; ey = random.nextFloat() * viewHeight; break;
            case 2: ex = random.nextFloat() * viewWidth; ey = viewHeight + enemySize; break;
            default: ex = -enemySize; ey = random.nextFloat() * viewHeight; break;
        }
        float dm = difficultyFactor / 0.5f;
        float enemyHp = wave >= 5 ? (random.nextInt(3) == 0 ? 3 : 1) : 1;
        enemyHp = Math.max(1f, enemyHp * dm);
        float speed = (1.5f + wave * 0.15f + random.nextFloat()) * dm;
        enemies.add(new float[]{ex, ey, enemyHp, speed});
        enemiesSpawned++;
    }

    private float[] findNearestEnemy() {
        float minDist = Float.MAX_VALUE;
        float[] nearest = null;
        for (float[] enemy : enemies) {
            float dx = enemy[0] - playerX;
            float dy = enemy[1] - playerY;
            float dist = dx * dx + dy * dy;
            if (dist < minDist) {
                minDist = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!gameRunning || gamePaused) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touching = true;
                touchX = event.getX();
                touchY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                touching = false;
                break;
        }
        return true;
    }
}
