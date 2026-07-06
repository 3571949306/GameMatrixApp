package com.gamecenter.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.recovery.CrashDetector
import com.gamecenter.app.update.DownloadState
import com.gamecenter.app.update.UpdateCheckState
import com.gamecenter.app.update.UpdateInfo
import com.gamecenter.app.update.UpdateViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.lang.ref.WeakReference

/**
 * 应用的主界面（入口页面）。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var navController: NavController? = null
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
        if (navHostFragment != null) {
            navController = navHostFragment.navController
            val keepStateNavigator = KeepStateNavigator(
                this, navHostFragment.childFragmentManager, R.id.nav_host_fragment
            )
            navController?.navigatorProvider?.addNavigator(keepStateNavigator)
            navController?.setGraph(R.navigation.mobile_navigation)
        }

        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        setupDynamicNavigation(navView)

        handleNavTabIntent()

        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        observeUpdateStates()
        scheduleAutoUpdateCheck()
        downloadCoreModulesIfMissing()
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
            menu.add(Menu.NONE, R.id.navigation_wrongbook, Menu.NONE, R.string.nav_wrongbook)
                .setIcon(R.drawable.ic_nav_wrongbook)
        }

        navView.setOnItemSelectedListener { item ->
            navController?.navigate(item.itemId)
            true
        }

        navView.setOnItemReselectedListener {}

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
                                    setupDynamicNavigation(navView)
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
        val destId = when (tab) {
            "games_hall" -> R.id.navigation_games
            "browser" -> R.id.navigation_browser
            "tools" -> R.id.navigation_tools
            "ai" -> R.id.navigation_ai
            "vpn" -> R.id.navigation_vpn
            "wrongbook" -> R.id.navigation_wrongbook
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null) {
            setupDynamicNavigation(navView)
        }
        handleNavTabIntent()
    }

    override fun onResume() {
        super.onResume()
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)
        if (navView != null) {
            setupDynamicNavigation(navView)
        }
        handleNavTabIntent()
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
            append(String.format(getString(R.string.update_version), info.versionName))
            append("\n")
            append(getString(R.string.update_channel_label, info.channelLabel))
            append("\n")
            append(getString(R.string.update_version_code, info.versionCode))
            append("\n")
            append(String.format(getString(R.string.update_size), info.fileSizeFormatted))
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
        return navController?.navigateUp() ?: false || super.onSupportNavigateUp()
    }

    companion object {
        const val EXTRA_NAV_TAB = "extra_nav_tab"

        private fun formatFileSize(size: Long): String {
            if (size < 1024) return "$size B"
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0)
            return String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
