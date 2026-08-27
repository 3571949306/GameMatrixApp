package com.gamecenter.app

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gamecenter.app.recovery.CrashDetector
import com.gamecenter.app.util.CrashHandler
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.process.AppProcessIdentity
import com.gamecenter.app.process.AppProcessPolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 应用程序全局入口类，负责整个应用生命周期内的全局初始化与状态管理。
 */
@HiltAndroidApp
class App : Application() {

    // The ADB process must not allocate a host coroutine scope or start host work.
    private val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    private var hostInitializationEnabled = false

    private var isDarkMode = false
    private var updateAutoCheckDone = false

    /**
     * P1-内存：内存压力回调注册表。任何持有常驻缓存（Bitmap LruCache、AI 置换表、
     * 大型对象池）的组件都可以注册进来，按 trim level 释放对应层级的内存。
     * 使用 CopyOnWriteArrayList 防止并发 register/unregister 与 onTrimMemory 派发冲突。
     */
    private val trimListeners = CopyOnWriteArrayList<ComponentCallbacks2>()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    /**
     * P1-内存：注册内存压力回调监听器。
     * 系统发出 onTrimMemory(level) / onLowMemory() 时，本类会按注册顺序派发给所有监听器。
     */
    fun registerTrimListener(listener: ComponentCallbacks2) {
        if (!trimListeners.contains(listener)) {
            trimListeners.add(listener)
        }
    }

    /**
     * P1-内存：注销内存压力回调监听器。
     */
    fun unregisterTrimListener(listener: ComponentCallbacks2) {
        trimListeners.remove(listener)
    }

    /**
     * P1-内存：当前已注册的内存压力回调监听器数量（用于诊断/测试）。
     */
    fun getTrimListenerCount(): Int = trimListeners.size

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("App", "[trim] level=$level (UI_HIDDEN=20/BACKGROUND=40/MODERATE=60/COMPLETE=80)")
        // 注意：CopyOnWriteArrayList 的迭代器是 snapshot，并发 register/unregister 不会抛 ConcurrentModification
        for (listener in trimListeners) {
            try {
                listener.onTrimMemory(level)
            } catch (t: Throwable) {
                Log.w("App", "[trim] listener 抛异常", t)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w("App", "[trim] onLowMemory → 视为 TRIM_MEMORY_COMPLETE")
        for (listener in trimListeners) {
            try {
                listener.onLowMemory()
                listener.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            } catch (t: Throwable) {
                Log.w("App", "[trim] listener 抛异常", t)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 必须实现，否则 lint 警告 — 与 ComponentCallbacks2 契约对应
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
            // 校验提取/已存在的 APK 完整性；损坏则删除并重提一次，仍失败则拒绝（不残留坏包）。
            if (!validatePreinstalledApk(targetFile)) {
                Log.w("App", "[preinstall] APK 损坏或缺失，尝试重提: $assetName")
                targetFile.delete()
                try {
                    assets.open("modules/$assetName").use { input ->
                        val buf = ByteArray(8192)
                        var len: Int
                        FileOutputStream(targetFile).use { out ->
                            while (input.read(buf).also { len = it } > 0) out.write(buf, 0, len)
                        }
                    }
                    Log.i("App", "[preinstall] 重提成功: $assetName (${targetFile.length()} bytes)")
                } catch (e: IOException) {
                    Log.e("App", "[preinstall] 重提失败: $assetName - ${e.message}")
                }
            }
            if (!validatePreinstalledApk(targetFile)) {
                Log.e("App", "[preinstall] APK 重提后仍无效，拒绝: $assetName（模块将不可用，待商店/分发修复）")
                targetFile.delete()
            }
        }
        if (extractedCount > 0) {
            val prefs = getSharedPreferences("module_manager_prefs", MODE_PRIVATE)
            prefs.edit().putLong("preinstall_last_extract_time", System.currentTimeMillis()).apply()
            Log.i("App", "[preinstall] 共提取 $extractedCount 个模块 APK")
        }
    }

    /**
     * 轻量 APK 完整性校验：文件存在、非空、且为可读 zip 含 AndroidManifest.xml。
     * 仅用于预装阶段快速发现损坏包；深层签名/SHA-256 校验由运行时 ModuleLoader 负责。
     */
    private fun validatePreinstalledApk(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            java.util.zip.ZipFile(file).use { zf ->
                zf.getEntry("AndroidManifest.xml") != null
            }
        } catch (e: Exception) {
            Log.w("App", "[preinstall] APK 校验异常: ${file.name} - ${e.message}")
            false
        }
    }

    override fun onCreate() {
        val processRole = AppProcessPolicy.classify(packageName, AppProcessIdentity.currentName(this))
        hostInitializationEnabled = AppProcessPolicy.shouldInitializeHost(processRole)
        if (!hostInitializationEnabled) {
            // Keep Application/Hilt lifecycle intact, but do not share host recovery state,
            // module extraction, Flutter, downloads or Activity callbacks with :adb.
            super.onCreate()
            Log.i("App", "ADB process initialized without host services")
            return
        }
        if (processRole == AppProcessPolicy.Role.UNKNOWN) {
            Log.w("App", "Process name unavailable; preserving legacy host initialization")
        }

        // 尽早安装全局崩溃处理器：置于 super.onCreate() 之前，
        // 使 Hilt/DI 初始化期崩溃也能被 UncaughtExceptionHandler 捕获并驱动恢复模式。
        CrashHandler.init(this) { _, throwable ->
            // R1: 恢复页自身崩溃不计入触发信号，避免"恢复→崩溃→再恢复"死循环。
            // 仅当异常栈帧不来自 recovery 包时，才记录真实崩溃。
            if (!isFromRecovery(throwable)) {
                CrashDetector.recordCrash(this@App)
            }
        }

        super.onCreate()

        CrashDetector.markAppStart(this)
        applyLanguage()
        applyTheme()

        // 异步提取模块以防止阻塞主线程启动
        applicationScope.launch(Dispatchers.IO) {
            extractPreinstalledModules()
        }

        SecureOkHttpFactory.setHosts(BuildConfig.MODULE_HOST, !BuildConfig.DEBUG)

        if (BuildConfig.ENABLE_FLUTTER_MODULE_STORE) {
            runCatching {
                com.gamecenter.app.modules.bridge.FlutterStoreEngineManager.getOrCreate(this)
            }.onFailure { error ->
                Log.e("App", "Flutter module store prewarm failed; legacy store remains available", error)
            }
        }

        // Batch 21: 初始化下载指标收集器
        com.gamecenter.app.modules.DownloadMetricsCollector.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyColorScheme(activity)
                // Feature B (SETTINGS_ENHANCE): 应用用户选择的字号
                if (BuildConfig.SETTINGS_ENHANCE) {
                    com.gamecenter.app.settings.FontSizeHelper.apply(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        Log.i("App", "模块系统已初始化")

        // 统一加载器（core:module-host）失败清理/回滚回调注入：
        // 校验失败删除损坏文件并清理安装状态（SP），加载失败按事务策略回滚 last_good。
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(
            onVerifyFailure = { manifest, file ->
                runCatching {
                    if (file.exists()) file.delete()
                    com.gamecenter.app.modules.ModuleManager.removeInstalledModulePublic(this, manifest.id)
                    Log.w("App", "加载器校验失败已清理: ${manifest.id}")
                }
            },
            onLoadFailureRollback = { manifest ->
                runCatching {
                    if (com.gamecenter.app.BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
                        com.gamecenter.app.modules.store.TransactionInstaller.rollback(this, manifest)
                        Log.w("App", "加载失败已回滚: ${manifest.id}")
                    }
                }
            }
        )

        // P3-14 (OFFLINE_MODULE_PRELOAD): 初始化模块预下载管理器（WiFi 环境下自动预下载）
        runCatching {
            com.gamecenter.app.modules.ModulePreDownloadManager.init(this)
        }.onFailure { e ->
            Log.w("App", "模块预下载管理器初始化失败: ${e.message}")
        }
    }

    /**
     * R1: 判断异常是否源自恢复模式包。若恢复页自身崩溃也被计入触发信号，
     * 会导致"进入恢复 → 恢复页崩溃 → 再进入恢复"的死循环。
     * 仅检查栈帧包名，避免对恢复流程内部的崩溃计数。
     */
    private fun isFromRecovery(throwable: Throwable): Boolean {
        val recoveryPkg = "com.gamecenter.app.recovery"
        // 1) 直接类名前缀匹配（最快路径）
        if (throwable.javaClass.name.startsWith(recoveryPkg)) return true
        // 2) 遍历栈帧，任一帧属于 recovery 包即视为源自恢复流程
        for (element in throwable.stackTrace) {
            if (element.className.startsWith(recoveryPkg)) return true
        }
        // 3) 递归检查 cause，避免包装异常绕过
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            if (cause.javaClass.name.startsWith(recoveryPkg)) return true
            for (element in cause.stackTrace) {
                if (element.className.startsWith(recoveryPkg)) return true
            }
            cause = cause.cause
            depth++
        }
        return false
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

    override fun onTerminate() {
        super.onTerminate()
        if (!hostInitializationEnabled) return
        // Batch 21 改进：应用终止时 flush 下载指标，避免丢失未达 buffer 上限的数据
        com.gamecenter.app.modules.DownloadMetricsCollector.flush()
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
