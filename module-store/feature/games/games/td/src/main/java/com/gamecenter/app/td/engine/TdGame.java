package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔防「保卫蛋蛋」核心引擎。
 *
 * <p>纯 JVM 实现，零 Android 依赖，可在单元测试中确定性运行（固定 60Hz tick）。
 * UI 层须将返回值视为不可信输入表：所有玩家操作（建塔/升级/卖塔/提速）都有
 * 独立校验，操作失败返回 null 或 false 而不是抛异常。
 *
 * <p>坐标契约：引擎内一律使用 (row, col) 格子坐标与网格内浮点坐标
 * （float 坐标表示格内位置，x=col+0.5f 为格中心，y=row+0.5f 为格中心）。
 */
public class TdGame {

    /** 固定逻辑帧率 */
    public static final float FIXED_HZ = 60f;
    public static final float FIXED_DT = 1f / FIXED_HZ;

    /** 伤害来源只用于软抗性计算，所有来源最终仍走同一护甲/护盾/死亡提交。 */
    private enum DamageKind { DIRECT, POISON, LIGHTNING_CHAIN, BURN }

    /** 游戏状态 */
    public enum State {
        PREPARING,   // 准备中（可建塔）
        RUNNING,     // 战斗进行中
        WON,         // 胜利
        LOST         // 失败（蛋蛋被吃）
    }

    /** 难度档位：影响初始金币与怪血倍率 */
    public enum Difficulty {
        EASY("简单", 1.3f, 0.82f, 1f),
        NORMAL("普通", 1f, 1f, 1f),
        HARD("困难", 0.8f, 1.25f, 1.05f);

        public final String displayName;
        /** 初始金币倍率 */
        public final float coinMul;
        /** 怪血倍率 */
        public final float hpMul;
        /** 怪速倍率 */
        public final float speedMul;

        Difficulty(String displayName, float coinMul, float hpMul, float speedMul) {
            this.displayName = displayName;
            this.coinMul = coinMul;
            this.hpMul = hpMul;
            this.speedMul = speedMul;
        }
    }

    /** 单条波次定义 */
    public static class Wave {
        public final MonsterType type;
        /** 同一波内按顺序循环出现的怪物组合；单类型波次仅含一个元素。 */
        private final MonsterType[] composition;
        /** 多路线关卡中该波使用的路线序号。 */
        public final int routeIndex;
        public final int count;
        public final float intervalSec;
        public final float startDelaySec;
        public final float hpMul;
        public final float speedMul;

        public Wave(MonsterType type, int count, float intervalSec, float startDelaySec,
                    float hpMul, float speedMul) {
            this(new MonsterType[] { type }, 0, count, intervalSec, startDelaySec, hpMul, speedMul);
        }

        /** 创建一条带怪物组合和路线的波次。组合会按生成顺序循环。 */
        public Wave(MonsterType[] composition, int routeIndex, int count, float intervalSec,
                    float startDelaySec, float hpMul, float speedMul) {
            if (composition == null || composition.length == 0) {
                throw new IllegalArgumentException("wave composition must not be empty");
            }
            for (MonsterType monsterType : composition) {
                if (monsterType == null) throw new IllegalArgumentException("wave monster type is null");
            }
            if (routeIndex < 0 || count <= 0 || intervalSec < 0f || startDelaySec < 0f
                    || hpMul <= 0f || speedMul <= 0f || Float.isNaN(intervalSec)
                    || Float.isNaN(startDelaySec) || Float.isNaN(hpMul) || Float.isNaN(speedMul)) {
                throw new IllegalArgumentException("invalid wave values");
            }
            this.composition = composition.clone();
            this.type = this.composition[0];
            this.routeIndex = routeIndex;
            this.count = count;
            this.intervalSec = intervalSec;
            this.startDelaySec = startDelaySec;
            this.hpMul = hpMul;
            this.speedMul = speedMul;
        }

        /** 当前生成序号应出现的怪物类型。 */
        public MonsterType typeAt(int spawnIndex) {
            return composition[Math.floorMod(spawnIndex, composition.length)];
        }

        /** HUD 使用的可读波次预告。 */
        public String previewName() {
            if (composition.length == 1) return composition[0].displayName;
            StringBuilder out = new StringBuilder("混编：");
            for (int i = 0; i < composition.length; i++) {
                if (i > 0) out.append('+');
                out.append(composition[i].displayName);
            }
            return out.toString();
        }
    }

    /** 玩家可为每座塔切换的目标优先级。 */
    public enum TargetMode {
        FIRST("最前"),
        STRONG("强敌"),
        WEAK("残血");

        public final String displayName;

        TargetMode(String displayName) {
            this.displayName = displayName;
        }

        TargetMode next() {
            TargetMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    /** 塔实例 */
    public static class Tower {
        public final TowerType type;
        public final int row;
        public final int col;
        public int level = 1;
        /**
         * 这座塔实际投入的金币。合成会汇总两座来源塔的投入，出售据此返还，
         * 不使用“假设按点按升级”的推导价格，避免合成后少返还。
         */
        public int investedCost;
        public float cooldown = 0f;
        public float incomeTimer = 0f;
        public TargetMode targetMode = TargetMode.FIRST;
        /** 建成后经过的帧数（UI 做缩放登场动画） */
        public int buildAge = 0;

        Tower(TowerType type, int row, int col) {
            this.type = type;
            this.row = row;
            this.col = col;
            this.investedCost = type.baseCost;
        }

        public float damageAt() { return type.damageAt(level); }
        public float rangeAt() { return type.rangeAt(level); }
        public float fireIntervalAt() { return type.fireIntervalAt(level); }
        public int totalInvested() { return investedCost; }
    }

    /** 怪兽实例 */
    public static class Monster {
        public final MonsterType type;
        public float hp;
        public final float maxHp;
        /** 出生速度倍率（波次×难度合成值）；雪花减速只允许在此基础上临时缩放并按时还原 */
        public final float baseSpeedMul;
        public float speedMul = 1f;
        public float slowTimer = 0f;
        public float dotDps = 0f;
        public float dotTimer = 0f;
        public float shield = 0f;
        public float maxShield;
        public float shieldPulseTimer = 1.1f;
        public float shieldFlash = 0f;
        public float chargeCooldown = 2.4f;
        public float chargeTimer = 0f;
        public boolean charging = false;
        public boolean enraged = false;
        public float summonTimer = 6f;
        public int summonsRemaining = 4;
        /** 医生怪的治疗冷却和受治疗后的视觉提示。 */
        public float healTimer = 0.8f;
        public float healedFlash = 0f;
        /** 受击闪白计时（秒，UI 据此混白身体色）；仅视觉反馈，不影响逻辑 */
        public float hitFlash = 0f;
        public float x;          // 格子坐标（float）
        public float y;
        public int pathIndex = 0; // 当前所在路径段
        public float segT = 0f;   // 段内进度 0..1
        public boolean dead = false;
        /** 分裂产生的幼体只复用喽罗的基础类型，绝不再触发分裂。 */
        public boolean splitChild = false;
        /** 召唤产生的单位会在后续机制中设置，始终携带来源 ID。 */
        public boolean summoned = false;
        public int originMonsterId = 0;
        /** 派生单位可使用低于原型的击杀奖励。 */
        public int reward;
        public int id;
        private final int pathLen;
        /** 当前波次序号（用于波次报错/统计） */
        public int waveNo;
        /** 该怪物实际行走的路线序号。 */
        public final int routeIndex;

        Monster(MonsterType type, int startingX, int startingY, int pathLen, int id,
                float hpMul, float speedMul, int waveNo, int routeIndex) {
            this.type = type;
            this.maxHp = type.hp * hpMul;
            this.hp = maxHp;
            this.baseSpeedMul = speedMul;
            this.speedMul = speedMul;
            this.shield = type.shieldHp(type);
            this.maxShield = this.shield;
            this.x = startingX + 0.5f;
            this.y = startingY + 0.5f;
            this.pathIndex = 0;
            this.segT = 0f;
            this.pathLen = pathLen;
            this.id = id;
            this.waveNo = waveNo;
            this.routeIndex = routeIndex;
            this.reward = type.value;
        }

        /** 已走路径比例（0..1），用于决定攻击优先级（越靠近终点越优先） */
        public float pathProgress() {
            if (pathLen <= 1) return 1f;
            return ((float) pathIndex + segT) / (float) (pathLen - 1);
        }
    }

    /** 开火特效（UI 据此画激光，仅存活数帧） */
    public static class Beam {
        public final float x1, y1, x2, y2;
        public final TowerType type;
        public int life = 6; // 帧数
        Beam(float x1, float y1, float x2, float y2, TowerType type) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.type = type;
        }
    }

    /** 击杀事件（怪物死亡瞬间的网格坐标，UI 据此播放飘字/爆裂特效） */
    public static class KillEvent {
        public final float x, y;
        public final int value;
        public final boolean boss;
        KillEvent(float x, float y, int value, boolean boss) {
            this.x = x; this.y = y; this.value = value; this.boss = boss;
        }
    }

    /** Lv3 地雷短暂留下的燃烧区；以固定帧扣血且有硬时长。 */
    public static class BurnZone {
        public final float x, y, radius;
        public float secondsLeft;

        BurnZone(float x, float y, float radius, float secondsLeft) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.secondsLeft = secondsLeft;
        }
    }

    // ===== 关卡结构 =====
    private final int cols;
    private final int rows;
    /** 路径格子 (row, col)，每条路径均按出生→终点排列。 */
    private final int[][][] paths;
    /** 预计算的路径占用表，供建塔校验和渲染快速查询。 */
    private final boolean[][] pathCells;
    /** 终点坐标（蛋蛋所在格） */
    private final int eggRow, eggCol;
    /** 初始金币 */
    private final int startCoin;
    /** 蛋蛋生命值 */
    private final int maxMascotHp;

    private final List<Wave> waves;
    private int waveIndex = 0;          // 当前波（0-based）
    private float spawnTimer = 0f;      // 本波生成倒计时
    private int spawnedInWave = 0;      // 本波已生成数量
    private boolean waveStarted = false;

    // ===== 运行时状态 =====
    private State state = State.PREPARING;
    private int coin;
    private int mascotHp;
    private long gameTicks = 0;
    private final List<Monster> monsters = new ArrayList<>();
    private final List<Tower> towers = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();
    private final List<KillEvent> killEvents = new ArrayList<>();
    private final List<BurnZone> burnZones = new ArrayList<>();
    /** 死亡/技能产生的派生单位先入队，避免在敌人遍历期间修改 monsters。 */
    private final List<Monster> pendingDerivedMonsters = new ArrayList<>();
    private int nextMonsterId = 1;
    /** 蛋蛋受击反馈计时（秒，UI 做红闪），0 表示无受击 */
    private float eggHitTimer = 0f;

    /** 塔坐标索引：key = row * 1000 + col */
    private final java.util.Map<Integer, Tower> towerGrid = new java.util.HashMap<>();
    /** 玩家上次操作结果：LAST_ACTION_RESULT 描述可读结果，UI 弹 Toast */
    private String lastActionMessage = "";
    private String lastActionTone = ""; // "ok" | "err" | "info"

    private int totalWaves;
    private int coinsEarned = 0;
    private int monstersKilled = 0;
    private int mascotHpLost = 0;
    private int monstersSpawnedTotal = 0;
    private long elapsedTicks = 0;
    private Difficulty difficulty = Difficulty.NORMAL;

    public TdGame(int cols, int rows, int[][] path, int eggRow, int eggCol,
                  int startCoin, int mascotHp, List<Wave> waves) {
        this(cols, rows, new int[][][] { path }, eggRow, eggCol, startCoin, mascotHp, waves);
    }

    /**
     * 创建多入口关卡。每条路线均须从出生点连续走到同一个蛋蛋终点；不同路线可以
     * 共用终点或汇合段，但单条路线不允许重访格子，避免视觉重叠和禁建塔死角。
     */
    public TdGame(int cols, int rows, int[][][] paths, int eggRow, int eggCol,
                  int startCoin, int mascotHp, List<Wave> waves) {
        if (cols <= 0 || rows <= 0) throw new IllegalArgumentException("grid must be positive");
        if (paths == null || paths.length == 0) throw new IllegalArgumentException("paths must not be empty");
        if (eggRow < 0 || eggRow >= rows || eggCol < 0 || eggCol >= cols) {
            throw new IllegalArgumentException("egg is out of bounds");
        }
        if (startCoin < 0 || mascotHp <= 0) throw new IllegalArgumentException("invalid starting resources");
        if (waves == null || waves.isEmpty()) throw new IllegalArgumentException("waves must not be empty");
        // 深拷贝路径与波次，保证外部修改不影响本引擎。
        this.cols = cols;
        this.rows = rows;
        this.paths = new int[paths.length][][];
        this.pathCells = new boolean[rows][cols];
        for (int route = 0; route < paths.length; route++) {
            this.paths[route] = copyAndValidatePath(paths[route], route, eggRow, eggCol, rows, cols);
            for (int[] cell : this.paths[route]) {
                this.pathCells[cell[0]][cell[1]] = true;
            }
        }
        for (Wave wave : waves) {
            if (wave == null) throw new IllegalArgumentException("wave is null");
            if (wave.routeIndex >= this.paths.length) {
                throw new IllegalArgumentException("wave route index is out of bounds");
            }
        }
        this.eggRow = eggRow;
        this.eggCol = eggCol;
        this.startCoin = startCoin;
        this.coin = startCoin;
        this.maxMascotHp = mascotHp;
        this.mascotHp = mascotHp;
        this.waves = new ArrayList<>(waves);
        this.totalWaves = waves.size();
    }

    private static int[][] copyAndValidatePath(int[][] source, int route, int eggRow, int eggCol,
                                               int rows, int cols) {
        if (source == null || source.length < 2) {
            throw new IllegalArgumentException("route " + route + " is too short");
        }
        int[][] copy = new int[source.length][2];
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < source.length; i++) {
            if (source[i] == null || source[i].length != 2) {
                throw new IllegalArgumentException("route " + route + " has invalid cell");
            }
            int row = source[i][0];
            int col = source[i][1];
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                throw new IllegalArgumentException("route " + route + " leaves board");
            }
            int key = row * cols + col;
            if (!seen.add(key)) {
                throw new IllegalArgumentException("route " + route + " revisits a cell");
            }
            if (i > 0) {
                int distance = Math.abs(row - source[i - 1][0]) + Math.abs(col - source[i - 1][1]);
                if (distance != 1) {
                    throw new IllegalArgumentException("route " + route + " has a non-adjacent step");
                }
            }
            copy[i][0] = row;
            copy[i][1] = col;
        }
        int[] end = copy[copy.length - 1];
        if (end[0] != eggRow || end[1] != eggCol) {
            throw new IllegalArgumentException("route " + route + " must end at egg");
        }
        return copy;
    }

    // ===== 只读访问 =====

    public State getState() { return state; }
    public Difficulty getDifficulty() { return difficulty; }

    /** 应用难度：初始金币 × 难度倍率（仅 PREPARING 阶段生效）。 */
    public void applyDifficulty(Difficulty d) {
        this.difficulty = d != null ? d : Difficulty.NORMAL;
        if (state == State.PREPARING) {
            coin = Math.round(startCoin * difficulty.coinMul);
        }
    }
    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public int getCoin() { return coin; }
    public int getMascotHp() { return mascotHp; }
    public int getMaxMascotHp() { return maxMascotHp; }
    public int getWaveIndex() { return Math.min(waveIndex + 1, totalWaves); }
    public int getTotalWaves() { return totalWaves; }
    /** 波次定义的只读快照，供关卡预览和规则测试检查教学投放。 */
    public List<Wave> getWaves() { return new ArrayList<>(waves); }
    public long getTicks() { return gameTicks; }
    public int getCoinsEarned() { return coinsEarned; }
    public int getMonstersKilled() { return monstersKilled; }
    public int getMascotHpLost() { return mascotHpLost; }
    public int getMonstersSpawnedTotal() { return monstersSpawnedTotal; }
    /** 关内已存活秒数 */
    public float getElapsedSeconds() { return elapsedTicks * FIXED_DT; }
    /** 通关耗时标记：局内推帧计数（供 UI 展示，不用于胜负判定） */
    public long getElapsedTicksForDisplay() { return elapsedTicks; }
    public List<Monster> getMonsters() { return monsters; }
    public List<Tower> getTowers() { return towers; }
    public List<Beam> getBeams() { return beams; }
    public List<BurnZone> getBurnZones() { return burnZones; }
    public float getEggHitTimer() { return eggHitTimer; }

    /**
     * 取走本帧累计的击杀事件（取出即清空），UI 每帧调用一次用于播放特效。
     * 引擎只负责记录事件，不依赖 UI。
     */
    public List<KillEvent> drainKillEvents() {
        List<KillEvent> out = new ArrayList<>(killEvents);
        killEvents.clear();
        return out;
    }
    public String getLastActionMessage() { return lastActionMessage; }
    public String getLastActionTone() { return lastActionTone; }
    /** 主路线（兼容旧调用方）；多入口地图请使用 {@link #getPaths()}。 */
    public int[][] getPath() { return paths[0]; }
    /** 所有可视路线。调用方只能读取，不得修改返回内容。 */
    public int[][][] getPaths() { return paths; }
    public int getRouteLength(int routeIndex) {
        return routeIndex >= 0 && routeIndex < paths.length ? paths[routeIndex].length : 0;
    }
    public int getEggRow() { return eggRow; }
    public int getEggCol() { return eggCol; }
    public boolean isEnded() { return state == State.WON || state == State.LOST; }

    /** 下一波（未开始的下一波）怪类型 Display 名；无则返回空串 */
    public String nextWaveTypeName() {
        Wave wave = upcomingWave();
        return wave == null ? "" : wave.previewName();
    }
    /** 下一波怪数量；无则 0 */
    public int nextWaveCount() {
        Wave wave = upcomingWave();
        return wave == null ? 0 : wave.count;
    }

    /** 下一未开波次使用的路线（准备阶段为第 1 波）。 */
    public int nextWaveRouteIndex() {
        Wave wave = upcomingWave();
        return wave == null ? -1 : wave.routeIndex;
    }

    private Wave upcomingWave() {
        int nextIdx = state == State.PREPARING ? 0 : waveIndex + 1;
        return nextIdx >= 0 && nextIdx < totalWaves ? waves.get(nextIdx) : null;
    }

    /** 当前波是否仍在向场上刷怪（生成中）——供 UI/测试判断是否可推进下一波 */
    public boolean isWaveSpawning() {
        return waveStarted && spawnedInWave < getCurrentWaveCount();
    }

    /** 战斗是否活跃：正在刷怪或场上仍有怪。太阳花等经济来源仅在活跃期生效。 */
    public boolean isCombatActive() {
        return state == State.RUNNING && (waveStarted || !monsters.isEmpty());
    }

    /** 当前波定义（越界时返回空哨兵） */
    private int getCurrentWaveCount() {
        if (waveIndex >= totalWaves) return 0;
        return waves.get(waveIndex).count;
    }

    public Tower getTowerAt(int row, int col) {
        return towerGrid.get(key(row, col));
    }

    /** 该格是否为路径格 */
    public boolean isPathCell(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols && pathCells[row][col];
    }

    public boolean isEggCell(int row, int col) {
        return row == eggRow && col == eggCol;
    }

    /** 地雷只能贴着道路布置，既保持陷阱语义，也不会占用道路本身。 */
    public boolean isMinePlacementCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols || isPathCell(row, col) || isEggCell(row, col)) {
            return false;
        }
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (Math.abs(dr) + Math.abs(dc) != 1) continue;
                if (isPathCell(row + dr, col + dc)) return true;
            }
        }
        return false;
    }

    private static int key(int row, int col) { return row * 1000 + col; }

    // ===== 玩家操作 =====

    /** 新建塔。失败返回 null（金币不足/占用/路径/越界/已结束）。 */
    public Tower placeTower(TowerType type, int row, int col) {
        clearAction();
        if (type == null) { return fail("无效的塔类型"); }
        if (state == State.WON || state == State.LOST) { return fail("对局已结束"); }
        if (row < 0 || row >= rows || col < 0 || col >= cols) { return fail("位置越界"); }
        if (type == TowerType.MINE && !isMinePlacementCell(row, col)) {
            return fail("地雷塔只能放在紧邻路径的陷阱位");
        }
        if (type != TowerType.MINE && isPathCell(row, col)) { return fail("不能在路径上建塔"); }
        if (isEggCell(row, col)) { return fail("不能占蛋蛋的位置"); }
        if (towerGrid.containsKey(key(row, col))) { return fail("该位置已有塔"); }
        if (coin < type.baseCost) { return fail("金币不足，需要 " + type.baseCost); }
        coin -= type.baseCost;
        Tower t = new Tower(type, row, col);
        towers.add(t);
        towerGrid.put(key(row, col), t);
        lastActionTone = "ok";
        lastActionMessage = "已放置 " + type.displayName;
        return t;
    }

    /**
     * 旧的点按升级入口保留为兼容门面，但不再改变状态。
     * 统一等级规则为同类型、同等级的两座塔合成；调用方须使用 {@link #mergeTowers}。
     */
    public boolean upgradeTower(int row, int col) {
        clearAction();
        Tower t = towerGrid.get(key(row, col));
        lastActionMessage = t == null ? "该位置没有塔" : "请用两座同级" + t.type.displayName + "合成升级";
        lastActionTone = t == null ? "err" : "info";
        return false;
    }

    /**
     * 原子合成：source 被消耗，target 原地升一级。
     * 所有校验均发生在状态写入前；任何失败都不会改变金币、塔列表、网格索引或等级。
     */
    public boolean mergeTowers(int sourceRow, int sourceCol, int targetRow, int targetCol) {
        clearAction();
        if (state == State.WON || state == State.LOST) {
            lastActionMessage = "对局已结束";
            lastActionTone = "err";
            return false;
        }
        if (sourceRow == targetRow && sourceCol == targetCol) {
            lastActionMessage = "请选择另一座同级同类塔";
            lastActionTone = "err";
            return false;
        }
        Tower source = towerGrid.get(key(sourceRow, sourceCol));
        Tower target = towerGrid.get(key(targetRow, targetCol));
        if (source == null || target == null) {
            lastActionMessage = "合成需要两座已建造的塔";
            lastActionTone = "err";
            return false;
        }
        if (source.type != target.type) {
            lastActionMessage = "只能合成同类型防御塔";
            lastActionTone = "err";
            return false;
        }
        if (source.level != target.level) {
            lastActionMessage = "只能合成相同等级的防御塔";
            lastActionTone = "err";
            return false;
        }
        if (target.level >= 3) {
            lastActionMessage = "Lv3 已是最高等级，不能继续合成";
            lastActionTone = "info";
            return false;
        }

        // 所有条件已满足，以下为一次性提交点。
        towerGrid.remove(key(sourceRow, sourceCol));
        towers.remove(source);
        target.level++;
        target.investedCost += source.investedCost;
        target.buildAge = 0;
        lastActionTone = "ok";
        lastActionMessage = source.type.displayName + " 合成为 Lv" + target.level;
        return true;
    }

    /** 切换塔的目标优先级，给玩家主动调度高价值目标的空间。 */
    public boolean cycleTowerTargetMode(int row, int col) {
        clearAction();
        if (state == State.WON || state == State.LOST) {
            lastActionMessage = "对局已结束";
            lastActionTone = "err";
            return false;
        }
        Tower tower = towerGrid.get(key(row, col));
        if (tower == null || tower.type == TowerType.SUN) {
            lastActionMessage = tower == null ? "该位置没有塔" : "太阳花不需要选择目标";
            lastActionTone = "info";
            return false;
        }
        tower.targetMode = tower.targetMode.next();
        lastActionTone = "info";
        lastActionMessage = tower.type.displayName + "目标：" + tower.targetMode.displayName;
        return true;
    }

    /** 卖出塔，返还 60% 已投入。返回 true 表示成功。 */
    public boolean sellTower(int row, int col) {
        clearAction();
        if (state == State.WON || state == State.LOST) { return false; }
        Tower t = towerGrid.remove(key(row, col));
        if (t == null) { lastActionMessage = "该位置没有塔"; lastActionTone = "err"; return false; }
        towers.remove(t);
        int refund = (int) (t.totalInvested() * 0.6f);
        coin += refund;
        lastActionTone = "info";
        lastActionMessage = "卖出返还 " + refund + " 金币";
        return true;
    }

    /** 立刻开始下一波（PREPARING 时开始首波）；生成中可加速召唤剩余敌人换取奖励。 */
    public boolean startNextWaveEarly() {
        clearAction();
        if (state == State.WON || state == State.LOST) { lastActionMessage = "对局已结束"; lastActionTone = "err"; return false; }
        if (state == State.PREPARING) {
            // 首波开战
            state = State.RUNNING;
            waveIndex = 0;
            waveStarted = true;
            spawnedInWave = 0;
            spawnTimer = waves.get(0).startDelaySec;
            lastActionTone = "info";
            lastActionMessage = "第 1 波来袭，准备防守！";
            return true;
        }
        Wave w = waves.get(waveIndex);
        if (waveStarted) {
            // 正在生成：把剩余敌人立即召唤出来，按风险给予少量奖励。
            int remaining = w.count - spawnedInWave;
            if (remaining > 0) {
                spawnRemainingInstantly(w, spawnedInWave);
                int bonus = 2 * remaining;
                coin += bonus;
                coinsEarned += bonus;
                lastActionTone = "info";
                lastActionMessage = "加速召唤 " + remaining + " 名敌人，奖励 " + bonus + " 金币";
            } else {
                lastActionMessage = "本波已全部生成"; lastActionTone = "info";
            }
            waveStarted = false;
            return true;
        }
        // 本波已生成完 → 进入下一波（无下一波时提示）
        if (waveIndex + 1 >= totalWaves) {
            lastActionMessage = "已是最后一波"; lastActionTone = "info";
            return false;
        }
        waveIndex++;
        waveStarted = true;
        spawnedInWave = 0;
        Wave next = waves.get(waveIndex);
        spawnTimer = next.startDelaySec;
        lastActionTone = "info";
        lastActionMessage = "第 " + (waveIndex + 1) + " 波来袭";
        return true;
    }

    private void spawnRemainingInstantly(Wave w, int startIdx) {
        for (int i = startIdx; i < w.count; i++) {
            spawnMonster(w, i);
        }
        spawnedInWave = w.count;
    }

    private void spawnMonster(Wave wave, int spawnIndex) {
        int[][] route = paths[wave.routeIndex];
        int[] start = route[0];
        Monster monster = new Monster(wave.typeAt(spawnIndex), start[1], start[0], route.length,
                nextMonsterId++, wave.hpMul * difficulty.hpMul,
                wave.speedMul * difficulty.speedMul, waveIndex + 1, wave.routeIndex);
        monsters.add(monster);
        monstersSpawnedTotal++;
    }

    // ===== 主循环 =====

    /** 推进一帧（1/60 秒）。返回当前状态。 */
    public State tick() {
        if (state == State.WON || state == State.LOST) return state;
        gameTicks++;
        if (state == State.RUNNING) elapsedTicks++;
        if (eggHitTimer > 0f) eggHitTimer = Math.max(0f, eggHitTimer - FIXED_DT);
        updateSpawning();
        updateMonsters();
        flushDerivedMonsters();
        updateBurnZones();
        updateTowers();
        flushDerivedMonsters();
        pruneBeams();
        checkEnd();
        return state;
    }

    private void updateSpawning() {
        if (!waveStarted) return;      // 等待玩家开战/下一波
        if (waveIndex >= totalWaves) return;
        Wave w = waves.get(waveIndex);
        if (spawnedInWave >= w.count) {
            waveStarted = false;       // 本波生成完毕，等待玩家手动推进
            return;
        }
        if (spawnTimer > 0f) {
            // 出怪倒计时逐帧扣减；扣到 ≤0 的那帧才开始第一只，保证 intervalSec 真实生效
            spawnTimer = Math.max(0f, spawnTimer - FIXED_DT);
            if (spawnTimer > 0f) return;
        }
        if (w.intervalSec <= 0f) {
            // 零间隔保留原语义：整波单帧放完（BOSS 波单只等场景）
            spawnRemainingInstantly(w, spawnedInWave);
            waveStarted = false;
            return;
        }
        // 每个周期至多一只；间隔小于一帧也按一帧节流，杜绝同帧连锁生成
        spawnMonster(w, spawnedInWave++);
        if (spawnedInWave < w.count) {
            spawnTimer = Math.max(w.intervalSec, FIXED_DT);
        }
    }

    private void updateMonsters() {
        for (Monster m : monsters) {
            if (m.dead) continue;
            if (m.hitFlash > 0f) m.hitFlash = Math.max(0f, m.hitFlash - FIXED_DT);
            if (m.healedFlash > 0f) m.healedFlash = Math.max(0f, m.healedFlash - FIXED_DT);
            if (m.shieldFlash > 0f) m.shieldFlash = Math.max(0f, m.shieldFlash - FIXED_DT);
            // 减速：只临时缩放，过期后还原出生倍率（困难/特殊波的速度系数不被抹掉）
            if (m.slowTimer > 0f) {
                m.slowTimer -= FIXED_DT;
                if (m.slowTimer <= 0f) m.speedMul = m.baseSpeedMul;
            }
            // 中毒
            if (m.dotTimer > 0f) {
                m.dotTimer -= FIXED_DT;
                takeDamage(m, m.dotDps * FIXED_DT, DamageKind.POISON);
                if (m.dotTimer <= 0f) { m.dotDps = 0f; }
            }
            if (m.dead) continue;
            if (m.type == MonsterType.HEALER) updateHealer(m);
            if (m.type == MonsterType.CHARGER) updateCharger(m);
            if (m.type == MonsterType.SHIELD_GENERATOR) updateShieldGenerator(m);
            if (m.type == MonsterType.SUMMONER) updateSummoner(m);
            // 移动
            moveMonster(m);
        }
        // 移除死亡/到达终点的怪
        monsters.removeIf(m -> m.dead);
    }

    private void flushDerivedMonsters() {
        if (pendingDerivedMonsters.isEmpty()) return;
        monsters.addAll(pendingDerivedMonsters);
        monstersSpawnedTotal += pendingDerivedMonsters.size();
        pendingDerivedMonsters.clear();
    }

    private void updateBurnZones() {
        for (BurnZone zone : burnZones) {
            zone.secondsLeft -= FIXED_DT;
            for (Monster monster : monsters) {
                if (!monster.dead && dist(zone.x, zone.y, monster.x, monster.y) <= zone.radius) {
                    takeDamage(monster, TowerType.MINE_BURN_DPS * FIXED_DT, DamageKind.BURN);
                }
            }
        }
        burnZones.removeIf(zone -> zone.secondsLeft <= 0f);
    }

    /** 医生怪优先治疗范围内受伤最重的存活同伴，不能复活或超量治疗。 */
    private void updateHealer(Monster healer) {
        healer.healTimer -= FIXED_DT;
        if (healer.healTimer > 0f) return;
        healer.healTimer = 1.0f;
        Monster target = null;
        float mostMissing = 0f;
        for (Monster candidate : monsters) {
            if (candidate.dead || candidate == healer || dist(candidate.x, candidate.y, healer.x, healer.y) > 1.85f) {
                continue;
            }
            float missing = candidate.maxHp - candidate.hp;
            if (missing > mostMissing) {
                mostMissing = missing;
                target = candidate;
            }
        }
        if (target != null) {
            target.hp = Math.min(target.maxHp, target.hp + Math.min(16f, mostMissing));
            target.healedFlash = 0.32f;
        }
    }

    /** 冲锋使用独立临时倍率，绝不写入或覆盖雪花维护的 speedMul/baseSpeedMul。 */
    private void updateCharger(Monster charger) {
        if (charger.chargeTimer > 0f) {
            charger.chargeTimer -= FIXED_DT;
            if (charger.chargeTimer <= 0f) charger.charging = false;
            return;
        }
        charger.chargeCooldown -= FIXED_DT;
        if (charger.chargeCooldown <= 0f) {
            charger.charging = true;
            charger.chargeTimer = .65f;
            charger.chargeCooldown = 3.6f;
        }
    }

    /** 护盾发生器每次最多照顾三名同伴；同一目标护盾永远受 maxShield 限制。 */
    private void updateShieldGenerator(Monster generator) {
        generator.shieldPulseTimer -= FIXED_DT;
        if (generator.shieldPulseTimer > 0f) return;
        generator.shieldPulseTimer = 2.5f;
        int affected = 0;
        for (Monster candidate : monsters) {
            if (candidate.dead || candidate == generator
                    || dist(candidate.x, candidate.y, generator.x, generator.y) > 2.15f) {
                continue;
            }
            float cap = MonsterType.SHIELD.shieldHp(MonsterType.SHIELD) + 32f;
            candidate.maxShield = Math.max(candidate.maxShield, cap);
            candidate.shield = Math.min(candidate.maxShield, candidate.shield + 32f);
            candidate.shieldFlash = .38f;
            if (++affected >= 3) return;
        }
    }

    /** 每名召唤怪最多进行四次召唤，每次两只带来源标记的低价值喽罗。 */
    private void updateSummoner(Monster summoner) {
        if (summoner.summoned || summoner.summonsRemaining <= 0) return;
        summoner.summonTimer -= FIXED_DT;
        if (summoner.summonTimer > 0f) return;
        summoner.summonTimer = 6f;
        summoner.summonsRemaining--;
        queueSummonedMinion(summoner);
        queueSummonedMinion(summoner);
    }

    private void moveMonster(Monster m) {
        float behaviorSpeedMul = getBehaviorSpeedMultiplier(m);
        float effSpeed = m.type.speed * m.speedMul * behaviorSpeedMul * FIXED_DT;
        float remaining = effSpeed;
        int[][] route = paths[m.routeIndex];
        // 当前所在格子起点
        int lastIdx = route.length - 1;
        while (remaining > 0f) {
            if (m.pathIndex >= lastIdx) {
                // 已到达终点格 → 吃蛋蛋
                reachEgg(m);
                return;
            }
            int curRow = route[m.pathIndex][0];
            int curCol = route[m.pathIndex][1];
            int nxtRow = route[m.pathIndex + 1][0];
            int nxtCol = route[m.pathIndex + 1][1];
            float sx = curCol + 0.5f, sy = curRow + 0.5f;
            float ex = nxtCol + 0.5f, ey = nxtRow + 0.5f;
            float dx = ex - sx, dy = ey - sy;
            float segLen = (float) Math.sqrt(dx * dx + dy * dy);
            float segProgress = m.segT * segLen; // 本段已走距离
            float segRemain = segLen - segProgress;
            if (remaining >= segRemain) {
                remaining -= segRemain;
                m.pathIndex++;
                m.segT = 0f;
            } else {
                m.segT += remaining / segLen;
                remaining = 0f;
            }
        }
        // 更新显示坐标
        if (m.pathIndex < route.length) {
            int curRow = route[m.pathIndex][0];
            int curCol = route[m.pathIndex][1];
            int nxtIdx = m.pathIndex + 1;
            if (nxtIdx < route.length) {
                int nxtRow = route[nxtIdx][0];
                int nxtCol = route[nxtIdx][1];
                m.x = (curCol + 0.5f) + (nxtCol - curCol) * m.segT;
                m.y = (curRow + 0.5f) + (nxtRow - curRow) * m.segT;
            } else {
                m.x = curCol + 0.5f;
                m.y = curRow + 0.5f;
            }
        }
    }

    private void reachEgg(Monster m) {
        m.dead = true;
        int damage = m.type.leakDamage;
        mascotHp -= damage;
        mascotHpLost += damage;
        eggHitTimer = 0.6f;
        lastActionTone = "err";
        lastActionMessage = m.type.displayName + "突破防线！蛋蛋 -" + damage + "，剩余生命 " + mascotHp;
        if (mascotHp <= 0) {
            mascotHp = 0;
            state = State.LOST;
        }
    }

    private void takeDamage(Monster m, float dmg, DamageKind kind) {
        if (m == null || m.dead || dmg <= 0f) return;
        if (m.type == MonsterType.RESISTANT) {
            if (kind == DamageKind.POISON) dmg *= .60f;
            else if (kind == DamageKind.LIGHTNING_CHAIN) dmg *= .65f;
        }
        if (m.shield > 0f) {
            float absorbed = Math.min(m.shield, dmg);
            m.shield -= absorbed;
            dmg -= absorbed;
        }
        if (dmg > 0f) {
            float armorAbsorb = m.type.armor;
            float applied = Math.max(dmg - armorAbsorb, dmg * 0.2f); // 装甲最多减 80%
            m.hp -= applied;
            m.hitFlash = 0.18f; // 受击闪白（纯视觉）
        }
        if (m.type == MonsterType.RAGER && m.hp > 0f && m.hp <= m.maxHp * .5f) {
            m.enraged = true;
        }
        if (m.hp <= 0f) {
            m.hp = 0f;
            m.dead = true;
            coin += m.reward;
            coinsEarned += m.reward;
            monstersKilled++;
            killEvents.add(new KillEvent(m.x, m.y, m.reward,
                    m.type == MonsterType.BOSS));
            if (m.type == MonsterType.SPLITTER && !m.splitChild) {
                queueSplitChildren(m);
            }
        }
    }

    /** 分裂怪只派生两只低赏金喽罗；幼体带来源标记且不会再次分裂。 */
    private void queueSplitChildren(Monster parent) {
        for (int i = 0; i < 2; i++) {
            Monster child = createDerivedSwarm(parent);
            child.splitChild = true;
            pendingDerivedMonsters.add(child);
        }
    }

    private void queueSummonedMinion(Monster parent) {
        Monster minion = createDerivedSwarm(parent);
        minion.summoned = true;
        pendingDerivedMonsters.add(minion);
    }

    private Monster createDerivedSwarm(Monster parent) {
        int[][] route = paths[parent.routeIndex];
        int[] start = route[0];
        Monster child = new Monster(MonsterType.SWARM, start[1], start[0], route.length,
                nextMonsterId++, parent.maxHp / parent.type.hp, parent.baseSpeedMul,
                parent.waveNo, parent.routeIndex);
        child.pathIndex = parent.pathIndex;
        child.segT = parent.segT;
        child.x = parent.x;
        child.y = parent.y;
        child.originMonsterId = parent.id;
        child.reward = 1;
        return child;
    }

    private void updateTowers() {
        for (Tower t : towers) {
            t.buildAge++;
            if (t.type == TowerType.SUN) {
                // 太阳花产币；仅战斗活跃期累积与产出，堵死波间/准备期无限挂机
                if (!isCombatActive()) continue;
                t.incomeTimer -= FIXED_DT;
                if (t.incomeTimer <= 0f) {
                    t.incomeTimer = 1.8f; // 每 1.8 秒产一次
                    int gained = (int) (t.type.income * t.level);
                    coin += gained;
                    coinsEarned += gained;
                }
            } else if (t.type != TowerType.AMPLIFIER) {
                t.cooldown -= FIXED_DT;
                if (t.cooldown <= 0f) {
                    t.cooldown = effectiveFireIntervalAt(t);
                    fire(t);
                }
            }
        }
    }

    private void fire(Tower t) {
        Monster target = acquireTarget(t);
        if (target == null) return;
        float tx = target.x, ty = target.y;
        // 光束特效（UI 画）
        beams.add(new Beam(t.col + 0.5f, t.row + 0.5f, tx, ty, t.type));
        if (beams.size() > 40) beams.remove(0);
        float dmg = t.damageAt();
        switch (t.type) {
            case BOTTLE:
                takeDamage(target, dmg, DamageKind.DIRECT);
                break;
            case SNOW: {
                float slowEffect = target.type == MonsterType.RESISTANT ? .55f : 1f;
                target.slowTimer = TowerType.SNOW_SLOW_SEC * slowEffect;
                // 在出生倍率基础上缩放，避免覆盖波次/难度叠加出的原始速度
                target.speedMul = target.baseSpeedMul * (1f - TowerType.SNOW_SLOW_PCT * slowEffect);
                takeDamage(target, dmg * 0.4f, DamageKind.DIRECT);
                break;
            }
            case FAN: {
                // 周围溅射
                for (Monster m : monsters) {
                    if (m.dead) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS) takeDamage(m, dmg * 0.8f, DamageKind.DIRECT);
                }
                break;
            }
            case POISON: {
                // 对单个目标上毒，若附近有怪再溅射一点点
                target.dotDps = TowerType.POISON_DPS * (0.7f + 0.3f * t.level);
                float poisonEffect = target.type == MonsterType.RESISTANT ? .65f : 1f;
                target.dotTimer = TowerType.POISON_SEC * poisonEffect;
                takeDamage(target, dmg * 0.3f, DamageKind.DIRECT);
                for (Monster m : monsters) {
                    if (m.dead || m == target) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS * 0.8f) {
                        m.dotDps = TowerType.POISON_DPS * 0.5f;
                        float splashPoisonEffect = m.type == MonsterType.RESISTANT ? .65f : 1f;
                        m.dotTimer = TowerType.POISON_SEC * 0.7f * splashPoisonEffect;
                    }
                }
                break;
            }
            case ROCKET: {
                for (Monster m : monsters) {
                    if (m.dead) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS) takeDamage(m, dmg * 0.7f, DamageKind.DIRECT);
                }
                break;
            }
            case LIGHTNING:
                fireLightning(t, target, dmg);
                break;
            case SNIPER:
                takeDamage(target, dmg, DamageKind.DIRECT);
                break;
            case MINE: {
                float radius = t.type.mineBlastRadiusAt(t.level);
                for (Monster monster : monsters) {
                    if (!monster.dead && dist(monster.x, monster.y, tx, ty) <= radius) {
                        takeDamage(monster, dmg, DamageKind.DIRECT);
                    }
                }
                if (t.level >= 3) {
                    burnZones.add(new BurnZone(tx, ty, radius, TowerType.MINE_BURN_SEC));
                    if (burnZones.size() > 24) burnZones.remove(0);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * 有上限、不可回跳的雷电链。每次均从上一命中点搜索，且只接受存活目标，
     * 因而不会重复命中、不会攻击死亡单位，也不会无限扩张。
     */
    private void fireLightning(Tower tower, Monster first, float damage) {
        java.util.HashSet<Monster> hit = new java.util.HashSet<>();
        hit.add(first);
        takeDamage(first, damage, DamageKind.DIRECT);
        Monster previous = first;
        int maxTargets = tower.type.chainTargetCountAt(tower.level);
        for (int count = 1; count < maxTargets; count++) {
            Monster next = null;
            float bestDistance = Float.MAX_VALUE;
            for (Monster candidate : monsters) {
                if (candidate.dead || hit.contains(candidate)) continue;
                float distance = dist(previous.x, previous.y, candidate.x, candidate.y);
                if (distance <= TowerType.LIGHTNING_CHAIN_RANGE && distance < bestDistance) {
                    bestDistance = distance;
                    next = candidate;
                }
            }
            if (next == null) return;
            beams.add(new Beam(previous.x, previous.y, next.x, next.y, TowerType.LIGHTNING));
            if (beams.size() > 40) beams.remove(0);
            hit.add(next);
            takeDamage(next, damage * tower.type.chainDamageMultiplierAt(tower.level), DamageKind.LIGHTNING_CHAIN);
            previous = next;
        }
    }

    private Monster acquireTarget(Tower t) {
        Monster best = null;
        float bestScore = -Float.MAX_VALUE;
        float cx = t.col + 0.5f, cy = t.row + 0.5f;
        for (Monster m : monsters) {
            if (m.dead) continue;
            if (m.type.fly && !t.type.canAir) continue;
            float d = dist(m.x, m.y, cx, cy);
            if (d > effectiveRangeAt(t)) continue;
            // 狙击塔固定优先关键强敌，避免玩家忘记切目标模式时退化成昂贵瓶子炮。
            TargetMode mode = t.type == TowerType.SNIPER ? TargetMode.STRONG : t.targetMode;
            float score = targetScore(mode, m);
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    private static float targetScore(TargetMode mode, Monster monster) {
        float progress = monster.pathProgress();
        switch (mode) {
            case STRONG:
                // 最大生命与护盾共同决定“强敌”，再以路线进度打破平局。
                return monster.maxHp + monster.shield + progress * 0.01f;
            case WEAK:
                // 先收掉残血怪以兑现击杀金币，避免用 0 血比例外的绝对值偏袒坦克。
                return (1f - monster.hp / monster.maxHp) * 1000f + progress;
            case FIRST:
            default:
                return progress;
        }
    }

    private void pruneBeams() {
        beams.removeIf(b -> --b.life <= 0);
    }

    private void checkEnd() {
        if (state == State.LOST) return;
        // 最后一波已开始且生成完毕、场上无怪 → 胜利
        boolean lastWaveDone = waveIndex + 1 >= totalWaves && !waveStarted;
        if (lastWaveDone && monsters.isEmpty()) {
            state = State.WON;
            lastActionTone = "ok";
            lastActionMessage = "胜利！蛋蛋安全了";
        }
    }

    /** 通关星级：3=满血，2=>=60%血，1=其他。未通关返回 0。 */
    public int starsEarned() {
        if (state != State.WON) return 0;
        float ratio = (float) mascotHp / maxMascotHp;
        if (ratio >= 1f) return 3;
        if (ratio >= 0.6f) return 2;
        return 1;
    }

    /** 是否仍被该塔所在格子占用（卖掉后该格可重建） */
    public boolean isCellOccupied(int row, int col) {
        return towerGrid.containsKey(key(row, col));
    }

    /** 供渲染和测试读取的临时行为倍率；始终与基础/减速倍率相乘。 */
    public float getBehaviorSpeedMultiplier(Monster monster) {
        if (monster == null) return 1f;
        if (monster.charging) return 1.9f;
        return monster.enraged ? 1.3f : 1f;
    }

    /** 当前塔获得的攻击速度加成。重叠增幅塔只取最高等级的一份。 */
    public float getAttackSpeedBonus(Tower target) {
        Tower source = strongestAmplifierFor(target);
        return source == null ? 0f : source.type.amplifierAttackSpeedBonusAt(source.level);
    }

    /** 当前塔获得的射程加成；和攻速一样不叠加。 */
    public float getRangeBonus(Tower target) {
        Tower source = strongestAmplifierFor(target);
        return source == null ? 0f : source.type.amplifierRangeBonusAt(source.level);
    }

    public float effectiveRangeAt(Tower tower) {
        return tower.rangeAt() * (1f + getRangeBonus(tower));
    }

    public float effectiveFireIntervalAt(Tower tower) {
        return tower.fireIntervalAt() / (1f + getAttackSpeedBonus(tower));
    }

    private Tower strongestAmplifierFor(Tower target) {
        if (target == null || target.type == TowerType.AMPLIFIER || target.type == TowerType.SUN) return null;
        Tower strongest = null;
        float tx = target.col + .5f;
        float ty = target.row + .5f;
        for (Tower candidate : towers) {
            if (candidate.type != TowerType.AMPLIFIER || candidate == target) continue;
            float cx = candidate.col + .5f;
            float cy = candidate.row + .5f;
            if (dist(tx, ty, cx, cy) > candidate.rangeAt()) continue;
            if (strongest == null || candidate.level > strongest.level) strongest = candidate;
        }
        return strongest;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void clearAction() { lastActionMessage = ""; lastActionTone = "info"; }

    private Tower fail(String msg) {
        lastActionMessage = msg;
        lastActionTone = "err";
        return null;
    }
}
