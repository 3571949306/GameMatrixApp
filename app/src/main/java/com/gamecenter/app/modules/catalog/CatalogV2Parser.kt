package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.NavigationContribution
import org.json.JSONArray
import org.json.JSONObject

object CatalogV2Parser {

    fun parse(rawJson: String, source: String = "unknown"): CatalogV2 {
        val root = JSONObject(rawJson)
        val modulesJson = root.optJSONArray("modules")
            ?: throw IllegalArgumentException("Catalog does not contain a modules array")
        val usesRuntimeContract = (0 until modulesJson.length()).all { index ->
            modulesJson.optJSONObject(index)?.let { module ->
                module.has("runtimeType") && module.has("deliveryType")
            } == true
        }
        // The deployed display catalog also calls itself schemaVersion=2 but
        // predates the multi-runtime contract. Keep it on the lossless legacy
        // adapter until every entry declares runtime and delivery explicitly.
        if (root.optInt("schemaVersion", 0) < 2 || !usesRuntimeContract) {
            return LegacyCatalogAdapter.adapt(rawJson, source)
        }
        val catalog = CatalogV2(
            schemaVersion = root.optInt("schemaVersion", 2),
            catalogVersion = root.optInt("catalogVersion", 0),
            generatedAt = root.optString("generatedAt", ""),
            source = source,
            // A signature-validated on-disk catalog is a trusted remote cache, not an offline mode.
            offline = source !in setOf("remote", "signed_cache"),
            modules = parseModules(modulesJson)
        )
        val validation = CatalogSchemaValidator.validate(catalog)
        if (!validation.isValid) {
            throw IllegalArgumentException(
                validation.issues.joinToString("; ") { "${it.path}: ${it.message}" }
            )
        }
        return catalog
    }

    private fun parseModules(array: JSONArray): List<CatalogModule> = buildList {
        for (index in 0 until array.length()) {
            add(parseModule(array.getJSONObject(index)))
        }
    }

    private fun parseModule(json: JSONObject): CatalogModule {
        val runtime = RuntimeType.fromWire(json.optString("runtimeType"))
            ?: throw IllegalArgumentException("Unknown runtimeType for ${json.optString("id")}")
        val delivery = DeliveryType.fromWire(json.optString("deliveryType"))
            ?: throw IllegalArgumentException("Unknown deliveryType for ${json.optString("id")}")
        val packageJson = json.optJSONObject("package") ?: JSONObject()
        val assetsJson = json.optJSONObject("assets") ?: JSONObject()
        return CatalogModule(
            id = json.getString("id"),
            name = json.getString("name"),
            shortDescription = json.optString("shortDescription", ""),
            description = json.optString("description", ""),
            versionName = json.optString("versionName", "1.0.0"),
            versionCode = json.optInt("versionCode", 1),
            runtimeType = runtime,
            deliveryType = delivery,
            route = json.optString("route", ""),
            entryClass = json.optString("entryClass", ""),
            entry = json.optString("entry", ""),
            serviceType = json.optString("serviceType", ""),
            launcherId = json.optString("launcherId", ""),
            enabled = json.optBoolean("enabled", true),
            required = json.optBoolean("required", false),
            featured = json.optBoolean("featured", false),
            sortOrder = json.optInt("sortOrder", 0),
            minHostVersionCode = json.optInt("minHostVersionCode", 0),
            maxHostVersionCode = json.optInt("maxHostVersionCode", 0),
            category = json.optString("category", "other"),
            permissions = parsePermissions(json.optJSONArray("permissionsDescription"), json.optJSONArray("permissions")),
            dependencies = stringList(json.optJSONArray("dependencies")),
            tags = stringList(json.optJSONArray("tags")),
            screenshots = stringList(json.optJSONArray("screenshots")),
            changelog = stringList(json.optJSONArray("changelog")),
            navigationContribution = json.optJSONObject("navigationContribution")
                ?.let(NavigationContribution::fromJson),
            packageInfo = CatalogPackage(
                fileName = packageJson.optString("fileName", ""),
                fileSize = packageJson.optLong("fileSize", 0),
                downloadUrl = packageJson.optString("downloadUrl", ""),
                fallbackUrl = packageJson.optString("fallbackUrl", ""),
                githubUrl = packageJson.optString("githubUrl", ""),
                sha256 = packageJson.optString("sha256", ""),
                signature = packageJson.optString("signature", "")
            ),
            assets = CatalogAssets(
                url = assetsJson.optString("url", ""),
                sha256 = assetsJson.optString("sha256", ""),
                signature = assetsJson.optString("signature", "")
            )
        )
    }

    private fun parsePermissions(descriptions: JSONArray?, ids: JSONArray?): List<CatalogPermission> {
        if (descriptions != null) {
            return buildList {
                for (index in 0 until descriptions.length()) {
                    val item = descriptions.optJSONObject(index) ?: continue
                    add(CatalogPermission(item.optString("id"), item.optString("description")))
                }
            }
        }
        return stringList(ids).map(::CatalogPermission)
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
