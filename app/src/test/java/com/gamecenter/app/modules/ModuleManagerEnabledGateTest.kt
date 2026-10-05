package com.gamecenter.app.modules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.DynamicGameActivity
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.modules.store.TransactionInstaller
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 批次 3 回归：清单级 enabled=false 的游戏不得注册进游戏大厅。
 *
 * 覆盖三条注册路径：
 * 1. 目录条目（catalog/远程清单）enabled=false —— registerInstalledGameModules 入口拦截；
 * 2. 老安装 SP 快照 enabled=false —— registerGameFromManifest 入口拦截；
 * 3. enableBuiltInModule（商店"启用"动作）同样不得绕过隐藏开关。
 * 并用 enabled=true 的对照用例证明过滤是按清单字段生效、且开关可逆。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleManagerEnabledGateTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        GameRegistry.clearDynamicEntries()
        clearState()
    }

    @After
    fun tearDown() {
        GameRegistry.clearDynamicEntries()
        clearState()
    }

    @Test
    fun `catalog manifest with enabled false is not registered into the lobby`() {
        // 目录条目已翻 false，但老安装的 SP 快照仍带 enabled=true（升级前的安装残留）：
        // 必须以目录条目为准拦截，否则服务器隐藏后老安装仍会在大厅复活。
        seedInstalledSnapshot(gameManifest(enabled = true))
        ModuleManager.registerAvailableManifests(listOf(gameManifest(enabled = false)))

        ModuleManager.registerInstalledGameModules(context)

        assertNull(
            "目录 enabled=false 的已安装游戏不得出现在大厅",
            GameRegistry.getActivityClassById(context, GAME_ID)
        )
    }

    @Test
    fun `installed manifest snapshot with enabled false is not registered into the lobby`() {
        // 目录条目仍是 enabled=true（远程清单尚未同步），但安装快照已带 enabled=false：
        // registerGameFromManifest 入口必须兜底拦截。
        seedInstalledSnapshot(gameManifest(enabled = false))
        ModuleManager.registerAvailableManifests(listOf(gameManifest(enabled = true)))

        ModuleManager.registerInstalledGameModules(context)

        assertNull(
            "安装快照 enabled=false 的游戏不得出现在大厅",
            GameRegistry.getActivityClassById(context, GAME_ID)
        )
    }

    @Test
    fun `enabled true manifest keeps registering the installed game`() {
        seedInstalledSnapshot(gameManifest(enabled = true))
        ModuleManager.registerAvailableManifests(listOf(gameManifest(enabled = true)))

        ModuleManager.registerInstalledGameModules(context)

        assertEquals(
            "enabled=true（默认）时注册行为不变，证明隐藏开关可逆",
            DynamicGameActivity::class.java,
            GameRegistry.getActivityClassById(context, GAME_ID)
        )
    }

    @Test
    fun `enableBuiltInModule honours the enabled flag`() {
        val hidden = gameManifest(enabled = false)
        ModuleManager.registerAvailableManifests(listOf(hidden))

        ModuleManager.enableBuiltInModule(context, hidden)

        assertNull(
            "商店启用动作不得绕过清单级隐藏开关",
            GameRegistry.getActivityClassById(context, GAME_ID)
        )
    }

    // ===== helpers =====

    private fun gameManifest(enabled: Boolean) = ModuleManifest(
        id = GAME_ID,
        name = "Hidden gate game",
        versionName = "1.0.0",
        versionCode = 100,
        entryClass = "com.gamecenter.app.test.HiddenGameEntry",
        fileName = "${GAME_ID}_v100.apk",
        fileSize = 1024,
        sha256 = "aa".repeat(32),
        downloadUrl = "https://example.test/${GAME_ID}_v100.apk",
        type = "game",
        storeCategory = "entertainment_versus",
        enabled = enabled
    )

    private fun seedInstalledSnapshot(manifest: ModuleManifest) {
        TransactionInstaller.getCurrentFile(context, manifest).apply {
            parentFile?.mkdirs()
            writeBytes("hidden-gate-payload".toByteArray())
        }
        val installed = prefs.getStringSet("installed_modules", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        installed.add(GAME_ID)
        prefs.edit()
            .putStringSet("installed_modules", installed)
            .putInt("module_version_$GAME_ID", manifest.versionCode)
            .putString("module_current_manifest_$GAME_ID", manifest.toJson().toString())
            .commit()
        ModuleManager.invalidateInstalledCache()
    }

    private fun clearState() {
        val installed = prefs.getStringSet("installed_modules", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        installed.remove(GAME_ID)
        prefs.edit()
            .putStringSet("installed_modules", installed)
            .remove("module_version_$GAME_ID")
            .remove("module_current_manifest_$GAME_ID")
            .remove("module_last_good_version_$GAME_ID")
            .remove("module_last_good_manifest_$GAME_ID")
            .commit()
        TransactionInstaller.getCurrentDir(context).listFiles().orEmpty()
            .filter { it.name.contains(GAME_ID) }
            .forEach { it.deleteRecursively() }
        clearManifestIndex()
        ModuleManager.invalidateInstalledCache()
    }

    private fun clearManifestIndex() {
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        val manifests = ModuleManager::class.java.getDeclaredField("manifests").apply {
            isAccessible = true
        }.get(instance) as MutableMap<String, ModuleManifest>
        manifests.clear()
    }

    companion object {
        private const val GAME_ID = "enabled_gate_game"
    }
}
