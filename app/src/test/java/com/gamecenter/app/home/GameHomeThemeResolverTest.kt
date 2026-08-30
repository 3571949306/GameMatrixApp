package com.gamecenter.app.home

import com.gamecenter.app.ColorSchemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题解析器测试（计划 §9.1）：8 套 Scheme × 浅/深 = 16 组合的角色完整性与对比度门槛。
 */
class GameHomeThemeResolverTest {

    private val schemes: List<ColorSchemeManager.Scheme> = ColorSchemeManager.getSchemes()

    @Test
    fun `仓库提供8套配色方案`() {
        assertEquals(8, schemes.size)
    }

    @Test
    fun `16组合全部角色非透明`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val p = GameHomeThemeResolver.resolve(scheme, isDark)
                listOf(
                    p.background, p.surface, p.surfaceVariant, p.onSurface, p.onSurfaceVariant,
                    p.primary, p.onPrimary, p.primaryContainer, p.onPrimaryContainer,
                    p.outline, p.selectedContainer, p.onSelectedContainer,
                ).forEach { c ->
                    assertEquals(
                        "${scheme.name} ${if (isDark) "深色" else "浅色"} 角色出现透明值",
                        0xFF, (c ushr 24) and 0xFF
                    )
                }
            }
        }
    }

    @Test
    fun `正文对表面对比度不低于4_5`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val p = GameHomeThemeResolver.resolve(scheme, isDark)
                assertTrue(
                    "${scheme.name} ${if (isDark) "深" else "浅"} onSurface/surface=" +
                        "%.2f".format(GameHomeThemeResolver.contrastRatio(p.onSurface, p.surface)),
                    GameHomeThemeResolver.contrastRatio(p.onSurface, p.surface) >= 4.5
                )
                assertTrue(
                    GameHomeThemeResolver.contrastRatio(p.onSurfaceVariant, p.surface) >= 3.0
                )
            }
        }
    }

    @Test
    fun `主操作文字对主色对比度达标`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val p = GameHomeThemeResolver.resolve(scheme, isDark)
                val ratio = GameHomeThemeResolver.contrastRatio(p.onPrimary, p.primary)
                if (isDark) {
                    // 深色 onPrimary 由 Resolver 按对比度挑选，必须达 WCAG AA 正文
                    assertTrue(
                        "${scheme.name} 深 onPrimary/primary=%.2f".format(ratio), ratio >= 4.5
                    )
                } else {
                    // 浅色沿用 Scheme 既定配对（与全局语义一致），按大字号正文门槛 3.0
                    assertTrue(
                        "${scheme.name} 浅 onPrimary/primary=%.2f".format(ratio), ratio >= 3.0
                    )
                }
            }
        }
    }

    @Test
    fun `轮廓与选中容器对比度不低于3`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val p = GameHomeThemeResolver.resolve(scheme, isDark)
                assertTrue(
                    "${scheme.name} ${if (isDark) "深" else "浅"} outline/surface=" +
                        "%.2f".format(GameHomeThemeResolver.contrastRatio(p.outline, p.surface)),
                    GameHomeThemeResolver.contrastRatio(p.outline, p.surface) >= 3.0
                )
                assertTrue(
                    GameHomeThemeResolver.contrastRatio(p.onSelectedContainer, p.selectedContainer) >= 3.0
                )
            }
        }
    }

    @Test
    fun `按压覆盖色为半透明且不改基色`() {
        val base = 0xFF336699.toInt()
        val overlay = GameHomeThemeResolver.withAlpha(base, 0x1F)
        assertEquals(0x1F, (overlay ushr 24) and 0xFF)
        assertEquals(base and 0x00FFFFFF, overlay and 0x00FFFFFF)
    }

    @Test
    fun `Activity重建_Resolver两次解析结果一致_无状态缓存`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val a = GameHomeThemeResolver.resolve(scheme, isDark)
                val b = GameHomeThemeResolver.resolve(scheme, isDark)
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `按压覆盖与选中容器状态可辨识`() {
        schemes.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val p = GameHomeThemeResolver.resolve(scheme, isDark)
                // 按压覆盖 = onSurface 半透明；选中容器与内容对比度 ≥3（状态不只靠颜色，
                // 由选中 chip 的填充色差 + 文字对比共同表达）
                assertTrue((p.pressedOverlay ushr 24) and 0xFF in 1..0xFE)
                assertTrue(
                    GameHomeThemeResolver.contrastRatio(p.onSelectedContainer, p.selectedContainer) >= 3.0
                )
            }
        }
    }

    @Test
    fun `对比度公式边界_黑白为21`() {
        assertEquals(21.0, GameHomeThemeResolver.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, GameHomeThemeResolver.contrastRatio(0xFF000000.toInt(), 0xFF000000.toInt()), 0.001)
    }
}
