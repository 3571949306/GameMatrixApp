package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.CardType;

/**
 * 斗地主反馈映射器（P5）：把"出牌牌型"映射为特效 / 音效 / 震动档位。
 *
 * <p>纯 JVM 实现：不引用任何 Android 类或宿主资源常量，只做枚举到枚举的纯映射，
 * 便于单测锁定"牌型 → 反馈"契约。宿主资源 ID 的解析（{@code R.raw.xxx}）
 * 与实际播放集中在 {@link DoudizhuFeedback}（Android 侧桥层）。</p>
 *
 * <p>映射规则（改造执行计划 Phase 5 验收范围：炸弹/王炸/飞机/春天/连对）：</p>
 * <ul>
 *   <li>BOMB → 特效 BOMB + 音效 BOMB + 强震动</li>
 *   <li>JOKER_BOMB（王炸）→ 特效 ROCKET + 音效 ROCKET + 强震动</li>
 *   <li>AIRPLANE / AIRPLANE_WITH_WINGS → 特效 PLANE + 音效 PLANE + 轻震动</li>
 *   <li>STRAIGHT_PAIRS（连对）→ 特效 DOUBLE_LINE + 音效 DOUBLE_LINE + 轻震动</li>
 *   <li>其余牌型 → 无特效 + 普通出牌音效 + 不震动</li>
 *   <li>春天/反春不来自牌型（对局结束判定），由控制器在结算回调中直接指定
 *       {@link Effect#SPRING}（特效 + 中震动），不走 {@link #effectFor}</li>
 * </ul>
 *
 * <p>顺子（STRAIGHT）在 EffectsView 中有 SHUNZI 特效实现，但计划验收未要求接线
 * （顺子出现频率高，每手都触发会过度打扰），保持不触发。</p>
 */
public final class DoudizhuEffectMapper {

    /** 特效种类（与 DouDiZhuEffectsView.EffectType 一一对应，解耦 View 层） */
    public enum Effect {
        BOMB,        // 炸弹爆炸
        ROCKET,      // 王炸（火箭）
        PLANE,       // 飞机飞过
        SPRING,      // 春天花瓣（含反春复用）
        DOUBLE_LINE  // 连对金色双排
    }

    /** 音效种类（DoudizhuFeedback 负责映射到宿主 res/raw 资源） */
    public enum Sfx {
        PLAY,        // 普通出牌
        BOMB,        // 炸弹爆炸音
        ROCKET,      // 王炸音
        PLANE,       // 飞机音
        DOUBLE_LINE, // 连对语音
        WIN,         // 结算胜利
        LOSE         // 结算失败
    }

    /** 震动档位（时长由 DoudizhuFeedback 决定） */
    public enum Shake {
        NONE,    // 不震动
        LIGHT,   // 轻震（飞机/连对）
        MEDIUM,  // 中震（春天）
        STRONG   // 强震（炸弹/王炸）
    }

    private DoudizhuEffectMapper() {}

    /**
     * 牌型 → 特效种类。
     *
     * @param type 出牌牌型，null 或 ERROR 视为无效牌型
     * @return 特效种类；无对应特效时返回 null
     */
    public static Effect effectFor(CardType type) {
        if (type == null) return null;
        switch (type) {
            case BOMB: return Effect.BOMB;
            case JOKER_BOMB: return Effect.ROCKET;
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS: return Effect.PLANE;
            case STRAIGHT_PAIRS: return Effect.DOUBLE_LINE;
            default: return null;
        }
    }

    /**
     * 牌型 → 音效种类。有专属特效的牌型播专属音，其余一律普通出牌音。
     *
     * @param type 出牌牌型，null 或 ERROR 视为无效牌型
     * @return 音效种类，永不为 null
     */
    public static Sfx sfxFor(CardType type) {
        if (type == null) return Sfx.PLAY;
        switch (type) {
            case BOMB: return Sfx.BOMB;
            case JOKER_BOMB: return Sfx.ROCKET;
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS: return Sfx.PLANE;
            case STRAIGHT_PAIRS: return Sfx.DOUBLE_LINE;
            default: return Sfx.PLAY;
        }
    }

    /**
     * 特效种类 → 震动档位（特效触发时配套震动）。
     *
     * @param effect 特效种类，null 视为无特效
     * @return 震动档位，永不为 null
     */
    public static Shake shakeFor(Effect effect) {
        if (effect == null) return Shake.NONE;
        switch (effect) {
            case BOMB:
            case ROCKET: return Shake.STRONG;
            case SPRING: return Shake.MEDIUM;
            case PLANE:
            case DOUBLE_LINE: return Shake.LIGHT;
            default: return Shake.NONE;
        }
    }
}
