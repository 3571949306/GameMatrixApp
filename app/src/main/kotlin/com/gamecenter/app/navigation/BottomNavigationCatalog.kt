package com.gamecenter.app.navigation

import android.content.Context
import androidx.annotation.DrawableRes
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.core.common.ModuleRegistry
import com.gamecenter.app.core.common.NavigationSlot
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.store.DefaultStoreCatalogRepository

/**
 * Android 宿主拥有的底部导航目录。
 *
 * Flutter 商店只负责安装交互；本目录仅消费已经落入 Android 信任链的 Catalog 缓存、
 * 安装状态和已加载模块贡献。远程模块不能直接提供宿主资源 ID，图标必须映射到宿主白名单。
 */
object BottomNavigationCatalog {

    const val MAX_VISIBLE_ITEMS = 6
    const val GAMES_HALL_ID = "games_hall"

    private val legacyModuleOrder = linkedMapOf(
        "browser" to 20,
        "ai" to 40,
        "vpn" to 50
    )

    enum class DestinationKind {
        CONTRIBUTION,
        MODULE_SHELL,
        GAMES_HALL,
        TOOLS,
        PROFILE
    }

    data class Item(
        val id: String,
        val moduleId: String,
        val title: String,
        @DrawableRes val iconResId: Int,
        val defaultOrder: Int,
        val requiredVisible: Boolean = false,
        val destinationKind: DestinationKind,
        val contributionEntry: ModuleRegistry.NavigationContributionEntry? = null
    )

    /**
     * 返回当前设备上可供用户编排的入口。未安装、禁用或与宿主不兼容的远程模块不会出现。
     */
    fun discover(context: Context): List<Item> {
        val appContext = context.applicationContext
        hydrateTrustedCatalog(appContext)

        val installedIds = runCatching {
            ModuleManager.getInstalledModuleIds(appContext)
        }.getOrDefault(emptySet())
        val manifests = ModuleManager.getManifests()
        val items = linkedMapOf<String, Item>()

        val loadedContributions = ModuleRegistry.getNavigationContributionsForSlot(
            context,
            NavigationSlot.BOTTOM_NAV
        )
        for (entry in loadedContributions) {
            val contribution = entry.contribution
            val id = contribution.getContributionId().trim()
            if (id.isEmpty()) continue
            val manifest = manifests[entry.moduleId]
            val dynamicModule = manifest != null && !manifest.builtIn
            items[id] = Item(
                id = id,
                moduleId = entry.moduleId,
                title = contribution.getTitle(context).ifBlank { manifest?.name ?: id },
                iconResId = resolveIcon(
                    manifest?.navigationContribution?.icon,
                    id,
                    if (dynamicModule) 0 else contribution.getIconResId()
                ),
                defaultOrder = contribution.getOrder(),
                requiredVisible = id == GAMES_HALL_ID,
                destinationKind = when {
                    id == GAMES_HALL_ID -> DestinationKind.GAMES_HALL
                    dynamicModule -> DestinationKind.MODULE_SHELL
                    else -> DestinationKind.CONTRIBUTION
                },
                contributionEntry = if (dynamicModule) null else entry
            )
        }

        // Catalog 声明用于冷启动恢复。模块内容仍在首次点击时经 ModuleShellFragment 安全加载。
        for ((moduleId, manifest) in manifests) {
            val declaration = manifest.navigationContribution ?: continue
            if (!declaration.enabled || !declaration.slot.equals("bottom_nav", ignoreCase = true)) continue
            if (!manifest.enabled || moduleId !in installedIds) continue
            if (!ModuleManager.isModuleEnabled(appContext, moduleId)) continue
            if (!manifest.isCompatibleWithHost(BuildConfig.VERSION_CODE)) continue
            if (manifest.entryClass.isBlank()) continue
            if (items.containsKey(moduleId)) continue
            items[moduleId] = Item(
                id = moduleId,
                moduleId = moduleId,
                title = declaration.title.ifBlank { manifest.name },
                iconResId = resolveIcon(declaration.icon, moduleId),
                defaultOrder = declaration.order,
                destinationKind = DestinationKind.MODULE_SHELL
            )
        }

        // 兼容 vc594 及更早目录：这些标准模块尚未写 navigationContribution。
        for ((moduleId, order) in legacyModuleOrder) {
            if (moduleId !in installedIds || items.containsKey(moduleId)) continue
            if (!ModuleManager.isModuleEnabled(appContext, moduleId)) continue
            items[moduleId] = Item(
                id = moduleId,
                moduleId = moduleId,
                title = legacyTitle(context, moduleId),
                iconResId = resolveIcon(moduleId, moduleId),
                defaultOrder = order,
                destinationKind = DestinationKind.MODULE_SHELL
            )
        }

        // 宿主级入口是最后一道可用性兜底。
        if (!items.containsKey(GAMES_HALL_ID)) {
            items[GAMES_HALL_ID] = Item(
                id = GAMES_HALL_ID,
                moduleId = GAMES_HALL_ID,
                title = context.getString(R.string.nav_games),
                iconResId = R.drawable.ic_games,
                defaultOrder = 10,
                requiredVisible = true,
                destinationKind = DestinationKind.GAMES_HALL
            )
        }
        if (BuildConfig.ENABLE_P4_DYNAMIC_TOOLS && !items.containsKey("tools")) {
            items["tools"] = Item(
                id = "tools",
                moduleId = "tools",
                title = context.getString(R.string.nav_tools),
                iconResId = R.drawable.ic_tools,
                defaultOrder = 30,
                destinationKind = DestinationKind.TOOLS
            )
        }
        if (BuildConfig.PROFILE_FRAGMENT && !items.containsKey("profile")) {
            items["profile"] = Item(
                id = "profile",
                moduleId = "profile",
                title = context.getString(R.string.nav_profile),
                iconResId = R.drawable.ic_nav_profile,
                defaultOrder = 100,
                destinationKind = DestinationKind.PROFILE
            )
        }

        return items.values.sortedWith(compareBy<Item> { it.defaultOrder }.thenBy { it.id })
    }

    /** 从上次成功的商店缓存恢复清单，不发网络请求、不执行安装或卸载。 */
    private fun hydrateTrustedCatalog(context: Context) {
        val catalog = runCatching {
            DefaultStoreCatalogRepository.getInstance(context).getCachedCatalog()
        }.getOrNull() ?: return
        val manifests = catalog.modules
            .asSequence()
            .map { it.toModuleManifest() }
            .filter { it.isCompatibleWithHost(BuildConfig.VERSION_CODE) }
            .toList()
        ModuleManager.registerAvailableManifests(manifests)
    }

    @DrawableRes
    private fun resolveIcon(iconKey: String?, moduleId: String, trustedHostResId: Int = 0): Int {
        if (trustedHostResId != 0) return trustedHostResId
        return when (iconKey?.trim()?.lowercase()) {
            "games", "game", "gamepad", "games_hall" -> R.drawable.ic_games
            "browser", "web" -> R.drawable.ic_browser
            "tools", "tool" -> R.drawable.ic_tools
            "ai", "assistant" -> R.drawable.ic_ai
            "vpn", "network" -> R.drawable.ic_vpn
            "profile", "me" -> R.drawable.ic_nav_profile
            else -> when (moduleId) {
                GAMES_HALL_ID -> R.drawable.ic_games
                "browser" -> R.drawable.ic_browser
                "tools" -> R.drawable.ic_tools
                "ai" -> R.drawable.ic_ai
                "vpn" -> R.drawable.ic_vpn
                "profile" -> R.drawable.ic_nav_profile
                else -> R.drawable.ic_extension
            }
        }
    }

    private fun legacyTitle(context: Context, moduleId: String): String = when (moduleId) {
        "browser" -> context.getString(R.string.nav_browser)
        "ai" -> context.getString(R.string.nav_ai)
        "vpn" -> context.getString(R.string.nav_vpn)
        else -> moduleId
    }
}
