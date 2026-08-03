package com.gamecenter.app.ui

import android.content.Context
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.entity.AchievementEntity
import com.gamecenter.app.database.entity.GameUsageEntity
import kotlinx.coroutines.runBlocking
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
 * 1. `app_settings` SharedPreferences（主题、字号、声音开关、语言等）
 * 2. Room 表 `achievements`（成就解锁状态，原 `game_achievements` / `achievements` SP）
 * 3. Room 表 `game_usage`（收藏、最近游玩、胜负、时长、最高分、用户评分，原 `game_usage` SP）
 *
 * 文件格式（version=2）：
 * ```json
 * {
 *   "__version__": 2,
 *   "__timestamp__": <ms>,
 *   "prefs": { "app_settings": { ... } },
 *   "room_data": {
 *     "achievements": [ { ...AchievementEntity }, ... ],
 *     "game_usage":   [ { ...GameUsageEntity }, ... ]
 *   }
 * }
 * ```
 *
 * 向后兼容：导入时若检测到旧格式（version=1，`prefs` 中含 `game_usage` / `achievements`
 * SP 数据），会将其解析并写入对应的 Room 表，而非恢复到 SP。
 *
 * Room 操作为 suspend，由于调用方（ProfileFragment 的后台线程、CloudSyncManager 的
 * 非 suspend 函数）均不在协程上下文中，内部使用 [runBlocking] 阻塞读取/写入；
 * 调用方已在后台线程执行，不会阻塞 UI。
 */
object DataBackupHelper {

    private const val VERSION_KEY = "__version__"
    private const val VERSION_VALUE = 2
    private const val TIMESTAMP_KEY = "__timestamp__"
    private const val PREFS_KEY = "prefs"
    private const val ROOM_DATA_KEY = "room_data"
    private const val ROOM_ACHIEVEMENTS_KEY = "achievements"
    private const val ROOM_GAME_USAGE_KEY = "game_usage"

    /**
     * 仍以 SharedPreferences 备份的文件名。
     *
     * `game_usage` 与 `achievements` 已迁移至 Room，不再从此处备份；
     * 但导入时仍需识别旧备份文件中的同名 SP 数据以写入 Room（见 [importFromJson]）。
     */
    private val BACKUP_PREFS_NAMES = arrayOf("app_settings")

    // ============ 旧 SP key 前缀（仅用于导入旧备份文件的兼容解析） ============
    private const val LEGACY_PLAY_COUNT = "play_count_"
    private const val LEGACY_WIN_COUNT = "win_count_"
    private const val LEGACY_LOSS_COUNT = "loss_count_"
    private const val LEGACY_PLAY_TIME = "play_time_"
    private const val LEGACY_HIGH_SCORE = "high_score_"
    private const val LEGACY_LAST_PLAYED = "last_played_"
    private const val LEGACY_FAVORITES_STR = "favorites_str"
    private const val LEGACY_FAVORITES = "favorites"
    private const val LEGACY_RATING_PREFIX = "rating_"
    private const val LEGACY_ACHIEVEMENT_PREFIX = "achievement_"
    private const val LEGACY_ACHIEVEMENT_UNLOCKED_SUFFIX = "_unlocked"

    /**
     * 将 SharedPreferences（app_settings）与 Room 表（achievements, game_usage）
     * 序列化为 JSON 并写入 [outputStream]。调用方负责关闭流。
     *
     * @return 写入的字节数（用于成功提示）
     */
    fun exportToJson(context: Context, outputStream: OutputStream): Long {
        val ctx = context.applicationContext
        val root = JSONObject()
        root.put(VERSION_KEY, VERSION_VALUE)
        root.put(TIMESTAMP_KEY, System.currentTimeMillis())

        // 1) SharedPreferences 部分
        val prefsObj = JSONObject()
        for (name in BACKUP_PREFS_NAMES) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefsObj.put(name, encodePrefs(sp.all))
        }
        root.put(PREFS_KEY, prefsObj)

        // 2) Room 部分（suspend 调用，runBlocking 阻塞；调用方在后台线程）
        val roomData = JSONObject()
        runBlocking {
            val db = AppDatabase.getDatabase(ctx)
            val achievements = db.achievementDao().getAll()
            val usages = db.gameUsageDao().getAll()
            roomData.put(ROOM_ACHIEVEMENTS_KEY, encodeAchievements(achievements))
            roomData.put(ROOM_GAME_USAGE_KEY, encodeGameUsage(usages))
        }
        root.put(ROOM_DATA_KEY, roomData)

        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        outputStream.write(bytes)
        outputStream.flush()
        return bytes.size.toLong()
    }

    /**
     * 从 [inputStream] 读取 JSON 并覆盖写入对应 SharedPreferences 与 Room 表。
     * 调用方负责关闭流。
     *
     * 兼容性：
     * - 新格式（version>=2）：恢复 `prefs.app_settings` + `room_data` 两张表
     * - 旧格式（version=1）：`prefs.game_usage` / `prefs.achievements` 中的 SP 数据
     *   会被解析并写入 Room 表，而非恢复到 SP
     *
     * @return 导入的条目数（SP key + Room 行，用于成功提示）
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

        // 1) 恢复仍以 SP 存储的部分（app_settings 等）
        for (name in BACKUP_PREFS_NAMES) {
            if (!prefsObj.has(name)) continue
            importedCount += restorePrefs(ctx, name, prefsObj.getJSONObject(name))
        }

        // 2) Room 部分：优先读取新格式的 room_data
        val hasRoomData = root.has(ROOM_DATA_KEY)
        if (hasRoomData) {
            val roomData = root.getJSONObject(ROOM_DATA_KEY)
            importedCount += restoreRoomData(
                ctx,
                if (roomData.has(ROOM_ACHIEVEMENTS_KEY)) roomData.getJSONArray(ROOM_ACHIEVEMENTS_KEY) else null,
                if (roomData.has(ROOM_GAME_USAGE_KEY)) roomData.getJSONArray(ROOM_GAME_USAGE_KEY) else null
            )
        }

        // 3) 向后兼容：旧格式备份把 game_usage / achievements 放在 prefs 中（version=1）
        //    将其解析后写入 Room，而非恢复到 SP。
        if (prefsObj.has(ROOM_GAME_USAGE_KEY)) {
            importedCount += importLegacyGameUsage(ctx, prefsObj.getJSONObject(ROOM_GAME_USAGE_KEY))
        }
        if (prefsObj.has(ROOM_ACHIEVEMENTS_KEY)) {
            importedCount += importLegacyAchievements(ctx, prefsObj.getJSONObject(ROOM_ACHIEVEMENTS_KEY))
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

    private fun encodeAchievements(list: List<AchievementEntity>): JSONArray {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("achievementId", a.achievementId)
            o.put("gameId", a.gameId)
            o.put("unlocked", a.unlocked)
            o.put("progress", a.progress)
            o.put("maxProgress", a.maxProgress)
            o.put("unlockedAt", a.unlockedAt)
            o.put("title", a.title)
            o.put("description", a.description)
            arr.put(o)
        }
        return arr
    }

    private fun encodeGameUsage(list: List<GameUsageEntity>): JSONArray {
        val arr = JSONArray()
        for (u in list) {
            val o = JSONObject()
            o.put("gameId", u.gameId)
            o.put("playCount", u.playCount)
            o.put("wins", u.wins)
            o.put("losses", u.losses)
            o.put("totalPlayTimeMs", u.totalPlayTimeMs)
            o.put("highScore", u.highScore)
            o.put("userRating", u.userRating)
            o.put("isFavorite", u.isFavorite)
            o.put("lastPlayedAt", u.lastPlayedAt)
            arr.put(o)
        }
        return arr
    }

    /** 恢复单个 SP 文件（覆盖式），返回写入的 key 数量。 */
    private fun restorePrefs(ctx: Context, name: String, map: JSONObject): Int {
        val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val ed = sp.edit()
        ed.clear()  // 覆盖式导入
        var count = 0
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
            count++
        }
        ed.apply()
        return count
    }

    /** 将 room_data 中的两张表写入 Room，返回写入行数。 */
    private fun restoreRoomData(
        ctx: Context,
        achievements: JSONArray?,
        gameUsage: JSONArray?
    ): Int {
        val db = AppDatabase.getDatabase(ctx)
        var count = 0
        runBlocking {
            if (achievements != null && achievements.length() > 0) {
                val list = ArrayList<AchievementEntity>(achievements.length())
                for (i in 0 until achievements.length()) {
                    list += decodeAchievement(achievements.getJSONObject(i))
                }
                db.achievementDao().upsertAll(list)
                count += list.size
            }
            if (gameUsage != null && gameUsage.length() > 0) {
                val list = ArrayList<GameUsageEntity>(gameUsage.length())
                for (i in 0 until gameUsage.length()) {
                    list += decodeGameUsage(gameUsage.getJSONObject(i))
                }
                db.gameUsageDao().upsertAll(list)
                count += list.size
            }
        }
        return count
    }

    private fun decodeAchievement(o: JSONObject): AchievementEntity {
        return AchievementEntity(
            achievementId = o.optString("achievementId"),
            gameId = o.optString("gameId", ""),
            unlocked = o.optBoolean("unlocked", false),
            progress = o.optInt("progress", 0),
            maxProgress = o.optInt("maxProgress", 0),
            unlockedAt = o.optLong("unlockedAt", 0L),
            title = o.optString("title", ""),
            description = o.optString("description", "")
        )
    }

    private fun decodeGameUsage(o: JSONObject): GameUsageEntity {
        return GameUsageEntity(
            gameId = o.optString("gameId"),
            playCount = o.optInt("playCount", 0),
            wins = o.optInt("wins", 0),
            losses = o.optInt("losses", 0),
            totalPlayTimeMs = o.optLong("totalPlayTimeMs", 0L),
            highScore = o.optLong("highScore", 0L),
            userRating = o.optInt("userRating", 0),
            isFavorite = o.optBoolean("isFavorite", false),
            lastPlayedAt = o.optLong("lastPlayedAt", 0L)
        )
    }

    /**
     * 兼容旧格式：将 `game_usage` SP 的 key-value 映射解析为 [GameUsageEntity] 列表后写入 Room。
     *
     * 解析的 key 形态（与 GameUsageStore 一致）：
     * `play_count_{id}` / `win_count_{id}` / `loss_count_{id}` / `play_time_{id}` /
     * `high_score_{id}` / `last_played_{id}` / `rating_{id}` /
     * `favorites_str`（逗号分隔）/ `favorites`（旧 StringSet，JSONArray）
     */
    private fun importLegacyGameUsage(ctx: Context, prefsMap: JSONObject): Int {
        val favoriteIds = HashSet<String>()
        if (prefsMap.has(LEGACY_FAVORITES_STR)) {
            val s = prefsMap.optString(LEGACY_FAVORITES_STR, "")
            if (s.isNotEmpty()) {
                s.split(",").forEach { id -> if (id.isNotEmpty()) favoriteIds.add(id) }
            }
        } else if (prefsMap.has(LEGACY_FAVORITES)) {
            val fav = prefsMap.get(LEGACY_FAVORITES)
            if (fav is JSONArray) {
                for (i in 0 until fav.length()) favoriteIds.add(fav.getString(i))
            }
        }

        val byGameId = HashMap<String, GameUsageEntity>()
        val keys = prefsMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val gameId = extractGameId(key)
            if (gameId.isNullOrEmpty()) continue
            val entity = byGameId.getOrPut(gameId) { GameUsageEntity(gameId = gameId) }
            val value = prefsMap.get(key)
            when {
                key.startsWith(LEGACY_PLAY_COUNT) && value is Int ->
                    byGameId[gameId] = entity.copy(playCount = value)
                key.startsWith(LEGACY_WIN_COUNT) && value is Int ->
                    byGameId[gameId] = entity.copy(wins = value)
                key.startsWith(LEGACY_LOSS_COUNT) && value is Int ->
                    byGameId[gameId] = entity.copy(losses = value)
                key.startsWith(LEGACY_PLAY_TIME) && (value is Long || value is Int) ->
                    byGameId[gameId] = entity.copy(totalPlayTimeMs = (value as Number).toLong())
                key.startsWith(LEGACY_HIGH_SCORE) && (value is Int || value is Long) ->
                    byGameId[gameId] = entity.copy(highScore = (value as Number).toLong())
                key.startsWith(LEGACY_LAST_PLAYED) && (value is Long || value is Int) ->
                    byGameId[gameId] = entity.copy(lastPlayedAt = (value as Number).toLong())
                key.startsWith(LEGACY_RATING_PREFIX) && value is Int ->
                    byGameId[gameId] = entity.copy(userRating = value)
            }
        }

        // 合并收藏标记
        if (favoriteIds.isNotEmpty()) {
            for (id in favoriteIds) {
                val entity = byGameId.getOrPut(id) { GameUsageEntity(gameId = id) }
                byGameId[id] = entity.copy(isFavorite = true)
            }
        }

        if (byGameId.isEmpty()) return 0
        val list: List<GameUsageEntity> = ArrayList(byGameId.values)
        runBlocking {
            AppDatabase.getDatabase(ctx).gameUsageDao().upsertAll(list)
        }
        return list.size
    }

    /** 从旧格式 SP key 中提取 gameId，非游戏级 key 返回 null。 */
    private fun extractGameId(key: String): String? {
        val prefixes = listOf(
            LEGACY_PLAY_COUNT, LEGACY_WIN_COUNT, LEGACY_LOSS_COUNT,
            LEGACY_PLAY_TIME, LEGACY_HIGH_SCORE, LEGACY_LAST_PLAYED, LEGACY_RATING_PREFIX
        )
        for (p in prefixes) {
            if (key.startsWith(p) && key.length > p.length) {
                return key.substring(p.length)
            }
        }
        return null
    }

    /**
     * 兼容旧格式：将 `achievements` SP 的 key-value 映射解析为 [AchievementEntity] 列表后写入 Room。
     *
     * 解析的 key 形态（与 AchievementCenterActivity 一致）：
     * `achievement_{fullKey}_unlocked` (boolean) → achievementId = fullKey, unlocked = value
     */
    private fun importLegacyAchievements(ctx: Context, prefsMap: JSONObject): Int {
        val list = ArrayList<AchievementEntity>()
        val keys = prefsMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith(LEGACY_ACHIEVEMENT_PREFIX) || !key.endsWith(LEGACY_ACHIEVEMENT_UNLOCKED_SUFFIX)) {
                continue
            }
            val inner = key.substring(
                LEGACY_ACHIEVEMENT_PREFIX.length,
                key.length - LEGACY_ACHIEVEMENT_UNLOCKED_SUFFIX.length
            )
            if (inner.isEmpty()) continue
            val unlocked = prefsMap.optBoolean(key, false)
            list.add(AchievementEntity(achievementId = inner, unlocked = unlocked))
        }
        if (list.isEmpty()) return 0
        runBlocking {
            AppDatabase.getDatabase(ctx).achievementDao().upsertAll(list)
        }
        return list.size
    }
}
