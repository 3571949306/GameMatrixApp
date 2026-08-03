package com.gamecenter.app.modules

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.gamecenter.app.modules.store.DefaultStoreCatalogRepository
import com.gamecenter.app.modules.store.DefaultStoreUiConfigRepository
import com.gamecenter.app.modules.store.StoreActionRouter
import com.gamecenter.app.modules.store.StoreRendererHost
import com.gamecenter.app.modules.store.StoreSectionRendererRegistry
import com.gamecenter.app.modules.store.StoreViewModel
import com.gamecenter.app.modules.store.model.StoreCatalog
import com.gamecenter.app.modules.store.model.StoreHeroBanner
import com.gamecenter.app.modules.store.model.StoreUiConfig
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
 *
 * P2.6: 实现 [StoreRendererHost]，将区块渲染/动作路由委托给 [StoreSectionRendererRegistry] / [StoreActionRouter]。
 * 当前阶段 Activity 仍持有大量业务逻辑（搜索/筛选/下载/安装），ViewModel 仅作为 Repository 协调器；
 * 后续 P3/P4 会逐步把状态迁移到 [StoreViewModel]，Activity 变为薄层。
 */
class ModuleStoreActivity : AppCompatActivity(), StoreRendererHost {

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
    private var currentCategory: String = CATEGORY_ENTERTAINMENT_VERSUS
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
    private var etModuleSearch: com.google.android.material.textfield.TextInputEditText? = null
    private val searchHistoryPrefs by lazy {
        getSharedPreferences("module_search_history", MODE_PRIVATE)
    }

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var lastProgressUpdateMs: Long = 0
    private val subCategoryChips = mutableListOf<Chip>()

    // P1.6/P1.2/P1.3: 远程目录仓库 — 驱动分类、Hero Banner、模块元数据
    private lateinit var catalogRepository: DefaultStoreCatalogRepository
    private var currentCatalog: StoreCatalog? = null
    private val catalogObserver = { catalog: StoreCatalog ->
        currentCatalog = catalog
        // 重新渲染分类 tab 和 Hero Banner（保留当前搜索词与筛选状态）
        rebuildCategoryTabs()
        applyCategoryFilter()
        updateHeroBanner()
        updateStatsBar()
    }

    // P2.3/P2.6: 远程 UI 配置仓库 + ViewModel — 驱动区块显示/隐藏/顺序/列数
    private lateinit var uiConfigRepository: DefaultStoreUiConfigRepository
    private var currentUiConfig: StoreUiConfig? = null
    private val uiConfigObserver = { config: StoreUiConfig ->
        currentUiConfig = config
        // P2.4: 当 STORE_SECTION_RENDERER 开启时，按远程配置重新渲染区块
        if (BuildConfig.STORE_SECTION_RENDERER) {
            applySectionRenderers()
        }
    }
    private var storeViewModel: StoreViewModel? = null

    companion object {
        private const val FLUTTER_STORE_FRAGMENT_TAG = "flutter_module_store"
        // 6 类标准化 storeCategory wireValue（与 Flutter 商店一致，按结果组织模块）
        const val CATEGORY_ENTERTAINMENT_VERSUS = "entertainment_versus"
        const val CATEGORY_LEARNING_ORGANIZATION = "learning_organization"
        const val CATEGORY_READING_BROWSING = "reading_browsing"
        const val CATEGORY_TEXT_CREATION = "text_creation"
        const val CATEGORY_DEVICE_NETWORK = "device_network"
        const val CATEGORY_PERSONALIZATION = "personalization"
        const val CATEGORY_INSTALLED = "installed"

        private val CATEGORIES = listOf(
            Triple(CATEGORY_ENTERTAINMENT_VERSUS, R.string.store_category_entertainment_versus, R.drawable.ic_games),
            Triple(CATEGORY_LEARNING_ORGANIZATION, R.string.store_category_learning_organization, R.drawable.ic_nav_wrongbook),
            Triple(CATEGORY_READING_BROWSING, R.string.store_category_reading_browsing, R.drawable.ic_browser),
            Triple(CATEGORY_TEXT_CREATION, R.string.store_category_text_creation, R.drawable.ic_ai),
            Triple(CATEGORY_DEVICE_NETWORK, R.string.store_category_device_network, R.drawable.ic_vpn),
            Triple(CATEGORY_PERSONALIZATION, R.string.store_category_personalization, R.drawable.ic_settings),
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
        const val EXTRA_FORCE_LEGACY_STORE = "force_legacy_module_store"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.ENABLE_FLUTTER_MODULE_STORE &&
            !intent.getBooleanExtra(EXTRA_FORCE_LEGACY_STORE, false) &&
            showFlutterStore()
        ) {
            return
        }
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

        // DIAG-P1b: 在「真正完成 layout 之后」（而非 applyCategoryFilter 同步读取时）记录 RecyclerView
        // 的真实测量高度与已布局子项数量，用于区分两种情况：
        //   1) 布局缺陷导致内容区高度恒为 0（根因）
        //   2) 之前 diag_p1.txt 的 measuredH=0 仅因读取时机过早（布局尚未发生）的时序假象。
        // R8 会剥离 android.util.Log，因此改为写入文件，便于 adb pull 读取。
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                try {
                    val sb = StringBuilder()
                    sb.append("afterLayout=true\n")
                    sb.append("recyclerMeasuredH=${recyclerView.measuredHeight} recyclerMeasuredW=${recyclerView.measuredWidth}\n")
                    sb.append("recyclerChildCount=${recyclerView.childCount} adapterCount=${adapter.itemCount}\n")
                    val parent = recyclerView.parent as? View
                    sb.append("parentH=${parent?.measuredHeight} parentW=${parent?.measuredWidth}\n")
                    val gp = parent?.parent as? View
                    sb.append("grandParentH=${gp?.measuredHeight}\n")
                    val diagFile = File(getExternalFilesDir(null), "diag_p1b.txt")
                    diagFile.writeText(sb.toString())
                } catch (_: Exception) { }
            }
        })

        // 搜索防抖（300ms）
        etModuleSearch = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModuleSearch)
        etModuleSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchKeyword = s?.toString()?.trim() ?: ""
                // 搜索状态变化时立即更新子分类可见性（搜索中隐藏子分类 chips）
                updateSubCategoryVisibility()
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
        etModuleSearch?.setOnFocusChangeListener { _, hasFocus ->
            updateSearchHistoryVisibility(hasFocus)
        }

        setupCategoryTabs()
        setupSubCategoryChips()

        // P1.6: 初始化远程目录仓库并注册观察者（驱动分类、Banner、模块元数据）
        catalogRepository = DefaultStoreCatalogRepository.getInstance(applicationContext)
        catalogRepository.addObserver(catalogObserver)
        // 立即应用一次缓存目录（如有），避免首次进入商店时分类/Banner 空白
        catalogRepository.getCachedCatalog()?.let { catalogObserver(it) }

        // P2.3/P2.6: 初始化远程 UI 配置仓库 + ViewModel
        uiConfigRepository = DefaultStoreUiConfigRepository.getInstance(applicationContext)
        uiConfigRepository.addObserver(uiConfigObserver)
        storeViewModel = StoreViewModel(applicationContext).also { vm ->
            vm.addObserver { state ->
                // 当前阶段仅记录日志，状态迁移到 ViewModel 由后续 P3/P4 完成
                Log.d("ModuleStoreActivity", "StorePageState updated: catalog=${state.catalog != null} uiConfig.pages=${state.uiConfig.pages.size}")
            }
        }
        // 立即应用一次缓存 UI 配置（如有）
        uiConfigRepository.getCachedConfig()?.let { uiConfigObserver(it) }

        refreshModules()
    }

    private fun showFlutterStore(): Boolean = runCatching {
        com.gamecenter.app.modules.bridge.FlutterStoreEngineManager.getOrCreate(applicationContext)
        val container = FragmentContainerView(this).apply {
            id = R.id.flutter_module_store_container
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)
        if (supportFragmentManager.findFragmentByTag(FLUTTER_STORE_FRAGMENT_TAG) == null) {
            val fragment = com.gamecenter.app.modules.bridge.FlutterModuleStoreFeature()
                .createFragment(this)
            supportFragmentManager.beginTransaction()
                .replace(R.id.flutter_module_store_container, fragment, FLUTTER_STORE_FRAGMENT_TAG)
                .commitNow()
        }
        true
    }.getOrElse { error ->
        Log.e("ModuleStoreActivity", "Flutter store embedding failed; using legacy store", error)
        false
    }

    override fun onDestroy() {
        super.onDestroy()
        // P1.6: 注销目录观察者，避免内存泄漏
        if (::catalogRepository.isInitialized) {
            catalogRepository.removeObserver(catalogObserver)
        }
        // P2.3: 注销 UI 配置观察者
        if (::uiConfigRepository.isInitialized) {
            uiConfigRepository.removeObserver(uiConfigObserver)
        }
        // P2.6: 销毁 ViewModel
        storeViewModel?.destroy()
        storeViewModel = null
        heroHandler.removeCallbacks(heroAutoScrollRunnable)
        // P2 内存泄漏修复：清理搜索防抖 Handler 中可能残留的延迟任务
        searchHandler.removeCallbacksAndMessages(null)
        searchRunnable = null
    }

    // ====== P2.4/P2.6: StoreRendererHost 实现 ======

    override val hostContext: android.content.Context
        get() = this

    override fun currentModules(): List<ModuleManifest> = allModules

    override fun installedModuleIds(): Set<String> = ModuleManager.getInstalledModuleIds(this)

    /**
     * P2.5: 动作路由入口。Renderer 不直接调用 Activity 的私有方法，
     * 而是通过 [StoreActionRouter.dispatch] 白名单校验后回调到这里。
     *
     * 当前阶段：Activity 已有自己的点击处理逻辑（handleAction / handleItemBodyClick），
     * 此方法主要用于未来 Renderer 主动触发动作的场景（如 notice 区块的"查看详情"按钮）。
     */
    override fun dispatchAction(action: String, params: Map<String, String>) {
        Log.d("ModuleStoreActivity", "dispatchAction: $action params=$params")
        when (action) {
            StoreActionRouter.ACTION_OPEN_MODULE -> {
                val moduleId = params["moduleId"] ?: return
                allModules.firstOrNull { it.id == moduleId }?.let { openModule(it) }
            }
            StoreActionRouter.ACTION_OPEN_MODULE_DETAIL -> {
                val moduleId = params["moduleId"] ?: return
                allModules.firstOrNull { it.id == moduleId }?.let { handleItemBodyClick(it) }
            }
            StoreActionRouter.ACTION_OPEN_INSTALLED_MODULES -> {
                // 切换到"已安装"分类 tab
                val cats = getActiveCategories()
                val index = cats.indexOfFirst { it.first == CATEGORY_INSTALLED }
                if (index >= 0) categoryTabLayout.getTabAt(index)?.select()
            }
            StoreActionRouter.ACTION_REFRESH_CATALOG -> {
                refreshModules()
            }
            StoreActionRouter.ACTION_SWITCH_CATEGORY -> {
                val categoryId = params["categoryId"] ?: return
                switchCategory(categoryId)
            }
            StoreActionRouter.ACTION_OPEN_UPDATE_LIST -> {
                updateAllAvailable()
            }
            else -> {
                Log.w("ModuleStoreActivity", "未知动作: $action")
            }
        }
    }

    override fun triggerRefresh() {
        refreshModules()
    }

    override fun switchCategory(categoryId: String) {
        val cats = getActiveCategories()
        val index = cats.indexOfFirst { it.first == categoryId }
        if (index >= 0) {
            categoryTabLayout.getTabAt(index)?.select()
        } else {
            // 当前分类被服务器删除时，自动回到第一个
            Log.w("ModuleStoreActivity", "分类 $categoryId 不存在，回到第一个")
            if (categoryTabLayout.tabCount > 0) categoryTabLayout.getTabAt(0)?.select()
        }
    }

    /**
     * P2.4: 按当前 UI 配置渲染所有区块。
     * 仅在 STORE_SECTION_RENDERER=true 时调用。
     * sections 按 order 升序，仅渲染 enabled=true 的区块。
     */
    private fun applySectionRenderers() {
        val config = currentUiConfig ?: return
        val page = config.pages["store_home"] ?: return
        val sortedSections = page.sections
            .filter { it.enabled }
            .sortedBy { it.order }
        val container = findViewById<android.view.ViewGroup>(R.id.moduleStoreRootContainer) ?: return
        StoreSectionRendererRegistry.dispatchRender(sortedSections, container, this)
    }

    /**
     * P1.2: 获取当前生效的分类列表。
     *
     * 优先级：
     * 1. 远程目录 catalog.categories（enabled=true，按 order 升序）
     * 2. 硬编码 CATEGORIES（兜底）
     *
     * 始终追加 CATEGORY_INSTALLED 作为最后一个 tab（已安装管理入口，不受远程控制）。
     * 未知分类的模块仍能显示在"其他"分类，由 applyCategoryFilter 处理。
     */
    private fun getActiveCategories(): List<Triple<String, Int, Int>> {
        val catalogCats = currentCatalog?.categories
            ?.filter { it.enabled }
            ?.sortedBy { it.order }
            ?.mapNotNull { cat ->
                val stringRes = resolveCategoryStringRes(cat.id)
                val iconRes = resolveCategoryIconRes(cat.id, cat.icon)
                if (stringRes != 0 && iconRes != 0) {
                    Triple(cat.id, stringRes, iconRes)
                } else null
            }
        if (!catalogCats.isNullOrEmpty()) {
            val result = catalogCats.toMutableList()
            // 始终追加"已安装"入口（不受远程控制）
            if (result.none { it.first == CATEGORY_INSTALLED }) {
                result.add(Triple(CATEGORY_INSTALLED, R.string.module_category_installed, R.drawable.ic_checkin_calendar))
            }
            return result
        }
        // 兜底：硬编码
        return CATEGORIES
    }

    /** 根据分类 ID 解析字符串资源（未知分类返回 0，调用方降级） */
    private fun resolveCategoryStringRes(categoryId: String): Int = when (categoryId) {
        CATEGORY_ENTERTAINMENT_VERSUS -> R.string.store_category_entertainment_versus
        CATEGORY_LEARNING_ORGANIZATION -> R.string.store_category_learning_organization
        CATEGORY_READING_BROWSING -> R.string.store_category_reading_browsing
        CATEGORY_TEXT_CREATION -> R.string.store_category_text_creation
        CATEGORY_DEVICE_NETWORK -> R.string.store_category_device_network
        CATEGORY_PERSONALIZATION -> R.string.store_category_personalization
        CATEGORY_INSTALLED -> R.string.module_category_installed
        else -> 0
    }

    /** 根据分类 ID + 远程 icon 名称解析图标资源 */
    private fun resolveCategoryIconRes(categoryId: String, iconHint: String): Int {
        // 优先使用远程指定的 icon 资源名（如 "ic_games"）
        if (iconHint.isNotEmpty()) {
            val resId = resources.getIdentifier(iconHint, "drawable", packageName)
            if (resId != 0) return resId
        }
        return when (categoryId) {
            CATEGORY_ENTERTAINMENT_VERSUS -> R.drawable.ic_games
            CATEGORY_LEARNING_ORGANIZATION -> R.drawable.ic_nav_wrongbook
            CATEGORY_READING_BROWSING -> R.drawable.ic_browser
            CATEGORY_TEXT_CREATION -> R.drawable.ic_ai
            CATEGORY_DEVICE_NETWORK -> R.drawable.ic_vpn
            CATEGORY_PERSONALIZATION -> R.drawable.ic_settings
            CATEGORY_INSTALLED -> R.drawable.ic_checkin_calendar
            else -> 0
        }
    }

    /**
     * P1.2: 重建分类 tab（远程目录更新后调用）。
     * 保留当前选中的分类（若仍存在），否则回到第一个分类。
     */
    private fun rebuildCategoryTabs() {
        val preservedCategory = currentCategory
        categoryTabLayout.removeAllTabs()
        val cats = getActiveCategories()
        cats.forEachIndexed { index, (key, stringRes, iconRes) ->
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
            categoryTabLayout.addTab(tab, false)
        }
        // 恢复选中状态：若 preservedCategory 仍在列表中则选中它，否则选中第一个
        val targetIndex = cats.indexOfFirst { it.first == preservedCategory }.let { if (it >= 0) it else 0 }
        if (categoryTabLayout.tabCount > targetIndex) {
            categoryTabLayout.getTabAt(targetIndex)?.select()
            currentCategory = cats[targetIndex].first
        }
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
        // P1.2: 优先使用远程目录分类，兜底硬编码 CATEGORIES
        val cats = getActiveCategories()
        cats.forEachIndexed { index, (key, stringRes, iconRes) ->
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
        // 游戏子分类（益智/休闲/经典）仅对"娱乐与对战"分类显示；
        // 搜索状态下隐藏子分类，因为搜索结果跨所有分类，子分类筛选无意义。
        subCategoryContainer.visibility = if (
            currentCategory == CATEGORY_ENTERTAINMENT_VERSUS && searchKeyword.isEmpty()
        ) View.VISIBLE else View.GONE
    }

    private fun resetSubCategoryChips() {
        subCategoryChips.forEachIndexed { index, chip ->
            chip.isChecked = SUBCATEGORIES[index].first == currentSubCategory
        }
    }

    private fun applyCategoryFilter() {
        // MODULE_STORE_PERF_OPT: 一次性获取安装状态和版本，避免 N+1 主线程 IO
        val installedIds = ModuleManager.getInstalledModuleIds(this)
        val installedVersions = buildInstalledVersionsMapFromCache(installedIds)

        // 搜索全局化修复：当有搜索关键词时，跨所有分类搜索，避免"在工具箱分类搜索 2048 无结果"的体验问题。
        // 搜索状态下忽略 currentCategory 和 currentSubCategory，直接在 allModules 基础上过滤。
        val finalFiltered = if (searchKeyword.isNotEmpty()) {
            val searchBase = if (BuildConfig.MODULE_STORE_FILTER) {
                applyModuleFilterWithCache(allModules, installedIds, installedVersions)
            } else {
                allModules
            }
            searchBase.filter {
                it.name.contains(searchKeyword, ignoreCase = true) ||
                it.description.contains(searchKeyword, ignoreCase = true) ||
                it.gameId.contains(searchKeyword, ignoreCase = true) ||
                it.gameCategory.contains(searchKeyword, ignoreCase = true)
            }
        } else {
            val categoryFiltered = if (currentCategory == CATEGORY_INSTALLED) {
                allModules.filter { installedIds.contains(it.id) }
            } else {
                // NEW-001 修复：storeCategory 比较使用忽略大小写，避免远程 catalog 返回 "Games"/"GAME" 等大小写不一致时
                // 严格相等比较失败导致列表为空。同时保留 baseFramework 排序逻辑。
                val catLower = currentCategory.lowercase()
                val baseFrameworks = allModules.filter {
                    it.storeCategory?.lowercase() == catLower && it.isBaseFramework
                }
                val otherModules = allModules.filter {
                    it.storeCategory?.lowercase() == catLower && !it.isBaseFramework
                }
                baseFrameworks + otherModules
            }

            val categoryAndSubFiltered = if (currentCategory == CATEGORY_ENTERTAINMENT_VERSUS && GAME_SUBCATEGORIES.contains(currentSubCategory)) {
                val subLower = currentSubCategory.lowercase()
                categoryFiltered.filter { it.gameCategory?.lowercase() == subLower }
            } else {
                categoryFiltered
            }

            // Batch 21: 应用筛选（feature flag 控制）
            if (BuildConfig.MODULE_STORE_FILTER) {
                applyModuleFilterWithCache(categoryAndSubFiltered, installedIds, installedVersions)
            } else {
                categoryAndSubFiltered
            }
        }

        // MODULE_STORE_PERF_OPT: 先更新 adapter 的 installedIds/versions，再提交列表。
        // 这样 DiffUtil 在比较 areContentsTheSame 时能拿到最新的安装状态，
        // 避免 updateModules 之后再 notifyItemRangeChanged 触发全量重绑。
        // DIAG-P1: 排查模块商店列表空白（确认 finalFiltered 是否为空及 currentCategory/storeCategory 取值）。
        // R8 会剥离 android.util.Log，因此改为写入文件，便于 adb pull 读取。
        try {
            val diag = buildString {
                append("allModules=${allModules.size}\n")
                append("currentCategory=$currentCategory sub=$currentSubCategory search='$searchKeyword'\n")
                append("finalFiltered=${finalFiltered.size}\n")
                append("catalogCategoriesEmpty=${currentCatalog?.categories?.isEmpty() ?: true}\n")
                val dist = allModules.groupingBy { it.storeCategory ?: "null" }.eachCount()
                append("storeCategoryDist=${dist}\n")
                append("sample=${allModules.take(8).map { it.id to (it.storeCategory ?: "null") }}\n")
                append("recyclerVisibility=${recyclerView.visibility} measuredH=${recyclerView.measuredHeight} adapterCount=${adapter.itemCount} childCount=${recyclerView.childCount}\n")
            }
            val diagFile = File(getExternalFilesDir(null), "diag_p1.txt")
            diagFile.writeText(diag)
        } catch (_: Exception) { }

        adapter.installedVersions = installedVersions
        adapter.updateInstalledIds(installedIds)
        adapter.updateModules(finalFiltered)
        // NEW-001 修复：空列表时同时隐藏 RecyclerView 显示 emptyContainer，避免两者在 FrameLayout 中层叠
        // 造成"列表区域看似空白但 emptyContainer 又被 RecyclerView 遮挡"的视觉异常。
        if (finalFiltered.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyContainer.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyContainer.visibility = View.GONE
            // P1 修复：骨架屏 → 列表切换后，RecyclerView 有时因父布局未重新测量而保持 0 高，
            // 强制触发一次 layout pass，让 weight=1 的 FrameLayout 正确分配剩余高度。
            recyclerView.post {
                recyclerView.requestLayout()
                (recyclerView.parent as? View)?.requestLayout()
            }
        }
    }

    /**
     * Batch 21: 应用筛选：按安装状态 / 大小 / 版本
     * MODULE_STORE_PERF_OPT: 使用缓存的 installedIds/installedVersions，避免 N+1 IO
     */
    private fun applyModuleFilterWithCache(
        modules: List<ModuleManifest>,
        installedIds: Set<String>,
        installedVersions: Map<String, Int>
    ): List<ModuleManifest> {
        return modules.filter { module ->
            // 安装状态筛选
            val isInstalled = installedIds.contains(module.id)
            val stateMatch = when (filterState) {
                FILTER_STATE_INSTALLED -> isInstalled
                FILTER_STATE_NOT_INSTALLED -> !isInstalled
                FILTER_STATE_UPDATABLE -> {
                    val installedV = installedVersions[module.id] ?: 0
                    isInstalled && installedV in 1 until module.versionCode
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
        // P1 修复：进入商店时不主动展示搜索历史（避免占用列表可用高度，导致列表被挤压为 0）；
        // 仅在搜索框获得焦点时由 updateSearchHistoryVisibility(hasFocus=true) 展示。
        searchHistoryContainer.visibility = View.GONE
        history.forEach { keyword ->
            val chip = Chip(this).apply {
                text = keyword
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    // BUG-S003 修复：搜索框可能尚未初始化或已被回收，需 null 检查
                    val etSearch = etModuleSearch ?: return@setOnClickListener
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

        // P1.8: 同时触发远程目录刷新（驱动分类/Banner/模块元数据更新）
        if (::catalogRepository.isInitialized) {
            catalogRepository.refresh { result ->
                result.onFailure { e ->
                    Log.w("ModuleStoreActivity", "catalog refresh failed: ${e.message}")
                }
            }
        }

        // P2.3: 同时触发远程 UI 配置刷新（驱动区块显示/隐藏/顺序/列数）
        if (::uiConfigRepository.isInitialized) {
            uiConfigRepository.refresh { result ->
                result.onFailure { e ->
                    Log.w("ModuleStoreActivity", "ui config refresh failed: ${e.message}")
                }
            }
        }

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
                    // P1.4: 合并 catalog 中的扩展字段（changelog/screenshots/permissions 等）到 manifest
                    allModules = sortModules(mergeCatalogModules(modules))
                    // MODULE_STORE_PERF_OPT: 模块列表就绪后初始化安装状态缓存（内存级，后续切 tab 零 IO）
                    ModuleManager.ensureInstalledCache(applicationContext)
                    applyCategoryFilter()
                }
                firstCallback = false
                updateStatsBar()
                updateHeroBanner()
            }
        }
    }

    /**
     * P1.4: 将 catalog 中的扩展展示字段合并到 ModuleManifest 列表。
     *
     * catalog.modules 可能包含 modules.json 没有的字段（shortDescription、screenshots、
     * changelog、permissionsDescription、tags、sortOrder、featured、enabled）。
     * 按 id 匹配，将扩展字段拷贝到现有 ModuleManifest。
     *
     * catalog 中存在但 modules.json 不存在的模块（已下架但服务器仍列出）也会被加入列表，
     * 由 applyCategoryFilter 进一步处理。
     */
    private fun mergeCatalogModules(modules: List<ModuleManifest>): List<ModuleManifest> {
        val catalog = currentCatalog ?: return modules
        val catalogById = catalog.modules.associateBy { it.id }
        val merged = mutableListOf<ModuleManifest>()
        val seen = mutableSetOf<String>()
        for (m in modules) {
            val s = catalogById[m.id]
            if (s != null) {
                merged.add(m.copy(
                    shortDescription = s.shortDescription,
                    screenshots = s.screenshots,
                    changelog = s.changelog,
                    permissionsDescription = s.permissionsDescription,
                    tags = s.tags,
                    sortOrder = s.sortOrder,
                    featured = s.featured,
                    enabled = s.enabled
                ))
            } else {
                merged.add(m)
            }
            seen.add(m.id)
        }
        // catalog 中存在但 ModuleManager 未返回的模块（例如新增模块尚未同步到 modules.json）
        // 不在此添加 — ModuleManager 仍是模块下载/加载的真实数据源
        return merged
    }

    /** 三栏统计卡片：总数 / 已安装 / 有更新 */
    private fun updateStatsBar() {
        val total = allModules.size
        // MODULE_STORE_PERF_OPT: 使用缓存避免 N+1 IO
        val installedIds = ModuleManager.getInstalledModuleIds(this)
        val installedVersions = buildInstalledVersionsMapFromCache(installedIds)
        val installed = allModules.count { installedIds.contains(it.id) }
        // Batch 21 修复：
        // 1. 内置模块不参与"有更新"统计（其版本随宿主升级）
        // 2. installedVersion > 0 才算有效（避免文件存在但无版本记录被误判为待更新）
        val updatable = allModules.count { module ->
            !module.builtIn &&
            installedIds.contains(module.id) &&
            (installedVersions[module.id] ?: 0).let { it > 0 && it < module.versionCode }
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
     * Hero Banner 多卡片轮播（Batch 21 + P1.3 远程化）：
     *
     * P1.3 优先级：
     * 1. 远程目录 catalog.heroBanners（enabled=true，按 order 升序）
     *    - moduleId 无效的 Banner 跳过（不崩溃）
     *    - 最多展示 [HERO_MAX_ITEMS] 张
     * 2. 动态计算（兜底）：
     *    a. 优先选未安装的高版本模块
     *    b. 其次选最新更新的模块
     *    c. 最后按分类各取一个
     */
    private fun updateHeroBanner() {
        // 停止旧轮播
        heroHandler.removeCallbacks(heroAutoScrollRunnable)

        // P1.3: 优先使用远程 Banner 配置
        val remoteBanners = currentCatalog?.heroBanners
            ?.filter { it.enabled }
            ?.sortedBy { it.order }

        val items: List<HeroBannerItem> = if (!remoteBanners.isNullOrEmpty()) {
            val moduleById = allModules.associateBy { it.id }
            remoteBanners.mapNotNull { banner ->
                // moduleId 无效时跳过（不崩溃）
                val module = moduleById[banner.moduleId]
                if (module != null) HeroBannerItem(module = module, banner = banner) else null
            }.take(HERO_MAX_ITEMS)
        } else {
            // 兜底：动态计算
            computeDynamicHeroItems().map { HeroBannerItem(module = it, banner = null) }
        }

        if (items.isEmpty()) {
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
                    updateHeroIndicator(position, items.size)
                    // 用户手动滑动后重启计时
                    heroHandler.removeCallbacks(heroAutoScrollRunnable)
                    if (items.size > 1) {
                        heroHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS)
                    }
                }
            })
        }

        heroAdapter?.submitItems(items)
        updateHeroIndicator(0, items.size)

        // 启动自动轮播
        if (items.size > 1) {
            heroHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS)
        }
    }

    /** 兜底动态计算 Hero Banner 候选模块（当远程 Banner 配置缺失或全部无效时使用） */
    private fun computeDynamicHeroItems(): List<ModuleManifest> {
        val candidates = allModules.filter { !it.isBaseFramework && it.storeCategory != "vpn" }
        if (candidates.isEmpty()) return emptyList()

        // MODULE_STORE_PERF_OPT: 使用缓存避免 N+1 IO
        val installedIds = ModuleManager.getInstalledModuleIds(this)
        val installedVersions = buildInstalledVersionsMapFromCache(installedIds)

        // 1. 未安装的高版本模块（最多 3 个，按版本号降序）
        val notInstalled = candidates
            .filter { !installedIds.contains(it.id) }
            .sortedByDescending { it.versionCode }
            .take(3)

        // 2. 有更新的模块（排除内置模块 + 要求 installedVersion > 0）
        val hasUpdate = candidates.filter { module ->
            !module.builtIn &&
            installedIds.contains(module.id) &&
            (installedVersions[module.id] ?: 0).let { it > 0 && it < module.versionCode }
        }

        // 3. 各分类取一个已安装的代表（去重）
        val seenCategories = mutableSetOf<String>()
        val categoryReps = candidates
            .filter { installedIds.contains(it.id) }
            .sortedByDescending { it.versionCode }
            .filter { seenCategories.add(it.storeCategory) }
            .take(2)

        // 合并去重（按 id），最多 HERO_MAX_ITEMS 张
        val merged = LinkedHashSet<ModuleManifest>()
        merged.addAll(notInstalled)
        merged.addAll(hasUpdate)
        merged.addAll(categoryReps)
        return merged.toList().take(HERO_MAX_ITEMS)
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

    /**
     * MODULE_STORE_PERF_OPT: 基于 installedIds 缓存构建版本映射，
     * 避免对每个模块单独调用 getInstalledVersionCode（走内存缓存而非 N 次 SP 读）。
     */
    private fun buildInstalledVersionsMapFromCache(installedIds: Set<String>): Map<String, Int> {
        if (!BuildConfig.MODULE_STORE_PERF_OPT) return buildInstalledVersionsMap()
        val map = mutableMapOf<String, Int>()
        for (id in installedIds) {
            val version = ModuleManager.getInstalledVersionCode(this, id)
            if (version > 0) map[id] = version
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
