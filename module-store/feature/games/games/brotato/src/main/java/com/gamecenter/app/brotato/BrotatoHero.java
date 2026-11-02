package com.gamecenter.app.brotato;

import com.gamecenter.app.brotato.engine.BrotatoArena;

/**
 * 土豆兄弟角色/英雄系统：
 * 提供 6 位差异化鲜明的特色英雄，涵盖均衡、近战双枪、奥术爆发、重装肉盾、幸运打宝与极速风筝。
 */
public enum BrotatoHero {

    ALL_ROUNDER(
            "all_rounder",
            "全能土豆",
            "均衡大师",
            "🥔",
            "属性均衡，无明显短板，适应各种战局风格的标准勇士。",
            0,      // bonusMaxHp
            1.0f,   // speedFactor
            1.0f,   // damageFactor
            1.0f,   // fireIntervalFactor
            0,      // extraShots
            0.08f,  // critChance
            1.0f,   // magnetRangeFactor
            0xFFD4A574, // bodyColor
            0xFFEF4444  // accentColor (红头带)
    ),

    BRAWLER(
            "brawler",
            "狂暴斗士",
            "双枪近战",
            "🥊",
            "双枪开局，狂暴射速与压制火力，擅长近身贴脸输出，但体质稍脆。",
            -2,     // bonusMaxHp (8 HP)
            1.05f,  // speedFactor
            0.9f,   // damageFactor
            0.75f,  // fireIntervalFactor (大幅缩短射击间隔)
            1,      // extraShots (+1枪，双枪齐射)
            0.15f,  // critChance
            1.1f,   // magnetRangeFactor
            0xFFD97706, // bodyColor (古铜色)
            0xFFDC2626  // accentColor (赤红战纹)
    ),

    MAGE(
            "mage",
            "奥术法师",
            "魔弹爆发",
            "🧙",
            "倾注远古魔力，奥术飞弹单发伤害惊人且附带高额暴击，但攻速略缓。",
            -1,     // bonusMaxHp (9 HP)
            0.95f,  // speedFactor
            1.65f,  // damageFactor (大幅提高单发伤害)
            1.25f,  // fireIntervalFactor
            0,      // extraShots
            0.22f,  // critChance (高暴击)
            1.25f,  // magnetRangeFactor
            0xFF8B5CF6, // bodyColor (秘术紫)
            0xFF6366F1  // accentColor (星芒法帽)
    ),

    CHUNKY(
            "chunky",
            "重装巨兽",
            "泰坦壁垒",
            "🛡️",
            "拥有极其厚实的生命力与硬质甲胄，伤害扎实，但身躯沉重移速受限。",
            12,     // bonusMaxHp (22 HP!)
            0.85f,  // speedFactor
            1.2f,   // damageFactor
            1.05f,  // fireIntervalFactor
            0,      // extraShots
            0.05f,  // critChance
            1.0f,   // magnetRangeFactor
            0xFF78716C, // bodyColor (岩石灰)
            0xFF475569  // accentColor (精钢重铠)
    ),

    LUCKY(
            "lucky",
            "幸运星",
            "财运亨通",
            "🍀",
            "四叶草天眷者，暴击率极高，享有两倍宝石磁吸半径与更多战利品。",
            0,      // bonusMaxHp (10 HP)
            1.05f,  // speedFactor
            0.95f,  // damageFactor
            0.95f,  // fireIntervalFactor
            0,      // extraShots
            0.28f,  // critChance (超高暴击)
            2.0f,   // magnetRangeFactor (2倍磁吸范围)
            0xFF10B981, // bodyColor (翡翠绿)
            0xFF22C55E  // accentColor (四叶草)
    ),

    SPEEDSTER(
            "speedster",
            "疾风幽灵",
            "走位风筝",
            "⚡",
            "身轻如燕，拥有无可比拟的超高机动性与极快回火，风筝牵引之王。",
            -3,     // bonusMaxHp (7 HP)
            1.4f,   // speedFactor (高移速)
            0.85f,  // damageFactor
            0.82f,  // fireIntervalFactor
            0,      // extraShots
            0.12f,  // critChance
            1.3f,   // magnetRangeFactor
            0xFF06B6D4, // bodyColor (风灵青)
            0xFF38BDF8  // accentColor (风羽翼)
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
