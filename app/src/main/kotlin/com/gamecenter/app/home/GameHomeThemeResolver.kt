package com.gamecenter.app.home

import com.gamecenter.app.ColorSchemeManager
import kotlin.math.max
import kotlin.math.min

/**
 * 页面主题解析器（计划 §5.2）：用户 Scheme + 明暗 → 页面专用不可变 Palette。
 *
 * 角色契约（§5.1）全部来自 Scheme 已有字段；深色缺失的三个按钮/容器角色
 * （onPrimary、primaryContainer、onPrimaryContainer）采用**页面级降级映射**：
 * 以 WCAG 对比度在候选色中选择（§5.2.4 二选一之后者，不扩展 Scheme 字段）。
 * 全部透明度合成集中在 Resolver，Adapter/Fragment 禁止自行拼色（§5.2.3）。
 * 纯 Kotlin 位运算实现（无 android.graphics 依赖），保证本地单测可跑真实计算。
 */
object GameHomeThemeResolver {

    /** 页面专用不可变调色板。 */
    data class GameHomePalette(
        val background: Int,
        val surface: Int,
        val surfaceVariant: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val outline: Int,
        val pressedOverlay: Int,
        val selectedContainer: Int,
        val onSelectedContainer: Int,
    )

    fun resolve(scheme: ColorSchemeManager.Scheme, isDark: Boolean): GameHomePalette {
        if (!isDark) {
            return GameHomePalette(
                background = scheme.background,
                surface = scheme.surface,
                surfaceVariant = scheme.surfaceVariant,
                onSurface = scheme.onSurface,
                onSurfaceVariant = scheme.onSurfaceVariant,
                primary = scheme.primary,
                onPrimary = scheme.onPrimary,
                primaryContainer = scheme.primaryContainer,
                onPrimaryContainer = scheme.onPrimaryContainer,
                outline = scheme.cardBorder,
                pressedOverlay = withAlpha(scheme.onSurface, 0x1F),
                selectedContainer = scheme.primaryContainer,
                onSelectedContainer = scheme.onPrimaryContainer,
            )
        }
        // 深色：Scheme 缺深色 onPrimary/主色容器角色 → 按对比度降级映射
        val darkOnPrimary = pickByContrast(
            scheme.darkPrimary, candidates = listOf(scheme.darkBackground, scheme.darkOnSurface)
        )
        return GameHomePalette(
            background = scheme.darkBackground,
            surface = scheme.darkSurface,
            surfaceVariant = scheme.darkSurfaceVariant,
            onSurface = scheme.darkOnSurface,
            onSurfaceVariant = scheme.darkOnSurfaceVariant,
            primary = scheme.darkPrimary,
            onPrimary = darkOnPrimary,
            primaryContainer = scheme.darkSurfaceVariant,
            onPrimaryContainer = scheme.darkOnSurfaceVariant,
            outline = scheme.darkCardBorder,
            pressedOverlay = withAlpha(scheme.darkOnSurface, 0x1F),
            selectedContainer = scheme.darkSurfaceVariant,
            onSelectedContainer = scheme.darkOnSurfaceVariant,
        )
    }

    /** 生成按压态覆盖色（Resolver 统一 alpha 合成）。 */
    fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

    /** WCAG 相对对比度（1..21）。 */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun pickByContrast(background: Int, candidates: List<Int>): Int =
        candidates.maxByOrNull { contrastRatio(it, background) } ?: candidates.first()
}
