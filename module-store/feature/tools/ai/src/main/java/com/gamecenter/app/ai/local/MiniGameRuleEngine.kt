package com.gamecenter.app.ai.local

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * MiniGameRuleEngine provides deterministic responses for simple mini‑games.
 * Implements ILocalLlmEngine so it can be used interchangeably with other engines.
 * Supported games are identified by a `gameId` and receive a simple `input` string.
 *
 * Prompt format expected by generate()/generateStream():
 *   "gameId=<id>;input=<user input>"
 * Example: "gameId=rps;input=rock"
 */
class MiniGameRuleEngine : ILocalLlmEngine {
    companion object {
        const val ENGINE_TYPE = "mini-game"
    }

    private var loaded = false

    override fun load(context: Context, modelFile: File, options: ILocalLlmEngine.EngineOptions) {
        // No model file required for rule‑based engine
        loaded = true
    }

    private fun parsePrompt(prompt: String): Pair<String, String>? {
        val parts = prompt.split(";")
        if (parts.size != 2) return null
        val gamePart = parts[0].trim().removePrefix("gameId=")
        val inputPart = parts[1].trim().removePrefix("input=")
        return Pair(gamePart, inputPart)
    }

    private fun handleRps(input: String): String {
        return when (input.lowercase()) {
            "rock" -> "你出了 Rock，我出 Paper，我赢了！"
            "paper" -> "你出了 Paper，我出 Scissors，我赢了！"
            "scissors" -> "你出了 Scissors，我出 Rock，我赢了！"
            else -> "无效的出招，请发送 rock、paper 或 scissors。"
        }
    }

    private fun handleGuess(input: String): String {
        return try {
            val guess = input.trim().toInt()
            when {
                guess < 42 -> "太小了！"
                guess > 42 -> "太大了！"
                else -> "猜对了！答案就是 42。"
            }
        } catch (e: Exception) {
            "请发送一个数字作为猜测。"
        }
    }

    private fun handleTicTacToe(input: String): String {
        // Placeholder implementation – echo the input.
        return "TicTacToe 收到指令: $input"
    }

    override fun generate(prompt: String): String {
        if (!loaded) return "Engine not loaded."
        val parsed = parsePrompt(prompt) ?: return "提示格式错误，期望 'gameId=<id>;input=<data>'。"
        val (gameId, input) = parsed
        return when (gameId.lowercase()) {
            "rps" -> handleRps(input)
            "guess" -> handleGuess(input)
            "tictactoe" -> handleTicTacToe(input)
            else -> "不支持的游戏：$gameId"
        }
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        val full = generate(prompt)
        val chunkSize = 4
        var index = 0
        while (index < full.length) {
            val end = (index + chunkSize).coerceAtMost(full.length)
            emit(full.substring(index, end))
            index = end
            delay(20)
        }
    }

    override fun isLoaded(): Boolean = loaded

    override fun getLoadedModelPath(): String = "builtin:mini-game-rule-engine"

    override fun getEngineType(): String = ENGINE_TYPE

    override fun close() { loaded = false }
}
