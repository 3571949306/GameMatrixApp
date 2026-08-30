package com.gamecenter.app.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.gamecenter.app.MainActivity
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.RecentGamesManager
import com.gamecenter.app.games.StatsActivity
import com.gamecenter.app.games.achievement.AchievementCenterActivity
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.gamecenter.app.games.achievement.StreakTracker
import com.gamecenter.app.games.ui.GameLauncherHelper as Launcher
import com.gamecenter.app.modules.ModuleStoreActivity
import com.gamecenter.app.settings.AppSettingsDialog
import com.gamecenter.app.ui.GameDetailBottomSheet
import com.gamecenter.app.ui.GameLongPressMenu
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * 游戏库主页（docs/游戏中心主页面重做执行计划_2026-08-30.md）：
 * 顶栏（标题/设置/溢出）→ 搜索 → 筛选 → 单一 RecyclerView（继续/最近/全部游戏）。
 * 数据经 GameHomeViewModel（真源聚合），颜色经 GameHomeThemeResolver（用户 Scheme）。
 */
class GameLibraryFragment : Fragment(), GameHomeAdapter.Callbacks {

    private val viewModel: GameHomeViewModel by viewModels()
    private var adapter: GameHomeAdapter? = null

    private lateinit var root: View
    private lateinit var searchInput: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView

    /** 程序化 setText 时抑制 TextWatcher 回环（filters observer ↔ setQuery） */
    private var suppressSearchWatcher = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = inflater.inflate(R.layout.fragment_game_library, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchInput = view.findViewById(R.id.et_game_library_search)
        chipGroup = view.findViewById(R.id.chip_group_game_filters)
        recyclerView = view.findViewById(R.id.rv_game_library)

        adapter = GameHomeAdapter(
            iconLoader = ::loadIcon,
            nameLoader = { entry -> com.gamecenter.app.home.GameDisplayNames.gameName(requireContext(), entry) },
            callbacks = this
        )
        recyclerView.layoutManager = GridLayoutManager(
            requireContext(), 4
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val item = adapter?.currentList?.getOrNull(position) ?: return 1
                    val spans = GameHomeLayoutPolicy.spanCount(
                        (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt(),
                        resources.configuration.fontScale
                    )
                    return GameHomeLayoutPolicy.spanSizeFor(item, spans)
                }
            }
        }
        recyclerView.adapter = adapter

        view.findViewById<ImageButton>(R.id.btn_game_library_settings).setOnClickListener {
            AppSettingsDialog(this, { (activity as? MainActivity)?.checkUpdate(true) }, null).show()
        }
        view.findViewById<ImageButton>(R.id.btn_game_library_overflow).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressSearchWatcher) return
                viewModel.setQuery(s?.toString() ?: "")
            }
        })

        viewModel.filters.observe(viewLifecycleOwner) { filters ->
            // 仅在内容不同时回写，且抑制 watcher 回环（否则 setText→watcher→setQuery→observe 死循环 ANR）
            if (searchInput.text?.toString() != filters.query) {
                suppressSearchWatcher = true
                searchInput.setText(filters.query)
                searchInput.setSelection(filters.query.length)
                suppressSearchWatcher = false
            }
        }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderChips(state)
            applyPalette()
            adapter?.submitList(state.items)
        }
    }

    override fun onResume() {
        super.onResume()
        // §4.2：模块安装返回后先注册已安装模块，再刷新游戏库
        com.gamecenter.app.modules.ModuleManager
            .registerInstalledGameModules(requireContext())
        viewModel.refresh() // 筛选与滚动由 ViewModel/SavedState 恢复
    }

    // ===== GameHomeAdapter.Callbacks =====

    override fun onContinue(entry: GameRegistry.Entry) = launchGame(entry)

    override fun onRecent(entry: GameRegistry.Entry) = launchGame(entry)

    override fun onTileClick(entry: GameRegistry.Entry) {
        if (com.gamecenter.app.BuildConfig.GAME_DETAIL_SHEET) {
            GameDetailBottomSheet(
                entry,
                { e -> launchGame(e) },
                { viewModel.refresh() },
                { _, _ -> viewModel.refresh() }
            ).show(childFragmentManager, "GameDetailBottomSheet")
        } else {
            launchGame(entry)
        }
    }

    override fun onTileLongPress(entry: GameRegistry.Entry, anchor: View) {
        GameLongPressMenu.show(requireContext(), anchor, entry) { viewModel.refresh() }
    }

    override fun onEmptyAction() {
        // 两个空状态动作分别是“浏览模块商店”与“清除搜索”，按当前状态分发
        val state = viewModel.uiState.value ?: return
        val empty = state.items.filterIsInstance<com.gamecenter.app.home.GameHomeItem.EmptyState>()
            .firstOrNull() ?: return
        when (empty.message) {
            getString(R.string.game_library_empty) ->
                startActivity(android.content.Intent(requireContext(), ModuleStoreActivity::class.java))
            getString(R.string.game_library_no_search_results) -> {
                searchInput.setText("")
                viewModel.setQuery("")
            }
        }
    }

    override fun onToggleRecentExpanded() = viewModel.toggleRecentExpanded()

    // ===== 内部 =====

    /** 统一启动入口（数据推进链与旧 GamesFragment 完全一致）。 */
    private fun launchGame(entry: GameRegistry.Entry) {
        val ctx = requireContext()
        RecentGamesManager.getInstance(ctx).recordPlay(entry.id)
        StreakTracker.getInstance(ctx).recordActivity()
        DailyChallengeManager.getInstance(ctx).recordGamePlayed(entry.id, false)
        val ok = Launcher.launchGameWithDialog(ctx, entry.id)
        if (!ok) {
            Toast.makeText(
                ctx,
                getString(R.string.error_game_launch_failed_format, entry.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun renderChips(state: GameHomeUiState) {
        chipGroup.removeAllViews()
        state.categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.name
                isCheckable = true
                isChecked = cat.selected
                chipStrokeWidth = 0f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) viewModel.setCategory(cat.key)
            }
            chipGroup.addView(chip)
        }
        val fav = Chip(requireContext()).apply {
            text = getString(R.string.game_library_favorites_filter)
            isCheckable = true
            isChecked = state.filters.favoritesOnly
            chipStrokeWidth = 0f
        }
        fav.setOnCheckedChangeListener { _, checked ->
            viewModel.setFavoritesOnly(checked)
        }
        chipGroup.addView(fav)
        applyPaletteToChips()
    }

    private fun applyPaletteToChips() {
        val palette = currentPalette() ?: return
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            val selected = chip.isChecked
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                if (selected) palette.primary else palette.surfaceVariant
            )
            chip.setTextColor(
                if (selected) palette.onPrimary else palette.onSurfaceVariant
            )
        }
    }

    private fun currentPalette(): GameHomeThemeResolver.GameHomePalette? {
        val index = com.gamecenter.app.SettingsManager.getInstance(requireContext())
            .getColorSchemeIndex()
        val scheme = com.gamecenter.app.ColorSchemeManager.getScheme(index)
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return GameHomeThemeResolver.resolve(scheme, isDark)
    }

    private fun applyPalette() {
        val p = currentPalette() ?: return
        root.setBackgroundColor(p.background)
        root.findViewById<TextView>(R.id.tv_game_library_title)?.setTextColor(p.onSurface)
        root.findViewById<View>(R.id.game_library_toolbar)?.setBackgroundColor(p.surface)
        searchInput.setBackgroundColor(p.surfaceVariant)
        searchInput.setTextColor(p.onSurface)
        searchInput.setHintTextColor(p.onSurfaceVariant)
        adapter?.setPalette(p)
        applyPaletteToChips()
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, getString(R.string.module_store))
        popup.menu.add(0, 2, 1, getString(R.string.game_statistics))
        popup.menu.add(0, 3, 2, getString(R.string.profile_my_achievements))
        popup.menu.add(0, 4, 3, getString(R.string.nav_wrongbook))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(
                    android.content.Intent(requireContext(), ModuleStoreActivity::class.java)
                )
                2 -> startActivity(
                    android.content.Intent(requireContext(), StatsActivity::class.java)
                )
                3 -> startActivity(
                    android.content.Intent(requireContext(), AchievementCenterActivity::class.java)
                )
                4 -> (activity as? MainActivity)?.openModuleFromMenu("wrongbook")
            }
            true
        }
        popup.show()
    }

    private fun loadIcon(entry: GameRegistry.Entry) = runCatching {
        if (entry.iconRes != 0) ContextCompat.getDrawable(requireContext(), entry.iconRes)
        else null
    }.getOrNull() ?: runCatching {
        requireContext().applicationInfo.loadIcon(requireContext().packageManager)
    }.getOrNull()
}
