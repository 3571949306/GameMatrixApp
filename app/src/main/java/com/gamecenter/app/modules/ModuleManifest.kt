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
            val rawId = json.getString("id")
            val rawName = json.getString("name")
            val rawDesc = json.optString("description", "")
            
            val localizedName = when (rawId) {
                "games_hall" -> "游戏大厅"
                "browser" -> "浏览器"
                "tools" -> "工具箱"
                "ai" -> "AI助手"
                "vpn" -> "VPN服务"
                "checkers" -> "跳棋"
                "dice" -> "骰子"
                "rps", "rock" -> "石头剪刀布"
                "chinesechess", "chinese_chess" -> "中国象棋"
                "go" -> "围棋"
                "blackjack" -> "21点"
                "doudizhu" -> "斗地主"
                "game_2048", "2048" -> "2048"
                "breakout" -> "打砖块"
                "brotato" -> "土豆兄弟"
                "gomoku" -> "五子棋"
                "sokoban" -> "推箱子"
                "klotski" -> "华容道"
                "tetris" -> "俄罗斯方块"
                "snake" -> "贪吃蛇"
                "minesweeper" -> "扫雷"
                "flappy" -> "像素鸟"
                "tic" -> "井字棋"
                "whack" -> "打地鼠"
                "match" -> "消消乐"
                "memory" -> "记忆翻牌"
                "pipeline" -> "水管工"
                "plane" -> "飞机大战"
                "reaction" -> "反应测试"
                "sudoku" -> "数独"
                "tiles" -> "瓷砖拼图"
                "tts_voice" -> "语音朗读"
                "guess" -> "猜数字"
                else -> rawName
            }
            
            val localizedDesc = when (rawId) {
                "games_hall" -> "聚合宿主游戏与已下载游戏模块的内置入口。"
                "browser" -> "支持多标签、书签、下载管理及桌面模式的浏览器模块。"
                "tools" -> "提供设备诊断、二维码工具、电池与传感器监控的实用工具箱。"
                "ai" -> "集成对话历史及工具支持的 AI 智能助手模块。"
                "vpn" -> "安全网络连接服务模块。"
                "checkers" -> "经典跳棋游戏。"
                "dice" -> "趣味骰子游戏。"
                "rps", "rock" -> "经典石头剪刀布。"
                "chinesechess", "chinese_chess" -> "经典中国象棋。"
                "go" -> "经典围棋游戏。"
                "blackjack" -> "经典21点纸牌游戏。"
                "doudizhu" -> "经典斗地主纸牌游戏。"
                "game_2048", "2048" -> "经典数字合成益智游戏。"
                "breakout" -> "经典打砖块休闲游戏。"
                "brotato" -> "经典生存射击游戏。"
                "gomoku" -> "经典五子棋对战游戏。"
                "sokoban" -> "经典推箱子解谜游戏。"
                "klotski" -> "经典华容道益智游戏。"
                "tetris" -> "经典俄罗斯方块。"
                "snake" -> "经典贪吃蛇休闲游戏。"
                "minesweeper" -> "经典扫雷逻辑游戏。"
                "flappy" -> "经典像素鸟躲避游戏。"
                "tic" -> "经典井字棋策略游戏。"
                "whack" -> "经典打地鼠反应游戏。"
                "match" -> "经典三消益智游戏。"
                "memory" -> "经典记忆翻牌游戏。"
                "pipeline" -> "经典水管连接解谜游戏。"
                "plane" -> "经典飞机射击游戏。"
                "reaction" -> "测试你的反应速度。"
                "sudoku" -> "经典数独逻辑游戏。"
                "tiles" -> "经典瓷砖拼图游戏。"
                "tts_voice" -> "文字转语音朗读模块。"
                "guess" -> "经典猜数字益智游戏。"
                else -> rawDesc
            }
            
            return ModuleManifest(
                id = rawId,
                name = localizedName,
                description = localizedDesc,
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
                gameDesc = if (json.optString("gameDesc", "").isNotEmpty()) localizedDesc else "",
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
