package com.gamecenter.app.modules

import org.json.JSONObject

/**
 * 模块清单兼容包装。
 *
 * P0 改造：规范定义已迁移至 `com.gamecenter.app.core.common.ModuleManifest`。
 * 此处仅保留同名的类型别名和兼容方法，避免现有调用方大面积改动。
 */
typealias ModuleManifest = com.gamecenter.app.core.common.ModuleManifest

object ModuleManifestCompat {

    fun fromJson(json: JSONObject): ModuleManifest = ModuleManifest.fromJson(json)

    fun fromJsonArray(jsonStr: String): List<ModuleManifest> = ModuleManifest.fromJsonArray(jsonStr)
}
