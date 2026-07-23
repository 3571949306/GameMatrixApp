package com.gamecenter.app.modules.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.gamecenter.app.core.common.ModuleManifest

class CatalogV2ParserTest {

    @Test
    fun `parses formal multi-runtime catalog`() {
        val catalog = CatalogV2Parser.parse(formalCatalog(), "remote")

        assertEquals(2, catalog.schemaVersion)
        assertEquals(7, catalog.catalogVersion)
        assertEquals(6, catalog.modules.size)
        assertEquals(RuntimeType.entries.toSet(), catalog.modules.map { it.runtimeType }.toSet())
        assertEquals(
            setOf(DeliveryType.BUILTIN, DeliveryType.ZIP, DeliveryType.APK, DeliveryType.CONTENT),
            catalog.modules.map { it.deliveryType }.toSet()
        )
        assertFalse(catalog.offline)
    }

    @Test
    fun `keeps a signature validated remote cache online`() {
        val catalog = CatalogV2Parser.parse(formalCatalog(), "signed_cache")

        assertFalse(catalog.offline)
    }

    @Test
    fun `rejects insecure package URL`() {
        val raw = formalCatalog().replace("https://example.test/tool.zip", "http://example.test/tool.zip")

        val result = runCatching { CatalogV2Parser.parse(raw, "remote") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `adapts deployed display schema without runtime declarations`() {
        val raw = """
            {
              "schemaVersion": 2,
              "catalogVersion": 3,
              "modules": [{
                "id": "legacy_tool",
                "name": "Legacy tool",
                "versionCode": 1,
                "builtIn": true,
                "kind": "feature-apk"
              }]
            }
        """.trimIndent()

        val catalog = CatalogV2Parser.parse(raw, "asset_catalog")

        assertEquals(RuntimeType.ANDROID, catalog.modules.single().runtimeType)
        assertEquals(DeliveryType.BUILTIN, catalog.modules.single().deliveryType)
    }

    @Test
    fun `preserves bottom navigation declaration from formal catalog`() {
        val raw = formalCatalog().replaceFirst(
            "\"entryClass\": \"com.example.ToolEntryPoint\",",
            "\"entryClass\": \"com.example.ToolEntryPoint\",\n" +
                "              \"navigationContribution\": {\"slot\":\"bottom_nav\",\"title\":\"Tool\",\"icon\":\"extension\",\"order\":15},"
        )

        val module = CatalogV2Parser.parse(raw, "remote").modules.first { it.id == "android_tool" }

        assertEquals("bottom_nav", module.navigationContribution?.slot)
        assertEquals(15, module.navigationContribution?.order)
    }

    @Test
    fun `rejects runtime and delivery mismatch`() {
        val raw = formalCatalog().replaceFirst(
            "\"deliveryType\": \"zip\"",
            "\"deliveryType\": \"apk\""
        )

        val result = runCatching { CatalogV2Parser.parse(raw, "remote") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("does not support"))
    }

    @Test
    fun `binds archive only when signed catalog and downloader manifest match exactly`() {
        val manifest = ModuleManifest(
            id = "web_tool",
            name = "Web tool",
            versionCode = 1,
            fileName = "web_tool.zip",
            fileSize = 1024,
            sha256 = "a".repeat(64),
            downloadUrl = "https://example.test/tool.zip",
            kind = "web",
            minAppVersionCode = 591
        )
        val module = CatalogV2Parser.parse(formalCatalog(), "remote")
            .modules.first { it.id == "web_tool" }

        assertTrue(CatalogAuthorityMatcher.matches(module, manifest))
        assertFalse(CatalogAuthorityMatcher.matches(module, manifest.copy(sha256 = "b".repeat(64))))

        CatalogPackageTrustRegistry.replace(listOf(module.copy(legacyManifest = manifest)))
        assertTrue(CatalogPackageTrustRegistry.isTrusted(manifest))
        assertFalse(CatalogPackageTrustRegistry.isTrusted(manifest.copy(fileSize = 1025)))
        CatalogPackageTrustRegistry.clearForTest()
    }

    private fun formalCatalog(): String = """
        {
          "schemaVersion": 2,
          "catalogVersion": 7,
          "generatedAt": "2026-07-21T00:00:00Z",
          "modules": [
            {
              "id": "flutter_store",
              "name": "Flutter store",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "flutter",
              "deliveryType": "builtin",
              "route": "/store"
            },
            {
              "id": "web_tool",
              "name": "Web tool",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "web",
              "deliveryType": "zip",
              "minHostVersionCode": 591,
              "entry": "index.html",
              "package": {
                "fileName": "web_tool.zip",
                "fileSize": 1024,
                "downloadUrl": "https://example.test/tool.zip",
                "sha256": "${"a".repeat(64)}"
              }
            },
            {
              "id": "asset_pack",
              "name": "Asset pack",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "asset",
              "deliveryType": "zip",
              "package": {
                "fileName": "asset_pack.zip",
                "downloadUrl": "https://example.test/asset.zip",
                "sha256": "${"b".repeat(64)}"
              }
            },
            {
              "id": "android_tool",
              "name": "Android tool",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "android",
              "deliveryType": "apk",
              "entryClass": "com.example.ToolEntryPoint",
              "package": {
                "fileName": "android_tool.apk",
                "downloadUrl": "https://example.test/android.apk",
                "sha256": "${"c".repeat(64)}"
              }
            },
            {
              "id": "vpn_service",
              "name": "VPN service",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "native_service",
              "deliveryType": "apk",
              "entryClass": "com.example.VpnEntryPoint",
              "serviceType": "vpn",
              "package": {
                "fileName": "vpn_service.apk",
                "downloadUrl": "https://example.test/vpn.apk",
                "sha256": "${"d".repeat(64)}"
              }
            },
            {
              "id": "unity_content",
              "name": "Unity content",
              "versionName": "1.0.0",
              "versionCode": 1,
              "runtimeType": "unity",
              "deliveryType": "content",
              "launcherId": "unity-test-launcher",
              "package": {
                "fileName": "unity_content.zip",
                "downloadUrl": "https://example.test/unity.zip",
                "sha256": "${"e".repeat(64)}"
              }
            }
          ]
        }
    """.trimIndent()
}
