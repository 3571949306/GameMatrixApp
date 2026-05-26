package com.gamecenter.app.modules

import org.json.JSONObject

data class ModuleManifest(
    val id: String,
    val name: String,
    val description: String,
    val versionName: String,
    val versionCode: Int,
    val entryClass: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val downloadUrl: String,
    val fallbackUrl: String = "",
    val githubUrl: String = "",
    val iconUrl: String = "",
    val category: String = "other",
    val minAppVersion: Int = 0,
    val depends: List<String> = emptyList(),
    val type: String = "module",
    val activityClass: String = "",
    val gameId: String = "",
    val gameCategory: String = "",
    val gameDesc: String = "",
    val builtIn: Boolean = false,
    val storeCategory: String = "game",
    val isBaseFramework: Boolean = false,
    val builtInVersionCode: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("entryClass", entryClass)
            put("fileName", fileName)
            put("fileSize", fileSize)
            put("sha256", sha256)
            put("downloadUrl", downloadUrl)
            put("fallbackUrl", fallbackUrl)
            put("githubUrl", githubUrl)
            put("iconUrl", iconUrl)
            put("category", category)
            put("minAppVersion", minAppVersion)
            put("type", type)
            put("activityClass", activityClass)
            put("gameId", gameId)
            put("gameCategory", gameCategory)
            put("gameDesc", gameDesc)
            put("builtIn", builtIn)
            put("storeCategory", storeCategory)
            put("isBaseFramework", isBaseFramework)
            put("builtInVersionCode", builtInVersionCode)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ModuleManifest {
            val dependsArray = json.optJSONArray("depends")
            val dependsList = mutableListOf<String>()
            if (dependsArray != null) {
                for (i in 0 until dependsArray.length()) {
                    dependsList.add(dependsArray.getString(i))
                }
            }
            return ModuleManifest(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                versionName = json.getString("versionName"),
                versionCode = json.getInt("versionCode"),
                entryClass = json.getString("entryClass"),
                fileName = json.getString("fileName"),
                fileSize = json.getLong("fileSize"),
                sha256 = json.getString("sha256"),
                downloadUrl = json.getString("downloadUrl"),
                fallbackUrl = json.optString("fallbackUrl", ""),
                githubUrl = json.optString("githubUrl", ""),
                iconUrl = json.optString("iconUrl", ""),
                category = json.optString("category", "other"),
                minAppVersion = json.optInt("minAppVersion", 0),
                depends = dependsList,
                type = json.optString("type", "module"),
                activityClass = json.optString("activityClass", ""),
                gameId = json.optString("gameId", ""),
                gameCategory = json.optString("gameCategory", ""),
                gameDesc = json.optString("gameDesc", ""),
                builtIn = json.optBoolean("builtIn", false),
                storeCategory = json.optString("storeCategory", "game"),
                isBaseFramework = json.optBoolean("isBaseFramework", false),
                builtInVersionCode = json.optInt("builtInVersionCode", 0)
            )
        }

        fun fromJsonArray(jsonStr: String): List<ModuleManifest> {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ModuleManifest>()
            for (i in 0 until array.length()) {
                list.add(fromJson(array.getJSONObject(i)))
            }
            return list
        }
    }

    fun getAllDownloadUrls(): List<String> {
        val urls = mutableListOf(downloadUrl)
        if (fallbackUrl.isNotEmpty() && fallbackUrl != downloadUrl) {
            urls.add(fallbackUrl)
        }
        if (githubUrl.isNotEmpty() && githubUrl != downloadUrl && githubUrl != fallbackUrl) {
            urls.add(githubUrl)
        }
        return urls
    }
}
