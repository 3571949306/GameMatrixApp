package com.gamecenter.app.modules

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.MainActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.gamecenter.app.R
import android.util.Log
import java.io.File

class ModuleStoreActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var skeletonContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var errorText: TextView
    private lateinit var categoryTabLayout: TabLayout
    private lateinit var subCategoryTabsContainer: LinearLayout
    private lateinit var subCategoryContainer: HorizontalScrollView
    private lateinit var adapter: ModuleAdapter

    private var allModules: List<ModuleManifest> = emptyList()
    private var currentCategory: String = CATEGORY_GAMES
    private var currentSubCategory: String = SUBCATEGORY_ALL
    private val subTabButtons = mutableListOf<android.widget.Button>()
    private var searchKeyword: String = ""

    companion object {
        const val CATEGORY_GAMES = "game"
        const val CATEGORY_BROWSER = "browser"
        const val CATEGORY_TOOLS = "tools"
        const val CATEGORY_AI = "ai"
        const val CATEGORY_VPN = "vpn"

        private val CATEGORIES = listOf(
            Pair(CATEGORY_GAMES, R.string.store_category_games),
            Pair(CATEGORY_BROWSER, R.string.store_category_browser),
            Pair(CATEGORY_TOOLS, R.string.store_category_tools),
            Pair(CATEGORY_AI, R.string.store_category_ai),
            Pair(CATEGORY_VPN, R.string.store_category_vpn)
        )

        const val SUBCATEGORY_ALL = "all"
        const val SUBCATEGORY_PUZZLE = "puzzle"
        const val SUBCATEGORY_CASUAL = "casual"
        const val SUBCATEGORY_CLASSICS = "classics"

        private val SUBCATEGORIES = listOf(
            Pair(SUBCATEGORY_PUZZLE, R.string.store_subcategory_puzzle),
            Pair(SUBCATEGORY_CASUAL, R.string.store_subcategory_casual),
            Pair(SUBCATEGORY_CLASSICS, R.string.store_subcategory_classics)
        )

        private val GAME_SUBCATEGORIES = setOf(SUBCATEGORY_PUZZLE, SUBCATEGORY_CASUAL, SUBCATEGORY_CLASSICS)
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
        startSkeletonAnimation()
        emptyText = findViewById(R.id.moduleEmptyText)
        errorText = findViewById(R.id.moduleErrorText)
        categoryTabLayout = findViewById(R.id.moduleCategoryTabs)
        subCategoryTabsContainer = findViewById(R.id.moduleSubCategoryTabs)
        subCategoryContainer = findViewById(R.id.moduleSubCategoryContainer)

        adapter = ModuleAdapter(emptyList(), ModuleManager.getInstalledModuleIds(this)) { module, action ->
            when (action) {
                ModuleAdapter.ACTION_DOWNLOAD -> {
                    Log.d("ModuleStore", "ACTION_DOWNLOAD clicked for ${module.id}")
                    if (adapter.isDownloading(module.id)) {
                        ModuleManager.cancelDownload(module.id)
                        adapter.removeDownloadProgress(module.id)
                    } else {
                        downloadModule(module)
                    }
                }
                ModuleAdapter.ACTION_UPDATE -> {
                    Log.d("ModuleStore", "ACTION_UPDATE clicked for ${module.id}")
                    downloadModule(module)
                }
                ModuleAdapter.ACTION_ENABLE -> {
                    enableBuiltInModule(module)
                }
                ModuleAdapter.ACTION_OPEN -> {
                    openModule(module)
                }
                ModuleAdapter.ACTION_UNINSTALL -> {
                    uninstallModule(module)
                }
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val etModuleSearch = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModuleSearch)
        etModuleSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchKeyword = s?.toString()?.trim() ?: ""
                applyCategoryFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        setupCategoryTabs()
        setupSubCategoryTabs()
        refreshModules()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.module_store_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return handleToolbarAction(item.itemId) || super.onOptionsItemSelected(item)
    }

    private fun handleToolbarAction(itemId: Int): Boolean {
        return when (itemId) {
            R.id.action_installed_modules -> {
                startActivity(Intent(this, InstalledModulesActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                refreshModules()
                Toast.makeText(this, "正在刷新模块列表", Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }

    private fun setupCategoryTabs() {
        CATEGORIES.forEachIndexed { index, (_, stringRes) ->
            val tab = categoryTabLayout.newTab().apply {
                text = getString(stringRes)
            }
            categoryTabLayout.addTab(tab, index == 0)
        }

        categoryTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.text?.let { text ->
                    val category = CATEGORIES.find { getString(it.second) == text }?.first
                    if (category != null) {
                        currentCategory = category
                        currentSubCategory = SUBCATEGORY_ALL
                        updateSubCategoryVisibility()
                        resetSubCategoryTabs()
                        applyCategoryFilter()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSubCategoryTabs() {
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val unselectedColor = 0xFF9E9E9E.toInt()
        val selectedColor = 0xFF4CAF50.toInt()

        SUBCATEGORIES.forEach { (key, stringRes) ->
            val btn = android.widget.Button(this).apply {
                text = getString(stringRes)
                textSize = 12f
                setPadding(dp8, dp4, dp8, dp4)
                layoutParams = LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = dp4 }
                setBackgroundColor(if (key == currentSubCategory) selectedColor else unselectedColor)
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setOnClickListener { switchSubCategory(key) }
            }
            subTabButtons.add(btn)
            subCategoryTabsContainer.addView(btn)
        }
        updateSubCategoryVisibility()
    }

    private fun switchSubCategory(subCategory: String) {
        if (subCategory == currentSubCategory) return
        currentSubCategory = subCategory
        val unselectedColor = 0xFF9E9E9E.toInt()
        val selectedColor = 0xFF4CAF50.toInt()
        SUBCATEGORIES.forEachIndexed { index, (key, _) ->
            subTabButtons[index].setBackgroundColor(if (key == subCategory) selectedColor else unselectedColor)
        }
        applyCategoryFilter()
    }

    private fun updateSubCategoryVisibility() {
        subCategoryContainer.visibility = if (currentCategory == CATEGORY_GAMES) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun resetSubCategoryTabs() {
        val unselectedColor = 0xFF9E9E9E.toInt()
        val selectedColor = 0xFF4CAF50.toInt()
        SUBCATEGORIES.forEachIndexed { index, (key, _) ->
            subTabButtons[index].setBackgroundColor(
                if (key == currentSubCategory) selectedColor else unselectedColor
            )
        }
    }

    private fun applyCategoryFilter() {
        val baseFrameworks = allModules.filter { it.storeCategory == currentCategory && it.isBaseFramework }
        val otherModules = allModules.filter { it.storeCategory == currentCategory && !it.isBaseFramework }
        val categoryFiltered = baseFrameworks + otherModules

        val categoryAndSubFiltered = if (currentCategory == CATEGORY_GAMES && GAME_SUBCATEGORIES.contains(currentSubCategory)) {
            categoryFiltered.filter { it.gameCategory == currentSubCategory }
        } else {
            categoryFiltered
        }

        val finalFiltered = if (searchKeyword.isNotEmpty()) {
            categoryAndSubFiltered.filter {
                it.name.contains(searchKeyword, ignoreCase = true) ||
                it.description.contains(searchKeyword, ignoreCase = true)
            }
        } else {
            categoryAndSubFiltered
        }

        adapter.updateModules(finalFiltered)
        adapter.updateInstalledIds(ModuleManager.getInstalledModuleIds(this))
        adapter.installedVersions = buildInstalledVersionsMap()
        if (finalFiltered.isEmpty()) {
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
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
        emptyText.visibility = View.GONE
        errorText.visibility = View.GONE

        // 本地优先 + 后台版本对比（内置兜底在 loadModuleList 内部处理）
        var firstCallback = true
        ModuleManager.loadModuleList(applicationContext) { modules, error ->
            runOnUiThread {
                skeletonContainer.visibility = View.GONE
                stopSkeletonAnimation()
                recyclerView.visibility = View.VISIBLE
                if (error != null && firstCallback && modules.isEmpty()) {
                    errorText.text = error
                    errorText.visibility = View.VISIBLE
                } else if (modules.isEmpty() && firstCallback) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    allModules = sortModules(modules)
                    applyCategoryFilter()
                }
                firstCallback = false
            }
        }
    }

    private fun sortModules(modules: List<ModuleManifest>): List<ModuleManifest> {
        return modules.sortedWith(
            compareBy<ModuleManifest> { if (it.isBaseFramework) 0 else 1 }
                .thenBy { it.name }
        )
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
        Toast.makeText(this, "${module.name} 已启用", Toast.LENGTH_SHORT).show()
    }

    private fun downloadModule(module: ModuleManifest) {
        Log.d("ModuleStore", "downloadModule() called for ${module.id} (${module.name})")
        Toast.makeText(this, "开始下载 ${module.name}...", Toast.LENGTH_SHORT).show()
        adapter.updateDownloadProgress(module.id, 0)
        ModuleManager.downloadModule(this, module.id, object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                Log.d("ModuleStore", "onProgress: $moduleId downloaded=$downloaded total=$total")
                runOnUiThread {
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    adapter.updateDownloadProgress(moduleId, percent)
                }
            }

            override fun onComplete(moduleId: String, file: File) {
                Log.d("ModuleStore", "onComplete: $moduleId file=${file.absolutePath} exists=${file.exists()}")
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    adapter.installedVersions = buildInstalledVersionsMap()
                    allModules = sortModules(ModuleManager.getAvailableModules())
                    applyCategoryFilter()
                    Toast.makeText(this@ModuleStoreActivity, "${module.name} 下载完成", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(moduleId: String, message: String) {
                Log.e("ModuleStore", "onError: $moduleId message=$message")
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    Toast.makeText(this@ModuleStoreActivity, "下载失败: $message", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "${module.name} 已卸载", Toast.LENGTH_SHORT).show()
    }

    private fun openModule(module: ModuleManifest) {
        // 内置/标准 nav 底部导航栏模块（games_hall, browser, tools, ai, vpn）
        if (module.type == "nav" && (module.isBaseFramework || listOf("games_hall", "browser", "tools", "ai", "vpn").contains(module.id))) {
            val instance = ModuleManager.loadModule(this, module.id)
            if (instance != null || ModuleManager.isModuleInstalled(this, module.id)) {
                val intent = Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_NAV_TAB, module.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                return
            }
            Toast.makeText(this, "模块加载失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 有入口类的独立模块（如 TTS 语音合成等）
        if (module.type == "nav" && module.entryClass.isNotEmpty()) {
            ModuleManager.loadModule(this, module.id)
            ModuleManager.startModule(this, module.id) // 先调用 start() 注册资源

            // 如果设置了 activityClass，从模块 APK 的 DexClassLoader 启动 Activity
            if (module.activityClass.isNotEmpty()) {
                val moduleClassLoader = com.gamecenter.app.modules.ModuleLoader.getModuleClassLoader(module.id)
                if (moduleClassLoader != null) {
                    try {
                        val activityClass = moduleClassLoader.loadClass(module.activityClass)
                        val intent = Intent(this, activityClass)
                        startActivity(intent)
                        return
                    } catch (e: Exception) {
                        Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }

            // activityClass 为空时，尝试通过游戏大厅入口（TTS 已在 start() 中注册到 GameRegistry）
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
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (module.activityClass.isNotEmpty()) {
            Toast.makeText(this, "该游戏需要下载真实模块包", Toast.LENGTH_SHORT).show()
            return
        }

        // 通用回退：尝试通过 ModuleInterface 启动
        ModuleManager.loadModule(this, module.id)
        if (ModuleManager.startModule(this, module.id)) return

        Toast.makeText(this, "模块加载失败", Toast.LENGTH_SHORT).show()
    }
}
