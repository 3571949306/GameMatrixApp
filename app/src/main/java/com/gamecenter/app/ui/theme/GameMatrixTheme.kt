package com.gamecenter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * GameMatrixApp Compose 主题（Phase 1 试点）。
 *
 * 设计 Token 取值来源：Spec §7.1 / §7.2（与 res/values/colors.xml + dimens_tokens.xml 同源）。
 * 主色 #3D5AFE / 强调色 #FFB300 / 辅助色 #00897B。
 *
 * 注意：XML 资源（color_tokens.xml）仍是单一真源；这里把同样的色值映射进 Compose
 * ColorScheme，避免运行时跨边界读资源带来的开销与生命周期耦合。如需调整色值，请同步
 * 修改 res/values/colors.xml 与 res/values-night/colors.xml。
 */
private val GameMatrixLightColorScheme = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE3FF),
    onPrimaryContainer = Color(0xFF001558),
    secondary = Color(0xFFFFB300),
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFFFFE08C),
    onSecondaryContainer = Color(0xFF2E1E00),
    tertiary = Color(0xFF00897B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF97D6CB),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF16181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFE7E9F2),
    onSurfaceVariant = Color(0xFF43474F),
    surfaceContainerLow = Color(0xFFEEF0F7),
    surfaceContainer = Color(0xFFE7E9F2),
    surfaceContainerHigh = Color(0xFFE0E3EC),
    outline = Color(0xFFC8CCDA),
    error = Color(0xFFD8392F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val GameMatrixDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8C9CFF),
    onPrimary = Color(0xFF001558),
    primaryContainer = Color(0xFF2E3BB8),
    onPrimaryContainer = Color(0xFFDEE3FF),
    secondary = Color(0xFFFFC24D),
    onSecondary = Color(0xFF2E1E00),
    secondaryContainer = Color(0xFF5C4400),
    onSecondaryContainer = Color(0xFFFFE08C),
    tertiary = Color(0xFF4FD1C5),
    onTertiary = Color(0xFF00302B),
    tertiaryContainer = Color(0xFF004C44),
    onTertiaryContainer = Color(0xFF97D6CB),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE4E6F0),
    surface = Color(0xFF161922),
    onSurface = Color(0xFFE4E6F0),
    surfaceVariant = Color(0xFF2A2E3A),
    onSurfaceVariant = Color(0xFFC5C9D6),
    surfaceContainerLow = Color(0xFF1C202B),
    surfaceContainer = Color(0xFF222633),
    surfaceContainerHigh = Color(0xFF2A2F3D),
    outline = Color(0xFF3D4250),
    error = Color(0xFFFF8A84),
    onError = Color(0xFF4B0D0A),
    errorContainer = Color(0xFF8C1A16),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Type Scale — Spec §7.2。
 *
 * 字体栈：Noto Sans SC / Inter / system-ui 等（由 FontFamily.Default 在运行时回落到系统字体）。
 * 等宽：JetBrains Mono / Roboto Mono（FontFamily.Monospace）。
 */
private val GameMatrixTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * GameMatrixTheme — Compose 主题入口。
 *
 * @param darkTheme 是否使用深色主题，默认跟随系统。
 * @param content 子树。
 */
@Composable
fun GameMatrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) GameMatrixDarkColorScheme else GameMatrixLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GameMatrixTypography,
        content = content,
    )
}
