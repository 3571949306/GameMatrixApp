package com.gamecenter.app.games.brotato;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Brotato 风格射击生存游戏的核心逻辑引擎。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理游戏世界状态：玩家、敌人、子弹、拾取物、武器</li>
 *   <li>驱动每帧更新：敌人生成、武器射击、碰撞检测、拾取物收集</li>
 *   <li>处理升级系统：经验值积累、升级选项生成与选择</li>
 *   <li>控制波次推进与 Boss 生成的时间节奏</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>游戏逻辑与渲染完全分离，由 Activity 驱动 update() 调用</li>
 *   <li>使用"威胁等级"（bossThreatLevel）机制随时间递增难度，
 *       Boss 击杀后提升全局敌人属性</li>
 *   <li>坐标系统以游戏单位（非像素）表示，由 View 层负责屏幕映射</li>
 *   <li>内部类（Player、Enemy、Bullet、Weapon、Pickup、UpgradeOption）
 *       均为纯数据结构，不包含渲染逻辑</li>
 * </ul>
 */
public class BrotatoGame {

    /** 游戏棋盘宽度（游戏单位） */
    public static final int BOARD_WIDTH = 40;

    /** 游戏棋盘高度（游戏单位） */
    public static final int BOARD_HEIGHT = 60;

    /** 玩家可携带的最大武器数量 */
    public static final int MAX_WEAPONS = 6;

    /** 精英敌人生成间隔（毫秒） */
    public static final long ELITE_INTERVAL = 30000L;

    /** 小 Boss 生成间隔（毫秒） */
    public static final long MINI_BOSS_INTERVAL = 50000L;

    /** 终局 Boss 出现时间（毫秒），即游戏 10 分钟时 */
    public static final long FINAL_BOSS_TIME = 600000L;

    /** 地图拾取物生成间隔（毫秒） */
    public static final long MAP_PICKUP_INTERVAL = 30000L;

    private final Random random = new Random();
    private Player player;
    private List<Enemy> enemies = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private List<Pickup> pickups = new ArrayList<>();
    private List<Weapon> weapons = new ArrayList<>();
    private List<UpgradeOption> upgradeOptions = new ArrayList<>();

    private int wave;
    private int score;
    private int gold;
    private int level;
    private int exp;
    private int expToLevel;
    private int kills;
    /** Boss 威胁等级，影响全局敌人的生命值、伤害和速度 */
    private int bossThreatLevel;
    private boolean gameOver;
    private boolean gameWon;
    private boolean finalBossSpawned;
    private boolean waitingForUpgrade;
    private long lastSpawnTime;
    private long startedAt;
    private long nextWaveAt;
    private long nextEliteAt;
    private long nextMiniBossAt;
    private long nextMapPickupAt;
    private long elapsedTime;

    /**
     * 构造函数，初始化时自动调用 reset()。
     */
    public BrotatoGame() {
        reset();
    }

    /**
     * 重置所有游戏状态到初始值。
     * <p>
     * 玩家出生在原点，初始携带一把手枪，
     * 所有计时器和计数器归零。
     */
    public void reset() {
        player = new Player(0f, 0f);
        enemies.clear();
        bullets.clear();
        pickups.clear();
        weapons.clear();
        upgradeOptions.clear();

        weapons.add(Weapon.create(Weapon.Type.PISTOL));
        wave = 1;
        score = 0;
        gold = 0;
        level = 1;
        exp = 0;
        expToLevel = 14;
        kills = 0;
        bossThreatLevel = 0;
        gameOver = false;
        gameWon = false;
        finalBossSpawned = false;
        waitingForUpgrade = false;
        lastSpawnTime = 0;
        startedAt = 0;
        nextWaveAt = 0;
        nextEliteAt = 0;
        nextMiniBossAt = 0;
        nextMapPickupAt = 0;
        elapsedTime = 0;
    }

    /**
     * 游戏主更新方法，每帧调用一次。
     * <p>
     * 按顺序执行：时间初始化 → 波次推进 → 定时威胁生成 → 地图拾取物生成 →
     * 普通敌人生成 → 武器射击 → 子弹移动 → 敌人移动 → 拾取物收集 → 碰撞检测 → 生命回复。
     *
     * @param currentTime 当前系统时间（毫秒），用于计算时间差
     */
    public void update(long currentTime) {
        if (gameOver || waitingForUpgrade) return;

        // 首次调用时初始化所有计时器
        if (startedAt == 0) {
            startedAt = currentTime;
            nextWaveAt = currentTime + 25000;
            nextEliteAt = currentTime + ELITE_INTERVAL;
            nextMiniBossAt = currentTime + MINI_BOSS_INTERVAL;
            nextMapPickupAt = currentTime + 15000L;
        }
        elapsedTime = currentTime - startedAt;

        // 波次推进：每 25 秒进入下一波，奖励金币随波次递增
        if (currentTime >= nextWaveAt) {
            wave++;
            gold += 10 + wave * 4;
            nextWaveAt = currentTime + 25000;
        }

        spawnTimedThreats(currentTime);
        spawnMapPickup(currentTime);
        spawnEnemies(currentTime);
        updateWeapons(currentTime);
        updateBullets();
        updateEnemies();
        collectPickups();
        handleCollisions(currentTime);
        player.regenerate();
    }

    /**
     * 根据时间节奏生成 Boss 级威胁（终局 Boss、小 Boss、精英）。
     * <p>
     * 生成优先级：终局 Boss > 小 Boss > 精英。
     * 为避免 Boss 同时出现，当两个生成时间过于接近（<5秒）时，延迟较低优先级的生成。
     *
     * @param currentTime 当前系统时间
     */
    private void spawnTimedThreats(long currentTime) {
        // 终局 Boss：游戏 10 分钟时出现，出现后重置精英和小 Boss 的计时器
        if (!finalBossSpawned && elapsedTime >= FINAL_BOSS_TIME) {
            spawnEnemyAtEdge(Enemy.Kind.FINAL_BOSS);
            bossThreatLevel += 4;
            finalBossSpawned = true;
            nextEliteAt = currentTime + ELITE_INTERVAL;
            nextMiniBossAt = currentTime + MINI_BOSS_INTERVAL;
            return;
        }

        // 小 Boss 生成
        if (currentTime >= nextMiniBossAt) {
            spawnEnemyAtEdge(Enemy.Kind.MINI_BOSS);
            bossThreatLevel += 2;
            nextMiniBossAt += MINI_BOSS_INTERVAL;
            // 如果精英生成时间与当前过于接近，延迟精英生成
            if (Math.abs(nextEliteAt - currentTime) < 5000L) {
                nextEliteAt = currentTime + 8000L;
            }
        }

        // 精英生成（避免与小 Boss 同时出现）
        if (currentTime >= nextEliteAt) {
            if (Math.abs(nextMiniBossAt - currentTime) > 5000L) {
                spawnEnemyAtEdge(Enemy.Kind.ELITE);
                bossThreatLevel += 1;
                nextEliteAt += ELITE_INTERVAL;
            } else {
                // 与小 Boss 时间冲突，延迟 8 秒
                nextEliteAt += 8000L;
            }
        }
    }

    /**
     * 定时在玩家周围生成地图拾取物（医疗包或磁铁）。
     * <p>
     * 55% 概率生成医疗包，45% 概率生成磁铁。
     * 生成位置在玩家周围 13~25 个游戏单位的圆环内随机分布。
     *
     * @param currentTime 当前系统时间
     */
    private void spawnMapPickup(long currentTime) {
        if (currentTime < nextMapPickupAt) return;

        Pickup.Type type = random.nextInt(100) < 55 ? Pickup.Type.MEDKIT : Pickup.Type.MAGNET;
        float angle = random.nextFloat() * (float) Math.PI * 2f;
        float radius = 13f + random.nextFloat() * 12f;
        float x = player.x + (float) Math.cos(angle) * radius;
        float y = player.y + (float) Math.sin(angle) * radius;
        pickups.add(new Pickup(x, y, type, 1));
        // 下次生成时间带随机偏移（±3.5秒）
        nextMapPickupAt = currentTime + MAP_PICKUP_INTERVAL + random.nextInt(7000) - 3500;
    }

    /**
     * 按节奏持续生成普通敌人。
     * <p>
     * 生成间隔随波次和威胁等级递减（最低 220ms），
     * 每次生成的数量随波次和威胁等级递增。
     *
     * @param currentTime 当前系统时间
     */
    private void spawnEnemies(long currentTime) {
        // 生成延迟随波次、威胁等级和玩家压力减免递减
        long spawnDelay = Math.max(220, 1250 - wave * 65L - bossThreatLevel * 45L - player.enemyPressureReduction);
        if (currentTime - lastSpawnTime < spawnDelay) return;

        // 每次生成 1~5 个敌人，数量随波次和威胁等级增长
        int count = 1 + Math.min(2, wave / 4) + Math.min(2, bossThreatLevel / 4);
        for (int i = 0; i < count; i++) {
            Enemy.Kind kind = rollEnemyKind();
            spawnEnemyAtEdge(kind);
        }
        lastSpawnTime = currentTime;
    }

    /**
     * 在玩家视野边缘的指定距离处生成一个敌人。
     * <p>
     * 终局 Boss 生成距离更远（34单位），其他敌人在 28~36 单位处。
     * 生成后应用当前威胁等级的属性加成。
     *
     * @param kind 敌人类型
     */
    private void spawnEnemyAtEdge(Enemy.Kind kind) {
        float angle = random.nextFloat() * (float) Math.PI * 2f;
        float radius = kind == Enemy.Kind.FINAL_BOSS ? 34f : 28f + random.nextFloat() * 8f;
        float x = player.x + (float) Math.cos(angle) * radius;
        float y = player.y + (float) Math.sin(angle) * radius;
        Enemy enemy = Enemy.create(kind, x, y, wave);
        enemy.applyThreatLevel(bossThreatLevel);
        enemies.add(enemy);
    }

    /**
     * 随机决定普通敌人的类型。
     * <p>
     * 波次 5+ 有 12% 概率生成壮汉（BRUTE），
     * 波次 3+ 有 16% 概率生成跑者（RUNNER），
     * 其余为普通小兵（GRUNT）。
     *
     * @return 随机生成的敌人类型
     */
    private Enemy.Kind rollEnemyKind() {
        int roll = random.nextInt(100);
        if (wave >= 5 && roll > 88) return Enemy.Kind.BRUTE;
        if (wave >= 3 && roll > 72) return Enemy.Kind.RUNNER;
        return Enemy.Kind.GRUNT;
    }

    /**
     * 更新所有武器的射击逻辑。
     * <p>
     * 每把武器独立计算冷却时间，冷却结束后自动寻找最近目标并开火。
     *
     * @param currentTime 当前系统时间
     */
    private void updateWeapons(long currentTime) {
        for (Weapon weapon : weapons) {
            if (currentTime - weapon.lastShotTime >= weapon.getFireDelay(player)) {
                Enemy target = findTarget(weapon.range);
                if (target != null) {
                    fireWeapon(weapon, target, currentTime);
                }
            }
        }
    }

    /**
     * 在指定范围内寻找距离玩家最近的存活敌人。
     *
     * @param range 搜索范围（游戏单位）
     * @return 最近的敌人，若无则返回 null
     */
    private Enemy findTarget(float range) {
        Enemy best = null;
        float bestDist = Float.MAX_VALUE;
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            float dist = distance(player.x, player.y, enemy.x, enemy.y);
            if (dist <= range && dist < bestDist) {
                best = enemy;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * 向目标敌人发射子弹。
     * <p>
     * 计算射击角度，若有多发弹体则按扇形分布。
     * 每发子弹的角度偏移量 = (索引 - 中心偏移) × 散布角度。
     *
     * @param weapon 发射的武器
     * @param target 目标敌人
     * @param currentTime 当前系统时间
     */
    private void fireWeapon(Weapon weapon, Enemy target, long currentTime) {
        float angle = (float) Math.atan2(target.y - player.y, target.x - player.x);
        int shots = weapon.projectiles + player.extraProjectiles;
        // 多发弹体时的散布角度
        float spread = shots <= 1 ? 0f : 0.16f;
        for (int i = 0; i < shots; i++) {
            float offset = (i - (shots - 1) / 2f) * spread;
            bullets.add(new Bullet(player.x, player.y, angle + offset, weapon, player));
        }
        weapon.lastShotTime = currentTime;
        // 更新玩家朝向角度
        player.angle = (float) Math.toDegrees(angle);
    }

    /**
     * 更新所有子弹的位置，移除超出范围或已失效的子弹。
     */
    private void updateBullets() {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            bullet.update();
            if (!bullet.active || bullet.isFarFrom(player)) {
                bullets.remove(i);
            }
        }
    }

    /**
     * 更新所有敌人的位置（向玩家移动）。
     */
    private void updateEnemies() {
        for (Enemy enemy : enemies) {
            enemy.update(player);
        }
    }

    /**
     * 处理拾取物的收集逻辑。
     * <p>
     * 拾取物在玩家磁力范围内会被吸引向玩家移动。
     * 接触后根据类型执行效果：金币加钱、经验加经验、
     * 医疗包回复 80% 最大生命、磁铁收集所有经验和金币、
     * 其他类型直接回复生命值。
     */
    private void collectPickups() {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pickup = pickups.get(i);
            // 磁力吸引：在拾取范围内时向玩家移动
            float magnetRange = 2.2f + player.pickupRange;
            if (distance(player.x, player.y, pickup.x, pickup.y) <= magnetRange) {
                float dx = player.x - pickup.x;
                float dy = player.y - pickup.y;
                float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
                pickup.x += dx / len * 1.25f;
                pickup.y += dy / len * 1.25f;
            }
            if (pickup.isCollected(player)) {
                if (pickup.type == Pickup.Type.GOLD) {
                    gold += 2 + wave / 2;
                } else if (pickup.type == Pickup.Type.EXP) {
                    addExp(pickup.value);
                } else if (pickup.type == Pickup.Type.MEDKIT) {
                    // 医疗包回复 80% 最大生命值
                    player.heal(Math.round(player.maxHp * 0.8f));
                } else if (pickup.type == Pickup.Type.MAGNET) {
                    // 磁铁立即收集场上所有经验和金币
                    collectAllEnergyPickups();
                    pickups.remove(pickup);
                    return;
                } else {
                    player.heal(pickup.value);
                }
                pickups.remove(i);
            }
        }
    }

    /**
     * 立即收集场上所有经验和金币类型的拾取物。
     * <p>
     * 由磁铁拾取物触发。
     */
    private void collectAllEnergyPickups() {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pickup = pickups.get(i);
            if (pickup.type == Pickup.Type.EXP) {
                addExp(pickup.value);
                pickups.remove(i);
            } else if (pickup.type == Pickup.Type.GOLD) {
                gold += 2 + wave / 2;
                pickups.remove(i);
            }
        }
    }

    /**
     * 处理子弹与敌人、敌人与玩家的碰撞检测。
     * <p>
     * 子弹命中敌人后扣减穿透次数，穿透耗尽后子弹失效。
     * 敌人碰撞玩家时造成伤害（有 650ms 的碰撞冷却），
     * 伤害值至少为 1（护甲可减免但不能完全抵消）。
     * 死亡的敌人和远离玩家的非 Boss 敌人会被移除。
     *
     * @param currentTime 当前系统时间，用于碰撞冷却判断
     */
    private void handleCollisions(long currentTime) {
        // 子弹与敌人碰撞
        for (Bullet bullet : bullets) {
            if (!bullet.active) continue;
            for (Enemy enemy : enemies) {
                if (enemy.isAlive() && bullet.collidesWith(enemy)) {
                    enemy.takeDamage(bullet.damage);
                    bullet.pierceLeft--;
                    if (bullet.pierceLeft < 0) {
                        bullet.active = false;
                    }
                    if (enemy.isDead()) {
                        onEnemyKilled(enemy);
                    }
                    if (!bullet.active) break;
                }
            }
        }

        // 敌人与玩家碰撞（650ms 冷却防止连续伤害）
        for (Enemy enemy : enemies) {
            if (enemy.isAlive() && enemy.collidesWith(player) && currentTime - enemy.lastHitTime >= 650) {
                // 伤害至少为 1，护甲不能完全抵消
                player.takeDamage(Math.max(1, enemy.damage - player.armor));
                enemy.lastHitTime = currentTime;
                if (player.isDead()) {
                    gameOver = true;
                    waitingForUpgrade = false;
                }
            }
        }

        // 清理死亡敌人和远离玩家的非 Boss 敌人
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDead()) {
                enemies.remove(i);
            } else if (!enemy.isBoss() && distance(enemy.x, enemy.y, player.x, player.y) > 76f) {
                // 非 Boss 敌人远离玩家超过 76 单位时自动移除
                enemies.remove(i);
            }
        }
    }

    /**
     * 敌人被击杀后的处理：增加击杀数、分数、金币，
     * 掉落经验和金币拾取物，有概率掉落生命拾取物。
     * <p>
     * 击杀终局 Boss 时触发胜利条件。
     *
     * @param enemy 被击杀的敌人
     */
    private void onEnemyKilled(Enemy enemy) {
        kills++;
        score += enemy.scoreValue;
        gold += enemy.goldValue;
        // 击杀终局 Boss 触发胜利
        if (enemy.kind == Enemy.Kind.FINAL_BOSS) {
            gameWon = true;
            gameOver = true;
            waitingForUpgrade = false;
        }
        // 必定掉落经验
        pickups.add(new Pickup(enemy.x, enemy.y, Pickup.Type.EXP, enemy.expValue));
        // 38% 概率掉落金币
        if (random.nextInt(100) < 38) {
            pickups.add(new Pickup(enemy.x + randomOffset(), enemy.y + randomOffset(), Pickup.Type.GOLD, 1));
        }
        // 6% + 幸运值 概率掉落生命拾取物
        if (random.nextInt(100) < 6 + player.luck) {
            pickups.add(new Pickup(enemy.x + randomOffset(), enemy.y + randomOffset(), Pickup.Type.HP, 5 + level));
        }
    }

    /**
     * 生成 -0.8 ~ 0.8 之间的随机偏移量，用于拾取物掉落位置散布。
     */
    private float randomOffset() {
        return -0.8f + random.nextFloat() * 1.6f;
    }

    /**
     * 增加经验值并检查是否升级。
     * <p>
     * 每次升级时：经验溢出部分保留、等级+1、升级所需经验递增、
     * 回复一定生命值、进入升级等待状态并生成升级选项。
     *
     * @param amount 获得的经验值
     */
    private void addExp(int amount) {
        exp += amount;
        while (exp >= expToLevel) {
            exp -= expToLevel;
            level++;
            expToLevel = 12 + level * 8;
            player.heal(6 + level);
            waitingForUpgrade = true;
            rollUpgradeOptions();
        }
    }

    /**
     * 通过方向增量移动玩家（用于非摇杆输入方式）。
     *
     * @param dx X 方向增量
     * @param dy Y 方向增量
     */
    public void movePlayer(float dx, float dy) {
        if (waitingForUpgrade || gameOver) return;
        player.move(dx, dy);
    }

    /**
     * 通过归一化摇杆输入移动玩家。
     *
     * @param inputX X 方向归一化输入 [-1, 1]
     * @param inputY Y 方向归一化输入 [-1, 1]
     */
    public void movePlayerInput(float inputX, float inputY) {
        if (waitingForUpgrade || gameOver) return;
        player.moveByInput(inputX, inputY);
    }

    /**
     * 随机生成 3 个不重复的升级选项。
     * <p>
     * 选项类型包括武器强化/新增和 11 种属性强化。
     * 武器选项的概率取决于当前武器数量是否已达上限。
     */
    public void rollUpgradeOptions() {
        upgradeOptions.clear();
        while (upgradeOptions.size() < 3) {
            UpgradeOption option = createRandomOption();
            if (!containsOption(option.title)) {
                upgradeOptions.add(option);
            }
        }
    }

    /**
     * 检查升级选项列表中是否已包含指定标题的选项（避免重复）。
     *
     * @param optionTitle 选项标题
     * @return 是否已包含
     */
    private boolean containsOption(String optionTitle) {
        for (UpgradeOption option : upgradeOptions) {
            if (option.title.equals(optionTitle)) return true;
        }
        return false;
    }

    /**
     * 随机创建一个升级选项。
     * <p>
     * 武器未满 6 把时 35% 概率出武器选项，已满时降至 18%。
     * 其余按等概率从 11 种属性强化中选择。
     *
     * @return 随机生成的升级选项
     */
    private UpgradeOption createRandomOption() {
        int weaponChance = weapons.size() < MAX_WEAPONS ? 35 : 18;
        if (random.nextInt(100) < weaponChance) {
            Weapon.Type type = Weapon.Type.values()[random.nextInt(Weapon.Type.values().length)];
            boolean owned = hasWeapon(type);
            String title = owned ? Weapon.getName(type) + "强化" : "新武器 " + Weapon.getName(type);
            String desc = owned ? "提升这类武器的伤害和射速" : "携带一把自动攻击的新武器";
            return new UpgradeOption(title, desc, UpgradeOption.Kind.WEAPON, type);
        }

        UpgradeOption.Kind[] kinds = {
                UpgradeOption.Kind.MAX_HP,
                UpgradeOption.Kind.DAMAGE,
                UpgradeOption.Kind.ATTACK_SPEED,
                UpgradeOption.Kind.MOVE_SPEED,
                UpgradeOption.Kind.ARMOR,
                UpgradeOption.Kind.REGEN,
                UpgradeOption.Kind.PICKUP,
                UpgradeOption.Kind.CRIT,
                UpgradeOption.Kind.LUCK,
                UpgradeOption.Kind.PROJECTILE,
                UpgradeOption.Kind.PIERCING
        };
        UpgradeOption.Kind kind = kinds[random.nextInt(kinds.length)];
        switch (kind) {
            case MAX_HP:
                return new UpgradeOption("强壮体魄", "最大生命 +18，并回复生命", kind, null);
            case DAMAGE:
                return new UpgradeOption("火力校准", "所有武器伤害 +18%", kind, null);
            case ATTACK_SPEED:
                return new UpgradeOption("快手扳机", "攻击速度 +16%", kind, null);
            case MOVE_SPEED:
                return new UpgradeOption("轻装移动", "移动速度 +12%", kind, null);
            case ARMOR:
                return new UpgradeOption("装甲背心", "护甲 +1，受到的碰撞伤害降低", kind, null);
            case REGEN:
                return new UpgradeOption("应急医疗", "生命回复 +0.05/帧", kind, null);
            case PICKUP:
                return new UpgradeOption("磁力背包", "拾取范围 +1.4", kind, null);
            case CRIT:
                return new UpgradeOption("弱点瞄准", "暴击率 +8%", kind, null);
            case LUCK:
                return new UpgradeOption("幸运硬币", "幸运 +4，生命掉落更多", kind, null);
            case PROJECTILE:
                return new UpgradeOption("双发改造", "所有武器额外弹体 +1", kind, null);
            default:
                return new UpgradeOption("穿透弹头", "子弹穿透 +1", kind, null);
        }
    }

    /**
     * 执行玩家选择的升级选项。
     * <p>
     * 根据选项类型应用对应效果：武器类添加或强化武器，
     * 属性类直接修改玩家对应属性值。
     *
     * @param index 选择的升级选项索引
     */
    public void chooseUpgrade(int index) {
        if (index < 0 || index >= upgradeOptions.size()) return;
        UpgradeOption option = upgradeOptions.get(index);
        switch (option.kind) {
            case WEAPON:
                addOrImproveWeapon(option.weaponType);
                break;
            case MAX_HP:
                player.maxHp += 18;
                player.heal(28);
                break;
            case DAMAGE:
                player.damageMultiplier += 0.18f;
                break;
            case ATTACK_SPEED:
                player.attackSpeed += 0.16f;
                break;
            case MOVE_SPEED:
                player.moveSpeed += 0.10f;
                break;
            case ARMOR:
                player.armor += 1;
                break;
            case REGEN:
                player.regen += 0.05f;
                break;
            case PICKUP:
                player.pickupRange += 1.4f;
                break;
            case CRIT:
                player.critChance += 0.08f;
                break;
            case LUCK:
                player.luck += 4;
                break;
            case PROJECTILE:
                player.extraProjectiles += 1;
                break;
            case PIERCING:
                player.piercing += 1;
                break;
        }
        waitingForUpgrade = false;
        upgradeOptions.clear();
    }

    /**
     * 添加新武器或强化已有武器。
     * <p>
     * 若玩家已拥有同类型武器，则提升其等级（增加伤害、降低冷却、
     * 偶数级增加弹体数、3 的倍数级增加穿透）。
     * 若未拥有且武器槽未满，则添加新武器。
     * 若武器槽已满，则随机提升一把已有武器的等级。
     *
     * @param type 武器类型
     */
    private void addOrImproveWeapon(Weapon.Type type) {
        for (Weapon weapon : weapons) {
            if (weapon.type == type) {
                weapon.level++;
                weapon.damage += 2 + weapon.level;
                weapon.cooldown = Math.max(90, (int) (weapon.cooldown * 0.88f));
                // 偶数等级增加弹体数，上限 4
                weapon.projectiles = Math.min(4, weapon.projectiles + (weapon.level % 2 == 0 ? 1 : 0));
                // 3 的倍数等级增加穿透
                weapon.pierce += weapon.level % 3 == 0 ? 1 : 0;
                return;
            }
        }
        if (weapons.size() < MAX_WEAPONS) {
            weapons.add(Weapon.create(type));
        } else {
            // 武器槽已满时随机提升一把已有武器
            weapons.get(random.nextInt(weapons.size())).level++;
        }
    }

    /**
     * 检查玩家是否已拥有指定类型的武器。
     *
     * @param type 武器类型
     * @return 是否已拥有
     */
    private boolean hasWeapon(Weapon.Type type) {
        for (Weapon weapon : weapons) {
            if (weapon.type == type) return true;
        }
        return false;
    }

    /**
     * 计算两点之间的欧几里得距离。
     *
     * @param ax 第一个点的 X 坐标
     * @param ay 第一个点的 Y 坐标
     * @param bx 第二个点的 X 坐标
     * @param by 第二个点的 Y 坐标
     * @return 两点之间的距离
     */
    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public Player getPlayer() { return player; }
    public List<Enemy> getEnemies() { return enemies; }
    public List<Bullet> getBullets() { return bullets; }
    public List<Pickup> getPickups() { return pickups; }
    public List<Weapon> getWeapons() { return weapons; }
    public List<UpgradeOption> getUpgradeOptions() { return upgradeOptions; }
    public int getWave() { return wave; }
    public int getScore() { return score; }
    public int getGold() { return gold; }
    public int getLevel() { return level; }
    public int getExp() { return exp; }
    public int getExpToLevel() { return expToLevel; }
    public int getKills() { return kills; }
    public int getBossThreatLevel() { return bossThreatLevel; }
    public boolean isGameOver() { return gameOver; }
    public boolean isGameWon() { return gameWon; }
    public boolean isWaitingForUpgrade() { return waitingForUpgrade; }
    public long getElapsedTime() { return elapsedTime; }
    public boolean isFinalBossSpawned() { return finalBossSpawned; }

    /**
     * 生成玩家属性信息的文本摘要，用于 UI 显示。
     *
     * @return 格式化的属性文本
     */
    public String getStatsText() {
        return "生命 " + player.hp + "/" + player.maxHp
                + "  伤害 +" + Math.round((player.damageMultiplier - 1f) * 100) + "%"
                + "  攻速 +" + Math.round(player.attackSpeed * 100) + "%"
                + "\n移速 " + String.format("%.1f", player.moveSpeed)
                + "  护甲 " + player.armor
                + "  回复 " + String.format("%.2f", player.regen)
                + "  暴击 " + Math.round(player.critChance * 100) + "%"
                + "\n拾取 +" + String.format("%.1f", player.pickupRange)
                + "  穿透 +" + player.piercing
                + "  弹体 +" + player.extraProjectiles
                + "  武器 " + weapons.size() + "/" + MAX_WEAPONS;
    }

    /**
     * 生成武器列表的文本摘要，用于 UI 显示。
     *
     * @return 格式化的武器文本
     */
    public String getWeaponsText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < weapons.size(); i++) {
            Weapon weapon = weapons.get(i);
            if (i > 0) builder.append(" | ");
            builder.append(weapon.name).append(" Lv.").append(weapon.level);
        }
        return builder.toString();
    }

    /**
     * 玩家实体，包含位置、属性和状态信息。
     * <p>
     * 玩家坐标以游戏单位表示，生命回复使用"银行"机制
     * （regenBank 累积小数，满 1 时转为整数回复）。
     */
    public static class Player {
        public float x, y;
        /** 朝向角度（度数），-90 表示朝上 */
        public float angle = -90;
        public int hp = 95;
        public int maxHp = 95;
        public float moveSpeed = 0.75f;
        /** 碰撞体积半径（游戏单位） */
        public float size = 1.35f;
        /** 伤害倍率，1.0 为基础值 */
        public float damageMultiplier = 1f;
        /** 攻击速度加成（0 为无加成） */
        public float attackSpeed = 0f;
        /** 护甲值，每点减免 1 点碰撞伤害 */
        public int armor = 0;
        /** 每帧生命回复量（小数） */
        public float regen = 0.02f;
        /** 回复累积银行，满 1 时转为整数回复 */
        public float regenBank = 0f;
        /** 拾取范围加成（游戏单位） */
        public float pickupRange = 2f;
        /** 暴击概率（0~1） */
        public float critChance = 0.05f;
        /** 幸运值，影响生命拾取物掉落概率 */
        public int luck = 0;
        /** 额外弹体数，所有武器共享 */
        public int extraProjectiles = 0;
        /** 额外穿透次数，所有武器共享 */
        public int piercing = 0;
        /** 敌人生成压力减免（毫秒），降低敌人生成频率 */
        public int enemyPressureReduction = 0;

        public Player(float x, float y) {
            this.x = x;
            this.y = y;
        }

        /**
         * 通过方向增量移动玩家，移动步长受限于移动速度。
         *
         * @param dx X 方向增量
         * @param dy Y 方向增量
         */
        public void move(float dx, float dy) {
            if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) return;
            float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            float step = Math.min(moveSpeed, len * 0.035f);
            x += dx / len * step;
            y += dy / len * step;
        }

        /**
         * 通过归一化摇杆输入移动玩家。
         * <p>
         * 输入向量先归一化，再乘以移动速度和力度系数。
         * 力度系数由输入向量长度决定（最大 1.0），
         * joystickScale (0.78) 用于微调手感。
         *
         * @param inputX X 方向归一化输入
         * @param inputY Y 方向归一化输入
         */
        public void moveByInput(float inputX, float inputY) {
            float len = Math.max(0.01f, (float) Math.sqrt(inputX * inputX + inputY * inputY));
            // 输入过小时忽略，防止漂移
            if (len < 0.08f) return;
            float power = Math.min(1f, len);
            float joystickScale = 0.78f;
            x += inputX / len * moveSpeed * power * joystickScale;
            y += inputY / len * moveSpeed * power * joystickScale;
        }

        /**
         * 玩家受到伤害，直接扣减生命值。
         *
         * @param amount 伤害量
         */
        public void takeDamage(int amount) {
            hp -= amount;
        }

        /**
         * 回复生命值，不超过最大生命值上限。
         *
         * @param amount 回复量
         */
        public void heal(int amount) {
            hp = Math.min(maxHp, hp + amount);
        }

        /**
         * 每帧执行生命回复。
         * <p>
         * 使用"银行"机制将小数回复量累积为整数，
         * 避免浮点回复导致生命值显示异常。
         */
        public void regenerate() {
            regenBank += regen;
            if (regenBank >= 1f) {
                int healAmount = (int) regenBank;
                regenBank -= healAmount;
                heal(healAmount);
            }
        }

        /**
         * 判断玩家是否死亡。
         *
         * @return 生命值 <= 0 时返回 true
         */
        public boolean isDead() {
            return hp <= 0;
        }
    }

    /**
     * 敌人实体，包含位置、属性和类型信息。
     * <p>
     * 敌人类型从弱到强：GRUNT（小兵）→ RUNNER（跑者）→ BRUTE（壮汉）→
     * ELITE（精英）→ MINI_BOSS（小 Boss）→ FINAL_BOSS（终局 Boss）。
     * 属性值随波次递增，威胁等级提供额外加成。
     */
    public static class Enemy {
        /** 敌人类型枚举，按强度递增排列 */
        public enum Kind { GRUNT, RUNNER, BRUTE, ELITE, MINI_BOSS, FINAL_BOSS }

        public float x, y;
        public int hp;
        public int maxHp;
        public int damage;
        public float speed;
        /** 碰撞体积半径（游戏单位） */
        public float size;
        /** 击杀后提供的经验值 */
        public int expValue;
        /** 击杀后提供的金币数 */
        public int goldValue;
        /** 击杀后提供的分数 */
        public int scoreValue;
        /** 上次碰撞玩家的时间，用于碰撞冷却 */
        public long lastHitTime;
        public Kind kind;

        /**
         * 工厂方法：根据类型和当前波次创建敌人。
         * <p>
         * 所有属性值随波次递增，确保游戏难度持续上升。
         *
         * @param kind 敌人类型
         * @param x 出生 X 坐标
         * @param y 出生 Y 坐标
         * @param wave 当前波次
         * @return 初始化完成的敌人实例
         */
        public static Enemy create(Kind kind, float x, float y, int wave) {
            Enemy enemy = new Enemy();
            enemy.kind = kind;
            enemy.x = x;
            enemy.y = y;
            if (kind == Kind.FINAL_BOSS) {
                enemy.maxHp = 350000 + wave * 12000;
                enemy.damage = 34 + wave;
                enemy.speed = 0.068f;
                enemy.size = 3.6f;
                enemy.expValue = 200;
                enemy.goldValue = 300;
                enemy.scoreValue = 10000;
            } else if (kind == Kind.MINI_BOSS) {
                enemy.maxHp = 4200 + wave * 520;
                enemy.damage = 16 + wave / 2;
                enemy.speed = 0.085f + wave * 0.0018f;
                enemy.size = 2.25f;
                enemy.expValue = 70;
                enemy.goldValue = 60;
                enemy.scoreValue = 1200;
            } else if (kind == Kind.ELITE) {
                enemy.maxHp = 950 + wave * 130;
                enemy.damage = 9 + wave / 3;
                enemy.speed = 0.13f + wave * 0.0025f;
                enemy.size = 1.75f;
                enemy.expValue = 30;
                enemy.goldValue = 20;
                enemy.scoreValue = 360;
            } else if (kind == Kind.RUNNER) {
                enemy.maxHp = 8 + wave * 2;
                enemy.damage = 3 + wave / 4;
                enemy.speed = 0.23f + wave * 0.006f;
                enemy.size = 0.8f;
                enemy.expValue = 4;
                enemy.goldValue = 1;
                enemy.scoreValue = 18;
            } else if (kind == Kind.BRUTE) {
                enemy.maxHp = 28 + wave * 5;
                enemy.damage = 7 + wave / 3;
                enemy.speed = 0.09f + wave * 0.003f;
                enemy.size = 1.45f;
                enemy.expValue = 9;
                enemy.goldValue = 3;
                enemy.scoreValue = 40;
            } else {
                enemy.maxHp = 12 + wave * 3;
                enemy.damage = 4 + wave / 4;
                enemy.speed = 0.14f + wave * 0.004f;
                enemy.size = 1f;
                enemy.expValue = 5;
                enemy.goldValue = 1;
                enemy.scoreValue = 24;
            }
            enemy.hp = enemy.maxHp;
            return enemy;
        }

        /**
         * 应用威胁等级加成，提升敌人的生命值、伤害和速度。
         * <p>
         * 终局 Boss 不受威胁等级影响。每级威胁提供：
         * 生命 +12%、伤害 +6%、速度 +0.004（上限 0.08）。
         *
         * @param threatLevel 当前威胁等级
         */
        public void applyThreatLevel(int threatLevel) {
            if (threatLevel <= 0 || kind == Kind.FINAL_BOSS) return;
            float hpScale = 1f + threatLevel * 0.12f;
            float damageScale = 1f + threatLevel * 0.06f;
            maxHp = Math.max(1, Math.round(maxHp * hpScale));
            hp = maxHp;
            damage = Math.max(1, Math.round(damage * damageScale));
            speed += Math.min(0.08f, threatLevel * 0.004f);
        }

        /**
         * 更新敌人位置，向玩家直线移动。
         *
         * @param player 玩家对象，用于计算移动方向
         */
        public void update(Player player) {
            float dx = player.x - x;
            float dy = player.y - y;
            float dist = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            x += dx / dist * speed;
            y += dy / dist * speed;
        }

        /**
         * 敌人受到伤害。
         *
         * @param amount 伤害量
         */
        public void takeDamage(int amount) {
            hp -= amount;
        }

        /** 敌人是否存活（生命值 > 0） */
        public boolean isAlive() {
            return hp > 0;
        }

        /** 敌人是否死亡（生命值 <= 0） */
        public boolean isDead() {
            return hp <= 0;
        }

        /**
         * 检测敌人是否与玩家发生碰撞（圆形碰撞检测）。
         *
         * @param player 玩家对象
         * @return 碰撞时返回 true
         */
        public boolean collidesWith(Player player) {
            return distance(x, y, player.x, player.y) < size + player.size;
        }

        /**
         * 判断是否为 Boss 级敌人（精英、小 Boss 或终局 Boss）。
         *
         * @return 是 Boss 级敌人返回 true
         */
        public boolean isBoss() {
            return kind == Kind.ELITE || kind == Kind.MINI_BOSS || kind == Kind.FINAL_BOSS;
        }
    }

    /**
     * 子弹实体，由武器发射，沿直线飞行。
     * <p>
     * 子弹具有穿透属性（pierceLeft），穿透耗尽后失效。
     * 暴击时伤害倍率为 1.8x。
     */
    public static class Bullet {
        public float x, y;
        /** X 方向速度分量 */
        public float vx;
        /** Y 方向速度分量 */
        public float vy;
        /** 伤害值（已应用暴击和伤害倍率） */
        public int damage;
        /** 子弹是否仍然有效 */
        public boolean active = true;
        /** 剩余穿透次数，-1 表示子弹失效 */
        public int pierceLeft;
        /** 碰撞体积半径 */
        public float size;
        /** 子弹颜色 */
        public int color;

        /**
         * 创建一颗子弹。
         * <p>
         * 根据暴击概率决定是否暴击（1.8x 伤害），
         * 伤害值 = 武器基础伤害 × 玩家伤害倍率 × 暴击倍率，最低 1。
         *
         * @param x 发射位置 X
         * @param y 发射位置 Y
         * @param angle 发射角度（弧度）
         * @param weapon 发射武器
         * @param player 玩家对象（用于获取属性加成）
         */
        public Bullet(float x, float y, float angle, Weapon weapon, Player player) {
            this.x = x;
            this.y = y;
            float crit = Math.random() < player.critChance ? 1.8f : 1f;
            this.damage = Math.max(1, Math.round(weapon.damage * player.damageMultiplier * crit));
            this.pierceLeft = weapon.pierce + player.piercing;
            this.size = weapon.bulletSize;
            this.color = weapon.color;
            this.vx = (float) Math.cos(angle) * weapon.bulletSpeed;
            this.vy = (float) Math.sin(angle) * weapon.bulletSpeed;
        }

        /**
         * 更新子弹位置（直线飞行）。
         */
        public void update() {
            x += vx;
            y += vy;
        }

        /**
         * 判断子弹是否远离玩家（超过 58 个游戏单位）。
         *
         * @param player 玩家对象
         * @return 超出范围返回 true
         */
        public boolean isFarFrom(Player player) {
            return distance(x, y, player.x, player.y) > 58f;
        }

        /**
         * 检测子弹是否与敌人碰撞（圆形碰撞检测）。
         *
         * @param enemy 敌人对象
         * @return 碰撞时返回 true
         */
        public boolean collidesWith(Enemy enemy) {
            return distance(x, y, enemy.x, enemy.y) < size + enemy.size;
        }
    }

    /**
     * 武器实体，定义武器的属性和射击参数。
     * <p>
     * 武器类型：手枪（PISTOL）、霰弹枪（SHOTGUN）、冲锋枪（SMG）、
     * 步枪（RIFLE）、激光枪（LASER）、火箭筒（ROCKET）。
     * 每种武器有不同的伤害、射速、射程、弹体数和穿透属性。
     */
    public static class Weapon {
        /** 武器类型枚举 */
        public enum Type { PISTOL, SHOTGUN, SMG, RIFLE, LASER, ROCKET }

        public Type type;
        /** 武器显示名称 */
        public String name;
        /** 武器等级，强化时递增 */
        public int level = 1;
        public int damage;
        /** 基础射击冷却时间（毫秒） */
        public int cooldown;
        /** 射程（游戏单位） */
        public float range;
        /** 子弹飞行速度 */
        public float bulletSpeed;
        /** 子弹体积半径 */
        public float bulletSize;
        /** 每次射击的弹体数量 */
        public int projectiles;
        /** 基础穿透次数 */
        public int pierce;
        /** 武器主题颜色 */
        public int color;
        /** 上次射击时间戳 */
        public long lastShotTime;

        /**
         * 工厂方法：根据类型创建武器并初始化属性。
         *
         * @param type 武器类型
         * @return 初始化完成的武器实例
         */
        public static Weapon create(Type type) {
            Weapon weapon = new Weapon();
            weapon.type = type;
            weapon.name = getName(type);
            if (type == Type.SHOTGUN) {
                weapon.damage = 7;
                weapon.cooldown = 760;
                weapon.range = 22;
                weapon.bulletSpeed = 1.35f;
                weapon.bulletSize = 0.35f;
                weapon.projectiles = 4;
                weapon.pierce = 0;
                weapon.color = 0xFFFFB347;
            } else if (type == Type.SMG) {
                weapon.damage = 4;
                weapon.cooldown = 170;
                weapon.range = 18;
                weapon.bulletSpeed = 1.6f;
                weapon.bulletSize = 0.24f;
                weapon.projectiles = 1;
                weapon.pierce = 0;
                weapon.color = 0xFF9FE6FF;
            } else if (type == Type.RIFLE) {
                weapon.damage = 14;
                weapon.cooldown = 520;
                weapon.range = 31;
                weapon.bulletSpeed = 2.05f;
                weapon.bulletSize = 0.3f;
                weapon.projectiles = 1;
                weapon.pierce = 1;
                weapon.color = 0xFFFFF176;
            } else if (type == Type.LASER) {
                weapon.damage = 10;
                weapon.cooldown = 390;
                weapon.range = 28;
                weapon.bulletSpeed = 2.25f;
                weapon.bulletSize = 0.2f;
                weapon.projectiles = 1;
                weapon.pierce = 2;
                weapon.color = 0xFFFF79C6;
            } else if (type == Type.ROCKET) {
                weapon.damage = 26;
                weapon.cooldown = 980;
                weapon.range = 26;
                weapon.bulletSpeed = 1.05f;
                weapon.bulletSize = 0.55f;
                weapon.projectiles = 1;
                weapon.pierce = 0;
                weapon.color = 0xFFFF5555;
            } else {
                weapon.damage = 9;
                weapon.cooldown = 430;
                weapon.range = 24;
                weapon.bulletSpeed = 1.75f;
                weapon.bulletSize = 0.28f;
                weapon.projectiles = 1;
                weapon.pierce = 0;
                weapon.color = 0xFFECEFF1;
            }
            return weapon;
        }

        /**
         * 计算考虑攻速加成后的实际射击延迟。
         * <p>
         * 实际延迟 = 基础冷却 / (1 + 攻速加成)，最低 55ms。
         *
         * @param player 玩家对象（用于获取攻速加成）
         * @return 实际射击延迟（毫秒）
         */
        public int getFireDelay(Player player) {
            return Math.max(55, Math.round(cooldown / (1f + player.attackSpeed)));
        }

        /**
         * 获取武器类型对应的中文名称。
         *
         * @param type 武器类型
         * @return 中文名称
         */
        public static String getName(Type type) {
            switch (type) {
                case SHOTGUN:
                    return "霰弹枪";
                case SMG:
                    return "冲锋枪";
                case RIFLE:
                    return "步枪";
                case LASER:
                    return "激光枪";
                case ROCKET:
                    return "火箭筒";
                default:
                    return "手枪";
            }
        }
    }

    /**
     * 拾取物实体，敌人掉落或地图定时生成。
     * <p>
     * 类型：GOLD（金币）、EXP（经验）、HP（生命）、MEDKIT（医疗包）、MAGNET（磁铁）。
     */
    public static class Pickup {
        /** 拾取物类型枚举 */
        public enum Type { GOLD, EXP, HP, MEDKIT, MAGNET }

        public float x, y;
        public Type type;
        /** 拾取物价值（经验值或回复量） */
        public int value;

        /**
         * 创建拾取物。
         *
         * @param x X 坐标
         * @param y Y 坐标
         * @param type 拾取物类型
         * @param value 价值
         */
        public Pickup(float x, float y, Type type, int value) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.value = value;
        }

        /**
         * 判断拾取物是否被玩家收集（接触检测）。
         * <p>
         * 收集距离 = 玩家体积 + 0.9 个游戏单位。
         *
         * @param player 玩家对象
         * @return 被收集返回 true
         */
        public boolean isCollected(Player player) {
            return distance(x, y, player.x, player.y) <= player.size + 0.9f;
        }
    }

    /**
     * 升级选项，玩家升级时从三个选项中选择一个。
     * <p>
     * 类型包括：武器强化/新增和 11 种属性强化。
     */
    public static class UpgradeOption {
        /** 升级类型枚举 */
        public enum Kind {
            WEAPON, MAX_HP, DAMAGE, ATTACK_SPEED, MOVE_SPEED, ARMOR, REGEN,
            PICKUP, CRIT, LUCK, PROJECTILE, PIERCING
        }

        /** 选项标题 */
        public String title;
        /** 选项描述 */
        public String desc;
        /** 升级类型 */
        public Kind kind;
        /** 武器类型（仅 WEAPON 类型时有效） */
        public Weapon.Type weaponType;

        /**
         * 创建升级选项。
         *
         * @param title 标题
         * @param desc 描述
         * @param kind 升级类型
         * @param weaponType 武器类型（非武器升级时为 null）
         */
        public UpgradeOption(String title, String desc, Kind kind, Weapon.Type weaponType) {
            this.title = title;
            this.desc = desc;
            this.kind = kind;
            this.weaponType = weaponType;
        }
    }
}
