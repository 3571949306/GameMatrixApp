package com.gamecenter.app.modules

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.common.ModuleRegistry
import com.gamecenter.app.features.GamesHallDestinationFactory
import com.gamecenter.app.modules.store.DownloadSourceSelector
import com.gamecenter.app.navigation.BottomNavigationCatalog
import com.gamecenter.app.navigation.BottomNavigationPreferences
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 底部导航管理器（P4）。
 *
 * 职责：
 * 1. 从 ModuleRegistry 收集 BOTTOM_NAV 槽位的导航贡献
 * 2. 动态构建 BottomNavigationView 菜单
 * 3. 管理 Fragment 切换（使用 show/hide 保持状态）
 * 4. 提供默认的游戏大厅和"我的"兜底入口
 */
class BottomNavigationManager(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val containerId: Int,
    private val navView: BottomNavigationView
) {

    companion object {
        private const val TAG = "BottomNavigationManager"
    }

    private val menuIdToItem = mutableMapOf<Int, BottomNavigationCatalog.Item>()
    private val contributionIdToMenuId = mutableMapOf<String, Int>()
    private val preferences = BottomNavigationPreferences(context)
    private var currentFragmentTag: String? = null

    /**
     * 刷新底部导航菜单。
     * 应在模块安装/卸载后调用。
     *
     * 可用入口由 [BottomNavigationCatalog] 统一发现，再应用用户本机的排序与隐藏偏好。
     * 游戏大厅始终保留，宿主最多展示六项。
     */
    fun refreshNavigation() {
        val previouslySelected = currentFragmentTag
        menuIdToItem.clear()
        contributionIdToMenuId.clear()
        navView.menu.clear()

        val items = preferences.visibleItems(BottomNavigationCatalog.discover(context))
        var nextMenuId = 1
        for (entry in items) {
            val menuId = nextMenuId++
            menuIdToItem[menuId] = entry
            contributionIdToMenuId[entry.id] = menuId
            navView.menu.add(Menu.NONE, menuId, Menu.NONE, entry.title).setIcon(entry.iconResId)
        }

        navView.setOnItemSelectedListener { item ->
            Log.d(TAG, "onItemSelected: itemId=${item.itemId}, title=${item.title}")
            val result = navigateTo(item.itemId)
            if (BuildConfig.NAV_ACTIVE_ANIM) {
                animateNavItemIcon(item.itemId)
            }
            result
        }

        navView.setOnItemReselectedListener { item ->
            Log.d(TAG, "onItemReselected: itemId=${item.itemId}, title=${item.title}")
            navigateTo(item.itemId)
            if (BuildConfig.NAV_ACTIVE_ANIM) {
                animateNavItemIcon(item.itemId)
            }
        }

        // 保留当前入口；如果它被用户隐藏或卸载，安全回到游戏大厅。
        val selectedId = previouslySelected?.let(contributionIdToMenuId::get)
            ?: contributionIdToMenuId[BottomNavigationCatalog.GAMES_HALL_ID]
            ?: menuIdToItem.keys.firstOrNull()
        if (selectedId != null) {
            navView.selectedItemId = selectedId
        }

        Log.d(TAG, "底部导航已刷新: ${navView.menu.size()} 个 item, ids=${items.map { it.id }}")
    }

    /**
     * 导航到指定菜单 ID 对应的 Fragment。
     *
     * 动态 APK 一律经 ModuleShellFragment 承载，避免宿主 FragmentManager 在进程恢复时
     * 使用错误 ClassLoader；宿主内置贡献仍可直接创建 Fragment。
     */
    fun navigateTo(menuId: Int): Boolean {
        DownloadSourceSelector.noteNavigation() // 分发 v2：测速避让时钟（用户开始切换则测速顺延）
        val item = menuIdToItem[menuId] ?: return false
        val tag = item.id
        val fragment = createFragment(item) ?: return false

        val transaction = fragmentManager.beginTransaction()

        // 隐藏当前 Fragment
        currentFragmentTag?.let {
            fragmentManager.findFragmentByTag(it)?.let { current ->
                transaction.hide(current)
            }
        }

        // 复用已存在的 Fragment
        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(containerId, fragment, tag)
        } else {
            transaction.show(existing)
        }

        transaction.commitNowAllowingStateLoss()
        currentFragmentTag = tag

        Log.d(TAG, "切换到底部导航: $tag")
        return true
    }

    /**
     * 获取当前选中的 contribution ID。
     *
     * 优先返回 [currentFragmentTag]：当通过 [openModule] 打开非菜单模块（如错题本）
     * 时，底部导航的 selectedItemId 仍指向原 tab，但实际显示的是另一个 Fragment。
     * 此时 back handler 需要知道真实当前 Fragment 才能正确切回游戏大厅。
     */
    fun getCurrentContributionId(): String? {
        return currentFragmentTag ?: menuIdToItem[navView.selectedItemId]?.id
    }

    /** 供深链、返回键和自定义排序后按稳定贡献 ID 选择入口。 */
    fun selectContribution(contributionId: String): Boolean {
        val menuId = contributionIdToMenuId[contributionId] ?: return false
        navView.selectedItemId = menuId
        return true
    }

    /**
     * 打开不在底部导航菜单中的模块（如错题本）。
     *
     * 与 [navigateTo] 使用相同的 show/hide 机制，但直接按 moduleId 创建
     * ModuleShellFragment，无需注册为底部导航 contribution。返回键由
     * [com.gamecenter.app.MainActivity.setupBackToGamesHandler] 处理：
     * [getCurrentContributionId] 会返回此 moduleId，back handler 据此
     * 调用 selectContribution("games_hall") 切回游戏大厅。
     */
    fun openModule(moduleId: String): Boolean {
        val tag = moduleId
        val transaction = fragmentManager.beginTransaction()

        currentFragmentTag?.let {
            fragmentManager.findFragmentByTag(it)?.let { current ->
                transaction.hide(current)
            }
        }

        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            val fragment = com.gamecenter.app.features.ModuleShellFragment().apply {
                arguments = Bundle().apply {
                    putString(com.gamecenter.app.features.ModuleShellFragment.ARG_MODULE_ID, moduleId)
                }
            }
            transaction.add(containerId, fragment, tag)
        } else {
            transaction.show(existing)
        }

        transaction.commitNowAllowingStateLoss()
        currentFragmentTag = tag

        Log.d(TAG, "切换到模块（非菜单）: $tag")
        return true
    }

    private fun createFragment(item: BottomNavigationCatalog.Item): Fragment? {
        return try {
            when (item.destinationKind) {
                BottomNavigationCatalog.DestinationKind.CONTRIBUTION -> {
                    val contribution = item.contributionEntry ?: return null
                    ModuleRegistry.createFragmentForContribution(context, contribution)
                }
                BottomNavigationCatalog.DestinationKind.MODULE_SHELL -> {
                    com.gamecenter.app.features.ModuleShellFragment().apply {
                        arguments = Bundle().apply {
                            putString(com.gamecenter.app.features.ModuleShellFragment.ARG_MODULE_ID, item.moduleId)
                        }
                    }
                }
                BottomNavigationCatalog.DestinationKind.GAMES_HALL -> {
                    // 分发 v2/主页重做：入口优先级单一真源（动态大厅 > 游戏库主页 > 旧 GamesFragment）
                    when (GamesHallDestinationFactory.resolve(
                        GamesHallDestinationFactory.Options(
                            dynamicGamesHall = BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL,
                            libraryRevamp = BuildConfig.HOME_LIBRARY_REVAMP
                        )
                    )) {
                        GamesHallDestinationFactory.Destination.DYNAMIC_GAMES_HALL ->
                            com.gamecenter.app.features.DynamicGamesHallFragment()
                        GamesHallDestinationFactory.Destination.GAME_LIBRARY ->
                            com.gamecenter.app.home.GameLibraryFragment()
                        GamesHallDestinationFactory.Destination.LEGACY_GAMES ->
                            com.gamecenter.app.GamesFragment()
                    }
                }
                BottomNavigationCatalog.DestinationKind.TOOLS -> com.gamecenter.app.features.DynamicToolsFragment()
                BottomNavigationCatalog.DestinationKind.PROFILE -> com.gamecenter.app.ProfileFragment()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建底部导航 Fragment 失败: ${item.id}", e)
            null
        }
    }

    /**
     * 在 BottomNavigationItemView 中查找 ImageView 图标。
     */
    private fun findIconView(itemView: android.view.View): ImageView? {
        if (itemView is ImageView) return itemView
        if (itemView !is ViewGroup) return null
        for (i in 0 until itemView.childCount) {
            val child = itemView.getChildAt(i)
            val found = findIconView(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Batch 6 (NAV_ACTIVE_ANIM): 底部导航选中 item 图标缩放动画。
     */
    private fun animateNavItemIcon(itemId: Int) {
        try {
            val menuView = navView.getChildAt(0) as? ViewGroup ?: return
            val menu = navView.menu
            var position = -1
            for (i in 0 until menu.size()) {
                if (menu.getItem(i).itemId == itemId) {
                    position = i
                    break
                }
            }
            if (position < 0 || position >= menuView.childCount) return

            val itemView = menuView.getChildAt(position)
            val iconView = findIconView(itemView) ?: return

            iconView.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    iconView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "导航图标动画失败: ${e.message}")
        }
    }
}
