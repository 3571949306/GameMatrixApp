package com.gamecenter.app.modules

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import android.util.Log
import java.io.File

/**
 * 模块商店主页（Batch 20: Hero Banner + 三栏统计 + 分类 tab 图标 + 详情 BottomSheet + 防抖搜索）。
 */
class ModuleStoreActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var skeletonContainer: LinearLayout
    private lateinit var emptyContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var statsBar: LinearLayout
    private lateinit var statTotalCount: TextView
    private lateinit var statInstalledCount: TextView
    private lateinit var statUpdateCount: TextView
    private lateinit var tvModuleStats: TextView
    private lateinit var btnUpdateAll: MaterialButton
    private lateinit var btnRetry: MaterialButton
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var categoryTabLayout: TabLayout
    private lateinit var subCategoryTabsContainer: LinearLayout
    private lateinit var subCategoryContainer: HorizontalScrollView
    private lateinit var adapter: ModuleAdapter

    // Hero Banner（Batch 21: 多卡片轮播）
    private lateinit var heroBannerContainer: View
    private lateinit var heroViewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var heroIndicator: LinearLayout
    private var heroAdapter: HeroBannerAdapter? = null
    private val heroHandler = Handler(Looper.getMainLooper())
    private val heroAutoScrollRunnable = object : Runnable {
        override fun run() {
            val count = heroAdapter?.itemCount ?: 0
            if (count > 1) {
                val next = (heroViewPager.currentItem + 1) % count
                heroViewPager.setCurrentItem(next, true)
                heroHandler.postDelayed(this, HERO_AUTO_SCROLL_INTERVAL_MS)
            }
        }
    }

    private var allModules: List<ModuleManifest> = emptyList()
    private var currentCategory: String = CATEGORY_GAMES
    private var currentSubCategory: String = SUBCATEGORY_ALL
    private var searchKeyword: String = ""
    private var isGridView = false
    private var currentSortMode: Int = SORT_BY_NAME

    // Batch 21: 筛选状态
    private var filterState: Int = FILTER_STATE_ALL
    private var filterSize: Int = FILTER_SIZE_ALL
    private var filterVersion: Int = FILTER_VERSION_ALL

    // Batch 21: 搜索历史
    private lateinit var searchHistoryContainer: View
    private lateinit var searchHistoryChips: ChipGroup
    private val searchHistoryPrefs by lazy {
        getSharedPreferences("module_search_history", MODE_PRIVATE)
    }

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var lastProgressUpdateMs: Long = 0
    private val subCategoryChips = mutableListOf<Chip>()

    companion object {
        const val CATEGORY_GAMES = "game"
        const val CATEGORY_BROWSER = "browser"
        const val CATEGORY_TOOLS = "tools"
        const val CATEGORY_AI = "ai"
        const val CATEGORY_VPN = "vpn"
        const val CATEGORY_INSTALLED = "installed"

        private val CATEGORIES = listOf(
            Triple(CATEGORY_GAMES, R.string.store_category_games, R.drawable.ic_games),
            Triple(CATEGORY_BROWSER, R.string.store_category_browser, R.drawable.ic_browser),
            Triple(CATEGORY_TOOLS, R.string.store_category_tools, R.drawable.ic_tools),
            Triple(CATEGORY_AI, R.string.store_category_ai, R.drawable.ic_ai),
            Triple(CATEGORY_VPN, R.string.store_category_vpn, R.drawable.ic_vpn),
            Triple(CATEGORY_INSTALLED, R.string.module_category_installed, R.drawable.ic_checkin_calendar)
        )

        const val SUBCATEGORY_ALL = "all"
        const val SUBCATEGORY_PUZZLE = "puzzle"
        const val SUBCATEGORY_CASUAL = "casual"
        const val SUBCATEGORY_CLASSICS = "classics"

        private val SUBCATEGORIES = listOf(
            Pair(SUBCATEGORY_ALL, R.string.module_subcategory_all),
            Pair(SUBCATEGORY_PUZZLE, R.string.store_subcategory_puzzle),
            Pair(SUBCATEGORY_CASUAL, R.string.store_subcategory_casual),
            Pair(SUBCATEGORY_CLASSICS, R.string.store_subcategory_classics)
        )

        private val GAME_SUBCATEGORIES = setOf(SUBCATEGORY_PUZZLE, SUBCATEGORY_CASUAL, SUBCATEGORY_CLASSICS)

        const val SORT_BY_NAME = 0
        const val SORT_BY_SIZE = 1
        const val SORT_BY_VERSION = 2
        const val SORT_BY_DOWNLOADS = 3
        const val SORT_BY_RATING = 4

        // Batch 21: 筛选常量
        const val FILTER_STATE_ALL = 0
        const val FILTER_STATE_INSTALLED = 1
        const val FILTER_STATE_NOT_INSTALLED = 2
        const val FILTER_STATE_UPDATABLE = 3

        const val FILTER_SIZE_ALL = 0
        const val FILTER_SIZE_SMALL = 1
        const val FILTER_SIZE_MEDIUM = 2
        const val FILTER_SIZE_LARGE = 3

        const val FILTER_VERSION_ALL = 0
        const val FILTER_VERSION_ABOVE_V1 = 1
        const val FILTER_VERSION_ABOVE_V2 = 2

        /** Hero Banner 自动轮播间隔 */
        const val HERO_AUTO_SCROLL_INTERVAL_MS = 4000L
        /** Hero Banner 最多展示几张 */
        const val HERO_MAX_ITEMS = 5

        /** 搜索历史最大保留条数 */
        const val SEARCH_HISTORY_MAX = 5
        const val SEARCH_HISTORY_KEY = "history"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_module_store)

        val toolbar = findViewById<MaterialToolbar>(R.id.moduleToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            handleToolbarAction(item.itemId)
        }

        recyclerView = findViewById(R.id.moduleRecyclerView)
        skeletonContainer = findViewById(R.id.moduleSkeletonContainer)
        emptyContainer = findViewById(R.id.moduleEmptyContainer)
        emptyText = findViewById(R.id.moduleEmptyText)
        errorContainer = findViewById(R.id.moduleErrorContainer)
        errorText = findViewById(R.id.moduleErrorText)
        statsBar = findViewById(R.id.statsBar)
        statTotalCount = findViewById(R.id.statTotalCount)
        statInstalledCount = findViewById(R.id.statInstalledCount)
        statUpdateCount = findViewById(R.id.statUpdateCount)
        tvModuleStats = findViewById(R.id.tvModuleStats)
        btnUpdateAll = findViewById(R.id.btnUpdateAll)
        btnRetry = findViewById(R.id.btnRetry)
        fabRefresh = findViewById(R.id.moduleFabRefresh)
        categoryTabLayout = findViewById(R.id.moduleCategoryTabs)
        subCategoryTabsContainer = findViewById(R.id.moduleSubCategoryTabs)
        subCategoryContainer = findViewById(R.id.moduleSubCategoryContainer)

        heroBannerContainer = findViewById(R.id.heroBannerContainer)
        heroViewPager = findViewById(R.id.heroViewPager)
        heroIndicator = findViewById(R.id.heroIndicator)

        // Batch 21: 搜索历史视图绑定
        searchHistoryContainer = findViewById(R.id.searchHistoryContainer)
        searchHistoryChips = findViewById(R.id.searchHistoryChips)
        findViewById<MaterialButton>(R.id.btnSearchHistoryClear).setOnClickListener {
            clearSearchHistory()
        }
        updateSearchHistoryChips()

        btnRetry.setOnClickListener { refreshModules() }
        btnUpdateAll.setOnClickListener { updateAllAvailable() }
        fabRefresh.setOnClickListener { refreshModules() }

        adapter = ModuleAdapter(
            installedIds = ModuleManager.getInstalledModuleIds(this),
            onActionClick = { module, action -> handleAction(module, action) },
            onItemBodyClick = { module -> handleItemBodyClick(module) }
        )

        applyLayoutManager()
        recyclerView.adapter = adapter

        // 搜索防抖（300ms）
        val etModuleSearch = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModuleSearch)
        etModuleSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchKeyword = s?.toString()?.trim() ?: ""
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val r = Runnable {
                    applyCategoryFilter()
                    // Batch 21: 防抖结束后如果搜索词长度 >= 2 且有匹配结果则保存历史
                    if (BuildConfig.MODULE_STORE_SEARCH_HISTORY && searchKeyword.length >= 2) {
                        saveSearchHistory(searchKeyword)
                    }
                }
                searchRunnable = r
                searchHandler.postDelayed(r, 300)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        // Batch 21: 搜索框聚焦时显示搜索历史
        etModuleSearch.setOnFocusChangeListener { _, hasFocus ->
            updateSearchHistoryVisibility(hasFocus)
        }

        setupCategoryTabs()
        setupSubCategoryChips()
        refreshModules()
    }

    private fun applyLayoutManager() {
        recyclerView.layoutManager = if (isGridView) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun handleAction(module: ModuleManifest, action: Int) {
        when (action) {
            ModuleAdapter.ACTION_DOWNLOAD -> {
                if (adapter.isDownloading(module.id)) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.module_cancel_download_confirm_title)
                        .setMessage(getString(R.string.module_cancel_download_confirm_format, module.name))
                        .setPositiveButton(R.string.module_action_cancel) { _, _ ->
                            ModuleManager.cancelDownload(module.id)
                            adapter.removeDownloadProgress(module.id)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    downloadModule(module)
                }
            }
            ModuleAdapter.ACTION_UPDATE -> downloadModule(module)
            ModuleAdapter.ACTION_ENABLE -> enableBuiltInModule(module)
            ModuleAdapter.ACTION_OPEN -> openModule(module)
            ModuleAdapter.ACTION_UNINSTALL -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.module_uninstall_confirm_title)
                    .setMessage(getString(R.string.module_uninstall_confirm_format, module.name))
                    .setPositiveButton(R.string.installed_uninstall) { _, _ -> uninstallModule(module) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    /** 点击卡片非按钮区域：打开详情 BottomSheet */
    private fun handleItemBodyClick(module: ModuleManifest) {
        ModuleDetailBottomSheet.show(supportFragmentManager, module) { action ->
            handleAction(module, action)
        }
    }

    private fun handleToolbarAction(itemId: Int): Boolean {
        return when (itemId) {
            R.id.action_installed_modules -> {
                startActivity(Intent(this, InstalledModulesActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                refreshModules()
                Toast.makeText(this, R.string.module_refreshing, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_manage -> {
                updateAllAvailable()
                true
            }
            R.id.action_toggle_view -> {
                isGridView = !isGridView
                applyLayoutManager()
                Toast.makeText(this, getString(if (isGridView) R.string.module_grid_view else R.string.module_list_view), Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_settings -> {
                Toast.makeText(this, R.string.module_settings_wip, Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }

    private fun showSortDialog() {
        // Batch 21: 扩展为 5 种排序（增加下载量/评分）
        val options = arrayOf(
            getString(R.string.module_sort_by_name),
            getString(R.string.module_sort_by_size),
            getString(R.string.module_sort_by_version),
            getString(R.string.module_sort_by_downloads),
            getString(R.string.module_sort_by_rating)
        )
        val currentChoice = when (currentSortMode) {
            SORT_BY_NAME -> 0
            SORT_BY_SIZE -> 1
            SORT_BY_VERSION -> 2
            SORT_BY_DOWNLOADS -> 3
            SORT_BY_RATING -> 4
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.module_sort_title)
            .setSingleChoiceItems(options, currentChoice) { dialog, which ->
                currentSortMode = when (which) {
                    0 -> SORT_BY_NAME
                    1 -> SORT_BY_SIZE
                    2 -> SORT_BY_VERSION
                    3 -> SORT_BY_DOWNLOADS
                    4 -> SORT_BY_RATING
                    else -> SORT_BY_NAME
                }
                allModules = sortModules(allModules)
                applyCategoryFilter()
                dialog.dismiss()
            }
            .show()
    }

    private fun setupCategoryTabs() {
        // Batch 21: 使用 customView 强制显示图标 + 文字（避免默认 tab.icon 在某些机型不渲染）
        CATEGORIES.forEachIndexed { index, (key, stringRes, iconRes) ->
            val tab = categoryTabLayout.newTab().apply {
                tag = key
                customView = layoutInflater.inflate(R.layout.tab_category_custom, categoryTabLayout, false).apply {
                    findViewById<ImageView>(R.id.tabIcon).apply {
                        setImageResource(iconRes)
                        imageTintList = ContextCompat.getColorStateList(
                            this@ModuleStoreActivity, R.color.tab_icon_tint_selector
                        )
                    }
                    findViewById<TextView>(R.id.tabText).text = getString(stringRes)
                }
            }
            categoryTabLayout.addTab(tab, index == 0)
        }

        categoryTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val category = tab?.tag as? String ?: return
                currentCategory = category
                currentSubCategory = SUBCATEGORY_ALL
                updateSubCategoryVisibility()
                resetSubCategoryChips()
                applyCategoryFilter()
                // Batch 21: 切换 tab 时刷新 customView 选中状态
                tab.customView?.isSelected = true
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.isSelected = false
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSubCategoryChips() {
        SUBCATEGORIES.forEach { (key, stringRes) ->
            val chip = Chip(this).apply {
                text = getString(stringRes)
                isCheckable = true
                isChecked = key == currentSubCategory
                setOnClickListener {
                    switchSubCategory(key)
                }
            }
            subCategoryChips.add(chip)
            subCategoryTabsContainer.addView(chip)
        }
        updateSubCategoryVisibility()
    }

    private fun switchSubCategory(subCategory: String) {
        if (subCategory == currentSubCategory) return
        currentSubCategory = subCategory
        subCategoryChips.forEach { it.isChecked = false }
        val index = SUBCATEGORIES.indexOfFirst { it.first == subCategory }
        if (index >= 0) subCategoryChips[index].isChecked = true
        applyCategoryFilter()
    }

    private fun updateSubCategoryVisibility() {
        subCategoryContainer.visibility = if (currentCategory == CATEGORY_GAMES) View.VISIBLE else View.GONE
    }

    private fun resetSubCategoryChips() {
        subCategoryChips.forEachIndexed { index, chip ->
            chip.isChecked = SUBCATEGORIES[index].first == currentSubCategory
        }
    }

    private fun applyCategoryFilter() {
        val categoryFiltered = if (currentCategory == CATEGORY_INSTALLED) {
            allModules.filter { ModuleManager.isModuleInstalled(this, it.id) }
        } else {
            val baseFrameworks = allModules.filter { it.storeCategory == currentCategory && it.isBaseFramework }
            val otherModules = allModules.filter { it.storeCategory == currentCategory && !it.isBaseFramework }
            baseFrameworks + otherModules
        }

        val categoryAndSubFiltered = if (currentCategory == CATEGORY_GAMES && GAME_SUBCATEGORIES.contains(currentSubCategory)) {
            categoryFiltered.filter { it.gameCategory == currentSubCategory }
        } else {
            categoryFiltered
        }

        // Batch 21: 应用筛选（feature flag 控制）
        val filterApplied = if (BuildConfig.MODULE_STORE_FILTER) {
            applyModuleFilter(categoryAndSubFiltered)
        } else {
            categoryAndSubFiltered
        }

        val finalFiltered = if (searchKeyword.isNotEmpty()) {
            filterApplied.filter {
                it.name.contains(searchKeyword, ignoreCase = true) ||
                it.description.contains(searchKeyword, ignoreCase = true) ||
                it.gameId.contains(searchKeyword, ignoreCase = true) ||
                it.gameCategory.contains(searchKeyword, ignoreCase = true)
            }
        } else {
            filterApplied
        }

        adapter.updateModules(finalFiltered)
        adapter.updateInstalledIds(ModuleManager.getInstalledModuleIds(this))
        adapter.installedVersions = buildInstalledVersionsMap()
        if (finalFiltered.isEmpty()) {
            emptyContainer.visibility = View.VISIBLE
        } else {
            emptyContainer.visibility = View.GONE
        }
    }

    /**
     * Batch 21: 应用筛选：按安装状态 / 大小 / 版本
     */
    private fun applyModuleFilter(modules: List<ModuleManifest>): List<ModuleManifest> {
        return modules.filter { module ->
            // 安装状态筛选
            val stateMatch = when (filterState) {
                FILTER_STATE_INSTALLED -> ModuleManager.isModuleInstalled(this, module.id)
                FILTER_STATE_NOT_INSTALLED -> !ModuleManager.isModuleInstalled(this, module.id)
                FILTER_STATE_UPDATABLE -> {
                    val installedV = ModuleManager.getInstalledVersionCode(this, module.id)
                    ModuleManager.isModuleInstalled(this, module.id) && installedV in 1 until module.versionCode
                }
                else -> true
            }
            if (!stateMatch) return@filter false

            // 大小筛选（仅对非内置模块生效，内置模块无大小）
            if (filterSize != FILTER_SIZE_ALL && !module.builtIn) {
                val sizeMb = module.fileSize / (1024.0 * 1024.0)
                val sizeMatch = when (filterSize) {
                    FILTER_SIZE_SMALL -> sizeMb < 5.0
                    FILTER_SIZE_MEDIUM -> sizeMb in 5.0..20.0
                    FILTER_SIZE_LARGE -> sizeMb > 20.0
                    else -> true
                }
                if (!sizeMatch) return@filter false
            }

            // 版本筛选
            if (filterVersion != FILTER_VERSION_ALL) {
                val majorVersion = module.versionName.substringBefore('.').toIntOrNull() ?: 1
                val versionMatch = when (filterVersion) {
                    FILTER_VERSION_ABOVE_V1 -> majorVersion >= 1
                    FILTER_VERSION_ABOVE_V2 -> majorVersion >= 2
                    else -> true
                }
                if (!versionMatch) return@filter false
            }
            true
        }
    }

    private fun showFilterDialog() {
        if (!BuildConfig.MODULE_STORE_FILTER) {
            Toast.makeText(this, R.string.module_settings_wip, Toast.LENGTH_SHORT).show()
            return
        }
        val context = this
        // 用 LinearLayout 构建多组单选项的筛选对话框
        val container = LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.gm_spacing_4),
                resources.getDimensionPixelSize(R.dimen.gm_spacing_2),
                resources.getDimensionPixelSize(R.dimen.gm_spacing_4),
                0
            )
        }

        // 安装状态
        container.addView(buildFilterSection(
            context, R.string.module_filter_state_label,
            intArrayOf(
                R.string.module_filter_state_all,
                R.string.module_filter_state_installed,
                R.string.module_filter_state_not_installed,
                R.string.module_filter_state_updatable
            ),
            filterState
        ) { selected -> filterState = selected })

        // 文件大小
        container.addView(buildFilterSection(
            context, R.string.module_filter_size_label,
            intArrayOf(
                R.string.module_filter_size_all,
                R.string.module_filter_size_small,
                R.string.module_filter_size_medium,
                R.string.module_filter_size_large
            ),
            filterSize
        ) { selected -> filterSize = selected })

        // 版本
        container.addView(buildFilterSection(
            context, R.string.module_filter_version_label,
            intArrayOf(
                R.string.module_filter_version_all,
                R.string.module_filter_version_above_v1,
                R.string.module_filter_version_above_v2
            ),
            filterVersion
        ) { selected -> filterVersion = selected })

        AlertDialog.Builder(context)
            .setTitle(R.string.module_filter_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyCategoryFilter()
                val resultCount = adapter.itemCount
                Toast.makeText(context, getString(R.string.module_filter_applied_format, resultCount), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.module_filter_clear) { _, _ ->
                filterState = FILTER_STATE_ALL
                filterSize = FILTER_SIZE_ALL
                filterVersion = FILTER_VERSION_ALL
                applyCategoryFilter()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /** 构建筛选子区块（标题 + 单选 ChipGroup） */
    private fun buildFilterSection(
        context: android.content.Context,
        titleRes: Int,
        optionRes: IntArray,
        currentSelection: Int,
        onSelected: (Int) -> Unit
    ): View {
        val wrapper = LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.gm_spacing_2)
            }
        }
        val title = TextView(context).apply {
            text = getString(titleRes)
            setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        wrapper.addView(title)
        val chipGroup = ChipGroup(context).apply {
            isSingleSelection = true
        }
        optionRes.forEachIndexed { index, resId ->
            val chip = Chip(context).apply {
                text = getString(resId)
                isCheckable = true
                isChecked = index == currentSelection
                setOnClickListener {
                    onSelected(index)
                    // 取消其它 chip 选中
                    for (i in 0 until chipGroup.childCount) {
                        val other = chipGroup.getChildAt(i) as Chip
                        other.isChecked = other === this
                    }
                }
            }
            chipGroup.addView(chip)
        }
        wrapper.addView(chipGroup)
        return wrapper
    }

    // ===== Batch 21: 搜索历史 =====

    /** 加载历史，按时间逆序返回（最新在前） */
    private fun loadSearchHistory(): List<String> {
        val raw = searchHistoryPrefs.getString(SEARCH_HISTORY_KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    /** 保存搜索词到历史（去重、最多 SEARCH_HISTORY_MAX 条） */
    private fun saveSearchHistory(keyword: String) {
        val history = loadSearchHistory().toMutableList()
        // 去重
        history.remove(keyword)
        history.add(0, keyword)
        // 限制条数
        while (history.size > SEARCH_HISTORY_MAX) {
            history.removeAt(history.size - 1)
        }
        searchHistoryPrefs.edit().putString(SEARCH_HISTORY_KEY, history.joinToString("\n")).apply()
        updateSearchHistoryChips()
    }

    /** 清空搜索历史 */
    private fun clearSearchHistory() {
        searchHistoryPrefs.edit().remove(SEARCH_HISTORY_KEY).apply()
        updateSearchHistoryChips()
        Toast.makeText(this, R.string.module_search_history_cleared, Toast.LENGTH_SHORT).show()
    }

    /** 刷新搜索历史 chip */
    private fun updateSearchHistoryChips() {
        searchHistoryChips.removeAllViews()
        if (!BuildConfig.MODULE_STORE_SEARCH_HISTORY) {
            searchHistoryContainer.visibility = View.GONE
            return
        }
        val history = loadSearchHistory()
        if (history.isEmpty()) {
            searchHistoryContainer.visibility = View.GONE
            return
        }
        searchHistoryContainer.visibility = View.VISIBLE
        history.forEach { keyword ->
            val chip = Chip(this).apply {
                text = keyword
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    val etSearch = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModuleSearch)
                    etSearch.setText(keyword)
                    etSearch.setSelection(keyword.length)
                    searchHistoryContainer.visibility = View.GONE
                }
            }
            searchHistoryChips.addView(chip)
        }
    }

    /** 根据焦点状态显示/隐藏搜索历史 */
    private fun updateSearchHistoryVisibility(hasFocus: Boolean) {
        if (!BuildConfig.MODULE_STORE_SEARCH_HISTORY) return
        if (!hasFocus) {
            searchHistoryContainer.visibility = View.GONE
            return
        }
        if (loadSearchHistory().isNotEmpty() && searchKeyword.isBlank()) {
            searchHistoryContainer.visibility = View.VISIBLE
        } else {
            searchHistoryContainer.visibility = View.GONE
        }
    }

    private fun startSkeletonAnimation() {
        val animator = android.animation.ObjectAnimator.ofFloat(skeletonContainer, "alpha", 1f, 0.5f, 1f)
        animator.duration = 1000
        animator.repeatCount = android.animation.ObjectAnimator.INFINITE
        animator.start()
        skeletonContainer.setTag(R.id.moduleSkeletonContainer, animator)
    }

    private fun stopSkeletonAnimation() {
        val animator = skeletonContainer.getTag(R.id.moduleSkeletonContainer) as? android.animation.ObjectAnimator
        animator?.cancel()
    }

    private fun refreshModules() {
        skeletonContainer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        startSkeletonAnimation()

        var firstCallback = true
        ModuleManager.loadModuleList(applicationContext) { modules, error ->
            runOnUiThread {
                skeletonContainer.visibility = View.GONE
                stopSkeletonAnimation()
                recyclerView.visibility = View.VISIBLE
                if (error != null && firstCallback && modules.isEmpty()) {
                    errorText.text = error
                    errorContainer.visibility = View.VISIBLE
                } else if (modules.isEmpty() && firstCallback) {
                    emptyContainer.visibility = View.VISIBLE
                } else {
                    allModules = sortModules(modules)
                    applyCategoryFilter()
                }
                firstCallback = false
                updateStatsBar()
                updateHeroBanner()
            }
        }
    }

    /** 三栏统计卡片：总数 / 已安装 / 有更新 */
    private fun updateStatsBar() {
        val total = allModules.size
        val installed = allModules.count { ModuleManager.isModuleInstalled(this, it.id) }
        // Batch 21 修复：
        // 1. 内置模块不参与"有更新"统计（其版本随宿主升级）
        // 2. installedVersion > 0 才算有效（避免文件存在但无版本记录被误判为待更新）
        val updatable = allModules.count { module ->
            !module.builtIn &&
            ModuleManager.isModuleInstalled(this, module.id) &&
            ModuleManager.getInstalledVersionCode(this, module.id).let { it > 0 && it < module.versionCode }
        }
        statTotalCount.text = total.toString()
        statInstalledCount.text = installed.toString()
        statUpdateCount.text = updatable.toString()

        // Batch 21: 三栏统计趋势子文本
        findViewById<TextView>(R.id.statTotalTrend)?.apply {
            if (total > 0) {
                text = getString(R.string.module_stat_total_trend, total)
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        findViewById<TextView>(R.id.statInstalledTrend)?.apply {
            if (total > 0) {
                val percent = (installed * 100) / total
                text = getString(R.string.module_stat_installed_trend, percent)
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        findViewById<TextView>(R.id.statUpdateTrend)?.apply {
            if (updatable > 0) {
                text = getString(R.string.module_stat_update_trend, updatable)
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }

        // 无障碍 live region（隐藏文本，仅 TalkBack 朗读）
        val updateSuffix = if (updatable > 0) getString(R.string.module_stats_update_suffix, updatable) else ""
        tvModuleStats.text = getString(R.string.module_stats_format, total, installed, updateSuffix)

        btnUpdateAll.visibility = if (updatable > 0) View.VISIBLE else View.GONE
        btnUpdateAll.text = if (updatable > 0) getString(R.string.module_update_all_count, updatable)
                           else getString(R.string.module_update_all)
    }

    /**
     * Hero Banner 多卡片轮播（Batch 21）：
     * 1. 优先选未安装的高版本模块
     * 2. 其次选最新更新的模块
     * 3. 最后按分类各取一个
     * 最多展示 [HERO_MAX_ITEMS] 张
     */
    private fun updateHeroBanner() {
        // 停止旧轮播
        heroHandler.removeCallbacks(heroAutoScrollRunnable)

        val candidates = allModules.filter { !it.isBaseFramework && it.storeCategory != "vpn" }
        if (candidates.isEmpty()) {
            heroBannerContainer.visibility = View.GONE
            heroAdapter = null
            return
        }

        // 1. 未安装的高版本模块（最多 3 个，按版本号降序）
        val notInstalled = candidates
            .filter { !ModuleManager.isModuleInstalled(this, it.id) }
            .sortedByDescending { it.versionCode }
            .take(3)

        // 2. 有更新的模块（Batch 21 修复：排除内置模块 + 要求 installedVersion > 0）
        val hasUpdate = candidates.filter { module ->
            !module.builtIn &&
            ModuleManager.isModuleInstalled(this, module.id) &&
            ModuleManager.getInstalledVersionCode(this, module.id).let { it > 0 && it < module.versionCode }
        }

        // 3. 各分类取一个已安装的代表（去重）
        val seenCategories = mutableSetOf<String>()
        val categoryReps = candidates
            .filter { ModuleManager.isModuleInstalled(this, it.id) }
            .sortedByDescending { it.versionCode }
            .filter { seenCategories.add(it.storeCategory) }
            .take(2)

        // 合并去重（按 id），最多 HERO_MAX_ITEMS 张
        val merged = LinkedHashSet<ModuleManifest>()
        merged.addAll(notInstalled)
        merged.addAll(hasUpdate)
        merged.addAll(categoryReps)
        val recommendedList = merged.toList().take(HERO_MAX_ITEMS)

        if (recommendedList.isEmpty()) {
            heroBannerContainer.visibility = View.GONE
            heroAdapter = null
            return
        }

        heroBannerContainer.visibility = View.VISIBLE

        // 初始化或更新 adapter
        if (heroAdapter == null) {
            heroAdapter = HeroBannerAdapter(
                context = this,
                onItemClick = { module -> handleItemBodyClick(module) },
                onActionClick = { module ->
                    if (ModuleManager.isModuleInstalled(this, module.id)) {
                        openModule(module)
                    } else {
                        downloadModule(module)
                    }
                }
            )
            heroViewPager.adapter = heroAdapter
            heroViewPager.offscreenPageLimit = 1

            // 页面间距 + clip
            val pageTransformer = androidx.viewpager2.widget.MarginPageTransformer(
                resources.getDimensionPixelSize(R.dimen.gm_spacing_2)
            )
            heroViewPager.setPageTransformer(pageTransformer)

            // 注册页面变化回调（更新指示器 + 重启轮播计时）
            heroViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateHeroIndicator(position, recommendedList.size)
                    // 用户手动滑动后重启计时
                    heroHandler.removeCallbacks(heroAutoScrollRunnable)
                    if (recommendedList.size > 1) {
                        heroHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS)
                    }
                }
            })
        }

        heroAdapter?.submit(recommendedList)
        updateHeroIndicator(0, recommendedList.size)

        // 启动自动轮播
        if (recommendedList.size > 1) {
            heroHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS)
        }
    }

    /** 更新 Hero Banner 指示器圆点 */
    private fun updateHeroIndicator(current: Int, total: Int) {
        heroIndicator.removeAllViews()
        if (total <= 1) return
        for (i in 0 until total) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.gm_spacing_2),
                    resources.getDimensionPixelSize(R.dimen.gm_spacing_2)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.gm_spacing_1)
                    val active = i == current
                    setBackgroundResource(
                        if (active) R.drawable.hero_indicator_dot_active
                        else R.drawable.hero_indicator_dot
                    )
                }
            }
            heroIndicator.addView(dot)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heroHandler.removeCallbacks(heroAutoScrollRunnable)
    }

    override fun onPause() {
        super.onPause()
        heroHandler.removeCallbacks(heroAutoScrollRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (heroAdapter?.itemCount ?: 0 > 1) {
            heroHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS)
        }
    }

    private fun updateAllAvailable() {
        // Batch 21 修复：
        // 1. 内置模块不参与"一键更新"（其版本随宿主升级）
        // 2. installedVersion > 0 才算有效（避免误判）
        val updatable = allModules.filter { module ->
            !module.builtIn &&
            ModuleManager.isModuleInstalled(this, module.id) &&
                    ModuleManager.getInstalledVersionCode(this, module.id).let { it > 0 && it < module.versionCode } &&
                    module.fileName.isNotEmpty() && module.sha256.isNotEmpty()
        }
        if (updatable.isEmpty()) {
            Toast.makeText(this, R.string.module_no_updates, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.module_start_update_count, updatable.size), Toast.LENGTH_SHORT).show()
        for (module in updatable) {
            downloadModule(module)
        }
    }

    private fun sortModules(modules: List<ModuleManifest>): List<ModuleManifest> {
        return when (currentSortMode) {
            SORT_BY_SIZE -> modules.sortedWith(
                compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                    .thenByDescending { it.fileSize }
            )
            SORT_BY_VERSION -> modules.sortedWith(
                compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                    .thenByDescending { it.versionCode }
            )
            SORT_BY_DOWNLOADS -> modules.sortedWith(
                compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                    .thenByDescending { stableDownloadCount(it.id) }
            )
            SORT_BY_RATING -> modules.sortedWith(
                compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                    .thenByDescending { stableRating(it.id) }
            )
            else -> modules.sortedWith(
                compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                    .thenBy { it.name }
            )
        }
    }

    /** Batch 21: 稳定的下载次数（mock，与 ModuleAdapter.generateStableDownloadCount 同算法） */
    private fun stableDownloadCount(moduleId: String): Int {
        val hash = moduleId.hashCode()
        return 50 + (Math.abs(hash) % 12500)
    }

    /** Batch 21: 稳定的评分（mock，与 ModuleAdapter.generateStableRating 同算法） */
    private fun stableRating(moduleId: String): Float {
        val hash = moduleId.hashCode()
        val raw = 3.8f + ((Math.abs(hash) % 13) * 0.1f).toFloat()
        return Math.min(raw, 5.0f)
    }

    private fun buildInstalledVersionsMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (module in allModules) {
            val version = ModuleManager.getInstalledVersionCode(this, module.id)
            if (version > 0) {
                map[module.id] = version
            }
        }
        return map
    }

    private fun enableBuiltInModule(module: ModuleManifest) {
        ModuleManager.enableBuiltInModule(this, module)
        applyCategoryFilter()
        Toast.makeText(this, getString(R.string.module_enabled_format, module.name), Toast.LENGTH_SHORT).show()
    }

    private fun downloadModule(module: ModuleManifest) {
        Log.d("ModuleStore", "downloadModule() called for ${module.id} (${module.name})")
        Toast.makeText(this, getString(R.string.module_start_download_format, module.name), Toast.LENGTH_SHORT).show()
        adapter.updateDownloadProgress(module.id, 0)
        ModuleManager.downloadModule(this, module.id, object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdateMs < 100) return
                lastProgressUpdateMs = now
                runOnUiThread {
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    adapter.updateDownloadProgress(moduleId, percent)
                }
            }

            override fun onComplete(moduleId: String, file: File) {
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    adapter.installedVersions = buildInstalledVersionsMap()
                    allModules = sortModules(ModuleManager.getAvailableModules())
                    applyCategoryFilter()
                    updateStatsBar()
                    updateHeroBanner()
                    Toast.makeText(this@ModuleStoreActivity,
                        getString(R.string.module_download_complete_format, module.name),
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(moduleId: String, message: String) {
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    Toast.makeText(this@ModuleStoreActivity,
                        getString(R.string.module_download_failed_format, message),
                        Toast.LENGTH_LONG).show()
                }
            }

            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
                Log.d("ModuleStore", "onSourceSwitch: $moduleId sourceIndex=$sourceIndex")
            }
        })
    }

    private fun uninstallModule(module: ModuleManifest) {
        if (adapter.isDownloading(module.id)) {
            ModuleManager.cancelDownload(module.id)
            adapter.removeDownloadProgress(module.id)
        }
        ModuleManager.uninstallModule(this, module.id)
        allModules = sortModules(ModuleManager.getAvailableModules())
        applyCategoryFilter()
        updateStatsBar()
        updateHeroBanner()
        Toast.makeText(this, getString(R.string.module_uninstalled_format, module.name), Toast.LENGTH_SHORT).show()
    }

    private fun openModule(module: ModuleManifest) {
        if (module.type == "nav" && (module.isBaseFramework || listOf("games_hall", "browser", "tools", "ai", "vpn").contains(module.id))) {
            val instance = ModuleManager.loadModule(this, module.id)
            if (instance != null || ModuleManager.isModuleInstalled(this, module.id)) {
                val intent = Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_NAV_TAB, module.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                return
            }
            Toast.makeText(this, R.string.module_load_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (module.type == "nav" && module.entryClass.isNotEmpty()) {
            ModuleManager.loadModule(this, module.id)
            ModuleManager.startModule(this, module.id)

            val feature = ModuleManager.getLoadedFeature(this, module.id)
            if (feature != null) {
                val intent = Intent(this, com.gamecenter.app.DynamicGameActivity::class.java)
                intent.putExtra(com.gamecenter.app.DynamicGameActivity.EXTRA_GAME_ID, module.id)
                startActivity(intent)
                return
            }

            val intent = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NAV_TAB, "games_hall")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            return
        }

        if (module.type == "game" && module.activityClass.isNotEmpty() && module.builtIn) {
            try {
                val activityClass = Class.forName(module.activityClass)
                val intent = Intent(this, activityClass)
                startActivity(intent)
                return
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.module_start_failed_format, e.message), Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (module.type == "game" && module.entryClass.isNotEmpty()) {
            val intent = Intent(this, com.gamecenter.app.DynamicGameActivity::class.java)
            intent.putExtra(com.gamecenter.app.DynamicGameActivity.EXTRA_GAME_ID, module.gameId.ifEmpty { module.id })
            startActivity(intent)
            return
        }

        if (module.type == "game" && ModuleManager.getHostGameActivityClassName(module.gameId.ifEmpty { module.id }) != null) {
            ModuleManager.registerInstalledGameModules(this)
            val intent = Intent(this, com.gamecenter.app.DynamicGameActivity::class.java)
            intent.putExtra(com.gamecenter.app.DynamicGameActivity.EXTRA_GAME_ID, module.gameId.ifEmpty { module.id })
            startActivity(intent)
            return
        }

        if (module.type == "game" && ModuleManager.isModuleInstalled(this, module.id)) {
            ModuleManager.registerInstalledGameModules(this)
            val intent = Intent(this, com.gamecenter.app.DynamicGameActivity::class.java)
            intent.putExtra(com.gamecenter.app.DynamicGameActivity.EXTRA_GAME_ID, module.gameId.ifEmpty { module.id })
            startActivity(intent)
            return
        }

        if (module.builtIn && module.entryClass.isNotEmpty()) {
            try {
                val entryClass = Class.forName(module.entryClass)
                val instance = entryClass.getDeclaredConstructor().newInstance()
                if (instance is ModuleInterface) {
                    instance.init(this)
                    instance.start(this)
                    return
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.module_start_failed_format, e.message), Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (module.activityClass.isNotEmpty()) {
            Toast.makeText(this, R.string.module_need_real_package, Toast.LENGTH_SHORT).show()
            return
        }

        ModuleManager.loadModule(this, module.id)
        if (ModuleManager.startModule(this, module.id)) return

        Toast.makeText(this, R.string.module_load_failed, Toast.LENGTH_SHORT).show()
    }
}
