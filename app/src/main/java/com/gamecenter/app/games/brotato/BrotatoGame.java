package com.gamecenter.app.games.brotato;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BrotatoGame {

    public static final int BOARD_WIDTH = 40;
    public static final int BOARD_HEIGHT = 60;
    public static final int MAX_WEAPONS = 6;
    public static final long ELITE_INTERVAL = 30000L;
    public static final long MINI_BOSS_INTERVAL = 50000L;
    public static final long FINAL_BOSS_TIME = 600000L;
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

    public BrotatoGame() {
        reset();
    }

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

    public void update(long currentTime) {
        if (gameOver || waitingForUpgrade) return;
        if (startedAt == 0) {
            startedAt = currentTime;
            nextWaveAt = currentTime + 25000;
            nextEliteAt = currentTime + ELITE_INTERVAL;
            nextMiniBossAt = currentTime + MINI_BOSS_INTERVAL;
            nextMapPickupAt = currentTime + 15000L;
        }
        elapsedTime = currentTime - startedAt;

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

    private void spawnTimedThreats(long currentTime) {
        if (!finalBossSpawned && elapsedTime >= FINAL_BOSS_TIME) {
            spawnEnemyAtEdge(Enemy.Kind.FINAL_BOSS);
            bossThreatLevel += 4;
            finalBossSpawned = true;
            nextEliteAt = currentTime + ELITE_INTERVAL;
            nextMiniBossAt = currentTime + MINI_BOSS_INTERVAL;
            return;
        }

        if (currentTime >= nextMiniBossAt) {
            spawnEnemyAtEdge(Enemy.Kind.MINI_BOSS);
            bossThreatLevel += 2;
            nextMiniBossAt += MINI_BOSS_INTERVAL;
            if (Math.abs(nextEliteAt - currentTime) < 5000L) {
                nextEliteAt = currentTime + 8000L;
            }
        }

        if (currentTime >= nextEliteAt) {
            if (Math.abs(nextMiniBossAt - currentTime) > 5000L) {
                spawnEnemyAtEdge(Enemy.Kind.ELITE);
                bossThreatLevel += 1;
                nextEliteAt += ELITE_INTERVAL;
            } else {
                nextEliteAt += 8000L;
            }
        }
    }

    private void spawnMapPickup(long currentTime) {
        if (currentTime < nextMapPickupAt) return;

        Pickup.Type type = random.nextInt(100) < 55 ? Pickup.Type.MEDKIT : Pickup.Type.MAGNET;
        float angle = random.nextFloat() * (float) Math.PI * 2f;
        float radius = 13f + random.nextFloat() * 12f;
        float x = player.x + (float) Math.cos(angle) * radius;
        float y = player.y + (float) Math.sin(angle) * radius;
        pickups.add(new Pickup(x, y, type, 1));
        nextMapPickupAt = currentTime + MAP_PICKUP_INTERVAL + random.nextInt(7000) - 3500;
    }

    private void spawnEnemies(long currentTime) {
        long spawnDelay = Math.max(220, 1250 - wave * 65L - bossThreatLevel * 45L - player.enemyPressureReduction);
        if (currentTime - lastSpawnTime < spawnDelay) return;

        int count = 1 + Math.min(2, wave / 4) + Math.min(2, bossThreatLevel / 4);
        for (int i = 0; i < count; i++) {
            Enemy.Kind kind = rollEnemyKind();
            spawnEnemyAtEdge(kind);
        }
        lastSpawnTime = currentTime;
    }

    private void spawnEnemyAtEdge(Enemy.Kind kind) {
        float angle = random.nextFloat() * (float) Math.PI * 2f;
        float radius = kind == Enemy.Kind.FINAL_BOSS ? 34f : 28f + random.nextFloat() * 8f;
        float x = player.x + (float) Math.cos(angle) * radius;
        float y = player.y + (float) Math.sin(angle) * radius;
        Enemy enemy = Enemy.create(kind, x, y, wave);
        enemy.applyThreatLevel(bossThreatLevel);
        enemies.add(enemy);
    }

    private Enemy.Kind rollEnemyKind() {
        int roll = random.nextInt(100);
        if (wave >= 5 && roll > 88) return Enemy.Kind.BRUTE;
        if (wave >= 3 && roll > 72) return Enemy.Kind.RUNNER;
        return Enemy.Kind.GRUNT;
    }

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

    private void fireWeapon(Weapon weapon, Enemy target, long currentTime) {
        float angle = (float) Math.atan2(target.y - player.y, target.x - player.x);
        int shots = weapon.projectiles + player.extraProjectiles;
        float spread = shots <= 1 ? 0f : 0.16f;
        for (int i = 0; i < shots; i++) {
            float offset = (i - (shots - 1) / 2f) * spread;
            bullets.add(new Bullet(player.x, player.y, angle + offset, weapon, player));
        }
        weapon.lastShotTime = currentTime;
        player.angle = (float) Math.toDegrees(angle);
    }

    private void updateBullets() {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            bullet.update();
            if (!bullet.active || bullet.isFarFrom(player)) {
                bullets.remove(i);
            }
        }
    }

    private void updateEnemies() {
        for (Enemy enemy : enemies) {
            enemy.update(player);
        }
    }

    private void collectPickups() {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pickup = pickups.get(i);
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
                    player.heal(Math.round(player.maxHp * 0.8f));
                } else if (pickup.type == Pickup.Type.MAGNET) {
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

    private void handleCollisions(long currentTime) {
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

        for (Enemy enemy : enemies) {
            if (enemy.isAlive() && enemy.collidesWith(player) && currentTime - enemy.lastHitTime >= 650) {
                player.takeDamage(Math.max(1, enemy.damage - player.armor));
                enemy.lastHitTime = currentTime;
                if (player.isDead()) {
                    gameOver = true;
                    waitingForUpgrade = false;
                }
            }
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDead()) {
                enemies.remove(i);
            } else if (!enemy.isBoss() && distance(enemy.x, enemy.y, player.x, player.y) > 76f) {
                enemies.remove(i);
            }
        }
    }

    private void onEnemyKilled(Enemy enemy) {
        kills++;
        score += enemy.scoreValue;
        gold += enemy.goldValue;
        if (enemy.kind == Enemy.Kind.FINAL_BOSS) {
            gameWon = true;
            gameOver = true;
            waitingForUpgrade = false;
        }
        pickups.add(new Pickup(enemy.x, enemy.y, Pickup.Type.EXP, enemy.expValue));
        if (random.nextInt(100) < 38) {
            pickups.add(new Pickup(enemy.x + randomOffset(), enemy.y + randomOffset(), Pickup.Type.GOLD, 1));
        }
        if (random.nextInt(100) < 6 + player.luck) {
            pickups.add(new Pickup(enemy.x + randomOffset(), enemy.y + randomOffset(), Pickup.Type.HP, 5 + level));
        }
    }

    private float randomOffset() {
        return -0.8f + random.nextFloat() * 1.6f;
    }

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

    public void movePlayer(float dx, float dy) {
        if (waitingForUpgrade || gameOver) return;
        player.move(dx, dy);
    }

    public void movePlayerInput(float inputX, float inputY) {
        if (waitingForUpgrade || gameOver) return;
        player.moveByInput(inputX, inputY);
    }

    public void rollUpgradeOptions() {
        upgradeOptions.clear();
        while (upgradeOptions.size() < 3) {
            UpgradeOption option = createRandomOption();
            if (!containsOption(option.title)) {
                upgradeOptions.add(option);
            }
        }
    }

    private boolean containsOption(String title) {
        for (UpgradeOption option : upgradeOptions) {
            if (option.title.equals(title)) return true;
        }
        return false;
    }

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

    private void addOrImproveWeapon(Weapon.Type type) {
        for (Weapon weapon : weapons) {
            if (weapon.type == type) {
                weapon.level++;
                weapon.damage += 2 + weapon.level;
                weapon.cooldown = Math.max(90, (int) (weapon.cooldown * 0.88f));
                weapon.projectiles = Math.min(4, weapon.projectiles + (weapon.level % 2 == 0 ? 1 : 0));
                weapon.pierce += weapon.level % 3 == 0 ? 1 : 0;
                return;
            }
        }
        if (weapons.size() < MAX_WEAPONS) {
            weapons.add(Weapon.create(type));
        } else {
            weapons.get(random.nextInt(weapons.size())).level++;
        }
    }

    private boolean hasWeapon(Weapon.Type type) {
        for (Weapon weapon : weapons) {
            if (weapon.type == type) return true;
        }
        return false;
    }

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

    public String getWeaponsText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < weapons.size(); i++) {
            Weapon weapon = weapons.get(i);
            if (i > 0) builder.append(" | ");
            builder.append(weapon.name).append(" Lv.").append(weapon.level);
        }
        return builder.toString();
    }

    public static class Player {
        public float x, y;
        public float angle = -90;
        public int hp = 95;
        public int maxHp = 95;
        public float moveSpeed = 0.75f;
        public float size = 1.35f;
        public float damageMultiplier = 1f;
        public float attackSpeed = 0f;
        public int armor = 0;
        public float regen = 0.02f;
        public float regenBank = 0f;
        public float pickupRange = 2f;
        public float critChance = 0.05f;
        public int luck = 0;
        public int extraProjectiles = 0;
        public int piercing = 0;
        public int enemyPressureReduction = 0;

        public Player(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void move(float dx, float dy) {
            if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) return;
            float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            float step = Math.min(moveSpeed, len * 0.035f);
            x += dx / len * step;
            y += dy / len * step;
        }

        public void moveByInput(float inputX, float inputY) {
            float len = Math.max(0.01f, (float) Math.sqrt(inputX * inputX + inputY * inputY));
            if (len < 0.08f) return;
            float power = Math.min(1f, len);
            float joystickScale = 0.78f;
            x += inputX / len * moveSpeed * power * joystickScale;
            y += inputY / len * moveSpeed * power * joystickScale;
        }

        public void takeDamage(int amount) {
            hp -= amount;
        }

        public void heal(int amount) {
            hp = Math.min(maxHp, hp + amount);
        }

        public void regenerate() {
            regenBank += regen;
            if (regenBank >= 1f) {
                int healAmount = (int) regenBank;
                regenBank -= healAmount;
                heal(healAmount);
            }
        }

        public boolean isDead() {
            return hp <= 0;
        }
    }

    public static class Enemy {
        public enum Kind { GRUNT, RUNNER, BRUTE, ELITE, MINI_BOSS, FINAL_BOSS }

        public float x, y;
        public int hp;
        public int maxHp;
        public int damage;
        public float speed;
        public float size;
        public int expValue;
        public int goldValue;
        public int scoreValue;
        public long lastHitTime;
        public Kind kind;

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

        public void applyThreatLevel(int threatLevel) {
            if (threatLevel <= 0 || kind == Kind.FINAL_BOSS) return;
            float hpScale = 1f + threatLevel * 0.12f;
            float damageScale = 1f + threatLevel * 0.06f;
            maxHp = Math.max(1, Math.round(maxHp * hpScale));
            hp = maxHp;
            damage = Math.max(1, Math.round(damage * damageScale));
            speed += Math.min(0.08f, threatLevel * 0.004f);
        }

        public void update(Player player) {
            float dx = player.x - x;
            float dy = player.y - y;
            float dist = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            x += dx / dist * speed;
            y += dy / dist * speed;
        }

        public void takeDamage(int amount) {
            hp -= amount;
        }

        public boolean isAlive() {
            return hp > 0;
        }

        public boolean isDead() {
            return hp <= 0;
        }

        public boolean collidesWith(Player player) {
            return distance(x, y, player.x, player.y) < size + player.size;
        }

        public boolean isBoss() {
            return kind == Kind.ELITE || kind == Kind.MINI_BOSS || kind == Kind.FINAL_BOSS;
        }
    }

    public static class Bullet {
        public float x, y;
        public float vx, vy;
        public int damage;
        public boolean active = true;
        public int pierceLeft;
        public float size;
        public int color;

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

        public void update() {
            x += vx;
            y += vy;
        }

        public boolean isFarFrom(Player player) {
            return distance(x, y, player.x, player.y) > 58f;
        }

        public boolean collidesWith(Enemy enemy) {
            return distance(x, y, enemy.x, enemy.y) < size + enemy.size;
        }
    }

    public static class Weapon {
        public enum Type { PISTOL, SHOTGUN, SMG, RIFLE, LASER, ROCKET }

        public Type type;
        public String name;
        public int level = 1;
        public int damage;
        public int cooldown;
        public float range;
        public float bulletSpeed;
        public float bulletSize;
        public int projectiles;
        public int pierce;
        public int color;
        public long lastShotTime;

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

        public int getFireDelay(Player player) {
            return Math.max(55, Math.round(cooldown / (1f + player.attackSpeed)));
        }

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

    public static class Pickup {
        public enum Type { GOLD, EXP, HP, MEDKIT, MAGNET }

        public float x, y;
        public Type type;
        public int value;

        public Pickup(float x, float y, Type type, int value) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.value = value;
        }

        public boolean isCollected(Player player) {
            return distance(x, y, player.x, player.y) <= player.size + 0.9f;
        }
    }

    public static class UpgradeOption {
        public enum Kind {
            WEAPON, MAX_HP, DAMAGE, ATTACK_SPEED, MOVE_SPEED, ARMOR, REGEN,
            PICKUP, CRIT, LUCK, PROJECTILE, PIERCING
        }

        public String title;
        public String desc;
        public Kind kind;
        public Weapon.Type weaponType;

        public UpgradeOption(String title, String desc, Kind kind, Weapon.Type weaponType) {
            this.title = title;
            this.desc = desc;
            this.kind = kind;
            this.weaponType = weaponType;
        }
    }
}
