package com.gamecenter.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份与恢复助手（Batch 11-2 / DATA_BACKUP_RESTORE）。
 *
 * 导出范围：
 * 1. {@code game_usage}（收藏、最近游玩、胜负、时长、最高分、用户评分）
 * 2. {@code achievements}（成就解锁状态）
 * 3. {@code app_settings}（主题、字号、声音开关、语言等）
 *
 * 文件格式：单个 JSON 对象，顶层 key 为 prefs 名，value 为 key-value 映射。
 *
 * 不导出：模块加载相关的运行时缓存（不在以上三个文件中）。
 */
object DataBackupHelper {

    private const val VERSION_KEY = "__version__"
    private const val VERSION_VALUE = 1
    private const val TIMESTAMP_KEY = "__timestamp__"
    private const val PREFS_KEY = "prefs"

    private val BACKUP_PREFS_NAMES = arrayOf("game_usage", "achievements", "app_settings")

    /**
     * 将 3 个 SharedPreferences 序列化为 JSON 并写入 [outputStream]。
     * 调用方负责关闭流。
     *
     * @return 写入的字节数（用于成功提示）
     */
    fun exportToJson(context: Context, outputStream: OutputStream): Long {
        val ctx = context.applicationContext
        val root = JSONObject()
        root.put(VERSION_KEY, VERSION_VALUE)
        root.put(TIMESTAMP_KEY, System.currentTimeMillis())

        val prefsObj = JSONObject()
        for (name in BACKUP_PREFS_NAMES) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefsObj.put(name, encodePrefs(sp.all))
        }
        root.put(PREFS_KEY, prefsObj)

        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        outputStream.write(bytes)
        outputStream.flush()
        return bytes.size.toLong()
    }

    /**
     * 从 [inputStream] 读取 JSON 并覆盖写入对应 SharedPreferences。
     * 调用方负责关闭流。
     *
     * @return 导入的 key 数量（用于成功提示）
     */
    fun importFromJson(context: Context, inputStream: InputStream): Int {
        val ctx = context.applicationContext
        val raw = inputStream.readBytes().toString(Charsets.UTF_8)
        val root = JSONObject(raw)
        if (!root.has(PREFS_KEY)) {
            throw IllegalArgumentException("missing prefs key")
        }
        val prefsObj = root.getJSONObject(PREFS_KEY)
        var importedCount = 0

        for (name in BACKUP_PREFS_NAMES) {
            if (!prefsObj.has(name)) continue
            val map = prefsObj.getJSONObject(name)
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val ed = sp.edit()
            ed.clear()  // 覆盖式导入
            val keys = map.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = map.get(k)
                when (v) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Float -> ed.putFloat(k, v)
                    is String -> ed.putString(k, v)
                    is JSONArray -> ed.putStringSet(k, toStringSet(v))
                    else -> ed.putString(k, v.toString())
                }
                importedCount++
            }
            ed.apply()
        }
        return importedCount
    }

    /** 生成默认备份文件名：gamematrix_backup_yyyy-MM-dd_HHmm.json */
    fun defaultFilename(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "gamematrix_backup_$ts.json"
    }

    // ============ 内部工具 ============

    private fun encodePrefs(all: Map<String, *>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in all) {
            when (v) {
                is Boolean, is Int, is Long, is String, is Double, is Float -> obj.put(k, v)
                is Set<*> -> {
                    val arr = JSONArray()
                    v.forEach { arr.put(it.toString()) }
                    obj.put(k, arr)
                }
                else -> obj.put(k, v.toString())
            }
        }
        return obj
    }

    private fun toStringSet(arr: JSONArray): Set<String> {
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            out.add(arr.getString(i))
        }
        return out
    }
}
