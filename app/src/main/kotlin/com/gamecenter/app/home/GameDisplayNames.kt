package com.gamecenter.app.home

import android.content.Context
import com.gamecenter.app.games.GameRegistry
import java.util.Locale

/**
 * 数据层展示名英文化（计划外补充：GameRegistry/导航目录的游戏名与标签为中文硬编码，
 * 不走资源系统；英文语言下按 id 映射英文名，缺省回落中文名）。
 */
object GameDisplayNames {

    /** 游戏 id → 英文名。 */
    private val GAME_NAMES_EN: Map<String, String> = mapOf(
        "games_hall" to "Games Hall",
        "browser" to "Browser",
        "tools" to "Toolbox",
        "ai" to "AI Assistant",
        "wrongbook" to "Mistakes",
        "vpn" to "VPN",
        "gomoku" to "Gomoku",
        "chinesechess" to "Chinese Chess",
        "go" to "Go",
        "doudizhu" to "Dou Dizhu",
        "blackjack" to "Blackjack",
        "checkers" to "Checkers",
        "dice" to "Dice",
        "rock" to "Rock Paper Scissors",
        "game_2048" to "2048",
        "sudoku" to "Sudoku",
        "klotski" to "Klotski",
        "sokoban" to "Sokoban",
        "pipeline" to "Pipeline",
        "minesweeper" to "Minesweeper",
        "match" to "Match-3",
        "memory" to "Memory Match",
        "breakout" to "Brick Breaker",
        "tiles" to "15 Puzzle",
        "tetris" to "Tetris",
        "snake" to "Snake",
        "flappy" to "Flappy Wings",
        "brotato" to "Brotato",
        "plane" to "Sky Shooter",
        "reaction" to "Reaction Test",
        "guess" to "Number Guess",
        "tic" to "Tic-Tac-Toe",
        "whack" to "Whack-a-Mole",
        "td" to "Egg Defense",
    )

    /** 分类 key → 英文名。 */
    private val CATEGORY_NAMES_EN: Map<String, String> = mapOf(
        "classics" to "Classics",
        "puzzle" to "Puzzle",
        "casual" to "Casual",
    )

    /** 底部导航 id → 英文标签。 */
    private val NAV_TITLES_EN: Map<String, String> = mapOf(
        "games_hall" to "Games",
        "browser" to "Browser",
        "tools" to "Toolbox",
        "ai" to "AI Assistant",
        "vpn" to "VPN",
        "wrongbook" to "Mistakes",
        "profile" to "Profile",
    )

    fun isEnglish(context: Context): Boolean =
        context.resources.configuration.locales[0].language == "en"

    /** 游戏显示名：英文语言下优先映射名，否则回落 Registry 中文名。 */
    fun gameName(context: Context, entry: GameRegistry.Entry): String =
        if (isEnglish(context)) GAME_NAMES_EN[entry.id] ?: entry.name else entry.name

    /** 分类显示名。 */
    fun categoryName(context: Context, categoryKey: String?, fallback: String): String {
        if (!isEnglish(context)) return fallback
        if (categoryKey == null) return "All"
        return CATEGORY_NAMES_EN[categoryKey] ?: fallback
    }

    /** 底部导航标签。 */
    fun navTitle(context: Context, id: String, fallback: String): String =
        if (isEnglish(context)) NAV_TITLES_EN[id] ?: fallback else fallback
}
