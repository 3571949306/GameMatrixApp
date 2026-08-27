package com.gamecenter.app.ai.bridge

import android.content.Context
import com.gamecenter.app.ai.AiTaskRouterCoroutine
import com.gamecenter.app.ai.session.ChatContextManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.gamecenter.app.ai.local.ILocalLlmEngine
import com.gamecenter.app.ai.local.MiniGameRuleEngine
/**
 * 跨模块统一 AI 服务门面（CoreAiService）。
 *
 * 为其他业务模块（错题本 wrongbook、游戏复盘 chinesechess/go、浏览器 browser、日常工具箱 tools）
 * 提供极简、统一的本地优先 AI 能力接口。
 */
class CoreAiService private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: CoreAiService? = null

        @JvmStatic
        fun getInstance(context: Context): CoreAiService {
            return instance ?: synchronized(this) {
                instance ?: CoreAiService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val router = AiTaskRouterCoroutine(appContext)

    /**
     * 同步评估小游戏交互，返回完整结果字符串。
     */
    fun evaluateMiniGameSync(gameId: String, input: String): String {
        val engine = MiniGameRuleEngine()
        engine.load(appContext, java.io.File(""), ILocalLlmEngine.EngineOptions())
        val prompt = "gameId=$gameId;input=$input"
        return engine.generate(prompt)
    }

    /**
     * 错题离线解析与步骤推导（供 wrongbook 模块调用）
     *
     * @param problemText 题目正文（OCR 识别或用户输入）
     * @param subject 学科类型（如 "数学", "物理", "英语" 等）
     */
    fun explainProblem(problemText: String, subject: String = "通用学科"): Flow<String> = flow {
        val systemPrompt = "你是一位耐心的${subject}特级教师。请为用户分步解析题目。输出格式：\n" +
                "1.【考点定位】\n2.【易错陷阱】\n3.【详细推导步骤】\n4.【同类题思路】"
        val prompt = ChatContextManager.formatPrompt(
            messages = listOf(ChatContextManager.Message("user", "学科：$subject\n题目：$problemText")),
            systemPrompt = systemPrompt
        )
        router.executeTask("qa", prompt).collect { result ->
            if (result.success) {
                emit(result.content ?: "")
            } else {
                emit(result.message ?: "解析失败，请检查模型状态")
            }
        }
    }

    /**
     * 棋局走法智能解说与复盘分析（供 chinesechess / go 游戏模块调用）
     *
     * @param gameName 游戏名称（"中国象棋", "围棋", "五子棋"）
     * @param moveHistory 历史着法或当前着法（如 "炮二平五", "星位点三三"）
     * @param evalScore 引擎搜索评估分（分值差）
     */
    fun analyzeGameMove(
        gameName: String,
        moveHistory: String,
        evalScore: Int
    ): Flow<String> = flow {
        val systemPrompt = "你是精通${gameName}的国家级裁判兼专业解说员，请根据走法和局面评分进行一句话战术评述。"
        val userContent = "游戏：$gameName\n当前走法：$moveHistory\n局面评分：$evalScore (正数占优，负数落后)"
        val prompt = ChatContextManager.formatPrompt(
            messages = listOf(ChatContextManager.Message("user", userContent)),
            systemPrompt = systemPrompt
        )
        router.executeTask("qa", prompt).collect { result ->
            if (result.success) {
                emit(result.content ?: "")
            } else {
                emit(result.message ?: "对局解说生成失败")
            }
        }
    }

    /**
     * 网页离线速读与要点总结（供 browser 模块调用）
     *
     * @param title 网页标题
     * @param content 网页正文
     */
    fun summarizeArticle(title: String, content: String): Flow<String> = flow {
        val truncated = if (content.length > 2000) content.substring(0, 2000) + "..." else content
        val prompt = "请为以下文章提取 3 条核心要点摘要：\n标题：$title\n正文：$truncated"
        router.executeTask("summary", prompt).collect { result ->
            if (result.success) {
                emit(result.content ?: "")
            } else {
                emit(result.message ?: "摘要生成失败")
            }
        }
    }
}
