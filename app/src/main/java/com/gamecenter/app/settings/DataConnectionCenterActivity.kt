package com.gamecenter.app.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gamecenter.app.R
import com.gamecenter.app.core.common.DataConnectionCenter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/**
 * 数据与连接中心（#23.3）。
 *
 * 聚合展示用户设备上与数据/网络相关的 6 项状态：
 * 1. 已授权权限
 * 2. 已启用联网模块（隐私卡涉及云端）
 * 3. 最近同步状态
 * 4. 下载记录
 * 5. 缓存大小
 * 6. 本地数据汇总
 *
 * 支持一键导出（#23.4）和一键清除缓存（#23.5）。
 *
 * 入口：SettingsActivity → "数据与连接中心"。
 */
class DataConnectionCenterActivity : AppCompatActivity() {

    private lateinit var provider: DataConnectionCenterProvider
    private lateinit var container: LinearLayout
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var scrollView: View
    private lateinit var actionBar: View
    private lateinit var btnExport: MaterialButton
    private lateinit var btnClearCache: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_connection_center)

        provider = DataConnectionCenterProvider(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        container = findViewById(R.id.content_container)
        progressBar = findViewById(R.id.progress_bar)
        scrollView = findViewById(R.id.scroll_view)
        actionBar = findViewById(R.id.action_bar)
        btnExport = findViewById(R.id.btn_export)
        btnClearCache = findViewById(R.id.btn_clear_cache)

        btnExport.setOnClickListener { onExportClicked() }
        btnClearCache.setOnClickListener { onClearCacheClicked() }

        loadData()
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        actionBar.visibility = View.GONE

        lifecycleScope.launch {
            val snapshot = provider.aggregate()
            renderSnapshot(snapshot)
            progressBar.visibility = View.GONE
            scrollView.visibility = View.VISIBLE
            actionBar.visibility = View.VISIBLE
        }
    }

    private fun renderSnapshot(snapshot: DataConnectionCenter) {
        container.removeAllViews()

        if (!snapshot.hasContent) {
            container.addView(buildEmptyHint())
            return
        }

        // 摘要卡片
        container.addView(buildSummaryCard(snapshot))

        // 已授权权限
        container.addView(
            buildSection(
                title = getString(R.string.data_center_section_permissions),
                items = if (snapshot.grantedPermissions.isEmpty()) {
                    listOf(getString(R.string.data_center_no_permissions))
                } else {
                    snapshot.grantedPermissions.map { perm ->
                        val dangerTag = if (perm.isDangerous) " [${getString(R.string.data_center_permission_dangerous)}]" else ""
                        val label = if (perm.label.isNotEmpty()) perm.label else perm.permission
                        "$label$dangerTag"
                    }
                }
            )
        )

        // 已启用联网模块
        container.addView(
            buildSection(
                title = getString(R.string.data_center_section_network_modules),
                items = if (snapshot.networkModules.isEmpty()) {
                    listOf(getString(R.string.data_center_no_modules))
                } else {
                    snapshot.networkModules.map { m ->
                        val domains = if (m.networkDomains.isNotEmpty()) m.networkDomains.joinToString("、") else "—"
                        "${m.moduleName}\n  域名：$domains\n  云端数据：${m.cloudData.ifEmpty { "—" }}"
                    }
                }
            )
        )

        // 最近同步
        container.addView(buildSyncCard(snapshot))

        // 下载记录
        container.addView(
            buildSection(
                title = getString(R.string.data_center_section_downloads),
                items = if (snapshot.downloadRecords.isEmpty()) {
                    listOf(getString(R.string.data_center_no_downloads))
                } else {
                    snapshot.downloadRecords.map { r ->
                        val progress = if (r.progressPercent >= 0) " ${r.progressPercent}%" else ""
                        "${r.moduleName}：${r.state}$progress"
                    }
                }
            )
        )

        // 本地数据
        container.addView(
            buildSection(
                title = getString(R.string.data_center_section_local_data),
                items = if (snapshot.localDataSummary.isEmpty()) {
                    listOf(getString(R.string.data_center_no_local_data))
                } else {
                    snapshot.localDataSummary.map { d ->
                        val retention = if (d.retentionPeriod.isNotEmpty()) "\n  保存期限：${d.retentionPeriod}" else ""
                        val deletion = if (d.deletionMethod.isNotEmpty()) "\n  删除方式：${d.deletionMethod}" else ""
                        "${d.moduleName}\n  ${d.localData}$retention$deletion"
                    }
                }
            )
        )
    }

    /** 构建摘要卡片：缓存大小 + 网络模块数 + 活跃下载数 */
    private fun buildSummaryCard(snapshot: DataConnectionCenter): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = getColorAttr(com.google.android.material.R.attr.colorOutline)
            setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurface))
            elevation = 0f
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.data_center_summary)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }

        val cacheView = buildSummaryRow(
            getString(R.string.data_center_cache_size),
            Formatter.formatFileSize(this, snapshot.cacheSizeBytes)
        )
        val networkView = buildSummaryRow(
            getString(R.string.data_center_network_modules),
            snapshot.networkModuleCount.toString()
        )
        val downloadView = buildSummaryRow(
            getString(R.string.data_center_active_downloads),
            snapshot.activeDownloadCount.toString()
        )

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(View(this@DataConnectionCenterActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                )
            })
            addView(cacheView)
            addView(networkView)
            addView(downloadView)
        }
        card.addView(inner)
        return card
    }

    private fun buildSummaryRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val labelView = TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val valueView = TextView(this).apply {
            text = value
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        row.addView(labelView)
        row.addView(valueView)
        return row
    }

    /** 构建同步状态卡片 */
    private fun buildSyncCard(snapshot: DataConnectionCenter): View {
        val sync = snapshot.lastSync
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = getColorAttr(com.google.android.material.R.attr.colorOutline)
            setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            elevation = 0f
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.data_center_section_sync)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
        }

        val statusText = when {
            !sync.isConfigured -> getString(R.string.data_center_sync_not_configured)
            sync.lastSyncTime <= 0L -> getString(R.string.data_center_sync_never)
            sync.hasConflict -> getString(R.string.data_center_sync_conflict)
            sync.lastSyncStatus == "failure" -> getString(R.string.data_center_sync_failure)
            sync.lastSyncStatus == "success" -> getString(R.string.data_center_sync_success)
            else -> "—"
        }

        val timeText = if (sync.lastSyncTime > 0L) {
            com.gamecenter.app.cloudsync.CloudSyncManager.formatTime(sync.lastSyncTime)
        } else {
            getString(R.string.data_center_sync_never)
        }

        val autoText = if (sync.autoSyncEnabled) {
            getString(R.string.data_center_sync_auto_on)
        } else {
            getString(R.string.data_center_sync_auto_off)
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(View(this@DataConnectionCenterActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                )
            })
            addView(buildSummaryRow("状态", statusText).apply {
                (getChildAt(0) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                (getChildAt(1) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            })
            addView(buildSummaryRow("时间", timeText).apply {
                (getChildAt(0) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                (getChildAt(1) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            })
            addView(buildSummaryRow("自动同步", autoText).apply {
                (getChildAt(0) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                (getChildAt(1) as TextView).setTextColor(
                    getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            })
        }
        card.addView(inner)
        return card
    }

    /** 构建通用 section：标题 + 项目列表 */
    private fun buildSection(title: String, items: List<String>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        container.addView(titleView)

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            strokeColor = getColorAttr(com.google.android.material.R.attr.colorOutline)
            setCardBackgroundColor(getColorAttr(com.google.android.material.R.attr.colorSurface))
            elevation = 0f
            setContentPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        items.forEachIndexed { index, item ->
            val itemText = TextView(this).apply {
                text = "• $item"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurface))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = dp(8) }
            }
            inner.addView(itemText)
        }
        card.addView(inner)
        container.addView(card)
        return container
    }

    private fun buildEmptyHint(): View {
        return TextView(this).apply {
            text = getString(R.string.data_center_empty)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(48), 0, dp(48))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
    }

    private fun onExportClicked() {
        lifecycleScope.launch {
            runCatching { provider.exportToJson() }
                .onSuccess { file ->
                    Toast.makeText(
                        this@DataConnectionCenterActivity,
                        getString(R.string.data_center_export_success, file.absolutePath),
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure { e ->
                    Toast.makeText(
                        this@DataConnectionCenterActivity,
                        getString(R.string.data_center_export_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun onClearCacheClicked() {
        AlertDialog.Builder(this)
            .setTitle(R.string.data_center_clear_cache)
            .setMessage(R.string.data_center_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.data_center_clear_cache) { _, _ ->
                lifecycleScope.launch {
                    runCatching { provider.clearCache() }
                        .onSuccess { bytes ->
                            Toast.makeText(
                                this@DataConnectionCenterActivity,
                                getString(
                                    R.string.data_center_clear_success,
                                    Formatter.formatFileSize(this@DataConnectionCenterActivity, bytes)
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            loadData() // 重新加载
                        }
                        .onFailure { e ->
                            Toast.makeText(
                                this@DataConnectionCenterActivity,
                                getString(R.string.data_center_clear_failed, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun getColorAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
