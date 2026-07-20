package com.gamecenter.app.features

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
 * P4 动态游戏大厅 Fragment。
 *
 * 从 ModuleRegistry 收集 NavigationSlot.GAMES_HALL 贡献，
 * 以卡片形式展示已安装/已加载的游戏模块入口。
 */
class DynamicGamesHallFragment : Fragment() {

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

        val contributions = ModuleRegistry.getNavigationContributionsForSlot(ctx, NavigationSlot.GAMES_HALL)
            .filter { it.contribution.isEnabled() }
            .sortedBy { it.contribution.getOrder() }

        if (contributions.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "暂无游戏模块，请前往模块商店下载。"
                textSize = 16f
                setTextColor(Color.rgb(97, 97, 97))
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, dp(32))
            })
            return
        }

        content.addView(TextView(ctx).apply {
            text = "游戏大厅"
            textSize = 24f
            setTextColor(Color.rgb(33, 33, 33))
            setPadding(0, dp(8), 0, dp(16))
        })

        contributions.chunked(2).forEach { rowContributions ->
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
                    LinearLayout.LayoutParams(0, dp(120), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(8))
                    }
                )
            }
            if (rowContributions.size == 1) {
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
            setPadding(dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(224, 224, 224))
            }
            isClickable = true
            isFocusable = true

            addView(
                TextView(ctx).apply {
                    text = title
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(33, 33, 33))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
