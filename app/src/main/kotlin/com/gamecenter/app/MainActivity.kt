package com.gamecenter.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.gamecenter.app.modules.BottomNavigationManager
import com.gamecenter.app.modules.CoreModulePreloader
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.recovery.CrashDetector
import com.gamecenter.app.update.DownloadState
import com.gamecenter.app.update.UpdateCheckState
import com.gamecenter.app.update.UpdateInfo
import com.gamecenter.app.update.UpdateViewModel
import com.gamecenter.app.ui.NavBadgeHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * 应用的主界面（入口页面）。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var bottomNavigationManager: com.gamecenter.app.modules.BottomNavigationManager? = null
    private var permissionHelper: PermissionHelper? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var updateViewModel: UpdateViewModel? = null
    private var updateDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (CrashDetector.shouldLaunchRecovery(this)) {
            startActivity(Intent(this, com.gamecenter.app.recovery.RecoveryActivity::class.java))
            finish()
            return
        }

        CrashDetector.markAppRunning(this)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        // 分发架构 v2：移动网络提示弹窗（8s）+ 下载源测速（Step1 避让式调度：
        // 后台等待"启动≥15s 且 6s 内无页签切换"再测，随后预取商店首屏数据）
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                showMobileNetworkNoticeIfNeeded()
                Log.d("DLSelector", "entry: mobile=" +
                    com.gamecenter.app.modules.store.DownloadSourceSelector.isMobileNetwork(this) +
                    ", popup shown once")
            } catch (t: Throwable) {
                android.util.Log.e("DLSelector", "mobile notice failed", t)
            }
        }, 8_000L)
        Thread {
            runCatching {
                com.gamecenter.app.modules.store.DownloadSourceSelector
                    .scheduleEntryProbeIfNeeded(applicationContext)
            }
        }.start()

        permissionHelper = PermissionHelper(this)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val grantResults = BooleanArray(result.size)
            var i = 0
            for (granted in result.values) {
                grantResults[i++] = granted ?: false
            }
            permissionHelper?.onPermissionsResult(grantResults)
        }

        if (permissionHelper?.isFirstLaunch == true) {
            permissionHelper?.showPermissionDialog(permissionLauncher)
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)

        // P4: 使用模块贡献动态构建底部导航（统一路径，双轨已收敛）。
        // 核心模块（games_hall/browser/tools）的 Dex 加载已移至 SplashActivity 后台预加载
        // （CoreModulePreloader），不再在主线程同步阻塞；未就绪时后台补加载后回主线程构建。
        if (navHostFragment != null) {
            bottomNavigationManager = BottomNavigationManager(
                this,
                navHostFragment.childFragmentManager,
                R.id.nav_host_fragment,
                navView
            )
            if (CoreModulePreloader.isReady) {
                setupP4DynamicNavigation(navView)
            } else {
                CoreModulePreloader.ensureLoadedAsync(this) {
                    runOnUiThread { setupP4DynamicNavigation(navView) }
                }
            }
        }

        handleNavTabIntent()

        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        observeUpdateStates()
        scheduleAutoUpdateCheck()
        downloadCoreModulesIfMissing()

        // Batch 9-1 (GAME_LONG_PRESS_MENU): 处理桌面快捷方式启动 Intent
        if (BuildConfig.GAME_LONG_PRESS_MENU) {
            handleGameShortcutIntent(intent)
        }

        // Batch 9-4 (NAV_BADGE_UNREAD): 初始化底部导航未读徽章
        if (BuildConfig.NAV_BADGE_UNREAD) {
            NavBadgeHelper.updateBadges(this, navView)
        }

        // 返回手势/返回键统一拦截：在非"游戏大厅" destination 时，按返回或边缘滑动
        // 自动切回游戏大厅，避免 KeepStateNavigator 场景下直接退出应用。
        // 适用 destination：错题本 / browser / tools / ai / vpn / profile。
        setupBackToGamesHandler()
    }

    /**
     * 注册全局返回拦截：当当前显示的不是"游戏大厅"时，按系统返回键或
     * 边缘滑动 predictive back 手势会切回游戏大厅，而不是退出应用。
     *
     * 原因：动态导航使用 show/hide 切换 Fragment，系统 back stack 不含页面切换记录，
     * 系统返回键会直接走 Activity.finish()。这里手动拦截，保证用户体验。
     */
    private fun setupBackToGamesHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 动态导航：用户可以重排 tab，必须按稳定贡献 ID 返回游戏大厅，不能假设它排第一。
                val manager = bottomNavigationManager ?: return
                val currentId = manager.getCurrentContributionId()
                if (currentId == null || currentId == "games_hall") {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }
                manager.selectContribution("games_hall")
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    /**
     * P4: 使用模块贡献动态构建底部导航。
     */
    private fun setupP4DynamicNavigation(navView: BottomNavigationView) {
        val manager = bottomNavigationManager ?: return
        manager.refreshNavigation()

        // 默认选中游戏大厅
        if (manager.getCurrentContributionId() == null) {
            manager.selectContribution("games_hall")
        }
    }

    private fun downloadCoreModulesIfMissing() {
        val coreModules = arrayOf("games_hall", "browser")
        for (moduleId in coreModules) {
            if (!ModuleManager.isModuleInstalled(this, moduleId)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    // P0 内存泄漏修复：postDelayed lambda 捕获 this@MainActivity，
                    // 若 Activity 在 1000ms 内销毁，会导致 MainActivity 及其 View 树泄漏。
                    // 与同文件 handleGameShortcutIntent 保持一致的生命周期检查。
                    if (isFinishing || isDestroyed) return@postDelayed
                    ModuleManager.downloadModule(this, moduleId, object : com.gamecenter.app.modules.ModuleDownloader.Callback {
                        override fun onProgress(id: String, downloaded: Long, total: Long, speed: Long) {}
                        override fun onComplete(id: String, file: File) {
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                val navView = findViewById<BottomNavigationView>(R.id.nav_view)
                                if (navView != null) {
                                    setupP4DynamicNavigation(navView)
                                }
                            }
                        }
                        override fun onError(id: String, message: String) {
                            Log.e("MainActivity", "Core module download failed: $id - $message")
                        }
                        override fun onError(id: String, errorCode: Int, message: String) {
                            onError(id, message)
                        }
                        override fun onSourceSwitch(id: String, sourceIndex: Int, url: String) {}
                    })
                }, 1000)
            }
        }
    }

    private fun handleNavTabIntent() {
        val tab = intent?.getStringExtra(EXTRA_NAV_TAB) ?: return
        val manager = bottomNavigationManager ?: return
        // 自定义排序后索引会变化，因此始终通过稳定贡献 ID 跳转。
        if (!manager.selectContribution(tab)) manager.selectContribution("games_hall")
        intent?.removeExtra(EXTRA_NAV_TAB)
    }

    /**
     * Batch 9-1 (GAME_LONG_PRESS_MENU): 处理桌面快捷方式启动 Intent。
     * 如果是从游戏桌面快捷方式启动，直接拉起对应游戏。
     */
    private fun handleGameShortcutIntent(intent: Intent?) {
        val gameId = com.gamecenter.app.ui.GameLongPressMenu.extractGameIdFromIntent(intent)
            ?: return
        // 仅消费一次，避免旋转屏幕重复启动
        intent?.removeExtra("extra_long_press_game_id")
        intent?.removeExtra("extra_long_press_game_name")
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            com.gamecenter.app.games.ui.GameLauncherHelper.launchGameWithDialog(this, gameId)
        }, 600L)
    }

    /**
     * Batch 6 (NAV_ACTIVE_ANIM): 底部导航选中 item 图标缩放动画。
     * 已下沉至 BottomNavigationManager（P4 动态导航统一实现）。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null) {
            setupP4DynamicNavigation(navView)
        }
        handleNavTabIntent()
        // Batch 9-1 (GAME_LONG_PRESS_MENU): 顶部再启动时也要处理桌面快捷方式 Intent
        if (BuildConfig.GAME_LONG_PRESS_MENU) {
            handleGameShortcutIntent(intent)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // R4: 用户主动离开（Home/最近任务）→ 标记上一次会话为优雅退出，
        // 避免"连续快速开关 3 次"被启发式误判为连续闪退。
        CrashDetector.markGracefulExit(this)
    }

    override fun onResume() {
        super.onResume()
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null) {
            setupP4DynamicNavigation(navView)
        }
        handleNavTabIntent()
        // Batch 9-4 (NAV_BADGE_UNREAD): 每次回到前台刷新未读徽章
        if (BuildConfig.NAV_BADGE_UNREAD && navView != null) {
            NavBadgeHelper.updateBadges(this, navView)
        }
    }

    private fun applySystemBarInsets() {
        val container = findViewById<View>(R.id.container)
        if (container == null) return

        val left = container.paddingLeft
        val top = container.paddingTop
        val right = container.paddingRight
        val bottom = container.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            container.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun observeUpdateStates() {
        updateViewModel?.updateCheckState?.observe(this) { state ->
            if (isFinishing || isDestroyed) return@observe

            when (state) {
                is UpdateCheckState.Available -> showUpdateDialog(state.info)
                is UpdateCheckState.NotAvailable -> {
                    Toast.makeText(this, R.string.update_no_update, Toast.LENGTH_SHORT).show()
                }
                is UpdateCheckState.BetaOnly -> showBetaOnlyNoticeDialog(state.info)
                is UpdateCheckState.BetaBlocked -> {
                    Toast.makeText(this, R.string.update_beta_only_toast, Toast.LENGTH_LONG).show()
                }
                is UpdateCheckState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        updateViewModel?.downloadState?.observe(this) { state ->
            if (isFinishing || isDestroyed) return@observe

            when (state) {
                is DownloadState.Downloading -> {
                    if (progressDialog?.isShowing == true) {
                        updateProgressDialog(state.downloaded, state.total)
                    }
                }
                is DownloadState.Verifying -> {
                    if (progressDialog?.isShowing == true) {
                        updateProgressVerifying()
                    }
                }
                is DownloadState.Completed -> {
                    dismissProgressDialog()
                    showInstallDialog(state.apkFile)
                }
                is DownloadState.Error -> {
                    dismissProgressDialog()
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is DownloadState.Cancelled -> {
                    dismissProgressDialog()
                }
                else -> {}
            }
        }
    }

    private fun scheduleAutoUpdateCheck() {
        val app = application as? App ?: return
        Handler(Looper.getMainLooper()).postDelayed(
            SafeUpdateCheckRunnable(this, app), 2000
        )
    }

    private class SafeUpdateCheckRunnable(
        activity: MainActivity,
        private val app: App
    ) : Runnable {
        private val activityRef = WeakReference(activity)

        override fun run() {
            val activity = activityRef.get() ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            val sm = SettingsManager.getInstance(activity)
            if (app.shouldAutoCheckUpdate() && sm.isAutoCheckUpdate) {
                activity.updateViewModel?.checkUpdate(activity, false)
            }
        }
    }

    fun checkUpdate(showToast: Boolean) {
        updateViewModel?.checkUpdate(this, showToast)
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        if (isFinishing || isDestroyed) return

        val title = if (info.isForceUpdate) {
            "${getString(R.string.update_force)} - ${getString(R.string.update_new_version)}"
        } else {
            getString(R.string.update_new_version)
        }

        val message = StringBuilder().apply {
            append(String.format(Locale.getDefault(), getString(R.string.update_version), info.versionName))
            append("\n")
            append(getString(R.string.update_channel_label, info.channelLabel))
            append("\n")
            append(getString(R.string.update_version_code, info.versionCode))
            append("\n")
            append(String.format(Locale.getDefault(), getString(R.string.update_size), info.fileSizeFormatted))
            append("\n\n")
            append(getString(R.string.update_changelog))
            append("\n")
            append(info.changelog)
        }.toString()

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                startDownloadWithProgressDialog(info)
            }

        if (!info.isForceUpdate) {
            builder.setNegativeButton(R.string.update_later) { dialog, _ -> dialog.dismiss() }
        } else {
            builder.setCancelable(false)
        }

        updateDialog = builder.create()
        updateDialog?.show()
    }

    private fun startDownloadWithProgressDialog(info: UpdateInfo) {
        if (isFinishing || isDestroyed) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        progressBar.max = 100
        progressBar.setProgress(0, true)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_downloading)
            .setView(dialogView)
            .setCancelable(!info.isForceUpdate)

        progressDialog = builder.create()
        progressDialog?.show()

        updateViewModel?.startDownload(this, info)
    }

    private fun updateProgressDialog(downloaded: Long, total: Long) {
        val dialog = progressDialog ?: return
        if (!dialog.isShowing) return
        val decorView = dialog.window?.decorView ?: return

        val progressBar = decorView.findViewById<ProgressBar>(R.id.progress_bar)
        val tvProgressPercent = decorView.findViewById<TextView>(R.id.tv_progress_percent)
        val tvProgressSize = decorView.findViewById<TextView>(R.id.tv_progress_size)

        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        progressBar?.setProgress(percent, true)
        tvProgressPercent?.text = "$percent%"
        tvProgressSize?.text = formatDownloadProgress(downloaded, total)
    }

    private fun updateProgressVerifying() {
        val dialog = progressDialog ?: return
        if (!dialog.isShowing) return
        val decorView = dialog.window?.decorView ?: return

        val tvProgressPercent = decorView.findViewById<TextView>(R.id.tv_progress_percent)
        tvProgressPercent?.text = getString(R.string.update_verifying)
    }

    private fun showBetaOnlyNoticeDialog(info: UpdateInfo) {
        if (isFinishing || isDestroyed) return

        val lastStableName = if (info.lastStableVersionName.isEmpty()) {
            getString(R.string.update_last_stable_default)
        } else {
            info.lastStableVersionName
        }

        val message = StringBuilder().apply {
            append(
                getString(
                    R.string.update_beta_only_msg,
                    info.versionName, info.versionCode,
                    info.localVersionCode, lastStableName
                )
            )
            if (info.lastStableVersionCode > 0) {
                append(getString(R.string.update_beta_only_stable_code, info.lastStableVersionCode))
            }
            append(getString(R.string.update_beta_only_hint))
        }.toString()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_beta_only_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_beta_only_enable) { _, _ ->
                updateViewModel?.enableBetaAndRecheck(this)
            }
            .setNegativeButton(R.string.update_beta_only_wait, null)
            .show()
    }

    private fun showInstallDialog(apkFile: File) {
        if (isFinishing || isDestroyed) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_new_version)
            .setMessage(R.string.update_install_prompt)
            .setPositiveButton(R.string.update_install) { _, _ -> updateViewModel?.installApk(this) }
            .setNeutralButton(R.string.update_open_directory) { _, _ ->
                updateViewModel?.openDownloadDirectory(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UpdateViewModel.REQUEST_INSTALL_PERMISSION) {
            updateViewModel?.onInstallPermissionResult(this, resultCode)
        }
    }

    override fun onDestroy() {
        dismissProgressDialog()
        updateDialog?.let {
            if (it.isShowing) {
                try {
                    it.dismiss()
                } catch (e: Exception) {
                    Log.d("MainActivity", "Dialog dismiss failed", e)
                }
            }
        }
        updateDialog = null
        super.onDestroy()
    }

    private fun dismissProgressDialog() {
        progressDialog?.let {
            if (it.isShowing) {
                try {
                    it.dismiss()
                } catch (e: Exception) {
                    Log.d("MainActivity", "Dialog dismiss failed", e)
                }
            }
        }
        progressDialog = null
    }

    private fun formatDownloadProgress(downloaded: Long, total: Long): String {
        val downloadedStr = formatFileSize(downloaded)
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        if (total > 0) {
            val totalStr = formatFileSize(total)
            return "$downloadedStr / $totalStr ($percent%)"
        }
        return "$downloadedStr ($percent%)"
    }

    override fun onSupportNavigateUp(): Boolean {
        // 动态导航模式下不使用 NavController
        return super.onSupportNavigateUp()
    }

    /**
     * 打开不在底部导航菜单中的模块（如错题本）。
     * 由 GamesFragment 头像菜单等入口调用。
     */
    fun openModuleFromMenu(moduleId: String): Boolean {
        val manager = bottomNavigationManager ?: return false
        return manager.openModule(moduleId)
    }

    /**
     * 分发架构 v2：移动网络提示（屏幕正中小卡片，3 秒自动消失、点击可关、每进程一次）。
     * 仅在"自动选择下载源"主开关开启时出现——关闭了自动选源则该提示无意义。
     */
    private fun showMobileNetworkNoticeIfNeeded() {
        if (mobileNoticeShown.getAndSet(true)) return
        if (!com.gamecenter.app.SettingsManager.getInstance(this).isDlAutoSelect()) return
        if (!com.gamecenter.app.modules.store.DownloadSourceSelector.isMobileNetwork(this)) return
        runCatching {
            val card = android.widget.TextView(this).apply {
                text = getString(com.gamecenter.app.R.string.dl_notice_mobile_network)
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundResource(android.R.drawable.dialog_frame)
                setPadding(48, 32, 48, 32)
                gravity = android.view.Gravity.CENTER
                isClickable = true
                setOnClickListener { (parent as? android.view.ViewGroup)?.removeView(this) }
            }
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
            (window.decorView as android.view.ViewGroup).addView(card, params)
            card.postDelayed({ (card.parent as? android.view.ViewGroup)?.removeView(card) }, 3_000L)
        }
    }

    companion object {
        /** 移动网络提示：每进程只显示一次 */
        private val mobileNoticeShown = java.util.concurrent.atomic.AtomicBoolean(false)

        const val EXTRA_NAV_TAB = "extra_nav_tab"

        private fun formatFileSize(size: Long): String {
            if (size < 1024) return "$size B"
            if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
