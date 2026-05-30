package com.gamecenter.app.games.brotato;

import android.content.Context;
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
 * 土豆兄弟游戏自定义 View。
 *
 * <p>玩家控制土豆角色移动并自动射击，敌人从四周涌来。
 * 需要在波次中生存，每波结束后可恢复生命。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class BrotatoView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score, int wave);
        void onWaveComplete(int wave);
    }

    // ==================== 常量 ====================
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
    private boolean gamePaused = false;
    private long lastShootTime = 0;
    private long lastEnemySpawnTime = 0;
    private long waveStartTime = 0;

    private List<float[]> bullets = new ArrayList<>();      // [x, y, vx, vy]
    private List<float[]> enemies = new ArrayList<>();        // [x, y, hp, speed]
    private Random random = new Random();
    private OnGameListener listener;

    // ==================== 构造方法 ====================

    public BrotatoView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setBackgroundColor(0xFF1B1B2F);
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    // ==================== 游戏控制 ====================

    public void startGame() {
        this.score = 0;
        this.wave = 1;
        this.hp = 10;
        this.maxHp = 10;
        this.gameRunning = true;
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
        if (!gameRunning) return;

        // 绘制地面网格
        paint.setColor(0x22FFFFFF);
        paint.setStrokeWidth(1);
        for (float x = 0; x < viewWidth; x += 40) {
            canvas.drawLine(x, 0, x, viewHeight, paint);
        }
        for (float y = 0; y < viewHeight; y += 40) {
            canvas.drawLine(0, y, viewWidth, y, paint);
        }

        // 绘制子弹
        paint.setColor(0xFFFFEB3B);
        for (float[] bullet : bullets) {
            canvas.drawCircle(bullet[0], bullet[1], BULLET_SIZE, paint);
        }

        // 绘制敌人
        for (float[] enemy : enemies) {
            if (enemy[2] > 1) {
                paint.setColor(0xFFE53935); // 强敌红色
            } else {
                paint.setColor(0xFFFF5722); // 普通敌橙色
            }
            canvas.drawCircle(enemy[0], enemy[1], ENEMY_SIZE, paint);
            // 敌人眼睛
            paint.setColor(Color.WHITE);
            canvas.drawCircle(enemy[0] - 5, enemy[1] - 4, 4, paint);
            canvas.drawCircle(enemy[0] + 5, enemy[1] - 4, 4, paint);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(enemy[0] - 4, enemy[1] - 4, 2, paint);
            canvas.drawCircle(enemy[0] + 6, enemy[1] - 4, 2, paint);
        }

        // 绘制玩家（土豆）
        paint.setColor(0xFFD4A574);
        canvas.drawCircle(playerX, playerY, PLAYER_SIZE, paint);
        // 土豆轮廓
        paint.setColor(0xFF8D6E63);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(playerX, playerY, PLAYER_SIZE, paint);
        paint.setStyle(Paint.Style.FILL);
        // 土豆眼睛
        paint.setColor(Color.WHITE);
        canvas.drawCircle(playerX - 8, playerY - 6, 6, paint);
        canvas.drawCircle(playerX + 8, playerY - 6, 6, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(playerX - 6, playerY - 6, 3, paint);
        canvas.drawCircle(playerX + 10, playerY - 6, 3, paint);
        // 土豆微笑
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawArc(new RectF(playerX - 8, playerY + 2, playerX + 8, playerY + 12),
                0, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // 绘制武器指示（小枪）
        paint.setColor(0xFF9E9E9E);
        canvas.drawRect(playerX + PLAYER_SIZE, playerY - 3,
                playerX + PLAYER_SIZE + 16, playerY + 3, paint);

        // 绘制生命条
        paint.setColor(0xFF333333);
        canvas.drawRect(16, viewHeight - 50, viewWidth - 16, viewHeight - 34, paint);
        float hpRatio = (float) hp / maxHp;
        paint.setColor(hpRatio > 0.5f ? 0xFF4CAF50 : hpRatio > 0.25f ? 0xFFFF9800 : 0xFFF44336);
        canvas.drawRect(16, viewHeight - 50, 16 + (viewWidth - 32) * hpRatio, viewHeight - 34, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(16);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hp + "/" + maxHp, viewWidth / 2, viewHeight - 38, paint);

        // 绘制波次和分数
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("分：" + score, 16, 32, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("波次 " + wave, viewWidth - 16, 32, paint);

        // 波次间歇提示
        if (!waveActive) {
            paint.setColor(0xAA000000);
            canvas.drawRect(0, viewHeight / 2 - 40, viewWidth, viewHeight / 2 + 40, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("波次 " + wave + " 即将开始...", viewWidth / 2, viewHeight / 2 + 10, paint);
        }
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
                playerX = Math.max(PLAYER_SIZE, Math.min(playerX, viewWidth - PLAYER_SIZE));
                playerY = Math.max(PLAYER_SIZE, Math.min(playerY, viewHeight - PLAYER_SIZE - 60));
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
            float spawnInterval = Math.max(300, 1500 - wave * 100);
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
                if (dx * dx + dy * dy < (ENEMY_SIZE + BULLET_SIZE) * (ENEMY_SIZE + BULLET_SIZE)) {
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
            if (dx * dx + dy * dy < (PLAYER_SIZE + ENEMY_SIZE) * (PLAYER_SIZE + ENEMY_SIZE)) {
                hp--;
                enemyIter.remove();
                if (hp <= 0) {
                    gameRunning = false;
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
            case 0: ex = random.nextFloat() * viewWidth; ey = -ENEMY_SIZE; break;
            case 1: ex = viewWidth + ENEMY_SIZE; ey = random.nextFloat() * viewHeight; break;
            case 2: ex = random.nextFloat() * viewWidth; ey = viewHeight + ENEMY_SIZE; break;
            default: ex = -ENEMY_SIZE; ey = random.nextFloat() * viewHeight; break;
        }
        float enemyHp = wave >= 5 ? (random.nextInt(3) == 0 ? 3 : 1) : 1;
        float speed = 1.5f + wave * 0.15f + random.nextFloat();
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
