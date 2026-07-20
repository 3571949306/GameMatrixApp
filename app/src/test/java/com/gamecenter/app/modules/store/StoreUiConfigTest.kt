package com.gamecenter.app.modules.store

import com.gamecenter.app.modules.store.model.StoreSection
import com.gamecenter.app.modules.store.model.StoreUiConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * P2.8: 商店 UI 配置（store-ui.json）解析测试。
 *
 * 覆盖：
 * - 默认布局（defaultConfig）
 * - 区块顺序改变（order 字段）
 * - 区块隐藏（enabled=false）
 * - list/grid 切换（type 字段）
 * - 未知组件（type 不在白名单）
 * - 非法 columns（0、5、-1）
 * - minHostVersionCode 不满足
 * - store-ui.json 损坏
 */
class StoreUiConfigTest {

    // ====== 默认布局 ======

    @Test
    fun `defaultConfig contains store_home page with 6 sections`() {
        val config = StoreUiConfig.defaultConfig()
        assertEquals(1, config.schemaVersion)
        assertEquals(1, config.pageVersion)
        assertEquals(0, config.minHostVersionCode)
        val page = config.pages["store_home"]
        assertNotNull(page)
        assertEquals(6, page!!.sections.size)
        // 顺序应为 hero -> search -> categories -> modules -> updates -> installed
        assertEquals("hero_banner", page.sections[0].type)
        assertEquals("search_bar", page.sections[1].type)
        assertEquals("category_tabs", page.sections[2].type)
        assertEquals("module_grid", page.sections[3].type)
        assertEquals("update_section", page.sections[4].type)
        assertEquals("installed_section", page.sections[5].type)
    }

    @Test
    fun `defaultConfig module_grid has 2 columns`() {
        val config = StoreUiConfig.defaultConfig()
        val modulesSection = config.pages["store_home"]!!.sections.first { it.type == "module_grid" }
        assertEquals(2, modulesSection.columns)
    }

    // ====== 基础解析 ======

    @Test
    fun `valid v1 config parses all sections`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pageVersion", 1)
            put("minHostVersionCode", 567)
            put("generatedAt", "2026-07-20T00:00:00Z")
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", "hero"); put("type", "hero_banner")
                            put("enabled", true); put("order", 10)
                        })
                        put(JSONObject().apply {
                            put("id", "modules"); put("type", "module_grid")
                            put("enabled", true); put("order", 50); put("columns", 3)
                        })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        assertEquals(1, config.schemaVersion)
        assertEquals(567, config.minHostVersionCode)
        assertEquals("2026-07-20T00:00:00Z", config.generatedAt)
        val page = config.pages["store_home"]
        assertNotNull(page)
        assertEquals(2, page!!.sections.size)
        assertEquals("hero_banner", page.sections[0].type)
        assertEquals("module_grid", page.sections[1].type)
        assertEquals(3, page.sections[1].columns)
    }

    // ====== 区块顺序 ======

    @Test
    fun `sections preserve server order - caller sorts by order field`() {
        // 服务器返回的顺序可能不是按 order 升序，调用方负责排序
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "z_last"); put("type", "module_list"); put("order", 99) })
                        put(JSONObject().apply { put("id", "a_first"); put("type", "hero_banner"); put("order", 1) })
                        put(JSONObject().apply { put("id", "m_mid"); put("type", "search_bar"); put("order", 50) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val sections = config.pages["store_home"]!!.sections
        // 解析层保留服务器顺序（不主动排序），由调用方排序
        assertEquals("z_last", sections[0].id)
        assertEquals("a_first", sections[1].id)
        assertEquals("m_mid", sections[2].id)

        // 模拟调用方排序
        val sorted = sections.sortedBy { it.order }
        assertEquals("a_first", sorted[0].id)
        assertEquals("m_mid", sorted[1].id)
        assertEquals("z_last", sorted[2].id)
    }

    // ====== 区块隐藏 ======

    @Test
    fun `disabled section is parsed but caller filters`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "visible"); put("type", "hero_banner"); put("enabled", true) })
                        put(JSONObject().apply { put("id", "hidden"); put("type", "search_bar"); put("enabled", false) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val sections = config.pages["store_home"]!!.sections
        assertEquals(2, sections.size)
        assertTrue(sections[0].enabled)
        assertFalse(sections[1].enabled)

        // 模拟调用方过滤
        val enabled = sections.filter { it.enabled }
        assertEquals(1, enabled.size)
        assertEquals("visible", enabled[0].id)
    }

    // ====== list / grid 切换 ======

    @Test
    fun `module_list and module_grid are different types`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "list_mode"); put("type", "module_list") })
                        put(JSONObject().apply { put("id", "grid_mode"); put("type", "module_grid"); put("columns", 4) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val sections = config.pages["store_home"]!!.sections
        assertEquals("module_list", sections[0].type)
        assertEquals("module_grid", sections[1].type)
        assertEquals(4, sections[1].columns)
        // module_list 通常不指定 columns
        assertEquals(0, sections[0].columns)
    }

    // ====== 未知组件 ======

    @Test
    fun `unknown section type is still parsed - renderer registry skips`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "known"); put("type", "hero_banner") })
                        put(JSONObject().apply { put("id", "unknown"); put("type", "some_future_type") })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val sections = config.pages["store_home"]!!.sections
        assertEquals(2, sections.size)
        assertEquals("some_future_type", sections[1].type)

        // Renderer 注册表应能识别已知类型，跳过未知类型
        assertTrue(StoreSectionRendererRegistry.isSupported("hero_banner"))
        assertFalse(StoreSectionRendererRegistry.isSupported("some_future_type"))
    }

    @Test
    fun `renderer registry supports all 9 whitelist types`() {
        StoreSection.SUPPORTED_TYPES.forEach { type ->
            assertTrue("Renderer 应支持白名单类型: $type", StoreSectionRendererRegistry.isSupported(type))
        }
        assertEquals(9, StoreSection.SUPPORTED_TYPES.size)
        assertEquals(9, StoreSectionRendererRegistry.registeredCount)
    }

    // ====== 非法 columns ======

    @Test
    fun `columns zero falls back to zero - caller uses default`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "m"); put("type", "module_grid"); put("columns", 0) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val section = config.pages["store_home"]!!.sections[0]
        assertEquals(0, section.columns) // 解析层回退到 0
        // 调用方（ModuleGridRenderer）会在 columns 不在 [1,4] 时用 DEFAULT_COLUMNS=2
        val effectiveCols = if (section.columns in StoreSection.MIN_COLUMNS..StoreSection.MAX_COLUMNS) {
            section.columns
        } else {
            StoreSection.DEFAULT_COLUMNS
        }
        assertEquals(2, effectiveCols)
    }

    @Test
    fun `columns five is clamped to zero - caller uses default`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "m"); put("type", "module_grid"); put("columns", 5) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val section = config.pages["store_home"]!!.sections[0]
        assertEquals(0, section.columns) // 5 超出 [1,4]，回退到 0
    }

    @Test
    fun `columns negative is clamped to zero`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply { put("id", "m"); put("type", "module_grid"); put("columns", -1) })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val section = config.pages["store_home"]!!.sections[0]
        assertEquals(0, section.columns)
    }

    @Test
    fun `columns in valid range 1 to 4 is preserved`() {
        for (cols in 1..4) {
            val json = JSONObject().apply {
                put("schemaVersion", 1)
                put("pages", JSONObject().apply {
                    put("store_home", JSONObject().apply {
                        put("sections", JSONArray().apply {
                            put(JSONObject().apply { put("id", "m"); put("type", "module_grid"); put("columns", cols) })
                        })
                    })
                })
            }.toString()

            val config = StoreUiConfig.fromJson(json)
            assertEquals(cols, config.pages["store_home"]!!.sections[0].columns)
        }
    }

    // ====== schemaVersion 校验 ======

    @Test(expected = IllegalArgumentException::class)
    fun `schemaVersion 0 throws exception`() {
        StoreUiConfig.fromJson("""{"schemaVersion": 0}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `schemaVersion 2 throws exception`() {
        StoreUiConfig.fromJson("""{"schemaVersion": 2}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing schemaVersion throws exception`() {
        StoreUiConfig.fromJson("""{"pages": {}}""")
    }

    // ====== 单条目损坏不影响其他条目 ======

    @Test
    fun `single corrupted section is skipped others remain`() {
        val json = """{
            "schemaVersion": 1,
            "pages": {
                "store_home": {
                    "sections": [
                        {"id": "valid_1", "type": "hero_banner"},
                        "not-an-object",
                        {"id": "valid_2", "type": "search_bar"}
                    ]
                }
            }
        }""".trimIndent()

        val config = StoreUiConfig.fromJson(json)
        val sections = config.pages["store_home"]!!.sections
        assertEquals(2, sections.size)
        assertEquals("valid_1", sections[0].id)
        assertEquals("valid_2", sections[1].id)
    }

    // ====== pages 缺失 ======

    @Test
    fun `missing pages field returns empty map - caller uses defaultConfig`() {
        val json = """{"schemaVersion": 1}""".trimIndent()

        val config = StoreUiConfig.fromJson(json)
        assertTrue(config.pages.isEmpty())
        // 调用方应在 pages 为空时使用 defaultConfig
    }

    // ====== 非法 JSON 抛异常（调用方降级） ======

    @Test(expected = org.json.JSONException::class)
    fun `malformed json throws exception for caller to degrade`() {
        StoreUiConfig.fromJson("{not valid json")
    }

    // ====== params 解析 ======

    @Test
    fun `params are parsed as string map`() {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("pages", JSONObject().apply {
                put("store_home", JSONObject().apply {
                    put("sections", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", "notice"); put("type", "notice")
                            put("params", JSONObject().apply {
                                put("text", "欢迎来到模块商店")
                                put("dismissable", "true")
                            })
                        })
                    })
                })
            })
        }.toString()

        val config = StoreUiConfig.fromJson(json)
        val section = config.pages["store_home"]!!.sections[0]
        assertEquals(2, section.params.size)
        assertEquals("欢迎来到模块商店", section.params["text"])
        assertEquals("true", section.params["dismissable"])
    }

    // ====== toJson 往返 ======

    @Test
    fun `toJson fromJson roundtrip preserves key fields`() {
        val original = StoreUiConfig.defaultConfig()
        val jsonStr = original.toJson().toString()
        val parsed = StoreUiConfig.fromJson(jsonStr)

        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.pageVersion, parsed.pageVersion)
        assertEquals(original.minHostVersionCode, parsed.minHostVersionCode)
        assertEquals(original.pages.size, parsed.pages.size)
        val originalPage = original.pages["store_home"]!!
        val parsedPage = parsed.pages["store_home"]!!
        assertEquals(originalPage.sections.size, parsedPage.sections.size)
        // 验证每个 section 的关键字段
        for (i in originalPage.sections.indices) {
            val o = originalPage.sections[i]
            val p = parsedPage.sections[i]
            assertEquals(o.id, p.id)
            assertEquals(o.type, p.type)
            assertEquals(o.enabled, p.enabled)
            assertEquals(o.order, p.order)
            assertEquals(o.columns, p.columns)
        }
    }
}
