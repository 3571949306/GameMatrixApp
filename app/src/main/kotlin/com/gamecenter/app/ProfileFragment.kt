package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.StatsActivity
import com.gamecenter.app.games.achievement.AchievementCenterActivity
import com.gamecenter.app.games.achievement.StreakTracker
import com.gamecenter.app.modules.ModuleStoreActivity
import com.gamecenter.app.settings.AppSettingsDialog
import com.gamecenter.app.ui.DataBackupHelper
import java.util.Locale

/**
 * 个人中心 Fragment：展示玩家战绩、收藏与快捷入口。
 *
 * 受 feature flag [BuildConfig.PROFILE_FRAGMENT] 保护，关闭时 [onViewCreated] 直接返回，
 * 不会绑定数据也不会崩溃。该 Fragment 注册在底部导航的 keep_state_fragment 中，
 * 与 MainActivity 同包以便导航器按类名实例化。
 */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val tag = "ProfileFragment"

    // Batch 11-2 (DATA_BACKUP_RESTORE): SAF launcher —— 必须在 Fragment 构造期注册
    private val exportDataLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            handleExportResult(uri)
        }
    private val importDataLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            handleImportResult(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!BuildConfig.PROFILE_FRAGMENT) return

        // 游客模式昵称
        view.findViewById<TextView>(R.id.tv_profile_name).text =
            getString(R.string.profile_guest_name)

        bindStats(view)
        bindFavorites(view)
        bindQuickEntries(view)
    }

    override fun onResume() {
        super.onResume()
        // 用户从其他页面返回时数据可能已更新，重新刷新
        if (!BuildConfig.PROFILE_FRAGMENT) return
        view?.let {
            bindStats(it)
            bindFavorites(it)
        }
    }

    /** 绑定连胜与总对局数据。 */
    private fun bindStats(view: View) {
        val tracker = StreakTracker.getInstance(requireContext())
        view.findViewById<TextView>(R.id.tv_profile_streak_value).text =
            tracker.currentStreak.toString()
        view.findViewById<TextView>(R.id.tv_profile_best_streak_value).text =
            tracker.bestStreak.toString()
        view.findViewById<TextView>(R.id.tv_profile_total_games_value).text =
            tracker.totalGames.toString()
    }

    /** 绑定收藏数量；无收藏时显示提示文案。 */
    private fun bindFavorites(view: View) {
        val count = GameUsageStore(requireContext()).getFavoriteIds().size
        val tv = view.findViewById<TextView>(R.id.tv_profile_favorites_count)
        tv.text = if (count == 0) {
            getString(R.string.profile_no_favorites)
        } else {
            getString(R.string.profile_favorites_count_format, count)
        }
    }

    /** 绑定收藏卡片与四个快捷入口的点击事件。 */
    private fun bindQuickEntries(view: View) {
        // 收藏卡片：无收藏时提示，有收藏时简单 Toast 提示前往游戏大厅查看
        view.findViewById<View>(R.id.card_favorites).setOnClickListener {
            val count = GameUsageStore(requireContext()).getFavoriteIds().size
            val resId = if (count == 0) {
                R.string.profile_no_favorites
            } else {
                R.string.profile_my_favorites
            }
            Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btn_profile_stats).setOnClickListener {
            try {
                startActivity(Intent(requireContext(), StatsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_profile_achievements).setOnClickListener {
            try {
                startActivity(Intent(requireContext(), AchievementCenterActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_profile_module_store).setOnClickListener {
            try {
                startActivity(Intent(requireContext(), ModuleStoreActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_profile_settings).setOnClickListener {
            try {
                openSettings()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Batch 11-2 (DATA_BACKUP_RESTORE) ====================

    /** 打开设置对话框，注入 SAF 回调。 */
    private fun openSettings() {
        AppSettingsDialog(
            this,
            null,
            null,
            { exportDataLauncher.launch(DataBackupHelper.defaultFilename()) },
            { importDataLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        ).show()
    }

    /** SAF 导出回调：在后台线程写入 JSON。 */
    private fun handleExportResult(uri: Uri?) {
        if (uri == null) return // 用户取消
        val ctx = requireContext()
        Thread {
            ctx.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                try {
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fos ->
                        val bytes = DataBackupHelper.exportToJson(ctx, fos)
                        toastOnUi(ctx, ctx.getString(R.string.data_backup_export_success, humanSize(bytes)))
                    }
                } catch (e: Exception) {
                    Log.e(tag, "导出数据失败", e)
                    toastOnUi(ctx, ctx.getString(R.string.data_backup_export_failed,
                        e.message ?: e.javaClass.simpleName))
                }
            } ?: run {
                toastOnUi(ctx, ctx.getString(R.string.data_backup_export_failed,
                    "openFileDescriptor returned null"))
            }
        }.start()
    }

    /** SAF 导入回调：在后台线程读取 JSON 并覆盖 SharedPreferences。 */
    private fun handleImportResult(uri: Uri?) {
        if (uri == null) return // 用户取消
        val ctx = requireContext()
        Thread {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    val count = DataBackupHelper.importFromJson(ctx, ins)
                    toastOnUi(ctx, ctx.getString(R.string.data_backup_import_success, count))
                    // 重建 Activity 让所有界面元素刷新（主题/语言/声音设置可能变化）
                    activity?.runOnUiThread {
                        try {
                            activity?.recreate()
                        } catch (e: Exception) {
                            Log.e(tag, "导入后刷新 UI 失败", e)
                        }
                    }
                } ?: run {
                    toastOnUi(ctx, ctx.getString(R.string.data_backup_import_failed,
                        "openInputStream returned null"))
                }
            } catch (e: Exception) {
                Log.e(tag, "导入数据失败", e)
                toastOnUi(ctx, ctx.getString(R.string.data_backup_import_failed,
                    e.message ?: e.javaClass.simpleName))
            }
        }.start()
    }

    /** 在 UI 线程显示 Toast。 */
    private fun toastOnUi(ctx: Context, text: String) {
        activity?.runOnUiThread {
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
        }
    }

    /** 字节数转人类可读字符串。 */
    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024))
    }
}
