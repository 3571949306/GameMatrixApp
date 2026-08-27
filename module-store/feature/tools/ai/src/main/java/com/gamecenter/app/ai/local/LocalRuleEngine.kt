package com.gamecenter.app.ai.local

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 本地启发式规则引擎适配器。
 *
 * 作为 0 内存开销、0 MB 权重下载的通用兜底实现，完美对接 ILocalLlmEngine 接口。
 * 支持同步返回与模拟打字机效果的流式输出。
 */
class LocalRuleEngine : ILocalLlmEngine {

    companion object {
        const val ENGINE_TYPE = "rules"
    }

    private var loaded = false

    override fun load(context: Context, modelFile: File, options: ILocalLlmEngine.EngineOptions) {
        // 规则引擎无需权重文件，直接标记为就绪
        loaded = true
    }

    override fun generate(prompt: String): String {
        if (prompt.isBlank()) return "输入内容为空。"

        val lower = prompt.lowercase()
        return when {
            lower.contains("总结") || lower.contains("摘要") || lower.contains("summarize") -> {
                val res = LocalAiProcessor.simpleSummarize(prompt, 5)
                res.content ?: "无法生成摘要"
            }
            lower.contains("翻译") || lower.contains("translate") -> {
                val res = LocalAiProcessor.translateText(prompt)
                res.content ?: "无法翻译该内容"
            }
            lower.contains("错题") || lower.contains("题目") || lower.contains("解析") -> {
                "【本地规则解析】\n1. 考点提炼：" + (LocalAiProcessor.extractKeywords(prompt).content ?: "通用知识点") +
                        "\n2. 建议步骤：请梳理题干条件与未知数，结合定义逐步推导。\n3. 提示：如需深度 AI 分步推导，请在网络可用时使用云端模型或下载端侧 Qwen/Gemma 模型。"
            }
            lower.contains("象棋") || lower.contains("围棋") || lower.contains("棋局") -> {
                "【本地对局简析】\n当前局面攻守平衡。关键要点：控制中路枢纽，避免子力脱节，时刻注意防范对方牵制与反击。"
            }
            else -> {
                val keywords = LocalAiProcessor.extractKeywords(prompt).content ?: ""
                "【本地助手回复】\n已提取核心词：$keywords\n针对您的问题，建议梳理核心诉求。当前处于本地规则模式，若需完整大模型解答，可连接网络或在模型管理中下载离线大模型。"
            }
        }
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        val fullText = generate(prompt)
        // 模拟打字机流式发射，按标点或字符小分块
        val chunkSize = 3
        var index = 0
        while (index < fullText.length) {
            val end = (index + chunkSize).coerceAtMost(fullText.length)
            val chunk = fullText.substring(index, end)
            emit(chunk)
            index = end
            delay(20) // 20ms 模拟生成节奏
        }
    }

    override fun isLoaded(): Boolean = loaded

    override fun getLoadedModelPath(): String = "builtin:rule-engine"

    override fun getEngineType(): String = ENGINE_TYPE

    override fun close() {
        loaded = false
    }
}
