package com.gamecenter.app.brotato;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 《蛋壳特工队》（Survivor.io）经典主动技能、被动补给与超级武器（EVO）体系。
 * 遵循核心法则：主动武器达到 5星满级 + 对应被动补给达到 1星以上 = 触发超武进化！
 */
public final class SurvivorSkill {

    /** 技能类型：主动武器 或 被动补给 */
    public enum Category {
        ACTIVE_WEAPON,
        PASSIVE_SUPPLY
    }

    /** 技能定义 */
    public enum Type {
        // ==== 主动武器 (Active Weapons) ====
        KUNAI("kunai", "苦无", "投掷高速手里剑，自动追踪最近敌人", Category.ACTIVE_WEAPON, "🗡️",
                "shuriken", "幽灵手里剑", "零冷却无限极速追踪连射，单体融化神器", "⭐"),
        BASEBALL_BAT("baseball_bat", "棒球棍", "前方大范围重型击退挥击", Category.ACTIVE_WEAPON, "🏏",
                "lucille", "暴力美学", "360° 全身粉碎冲击波，尸潮绝对克星", "💫"),
        SHOTGUN("shotgun", "霰弹枪", "向前喷射扇形多重穿透弹片", Category.ACTIVE_WEAPON, "💥",
                "gatling", "加特林", "极速激光直线贯穿弹幕，强力扫射清屏", "🔥"),
        GUARDIAN("guardian", "守卫者", "召唤多枚高速旋转飞盘环绕自身", Category.ACTIVE_WEAPON, "🥏",
                "defender", "捍卫者", "永久旋转在场无缝转动，绝对击退并格挡子弹", "🛡️"),
        FORCEFIELD("forcefield", "力场发生器", "产生等离子微光领域，减速并伤害靠近敌人", Category.ACTIVE_WEAPON, "🌐",
                "max_forcefield", "压制领域", "深紫高能超大范围强力压制，减速伤害翻倍", "🔮"),
        SOCCER_BALL("soccer_ball", "弹力足球", "发射高动能足球在边缘高速反弹", Category.ACTIVE_WEAPON, "⚽",
                "quantum_balls", "量子球", "全场分裂疯狂几何折射，清怪与清屏爆发", "⚛️"),
        ROCKET("rocket", "火箭发射器", "发射高爆火箭弹，命中产生大范围爆炸", Category.ACTIVE_WEAPON, "🚀",
                "sharkmaw", "鲨嘴炮", "巨型鲨鱼核弹，全屏震颤巨额爆破", "🦈"),
        LIGHTNING("lightning", "雷电发射器", "召唤落雷轰击随机目标", Category.ACTIVE_WEAPON, "⚡",
                "supercell", "狂雷电池", "狂暴无缝连锁雷暴雨，群体高伤融怪", "🌩️"),
        DRONE_A("drone_a", "A型无人机", "悬浮战术无人机，高频机枪扫射", Category.ACTIVE_WEAPON, "🤖",
                "destroyer", "毁灭者", "与B机合二为一，全屏超凡轰炸，腾出主动槽", "👾"),
        DRONE_B("drone_b", "B型无人机", "悬浮战术无人机，散射微型导弹", Category.ACTIVE_WEAPON, "🛰️",
                "destroyer", "毁灭者", "与A机合二为一，全屏超凡轰炸，腾出主动槽", "👾"),
        DRILL_SHOT("drill_shot", "钻头弹", "发射穿透大钻头在屏幕内弹射", Category.ACTIVE_WEAPON, "🔩",
                "whistler", "哨箭", "化作超音速巡航飞箭全图无限制收割", "🏹"),

        // ==== 被动补给 (Passive Supplies) ====
        KOGA_SCROLL("koga_scroll", "甲贺忍法帖", "经验获取量 +15%/阶", Category.PASSIVE_SUPPLY, "📜",
                null, null, null, null),
        FITNESS_GUIDE("fitness_guide", "健身指南", "特工生命上限 +20%/阶", Category.PASSIVE_SUPPLY, "📖",
                null, null, null, null),
        ENERGY_BULLET("energy_bullet", "能量弹头", "全武器攻击伤害 +10%/阶", Category.PASSIVE_SUPPLY, "🔋",
                null, null, null, null),
        EXO_BRACER("exo_bracer", "外骨骼装甲", "技能持续时间 +20%/阶", Category.PASSIVE_SUPPLY, "🦾",
                null, null, null, null),
        ENERGY_DRINK("energy_drink", "功能饮料", "每5秒回复生命 +2点/阶", Category.PASSIVE_SUPPLY, "🥤",
                null, null, null, null),
        SPORTS_SHOES("sports_shoes", "跑鞋", "移动速度 +10%/阶", Category.PASSIVE_SUPPLY, "👟",
                null, null, null, null),
        HE_FUEL("he_fuel", "高爆燃料", "攻击范围与弹药体积 +15%/阶", Category.PASSIVE_SUPPLY, "⛽",
                null, null, null, null),
        ENERGY_CUBE("energy_cube", "能量块", "全技能冷却时间 -10%/阶", Category.PASSIVE_SUPPLY, "🧊",
                null, null, null, null),
        AMMO_THRUSTER("ammo_thruster", "弹药推进器", "子弹飞行速度 +20%/阶", Category.PASSIVE_SUPPLY, "💨",
                null, null, null, null),
        MAGNET_SUPPLY("magnet_supply", "强力磁铁", "晶石磁吸范围 +35%/阶", Category.PASSIVE_SUPPLY, "🧲",
                null, null, null, null);

        public final String id;
        public final String nameCn;
        public final String descCn;
        public final Category category;
        public final String icon;
        public final String evoId;
        public final String evoName;
        public final String evoDesc;
        public final String evoIcon;

        Type(String id, String nameCn, String descCn, Category category, String icon,
             String evoId, String evoName, String evoDesc, String evoIcon) {
            this.id = id;
            this.nameCn = nameCn;
            this.descCn = descCn;
            this.category = category;
            this.icon = icon;
            this.evoId = evoId;
            this.evoName = evoName;
            this.evoDesc = evoDesc;
            this.evoIcon = evoIcon;
        }

        public boolean isEvoCapable() {
            return evoId != null;
        }
    }

    /** 超武合成配方规则 */
    public static class EvoRule {
        public final Type activeA;
        public final Type passiveRequired; // 若为无人机组合，可为另一架无人机
        public final String evoTitle;
        public final String evoDescription;

        public EvoRule(Type activeA, Type passiveRequired, String evoTitle, String evoDescription) {
            this.activeA = activeA;
            this.passiveRequired = passiveRequired;
            this.evoTitle = evoTitle;
            this.evoDescription = evoDescription;
        }
    }

    public static final List<EvoRule> EVO_RULES;

    static {
        List<EvoRule> list = new ArrayList<>();
        list.add(new EvoRule(Type.KUNAI, Type.KOGA_SCROLL, "幽灵手里剑", "零冷却无限高速自动追踪"));
        list.add(new EvoRule(Type.BASEBALL_BAT, Type.FITNESS_GUIDE, "暴力美学", "360° 全身粉碎冲击波"));
        list.add(new EvoRule(Type.SHOTGUN, Type.ENERGY_BULLET, "加特林", "极速穿透直线激光弹幕"));
        list.add(new EvoRule(Type.GUARDIAN, Type.EXO_BRACER, "捍卫者", "永久旋转击退并格挡子弹"));
        list.add(new EvoRule(Type.FORCEFIELD, Type.ENERGY_DRINK, "压制领域", "超大范围减速翻倍压制"));
        list.add(new EvoRule(Type.SOCCER_BALL, Type.SPORTS_SHOES, "量子球", "全场分裂高速几何折射"));
        list.add(new EvoRule(Type.ROCKET, Type.HE_FUEL, "鲨嘴炮", "巨型鲨鱼核弹全屏爆破"));
        list.add(new EvoRule(Type.LIGHTNING, Type.ENERGY_CUBE, "狂雷电池", "无缝连续链状落雷风暴"));
        list.add(new EvoRule(Type.DRILL_SHOT, Type.AMMO_THRUSTER, "哨箭", "超音速巡航飞箭穿透收割"));
        list.add(new EvoRule(Type.DRONE_A, Type.DRONE_B, "毁灭者", "双机合体超凡狂轰滥炸"));
        EVO_RULES = Collections.unmodifiableList(list);
    }

    /** 获取某个主动武器对应的合成需求被动 */
    public static Type getRequiredPair(Type active) {
        if (active == null) return null;
        for (EvoRule rule : EVO_RULES) {
            if (rule.activeA == active) {
                return rule.passiveRequired;
            }
        }
        return null;
    }

    /** 获取某个被动补给被哪些主动武器所需求 */
    public static List<Type> getActivesNeedingPassive(Type passive) {
        List<Type> result = new ArrayList<>();
        if (passive == null) return result;
        for (EvoRule rule : EVO_RULES) {
            if (rule.passiveRequired == passive) {
                result.add(rule.activeA);
            }
        }
        return result;
    }

    /**
     * 特工技能装备栏状态机（局内持有的主动武器、被动补给、星级与超武状态）
     */
    public static class SkillInventory {
        public static final int MAX_ACTIVE_SLOTS = 6;
        public static final int MAX_PASSIVE_SLOTS = 6;

        private final java.util.Map<Type, Integer> levels = new java.util.LinkedHashMap<>();
        private final java.util.Set<Type> evoSkills = new java.util.HashSet<>();
        private final java.util.Random random = new java.util.Random();

        public SkillInventory() {
            reset();
        }

        public void reset() {
            levels.clear();
            evoSkills.clear();
            // 开局标配 1 级苦无
            levels.put(Type.KUNAI, 1);
        }

        public int getLevel(Type type) {
            return levels.getOrDefault(type, 0);
        }

        public boolean isEvolved(Type type) {
            return evoSkills.contains(type);
        }

        public int getActiveCount() {
            int count = 0;
            for (Type t : levels.keySet()) {
                if (t.category == Category.ACTIVE_WEAPON && levels.get(t) > 0) count++;
            }
            return count;
        }

        public int getPassiveCount() {
            int count = 0;
            for (Type t : levels.keySet()) {
                if (t.category == Category.PASSIVE_SUPPLY && levels.get(t) > 0) count++;
            }
            return count;
        }

        public java.util.Map<Type, Integer> getAllSkills() {
            return Collections.unmodifiableMap(levels);
        }

        /** 判断主动武器是否已达成超武进化条件 */
        public boolean canEvo(Type active) {
            if (active == null || !active.isEvoCapable()) return false;
            if (evoSkills.contains(active)) return false;
            if (getLevel(active) < 5) return false;

            Type req = getRequiredPair(active);
            if (req == null) return false;

            if (active == Type.DRONE_A || active == Type.DRONE_B) {
                return getLevel(req) >= 5;
            } else {
                return getLevel(req) >= 1;
            }
        }

        /** 判断技能是否可升级或进化 */
        public boolean canUpgrade(Type type) {
            if (type == null) return false;
            if (evoSkills.contains(type)) return false;
            if (canEvo(type)) return true;

            int cur = getLevel(type);
            if (cur >= 5) return false;
            if (cur == 0) {
                if (type.category == Category.ACTIVE_WEAPON && getActiveCount() >= MAX_ACTIVE_SLOTS) {
                    return false;
                }
                if (type.category == Category.PASSIVE_SUPPLY && getPassiveCount() >= MAX_PASSIVE_SLOTS) {
                    return false;
                }
            }
            return true;
        }

        /** 应用升级或超武进化 */
        public boolean applyUpgrade(Type type) {
            if (!canUpgrade(type)) return false;
            if (canEvo(type)) {
                evoSkills.add(type);
                if (type == Type.DRONE_A || type == Type.DRONE_B) {
                    evoSkills.add(Type.DRONE_A);
                    evoSkills.add(Type.DRONE_B);
                }
                return true;
            }
            int cur = getLevel(type);
            levels.put(type, cur + 1);
            return true;
        }

        /** 生成 3 张升级卡片候选项 */
        public List<Type> rollChoices(int count) {
            List<Type> candidates = new ArrayList<>();
            // 1. 优先检查是否有可进化的超武
            for (Type t : Type.values()) {
                if (canEvo(t)) {
                    candidates.add(t);
                }
            }

            // 2. 检查已持有且可升级的技能
            for (Type t : levels.keySet()) {
                if (!candidates.contains(t) && canUpgrade(t)) {
                    candidates.add(t);
                }
            }

            // 3. 检查未持有且槽位未满的技能
            List<Type> freshPool = new ArrayList<>();
            for (Type t : Type.values()) {
                if (!levels.containsKey(t) && canUpgrade(t)) {
                    freshPool.add(t);
                }
            }
            Collections.shuffle(freshPool, random);
            candidates.addAll(freshPool);

            // 4. 打乱并截取指定数量
            List<Type> result = new ArrayList<>();
            for (Type c : candidates) {
                if (!result.contains(c)) {
                    result.add(c);
                    if (result.size() >= count) break;
                }
            }
            return result;
        }

        /** 幸运宝箱奖励：若有超武条件必定优先进化超武，否则升级已有技能 */
        public List<Type> rollLuckyChestRewards(int rolls) {
            List<Type> result = new ArrayList<>();
            // 若有可进化的超武，保底首发超武进化！
            for (Type t : Type.values()) {
                if (canEvo(t)) {
                    result.add(t);
                    applyUpgrade(t);
                    break;
                }
            }

            // 填充剩余抽数
            while (result.size() < rolls) {
                List<Type> choices = rollChoices(3);
                if (choices.isEmpty()) break;
                Type picked = choices.get(0);
                result.add(picked);
                applyUpgrade(picked);
            }
            return result;
        }

        // ==================== 属性增益换算 ====================
        public float getDamageMultiplier() {
            int lvl = getLevel(Type.ENERGY_BULLET);
            return 1.0f + lvl * 0.10f; // 能量弹头：每级 +10% 伤害
        }

        public float getSpeedMultiplier() {
            int lvl = getLevel(Type.SPORTS_SHOES);
            return 1.0f + lvl * 0.10f; // 跑鞋：每级 +10% 移速
        }

        public float getCooldownMultiplier() {
            int lvl = getLevel(Type.ENERGY_CUBE);
            return Math.max(0.4f, 1.0f - lvl * 0.10f); // 能量块：每级 -10% CD
        }

        public float getMagnetMultiplier() {
            int lvl = getLevel(Type.MAGNET_SUPPLY);
            return 1.0f + lvl * 0.35f; // 强力磁铁：每级 +35% 范围
        }

        public int getBonusMaxHp() {
            int lvl = getLevel(Type.FITNESS_GUIDE);
            return lvl * 3; // 健身指南：每级 +3 生命上限
        }

        public int getRegenPerTick() {
            return getLevel(Type.ENERGY_DRINK); // 功能饮料：每级回复提升
        }
    }
}
