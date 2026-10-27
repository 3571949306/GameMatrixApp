package com.gamecenter.app.home

/**
 * 自适应列数策略（视觉舒适度与信息密度平衡升级）。
 *
 * 在常规竖屏模式下，无论 360dp、400dp 还是 720dp（平板/大屏/模拟器竖屏），
 * 均采用舒适大气的 3~4 列布局，彻底告别过密 6 列导致的文字拥挤和图标微缩。
 * 仅在真正超宽桌面/大平板横屏模式 (>= 900dp) 下才扩展到 5~6 列。
 */
object GameHomeLayoutPolicy {

    data class GridSpec(val spanCount: Int)

    const val FULL_SPAN = -1

    fun spanCount(widthDp: Int, fontScale: Float): Int {
        val scaled = widthDp / maxOf(fontScale, 1f)
        return when {
            scaled < 300 -> 2
            widthDp < 360 -> 3
            fontScale >= 2.0f -> 3
            widthDp >= 900 -> if (fontScale >= 1.3f) 5 else 6
            widthDp >= 600 -> if (fontScale >= 1.4f) 3 else 4
            else -> if (fontScale >= 1.3f) 3 else 4
        }
    }

    /** 行占宽：Tile 占 1 列；其余（提醒/继续/标题/最近行/空状态）占满全部列。 */
    fun spanSizeFor(item: GameHomeItem, spanCount: Int): Int = when (item) {
        is GameHomeItem.GameTile -> 1
        else -> spanCount
    }
}
