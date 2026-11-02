package com.gamecenter.app.brotato;

import com.gamecenter.app.brotato.engine.BrotatoArena;

/**
 * 土豆兄弟角色/英雄系统：
 * 提供 6 位差异化鲜明的特色英雄，涵盖均衡、近战双枪、奥术爆发、重装肉盾、幸运打宝与极速风筝。
 */
public enum BrotatoHero {

    ALL_ROUNDER(
            "all_rounder",
            "科萌",
            "战术特工",
            "🧣",
            "《蛋壳特工队》王牌特工，标志性飘逸红领巾，全能战术均衡大师。",
            0,      // bonusMaxHp
            1.0f,   // speedFactor
            1.0f,   // damageFactor
            1.0f,   // fireIntervalFactor
            0,      // extraShots
            0.08f,  // critChance
            1.0f,   // magnetRangeFactor
            0xFFD4A574, // bodyColor
            0xFFEF4444  // accentColor (红领巾)
    ),

    BRAWLER(
            "brawler",
            "金",
            "暴击女王",
            "👑",
            "双枪暴击特工，专属致命感知火力全开，攻速极快与高额暴击伤害。",
            -2,     // bonusMaxHp (8 HP)
            1.05f,  // speedFactor
            0.9f,   // damageFactor
            0.75f,  // fireIntervalFactor (大幅缩短射击间隔)
            1,      // extraShots (+1枪，双枪齐射)
            0.15f,  // critChance
            1.1f,   // magnetRangeFactor
            0xFFD97706, // bodyColor
            0xFFDC2626  // accentColor (绯红马尾)
    ),

    MAGE(
            "mage",
            "月咏",
            "月影剑客",
            "🌙",
            "神秘和风特工，月影斩剑气单发威力惊人，魔刃穿透群怪。",
            -1,     // bonusMaxHp (9 HP)
            0.95f,  // speedFactor
            1.65f,  // damageFactor (大幅提高单发伤害)
            1.25f,  // fireIntervalFactor
            0,      // extraShots
            0.22f,  // critChance (高暴击)
            1.25f,  // magnetRangeFactor
            0xFF8B5CF6, // bodyColor (秘术紫)
            0xFF6366F1  // accentColor
    ),

    CHUNKY(
            "chunky",
            "沃尔默",
            "重装教官",
            "🛡️",
            "退役重装特工教官，泰坦身躯极厚防弹血甲，稳扎稳打。",
            12,     // bonusMaxHp (22 HP!)
            0.85f,  // speedFactor
            1.2f,   // damageFactor
            1.05f,  // fireIntervalFactor
            0,      // extraShots
            0.05f,  // critChance
            1.0f,   // magnetRangeFactor
            0xFF78716C, // bodyColor (岩石灰)
            0xFF475569  // accentColor (重装战术盔)
    ),

    LUCKY(
            "lucky",
            "凯蒂斯",
            "机灵猫娘",
            "🐱",
            "战术医疗猫娘特工，四叶草天眷，超大范围晶石磁吸与战地补给。",
            0,      // bonusMaxHp (10 HP)
            1.05f,  // speedFactor
            0.95f,  // damageFactor
            0.95f,  // fireIntervalFactor
            0,      // extraShots
            0.28f,  // critChance (超高暴击)
            2.0f,   // magnetRangeFactor (2倍磁吸范围)
            0xFF10B981, // bodyColor (翡翠绿)
            0xFF22C55E  // accentColor (猫耳通讯器)
    ),

    SPEEDSTER(
            "speedster",
            "斯巴达",
            "极速游侠",
            "⚡",
            "特工穿插游侠，身手敏捷拥有超高机动性与极快回火，风筝牵引之王。",
            -3,     // bonusMaxHp (7 HP)
            1.4f,   // speedFactor (高移速)
            0.85f,  // damageFactor
            0.82f,  // fireIntervalFactor
            0,      // extraShots
            0.12f,  // critChance
            1.3f,   // magnetRangeFactor
            0xFF06B6D4, // bodyColor (风灵青)
            0xFF38BDF8  // accentColor (战术夜视镜)
    );

    private final String id;
    private final String nameCn;
    private final String title;
    private final String emoji;
    private final String description;
    private final int bonusMaxHp;
    private final float speedFactor;
    private final float damageFactor;
    private final float fireIntervalFactor;
    private final int extraShots;
    private final float critChance;
    private final float magnetRangeFactor;
    private final int bodyColor;
    private final int accentColor;

    BrotatoHero(String id, String nameCn, String title, String emoji, String description,
                int bonusMaxHp, float speedFactor, float damageFactor, float fireIntervalFactor,
                int extraShots, float critChance, float magnetRangeFactor, int bodyColor,
                int accentColor) {
        this.id = id;
        this.nameCn = nameCn;
        this.title = title;
        this.emoji = emoji;
        this.description = description;
        this.bonusMaxHp = bonusMaxHp;
        this.speedFactor = speedFactor;
        this.damageFactor = damageFactor;
        this.fireIntervalFactor = fireIntervalFactor;
        this.extraShots = extraShots;
        this.critChance = critChance;
        this.magnetRangeFactor = magnetRangeFactor;
        this.bodyColor = bodyColor;
        this.accentColor = accentColor;
    }

    public String getId() {
        return id;
    }

    public String getNameCn() {
        return nameCn;
    }

    public String getTitle() {
        return title;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDescription() {
        return description;
    }

    public int getBonusMaxHp() {
        return bonusMaxHp;
    }

    public float getSpeedFactor() {
        return speedFactor;
    }

    public float getDamageFactor() {
        return damageFactor;
    }

    public float getFireIntervalFactor() {
        return fireIntervalFactor;
    }

    public int getExtraShots() {
        return extraShots;
    }

    public float getCritChance() {
        return critChance;
    }

    public float getMagnetRangeFactor() {
        return magnetRangeFactor;
    }

    public int getBodyColor() {
        return bodyColor;
    }

    public int getAccentColor() {
        return accentColor;
    }

    /**
     * 将英雄特质安全注入到已开局的 Arena 实例中。
     */
    public void applyTo(BrotatoArena arena) {
        if (arena == null) return;
        arena.applyHeroModifiers(bonusMaxHp, speedFactor, damageFactor, fireIntervalFactor, extraShots);
    }

    public static BrotatoHero fromId(String id) {
        if (id == null) return ALL_ROUNDER;
        for (BrotatoHero hero : values()) {
            if (hero.id.equalsIgnoreCase(id)) {
                return hero;
            }
        }
        return ALL_ROUNDER;
    }
}
