package com.gamecenter.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gamecenter.app.recovery.CrashDetector
import com.gamecenter.app.core.security.SecureOkHttpFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 应用程序全局入口类，负责整个应用生命周期内的全局初始化与状态管理。
 */
@HiltAndroidApp
class App : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isDarkMode = false
    private var updateAutoCheckDone = false
    private lateinit var moduleLifecycleManager: ModuleLifecycleManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    /**
     * 2026-06-23 模块预装：把 assets/modules/ 中的预装 APK 提取到 filesDir/modules/。
     */
    private fun extractPreinstalledModules() {
        val modulesDir = File(filesDir, "modules")
        if (!modulesDir.exists() && !modulesDir.mkdirs()) {
            Log.w("App", "[preinstall] 无法创建 modules 目录: ${modulesDir.absolutePath}")
            return
        }

        val assetFiles = try {
            assets.list("modules")
        } catch (e: IOException) {
            Log.w("App", "[preinstall] 无法读取 assets/modules/: ${e.message}")
            return
        }
        if (assetFiles.isNullOrEmpty()) {
            Log.d("App", "[preinstall] assets/modules/ 中无预装模块")
            return
        }

        var extractedCount = 0
        for (assetName in assetFiles) {
            if (!assetName.endsWith(".apk")) continue
            val targetFile = File(modulesDir, assetName)
            val tempFile = File(modulesDir, "$assetName.tmp")
            try {
                assets.open("modules/$assetName").use { input ->
                    val assetSize = input.available()
                    if (targetFile.exists() && targetFile.length() == assetSize.toLong()) {
                        Log.d("App", "[preinstall] 已存在且大小一致: $assetName，跳过提取")
                        return@use
                    }
                    if (targetFile.exists()) {
                        if (!targetFile.canWrite()) {
                            targetFile.setWritable(true, true)
                        }
                        if (!targetFile.delete()) {
                            Log.w("App", "[preinstall] 无法删除旧模块: ${targetFile.absolutePath}")
                            return@use
                        }
                    }
                    if (tempFile.exists() && !tempFile.delete()) {
                        Log.w("App", "[preinstall] 无法删除旧临时文件: ${tempFile.absolutePath}")
                        return@use
                    }
                    FileOutputStream(tempFile).use { out ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (input.read(buf).also { len = it } > 0) {
                            out.write(buf, 0, len)
                        }
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        Log.w("App", "[preinstall] 无法替换模块: $assetName")
                        return@use
                    }
                    extractedCount++
                    Log.i("App", "[preinstall] 提取成功: $assetName (${targetFile.length()} bytes)")
                }
            } catch (e: IOException) {
                Log.e("App", "[preinstall] 提取失败: $assetName - ${e.message}")
            }
        }
        if (extractedCount > 0) {
            val prefs = getSharedPreferences("module_manager_prefs", MODE_PRIVATE)
            prefs.edit().putLong("preinstall_last_extract_time", System.currentTimeMillis()).apply()
            Log.i("App", "[preinstall] 共提取 $extractedCount 个模块 APK")
        }
    }

    override fun onCreate() {
        super.onCreate()

        CrashDetector.markAppStart(this)
        applyLanguage()
        applyTheme()

        // 异步提取模块以防止阻塞主线程启动
        applicationScope.launch(Dispatchers.IO) {
            extractPreinstalledModules()
        }

        SecureOkHttpFactory.setHosts(BuildConfig.MODULE_HOST, !BuildConfig.DEBUG)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyColorScheme(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        moduleLifecycleManager = ModuleLifecycleManager.getInstance(this)
        moduleLifecycleManager.initialize()
        Log.i("App", "模块系统已初始化")
    }

    fun shouldAutoCheckUpdate(): Boolean {
        if (updateAutoCheckDone) return false
        updateAutoCheckDone = true
        return true
    }

    fun applyTheme() {
        val settings = SettingsManager.getInstance(this)
        val themeMode = settings.themeMode

        when (themeMode) {
            SettingsManager.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                isDarkMode = false
            }
            SettingsManager.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                isDarkMode = true
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                isDarkMode = (resources.configuration.uiMode
                        and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun applyLanguage() {
        val settings = SettingsManager.getInstance(this)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(settings.appLanguage)
        )
    }

    private fun applyColorScheme(activity: Activity) {
        val settings = SettingsManager.getInstance(this)
        val schemeIndex = settings.colorSchemeIndex
        val scheme = ColorSchemeManager.getScheme(schemeIndex)
        ColorSchemeManager.applyScheme(activity, scheme, isDarkMode)
    }

    fun getModuleLifecycleManager(): ModuleLifecycleManager {
        return moduleLifecycleManager
    }

    override fun onTerminate() {
        super.onTerminate()
        moduleLifecycleManager.release()
        Log.i("App", "应用程序已终止，所有资源已释放")
    }

    companion object {
        @JvmStatic
        fun refreshColorScheme(activity: Activity?) {
            if (activity != null && activity.application is App) {
                val app = activity.application as App
                val settings = SettingsManager.getInstance(app)
                val schemeIndex = settings.colorSchemeIndex
                val scheme = ColorSchemeManager.getScheme(schemeIndex)
                ColorSchemeManager.applyScheme(activity, scheme, app.isDarkMode)
            }
        }
    }
}
