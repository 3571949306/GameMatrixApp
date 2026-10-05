package com.gamecenter.app.brotato.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Brotato（生存割草）核心引擎：一局的唯一真源状态机。
 *
 * <p>纯 JVM、零 Android 依赖：所有输入都走显式 setter（{@link #setBounds(float, float)} /
 * {@link #setTouchTarget(float, float, boolean)} / {@link #selectUpgrade(int)} / {@code debug*}），
 * 所有输出都走 {@link Listener} 回调与只读 getter，渲染层不做任何判定。</p>
 *
 * <p>时间口径：引擎只看内部时钟 {@code clockMs() = tickCount * }{@link #TICK_MS}，
 * <b>禁用墙上时间</b>。一次 {@link #tick()} = 一帧 = 固定 16ms（60fps 语义），
 * 所以同一种子 + 同一输入序列必然复现同一局，可直接在单元测试里快进。</p>
 *
 * <p>状态流转：
 * {@link #start()} → INTERMISSION(2000ms) → WAVE_ACTIVE（四边按 spawnCount/interval/难度标量刷怪，
 * composition 加权随机，bossGroup 在 delayMs/intervalMs 定点出 Boss）→ 敌人追踪玩家
 * （ZIGZAG 叠加横向正弦分量；SPLIT 死亡原地生 2 只 minion）→ 自动瞄准最近敌按 fireIntervalMs 连射
 * （MULTISHOT 以 12° 递增散射）→ 弹/敌圆碰撞掉血、死亡结算得分与分裂 → 敌/我碰撞扣 contactDamage
 * 且该敌消失 → hp≤0 走 GAME_OVER；刷怪额度用完且场上清空即本波完成：回血（REGEN 抬到 4），
 * 还有下一波则 REWARD_PENDING + onRewardCards（三张不重复标题），选完卡进下一波 INTERMISSION；
 * 最后一波（第 20 波）完成走 WIN。</p>
 *
 * <p>随机源唯一：{@link Random} 以构造期 {@code seed} 初始化，出怪边、出怪位置、加权种类、
 * Boss 出场与奖励卡都只读它，因此不做任何"用当前时间播种"的隐式行为。</p>
 */
public final class BrotatoArena {

    /** 一局的状态机阶段。 */
    public enum State {
        /** 波间歇：2000ms，玩家可走位，场上无敌人。 */
        INTERMISSION,
        /** 波进行：刷怪、追踪、自动射击与碰撞结算。 */
        WAVE_ACTIVE,
        /** 奖励待选：整局冻结，直到 {@link #selectUpgrade(int)}。 */
        REWARD_PENDING,
        /** 生命归零，本局结束。 */
        GAME_OVER,
        /** 最后一波完成，本局通关。 */
        WIN
    }

    /**
     * 引擎事件回调。全部由 {@link #tick()} / {@link #selectUpgrade(int)} 同步触发，
     * 因此 UI 侧可以直接当作"本帧最终值"用，不需要再做节流。
     */
    public interface Listener {
        /** 得分变化（击杀结算后）。 */
        void onScoreChanged(int score);

        /** 一波完成（刷怪额度用完且场上清空）。 */
        void onWaveComplete(int wave);

        /** 生命归零；带上最终得分与死在第几波。 */
        void onGameOver(int score, int wave);

        /** 通关（最后一波完成）。 */
        void onWin(int score);

        /** 奖励卡就绪：三张互不重复的中文标题，顺序即 {@link #selectUpgrade(int)} 的下标。 */
        void onRewardCards(int wave, String[] titles);
    }

    // ==== 固定口径常量 ============================================================

    /** 一 tick 的固定时长（ms）。 */
    public static final long TICK_MS = 16L;
    /** {@link #TICK_MS} 的 int 形态：渲染层排帧循环时用它当"一帧多少毫秒"。 */
    public static final int FRAME_MS = (int) TICK_MS;
    /** 每波开始前的间歇时长（ms）。 */
    public static final long INTERMISSION_MS = 2000L;

    /** 三档难度对应的构造期标量（都在 {@link #MIN_DIFFICULTY_SCALAR}..{@link #MAX_DIFFICULTY_SCALAR} 内）。 */
    public static final float DIFFICULTY_EASY = 0.7f;
    public static final float DIFFICULTY_NORMAL = 1.0f;
    public static final float DIFFICULTY_HARD = 1.35f;

    /** 初始/基准成长值。 */
    public static final int BASE_MAX_HP = 10;
    public static final float BASE_FIRE_INTERVAL_MS = 300f;
    public static final float BASE_MOVE_SPEED = 5f;
    public static final float BASE_BULLET_DAMAGE = 1f;
    public static final int BASE_SHOT_COUNT = 1;

    /** 弹速（px/tick）与弹的碰撞半径（px）。 */
    public static final float BULLET_SPEED = 9f;
    public static final float BULLET_RADIUS_PX = 2f;

    /** 引擎内部 dp → px 比例固定为 1：内容里的 sizeDp 就是判定半径的像素值。 */
    public static final float PX_PER_DP = 1f;
    /** 玩家（角色）碰撞半径（dp）。 */
    public static final float PLAYER_RADIUS_DP = 9f;

    /** 难度标量合法闭区间，构造期之外一律 {@link IllegalArgumentException}。 */
    public static final float MIN_DIFFICULTY_SCALAR = 0.01f;
    public static final float MAX_DIFFICULTY_SCALAR = 3f;

    /** 每次奖励发的卡片张数。 */
    public static final int REWARD_CARD_COUNT = 3;

    /** 未调用 setBounds 时的默认竞技场尺寸（px）。 */
    public static final float DEFAULT_WIDTH_PX = 360f;
    public static final float DEFAULT_HEIGHT_PX = 640f;

    /** 场上实体上限：只影响正常刷怪路径，防止无限叠怪拖垮帧时间。 */
    public static final int MAX_LIVE_ENEMIES = 320;
    public static final int MAX_LIVE_PROJECTILES = 240;

    /** ZIGZAG：相位推进（弧度/tick）与横向分量系数（乘 speed）。 */
    public static final float ZIGZAG_PHASE_PER_TICK = 0.35f;
    public static final float ZIGZAG_LATERAL_FACTOR = 0.6f;

    /** 出怪边序号。 */
    private static final int EDGE_TOP = 0;
    private static final int EDGE_RIGHT = 1;
    private static final int EDGE_BOTTOM = 2;
    private static final int EDGE_LEFT = 3;
    private static final int EDGE_COUNT = 4;

    // ==== 不可变注入 ==============================================================

    private final BrotatoContent content;
    private final float difficultyScalar;
    private final long seed;
    private final Random random;
    private final float playerRadiusPx = PLAYER_RADIUS_DP * PX_PER_DP;

    // ==== 可变对局状态 ============================================================

    private Listener listener;
    private State state = State.INTERMISSION;
    private boolean started;
    private long tickCount;

    private float widthPx = DEFAULT_WIDTH_PX;
    private float heightPx = DEFAULT_HEIGHT_PX;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    /** 本帧死亡管线产出的分裂落点，帧末统一入列，避免边遍历边插入。 */
    private final java.util.List<float[]> pendingSplits = new java.util.ArrayList<>();

    private float playerX = widthPx / 2f;
    private float playerY = heightPx / 2f;
    private float touchX;
    private float touchY;
    private boolean touchActive;

    private int waveIndex;              // 0 基；未 start 时为 -1
    private int spawnsRemaining;
    private long nextSpawnAtMs;
    private int bossesRemaining;
    private long nextBossAtMs;
    private long waveStartMs;
    private long phaseEndsAtMs;
    private long lastFireAtMs;

    private int score;
    private int hp = BASE_MAX_HP;
    private int maxHp = BASE_MAX_HP;

    private float fireIntervalMs = BASE_FIRE_INTERVAL_MS;
    private float moveSpeed = BASE_MOVE_SPEED;
    private float bulletDamage = BASE_BULLET_DAMAGE;
    private int shotCount = BASE_SHOT_COUNT;
    private int regenStacks;
    private final Map<Upgrade, Integer> upgradeCounts = new EnumMap<>(Upgrade.class);

    private Upgrade[] pendingCards;
    private int pendingCardWave;

    /**
     * 开一局引擎（尚未 {@link #start()}）。
     *
     * @param content          已装载的内容表，不得为 null
     * @param difficultyScalar 难度标量，合法闭区间 {@value #MIN_DIFFICULTY_SCALAR}..
     *                         {@value #MAX_DIFFICULTY_SCALAR}：缩放出怪间隔、敌血与敌速
     * @param seed             随机种子；同种子 + 同输入序列 = 同一局
     * @throws IllegalArgumentException content 为 null，或标量非有限/越界
     */
    public BrotatoArena(BrotatoContent content, float difficultyScalar, long seed) {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (!Float.isFinite(difficultyScalar) || difficultyScalar < MIN_DIFFICULTY_SCALAR
                || difficultyScalar > MAX_DIFFICULTY_SCALAR) {
            throw new IllegalArgumentException("difficultyScalar must be within "
                    + MIN_DIFFICULTY_SCALAR + ".." + MAX_DIFFICULTY_SCALAR + " but was "
                    + difficultyScalar);
        }
        this.content = content;
        this.difficultyScalar = difficultyScalar;
        this.seed = seed;
        this.random = new Random(seed);
        this.waveIndex = -1;
    }

    // ==== 输入 ====================================================================

    /** 注册回调；允许 null（清除），引擎内部保证回调缺席时照常推进。 */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * 设置竞技场尺寸（px）。玩家出生点取中心，之后 {@link #setBounds(float, float)} 只会把
     * 越界的玩家夹回场内，不会重置对局。
     *
     * @throws IllegalArgumentException 任一维度非有限或 ≤ 0
     */
    public void setBounds(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("bounds must be finite and positive but were "
                    + width + "x" + height);
        }
        this.widthPx = width;
        this.heightPx = height;
        clampPlayerInside();
    }

    /**
     * 摇杆/点按目标：{@code active} 为 true 时每帧朝 (x,y) 以 {@link #moveSpeed()} 推进，
     * 到点即停；为 false 时立刻停止移动。非有限坐标视为停止，不污染状态。
     */
    public void setTouchTarget(float x, float y, boolean active) {
        if (!active || !Float.isFinite(x) || !Float.isFinite(y)) {
            this.touchActive = false;
            return;
        }
        this.touchX = x;
        this.touchY = y;
        this.touchActive = true;
    }

    /**
     * 开局 / 重开：把内部时钟、实体、成长与得分全部归零后进入第一波的 INTERMISSION。
     * 进行中的重复调用是幂等的（不重置已在跑的一局）；已结束（GAME_OVER/WIN）后调用即重开。
     */
    public void start() {
        if (started && !isTerminal()) return;
        started = true;
        tickCount = 0L;
        random.setSeed(seed);
        enemies.clear();
        projectiles.clear();
        pendingSplits.clear();
        score = 0;
        maxHp = BASE_MAX_HP;
        hp = BASE_MAX_HP;
        fireIntervalMs = BASE_FIRE_INTERVAL_MS;
        moveSpeed = BASE_MOVE_SPEED;
        bulletDamage = BASE_BULLET_DAMAGE;
        shotCount = BASE_SHOT_COUNT;
        regenStacks = 0;
        upgradeCounts.clear();
        debugRewardPool = null;
        pendingCards = null;
        pendingCardWave = 0;
        waveIndex = 0;
        spawnsRemaining = 0;
        bossesRemaining = 0;
        lastFireAtMs = 0L;
        waveStartMs = 0L;
        touchActive = false;
        playerX = widthPx / 2f;
        playerY = heightPx / 2f;
        state = State.INTERMISSION;
        phaseEndsAtMs = clockMs() + INTERMISSION_MS;
    }

    /**
     * 推进一帧（固定 {@value #TICK_MS}ms 逻辑时间）。
     *
     * <p>未开局、或处于 REWARD_PENDING / GAME_OVER / WIN 时整帧冻结（连内部时钟都不推进），
     * 保证奖励选择是显式的、结算不会在幕后继续。</p>
     */
    public void tick() {
        if (!started) return;
        if (state == State.REWARD_PENDING || state == State.GAME_OVER || state == State.WIN) return;
        tickCount++;
        movePlayer();
        if (state == State.WAVE_ACTIVE) runSpawns();
        // 间歇期同样要让场上敌人推进（debugSpawnEnemy 放进去的怪也必须能被追踪/撞到）
        advanceEnemies();
        runFire();
        advanceProjectiles();
        runContactDamage();
        flushPendingSplits();
        sweepRemoved();
        if (state == State.INTERMISSION) {
            if (clockMs() >= phaseEndsAtMs) beginWave();
            return;
        }
        checkWaveComplete();
    }

    /**
     * 在 REWARD_PENDING 里选一张卡并进入下一波间歇。
     *
     * @param index 0..2，顺序即 {@link Listener#onRewardCards(int, String[])} 给出的标题顺序
     * @throws IllegalStateException    当前不在 REWARD_PENDING
     * @throws IllegalArgumentException 下标越界
     */
    public void selectUpgrade(int index) {
        if (state != State.REWARD_PENDING) {
            throw new IllegalStateException("selectUpgrade requires REWARD_PENDING but state is "
                    + state);
        }
        Upgrade[] cards = pendingCards;
        if (cards == null || cards.length == 0) {
            throw new IllegalStateException("no reward cards are pending for wave " + wave());
        }
        if (index < 0 || index >= cards.length) {
            throw new IllegalArgumentException("upgrade index " + index + " out of range 0.."
                    + (cards.length - 1));
        }
        Upgrade card = cards[index];
        pendingCards = null;
        applyUpgrade(card);
        waveIndex++;
        state = State.INTERMISSION;
        phaseEndsAtMs = clockMs() + INTERMISSION_MS;
    }

    // ==== 调试入口（测试与实机排错共用）==========================================

    /**
     * 直接在指定坐标放一只敌人（走与正常出怪相同的难度倍率结算，但不占用本波刷怪额度）。
     * 可用于任何未结束的阶段，包括尚未 {@link #start()} 时。
     *
     * @throws IllegalArgumentException id 未定义或坐标非有限
     * @throws IllegalStateException    本局已结束
     */
    public void debugSpawnEnemy(String kindId, float x, float y) {
        if (isTerminal()) {
            throw new IllegalStateException("cannot spawn after " + state);
        }
        EnemyKind kind = content.kind(kindId);
        if (kind == null) {
            throw new IllegalArgumentException("unknown enemy kind \"" + kindId + "\"");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("spawn position must be finite");
        }
        placeEnemy(kind, x, y);
    }

    /**
     * 把玩家瞬移到指定坐标（夹进竞技场边界），并停止摇杆跟随，避免下一帧又被拖走。
     *
     * @throws IllegalArgumentException 坐标非有限
     */
    public void debugMovePlayerTo(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("player position must be finite");
        }
        touchActive = false;
        playerX = x;
        playerY = y;
        clampPlayerInside();
    }

    /**
     * 让一只敌人走完整的正常死亡管线：移出战场、结算 {@link EnemyKind#score()} 与
     * {@link Listener#onScoreChanged(int)}、SPLIT 种类原地吐 2 只 minion。
     * 不在战场上的实例（含已被移除的）视为空操作，可安全重复调用。
     *
     * @throws IllegalArgumentException e 为 null
     */
    public void debugKillEnemy(Enemy target) {
        if (target == null) throw new IllegalArgumentException("enemy must not be null");
        if (!enemies.contains(target)) return;
        killEnemy(target);
        flushPendingSplits();
        sweepRemoved();
        checkWaveComplete();
    }

    // ==== 只读接口 ================================================================

    /** 当前阶段。未开局时为 {@link State#INTERMISSION}。 */
    public State state() {
        return state;
    }

    /** 当前/进行中的波号，1 起；未开局为 0，REWARD_PENDING 期间为刚完成的那一波。 */
    public int wave() {
        WaveLevel level = currentLevel();
        return level == null ? 0 : level.wave();
    }

    /** 累计得分。 */
    public int score() {
        return score;
    }

    /** 当前生命。 */
    public int hp() {
        return hp;
    }

    /** 生命上限（VITALITY 会抬高它）。 */
    public int maxHp() {
        return maxHp;
    }

    /** 只读敌人列表（实时视图，随 {@link #tick()} 变化）。 */
    public List<Enemy> enemies() {
        return Collections.unmodifiableList(enemies);
    }

    /** 只读子弹列表（实时视图）。 */
    public List<Projectile> projectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    public float playerX() {
        return playerX;
    }

    public float playerY() {
        return playerY;
    }

    /** 当前射击间隔（ms），RAPID_FIRE 叠乘后的实际值。 */
    public float fireIntervalMs() {
        return fireIntervalMs;
    }

    /** 当前移动速度（px/tick）。 */
    public float moveSpeed() {
        return moveSpeed;
    }

    /** 当前单发伤害。 */
    public float bulletDamage() {
        return bulletDamage;
    }

    /** 当前每枪弹数（MULTISHOT 叠出来的值）。 */
    public int shotCount() {
        return shotCount;
    }

    /** 某张卡已选层数；未持有为 0。 */
    public int upgradeCount(Upgrade upgrade) {
        if (upgrade == null) throw new IllegalArgumentException("upgrade must not be null");
        Integer value = upgradeCounts.get(upgrade);
        return value == null ? 0 : value;
    }

    /** 每波结束的回血量（REGEN 会抬到 4，再叠每张 +1）。 */
    public int waveHealAmount() {
        return regenStacks > 0 ? Upgrade.REGEN_WAVE_HEAL + (regenStacks - 1) : Upgrade.BASE_WAVE_HEAL;
    }

    /** 内部时钟（ms）= tickCount * {@link #TICK_MS}，与墙上时间无关。 */
    public long clockMs() {
        return tickCount * TICK_MS;
    }

    /** 已推进的帧数。 */
    public long tickCount() {
        return tickCount;
    }

    /** 本波还剩多少普通怪没出（含 REWARD_PENDING 后的 0）。 */
    public int remainingSpawns() {
        return Math.max(0, spawnsRemaining);
    }

    /** 本波还剩多少 Boss 没出。 */
    /** 间歇期展示的"即将来袭"总数：下一波常规名额 + Boss 名额。 */
    public int upcomingWaveTotal() {
        if (state != State.INTERMISSION) return 0;
        WaveLevel level = currentLevel();
        if (level == null) return 0;
        return level.spawnCount() + (level.bossGroup() == null ? 0 : level.bossGroup().count());
    }

    public int remainingBosses() {
        return Math.max(0, bossesRemaining);
    }

    /** 当前波调度表；未开局或已通关为 null。 */
    public WaveLevel currentLevel() {
        return content.levelForWave(waveIndex + 1);
    }

    /** 本波结束（或本局结束）的剩余间歇时长（ms），非 INTERMISSION 阶段为 0。 */
    public long intermissionRemainingMs() {
        if (state != State.INTERMISSION) return 0L;
        long remaining = phaseEndsAtMs - clockMs();
        return remaining < 0L ? 0L : remaining;
    }

    /** 待选卡片（克隆）；非 REWARD_PENDING 为空数组。 */
    public Upgrade[] pendingCards() {
        return pendingCards == null ? new Upgrade[0] : pendingCards.clone();
    }

    /** 最近一次发出奖励卡的波号。 */
    public int pendingCardWave() {
        return pendingCardWave;
    }

    /** 玩家碰撞半径（px），渲染层画血条/命中框用。 */
    public float playerRadiusPx() {
        return playerRadiusPx;
    }

    /** 竞技场宽高（px）。 */
    public float boundsWidth() {
        return widthPx;
    }

    public float boundsHeight() {
        return heightPx;
    }

    /** 构造期注入的难度标量。 */
    public float difficultyScalar() {
        return difficultyScalar;
    }

    /** 构造期注入的随机种子。 */
    public long seed() {
        return seed;
    }

    /** 本局使用的内容表。 */
    public BrotatoContent content() {
        return content;
    }

    /** 是否已经 start 过（含结束态）。 */
    public boolean isStarted() {
        return started;
    }

    /** 本局是否已结束（GAME_OVER 或 WIN）。 */
    public boolean isTerminal() {
        return state == State.GAME_OVER || state == State.WIN;
    }

    /** 距离玩家最近的存活敌人；场上无敌返回 null。 */
    public Enemy nearestEnemy() {
        Enemy best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Enemy enemy : enemies) {
            if (enemy.removed) continue;
            float dx = enemy.x - playerX;
            float dy = enemy.y - playerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = enemy;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "BrotatoArena(state=" + state + ",wave=" + wave() + ",score=" + score
                + ",hp=" + hp + "/" + maxHp + ",enemies=" + enemies.size()
                + ",projectiles=" + projectiles.size() + ",tick=" + tickCount
                + ",scalar=" + difficultyScalar + ",seed=" + seed + ")";
    }

    // ==== 阶段实现 ================================================================

    /** 间歇结束：把本波的出怪/Boss 计时器上膛，进入 WAVE_ACTIVE。 */
    private void beginWave() {
        WaveLevel level = currentLevel();
        if (level == null) {
            finishWithWin();
            return;
        }
        state = State.WAVE_ACTIVE;
        waveStartMs = clockMs();
        spawnsRemaining = level.spawnCount();
        nextSpawnAtMs = waveStartMs;          // 波开始当帧就出第一只
        WaveLevel.BossGroup boss = level.bossGroup();
        if (boss == null) {
            bossesRemaining = 0;
            nextBossAtMs = 0L;
        } else {
            bossesRemaining = boss.count();
            nextBossAtMs = waveStartMs + boss.delayMs();   // Boss 走内容里的定点时间轴
        }
    }

    /** 普通怪按间隔补帧出怪，Boss 按 delayMs/intervalMs 定点出怪。 */
    private void runSpawns() {
        WaveLevel level = currentLevel();
        if (level == null) return;
        long interval = scaledSpawnIntervalMs(level);
        while (spawnsRemaining > 0 && clockMs() >= nextSpawnAtMs) {
            spawnWeighted(level);
            spawnsRemaining--;
            nextSpawnAtMs += interval;
        }
        WaveLevel.BossGroup boss = level.bossGroup();
        while (boss != null && bossesRemaining > 0 && clockMs() >= nextBossAtMs) {
            EnemyKind kind = content.kind(boss.kind());
            if (kind != null) spawnAtEdge(kind);
            bossesRemaining--;
            nextBossAtMs += boss.intervalMs();
        }
    }

    /** 出怪间隔按难度标量缩放（标量越大越密），下限一帧；标量 1 时与内容逐字一致。 */
    private long scaledSpawnIntervalMs(WaveLevel level) {
        double scaled = level.spawnIntervalMs() / difficultyScalar;
        long interval = Math.round(scaled);
        return interval < TICK_MS ? TICK_MS : interval;
    }

    /** 按 composition 权重抽一种敌人，从随机一边上场。 */
    private void spawnWeighted(WaveLevel level) {
        WaveLevel.CompEntry entry = level.entryForRoll(random.nextInt(Math.max(1, level.totalWeight())));
        if (entry == null) return;
        EnemyKind kind = content.kind(entry.kind());
        if (kind == null) return;   // 装载期已 fail-closed，这里只是纵深防御
        spawnAtEdge(kind);
    }

    /** 四边随机选一条边放怪（顶/右/底/左），位置沿该边均匀取点。 */
    private void spawnAtEdge(EnemyKind kind) {
        if (enemies.size() >= MAX_LIVE_ENEMIES) return;   // 拖帧保护
        int edge = random.nextInt(EDGE_COUNT);
        float x;
        float y;
        switch (edge) {
            case EDGE_TOP:
                x = random.nextFloat() * widthPx;
                y = 0f;
                break;
            case EDGE_RIGHT:
                x = widthPx;
                y = random.nextFloat() * heightPx;
                break;
            case EDGE_BOTTOM:
                x = random.nextFloat() * widthPx;
                y = heightPx;
                break;
            case EDGE_LEFT:
            default:
                x = 0f;
                y = random.nextFloat() * heightPx;
                break;
        }
        placeEnemy(kind, x, y);
    }

    /** 真正入列：血量与速度按难度标量结算，contactDamage 保持内容原值。 */
    private Enemy placeEnemy(EnemyKind kind, float x, float y) {
        Enemy enemy = new Enemy(kind, x, y, hpScale(), speedScale(), PX_PER_DP);
        enemies.add(enemy);
        return enemy;
    }

    /** 血量倍率：标量 1 → 1.0，标量 3 → 3.0，标量 0.01 → 接近一触即溃。 */
    private float hpScale() {
        return difficultyScalar;
    }

    /** 速度倍率：标量 1 → 1.0，两端收敛到 0.5..2.0，避免"简单难度怪追不上"或"三倍速不可玩"。 */
    private float speedScale() {
        float scaled = 0.5f + 0.5f * difficultyScalar;
        return scaled < 0.5f ? 0.5f : (scaled > 2f ? 2f : scaled);
    }

    /** 玩家移动：朝触点以 moveSpeed 推进，到点即停，并被夹在竞技场内。 */
    private void movePlayer() {
        if (!touchActive) return;
        float dx = touchX - playerX;
        float dy = touchY - playerY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= moveSpeed) {
            playerX = touchX;
            playerY = touchY;
        } else {
            playerX += dx / distance * moveSpeed;
            playerY += dy / distance * moveSpeed;
        }
        clampPlayerInside();
    }

    private void clampPlayerInside() {
        float limitX = widthPx - playerRadiusPx;
        float limitY = heightPx - playerRadiusPx;
        if (playerRadiusPx > widthPx / 2f || playerRadiusPx > heightPx / 2f) {
            playerX = widthPx / 2f;
            playerY = heightPx / 2f;
            return;
        }
        if (playerX < playerRadiusPx) playerX = playerRadiusPx;
        if (playerY < playerRadiusPx) playerY = playerRadiusPx;
        if (playerX > limitX) playerX = limitX;
        if (playerY > limitY) playerY = limitY;
    }

    /** 敌人追踪：ZIGZAG 种类在垂直于追踪向量方向叠加 sin(tickAge*0.35)*speed*0.6 的横向分量。 */
    private void advanceEnemies() {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.removed) continue;
            enemy.tickAge++;
            float dx = playerX - enemy.x;
            float dy = playerY - enemy.y;
            float distance = (float) Math.hypot(dx, dy);
            if (distance <= 0f) continue;
            float ux = dx / distance;
            float uy = dy / distance;
            float step = enemy.speed;
            if (step > distance) step = distance;      // 不越过玩家，避免抖动
            enemy.x += ux * step;
            enemy.y += uy * step;
            if (enemy.kind != null && enemy.kind.zigzag()) {
                float lateral = (float) Math.sin(enemy.tickAge * ZIGZAG_PHASE_PER_TICK)
                        * enemy.speed * ZIGZAG_LATERAL_FACTOR;
                enemy.x += -uy * lateral;
                enemy.y += ux * lateral;
            }
        }
    }

    /** 自动瞄准最近敌人开火；MULTISHOT 时以 12° 递增对称散开。 */
    private void runFire() {
        if (enemies.isEmpty()) {
            return;
        }
        if (clockMs() - lastFireAtMs < fireIntervalMs) return;
        Enemy target = nearestEnemy();
        if (target == null) return;
        lastFireAtMs = clockMs();
        double base = Math.atan2(target.y - playerY, target.x - playerX);
        double step = Math.toRadians(Upgrade.SPREAD_STEP_DEG);
        int shots = Math.max(1, shotCount);
        for (int i = 0; i < shots; i++) {
            if (projectiles.size() >= MAX_LIVE_PROJECTILES) break;
            double angle = base + (i - (shots - 1) / 2.0) * step;
            projectiles.add(new Projectile(playerX, playerY,
                    (float) (Math.cos(angle) * BULLET_SPEED),
                    (float) (Math.sin(angle) * BULLET_SPEED),
                    bulletDamage));
        }
    }

    /** 子弹推进 + 圆碰撞结算：命中即扣血并消失，血量归零走死亡管线。 */
    private void advanceProjectiles() {
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile bullet = projectiles.get(i);
            if (bullet.removed) continue;
            bullet.tickAge++;
            bullet.x += bullet.vx;
            bullet.y += bullet.vy;
            if (bullet.outside(widthPx, heightPx, bullet.radiusPx * 4f)) {
                bullet.removed = true;
                continue;
            }
            for (int j = 0; j < enemies.size(); j++) {
                Enemy enemy = enemies.get(j);
                if (enemy.removed) continue;
                if (!bullet.hits(enemy.x, enemy.y, enemy.radiusPx)) continue;
                bullet.removed = true;
                enemy.hp -= bullet.damage;
                if (enemy.hp <= 0f) killEnemy(enemy);
                break;                       // 一发只打一只
            }
        }
    }

    /** 敌/我碰撞：扣 contactDamage，该敌直接消失（不计分、不分裂），生命归零即结束本局。 */
    private void runContactDamage() {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.removed) continue;
            if (!enemy.touches(playerX, playerY, playerRadiusPx)) continue;
            enemy.removed = true;
            hp -= Math.max(0, enemy.contactDamage);
            if (hp <= 0) {
                hp = 0;
                state = State.GAME_OVER;
                touchActive = false;
                Listener target = listener;
                if (target != null) target.onGameOver(score, wave());
                return;
            }
        }
    }

    /**
     * 死亡管线（子弹击杀与 {@link #debugKillEnemy(Enemy)} 共用）：
     * 计分 + 回调，SPLIT 种类在原地留 2 只 minion。
     */
    private void killEnemy(Enemy enemy) {
        if (enemy.removed) return;
        enemy.removed = true;
        EnemyKind kind = enemy.kind;
        if (kind != null) {
            score += Math.max(0, kind.score());
            Listener target = listener;
            if (target != null) target.onScoreChanged(score);
            if (kind.split() && content.splitSupported()) {
                queueSplits(enemy.x, enemy.y);
            }
        }
    }

    /** 记下分裂落点，帧末再入列，保证同帧的遍历顺序不受插入影响。 */
    private void queueSplits(float x, float y) {
        for (int i = 0; i < EnemyKind.SPLIT_CHILD_COUNT; i++) {
            pendingSplits.add(new float[]{x, y});
        }
    }

    private void flushPendingSplits() {
        if (pendingSplits.isEmpty()) return;
        EnemyKind minion = content.kind(EnemyKind.MINION_ID);
        if (minion != null) {
            for (float[] spot : pendingSplits) {
                if (enemies.size() >= MAX_LIVE_ENEMIES) break;
                placeEnemy(minion, spot[0], spot[1]);
            }
        }
        pendingSplits.clear();
    }


    /** 统一回收本帧标记移除的实体。 */
    private void sweepRemoved() {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            if (it.next().removed) it.remove();
        }
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            if (it.next().removed) it.remove();
        }
    }

    /** 刷怪额度用完（普通怪 + Boss）且场上清空 → 本波完成。 */
    private void checkWaveComplete() {
        if (state != State.WAVE_ACTIVE) return;
        if (spawnsRemaining > 0 || bossesRemaining > 0) return;
        if (!enemies.isEmpty()) return;
        completeWave();
    }

    private void completeWave() {
        int completed = wave();
        Listener target = listener;
        if (target != null) target.onWaveComplete(completed);
        if (currentLevel() == null || waveIndex + 1 >= content.totalWaves()) {
            finishWithWin();
            return;
        }
        hp = Math.min(maxHp, hp + waveHealAmount());
        state = State.REWARD_PENDING;
        pendingCards = drawRewardCards();
        pendingCardWave = completed;
        String[] titles = Upgrade.titlesOf(pendingCards);
        if (target != null) target.onRewardCards(completed, titles);
    }

    private void finishWithWin() {
        state = State.WIN;
        touchActive = false;
        Listener target = listener;
        if (target != null) target.onWin(score);
    }

    private Upgrade[] debugRewardPool;

    /**
     * 测试接缝：强制后续发牌按给定顺序取前 {@link #REWARD_CARD_COUNT} 张（去重后不足则少发），
     * 传 null 或空恢复随机抽卡。不消费随机源，保证抽卡类测试可定向、确定。
     */
    public void debugSetRewardPool(Upgrade... cards) {
        if (cards == null || cards.length == 0) {
            debugRewardPool = null;
            return;
        }
        java.util.LinkedHashSet<Upgrade> unique = new java.util.LinkedHashSet<>(java.util.Arrays.asList(cards));
        debugRewardPool = unique.toArray(new Upgrade[0]);
    }

    /** 从六张卡里不重复地抽 {@link #REWARD_CARD_COUNT} 张（部分 Fisher-Yates，只消费随机源）。 */
    private Upgrade[] drawRewardCards() {
        if (debugRewardPool != null) {
            return java.util.Arrays.copyOf(debugRewardPool,
                    Math.min(REWARD_CARD_COUNT, debugRewardPool.length));
        }
        Upgrade[] pool = Upgrade.values().clone();
        int count = Math.min(REWARD_CARD_COUNT, pool.length);
        Upgrade[] drawn = new Upgrade[count];
        for (int i = 0; i < count; i++) {
            int swap = i + random.nextInt(pool.length - i);
            Upgrade held = pool[i];
            pool[i] = pool[swap];
            pool[swap] = held;
            drawn[i] = pool[i];
        }
        return drawn;
    }

    /** 卡片效果落地：全部按"当前值 ×/＋ 系数"叠乘，并夹在常量上限内。 */
    private void applyUpgrade(Upgrade card) {
        if (card == null) return;
        upgradeCounts.put(card, upgradeCount(card) + 1);
        switch (card) {
            case RAPID_FIRE:
                fireIntervalMs = Math.max(Upgrade.MIN_FIRE_INTERVAL_MS,
                        fireIntervalMs * Upgrade.RAPID_FIRE_FACTOR);
                break;
            case SWIFT_BOOTS:
                moveSpeed = Math.min(Upgrade.MAX_MOVE_SPEED, moveSpeed * Upgrade.SWIFT_BOOTS_FACTOR);
                break;
            case VITALITY:
                maxHp += Upgrade.VITALITY_MAX_HP;
                hp = Math.min(maxHp, hp + Upgrade.VITALITY_MAX_HP);
                break;
            case MULTISHOT:
                shotCount = Math.min(Upgrade.MAX_SHOT_COUNT, shotCount + Upgrade.MULTISHOT_EXTRA_SHOTS);
                break;
            case DAMAGE_UP:
                bulletDamage = Math.min(Upgrade.MAX_BULLET_DAMAGE,
                        bulletDamage * Upgrade.DAMAGE_UP_FACTOR);
                break;
            case REGEN:
            default:
                regenStacks++;
                break;
        }
    }
}
