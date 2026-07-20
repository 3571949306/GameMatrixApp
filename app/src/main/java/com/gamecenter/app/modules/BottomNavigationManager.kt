package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import android.view.Menu
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.animation.addListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.core.common.ModuleRegistry
import com.gamecenter.app.core.common.NavigationSlot
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
        private const val MAX_BOTTOM_NAV_ITEMS = 6
    }

    private val menuIdToContribution = mutableMapOf<Int, ModuleRegistry.NavigationContributionEntry>()
    private var currentFragmentTag: String? = null

    /**
     * 刷新底部导航菜单。
     * 应在模块安装/卸载后调用。
     */
    fun refreshNavigation() {
        menuIdToContribution.clear()
        navView.menu.clear()

        // 收集模块贡献
        val contributions = ModuleRegistry.getNavigationContributionsForSlot(context, NavigationSlot.BOTTOM_NAV)
            .filter { it.contribution.isEnabled() }
            .sortedBy { it.contribution.getOrder() }
            .take(MAX_BOTTOM_NAV_ITEMS - 2) // 预留游戏大厅和"我的"

        // 强制插入游戏大厅作为第一个入口（如果模块未声明）
        var nextMenuId = 1
        var gamesAdded = false
        var toolsAdded = false

        // 如果模块贡献了游戏大厅，优先使用模块贡献
        for (entry in contributions) {
            val id = nextMenuId++
            menuIdToContribution[id] = entry
            val title = entry.contribution.getTitle(context)
            val iconRes = entry.contribution.getIconResId().takeIf { it != 0 }
                ?: R.drawable.ic_games
            navView.menu.add(Menu.NONE, id, Menu.NONE, title).setIcon(iconRes)

            if (entry.contribution.getContributionId() == "games_hall") {
                gamesAdded = true
            }
            if (entry.contribution.getContributionId() == "tools") {
                toolsAdded = true
            }
        }

        // 兜底：如果没有模块贡献游戏大厅，添加内置游戏大厅
        if (!gamesAdded) {
            val gamesId = nextMenuId++
            navView.menu.add(Menu.NONE, gamesId, Menu.NONE, R.string.nav_games)
                .setIcon(R.drawable.ic_games)
        }

        // 兜底：P4 动态工具区，如果没有模块贡献工具箱，添加内置工具区入口
        if (BuildConfig.ENABLE_P4_DYNAMIC_TOOLS && !toolsAdded && navView.menu.size() < MAX_BOTTOM_NAV_ITEMS) {
            val toolsId = nextMenuId++
            navView.menu.add(Menu.NONE, toolsId, Menu.NONE, R.string.nav_tools)
                .setIcon(R.drawable.ic_tools)
        }

        // 兜底：添加"我的"入口
        if (BuildConfig.PROFILE_FRAGMENT && navView.menu.size() < MAX_BOTTOM_NAV_ITEMS) {
            val profileId = nextMenuId++
            navView.menu.add(Menu.NONE, profileId, Menu.NONE, R.string.nav_profile)
                .setIcon(R.drawable.ic_nav_profile)
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

        // 默认选中第一个
        if (navView.menu.size() > 0 && currentFragmentTag == null) {
            navView.selectedItemId = navView.menu.getItem(0).itemId
        }

        Log.d(TAG, "底部导航已刷新: ${navView.menu.size()} 个 item")
    }

    /**
     * 导航到指定菜单 ID 对应的 Fragment。
     */
    fun navigateTo(menuId: Int): Boolean {
        Log.d(TAG, "navigateTo called: menuId=$menuId, map=${menuIdToContribution.keys}")
        val entry = menuIdToContribution[menuId]

        val fragment: Fragment
        val tag: String

        if (entry != null) {
            tag = entry.contribution.getContributionId()
            fragment = ModuleRegistry.createFragmentForContribution(context, entry)
                ?: return false
        } else {
            // 使用兜底 Fragment
            tag = getFallbackTag(menuId) ?: return false
            fragment = createFallbackFragment(tag) ?: return false
        }

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
     */
    fun getCurrentContributionId(): String? {
        val menuId = navView.selectedItemId
        return menuIdToContribution[menuId]?.contribution?.getContributionId()
    }

    private fun getFallbackTag(menuId: Int): String? {
        val title = navView.menu.findItem(menuId)?.title?.toString() ?: return null
        return when {
            title == context.getString(R.string.nav_games) -> "games_hall"
            title == context.getString(R.string.nav_tools) -> "tools"
            title == context.getString(R.string.nav_profile) -> "profile"
            else -> null
        }
    }

    private fun createFallbackFragment(tag: String): Fragment? {
        return try {
            when (tag) {
                "games_hall" -> {
                    if (BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL) {
                        com.gamecenter.app.features.DynamicGamesHallFragment()
                    } else {
                        com.gamecenter.app.GamesFragment()
                    }
                }
                "tools" -> com.gamecenter.app.features.DynamicToolsFragment()
                "profile" -> com.gamecenter.app.ProfileFragment()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建兜底 Fragment 失败: $tag", e)
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
