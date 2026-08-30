package com.gamecenter.app.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gamecenter.app.R

/**
 * 游戏库主页（docs/游戏中心主页面重做执行计划_2026-08-30.md）。
 *
 * Phase 1 骨架：最小可辨识页面（标题 + 搜索占位 + 空状态提示）。
 * Phase 2+ 按 §4 信息架构逐层实现：继续玩 → 最近玩过 → 全部游戏。
 * 配色必须经 [GameHomeThemeResolver]（Phase 3），禁止固定色值。
 */
class GameLibraryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_game_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tv_game_library_title)?.text =
            getString(R.string.game_library_title)
        view.findViewById<TextView>(R.id.tv_game_library_search_hint)?.hint =
            getString(R.string.game_library_search_hint)
        view.findViewById<TextView>(R.id.tv_game_library_empty)?.text =
            getString(R.string.game_library_empty)
    }
}
