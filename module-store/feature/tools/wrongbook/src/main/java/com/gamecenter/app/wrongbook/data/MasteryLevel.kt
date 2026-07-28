package com.gamecenter.app.wrongbook.data

import com.gamecenter.app.wrongbook.R

/**
 * 错题掌握度分级（统一阈值，消除列表页/详情页/看板页的状态显示不一致）。
 *
 * 之前列表页用 80/50、看板页用 80/60/30、详情页无分级，导致同一道题在不同页面
 * 显示的掌握度状态档位不同。本对象统一为三档：
 * - MASTERED（已掌握）：>= 80
 * - REVIEWING（待复习）：50..79
 * - UNMASTERED（未掌握）：< 50
 *
 * 看板页的四档分布图仍可保留更细粒度（30/60/80），但薄弱知识点判定统一用 50。
 */
object MasteryLevel {

    /** 已掌握阈值（含）。 */
    const val MASTERED_THRESHOLD = 80

    /** 待复习阈值下限（含）。 */
    const val REVIEWING_THRESHOLD = 50

    /** 看板页分布图使用的未掌握细分阈值（仅用于图表，不影响状态文案）。 */
    const val CHART_UNMASTERED_LOW = 30

    /** 看板页分布图使用的待复习细分阈值（仅用于图表）。 */
    const val CHART_REVIEWING_HIGH = 60

    enum class Level {
        MASTERED,
        REVIEWING,
        UNMASTERED
    }

    /**
     * 按 mastery 值返回所属级别。
     */
    fun levelOf(mastery: Int): Level = when {
        mastery >= MASTERED_THRESHOLD -> Level.MASTERED
        mastery >= REVIEWING_THRESHOLD -> Level.REVIEWING
        else -> Level.UNMASTERED
    }

    /**
     * 返回级别对应的颜色资源 ID。
     */
    fun colorRes(level: Level): Int = when (level) {
        Level.MASTERED -> R.color.wrongbook_mastery_high
        Level.REVIEWING -> R.color.wrongbook_mastery_mid
        Level.UNMASTERED -> R.color.wrongbook_mastery_low
    }

    /**
     * 便捷方法：直接由 mastery 值返回颜色资源 ID。
     */
    fun colorResByMastery(mastery: Int): Int = colorRes(levelOf(mastery))

    /**
     * 看板页四档分布索引：0=未掌握(<30), 1=薄弱(30..59), 2=待复习(60..79), 3=已掌握(>=80)。
     * 仅用于看板页掌握度分布图表，不影响状态文案与颜色。
     */
    fun chartDistributionIndex(mastery: Int): Int = when {
        mastery < CHART_UNMASTERED_LOW -> 0
        mastery < CHART_REVIEWING_HIGH -> 1
        mastery < MASTERED_THRESHOLD -> 2
        else -> 3
    }

    /**
     * 判断是否为薄弱知识点（用于看板页薄弱知识点列表）。
     * 统一使用 REVIEWING_THRESHOLD（50）作为薄弱分界线，与列表页颜色分级一致。
     */
    fun isWeak(mastery: Int): Boolean = mastery < REVIEWING_THRESHOLD
}
