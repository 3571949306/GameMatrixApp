package com.gamecenter.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import com.gamecenter.app.R

/**
 * 设置页 UI 状态。
 *
 * 注意：[dynamicColorEnabled] 是 Android 12+ 动态取色占位字段，Phase 1 仅 UI 预留，
 * 实际取色逻辑 TODO 在 Phase 2 接入。
 */
data class SettingsUiState(
    val languageLabel: String = "",
    val defaultGameLabel: String = "（未设置）",
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val appVersion: String = "",
)

/** 主题模式枚举（与 SettingsActivity#showThemeDialog 旧实现保持一致：0/1/2） */
enum class ThemeMode(val storageValue: Int, @androidx.annotation.StringRes val labelRes: Int) {
    FOLLOW_SYSTEM(0, R.string.theme_system),
    LIGHT(1, R.string.theme_light),
    DARK(2, R.string.theme_dark),
}

/** 开源许可占位数据 */
data class OpenSourceLicense(val name: String, val license: String)

/**
 * 设置页回调集合。空实现由 Activity 注入真实逻辑。
 */
class SettingsCallbacks(
    val onBack: () -> Unit = {},
    val onLanguageClick: () -> Unit = {},
    val onDefaultGameClick: () -> Unit = {},
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorToggle: (Boolean) -> Unit = {},
    val onSoundToggle: (Boolean) -> Unit = {},
    val onVibrationToggle: (Boolean) -> Unit = {},
    val onAboutClick: () -> Unit = {},
    val onLicenseClick: (OpenSourceLicense) -> Unit = {},
)

private val SAMPLE_LICENSES = listOf(
    OpenSourceLicense("Kotlin", "Apache 2.0"),
    OpenSourceLicense("Jetpack Compose", "Apache 2.0"),
    OpenSourceLicense("Material Components for Android", "Apache 2.0"),
    OpenSourceLicense("OkHttp", "Apache 2.0"),
    OpenSourceLicense("Gson", "Apache 2.0"),
    OpenSourceLicense("Glide", "BSD, MIT"),
    OpenSourceLicense("Hilt", "Apache 2.0"),
)

/**
 * 设置页 Composable。
 *
 * 设计原则：
 * - 大顶栏（LargeTopAppBar）+ 折叠滚动；分组 ListItem
 * - 间距全部走 `gm_spacing_*` token（dimensionResource），不硬编码 dp
 * - 颜色/字体走 GameMatrixTheme 注入的 MaterialTheme.colorScheme / typography
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier
                            .size(dimensionResource(R.dimen.gm_touch_target))
                            .clickable(onClick = callbacks.onBack)
                            .padding(dimensionResource(R.dimen.gm_spacing_3)),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.gm_spacing_2)),
        ) {
            // ===== 通用 =====
            item { SectionHeader(stringResource(R.string.general)) }
            item {
                val followSystemLabel = stringResource(R.string.settings_language_follow_system)
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Star, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(state.languageLabel.ifEmpty { followSystemLabel }) },
                    modifier = Modifier.clickable(onClick = callbacks.onLanguageClick),
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Build, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_default_game)) },
                    supportingContent = { Text(state.defaultGameLabel) },
                    modifier = Modifier.clickable(onClick = callbacks.onDefaultGameClick),
                )
            }

            // ===== 外观 =====
            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_theme_mode)) },
                )
            }
            item {
                ThemeModeSelector(
                    selected = state.themeMode,
                    onSelect = callbacks.onThemeModeChange,
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Face, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_dynamic_color_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.dynamicColorEnabled,
                            onCheckedChange = callbacks.onDynamicColorToggle,
                        )
                    },
                )
            }

            // ===== 游戏 =====
            item { SectionHeader(stringResource(R.string.game)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_game_sound)) },
                    trailingContent = {
                        Switch(
                            checked = state.soundEnabled,
                            onCheckedChange = callbacks.onSoundToggle,
                        )
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.settings_vibration)) },
                    trailingContent = {
                        Switch(
                            checked = state.vibrationEnabled,
                            onCheckedChange = callbacks.onVibrationToggle,
                        )
                    },
                )
            }

            // ===== 关于 =====
            item { SectionHeader(stringResource(R.string.about)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.about_app_version)) },
                    supportingContent = {
                        Text(
                            text = state.appVersion,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.about_gamecenter_title)) },
                    modifier = Modifier.clickable(onClick = callbacks.onAboutClick),
                )
            }
            item {
                HorizontalDivider()
            }
            items(SAMPLE_LICENSES) { license ->
                ListItem(
                    headlineContent = { Text(license.name) },
                    supportingContent = { Text(license.license) },
                    modifier = Modifier.clickable {
                        callbacks.onLicenseClick(license)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimensionResource(R.dimen.gm_spacing_4),
                end = dimensionResource(R.dimen.gm_spacing_4),
                top = dimensionResource(R.dimen.gm_spacing_4),
                bottom = dimensionResource(R.dimen.gm_spacing_1),
            ),
    )
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = dimensionResource(R.dimen.gm_spacing_4)),
    ) {
        ThemeMode.values().forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(vertical = dimensionResource(R.dimen.gm_spacing_2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                )
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = dimensionResource(R.dimen.gm_spacing_2)),
                )
            }
        }
    }
}
