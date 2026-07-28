package com.gamecenter.app.features

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.gamecenter.app.R
import com.gamecenter.app.core.common.ModuleRegistry
import com.gamecenter.app.core.common.NavigationSlot

/**
 * P4 动态工具区 Fragment。
 *
 * 从 ModuleRegistry 收集 NavigationSlot.TOOLS_GRID 贡献，
 * 以卡片网格形式展示已安装/已加载的工具模块入口。
 *
 * 说明：此 Fragment 仅作为 DestinationKind.TOOLS 的兜底实现，
 * 用于展示通过 TOOLS_GRID 贡献注册的工具模块卡片（如 AI/VPN/错题本等）。
 * 内置 28 个工具卡片由 ToolsModuleEntryPoint 的 BOTTOM_NAV 贡献提供，
 * 由 [com.gamecenter.app.fragments.ToolsFragment] 承载，不在此处显示。
 *
 * 主题：支持浅色/深色主题，颜色根据 [isNightMode] 动态切换。
 * 本地化：所有文本使用字符串资源。
 */
class DynamicToolsFragment : Fragment() {

    private lateinit var content: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext())
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderContributions()
    }

    override fun onResume() {
        super.onResume()
        renderContributions()
    }

    private fun renderContributions() {
        val ctx = requireContext()
        content.removeAllViews()

        val contributions = ModuleRegistry.getNavigationContributionsForSlot(ctx, NavigationSlot.TOOLS_GRID)
            .filter { it.contribution.isEnabled() }
            .sortedBy { it.contribution.getOrder() }

        if (contributions.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = getString(R.string.dynamic_tools_empty_state)
                textSize = 16f
                setTextColor(colorOnSurface())
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, dp(32))
            })
            return
        }

        content.addView(TextView(ctx).apply {
            text = getString(R.string.tools_title)
            textSize = 24f
            setTextColor(colorOnSurface())
            setPadding(0, dp(8), 0, dp(16))
        })

        contributions.chunked(3).forEach { rowContributions ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            content.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            rowContributions.forEach { entry ->
                val card = createContributionCard(entry)
                row.addView(
                    card,
                    LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(8))
                    }
                )
            }
            val placeholders = 3 - rowContributions.size
            repeat(placeholders) {
                row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }

    private fun createContributionCard(
        entry: ModuleRegistry.NavigationContributionEntry
    ): View {
        val ctx = requireContext()
        val title = entry.contribution.getTitle(ctx)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8))
            background = GradientDrawable().apply {
                setColor(colorSurface())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), colorSurfaceStroke())
            }
            isClickable = true
            isFocusable = true

            addView(
                TextView(ctx).apply {
                    text = title
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(colorOnSurface())
                    setPadding(0, dp(8), 0, dp(4))
                }
            )

            setOnClickListener {
                openContribution(entry)
            }
        }
    }

    private fun openContribution(entry: ModuleRegistry.NavigationContributionEntry) {
        val ctx = requireContext()
        val fragment = ModuleRegistry.createFragmentForContribution(ctx, entry) ?: return
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment, entry.contribution.getContributionId())
            .addToBackStack(null)
            .commit()
    }

    // ===== 主题感知颜色 =====

    private fun isNightMode(): Boolean {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 卡片背景色：深色模式 #1E1E2E，浅色模式 WHITE */
    private fun colorSurface(): Int =
        if (isNightMode()) Color.parseColor("#1E1E2E") else Color.WHITE

    /** 卡片边框色：深色模式 #3A3A4A，浅色模式 #E0E0E0 */
    private fun colorSurfaceStroke(): Int =
        if (isNightMode()) Color.parseColor("#3A3A4A") else Color.rgb(224, 224, 224)

    /** 主文字色：深色模式 #E4E6F0，浅色模式 #212121 */
    private fun colorOnSurface(): Int =
        if (isNightMode()) Color.parseColor("#E4E6F0") else Color.rgb(33, 33, 33)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
