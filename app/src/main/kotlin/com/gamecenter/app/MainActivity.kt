package com.gamecenter.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.gamecenter.app.modules.BottomNavigationManager
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

    private var navController: NavController? = null
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

        // P4: 提前加载内置核心模块，确保导航贡献可用
        if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
            loadBuiltInCoreModules()
        }

        if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION && navHostFragment != null) {
            // P4: 使用模块贡献动态构建底部导航
            bottomNavigationManager = BottomNavigationManager(
                this,
                navHostFragment.childFragmentManager,
                R.id.nav_host_fragment,
                navView
            )
            setupP4DynamicNavigation(navView)
        } else if (navHostFragment != null) {
            // 旧逻辑：使用 Navigation 组件
            navController = navHostFragment.navController
            val keepStateNavigator = KeepStateNavigator(
                this, navHostFragment.childFragmentManager, R.id.nav_host_fragment
            )
            navController?.navigatorProvider?.addNavigator(keepStateNavigator)
            navController?.setGraph(R.navigation.mobile_navigation)
            setupDynamicNavigation(navView)
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
     * 注册全局返回拦截：当当前 destination 不是"游戏大厅"时，按系统返回键或
     * 边缘滑动 predictive back 手势会切回游戏大厅，而不是退出应用。
     *
     * 原因：使用 KeepStateNavigator 时，navigate() 不会把目标 destination
     * 真正 push 到系统 back stack 上，所以系统返回键会跳过 NavController
     * 直接走 Activity.finish()。这里手动拦截，保证用户体验。
     */
    private fun setupBackToGamesHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // P4: 使用动态导航管理器处理返回
                if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
                    val manager = bottomNavigationManager ?: return
                    val currentId = manager.getCurrentContributionId()
                    if (currentId == null || currentId == "games_hall") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                        return
                    }
                    // 用户可以重排 tab，必须按稳定 ID 返回游戏大厅，不能假设它排第一。
                    manager.selectContribution("games_hall")
                    return
                }

                val controller = navController ?: return
                val currentId = controller.currentDestination?.id
                if (currentId == null || currentId == R.id.navigation_games) {
                    // 已在游戏大厅（或未知）：让系统处理（退出应用）
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }
                // 其他 destination：先尝试 popBackStack，失败再直接 navigate 回游戏大厅
                if (!controller.popBackStack(R.id.navigation_games, false)) {
                    controller.navigate(R.id.navigation_games)
                }
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

    /**
     * P4: 预加载内置核心模块，确保它们的导航贡献被注册到 ModuleRegistry。
     */
    private fun loadBuiltInCoreModules() {
        val coreModules = listOf("games_hall", "browser")
        for (moduleId in coreModules) {
            try {
                ModuleManager.loadModule(this, moduleId)
                Log.d("MainActivity", "内置核心模块已加载: $moduleId")
            } catch (e: Exception) {
                Log.w("MainActivity", "内置核心模块加载失败（不影响主流程）: $moduleId", e)
            }
        }
    }

    private fun setupDynamicNavigation(navView: BottomNavigationView) {
        val installedIds = ModuleManager.getInstalledModuleIds(this)
        val menu = navView.menu
        menu.clear()

        menu.add(Menu.NONE, R.id.navigation_games, Menu.NONE, R.string.nav_games)
            .setIcon(R.drawable.ic_games)

        if (installedIds.contains("browser")) {
            menu.add(Menu.NONE, R.id.navigation_browser, Menu.NONE, R.string.nav_browser)
                .setIcon(R.drawable.ic_browser)
        }

        if (installedIds.contains("tools")) {
            menu.add(Menu.NONE, R.id.navigation_tools, Menu.NONE, R.string.nav_tools)
                .setIcon(R.drawable.ic_tools)
        }

        if (installedIds.contains("ai")) {
            menu.add(Menu.NONE, R.id.navigation_ai, Menu.NONE, R.string.nav_ai)
                .setIcon(R.drawable.ic_ai)
        }

        if (installedIds.contains("vpn")) {
            menu.add(Menu.NONE, R.id.navigation_vpn, Menu.NONE, R.string.nav_vpn)
                .setIcon(R.drawable.ic_vpn)
        }

        if (BuildConfig.ENABLE_WRONGBOOK && installedIds.contains("wrongbook")) {
            // wrongbook 已从底部导航移除（最多 6 个 item 限制），改由 GamesFragment 头像菜单进入
        }

        if (BuildConfig.PROFILE_FRAGMENT) {
            menu.add(Menu.NONE, R.id.navigation_profile, Menu.NONE, R.string.nav_profile)
                .setIcon(R.drawable.ic_nav_profile)
        }

        navView.setOnItemSelectedListener { item ->
            navController?.navigate(item.itemId)
            // Batch 6 (NAV_ACTIVE_ANIM): 选中 item 图标缩放动画
            if (BuildConfig.NAV_ACTIVE_ANIM) {
                animateNavItemIcon(navView, item.itemId)
            }
            true
        }

        // 关键修复：点击已选中 item 时也触发导航。
        // 场景：进入错题本（不在底部导航 menu 中）后，"游戏大厅" item 会被
        // BottomNavigationView 默认置为 selected。此时点击它触发的是
        // setOnItemReselectedListener 而不是 setOnItemSelectedListener，
        // 若回调为空则用户卡死在错题本无法返回游戏大厅。
        navView.setOnItemReselectedListener { item ->
            navController?.navigate(item.itemId)
            if (BuildConfig.NAV_ACTIVE_ANIM) {
                animateNavItemIcon(navView, item.itemId)
            }
        }

        navController?.currentDestination?.let { destination ->
            val currentId = destination.id
            if (menu.findItem(currentId) != null) {
                menu.findItem(currentId).isChecked = true
            }
        }
    }

    private fun downloadCoreModulesIfMissing() {
        val coreModules = arrayOf("games_hall", "browser")
        for (moduleId in coreModules) {
            if (!ModuleManager.isModuleInstalled(this, moduleId)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    ModuleManager.downloadModule(this, moduleId, object : com.gamecenter.app.modules.ModuleDownloader.Callback {
                        override fun onProgress(id: String, downloaded: Long, total: Long, speed: Long) {}
                        override fun onComplete(id: String, file: File) {
                            runOnUiThread {
                                val navView = findViewById<BottomNavigationView>(R.id.nav_view)
                                if (navView != null) {
                                    if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
                                        setupP4DynamicNavigation(navView)
                                    } else {
                                        setupDynamicNavigation(navView)
                                    }
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

        if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
            val manager = bottomNavigationManager ?: return
            // 自定义排序后索引会变化，因此始终通过稳定贡献 ID 跳转。
            if (!manager.selectContribution(tab)) manager.selectContribution("games_hall")
            intent?.removeExtra(EXTRA_NAV_TAB)
            return
        }

        val destId = when (tab) {
            "games_hall" -> R.id.navigation_games
            "browser" -> R.id.navigation_browser
            "tools" -> R.id.navigation_tools
            "ai" -> R.id.navigation_ai
            "vpn" -> R.id.navigation_vpn
            "wrongbook" -> R.id.navigation_wrongbook
            "profile" -> R.id.navigation_profile
            else -> return
        }
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null && navView.menu.findItem(destId) != null) {
            navView.selectedItemId = destId
        } else {
            navController?.navigate(destId)
        }
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
     *
     * 实现：BottomNavigationMenuView 是 BottomNavigationView 的第 0 个子 view，
     * 它的子 view 是 BottomNavigationItemView，每个 item 的第 0 个 ImageView 子 view 即图标。
     * 通过 menu 中 itemId 找到 position，定位到对应的 item view 并对其图标应用缩放动画。
     */
    private fun animateNavItemIcon(navView: BottomNavigationView, itemId: Int) {
        try {
            val menuView = navView.getChildAt(0) as? ViewGroup ?: return
            // 找到 itemId 在 menu 中的位置（仅计算可见 item）
            val menu = navView.menu
            var position = -1
            for (i in 0 until menu.size()) {
                if (menu.getItem(i).itemId == itemId) {
                    position++
                    break
                }
                position++
            }
            if (position < 0 || position >= menuView.childCount) return
            val itemView = menuView.getChildAt(position) ?: return
            val iconView = findFirstImageView(itemView) ?: return
            val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.25f, 1f)
            val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.25f, 1f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 220
                interpolator = DecelerateInterpolator()
                start()
            }
        } catch (e: Exception) {
            Log.d("MainActivity", "导航动画播放失败", e)
        }
    }

    /** 在 view 树中找到第一个 ImageView（用于定位 BottomNavigationItemView 的图标 view）。 */
    private fun findFirstImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                val found = findFirstImageView(child)
                if (found != null) return found
            }
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null) {
            if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
                setupP4DynamicNavigation(navView)
            } else {
                setupDynamicNavigation(navView)
            }
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
            if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
                setupP4DynamicNavigation(navView)
            } else {
                setupDynamicNavigation(navView)
            }
        }
        handleNavTabIntent()
        // Batch 9-4 (NAV_BADGE_UNREAD): 每次回到前台刷新未读徽章
        if (BuildConfig.NAV_BADGE_UNREAD && navView != null) {
            NavBadgeHelper.updateBadges(this, navView)
        }
    }

    private fun applySystemBarInsets() {
        val container = findViewById<View>(R.id.container)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (container == null) return

        val left = container.paddingLeft
        val top = container.paddingTop
        val right = container.paddingRight
        val bottom = container.paddingBottom
        val navLeft = navView?.paddingLeft ?: 0
        val navTop = navView?.paddingTop ?: 0
        val navRight = navView?.paddingRight ?: 0
        val navBottom = navView?.paddingBottom ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            container.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom)
            navView?.setPadding(navLeft, navTop, navRight, navBottom + bars.bottom)
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
        // P4: 动态导航模式下不使用 NavController
        if (BuildConfig.ENABLE_P4_DYNAMIC_NAVIGATION) {
            return super.onSupportNavigateUp()
        }
        return navController?.navigateUp() ?: false || super.onSupportNavigateUp()
    }

    companion object {
        const val EXTRA_NAV_TAB = "extra_nav_tab"

        private fun formatFileSize(size: Long): String {
            if (size < 1024) return "$size B"
            if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
