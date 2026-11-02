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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 《蛋壳特工队》（Survivor.io）风格核心游戏视图：
 * 整合废土都市战场渲染、特工科萌及特色战术特工体系、四阶经验晶石与战术掉落物生态、
 * 顶栏绿色发光经验槽与三选一战术科技卡片、精英怪三叶草幸运宝箱老虎机、
 * 守卫者环绕飞盘、力场发生器/压制领域、战术伴飞无人机与幽灵手里剑超武进化！
 */
public class BrotatoView extends View {

    public interface OnGameListener {
        void onScoreChanged(int score);
        void onGameOver(int score, int wave);
        void onWaveComplete(int wave);
        default void onWin(int score) { }
        default void onRewardCards(int wave, String[] titles) { }
        default void onMaterialsChanged(int materials) { }
        default void onLevelChanged(int level) { }
    }

    private static final float HUD_RESERVE_DP = 56f;
    private static final float MIN_BULLET_DRAW_DP = 3.5f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    /** 当前出战特工，默认看板特工科萌 */
    private BrotatoHero currentHero = BrotatoHero.ALL_ROUNDER;

    /** 核心子系统：渲染引擎、掉落物生态、特工技能装备库、战地粒子特效 */
    private final BrotatoRenderer renderer = new BrotatoRenderer();
    private final SurvivorDrops survivorDrops = new SurvivorDrops();
    private final SurvivorSkill.SkillInventory inventory = new SurvivorSkill.SkillInventory();
    private final BrotatoEffects effects = new BrotatoEffects();

    /** 局内经验与等级成长体系 (Survivor.io EXP & Leveling) */
    private int currentLevel = 1;
    private int currentExp = 0;
    private int maxExpForLevel = 5;

    /** 升级卡片选择弹窗状态 */
    private boolean levelUpPending = false;
    private final List<SurvivorSkill.Type> levelUpChoices = new ArrayList<>();
    private final RectF[] cardRects = new RectF[]{new RectF(), new RectF(), new RectF()};

    /** 三叶草幸运宝箱开箱弹窗状态 */
    private boolean luckyChestPending = false;
    private int luckyRolls = 1;
    private final List<SurvivorSkill.Type> luckyRewards = new ArrayList<>();
    private long luckyOpenTick = 0L;

    /** WARNING 警报状态 */
    private int warningTicks = 0;
    private int lastWarningWave = -1;

    /** 战斗打击感与统计 */
    private final Map<Enemy, Float> enemyHpMap = new HashMap<>();
    private int collectedGems = 0;
    private int prevPlayerHp = BrotatoArena.BASE_MAX_HP;
    private int damageFlashTicks = 0;
    private long lastFireMs = 0L;
    private int prevProjectileCount = 0;
    private long animTick = 0L;

    /** 虚拟触摸摇杆状态 */
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

        // 绑定掉落物生态监听器
        survivorDrops.setDropListener(new SurvivorDrops.DropListener() {
            @Override
            public void onExpCollected(int exp) {
                // 甲贺忍法帖加成经验
                float boost = 1.0f + inventory.getLevel(SurvivorSkill.Type.KOGA_SCROLL) * 0.15f;
                int finalExp = Math.max(1, Math.round(exp * boost));
                addExp(finalExp);
            }

            @Override
            public void onMagnetTriggered() {
                effects.triggerScreenShake(5f, 6);
                if (arena != null) {
                    effects.spawnBurst(arena.playerX(), arena.playerY(), 0xFF38BDF8, 20, 5.0f);
                }
            }

            @Override
            public void onNukeTriggered() {
                // 战术核弹全屏清屏！
                effects.triggerScreenShake(12f, 16);
                if (arena != null) {
                    effects.spawnBurst(arena.playerX(), arena.playerY(), 0xFFFFFFFF, 35, 8.0f);
                    List<Enemy> enemies = new ArrayList<>(arena.enemies());
                    for (Enemy e : enemies) {
                        if (e.hp < 15f && (e.kind == null || e.kind.score() < 10)) {
                            arena.debugKillEnemy(e);
                        }
                    }
                }
            }

            @Override
            public void onChickenCollected() {
                // 香脆烤鸡腿回复生命
                if (arena != null) {
                    effects.spawnBurst(arena.playerX(), arena.playerY(), 0xFF10B981, 15, 3.5f);
                }
            }

            @Override
            public void onLuckyChestOpened(int rolls) {
                // 开启三叶草幸运宝箱转盘
                luckyRolls = rolls;
                luckyRewards.clear();
                luckyRewards.addAll(inventory.rollLuckyChestRewards(rolls));
                luckyChestPending = true;
                luckyOpenTick = animTick;
                effects.triggerScreenShake(8f, 12);
                invalidate();
            }
        });
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
    }

    public BrotatoHero getHero() {
        return currentHero;
    }

    public SurvivorSkill.SkillInventory getInventory() {
        return inventory;
    }

    public int getCollectedMaterials() {
        return collectedGems;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void startGame() {
        content = requireContent();
        pendingStart = true;
        gameRunning = true;
        gamePaused = false;
        collectedGems = 0;
        currentLevel = 1;
        currentExp = 0;
        maxExpForLevel = 5;
        levelUpPending = false;
        luckyChestPending = false;
        warningTicks = 0;
        lastWarningWave = -1;
        joystickActive = false;
        inventory.reset();
        survivorDrops.clear();
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
            android.util.Log.e("BrotatoView", "failed to start survivor arena", exception);
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
            throw new IllegalStateException("brotato content must be injected via Fragment module assets");
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

    // ==================== 经验与升级机制 ====================

    private void addExp(int exp) {
        currentExp += exp;
        collectedGems += exp;
        if (listener != null) {
            listener.onMaterialsChanged(collectedGems);
        }

        if (currentExp >= maxExpForLevel && !levelUpPending) {
            triggerLevelUp();
        }
    }

    private void triggerLevelUp() {
        currentExp -= maxExpForLevel;
        currentLevel++;
        maxExpForLevel = 5 + (currentLevel - 1) * 8;

        levelUpChoices.clear();
        levelUpChoices.addAll(inventory.rollChoices(3));

        if (!levelUpChoices.isEmpty()) {
            levelUpPending = true;
            effects.triggerScreenShake(7f, 10);
            if (listener != null) {
                listener.onLevelChanged(currentLevel);
            }
        }
    }

    // ==================== 游戏主循环 ====================

    public void update() {
        if (!gameRunning || gamePaused) return;
        if (levelUpPending || luckyChestPending) {
            // 升级选卡与幸运宝箱时冻结战局逻辑
            postInvalidateOnAnimation();
            return;
        }

        if (arena == null) {
            createArenaIfPossible();
            if (arena == null) return;
        }

        animTick++;
        if (damageFlashTicks > 0) damageFlashTicks--;
        if (warningTicks > 0) warningTicks--;

        // 检测 Boss 警戒波次触发 WARNING 警报
        int curWave = arena.wave();
        if (curWave > 0 && curWave != lastWarningWave) {
            if (curWave % 5 == 0 || curWave == 10 || curWave == 20) {
                warningTicks = 70;
                lastWarningWave = curWave;
                effects.triggerScreenShake(8f, 14);
            }
        }

        // 开火时刻检测
        int currentProjCount = arena.projectiles().size();
        if (currentProjCount > prevProjectileCount) {
            lastFireMs = arena.clockMs();
        }
        prevProjectileCount = currentProjCount;

        // 推进底层核心逻辑
        arena.tick();

        // 碰撞受击与掉落生成
        trackCombatEvents(arena);

        // 推进战地掉落物生态
        float magnetRange = 65f * currentHero.getMagnetRangeFactor() * inventory.getMagnetMultiplier();
        survivorDrops.update(arena.playerX(), arena.playerY(), magnetRange, arena.playerRadiusPx());

        // 推进特色主动特战武器
        runSpecialTacticalWeapons(arena);

        postInvalidateOnAnimation();
    }

    /**
     * 实时驱动《蛋壳特工队》特色主动技能（守卫者、力场、无人机、幽灵手里剑）
     */
    private void runSpecialTacticalWeapons(BrotatoArena value) {
        float px = value.playerX();
        float py = value.playerY();
        float dmgMul = inventory.getDamageMultiplier() * currentHero.getDamageFactor();

        // 1. 守卫者 / 捍卫者 (Guardian / Defender): 高速旋转环绕飞盘实体判定
        int guardianLvl = inventory.getLevel(SurvivorSkill.Type.GUARDIAN);
        boolean isDefender = inventory.isEvolved(SurvivorSkill.Type.GUARDIAN);
        if (guardianLvl > 0 || isDefender) {
            int blades = isDefender ? 6 : Math.min(4, 2 + guardianLvl / 2);
            float orbitR = isDefender ? 75f : 60f;
            float speed = isDefender ? 0.12f : 0.08f;
            float baseAngle = animTick * speed;
            double step = (Math.PI * 2.0) / blades;
            float bladeR = (isDefender ? 13f : 10f);

            for (int i = 0; i < blades; i++) {
                double a = baseAngle + i * step;
                float bx = px + (float) Math.cos(a) * orbitR;
                float by = py + (float) Math.sin(a) * orbitR;

                for (Enemy enemy : value.enemies()) {
                    if (enemy.removed) continue;
                    float dx = enemy.x - bx;
                    float dy = enemy.y - by;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < bladeR + enemy.radiusPx) {
                        float hitDmg = (isDefender ? 2.5f : 1.2f) * dmgMul;
                        enemy.hp -= hitDmg;
                        // 击退敌人
                        if (dist > 0.01f) {
                            enemy.x += (dx / dist) * (isDefender ? 6f : 3.5f);
                            enemy.y += (dy / dist) * (isDefender ? 6f : 3.5f);
                        }
                        effects.spawnDamage(enemy.x, enemy.y, hitDmg, isDefender);
                        effects.spawnBurst(enemy.x, enemy.y, isDefender ? 0xFFF59E0B : 0xFF38BDF8, 2, 1.8f);
                        if (enemy.hp <= 0) {
                            value.debugKillEnemy(enemy);
                        }
                    }
                }
            }
        }

        // 2. 力场发生器 / 压制领域 (Forcefield / Max Forcefield): 等离子力场脉冲减速与融怪
        int forceLvl = inventory.getLevel(SurvivorSkill.Type.FORCEFIELD);
        boolean isMaxField = inventory.isEvolved(SurvivorSkill.Type.FORCEFIELD);
        if (forceLvl > 0 || isMaxField) {
            float fieldR = isMaxField ? 120f : (55f + forceLvl * 10f);
            if (animTick % 10 == 0) {
                for (Enemy enemy : value.enemies()) {
                    if (enemy.removed) continue;
                    float dx = enemy.x - px;
                    float dy = enemy.y - py;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < fieldR) {
                        float fieldDmg = (isMaxField ? 1.5f : 0.6f) * dmgMul;
                        enemy.hp -= fieldDmg;
                        effects.spawnDamage(enemy.x, enemy.y, fieldDmg, false);
                        if (enemy.hp <= 0) {
                            value.debugKillEnemy(enemy);
                        }
                    }
                }
            }
        }

        // 3. 战术伴飞无人机 (Drones A & B & Destroyer)
        int droneA = inventory.getLevel(SurvivorSkill.Type.DRONE_A);
        int droneB = inventory.getLevel(SurvivorSkill.Type.DRONE_B);
        boolean isDestroyer = inventory.isEvolved(SurvivorSkill.Type.DRONE_A) || inventory.isEvolved(SurvivorSkill.Type.DRONE_B);
        if (isDestroyer || droneA > 0 || droneB > 0) {
            int interval = isDestroyer ? 12 : 24;
            if (animTick % interval == 0) {
                Enemy target = value.nearestEnemy();
                if (target != null) {
                    float droneDmg = (isDestroyer ? 5.0f : 2.2f) * dmgMul;
                    target.hp -= droneDmg;
                    effects.spawnDamage(target.x, target.y, droneDmg, isDestroyer);
                    effects.spawnBurst(target.x, target.y, isDestroyer ? 0xFFEF4444 : 0xFF38BDF8, 6, 2.5f);
                    if (isDestroyer) effects.triggerScreenShake(3.5f, 4);
                    if (target.hp <= 0) {
                        value.debugKillEnemy(target);
                    }
                }
            }
        }

        // 4. 幽灵手里剑 (Ghost Shuriken / Kunai EVO): 极速自动索敌融化单体
        if (inventory.isEvolved(SurvivorSkill.Type.KUNAI)) {
            if (animTick % 5 == 0) {
                Enemy target = value.nearestEnemy();
                if (target != null) {
                    float shurikenDmg = 2.4f * dmgMul;
                    target.hp -= shurikenDmg;
                    effects.spawnDamage(target.x, target.y, shurikenDmg, true);
                    effects.spawnBurst(target.x, target.y, 0xFFC084FC, 4, 2.0f);
                    if (target.hp <= 0) {
                        value.debugKillEnemy(target);
                    }
                }
            }
        }
    }

    /**
     * 跟踪敌人受创与击杀，驱动战利品爆落与受击闪烁
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

        // 2. 敌人受创浮字
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

        // 3. 敌人阵亡爆落经验晶石、四大战术道具与三叶草幸运宝箱
        for (Map.Entry<Enemy, Float> entry : enemyHpMap.entrySet()) {
            Enemy oldEnemy = entry.getKey();
            if (!nextMap.containsKey(oldEnemy) && oldEnemy.removed) {
                int scoreVal = (oldEnemy.kind != null) ? oldEnemy.kind.score() : 1;
                boolean isBoss = (oldEnemy.kind != null && (oldEnemy.kind.score() >= 10
                        || oldEnemy.kind.id().contains("boss") || oldEnemy.maxHp >= 15f));
                boolean isElite = (oldEnemy.maxHp >= 10f || scoreVal >= 5);

                survivorDrops.spawnOnKill(oldEnemy.x, oldEnemy.y, scoreVal, isBoss, isElite);
                if (isBoss) {
                    effects.triggerScreenShake(11f, 15);
                    effects.spawnBurst(oldEnemy.x, oldEnemy.y, 0xFFF59E0B, 25, 6.0f);
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
            drawEndOverlay(canvas, value, "战斗落幕", "特工战陨于废土都市 · 点击下方重整旗鼓", 0xFFEF4444);
            return;
        }
        if (state == BrotatoArena.State.WIN) {
            drawEndOverlay(canvas, value, "旷世凯旋！", "二十波生化潮悉数荡平，都市特工绝对传奇！", 0xFF10B981);
            return;
        }
        if (!gameRunning) return;

        // 屏幕震颤应用
        canvas.save();
        canvas.translate(effects.getShakeOffsetX() * density, effects.getShakeOffsetY() * density);

        // 1. 废土都市沥青地面
        renderer.drawArenaGround(canvas, viewWidth, playfieldHeightPx(), density,
                value.playerX(), value.playerY(), animTick);

        // 2. 战利品掉落物（四阶经验晶石、磁铁、核弹、烤鸡腿、幸运宝箱）
        survivorDrops.draw(canvas, paint, density);

        // 3. 粒子特效
        effects.draw(canvas, paint, density);

        // 4. 特色武器光环（力场、守卫者飞盘、伴飞无人机）
        float px = value.playerX() * density;
        float py = value.playerY() * density;

        int forceLvl = inventory.getLevel(SurvivorSkill.Type.FORCEFIELD);
        boolean isMaxField = inventory.isEvolved(SurvivorSkill.Type.FORCEFIELD);
        if (forceLvl > 0 || isMaxField) {
            float fieldR = (isMaxField ? 120f : (55f + forceLvl * 10f)) * density;
            renderer.drawForcefield(canvas, px, py, fieldR, isMaxField, animTick, density);
        }

        int guardLvl = inventory.getLevel(SurvivorSkill.Type.GUARDIAN);
        boolean isDefender = inventory.isEvolved(SurvivorSkill.Type.GUARDIAN);
        if (guardLvl > 0 || isDefender) {
            int blades = isDefender ? 6 : Math.min(4, 2 + guardLvl / 2);
            float orbitR = (isDefender ? 75f : 60f) * density;
            renderer.drawGuardianBlades(canvas, px, py, orbitR, blades, isDefender, animTick, density);
        }

        int droneA = inventory.getLevel(SurvivorSkill.Type.DRONE_A);
        int droneB = inventory.getLevel(SurvivorSkill.Type.DRONE_B);
        boolean isDestroyer = inventory.isEvolved(SurvivorSkill.Type.DRONE_A) || inventory.isEvolved(SurvivorSkill.Type.DRONE_B);
        if (isDestroyer || droneA > 0 || droneB > 0) {
            renderer.drawDrones(canvas, px, py, droneA > 0, droneB > 0, isDestroyer, animTick, density);
        }

        // 5. 弹道子弹
        drawProjectiles(canvas, value);

        // 6. 异变体敌寇立体模型
        for (Enemy enemy : value.enemies()) {
            renderer.drawEnemy(canvas, enemy, animTick, density);
        }

        // 7. 特工主角科萌与武器
        renderer.drawPlayer(canvas, currentHero, px, py, value.playerRadiusPx() * density,
                (float) calcAimAngle(value), joystickActive, animTick, damageFlashTicks, density);

        canvas.restore();

        // 8. 全屏 WARNING 警报横幅
        if (warningTicks > 0) {
            renderer.drawWarningBanner(canvas, viewWidth, playfieldHeightPx(), density, animTick);
        }

        // 9. 界面与 HUD（不受震颤干扰）
        drawDangerVignette(canvas, value);
        drawJoystick(canvas);

        // 10. 顶栏横贯全屏经典发光绿色经验槽
        renderer.drawTopExpBar(canvas, viewWidth, density, currentExp, maxExpForLevel,
                currentLevel, value.score(), value.wave(), value.content().totalWaves());

        // 11. 升级卡片选择弹窗
        if (levelUpPending) {
            updateCardRects();
            renderer.drawLevelUpDialog(canvas, viewWidth, viewHeight, density,
                    levelUpChoices, inventory, cardRects);
        } else if (luckyChestPending) {
            // 三叶草幸运宝箱老虎机转盘弹窗
            float spinProgress = Math.min(1.0f, (animTick - luckyOpenTick) / 20f);
            renderer.drawLuckyChestDialog(canvas, viewWidth, viewHeight, density,
                    luckyRolls, luckyRewards, spinProgress);
        } else if (state == BrotatoArena.State.REWARD_PENDING) {
            drawRewardCards(canvas, value);
        } else if (state == BrotatoArena.State.INTERMISSION) {
            drawIntermissionBanner(canvas, value);
        }
    }

    private double calcAimAngle(BrotatoArena value) {
        Enemy target = value.nearestEnemy();
        if (target != null) {
            return Math.atan2(target.y - value.playerY(), target.x - value.playerX());
        }
        return (animTick * 0.04) % (Math.PI * 2);
    }

    private void drawProjectiles(Canvas canvas, BrotatoArena value) {
        for (Projectile bullet : value.projectiles()) {
            float bx = bullet.x * density;
            float by = bullet.y * density;
            float br = Math.max(MIN_BULLET_DRAW_DP, bullet.radiusPx) * density;

            paint.setColor(currentHero == BrotatoHero.MAGE ? 0x66A855F7 : 0x66FDE047);
            canvas.drawCircle(bx, by, br * 1.6f, paint);

            paint.setColor(currentHero == BrotatoHero.MAGE ? 0xFFC084FC : 0xFFFFEB3B);
            canvas.drawCircle(bx, by, br, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(bx, by, br * 0.45f, paint);
        }
    }

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

        paint.setColor(0x5593C5FD);
        paint.setStrokeWidth(3 * density);
        canvas.drawLine(joystickCenterX, joystickCenterY, joystickKnobX, joystickKnobY, paint);

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

    private void updateCardRects() {
        int count = Math.min(3, levelUpChoices.size());
        if (count == 0) return;

        float gap = 8 * density;
        float outer = 12 * density;
        float availableW = viewWidth - outer * 2f - gap * (count - 1);
        float cardW = Math.min(135 * density, availableW / count);
        float cardH = Math.min(235 * density, viewHeight * 0.46f);
        float blockW = cardW * count + gap * (count - 1);
        float startX = (viewWidth - blockW) / 2f;
        float topY = (playfieldHeightPx() - cardH) / 2f + 20 * density;

        for (int i = 0; i < count; i++) {
            float left = startX + i * (cardW + gap);
            cardRects[i].set(left, topY, left + cardW, topY + cardH);
        }
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
        canvas.drawText("⚔️ 第 " + value.wave() + " 波特战就绪", viewWidth / 2f,
                middle + 6 * density, paint);

        paint.setTextSize(14 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFCBD5E1);
        canvas.drawText(value.upcomingWaveTotal() + " 只感染变异体逼近 · 倒计时 "
                + String.format(java.util.Locale.US, "%.1f", value.intermissionRemainingMs() / 1000f) + "s",
                viewWidth / 2f, middle + 30 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRewardCards(Canvas canvas, BrotatoArena value) {
        Upgrade[] cards = value.pendingCards();
        if (cards == null || cards.length == 0) return;

        paint.setColor(0xDD090D16);
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFBBF24);
        paint.setTextSize(24 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("⚡ 战地核心强化 ⚡", viewWidth / 2f, viewHeight * 0.18f, paint);

        paint.setTextSize(14 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("波次 " + value.wave() + " 肃清达成 · 选取一项核心专精进化", viewWidth / 2f,
                viewHeight * 0.18f + 26 * density, paint);

        for (int i = 0; i < cards.length; i++) {
            drawEngineCard(canvas, value, cards[i], rewardCardRect(i, cards.length), i);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawEngineCard(Canvas canvas, BrotatoArena value, Upgrade upgrade, RectF rect, int index) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * density);
        paint.setColor(0xFF38BDF8);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0x33FFFFFF);
        canvas.drawCircle(rect.centerX(), rect.top + 38 * density, 20 * density, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22 * density);
        canvas.drawText("⚡", rect.centerX(), rect.top + 45 * density, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(16 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText(upgrade.displayNameCn(), rect.centerX(), rect.top + 80 * density, paint);

        int owned = value.upgradeCount(upgrade);
        if (owned > 0) {
            paint.setColor(0xFFFBBF24);
            paint.setTextSize(12 * density);
            canvas.drawText("★ 已持有 " + owned + " 阶", rect.centerX(), rect.bottom - 16 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
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
        paint.setColor(Color.argb(195, 10, 15, 25));
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(accent);
        paint.setTextSize(34 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText(title, viewWidth / 2f, viewHeight / 2f - 40 * density, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(18 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("总战功 " + value.score() + " · 采集晶石 💎 " + collectedGems,
                viewWidth / 2f, viewHeight / 2f + 4 * density, paint);

        paint.setTextSize(15 * density);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("坚持至波次 " + value.wave() + " / " + value.content().totalWaves()
                + " · 特工等级 LV." + currentLevel, viewWidth / 2f, viewHeight / 2f + 32 * density, paint);

        paint.setTextSize(14 * density);
        paint.setColor(0xFF38BDF8);
        canvas.drawText(hint, viewWidth / 2f, viewHeight / 2f + 68 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ==================== 触摸事件与交互响应 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        BrotatoArena value = arena;
        if (!gameRunning || gamePaused || value == null) return true;

        float touchX = event.getX();
        float touchY = event.getY();
        int action = event.getActionMasked();

        // 1. 升级卡片选择优先响应
        if (levelUpPending) {
            if (action == MotionEvent.ACTION_UP) {
                for (int i = 0; i < levelUpChoices.size() && i < cardRects.length; i++) {
                    if (cardRects[i].contains(touchX, touchY)) {
                        SurvivorSkill.Type picked = levelUpChoices.get(i);
                        inventory.applyUpgrade(picked);
                        levelUpPending = false;
                        if (currentExp >= maxExpForLevel) {
                            triggerLevelUp();
                        }
                        invalidate();
                        return true;
                    }
                }
            }
            return true;
        }

        // 2. 幸运宝箱点击关闭
        if (luckyChestPending) {
            if (action == MotionEvent.ACTION_UP) {
                luckyChestPending = false;
                invalidate();
            }
            return true;
        }

        // 3. 波次奖励发牌选择
        if (value.state() == BrotatoArena.State.REWARD_PENDING) {
            if (action == MotionEvent.ACTION_UP) {
                pickRewardCard(value, touchX, touchY);
                value.setTouchTarget(value.playerX(), value.playerY(), false);
            }
            return true;
        }

        // 4. 虚拟浮动触摸摇杆
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
