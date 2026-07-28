package com.gamecenter.app.plane;

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
 * 飞机大战游戏自定义 View（独立 APK 模块版本）。
 *
 * <p>由宿主 com.gamecenter.app.games.plane.PlaneView 迁移而来。
 * 移除了对宿主 R 资源的依赖，背景色支持浅色/深色主题。</p>
 *
 * <p>玩家控制飞机左右移动并自动射击，敌机从上方飞来。
 * 随着波次递增，敌机数量和速度增加。</p>
 */
public class PlaneView extends View {

    // ==================== 回调接口 ====================
    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score);
    }

    // ==================== 常量 ====================
    private static final float PLAYER_WIDTH = 40f;
    private static final float PLAYER_HEIGHT = 48f;
    private static final float BULLET_WIDTH = 4f;
    private static final float BULLET_HEIGHT = 16f;
    private static final float ENEMY_WIDTH = 36f;
    private static final float ENEMY_HEIGHT = 36f;
    private static final float BULLET_SPEED = 10f;
    private static final float INITIAL_ENEMY_SPEED = 2f;
    private static final long SHOOT_INTERVAL_MS = 200;
    private static final long ENEMY_SPAWN_INTERVAL_MS = 1500;

    // ==================== 游戏状态 ====================
    private Paint paint;
    private float viewWidth;
    private float viewHeight;
    private float playerX;
    private float playerY;
    private float touchX;
    private boolean touching = false;
    private int score = 0;
    private int wave = 1;
    private int lives = 3;
    private boolean gameRunning = false;
    private boolean gamePaused = false;
    private long lastShootTime = 0;
    private long lastEnemySpawnTime = 0;

    private List<float[]> bullets = new ArrayList<>();     // [x, y]
    private List<float[]> enemies = new ArrayList<>();      // [x, y, speed]
    private Random random = new Random();
    private OnGameListener listener;
    private float difficultyFactor = 0.5f;

    // ==================== 构造方法 ====================

    public PlaneView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setBackgroundColor(isNightMode() ? 0xFF0D1117 : 0xFF1A1A2E);
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

    public void startGame(int wave) {
        this.wave = wave;
        this.score = 0;
        this.lives = 3;
        this.gameRunning = true;
        this.gamePaused = false;
        this.touching = false;
        bullets.clear();
        enemies.clear();
        initGame();
        invalidate();
    }

    public void pauseGame() { gamePaused = true; }
    public void resumeGame() { gamePaused = false; }
    public void stopGame() { gameRunning = false; }
    public boolean isGameRunning() { return gameRunning; }
    public int getScore() { return score; }
    public int getWave() { return wave; }

    private void initGame() {
        playerX = viewWidth / 2 - PLAYER_WIDTH / 2;
        playerY = viewHeight - PLAYER_HEIGHT - 40;
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

        // 绘制星星背景
        paint.setColor(0x55FFFFFF);
        for (int i = 0; i < 20; i++) {
            float sx = (i * 37 + wave * 13) % (int) viewWidth;
            float sy = (i * 53 + System.currentTimeMillis() / 50) % (int) viewHeight;
            canvas.drawCircle(sx, sy, 1.5f, paint);
        }

        // 绘制子弹
        paint.setColor(0xFFFFEB3B);
        for (float[] bullet : bullets) {
            canvas.drawRect(bullet[0] - BULLET_WIDTH / 2, bullet[1],
                    bullet[0] + BULLET_WIDTH / 2, bullet[1] + BULLET_HEIGHT, paint);
        }

        // 绘制敌机
        paint.setColor(0xFF4CAF50);
        for (float[] enemy : enemies) {
            canvas.drawRect(enemy[0] - ENEMY_WIDTH / 2, enemy[1],
                    enemy[0] + ENEMY_WIDTH / 2, enemy[1] + ENEMY_HEIGHT, paint);
            // 敌机翅膀
            paint.setColor(0xFF388E3C);
            canvas.drawRect(enemy[0] - ENEMY_WIDTH / 2 - 8, enemy[1] + 8,
                    enemy[0] - ENEMY_WIDTH / 2, enemy[1] + ENEMY_HEIGHT - 8, paint);
            canvas.drawRect(enemy[0] + ENEMY_WIDTH / 2, enemy[1] + 8,
                    enemy[0] + ENEMY_WIDTH / 2 + 8, enemy[1] + ENEMY_HEIGHT - 8, paint);
            paint.setColor(0xFF4CAF50);
        }

        // 绘制玩家飞机
        paint.setColor(0xFFF44336);
        // 机身
        canvas.drawRect(playerX + PLAYER_WIDTH / 2 - 6, playerY,
                playerX + PLAYER_WIDTH / 2 + 6, playerY + PLAYER_HEIGHT, paint);
        // 机翼
        canvas.drawRect(playerX, playerY + PLAYER_HEIGHT * 0.4f,
                playerX + PLAYER_WIDTH, playerY + PLAYER_HEIGHT * 0.7f, paint);
        // 尾翼
        canvas.drawRect(playerX + 4, playerY + PLAYER_HEIGHT - 8,
                playerX + PLAYER_WIDTH - 4, playerY + PLAYER_HEIGHT, paint);

        // 绘制生命
        paint.setColor(Color.WHITE);
        paint.setTextSize(24);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("❤ × " + lives, 16, viewHeight - 16, paint);

        // 绘制分数和波次
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("分数: " + score, viewWidth - 16, 40, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(18);
        canvas.drawText("波次 " + wave, viewWidth / 2, viewHeight - 16, paint);
    }

    // ==================== 游戏循环 ====================

    public void update() {
        if (!gameRunning || gamePaused) return;

        long now = System.currentTimeMillis();

        // 玩家跟随触摸
        if (touching) {
            playerX += (touchX - playerX - PLAYER_WIDTH / 2) * 0.15f;
            playerX = Math.max(0, Math.min(playerX, viewWidth - PLAYER_WIDTH));
        }

        // 自动射击
        if (now - lastShootTime >= SHOOT_INTERVAL_MS) {
            bullets.add(new float[]{playerX + PLAYER_WIDTH / 2, playerY - BULLET_HEIGHT});
            lastShootTime = now;
        }

        // 子弹移动
        Iterator<float[]> bulletIter = bullets.iterator();
        while (bulletIter.hasNext()) {
            float[] bullet = bulletIter.next();
            bullet[1] -= BULLET_SPEED;
            if (bullet[1] + BULLET_HEIGHT < 0) {
                bulletIter.remove();
            }
        }

        // 生成敌机
        float dm = difficultyFactor / 0.5f;
        float enemySpeed = (INITIAL_ENEMY_SPEED + wave * 0.3f) * dm;
        if (now - lastEnemySpawnTime >= Math.max(500, (ENEMY_SPAWN_INTERVAL_MS - wave * 100) / dm)) {
            float ex = ENEMY_WIDTH / 2 + random.nextFloat() * (viewWidth - ENEMY_WIDTH);
            enemies.add(new float[]{ex, -ENEMY_HEIGHT, enemySpeed});
            lastEnemySpawnTime = now;
        }

        // 敌机移动
        Iterator<float[]> enemyIter = enemies.iterator();
        while (enemyIter.hasNext()) {
            float[] enemy = enemyIter.next();
            enemy[1] += enemy[2];
            if (enemy[1] > viewHeight) {
                enemyIter.remove();
                lives--;
                if (lives <= 0) {
                    gameOver();
                    return;
                }
            }
        }

        // 碰撞检测 - 子弹 vs 敌机
        for (int bi = bullets.size() - 1; bi >= 0; bi--) {
            float[] bullet = bullets.get(bi);
            for (int ei = enemies.size() - 1; ei >= 0; ei--) {
                float[] enemy = enemies.get(ei);
                if (bullet[0] >= enemy[0] - ENEMY_WIDTH / 2 && bullet[0] <= enemy[0] + ENEMY_WIDTH / 2
                        && bullet[1] <= enemy[1] + ENEMY_HEIGHT && bullet[1] + BULLET_HEIGHT >= enemy[1]) {
                    bullets.remove(bi);
                    enemies.remove(ei);
                    score += 10;
                    if (listener != null) {
                        listener.onScoreChanged(score);
                    }
                    break;
                }
            }
        }

        // 碰撞检测 - 敌机 vs 玩家
        for (float[] enemy : enemies) {
            if (enemy[0] + ENEMY_WIDTH / 2 > playerX && enemy[0] - ENEMY_WIDTH / 2 < playerX + PLAYER_WIDTH
                    && enemy[1] + ENEMY_HEIGHT > playerY && enemy[1] < playerY + PLAYER_HEIGHT) {
                lives--;
                // 移除碰撞的敌机
                enemies.remove(enemy);
                if (lives <= 0) {
                    gameOver();
                    return;
                }
                break;
            }
        }

        // 检查波次完成（每500分一波）
        if (score >= wave * 500) {
            wave++;
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

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!gameRunning || gamePaused) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touching = true;
                touchX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                touching = false;
                break;
        }
        return true;
    }
}
