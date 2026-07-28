package com.gamecenter.app.modules

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.gamecenter.app.R
import java.io.File

class InstalledModulesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var errorText: TextView
    private lateinit var categoryTabs: TabLayout
    private lateinit var adapter: InstalledModuleAdapter
    private var allRemoteModules: List<ModuleManifest> = emptyList()
    private var allInstalledModules: List<ModuleManifest> = emptyList()
    private var currentCategory = CATEGORY_ALL

    companion object {
        private const val CATEGORY_ALL = "all"
        // 与 Flutter 商店/ModuleStoreActivity 一致的 6 类 storeCategory
        private val CATEGORIES = listOf(
            CATEGORY_ALL to "全部",
            ModuleStoreActivity.CATEGORY_ENTERTAINMENT_VERSUS to "娱乐与对战",
            ModuleStoreActivity.CATEGORY_LEARNING_ORGANIZATION to "学习与整理",
            ModuleStoreActivity.CATEGORY_READING_BROWSING to "阅读与浏览",
            ModuleStoreActivity.CATEGORY_TEXT_CREATION to "文本与创作",
            ModuleStoreActivity.CATEGORY_DEVICE_NETWORK to "设备与网络",
            ModuleStoreActivity.CATEGORY_PERSONALIZATION to "个性化"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installed_modules)

        findViewById<MaterialToolbar>(R.id.installedToolbar).setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.installedRecyclerView)
        loadingProgress = findViewById(R.id.installedLoadingProgress)
        emptyText = findViewById(R.id.installedEmptyText)
        errorText = findViewById(R.id.installedErrorText)
        categoryTabs = findViewById(R.id.installedCategoryTabs)

        adapter = InstalledModuleAdapter(emptyList()) { module, action ->
            when (action) {
                InstalledModuleAdapter.ACTION_UPDATE -> updateModule(module)
                InstalledModuleAdapter.ACTION_UNINSTALL -> uninstallModule(module)
            }
        }
        adapter.setActivity(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupCategoryTabs()
        loadInstalledModules()
    }

    private fun setupCategoryTabs() {
        CATEGORIES.forEachIndexed { index, (_, label) ->
            categoryTabs.addTab(categoryTabs.newTab().setText(label), index == 0)
        }
        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val label = tab?.text?.toString() ?: return
                currentCategory = CATEGORIES.find { it.second == label }?.first ?: CATEGORY_ALL
                applyCategoryFilter()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadInstalledModules() {
        loadingProgress.visibility = android.view.View.VISIBLE
        emptyText.visibility = android.view.View.GONE
        errorText.visibility = android.view.View.GONE

        ModuleManager.loadModuleList(applicationContext) { modules, error ->
            runOnUiThread {
                loadingProgress.visibility = android.view.View.GONE
                if (error != null) {
                    errorText.text = error
                    errorText.visibility = android.view.View.VISIBLE
                } else {
                    allRemoteModules = modules
                    val installedIds = ModuleManager.getInstalledModuleIds(this)
                    val installedModules = modules.filter { installedIds.contains(it.id) }
                    allInstalledModules = installedModules
                    applyCategoryFilter()
                }
            }
        }
    }

    private fun applyCategoryFilter() {
        val filtered = if (currentCategory == CATEGORY_ALL) {
            allInstalledModules
        } else {
            allInstalledModules.filter { it.storeCategory == currentCategory }
        }
        adapter.updateInstalledModules(filtered)
        emptyText.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateModule(module: ModuleManifest) {
        if (module.builtIn) {
            Toast.makeText(this, getString(R.string.module_builtin_no_update_format, module.name), Toast.LENGTH_LONG).show()
            return
        }
        adapter.updateDownloadProgress(module.id, 0)
        ModuleManager.downloadModule(this, module.id, object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                runOnUiThread {
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    adapter.updateDownloadProgress(moduleId, percent)
                }
            }

            override fun onComplete(moduleId: String, file: File) {
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    loadInstalledModules()
                    Toast.makeText(this@InstalledModulesActivity, getString(R.string.module_update_done_format, module.name), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(moduleId: String, message: String) {
                runOnUiThread {
                    adapter.removeDownloadProgress(moduleId)
                    Toast.makeText(this@InstalledModulesActivity, getString(R.string.module_update_failed_format, message), Toast.LENGTH_LONG).show()
                }
            }

            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {}
        })
    }

    private fun uninstallModule(module: ModuleManifest) {
        ModuleManager.uninstallModule(this, module.id)
        loadInstalledModules()
        Toast.makeText(this, getString(R.string.module_uninstalled_format, module.name), Toast.LENGTH_SHORT).show()
    }

    fun hasUpdateForModule(module: ModuleManifest): Boolean {
        if (module.builtIn) return false
        val remoteModule = allRemoteModules.find { it.id == module.id } ?: return false
        val installedVersionCode = ModuleManager.getInstalledVersionCode(this, module.id)
        return remoteModule.versionCode > installedVersionCode
    }
}
