package com.gamecenter.app.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 接缝契约测试（质量提升计划 §六，BUG_LEDGER BL-001 守卫）。
 *
 * 契约：[ModuleManager.parseModulesArray] 必须把 V1 数组格式 `[...]` 与
 * V2 对象格式 `{version, modules:[...]}` 解析为**同一**清单列表；
 * 对合法清单绝不静默返回空（历史缺陷：V2 抛异常被吞 → null →
 * 出厂版本补种静默失效，只有真人升级模块时才暴露）。
 *
 * 纯 JVM：returnDefaultValues=true 兜住 Log.w；libs.json.test 提供真 org.json。
 */
class ModuleListParsingContractTest {

    private val moduleA =
        """{"id":"game_a","name":"A","versionCode":11,"fileName":"game_a_v11.apk","sha256":"${"a".repeat(64)}"}"""
    private val moduleB =
        """{"id":"game_b","name":"B","versionCode":22,"fileName":"game_b_v22.apk","sha256":"${"b".repeat(64)}"}"""

    private val v1 = "[$moduleA,$moduleB]"
    private val v2 = """{"schemaVersion":2,"version":37,"modules":[$moduleA,$moduleB]}"""

    @Test
    fun v1ArrayFormatParses() {
        val list = ModuleManager.parseModulesArray(v1)
        assertEquals(listOf("game_a", "game_b"), list.map { it.id })
        assertEquals(listOf(11, 22), list.map { it.versionCode })
    }

    @Test
    fun v2ObjectFormatParses() {
        val list = ModuleManager.parseModulesArray(v2)
        assertEquals(listOf("game_a", "game_b"), list.map { it.id })
        assertEquals(listOf(11, 22), list.map { it.versionCode })
    }

    @Test
    fun dualFormatsProduceIdenticalManifests() {
        assertEquals(ModuleManager.parseModulesArray(v1), ModuleManager.parseModulesArray(v2))
    }

    @Test
    fun v2EmptyModulesReturnsEmptyList() {
        assertTrue(ModuleManager.parseModulesArray("""{"version":1,"modules":[]}""").isEmpty())
    }

    @Test
    fun malformedEntrySkippedWithoutDraggingOthers() {
        val list = ModuleManager.parseModulesArray("""{"version":1,"modules":[$moduleA,{"no_id":1}]}""")
        assertEquals(listOf("game_a"), list.map { it.id })
    }

    @Test
    fun totalGarbageThrowsInsteadOfSilentNull() {
        // BL-001 的反面断言：坏输入必须响亮失败，不允许静默空列表
        assertThrows(org.json.JSONException::class.java) {
            ModuleManager.parseModulesArray("not-json-at-all")
        }
    }

    @Test
    fun realShippedAssetsListParsesFully() {
        // 读仓库真实产物（只读，不触碰受保护资产）——守卫真实清单格式不回潮
        val f = File("src/main/assets/modules.json")
        assertTrue("modules.json 未找到: ${f.absolutePath}", f.exists())
        val list = ModuleManager.parseModulesArray(f.readText(Charsets.UTF_8))
        assertTrue("真实清单解析数异常偏低: ${list.size}", list.size >= 30)
        assertTrue("games_hall 必须可解析", list.any { it.id == "games_hall" })
        assertTrue(list.all { it.id.isNotEmpty() })
        assertTrue(list.all { it.versionCode >= 1 })
    }
}
