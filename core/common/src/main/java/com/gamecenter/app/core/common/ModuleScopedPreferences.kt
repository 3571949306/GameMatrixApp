package com.gamecenter.app.core.common

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 模块作用域 SharedPreferences 辅助（Phase 3 数据隔离强约束）。
 *
 * 隔离背景（2026-08-26 审计）：所有模块共享宿主的 `shared_prefs/` 目录，仅靠 SP 文件名
 * 命名纪律隔离（各模块名当前均唯一），脆弱——恶意/越权模块可用任意文件名读取他人 SP。
 *
 * 本辅助把 SP 文件名**强制带 moduleId 前缀**，并从 API 层面**禁止模块伪造其它模块的作用域
 * 前缀**（同名/越权断言），使"模块只能访问自身 SP"成为结构约束而非约定。
 *
 * 作用域文件名形态：`mod_<moduleId>__<baseName>`。
 *
 * 用法（模块侧）：
 *   val prefs = ModuleScopedPreferences.get(context, moduleId, "history")
 * 迁移旧式扁平 SP（仅一次）：
 *   ModuleScopedPreferences.migrateFrom(context, moduleId, oldFlatName)
 */
object ModuleScopedPreferences {

    private const val TAG = "ModuleScopedPreferences"
    private const val PREFIX = "mod_"   // 作用域前缀
    private const val SEP = "__"        // moduleId 与 baseName 分隔符
    private const val MIGRATE_FLAG = "__migrated__"

    /**
     * 由 moduleId + baseName 推导作用域 SP 文件名。
     * 形如 `mod_<moduleId>__<baseName>`。
     *
     * 同名/越权断言：baseName 不得自带作用域前缀或分隔符，防止模块伪造其它模块作用域。
     */
    @JvmStatic
    fun scopedName(moduleId: String, baseName: String): String {
        require(moduleId.isNotBlank()) { "moduleId 不可为空" }
        require(baseName.isNotBlank()) { "baseName 不可为空" }
        require(!baseName.startsWith(PREFIX)) {
            "baseName 不得以作用域前缀 '$PREFIX' 开头（禁止伪造其它模块作用域）"
        }
        require(!baseName.contains(SEP)) {
            "baseName 不得包含分隔符 '$SEP'（禁止伪造其它模块作用域）"
        }
        return "$PREFIX${moduleId}$SEP$baseName"
    }

    /**
     * 获取模块作用域 SharedPreferences。仅当 moduleId + baseName 组合合法时返回，
     * 否则抛异常（断言越权作用域访问）。
     */
    @JvmStatic
    fun get(context: Context, moduleId: String, baseName: String): SharedPreferences {
        val name = scopedName(moduleId, baseName)
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /**
     * 迁移兼容：把旧式（无作用域）扁平 SP 的数据复制到作用域 SP。
     * 以 scoped SP 内的 [MIGRATE_FLAG] 标记保证仅迁移一次。返回是否实际执行了迁移。
     *
     * 注意：调用方需保证 oldFlatName 是该模块独占的扁平名，避免跨模块误迁移。
     */
    @JvmStatic
    @JvmOverloads
    fun migrateFrom(
        context: Context,
        moduleId: String,
        oldFlatName: String,
        newBaseName: String = oldFlatName
    ): Boolean {
        val newName = scopedName(moduleId, newBaseName)
        val newPrefs = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
        if (newPrefs.contains(MIGRATE_FLAG)) return false

        val oldPrefs = context.getSharedPreferences(oldFlatName, Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) {
            newPrefs.edit().putBoolean(MIGRATE_FLAG, true).apply()
            return false
        }

        newPrefs.edit().apply {
            for ((k, v) in oldPrefs.all) {
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Float -> putFloat(k, v)
                    is Long -> putLong(k, v)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(k, v as Set<String>)
                    else -> Log.w(TAG, "migrateFrom: 跳过不支持的类型 key=$k (${v?.javaClass})")
                }
            }
            putBoolean(MIGRATE_FLAG, true)
        }.apply()
        Log.i(TAG, "migrateFrom: 已迁移 $oldFlatName -> $newName")
        return true
    }
}
