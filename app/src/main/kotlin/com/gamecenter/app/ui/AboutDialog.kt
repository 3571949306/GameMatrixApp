package com.gamecenter.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.modules.DownloadMetricsCollector

/**
 * 关于对话框（Feature: SETTINGS_ABOUT_PAGE）。
 *
 * 展示应用版本、构建通道、GitHub 仓库链接、开源许可，
 * 提供复制版本信息与检查更新入口。
 *
 * 通过 [BuildConfig.SETTINGS_ABOUT_PAGE] 控制（调用方在 AppSettingsDialog 中判断）。
 */
class AboutDialog(
    private val onCheckUpdate: OnCheckUpdateListener? = null
) : DialogFragment() {

    /** 检查更新回调。Java 友好 SAM 接口。 */
    fun interface OnCheckUpdateListener {
        fun onCheckUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明背景兜底，让布局自身的 ?attr/colorSurface 生效
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx: Context = view.context

        val channelLabel = when (BuildConfig.VERSION_CHANNEL.lowercase()) {
            "beta" -> ctx.getString(R.string.about_channel_beta)
            "stable" -> ctx.getString(R.string.about_channel_stable)
            else -> BuildConfig.VERSION_CHANNEL.ifEmpty {
                ctx.getString(R.string.about_channel_stable)
            }
        }
        val versionDisplay = ctx.getString(
            R.string.about_version_display_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        view.findViewById<TextView>(R.id.tv_about_version).text = versionDisplay
        view.findViewById<TextView>(R.id.tv_about_version_name).text =
            ctx.getString(R.string.about_version_label) + "：" + BuildConfig.VERSION_NAME
        view.findViewById<TextView>(R.id.tv_about_version_code).text =
            ctx.getString(R.string.about_version_code_label) + "：" + BuildConfig.VERSION_CODE
        view.findViewById<TextView>(R.id.tv_about_channel).text =
            ctx.getString(R.string.about_build_channel_label) + "：" + channelLabel

        // GitHub
        view.findViewById<View>(R.id.card_about_github).setOnClickListener {
            openInBrowser(ctx, BuildConfig.GITHUB_RELEASES_URL)
        }

        // 开源许可
        view.findViewById<View>(R.id.card_about_licenses).setOnClickListener {
            showLicensesDialog(ctx)
        }

        // 复制版本信息
        view.findViewById<View>(R.id.btn_about_copy).setOnClickListener {
            copyVersionInfo(ctx, versionDisplay)
        }

        // Batch 21 改进：长按"复制版本信息"按钮显示下载指标 summary（仅 DEBUG）
        view.findViewById<View>(R.id.btn_about_copy).setOnLongClickListener {
            if (BuildConfig.DEBUG) {
                showDownloadMetricsDialog(ctx)
                true
            } else {
                Toast.makeText(ctx, R.string.about_download_metrics_hint, Toast.LENGTH_SHORT).show()
                false
            }
        }

        // 检查更新
        view.findViewById<View>(R.id.btn_about_check_update).setOnClickListener {
            dismissAllowingStateLoss()
            onCheckUpdate?.onCheckUpdate()
        }
    }

    private fun openInBrowser(ctx: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
        }
    }

    private fun showLicensesDialog(ctx: Context) {
        AlertDialog.Builder(ctx)
            .setTitle(R.string.about_open_source_licenses_title)
            .setMessage(ctx.getString(R.string.about_open_source_licenses_body))
            .setPositiveButton(R.string.settings_ok, null)
            .show()
    }

    private fun copyVersionInfo(ctx: Context, versionDisplay: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(ctx.getString(R.string.clipboard_label_version), versionDisplay)
            cm?.setPrimaryClip(clip)
            Toast.makeText(ctx, R.string.about_copy_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, versionDisplay, Toast.LENGTH_LONG).show()
        }
    }

    /** Batch 21 改进：显示下载指标汇总（仅 DEBUG 模式可达） */
    private fun showDownloadMetricsDialog(ctx: Context) {
        val summary = DownloadMetricsCollector.summary()
        AlertDialog.Builder(ctx)
            .setTitle(R.string.about_download_metrics_title)
            .setMessage(summary)
            .setPositiveButton(R.string.settings_ok, null)
            .show()
    }
}
