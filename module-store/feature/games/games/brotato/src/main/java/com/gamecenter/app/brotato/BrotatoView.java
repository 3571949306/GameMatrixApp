package com.gamecenter.app.brotato;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.brotato.engine.Enemy;
import com.gamecenter.app.brotato.engine.Projectile;
import com.gamecenter.app.brotato.engine.Upgrade;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * 土豆兄弟游戏自定义 View（独立 APK 模块深度改造版）。
 *
 * <p>特性增强：
 * 1. 英雄职业视觉体系（6 位特色英雄专属配色、头饰与武器形态）；
 * 2. 多武器环绕挂载与动态瞄准系统（多枪旋转瞄准 + 枪口火光与射击后坐力）；
 * 3. 战场掉落与磁吸系统（击杀掉落翡翠经验宝石，物理散落 + 磁力线吸附，实时材料记账）；
 * 4. 战斗打击感强化（屏幕震颤、受击白闪/红闪、暴击大字浮动伤害、残血危险暗角红晕）；
 * 5. 虚拟浮动摇杆（360° 丝滑风筝走位，同时兼容直接点触）；
 * 6. 全息升级卡片与卡牌品质光环展示。</p>
 */
public class BrotatoView extends View {

    public interface OnGameListener {
        void onScoreChanged(int score);

        void onGameOver(int score, int wave);

        void onWaveComplete(int wave);

        default void onWin(int score) { }

        default void onRewardCards(int wave, String[] titles) { }

        default void onMaterialsChanged(int materials) { }
    }

    private static final float HUD_RESERVE_DP = 64f;
    private static final float GRID_STEP_DP = 40f;
    private static final float MIN_BULLET_DRAW_DP = 3.5f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final Random random = new Random();

    private float viewWidth;
    private float viewHeight;
    private float density = 1f;

    private BrotatoContent content;
    private BrotatoArena arena;
    private OnGameListener listener;
    private float difficultyScalar = BrotatoArena.DIFFICULTY_NORMAL;
    private long seed = System.nanoTime();
    private boolean gameRunning;
    private boolean gamePaused;
    private boolean pendingStart;

    /** 当前出战英雄，默认全能土豆 */
    private BrotatoHero currentHero = BrotatoHero.ALL_ROUNDER;

    /** 特效系统与掉落物 */
    private final BrotatoEffects effects = new BrotatoEffects();
    private final Map<Enemy, Float> enemyHpMap = new HashMap<>();
    private int collectedMaterials = 0;
    private int prevPlayerHp = BrotatoArena.BASE_MAX_HP;
    private int damageFlashTicks = 0;
    private long lastFireMs = 0L;
    private int prevProjectileCount = 0;
    private long animTick = 0L;

    /** 虚拟摇杆状态 */
    private boolean joystickActive = false;
    private float joystickCenterX = 0f;
    private float joystickCenterY = 0f;
    private float joystickKnobX = 0f;
    private float joystickKnobY = 0f;

    private final BrotatoArena.Listener arenaListener = new BrotatoArena.Listener() {
        @Override
        public void onScoreChanged(int score) {
            OnGameListener value = listener;
            if (value != null) value.onScoreChanged(score);
        }

        @Override
        public void onWaveComplete(int wave) {
            OnGameListener value = listener;
            if (value != null) value.onWaveComplete(wave);
        }

        @Override
        public void onGameOver(int score, int wave) {
            gameRunning = false;
            joystickActive = false;
            OnGameListener value = listener;
            if (value != null) value.onGameOver(score, wave);
            invalidate();
        }

        @Override
        public void onWin(int score) {
            gameRunning = false;
            joystickActive = false;
            OnGameListener value = listener;
            if (value != null) value.onWin(score);
            invalidate();
        }

        @Override
        public void onRewardCards(int wave, String[] titles) {
            joystickActive = false;
            OnGameListener value = listener;
            if (value != null) value.onRewardCards(wave, titles);
            invalidate();
        }
    };

    public BrotatoView(Context context) {
        super(context);
        init();
    }

    public BrotatoView(Context context, BrotatoContent content) {
        super(context);
        this.content = content;
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(isNightMode() ? 0xFF0E1016 : 0xFF141720);
    }

    private boolean isNightMode() {
        int nightMode = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    public void setDifficultyScalar(float scalar) {
        this.difficultyScalar = scalar;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    public void setHero(BrotatoHero hero) {
        this.currentHero = (hero != null) ? hero : BrotatoHero.ALL_ROUNDER;
        if (arena != null && gameRunning) {
            // 下一局或重新开始时彻底生效
        }
    }

    public BrotatoHero getHero() {
        return currentHero;
    }

    public int getCollectedMaterials() {
        return collectedMaterials;
    }

    public void startGame() {
        content = requireContent();
        pendingStart = true;
        gameRunning = true;
        gamePaused = false;
        collectedMaterials = 0;
        joystickActive = false;
        createArenaIfPossible();
        invalidate();
    }

    private void createArenaIfPossible() {
        if (!pendingStart || content == null) return;
        float width = viewWidth / density;
        float height = playfieldHeightPx() / density;
        if (width <= 0f || height <= 0f) return;
        try {
            BrotatoArena value = new BrotatoArena(content, difficultyScalar, seed);
            value.setListener(arenaListener);
            value.setBounds(width, height);
            value.start();
            if (currentHero != null) {
                currentHero.applyTo(value);
            }
            arena = value;
            effects.clear();
            enemyHpMap.clear();
            prevPlayerHp = value.hp();
            prevProjectileCount = 0;
        } catch (RuntimeException exception) {
            android.util.Log.e("BrotatoView", "failed to start brotato arena", exception);
            gameRunning = false;
        }
        pendingStart = false;
    }

    public void pauseGame() {
        gamePaused = true;
    }

    public void resumeGame() {
        gamePaused = false;
    }

    public void stopGame() {
        gameRunning = false;
        pendingStart = false;
        joystickActive = false;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public int getScore() {
        return arena == null ? 0 : arena.score();
    }

    public int getWave() {
        return arena == null ? 0 : arena.wave();
    }

    public int getContentVersion() {
        return content == null ? 0 : content.contentVersion();
    }

    private BrotatoContent requireContent() {
        if (content == null) {
            throw new IllegalStateException("brotato content must be injected via Fragment module assets, never host");
        }
        return content;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        createArenaIfPossible();
        BrotatoArena value = arena;
        if (value != null && value.isStarted()) {
            float width = w / density;
            float height = playfieldHeightPx() / density;
            if (width > 0f && height > 0f) value.setBounds(width, height);
        }
    }

    private float playfieldHeightPx() {
        return Math.max(0f, viewHeight - HUD_RESERVE_DP * density);
    }

    // ==================== 游戏主循环 ====================

    public void update() {
        if (!gameRunning || gamePaused) return;
        if (arena == null) {
            createArenaIfPossible();
            if (arena == null) return;
        }

        animTick++;
        if (damageFlashTicks > 0) damageFlashTicks--;

        // 检测开火时刻
        int currentProjCount = arena.projectiles().size();
        if (currentProjCount > prevProjectileCount) {
            lastFireMs = arena.clockMs();
        }
        prevProjectileCount = currentProjCount;

        // 推进引擎逻辑
        arena.tick();

        // 碰撞与受击跟踪（伤害浮字、掉落宝石与屏幕震颤）
        trackCombatEvents(arena);

        // 推进特效与拾取
        float magnetRadius = 60f * currentHero.getMagnetRangeFactor();
        int newlyCollected = effects.update(arena.playerX(), arena.playerY(), magnetRadius, arena.playerRadiusPx());
        if (newlyCollected > 0) {
            collectedMaterials += newlyCollected;
            if (listener != null) {
                listener.onMaterialsChanged(collectedMaterials);
            }
        }

        postInvalidateOnAnimation();
    }

    /**
     * 跟踪敌人血量变化与玩家受击，触发打击感特效
     */
    private void trackCombatEvents(BrotatoArena value) {
        // 1. 玩家受击检测
        int curHp = value.hp();
        if (curHp < prevPlayerHp) {
            damageFlashTicks = 8;
            effects.triggerScreenShake(6.5f, 8);
            effects.spawnBurst(value.playerX(), value.playerY(), 0xFFFF4444, 10, 4.0f);
        }
        prevPlayerHp = curHp;

        // 2. 敌人受伤与死亡检测
        Map<Enemy, Float> nextMap = new HashMap<>();
        for (Enemy enemy : value.enemies()) {
            Float prev = enemyHpMap.get(enemy);
            if (prev != null && enemy.hp < prev) {
                float damage = prev - enemy.hp;
                boolean isCrit = random.nextFloat() < currentHero.getCritChance();
                effects.spawnDamage(enemy.x, enemy.y, damage, isCrit);
                effects.spawnBurst(enemy.x, enemy.y, isCrit ? 0xFFFF7043 : 0xFFFFD54F, 4, 2.2f);
            }
            nextMap.put(enemy, enemy.hp);
        }

        // 检测死亡离场的敌人（掉落翡翠经验宝石）
        for (Map.Entry<Enemy, Float> entry : enemyHpMap.entrySet()) {
            Enemy oldEnemy = entry.getKey();
            if (!nextMap.containsKey(oldEnemy) && oldEnemy.removed) {
                int scoreVal = (oldEnemy.kind != null) ? oldEnemy.kind.score() : 1;
                float bonusChance = (currentHero == BrotatoHero.LUCKY) ? 0.4f : 0.08f;
                effects.spawnGemsOnKill(oldEnemy.x, oldEnemy.y, scoreVal, bonusChance);
                if (oldEnemy.kind != null && (oldEnemy.kind.score() >= 10 || oldEnemy.kind.id().contains("boss") || oldEnemy.maxHp >= 15f)) {
                    effects.triggerScreenShake(9f, 12);
                }
            }
        }
        enemyHpMap.clear();
        enemyHpMap.putAll(nextMap);
    }

    // ==================== 渲染绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BrotatoArena value = arena;
        if (value == null) return;

        paint.setStyle(Paint.Style.FILL);
        var state = value.state();
        if (state == BrotatoArena.State.GAME_OVER) {
            drawEndOverlay(canvas, value, "战斗落幕", "英雄战陨于荒原 · 点击下方重整旗鼓", 0xFFEF4444);
            return;
        }
        if (state == BrotatoArena.State.WIN) {
            drawEndOverlay(canvas, value, "旷世凯旋！", "二十波敌潮悉数荡平，角斗场终极主宰！", 0xFF10B981);
            return;
        }
        if (!gameRunning) return;

        // 屏幕震颤应用
        canvas.save();
        canvas.translate(effects.getShakeOffsetX() * density, effects.getShakeOffsetY() * density);

        drawGrid(canvas);
        effects.draw(canvas, paint, density);
        drawProjectiles(canvas, value);
        drawEnemies(canvas, value);
        drawPlayer(canvas, value);

        canvas.restore();

        // 绘制界面与覆盖层（不受震颤影响）
        drawDangerVignette(canvas, value);
        drawJoystick(canvas);
        drawHud(canvas, value);

        if (state == BrotatoArena.State.REWARD_PENDING) {
            drawRewardCards(canvas, value);
        } else if (state == BrotatoArena.State.INTERMISSION) {
            drawIntermissionBanner(canvas, value);
        }
    }

    private void drawGrid(Canvas canvas) {
        paint.setColor(0x18FFFFFF);
        paint.setStrokeWidth(1);
        float step = GRID_STEP_DP * density;
        float playH = playfieldHeightPx();
        for (float x = 0; x < viewWidth; x += step) {
            canvas.drawLine(x, 0, x, playH, paint);
        }
        for (float y = 0; y < playH; y += step) {
            canvas.drawLine(0, y, viewWidth, y, paint);
        }
    }

    private void drawProjectiles(Canvas canvas, BrotatoArena value) {
        for (Projectile bullet : value.projectiles()) {
            float bx = bullet.x * density;
            float by = bullet.y * density;
            float br = Math.max(MIN_BULLET_DRAW_DP, bullet.radiusPx) * density;

            // 子弹尾迹微光
            paint.setColor(currentHero == BrotatoHero.MAGE ? 0x66A855F7 : 0x66FDE047);
            canvas.drawCircle(bx, by, br * 1.6f, paint);

            // 子弹本体
            paint.setColor(currentHero == BrotatoHero.MAGE ? 0xFFC084FC : 0xFFFFEB3B);
            canvas.drawCircle(bx, by, br, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(bx, by, br * 0.45f, paint);
        }
    }

    private void drawEnemies(Canvas canvas, BrotatoArena value) {
        for (Enemy enemy : value.enemies()) {
            float cx = enemy.x * density;
            float cy = enemy.y * density;
            float radius = enemy.radiusPx * density;

            // 阴影
            paint.setColor(0x33000000);
            canvas.drawOval(new RectF(cx - radius * 0.9f, cy + radius * 0.6f, cx + radius * 0.9f, cy + radius * 1.15f), paint);

            // 身体
            paint.setColor(enemy.kind == null ? 0xFFFF5722 : enemy.kind.color());
            canvas.drawCircle(cx, cy, radius, paint);

            // 边缘深色边线
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(0x44000000);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);

            // 眼睛
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - radius * 0.35f, cy - radius * 0.25f, radius * 0.26f, paint);
            canvas.drawCircle(cx + radius * 0.35f, cy - radius * 0.25f, radius * 0.26f, paint);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.25f, radius * 0.12f, paint);
            canvas.drawCircle(cx + radius * 0.42f, cy - radius * 0.25f, radius * 0.12f, paint);

            if (enemy.hp < enemy.maxHp) drawEnemyHealth(canvas, enemy, cx, cy, radius);
        }
    }

    private void drawEnemyHealth(Canvas canvas, Enemy enemy, float cx, float cy, float radius) {
        float width = radius * 2.2f;
        float height = 3.5f * density;
        float top = cy - radius - 8 * density;
        float ratio = enemy.hpRatio();
        paint.setColor(0x88000000);
        canvas.drawRoundRect(new RectF(cx - width / 2f, top, cx + width / 2f, top + height), 2 * density, 2 * density, paint);
        paint.setColor(0xFFEF4444);
        canvas.drawRoundRect(new RectF(cx - width / 2f, top, cx - width / 2f + width * ratio, top + height), 2 * density, 2 * density, paint);
    }

    /**
     * 绘制土豆主角与英雄专属装备外观
     */
    private void drawPlayer(Canvas canvas, BrotatoArena value) {
        float px = value.playerX() * density;
        float py = value.playerY() * density;
        float ps = value.playerRadiusPx() * density;

        // 1. 角色地面软阴影
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x44000000);
        canvas.drawOval(new RectF(px - ps * 1.15f, py + ps * 0.65f, px + ps * 1.15f, py + ps * 1.25f), paint);

        // 2. 土豆肉身（受击红白交替闪烁）
        int bodyColor = (damageFlashTicks > 0) ? 0xFFFF6B6B : currentHero.getBodyColor();
        paint.setColor(bodyColor);
        canvas.drawCircle(px, py, ps, paint);
        paint.setColor(0xFF5D4037);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawCircle(px, py, ps, paint);
        paint.setStyle(Paint.Style.FILL);

        // 3. 英雄专属装饰（头饰、战纹、帽子、甲胄、四叶草、羽翼）
        drawHeroApparel(canvas, px, py, ps);

        // 4. 大眼睛与灵动高光
        paint.setColor(Color.WHITE);
        canvas.drawCircle(px - ps * 0.3f, py - ps * 0.18f, ps * 0.28f, paint);
        canvas.drawCircle(px + ps * 0.3f, py - ps * 0.18f, ps * 0.28f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(px - ps * 0.24f, py - ps * 0.18f, ps * 0.13f, paint);
        canvas.drawCircle(px + ps * 0.36f, py - ps * 0.18f, ps * 0.13f, paint);
        // 眼内灵动反光
        paint.setColor(Color.WHITE);
        canvas.drawCircle(px - ps * 0.28f, py - ps * 0.23f, ps * 0.05f, paint);
        canvas.drawCircle(px + ps * 0.32f, py - ps * 0.23f, ps * 0.05f, paint);

        // 5. 坚毅微笑嘴型
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0xFF4E342E);
        canvas.drawArc(new RectF(px - ps * 0.28f, py + ps * 0.08f, px + ps * 0.28f, py + ps * 0.42f),
                0, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // 6. 绘制动态旋转瞄准的多枪武器系统
        drawWeapons(canvas, value, px, py, ps);
    }

    /**
     * 英雄专属穿戴
     */
    private void drawHeroApparel(Canvas canvas, float px, float py, float ps) {
        int accent = currentHero.getAccentColor();
        paint.setColor(accent);

        switch (currentHero) {
            case ALL_ROUNDER:
                // 经典红色英雄头带 + 飘带
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(px - ps * 0.95f, py - ps * 0.65f, px + ps * 0.95f, py - ps * 0.35f, paint);
                // 飘带
                tempPath.reset();
                tempPath.moveTo(px - ps * 0.95f, py - ps * 0.5f);
                tempPath.lineTo(px - ps * 1.55f, py - ps * 0.75f);
                tempPath.lineTo(px - ps * 1.45f, py - ps * 0.35f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                break;

            case BRAWLER:
                // 愤怒战纹 + 红色额前烈焰
                tempPath.reset();
                tempPath.moveTo(px - ps * 0.5f, py - ps * 0.8f);
                tempPath.lineTo(px, py - ps * 1.35f);
                tempPath.lineTo(px + ps * 0.5f, py - ps * 0.8f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                break;

            case MAGE:
                // 奥术巫师尖顶高帽
                tempPath.reset();
                tempPath.moveTo(px - ps * 1.1f, py - ps * 0.6f);
                tempPath.lineTo(px + ps * 1.1f, py - ps * 0.6f);
                tempPath.lineTo(px, py - ps * 1.75f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 帽檐黄色星芒扣
                paint.setColor(0xFFFBBF24);
                canvas.drawCircle(px, py - ps * 0.6f, ps * 0.2f, paint);
                break;

            case CHUNKY:
                // 重装钢铁铆钉肩铠
                paint.setColor(0xFF475569);
                canvas.drawRoundRect(new RectF(px - ps * 1.35f, py - ps * 0.4f, px - ps * 0.75f, py + ps * 0.4f), 3 * density, 3 * density, paint);
                canvas.drawRoundRect(new RectF(px + ps * 0.75f, py - ps * 0.4f, px + ps * 1.35f, py + ps * 0.4f), 3 * density, 3 * density, paint);
                paint.setColor(0xFF94A3B8);
                canvas.drawCircle(px - ps * 1.05f, py, 2 * density, paint);
                canvas.drawCircle(px + ps * 1.05f, py, 2 * density, paint);
                break;

            case LUCKY:
                // 幸运四叶草头饰
                paint.setColor(0xFF22C55E);
                canvas.drawCircle(px - ps * 0.2f, py - ps * 1.15f, ps * 0.22f, paint);
                canvas.drawCircle(px + ps * 0.2f, py - ps * 1.15f, ps * 0.22f, paint);
                canvas.drawCircle(px, py - ps * 1.35f, ps * 0.22f, paint);
                canvas.drawCircle(px, py - ps * 0.95f, ps * 0.18f, paint);
                break;

            case SPEEDSTER:
                // 疾风幽灵羽翼头饰
                paint.setColor(0xFF38BDF8);
                tempPath.reset();
                tempPath.moveTo(px - ps * 0.8f, py - ps * 0.4f);
                tempPath.lineTo(px - ps * 1.6f, py - ps * 0.9f);
                tempPath.lineTo(px - ps * 1.2f, py - ps * 0.1f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                tempPath.reset();
                tempPath.moveTo(px + ps * 0.8f, py - ps * 0.4f);
                tempPath.lineTo(px + ps * 1.6f, py - ps * 0.9f);
                tempPath.lineTo(px + ps * 1.2f, py - ps * 0.1f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                break;
        }
    }

    /**
     * 绘制多武器环绕挂载与动态瞄准系统
     */
    private void drawWeapons(Canvas canvas, BrotatoArena value, float px, float py, float ps) {
        int shots = Math.max(1, value.shotCount());
        Enemy target = value.nearestEnemy();

        double baseAngle;
        if (target != null) {
            baseAngle = Math.atan2(target.y - value.playerY(), target.x - value.playerX());
        } else {
            baseAngle = (animTick * 0.04) % (Math.PI * 2);
        }

        double spreadRad = Math.toRadians(Upgrade.SPREAD_STEP_DEG);
        float orbitDist = ps + 12 * density;
        boolean justFired = (value.clockMs() - lastFireMs < 64);

        for (int i = 0; i < shots; i++) {
            double angle = baseAngle + (i - (shots - 1) / 2.0) * spreadRad;
            float wx = px + (float) (Math.cos(angle - 0.28) * orbitDist);
            float wy = py + (float) (Math.sin(angle - 0.28) * orbitDist);

            canvas.save();
            canvas.translate(wx, wy);
            canvas.rotate((float) Math.toDegrees(angle));

            // 后坐力偏移
            if (justFired) {
                canvas.translate(-2.5f * density, 0);
            }

            // 绘制枪管/法杖
            drawGunBarrel(canvas);

            // 枪口火花
            if (justFired) {
                drawMuzzleFlash(canvas);
            }

            canvas.restore();
        }
    }

    private void drawGunBarrel(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        // 枪身阴影与枪托
        paint.setColor(0xFF374151);
        canvas.drawRoundRect(new RectF(0, -3.5f * density, 16 * density, 3.5f * density), 2 * density, 2 * density, paint);
        // 枪管高光
        paint.setColor(0xFF9CA3AF);
        canvas.drawRoundRect(new RectF(4 * density, -2 * density, 18 * density, 2 * density), 1.5f * density, 1.5f * density, paint);
        // 握把
        paint.setColor(0xFF78350F);
        canvas.drawRect(-3 * density, 0, 1 * density, 6 * density, paint);
    }

    private void drawMuzzleFlash(Canvas canvas) {
        paint.setColor(0xFFFFEB3B);
        float tipX = 20 * density;
        float flashS = 7 * density;
        tempPath.reset();
        tempPath.moveTo(tipX, -flashS * 0.5f);
        tempPath.lineTo(tipX + flashS, 0);
        tempPath.lineTo(tipX, flashS * 0.5f);
        tempPath.lineTo(tipX - flashS * 0.3f, 0);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        paint.setColor(0xFFFFA000);
        canvas.drawCircle(tipX + flashS * 0.3f, 0, flashS * 0.4f, paint);
    }

    /**
     * 残血危险暗角红晕
     */
    private void drawDangerVignette(Canvas canvas, BrotatoArena value) {
        float ratio = (float) value.hp() / Math.max(1, value.maxHp());
        if (ratio > 0.35f) return;

        float pulse = (float) (0.6f + 0.4f * Math.sin(animTick * 0.18f));
        int alpha = (int) ((1f - ratio / 0.35f) * 110 * pulse);
        if (alpha <= 0) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(16 * density);
        paint.setColor(Color.argb(alpha, 220, 38, 38));
        canvas.drawRect(0, 0, viewWidth, playfieldHeightPx(), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * 绘制虚拟触摸摇杆
     */
    private void drawJoystick(Canvas canvas) {
        if (!joystickActive) return;

        float baseRadius = 46 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33000000);
        canvas.drawCircle(joystickCenterX, joystickCenterY, baseRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0x8860A5FA);
        canvas.drawCircle(joystickCenterX, joystickCenterY, baseRadius, paint);

        // 摇杆连线
        paint.setColor(0x5593C5FD);
        paint.setStrokeWidth(3 * density);
        canvas.drawLine(joystickCenterX, joystickCenterY, joystickKnobX, joystickKnobY, paint);

        // 摇杆内钮
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC2563EB);
        float knobRadius = 20 * density;
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0xFF93C5FD);
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * HUD 看板（顶底信息、生命槽、材料宝石）
     */
    private void drawHud(Canvas canvas, BrotatoArena value) {
        float hpBarH = 16 * density;
        float hpBarBottom = viewHeight - 26 * density;
        float hpBarTop = hpBarBottom - hpBarH;
        int hp = value.hp();
        int maxHp = Math.max(1, value.maxHp());

        // 底部生命底槽
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(16 * density, hpBarTop, viewWidth - 16 * density, hpBarBottom), 8 * density, 8 * density, paint);

        float hpRatio = (float) hp / maxHp;
        int barColor = (hpRatio > 0.5f) ? 0xFF10B981 : (hpRatio > 0.25f) ? 0xFFF59E0B : 0xFFEF4444;
        paint.setColor(barColor);
        canvas.drawRoundRect(new RectF(16 * density, hpBarTop,
                16 * density + (viewWidth - 32 * density) * hpRatio, hpBarBottom), 8 * density, 8 * density, paint);

        // 生命数字与当前英雄标签
        paint.setColor(Color.WHITE);
        paint.setTextSize(13 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(currentHero.getEmoji() + " " + hp + " / " + maxHp, viewWidth / 2f, hpBarBottom - 3 * density, paint);

        // 顶部 HUD 文本
        paint.setTextSize(18 * density);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("分: " + value.score(), 16 * density, 30 * density, paint);

        // 实时掉落材料统计
        paint.setColor(0xFF10B981);
        canvas.drawText("💎 " + collectedMaterials, 120 * density, 30 * density, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.WHITE);
        canvas.drawText("波次 " + value.wave() + " / " + value.content().totalWaves(),
                viewWidth - 16 * density, 30 * density, paint);

        if (value.state() != BrotatoArena.State.INTERMISSION) {
            paint.setTextSize(12 * density);
            paint.setColor(0xFF94A3B8);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("剩余敌寇 " + (value.remainingSpawns() + value.remainingBosses())
                    + " · 场上 " + value.enemies().size(), viewWidth / 2f, 50 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawIntermissionBanner(Canvas canvas, BrotatoArena value) {
        float band = 44 * density;
        float middle = playfieldHeightPx() / 2f;
        paint.setColor(0xCC0F172A);
        canvas.drawRect(0, middle - band, viewWidth, middle + band, paint);

        paint.setColor(0xFF38BDF8);
        paint.setTextSize(26 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("⚔️ 第 " + value.wave() + " 波准备就绪", viewWidth / 2f,
                middle + 6 * density, paint);

        paint.setTextSize(14 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFCBD5E1);
        canvas.drawText(value.upcomingWaveTotal() + " 只异变体迫近 · 倒计时 "
                + String.format(java.util.Locale.US, "%.1f", value.intermissionRemainingMs() / 1000f) + "s",
                viewWidth / 2f, middle + 30 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * 奖励全息卡片选择
     */
    private void drawRewardCards(Canvas canvas, BrotatoArena value) {
        Upgrade[] cards = value.pendingCards();
        if (cards == null || cards.length == 0) return;

        paint.setColor(0xDD090D16);
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFBBF24);
        paint.setTextSize(24 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("⚡ 战地强化选择 ⚡", viewWidth / 2f, viewHeight * 0.18f, paint);

        paint.setTextSize(14 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("波次 " + value.wave() + " 肃清达成 · 选取一项核心专精进化", viewWidth / 2f,
                viewHeight * 0.18f + 26 * density, paint);

        for (int i = 0; i < cards.length; i++) {
            drawCard(canvas, value, cards[i], rewardCardRect(i, cards.length), i);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCard(Canvas canvas, BrotatoArena value, Upgrade upgrade, RectF rect, int index) {
        // 卡牌背景深蓝渐变质感
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);

        // 卡牌外光环
        int tierColor = getCardAccent(upgrade);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * density);
        paint.setColor(tierColor);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);
        paint.setStyle(Paint.Style.FILL);

        // 卡牌图标徽章
        paint.setColor(0x33FFFFFF);
        canvas.drawCircle(rect.centerX(), rect.top + 38 * density, 20 * density, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22 * density);
        canvas.drawText(getCardIcon(upgrade), rect.centerX(), rect.top + 45 * density, paint);

        // 卡牌标题
        paint.setColor(Color.WHITE);
        paint.setTextSize(16 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText(upgrade.displayNameCn(), rect.centerX(), rect.top + 80 * density, paint);

        // 说明文案
        paint.setTextSize(12.5f * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFE2E8F0);
        drawWrapped(canvas, upgrade.descriptionCn(), rect.centerX(), rect.top + 104 * density,
                rect.width() - 16 * density, 18 * density);

        // 持有层数展示
        int owned = value.upgradeCount(upgrade);
        if (owned > 0) {
            paint.setColor(0xFFFBBF24);
            paint.setTextSize(12 * density);
            canvas.drawText("★ 已持有 " + owned + " 阶", rect.centerX(), rect.bottom - 16 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private int getCardAccent(Upgrade upgrade) {
        switch (upgrade) {
            case DAMAGE_UP: return 0xFFEF4444; // 红
            case RAPID_FIRE: return 0xFFF59E0B; // 橙黄
            case MULTISHOT: return 0xFF8B5CF6; // 紫
            case SWIFT_BOOTS: return 0xFF06B6D4; // 青
            case VITALITY: return 0xFF10B981; // 绿
            case REGEN: default: return 0xFF3B82F6; // 蓝
        }
    }

    private String getCardIcon(Upgrade upgrade) {
        switch (upgrade) {
            case RAPID_FIRE: return "⚡";
            case SWIFT_BOOTS: return "👢";
            case VITALITY: return "❤️";
            case MULTISHOT: return "🎯";
            case DAMAGE_UP: return "💥";
            case REGEN: default: return "🌿";
        }
    }

    private void drawWrapped(Canvas canvas, String text, float centerX, float baseline,
                             float maxWidth, float lineHeight) {
        float y = baseline;
        int cursor = 0;
        while (cursor < text.length()) {
            int count = paint.breakText(text, cursor, text.length(), true, maxWidth, null);
            if (count <= 0) return;
            canvas.drawText(text, cursor, cursor + count, centerX, y, paint);
            cursor += count;
            y += lineHeight;
        }
    }

    private RectF rewardCardRect(int index, int cardCount) {
        float gap = 10 * density;
        float outer = 14 * density;
        float available = viewWidth - outer * 2f - gap * (cardCount - 1);
        float cardW = Math.min(145 * density, available / cardCount);
        float cardH = Math.min(210 * density, viewHeight * 0.44f);
        float blockW = cardW * cardCount + gap * (cardCount - 1);
        float left = (viewWidth - blockW) / 2f + index * (cardW + gap);
        float top = (playfieldHeightPx() - cardH) / 2f + 16 * density;
        return new RectF(left, top, left + cardW, top + cardH);
    }

    private void drawEndOverlay(Canvas canvas, BrotatoArena value, String title, String hint, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 10, 15, 25));
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(accent);
        paint.setTextSize(34 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText(title, viewWidth / 2f, viewHeight / 2f - 40 * density, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(18 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("总战功 " + value.score() + " · 收集战利品 💎 " + collectedMaterials,
                viewWidth / 2f, viewHeight / 2f + 4 * density, paint);

        paint.setTextSize(15 * density);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("坚持至波次 " + value.wave() + " / " + value.content().totalWaves(),
                viewWidth / 2f, viewHeight / 2f + 32 * density, paint);

        paint.setTextSize(14 * density);
        paint.setColor(0xFF38BDF8);
        canvas.drawText(hint, viewWidth / 2f, viewHeight / 2f + 68 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ==================== 触摸事件与摇杆响应 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        BrotatoArena value = arena;
        if (!gameRunning || gamePaused || value == null) return true;

        if (value.state() == BrotatoArena.State.REWARD_PENDING) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                pickRewardCard(value, event.getX(), event.getY());
                value.setTouchTarget(value.playerX(), value.playerY(), false);
            }
            return true;
        }

        int action = event.getActionMasked();
        float touchX = event.getX();
        float touchY = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            joystickActive = true;
            joystickCenterX = touchX;
            joystickCenterY = touchY;
            joystickKnobX = touchX;
            joystickKnobY = touchY;
            value.setTouchTarget(touchX / density, touchY / density, true);
        } else if (action == MotionEvent.ACTION_MOVE && joystickActive) {
            float dx = touchX - joystickCenterX;
            float dy = touchY - joystickCenterY;
            float maxR = 48 * density;
            float dist = (float) Math.hypot(dx, dy);

            if (dist > maxR) {
                joystickKnobX = joystickCenterX + (dx / dist) * maxR;
                joystickKnobY = joystickCenterY + (dy / dist) * maxR;
            } else {
                joystickKnobX = touchX;
                joystickKnobY = touchY;
            }

            if (dist > 4 * density) {
                float targetX = value.playerX() + (dx / dist) * 120f;
                float targetY = value.playerY() + (dy / dist) * 120f;
                value.setTouchTarget(targetX, targetY, true);
            } else {
                value.setTouchTarget(touchX / density, touchY / density, true);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            joystickActive = false;
            value.setTouchTarget(value.playerX(), value.playerY(), false);
        }
        return true;
    }

    private void pickRewardCard(BrotatoArena value, float x, float y) {
        Upgrade[] cards = value.pendingCards();
        if (cards == null) return;
        for (int i = 0; i < cards.length; i++) {
            if (rewardCardRect(i, cards.length).contains(x, y)) {
                value.selectUpgrade(i);
                invalidate();
                return;
            }
        }
    }
}
