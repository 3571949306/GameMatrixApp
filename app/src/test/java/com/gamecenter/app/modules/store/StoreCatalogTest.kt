package com.gamecenter.app.modules.store

import com.gamecenter.app.modules.store.model.StoreCatalog
import com.gamecenter.app.modules.store.model.StoreCategory
import com.gamecenter.app.modules.store.model.StoreHeroBanner
import com.gamecenter.app.modules.store.model.StoreModule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * P1.9: 商店目录（schemaVersion=2 + v1 兼容）解析测试。
 *
 * 覆盖：
 * - 旧 modules.json（v1 格式）
 * - 新 schemaVersion=2
 * - 空分类
 * - 未知分类
 * - 模块字段缺失
 * - enabled=false
 * - 服务器名称覆盖本地 fallback
 * - 无效截图 URL（空字符串过滤）
 * - 重复模块 ID（不主动去重，由调用方处理）
 * - 低版本 catalogVersion
 * - 未知 schemaVersion
 */
class StoreCatalogTest {

    // ====== v1 兼容 ======

    @Test
    fun `v1 modules_json parses with schemaVersion=1 and catalogVersion from version field`() {
        val v1Json = JSONObject().apply {
            put("version", 21)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "games_hall")
                    put("name", "Games Hall")
                    put("description", "Built-in entry")
                    put("versionName", "1.0.0")
                    put("versionCode", 100)
                    put("builtIn", true)
                    put("storeCategory", "game")
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(v1Json)
        assertEquals(1, catalog.schemaVersion)
        assertEquals(21, catalog.catalogVersion)
        assertEquals(1, catalog.modules.size)
        assertEquals("games_hall", catalog.modules[0].id)
        assertTrue(catalog.categories.isEmpty())
        assertTrue(catalog.heroBanners.isEmpty())
    }

    @Test
    fun `v1 json missing version field defaults catalogVersion to 0`() {
        val v1Json = JSONObject().apply {
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(v1Json)
        assertEquals(1, catalog.schemaVersion)
        assertEquals(0, catalog.catalogVersion)
        assertTrue(catalog.modules.isEmpty())
    }

    // ====== v2 基础解析 ======

    @Test
    fun `v2 catalog parses all sections`() {
        val v2Json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("generatedAt", "2026-07-20T00:00:00Z")
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "game"); put("name", "游戏")
                    put("order", 10); put("enabled", true)
                })
            })
            put("heroBanners", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "wrongbook_banner")
                    put("title", "AI错题本")
                    put("subtitle", "拍照识题")
                    put("moduleId", "wrongbook")
                    put("order", 10); put("enabled", true)
                })
            })
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "wrongbook")
                    put("name", "错题本")
                    put("description", "AI 错题整理")
                    put("versionName", "1.0.0")
                    put("versionCode", 100)
                    put("storeCategory", "wrongbook")
                    put("shortDescription", "AI 拍照识题")
                    put("sortOrder", 80)
                    put("featured", true)
                    put("enabled", true)
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(v2Json)
        assertEquals(2, catalog.schemaVersion)
        assertEquals(1, catalog.catalogVersion)
        assertEquals("2026-07-20T00:00:00Z", catalog.generatedAt)
        assertEquals(1, catalog.categories.size)
        assertEquals("game", catalog.categories[0].id)
        assertEquals(1, catalog.heroBanners.size)
        assertEquals("wrongbook_banner", catalog.heroBanners[0].id)
        assertEquals(1, catalog.modules.size)
        assertEquals("AI 拍照识题", catalog.modules[0].shortDescription)
        assertEquals(80, catalog.modules[0].sortOrder)
        assertTrue(catalog.modules[0].featured)
        assertTrue(catalog.modules[0].enabled)
    }

    // ====== 分类相关 ======

    @Test
    fun `empty categories array returns empty list`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("categories", JSONArray())
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertTrue(catalog.categories.isEmpty())
    }

    @Test
    fun `missing categories field returns empty list`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertTrue(catalog.categories.isEmpty())
    }

    @Test
    fun `unknown category id still parses - calling side handles via fallback`() {
        // 未知分类（未在客户端硬编码）也会被解析，由 ModuleStoreActivity 走 fallback 字符串/图标
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "unknown_cat")
                    put("name", "未知分类")
                    put("order", 99); put("enabled", true)
                })
            })
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.categories.size)
        assertEquals("unknown_cat", catalog.categories[0].id)
        assertEquals("未知分类", catalog.categories[0].name)
    }

    @Test
    fun `category with empty id is skipped`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "")
                    put("name", "空 ID 分类")
                })
                put(JSONObject().apply {
                    put("id", "valid_cat")
                    put("name", "有效分类")
                })
            })
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.categories.size)
        assertEquals("valid_cat", catalog.categories[0].id)
    }

    @Test
    fun `category enabled=false is parsed but caller filters`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "disabled_cat"); put("name", "禁用分类")
                    put("enabled", false)
                })
            })
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.categories.size)
        // 解析层不主动过滤 enabled=false，由调用方决定
        assertFalse(catalog.categories[0].enabled)
    }

    // ====== 模块字段缺失 / 默认值 ======

    @Test
    fun `module missing optional fields uses defaults`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "minimal_module")
                    // 其他字段全部缺失
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.modules.size)
        val m = catalog.modules[0]
        assertEquals("minimal_module", m.id)
        // name 缺失时回退到 id
        assertEquals("minimal_module", m.name)
        assertEquals("", m.description)
        assertEquals("1.0.0", m.versionName)
        assertEquals(0, m.versionCode)
        assertFalse(m.builtIn)
        assertFalse(m.required)
        // 新增字段默认值
        assertEquals("", m.shortDescription)
        assertTrue(m.screenshots.isEmpty())
        assertEquals("", m.changelog)
        assertTrue(m.permissionsDescription.isEmpty())
        assertEquals(0, m.sortOrder)
        assertFalse(m.featured)
        // enabled 默认 true（未显式设为 false 时认为上架）
        assertTrue(m.enabled)
    }

    @Test
    fun `module enabled=false is parsed but caller hides`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "deprecated_module")
                    put("name", "已下架模块")
                    put("enabled", false)
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.modules.size)
        assertFalse(catalog.modules[0].enabled)
    }

    @Test
    fun `module with empty id is skipped`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply { put("id", "") })
                put(JSONObject().apply { put("id", "valid_module") })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.modules.size)
        assertEquals("valid_module", catalog.modules[0].id)
    }

    // ====== 服务器名称覆盖本地 fallback ======

    @Test
    fun `server name and description take precedence over fallback`() {
        // StoreModule.fromJson 直接读取服务器字段；ModuleManifest.fromJson 在为空时回退到本地化映射
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "wrongbook")
                    put("name", "服务器自定义名称")
                    put("description", "服务器自定义描述")
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals("服务器自定义名称", catalog.modules[0].name)
        assertEquals("服务器自定义描述", catalog.modules[0].description)
    }

    // ====== 截图 URL 过滤 ======

    @Test
    fun `screenshots with empty strings are filtered out`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "browser")
                    put("screenshots", JSONArray().apply {
                        put("https://example.com/s1.png")
                        put("")  // 空字符串应被过滤
                        put("https://example.com/s2.png")
                    })
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(2, catalog.modules[0].screenshots.size)
        assertEquals("https://example.com/s1.png", catalog.modules[0].screenshots[0])
        assertEquals("https://example.com/s2.png", catalog.modules[0].screenshots[1])
    }

    @Test
    fun `invalid screenshots field as non-array is ignored`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "browser")
                    put("screenshots", "not_an_array")
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertTrue(catalog.modules[0].screenshots.isEmpty())
    }

    // ====== 重复模块 ID ======

    @Test
    fun `duplicate module ids are both parsed - deduplication is caller responsibility`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 1)
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "dupe")
                    put("name", "第一个")
                })
                put(JSONObject().apply {
                    put("id", "dupe")
                    put("name", "第二个")
                })
            })
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        // 解析层不去重，由调用方处理（如 ModuleStoreActivity mergeCatalogModules）
        assertEquals(2, catalog.modules.size)
        assertEquals("第一个", catalog.modules[0].name)
        assertEquals("第二个", catalog.modules[1].name)
    }

    // ====== catalogVersion 与 schemaVersion 边界 ======

    @Test
    fun `lower catalogVersion is still parsed - version gate is caller responsibility`() {
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("catalogVersion", 0)  // 低版本
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(0, catalog.catalogVersion)
        // 解析层不拒绝低版本，由调用方决定是否降级
    }

    @Test
    fun `unknown schemaVersion is still parsed`() {
        val json = JSONObject().apply {
            put("schemaVersion", 99)  // 未知版本
            put("catalogVersion", 1)
            put("modules", JSONArray())
        }.toString()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(99, catalog.schemaVersion)
        // 不主动抛异常，由调用方决定是否拒绝
    }

    // ====== JSON 单条目损坏不影响其他条目 ======

    @Test
    fun `single corrupted module entry is skipped others remain`() {
        val json = """{
            "schemaVersion": 2,
            "catalogVersion": 1,
            "modules": [
                {"id": "valid_1"},
                "this-is-not-an-object",
                {"id": "valid_2"},
                {"id": ""}
            ]
        }""".trimIndent()

        val catalog = StoreCatalog.fromJson(json)
        // 有效条目：valid_1 和 valid_2
        assertEquals(2, catalog.modules.size)
        assertEquals("valid_1", catalog.modules[0].id)
        assertEquals("valid_2", catalog.modules[1].id)
    }

    @Test
    fun `single corrupted category entry is skipped others remain`() {
        val json = """{
            "schemaVersion": 2,
            "catalogVersion": 1,
            "categories": [
                {"id": "valid_cat"},
                "not-an-object",
                {"id": ""}
            ]
        }""".trimIndent()

        val catalog = StoreCatalog.fromJson(json)
        assertEquals(1, catalog.categories.size)
        assertEquals("valid_cat", catalog.categories[0].id)
    }

    // ====== 非法 JSON 抛异常（调用方降级到缓存） ======

    @Test(expected = org.json.JSONException::class)
    fun `malformed json throws exception for caller to degrade`() {
        StoreCatalog.fromJson("{not valid json")
    }

    // ====== rescueCatalog ======

    @Test
    fun `rescueCatalog contains games_hall only`() {
        val rescue = StoreCatalog.rescueCatalog()
        assertEquals(2, rescue.schemaVersion)
        assertEquals(1, rescue.catalogVersion)
        assertEquals(1, rescue.modules.size)
        assertEquals("games_hall", rescue.modules[0].id)
        assertTrue(rescue.modules[0].builtIn)
        assertTrue(rescue.modules[0].isBaseFramework)
    }

    // ====== toJson 往返 ======

    @Test
    fun `toJson fromJson roundtrip preserves key fields`() {
        val original = StoreCatalog(
            schemaVersion = 2,
            catalogVersion = 1,
            generatedAt = "2026-07-20T00:00:00Z",
            categories = listOf(
                StoreCategory(id = "game", name = "游戏", order = 10, enabled = true, icon = "ic_games")
            ),
            heroBanners = listOf(
                StoreHeroBanner(
                    id = "wrongbook_banner", title = "AI错题本", subtitle = "拍照识题",
                    moduleId = "wrongbook", imageUrl = "", order = 10, enabled = true
                )
            ),
            modules = listOf(
                StoreModule(
                    id = "wrongbook", name = "错题本", description = "AI 错题整理",
                    versionName = "1.0.0", versionCode = 100,
                    entryClass = "com.gamecenter.app.wrongbook.WrongBookModuleEntryPoint",
                    fileName = "feature_wrongbook_v100.apk",
                    fileSize = 6137639L,
                    sha256 = "abc123",
                    downloadUrl = "https://example.com/x.apk",
                    storeCategory = "wrongbook",
                    shortDescription = "AI 拍照识题",
                    screenshots = listOf("https://example.com/s1.png"),
                    changelog = "v1.0.0 初版",
                    permissionsDescription = listOf("网络权限", "存储权限"),
                    tags = listOf("AI", "学习"),
                    sortOrder = 80,
                    featured = true,
                    enabled = true
                )
            )
        )

        val jsonStr = original.toJson().toString()
        val parsed = StoreCatalog.fromJson(jsonStr)

        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.catalogVersion, parsed.catalogVersion)
        assertEquals(original.generatedAt, parsed.generatedAt)
        assertEquals(1, parsed.categories.size)
        assertEquals("game", parsed.categories[0].id)
        assertEquals("ic_games", parsed.categories[0].icon)
        assertEquals(1, parsed.heroBanners.size)
        assertEquals("wrongbook_banner", parsed.heroBanners[0].id)
        assertEquals(1, parsed.modules.size)
        val m = parsed.modules[0]
        assertEquals("wrongbook", m.id)
        assertEquals("AI 拍照识题", m.shortDescription)
        assertEquals(1, m.screenshots.size)
        assertEquals("v1.0.0 初版", m.changelog)
        assertEquals(2, m.permissionsDescription.size)
        assertEquals(2, m.tags.size)
        assertEquals(80, m.sortOrder)
        assertTrue(m.featured)
        assertTrue(m.enabled)
    }
}
