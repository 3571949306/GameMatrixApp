package com.gamecenter.app.core.modulehost

import org.json.JSONObject

/**
 * 模块清单兼容包装（ModuleManifest v3）。
 *
 * P0 改造：规范定义已迁移至 `com.gamecenter.app.core.common.ModuleManifest`。
 * :core:module-host 保留同名类型别名，内部逻辑可直接引用核心模型。
 */
typealias ModuleManifest = com.gamecenter.app.core.common.ModuleManifest

object ModuleManifestCompat {

    fun fromJson(json: JSONObject): ModuleManifest = ModuleManifest.fromJson(json)

    fun fromJsonArray(jsonStr: String): List<ModuleManifest> = ModuleManifest.fromJsonArray(jsonStr)
}
