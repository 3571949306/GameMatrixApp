package com.gamecenter.app.ai.session

/**
 * 会话上下文与 Prompt 模板管理器。
 *
 * 负责不同端侧模型（ChatML、Gemma、DeepSeek）的 Prompt 格式化、系统提示词注入以及多轮对话历史的 Token 预算滑动窗口裁剪。
 */
object ChatContextManager {

    enum class TemplateFormat {
        /** Qwen2.5 / DeepSeek 等主流中文模型使用的 ChatML 格式 */
        CHAT_ML,
        /** Gemma 系列专用标记格式 */
        GEMMA,
        /** 基础通用纯文本格式 */
        PLAIN
    }

    data class Message(
        val role: String, // "system", "user", "assistant"
        val content: String
    )

    /**
     * 根据模型标识自动推导模板格式
     */
    fun resolveFormat(modelId: String): TemplateFormat {
        val lower = modelId.lowercase()
        return when {
            lower.contains("gemma") -> TemplateFormat.GEMMA
            lower.contains("qwen") || lower.contains("deepseek") -> TemplateFormat.CHAT_ML
            else -> TemplateFormat.CHAT_ML
        }
    }

    /**
     * 粗略估算文本的 Token 数量（中文字符约 0.7 token/字，英文约 0.25 token/词）
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var chineseChars = 0
        var otherChars = 0
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FA5) {
                chineseChars++
            } else {
                otherChars++
            }
        }
        return (chineseChars * 1.2 + otherChars * 0.35).toInt().coerceAtLeast(1)
    }

    /**
     * 对多轮对话历史进行滑动窗口截断，确保输入 Token 不超出端侧限制（默认 1500 Tokens）
     */
    fun pruneMessages(messages: List<Message>, maxBudgetTokens: Int = 1500): List<Message> {
        if (messages.isEmpty()) return emptyList()

        val result = mutableListOf<Message>()
        var currentTokens = 0

        // 从最新消息往前遍历保留
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            val cost = estimateTokens(msg.content)
            if (currentTokens + cost > maxBudgetTokens && result.isNotEmpty()) {
                break
            }
            result.add(0, msg)
            currentTokens += cost
        }
        return result
    }

    /**
     * 将多轮对话与系统提示词格式化为模型所需的输入 Prompt 字符串
     */
    fun formatPrompt(
        messages: List<Message>,
        systemPrompt: String? = null,
        format: TemplateFormat = TemplateFormat.CHAT_ML
    ): String {
        val defaultSystem = systemPrompt ?: "你是一个运行在设备本地的 AI 助手，请用简洁、准确的中文回答用户。"

        return when (format) {
            TemplateFormat.CHAT_ML -> {
                val sb = StringBuilder()
                sb.append("<|im_start|>system\n").append(defaultSystem).append("<|im_end|>\n")
                for (msg in messages) {
                    sb.append("<|im_start|>").append(msg.role).append("\n")
                        .append(msg.content).append("<|im_end|>\n")
                }
                sb.append("<|im_start|>assistant\n")
                sb.toString()
            }
            TemplateFormat.GEMMA -> {
                val sb = StringBuilder()
                for (msg in messages) {
                    val turn = if (msg.role == "assistant") "model" else "user"
                    sb.append("<start_of_turn>").append(turn).append("\n")
                        .append(msg.content).append("<end_of_turn>\n")
                }
                sb.append("<start_of_turn>model\n")
                sb.toString()
            }
            TemplateFormat.PLAIN -> {
                val sb = StringBuilder()
                if (defaultSystem.isNotBlank()) {
                    sb.append("系统提示: ").append(defaultSystem).append("\n\n")
                }
                for (msg in messages) {
                    val prefix = if (msg.role == "assistant") "AI: " else "用户: "
                    sb.append(prefix).append(msg.content).append("\n")
                }
                sb.append("AI: ")
                sb.toString()
            }
        }
    }

    /**
     * 清洗模型输出，截断残留的特殊标记与模型自编的后续对话（杜绝胡言乱语与自言自语）
     */
    fun cleanOutput(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw

        val stopMarkers = listOf(
            "<|im_end|>", "<|im_start|>", "<|endoftext|>", "<end_of_turn>", "<start_of_turn>",
            "\n\n用户：", "\n\n用户:", "\n\nUser:", "\n\nHuman:"
        )
        for (marker in stopMarkers) {
            val idx = cleaned.indexOf(marker)
            if (idx != -1) {
                cleaned = cleaned.substring(0, idx)
            }
        }
        return cleaned.trim()
    }
}

