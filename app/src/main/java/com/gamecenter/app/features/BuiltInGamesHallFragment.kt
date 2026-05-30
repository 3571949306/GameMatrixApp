package com.gamecenter.app.features

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.ui.GameLauncherHelper
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.ModuleStoreActivity
import com.gamecenter.app.settings.AppSettingsDialog

class BuiltInGamesHallFragment : Fragment() {

    private lateinit var content: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext())
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        scrollView.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scrollView
    }

    override fun onResume() {
        super.onResume()
        renderGames()
    }

    private fun renderGames() {
        val ctx = requireContext()
        ModuleManager.registerInstalledGameModules(ctx.applicationContext)
        content.removeAllViews()

        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(Button(ctx).apply {
            text = "模块商店"
            setOnClickListener { startActivity(Intent(ctx, ModuleStoreActivity::class.java)) }
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        actionRow.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = "设置"
            setPadding(dp(12))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showSettingsDialog() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(8)
        })
        content.addView(actionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp(8))
        })

        GameRegistry.getCategories(ctx).forEach { category ->
            content.addView(TextView(ctx).apply {
                text = category.name
                textSize = 20f
                setTextColor(Color.rgb(33, 33, 33))
                setPadding(0, dp(16), 0, dp(8))
            })

            category.games.chunked(2).forEach { rowGames ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                content.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                rowGames.forEach { entry ->
                    row.addView(createGameCard(entry), LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(8))
                    })
                }
                if (rowGames.size == 1) {
                    row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
        }
    }

    private fun createGameCard(entry: GameRegistry.Entry): View {
        val ctx = requireContext()
        val title = displayName(entry)
        val desc = displayDescription(entry)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(224, 224, 224))
            }
            isClickable = true
            isFocusable = true
            minimumHeight = dp(148)

            addView(ImageView(ctx).apply {
                setImageResource(entry.iconRes)
                contentDescription = title
            }, LinearLayout.LayoutParams(dp(48), dp(48)))

            addView(TextView(ctx).apply {
                text = title
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(33, 33, 33))
                setPadding(0, dp(8), 0, dp(4))
            })

            addView(TextView(ctx).apply {
                text = desc
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(Color.rgb(97, 97, 97))
            })

            setOnClickListener { openGame(entry) }
        }
    }

    private fun openGame(entry: GameRegistry.Entry) {
        val ctx = requireContext()
        GameLauncherHelper.launchGameWithDialog(ctx, entry.id)
    }

    private fun showSettingsDialog() {
        AppSettingsDialog(
            this,
            { (activity as? com.gamecenter.app.MainActivity)?.checkUpdate(true) },
            null
        ).show()
    }

    private fun displayName(entry: GameRegistry.Entry): String = when (entry.id) {
        "gomoku" -> "五子棋"
        "doudizhu" -> "斗地主"
        "game_2048" -> "2048"
        "chinesechess" -> "中国象棋"
        "klotski" -> "华容道"
        else -> entry.name
    }

    private fun displayDescription(entry: GameRegistry.Entry): String = when (entry.id) {
        "gomoku" -> "经典五子棋人机对战。"
        "doudizhu" -> "经典三人扑克对战。"
        "game_2048" -> "经典数字合并游戏。"
        "chinesechess" -> "经典中国象棋。"
        "klotski" -> "经典滑块益智游戏。"
        else -> entry.desc
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
