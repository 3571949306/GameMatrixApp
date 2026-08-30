package com.gamecenter.app.home

/** 自适应列数策略（计划 §6.6/Phase 4）：按可用宽度与 fontScale 决定列数。 */
object GameHomeLayoutPolicy {

    data class GridSpec(val spanCount: Int)

    const val FULL_SPAN = -1

    fun spanCount(widthDp: Int, fontScale: Float): Int {
        val scaled = widthDp / maxOf(fontScale, 1f)
        return when {
            widthDp >= 600 -> if (fontScale >= 1.3f) 4 else 6
            scaled < 300 -> 2
            widthDp < 360 -> 3
            fontScale >= 2.0f -> 3
            else -> 4
        }
    }

    /** 行占宽：Tile 占 1 列；其余（提醒/继续/标题/最近行/空状态）占满全部列。 */
    fun spanSizeFor(item: GameHomeItem, spanCount: Int): Int = when (item) {
        is GameHomeItem.GameTile -> 1
        else -> spanCount
    }
}
