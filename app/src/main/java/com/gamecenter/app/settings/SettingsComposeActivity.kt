package com.gamecenter.app.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.ui.theme.GameMatrixTheme

/**
 * Compose 版设置页（Phase 1 试点）。
 *
 * 与原 [com.gamecenter.app.SettingsActivity] 并存：
 * - 旧 Activity 仍走 ViewBinding + XML，保证存量功能不回归
 * - 新 Activity 用 Compose + GameMatrixTheme 试点新栈
 *
 * UI 状态由 SharedPreferences("settings") 持久化，键名沿用旧实现：
 * - `night_mode` (Int: 0=跟随系统, 1=浅色, 2=深色)
 * - `app_language` (Int: 0/1/2)
 * - `sound_enabled` (Boolean)
 * - `vibration_enabled` (Boolean)
 * - `dynamic_color_enabled` (Boolean, 预留)
 */
class SettingsComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameMatrixTheme {
                SettingsRoot(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var themeMode by remember {
        mutableStateOf(
            ThemeMode.values().firstOrNull {
                it.storageValue == prefs.getInt("night_mode", 0)
            } ?: ThemeMode.FOLLOW_SYSTEM
        )
    }
    var dynamicColor by remember {
        mutableStateOf(prefs.getBoolean("dynamic_color_enabled", false))
    }
    var soundEnabled by remember {
        mutableStateOf(prefs.getBoolean("sound_enabled", true))
    }
    var vibrationEnabled by remember {
        mutableStateOf(prefs.getBoolean("vibration_enabled", true))
    }
    val appVersion = remember {
        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    val callbacks = remember {
        SettingsCallbacks(
            onBack = onBack,
            onLanguageClick = {
                showLanguageDialog(context) { idx ->
                    prefs.edit().putInt("app_language", idx).apply()
                }
            },
            onDefaultGameClick = {
                // TODO Phase 2：接入默认游戏选择器（与 GamesFragment 联动）
            },
            onThemeModeChange = { mode ->
                themeMode = mode
                prefs.edit().putInt("night_mode", mode.storageValue).apply()
                val delegateMode = when (mode) {
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(delegateMode)
            },
            onDynamicColorToggle = { enabled ->
                dynamicColor = enabled
                prefs.edit().putBoolean("dynamic_color_enabled", enabled).apply()
                // TODO Phase 2：Android 12+ 接入动态取色 (DynamicColors API)
            },
            onSoundToggle = { enabled ->
                soundEnabled = enabled
                prefs.edit().putBoolean("sound_enabled", enabled).apply()
            },
            onVibrationToggle = { enabled ->
                vibrationEnabled = enabled
                prefs.edit().putBoolean("vibration_enabled", enabled).apply()
            },
            onAboutClick = {
                showAboutDialog(context)
            },
            onLicenseClick = { license ->
                showLicenseDialog(context, license)
            },
        )
    }

    SettingsScreen(
        state = SettingsUiState(
            languageLabel = languageLabel(prefs.getInt("app_language", 0)),
            defaultGameLabel = "（未设置）",
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColor,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            appVersion = appVersion,
        ),
        callbacks = callbacks,
    )
}

private fun languageLabel(idx: Int): String = when (idx) {
    1 -> "中文"
    2 -> "English"
    else -> "跟随系统"
}

private fun showLanguageDialog(context: Context, onPick: (Int) -> Unit) {
    if (context !is android.app.Activity) return
    val items = arrayOf("跟随系统", "中文", "English")
    AlertDialog.Builder(context)
        .setTitle("应用语言")
        .setItems(items) { _, which -> onPick(which) }
        .setNegativeButton("取消", null)
        .show()
}

private fun showAboutDialog(context: Context) {
    if (context !is android.app.Activity) return
    AlertDialog.Builder(context)
        .setTitle("关于 GameCenter")
        .setMessage(
            "GameCenter — 游戏中心\n\n" +
                    "一个模块化的游戏平台，\n" +
                    "所有游戏内容通过模块商店下载。\n\n" +
                    "服务器: ${BuildConfig.MODULE_HOST}\n" +
                    "模块存储: /data/data/包名/files/modules/"
        )
        .setPositiveButton("确定", null)
        .show()
}

private fun showLicenseDialog(context: Context, license: OpenSourceLicense) {
    if (context !is android.app.Activity) return
    AlertDialog.Builder(context)
        .setTitle(license.name)
        .setMessage("License: ${license.license}")
        .setPositiveButton("确定", null)
        .show()
}
