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
        public float cooldown = 0f;
        public float incomeTimer = 0f;
        public TargetMode targetMode = TargetMode.FIRST;
        /** 建成后经过的帧数（UI 做缩放登场动画） */
        public int buildAge = 0;

        Tower(TowerType type, int row, int col) {
            this.type = type;
            this.row = row;
            this.col = col;
        }

        public float damageAt() { return type.damageAt(level); }
        public float rangeAt() { return type.rangeAt(level); }
        public float fireIntervalAt() { return type.fireIntervalAt(level); }
        public int totalInvested() { return type.totalCostUpTo(level); }
    }

    /** 怪兽实例 */
    public static class Monster {
        public final MonsterType type;
        public float hp;
        public final float maxHp;
        public float speedMul = 1f;
        public float slowTimer = 0f;
        public float dotDps = 0f;
        public float dotTimer = 0f;
        public float shield = 0f;
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
            this.speedMul = speedMul;
            this.shield = type.shieldHp(type);
            this.x = startingX + 0.5f;
            this.y = startingY + 0.5f;
            this.pathIndex = 0;
            this.segT = 0f;
            this.pathLen = pathLen;
            this.id = id;
            this.waveNo = waveNo;
            this.routeIndex = routeIndex;
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

    private static int key(int row, int col) { return row * 1000 + col; }

    // ===== 玩家操作 =====

    /** 新建塔。失败返回 null（金币不足/占用/路径/越界/已结束）。 */
    public Tower placeTower(TowerType type, int row, int col) {
        clearAction();
        if (type == null) { return fail("无效的塔类型"); }
        if (state == State.WON || state == State.LOST) { return fail("对局已结束"); }
        if (row < 0 || row >= rows || col < 0 || col >= cols) { return fail("位置越界"); }
        if (isPathCell(row, col)) { return fail("不能在路径上建塔"); }
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

    /** 升级塔。失败返回 false（已满级/金币不足）。 */
    public boolean upgradeTower(int row, int col) {
        clearAction();
        if (state == State.WON || state == State.LOST) { return false; }
        Tower t = towerGrid.get(key(row, col));
        if (t == null) { lastActionMessage = "该位置没有塔"; lastActionTone = "err"; return false; }
        if (t.level >= 3) { lastActionMessage = "已满级（Lv3）"; lastActionTone = "info"; return false; }
        int cost = t.type.upgradeCost(t.level + 1);
        if (coin < cost) { lastActionMessage = "金币不足，升级需要 " + cost; lastActionTone = "err"; return false; }
        coin -= cost;
        t.level++;
        lastActionTone = "ok";
        lastActionMessage = t.type.displayName + " 升级到 Lv" + t.level;
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
        updateTowers();
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
            spawnTimer -= FIXED_DT;
            return;
        }
        while (spawnedInWave < w.count) {
            spawnMonster(w, spawnedInWave);
            spawnedInWave++;
            if (spawnedInWave >= w.count) break;
            spawnTimer += w.intervalSec;
        }
    }

    private void updateMonsters() {
        for (Monster m : monsters) {
            if (m.dead) continue;
            if (m.hitFlash > 0f) m.hitFlash = Math.max(0f, m.hitFlash - FIXED_DT);
            if (m.healedFlash > 0f) m.healedFlash = Math.max(0f, m.healedFlash - FIXED_DT);
            // 减速
            if (m.slowTimer > 0f) { m.slowTimer -= FIXED_DT; if (m.slowTimer <= 0f) m.speedMul = 1f; }
            // 中毒
            if (m.dotTimer > 0f) {
                m.dotTimer -= FIXED_DT;
                takeDamage(m, m.dotDps * FIXED_DT, false);
                if (m.dotTimer <= 0f) { m.dotDps = 0f; }
            }
            if (m.dead) continue;
            if (m.type == MonsterType.HEALER) updateHealer(m);
            // 移动
            moveMonster(m);
        }
        // 移除死亡/到达终点的怪
        monsters.removeIf(m -> m.dead);
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

    private void moveMonster(Monster m) {
        float effSpeed = m.type.speed * m.speedMul * FIXED_DT;
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

    private void takeDamage(Monster m, float dmg, boolean isPoisonLike) {
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
        if (m.hp <= 0f) {
            m.hp = 0f;
            m.dead = true;
            coin += m.type.value;
            coinsEarned += m.type.value;
            monstersKilled++;
            killEvents.add(new KillEvent(m.x, m.y, m.type.value,
                    m.type == MonsterType.BOSS));
        }
    }

    private void updateTowers() {
        for (Tower t : towers) {
            t.buildAge++;
            if (t.type == TowerType.SUN) {
                // 太阳花产币
                t.incomeTimer -= FIXED_DT;
                if (t.incomeTimer <= 0f) {
                    t.incomeTimer = 1.8f; // 每 1.8 秒产一次
                    int gained = (int) (t.type.income * t.level);
                    coin += gained;
                    coinsEarned += gained;
                }
            } else {
                t.cooldown -= FIXED_DT;
                if (t.cooldown <= 0f) {
                    t.cooldown = t.fireIntervalAt();
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
                takeDamage(target, dmg, false);
                break;
            case SNOW: {
                target.slowTimer = TowerType.SNOW_SLOW_SEC;
                target.speedMul = 1f - TowerType.SNOW_SLOW_PCT;
                takeDamage(target, dmg * 0.4f, false);
                break;
            }
            case FAN: {
                // 周围溅射
                for (Monster m : monsters) {
                    if (m.dead) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS) takeDamage(m, dmg * 0.8f, false);
                }
                break;
            }
            case POISON: {
                // 对单个目标上毒，若附近有怪再溅射一点点
                target.dotDps = TowerType.POISON_DPS * (0.7f + 0.3f * t.level);
                target.dotTimer = TowerType.POISON_SEC;
                takeDamage(target, dmg * 0.3f, true);
                for (Monster m : monsters) {
                    if (m.dead || m == target) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS * 0.8f) {
                        m.dotDps = TowerType.POISON_DPS * 0.5f;
                        m.dotTimer = TowerType.POISON_SEC * 0.7f;
                    }
                }
                break;
            }
            case ROCKET: {
                for (Monster m : monsters) {
                    if (m.dead) continue;
                    float d = dist(m.x, m.y, tx, ty);
                    if (d <= TowerType.AOE_RADIUS) takeDamage(m, dmg * 0.7f, false);
                }
                break;
            }
            default:
                break;
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
            if (d > t.rangeAt()) continue;
            float score = targetScore(t.targetMode, m);
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
