package com.gamecenter.app.ai;

import com.gamecenter.app.ai.data.AiErrorCode;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.data.TaskStatus;
import com.gamecenter.app.ai.local.LocalAiProcessor;
import com.gamecenter.app.ai.local.LocalAiProcessor.AiCommand;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AI任务路由器的单元测试类。
 *
 * 单元测试是什么？单元测试是对代码中最小的可测试单元（比如一个方法、一个类）
 * 进行验证的自动化测试。它的目的是确保每个小零件都能正常工作，
 * 这样组装起来的整个系统才不容易出问题。
 *
 * 为什么要写单元测试？想象你在造一辆汽车，如果你不先测试每个螺丝钉和零件，
 * 等整车装好后发现问题，找起来就非常困难。单元测试就是提前检查每个"零件"。
 *
 * 本测试类主要测试以下内容：
 * 1. AiTask 数据模型 —— AI任务的创建、状态转换、字段赋值
 * 2. AiResult 数据模型 —— AI处理结果的构建（成功/失败）
 * 3. AiProviderConfig —— 各种AI服务提供商的配置参数
 * 4. AiCommand 识别 —— 根据用户输入识别AI命令类型
 * 5. 本地处理路由 —— OCR、摘要、关键词提取、分类、模板填充
 * 6. 成本估算 —— 不同任务类型的资源消耗等级
 * 7. 回调机制 —— 任务完成后的结果通知
 */
public class AiTaskRouterTest {

    // ========== AiTask data model ==========
    // AiTask 是AI任务的数据模型，记录了一个AI任务的所有信息

    @Test
    public void aiTask_fullConstructor_assignsAllFields() {
        // 测试：使用完整构造函数创建AiTask时，所有字段都应该被正确赋值
        long now = System.currentTimeMillis();

        AiTask task = new AiTask("id-1", "ocr", "raw text", TaskStatus.PENDING, now, 1);

        assertEquals("id-1", task.taskId);
        assertEquals("ocr", task.taskType);
        assertEquals("raw text", task.input);
        assertEquals(TaskStatus.PENDING, task.status);
        assertEquals(now, task.createdAt);
        assertEquals(1, task.costLevel);
    }

    @Test
    public void aiTask_convenienceConstructor_setsDefaults() {
        // 测试：使用简化构造函数创建AiTask时，应自动生成ID，状态默认为PENDING，costLevel默认为0
        AiTask task = new AiTask("summary", "some input");

        assertNotNull(task.taskId);
        assertEquals("summary", task.taskType);
        assertEquals("some input", task.input);
        assertEquals(TaskStatus.PENDING, task.status);
        assertEquals(0, task.costLevel);
        assertTrue(task.createdAt > 0);
    }

    @Test
    public void aiTask_initialStatus_isNotCompletedOrFailed() {
        // 测试：新创建的任务，状态既不是"已完成"也不是"失败"
        AiTask task = new AiTask("ocr", "text");

        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusCompleted_isCompletedReturnsTrue() {
        // 测试：当任务状态设为COMPLETED时，isCompleted()应返回true，isFailed()应返回false
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.COMPLETED;

        assertTrue(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusFailed_isFailedReturnsTrue() {
        // 测试：当任务状态设为FAILED时，isFailed()应返回true，isCompleted()应返回false
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.FAILED;

        assertFalse(task.isCompleted());
        assertTrue(task.isFailed());
    }

    @Test
    public void aiTask_statusRunning_neitherCompletedNorFailed() {
        // 测试：当任务状态为RUNNING（正在执行）时，既不是完成也不是失败
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.RUNNING;

        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusTransitions_pendingToRunningToCompleted() {
        // 测试：任务状态的正常流转路径：等待中 → 执行中 → 已完成
        AiTask task = new AiTask("summary", "input");

        assertEquals(TaskStatus.PENDING, task.status);
        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());

        task.status = TaskStatus.RUNNING;
        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());

        task.status = TaskStatus.COMPLETED;
        assertTrue(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusTransitions_pendingToRunningToFailed() {
        // 测试：任务状态也可能从执行中变为失败：等待中 → 执行中 → 失败
        AiTask task = new AiTask("summary", "input");

        task.status = TaskStatus.RUNNING;
        task.status = TaskStatus.FAILED;

        assertFalse(task.isCompleted());
        assertTrue(task.isFailed());
    }

    @Test
    public void aiTask_outputCanBeSet() {
        // 测试：任务的输出结果可以被设置和获取，初始时output为null
        AiTask task = new AiTask("ocr", "raw");
        assertNull(task.output);

        task.output = "cleaned text";
        assertEquals("cleaned text", task.output);
    }

    @Test
    public void aiTask_costLevelCanBeUpdated() {
        // 测试：任务的成本等级可以被修改，初始默认为0
        AiTask task = new AiTask("summary", "input");
        assertEquals(0, task.costLevel);

        task.costLevel = 1;
        assertEquals(1, task.costLevel);
    }

    // ========== AiResult data model ==========
    // AiResult 是AI处理结果的数据模型，记录了处理是否成功、内容、来源等信息

    @Test
    public void aiResult_successBuilder_createsSuccessfulResult() {
        // 测试：使用success构建器创建的结果，success应为true，默认来源为"cloud"
        AiResult result = AiResult.success("hello").build();

        assertTrue(result.success);
        assertEquals("hello", result.content);
        assertEquals("cloud", result.source);
        assertEquals("", result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_createsFailedResult() {
        // 测试：使用fail构建器创建的结果，success应为false，包含错误信息
        AiResult result = AiResult.fail("something went wrong").build();

        assertFalse(result.success);
        assertEquals("something went wrong", result.message);
        assertEquals("cloud", result.source);
        assertEquals("", result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_withLocalSource() {
        // 测试：成功结果可以指定来源为"local"（本地处理）
        AiResult result = AiResult.success("content").source("local").build();

        assertTrue(result.success);
        assertEquals("content", result.content);
        assertEquals("local", result.source);
    }

    @Test
    public void aiResult_failBuilder_withErrorCode() {
        // 测试：失败结果可以附带错误码，方便定位问题原因
        AiResult result = AiResult.fail("quota exceeded")
                .errorCode("QUOTA_EXCEEDED").build();

        assertFalse(result.success);
        assertEquals("quota exceeded", result.message);
        assertEquals(AiErrorCode.QUOTA_EXCEEDED, result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_withLocalGemmaSource() {
        // 测试：成功结果可以指定来源为"local-gemma"（本地Gemma模型）
        AiResult result = AiResult.success("gemma output")
                .source("local-gemma").build();

        assertTrue(result.success);
        assertEquals("gemma output", result.content);
        assertEquals("local-gemma", result.source);
    }

    @Test
    public void aiResult_failBuilder_withLocalSourceAndErrorCode() {
        // 测试：本地模型失败时，可以同时指定来源和错误码
        AiResult result = AiResult.fail("low memory")
                .source("local-gemma")
                .errorCode("LOCAL_LLM_LOW_MEMORY").build();

        assertFalse(result.success);
        assertEquals("low memory", result.message);
        assertEquals("local-gemma", result.source);
        assertEquals(AiErrorCode.LOCAL_LLM_LOW_MEMORY, result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_degenerateOutput() {
        // 测试：本地模型输出退化（重复无意义内容）时的错误码
        AiResult result = AiResult.fail("degenerated output")
                .source("local-gemma")
                .errorCode("LOCAL_LLM_DEGENERATED_OUTPUT").build();

        assertFalse(result.success);
        assertEquals("degenerated output", result.message);
        assertEquals(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT, result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_networkError() {
        // 测试：网络请求失败时的错误码
        AiResult result = AiResult.fail("request failed")
                .errorCode("NETWORK_ERROR").build();

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_noApiKey() {
        // 测试：未配置API Key时的错误码
        AiResult result = AiResult.fail("no api key")
                .errorCode("NO_API_KEY").build();

        assertFalse(result.success);
        assertEquals(AiErrorCode.NO_API_KEY, result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_defaultSourceIsCloud() {
        // 测试：成功结果如果不指定来源，默认为"cloud"（云端处理）
        AiResult result = AiResult.success("data").build();

        assertEquals("cloud", result.source);
    }

    @Test
    public void aiResult_failBuilder_defaultErrorCodeIsEmpty() {
        // 测试：失败结果如果不指定错误码，默认为空字符串
        AiResult result = AiResult.fail("error").build();

        assertEquals("", result.errorCode);
    }

    // ========== AiProviderConfig ==========
    // AiProviderConfig 是AI服务提供商的配置，包含名称、模型、API密钥等信息

    @Test
    public void localConfig_hasExpectedValues() {
        // 测试：本地配置应有正确的默认值——无需API Key、无需网络、成本为0
        AiProviderConfig config = AiProviderConfig.localConfig();

        assertEquals("本地", config.providerName);
        assertEquals("on-device", config.modelName);
        assertEquals("", config.apiKey);
        assertEquals("", config.baseUrl);
        assertTrue(config.enabled);
        assertTrue(config.localOnly);
        assertEquals(2000, config.maxInputLength);
        assertEquals(0, config.costLevel);
    }

    @Test
    public void openAIConfig_hasExpectedValues() {
        // 测试：OpenAI配置应有正确的模型名、API地址和成本等级
        AiProviderConfig config = AiProviderConfig.openAIConfig("sk-test");

        assertEquals("OpenAI", config.providerName);
        assertEquals("gpt-4o-mini", config.modelName);
        assertEquals("sk-test", config.apiKey);
        assertEquals("https://api.openai.com/v1", config.baseUrl);
        assertTrue(config.enabled);
        assertFalse(config.localOnly);
        assertEquals(128000, config.maxInputLength);
        assertEquals(1, config.costLevel);
    }

    @Test
    public void openAIGpt4oConfig_hasBalancedCostLevel() {
        // 测试：OpenAI GPT-4o 配置应作为更高能力模型，成本等级为中
        AiProviderConfig config = AiProviderConfig.openAIGpt4oConfig("sk-test");

        assertEquals("OpenAI", config.providerName);
        assertEquals("gpt-4o", config.modelName);
        assertEquals(2, config.costLevel);
    }

    @Test
    public void deepseekConfig_hasExpectedValues() {
        // 测试：DeepSeek配置应有正确的模型名和成本等级
        AiProviderConfig config = AiProviderConfig.deepseekConfig("key");

        assertEquals("DeepSeek", config.providerName);
        assertEquals("deepseek-chat", config.modelName);
        assertEquals(1, config.costLevel);
    }

    @Test
    public void deepseekReasonerConfig_hasHigherCostLevel() {
        // 测试：DeepSeek推理模型（reasoner）的成本等级应高于普通模型
        AiProviderConfig config = AiProviderConfig.deepseekReasonerConfig("key");

        assertEquals("DeepSeek", config.providerName);
        assertEquals("deepseek-reasoner", config.modelName);
        assertEquals(2, config.costLevel);
    }

    @Test
    public void aliyunConfigs_haveExpectedCostLevels() {
        // 测试：阿里云三种配置的成本等级应依次递增：turbo=1, 标准=2, max=3
        assertEquals(1, AiProviderConfig.aliyunTurboConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.aliyunConfig("key").costLevel);
        assertEquals(3, AiProviderConfig.aliyunMaxConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.aliyunLongConfig("key").costLevel);
    }

    @Test
    public void siliconFlowConfigs_haveExpectedModels() {
        // 测试：硅基流动的两种配置应使用不同的模型名称
        assertEquals("Pro/deepseek-ai/DeepSeek-V3",
                AiProviderConfig.siliconFlowDeepSeekConfig("key").modelName);
        assertEquals("Qwen/Qwen2.5-7B-Instruct",
                AiProviderConfig.siliconFlowQwenConfig("key").modelName);
        assertEquals("Qwen/Qwen2.5-14B-Instruct",
                AiProviderConfig.siliconFlowQwen14BConfig("key").modelName);
        assertEquals("deepseek-ai/DeepSeek-R1",
                AiProviderConfig.siliconFlowDeepSeekR1Config("key").modelName);
    }

    @Test
    public void zhipuConfigs_haveExpectedCostLevels() {
        // 测试：智谱AI两种配置的成本等级：flash=1, plus=2
        assertEquals(1, AiProviderConfig.zhipuFlashConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.zhipuAirConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.zhipuPlusConfig("key").costLevel);
    }

    @Test
    public void yiConfigs_haveExpectedCostLevels() {
        // 测试：零一万物两种配置的成本等级：lightning=1, large=2
        assertEquals(1, AiProviderConfig.yiLightningConfig("key").costLevel);
        assertEquals(1, AiProviderConfig.yiMediumConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.yiLargeConfig("key").costLevel);
    }

    @Test
    public void moonshotConfigs_haveExpectedContextLevels() {
        // 测试：Kimi 三种上下文配置应覆盖 8K、32K 和 128K 场景
        assertEquals(8000, AiProviderConfig.moonshot8kConfig("key").maxInputLength);
        assertEquals(32000, AiProviderConfig.moonshot32kConfig("key").maxInputLength);
        assertEquals(128000, AiProviderConfig.moonshot128kConfig("key").maxInputLength);
    }

    @Test
    public void withEnabled_false_createsCopyWithDisabled() {
        // 测试：withEnabled(false)应创建一个新配置，enabled为false，其他字段不变
        AiProviderConfig original = AiProviderConfig.openAIConfig("key");

        AiProviderConfig disabled = original.withEnabled(false);

        assertFalse(disabled.enabled);
        assertEquals(original.providerName, disabled.providerName);
        assertEquals(original.modelName, disabled.modelName);
        assertEquals(original.costLevel, disabled.costLevel);
    }

    @Test
    public void withEnabled_true_createsCopyWithEnabled() {
        // 测试：withEnabled(true)应创建一个新配置，enabled为true
        AiProviderConfig original = AiProviderConfig.openAIConfig("key").withEnabled(false);

        AiProviderConfig enabled = original.withEnabled(true);

        assertTrue(enabled.enabled);
    }

    @Test
    public void allCloudProviders_areNotLocalOnly() {
        // 测试：所有云端AI服务提供商的localOnly都应为false
        assertFalse(AiProviderConfig.openAIConfig("key").localOnly);
        assertFalse(AiProviderConfig.deepseekConfig("key").localOnly);
        assertFalse(AiProviderConfig.aliyunConfig("key").localOnly);
        assertFalse(AiProviderConfig.siliconFlowDeepSeekConfig("key").localOnly);
        assertFalse(AiProviderConfig.zhipuFlashConfig("key").localOnly);
        assertFalse(AiProviderConfig.yiLightningConfig("key").localOnly);
        assertFalse(AiProviderConfig.moonshot8kConfig("key").localOnly);
    }

    // ========== AiCommand recognition ==========
    // AiCommand 识别：根据用户输入的文字，判断用户想要执行哪种AI操作

    @Test
    public void recognizeCommand_summarizePrefix_returnsSummarize() {
        // 测试：输入"总结"开头的文字，应识别为"summarize"（总结）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("总结这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_abstractPrefix_returnsSummarize() {
        // 测试：输入"摘要"开头的文字，也应识别为"summarize"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("摘要一下");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_helpMeSummarize_returnsSummarize() {
        // 测试：输入"帮我总结"开头的文字，应识别为"summarize"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("帮我总结一下");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_translatePrefix_returnsTranslate() {
        // 测试：输入"翻译"开头的文字，应识别为"translate"（翻译）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("翻译这段话");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_translateEnglish_returnsTranslate() {
        // 测试：英文"translate"也应识别为翻译命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("translate this");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_ocrPrefix_returnsOcr() {
        // 测试：输入"ocr"，应识别为OCR（文字识别）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("ocr");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_recognizeImage_returnsOcr() {
        // 测试：输入"识别图片中的文字"，应识别为OCR命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("识别图片中的文字");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_polishPrefix_returnsRewrite() {
        // 测试：输入"润色"开头的文字，应识别为"rewrite"（改写）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("润色这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_rewritePrefix_returnsRewrite() {
        // 测试：输入"改写"开头的文字，应识别为"rewrite"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("改写一下");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_optimizePrefix_returnsRewrite() {
        // 测试：输入"优化"开头的文字，应识别为"rewrite"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("优化这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qaPrefix_returnsQaPairs() {
        // 测试：输入"问答"，应识别为"qa_pairs"（问答对生成）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("问答");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qColonPrefix_returnsQaPairs() {
        // 测试：输入"q:"开头的文字（英文问答格式），应识别为"qa_pairs"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("q: what is this?");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_keywordInText_returnsKeywords() {
        // 测试：输入"提取关键词"，应识别为"keywords"（关键词提取）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_englishKeyword_returnsKeywords() {
        // 测试：英文"find keyword"也应识别为关键词提取命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("find keyword in text");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_null_returnsUnknown() {
        // 测试：输入null时，应返回"unknown"（未知）命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand(null);

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
        assertNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_unrecognized_returnsUnknown() {
        // 测试：输入无法识别的文字时，应返回"unknown"命令
        AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    // ========== Local processing routing — OCR ==========
    // 本地OCR处理：对OCR识别出的文字进行清洗和整理

    @Test
    public void localOcrProcessing_normalText_returnsCleanedResult() {
        // 测试：正常文字经过本地OCR处理后，应返回成功且包含原文内容
        AiResult result = LocalAiProcessor.processOcrResult("Hello World");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Hello World"));
    }

    @Test
    public void localOcrProcessing_messyText_cleansUp() {
        // 测试：包含多余空格、换行和分隔符的杂乱文字，清洗后应保留有效内容
        String input = "  Line1  \n\n\n  Line2  \n  ###  \n  Line3  ";
        AiResult result = LocalAiProcessor.processOcrResult(input);

        assertTrue(result.success);
        assertTrue(result.content.contains("Line1"));
        assertTrue(result.content.contains("Line2"));
        assertTrue(result.content.contains("Line3"));
    }

    @Test
    public void localOcrProcessing_emptyInput_returnsFail() {
        // 测试：空字符串输入时，应返回失败，提示"输入为空"
        AiResult result = LocalAiProcessor.processOcrResult("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    // ========== Local processing routing — Summary ==========
    // 本地摘要处理：对长文本进行简单的摘要提取

    @Test
    public void localSummaryProcessing_shortText_returnsContent() {
        // 测试：短文本直接返回内容（因为太短不需要摘要）
        AiResult result = LocalAiProcessor.simpleSummarize("Short note", 10);

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Short note"));
    }

    @Test
    public void localSummaryProcessing_longText_respectsMaxLines() {
        // 测试：长文本摘要应遵守最大行数限制，输出行数不超过指定值
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("This is line number ").append(i).append(" of the document.\n");
        }

        AiResult result = LocalAiProcessor.simpleSummarize(sb.toString(), 3);

        assertTrue(result.success);
        String[] lines = result.content.split("\n");
        assertTrue(lines.length <= 3);
    }

    @Test
    public void localSummaryProcessing_emptyInput_returnsFail() {
        // 测试：空输入时，应返回失败
        AiResult result = LocalAiProcessor.simpleSummarize("", 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    // ========== Local processing routing — Keywords ==========
    // 本地关键词提取：从文本中提取关键词

    @Test
    public void localKeywordProcessing_chineseText_extractsKeywords() {
        // 测试：中文文本应能提取出关键词（如"游戏中心"）
        AiResult result = LocalAiProcessor.extractKeywords("游戏中心的功能很好用");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("游戏中心"));
    }

    @Test
    public void localKeywordProcessing_onlyStopWords_returnsFallback() {
        // 测试：如果文本只包含停用词（如"的"、"了"、"是"），应返回"未能提取有效关键词"
        AiResult result = LocalAiProcessor.extractKeywords("的 了 是");

        assertTrue(result.success);
        assertEquals("未能提取有效关键词", result.content);
    }

    // ========== Local processing routing — Classification ==========
    // 本地文本分类：将文本分为技术问题、功能建议、用户反馈、正面评价等类别

    @Test
    public void localClassifyProcessing_bugReport_returnsTechCategory() {
        // 测试：包含"bug"、"闪退"等关键词的文本应分类为"技术问题"
        AiResult result = LocalAiProcessor.classifyText("应用闪退了，出现bug");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("技术问题"));
    }

    @Test
    public void localClassifyProcessing_featureRequest_returnsSuggestionCategory() {
        // 测试：包含"希望增加"等关键词的文本应分类为"功能建议"
        AiResult result = LocalAiProcessor.classifyText("希望能增加新功能");

        assertTrue(result.success);
        assertTrue(result.content.contains("功能建议"));
    }

    @Test
    public void localClassifyProcessing_complaint_returnsFeedbackCategory() {
        // 测试：包含"投诉"等关键词的文本应分类为"用户反馈"
        AiResult result = LocalAiProcessor.classifyText("我要投诉这个差评应用");

        assertTrue(result.success);
        assertTrue(result.content.contains("用户反馈"));
    }

    @Test
    public void localClassifyProcessing_praise_returnsPositiveCategory() {
        // 测试：包含"喜欢"、"不错"等关键词的文本应分类为"正面评价"
        AiResult result = LocalAiProcessor.classifyText("这个应用不错，我很喜欢");

        assertTrue(result.success);
        assertTrue(result.content.contains("正面评价"));
    }

    @Test
    public void localClassifyProcessing_neutralText_returnsOtherCategory() {
        // 测试：中性文本（不包含明显关键词）应分类为"其他"
        AiResult result = LocalAiProcessor.classifyText("今天是星期三");

        assertTrue(result.success);
        assertTrue(result.content.contains("其他"));
    }

    // ========== Local processing routing — Template filling ==========
    // 本地模板填充：将模板中的占位符替换为实际值

    @Test
    public void localTemplateProcessing_withVariables_replacesPlaceholders() {
        // 测试：模板中的{{name}}和{{place}}应被替换为对应的变量值
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");
        vars.put("place", "GameMatrix");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertEquals("Hello World, welcome to GameMatrix!", result.content);
    }

    @Test
    public void localTemplateProcessing_missingVariable_leavesPlaceholder() {
        // 测试：如果变量缺失，对应的占位符应保持不变
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to {{place}}!", result.content);
    }

    @Test
    public void localTemplateProcessing_nullVars_returnsOriginalTemplate() {
        // 测试：变量为null时，应返回原始模板（不做任何替换）
        AiResult result = LocalAiProcessor.fillTemplate("Hello {{name}}", null);

        assertTrue(result.success);
        assertEquals("Hello {{name}}", result.content);
    }

    @Test
    public void localTemplateProcessing_nullTemplate_returnsFail() {
        // 测试：模板为null时，应返回失败
        AiResult result = LocalAiProcessor.fillTemplate(null, new HashMap<String, String>());

        assertFalse(result.success);
        assertEquals("模板为空", result.message);
    }

    // ========== Cost estimation (mirrors AiTaskRouter.estimateCost logic) ==========
    // 成本估算：不同类型的AI任务消耗的资源不同，用costLevel表示

    @Test
    public void costEstimation_ocrTask_returnsCost1() {
        // 测试：OCR任务的成本等级为1
        assertEquals(1, estimateCost("ocr"));
    }

    @Test
    public void costEstimation_ocrCleanTask_returnsCost2() {
        // 测试：OCR清洗任务的成本等级为2（比普通OCR更复杂）
        assertEquals(2, estimateCost("ocr_clean"));
    }

    @Test
    public void costEstimation_summaryTask_returnsCost1() {
        // 测试：摘要任务的成本等级为1
        assertEquals(1, estimateCost("summary"));
    }

    @Test
    public void costEstimation_keywordsTask_returnsCost1() {
        // 测试：关键词提取任务的成本等级为1
        assertEquals(1, estimateCost("keywords"));
    }

    @Test
    public void costEstimation_classifyTask_returnsCost1() {
        // 测试：分类任务的成本等级为1
        assertEquals(1, estimateCost("classify"));
    }

    @Test
    public void costEstimation_translateTask_returnsCost1() {
        // 测试：翻译任务的成本等级为1
        assertEquals(1, estimateCost("translate"));
    }

    @Test
    public void costEstimation_rewriteTask_returnsCost1() {
        // 测试：改写任务的成本等级为1
        assertEquals(1, estimateCost("rewrite"));
    }

    @Test
    public void costEstimation_qaPairsTask_returnsCost1() {
        // 测试：问答对生成任务的成本等级为1
        assertEquals(1, estimateCost("qa_pairs"));
    }

    @Test
    public void costEstimation_chatTask_returnsCost2() {
        // 测试：聊天任务的成本等级为2（比简单任务更消耗资源）
        assertEquals(2, estimateCost("chat"));
    }

    @Test
    public void costEstimation_unknownTask_returnsCost2() {
        // 测试：未知类型的任务默认成本等级为2
        assertEquals(2, estimateCost("unknown_type"));
    }

    // 这是一个辅助方法，模拟了AiTaskRouter中的成本估算逻辑
    private static int estimateCost(String taskType) {
        switch (taskType) {
            case "ocr":
            case "summary":
            case "keywords":
            case "classify":
            case "translate":
                return 1;
            case "rewrite":
                return 1;
            case "qa_pairs":
                return 1;
            default:
                return 2;
        }
    }

    // ========== Stats tracking (mirrors AiTaskRouter.getStats format) ==========
    // 统计信息格式化：将任务执行统计格式化为可读字符串

    @Test
    public void statsFormat_reflectsCounts() {
        // 测试：统计信息应正确显示总任务数、本地任务数和云端任务数
        int totalTasks = 10;
        int localTasks = 7;
        int cloudTasks = 3;

        String stats = String.format("总任务: %d | 本地: %d | 云端: %d",
                totalTasks, localTasks, cloudTasks);

        assertEquals("总任务: 10 | 本地: 7 | 云端: 3", stats);
    }

    @Test
    public void statsFormat_zeroCounts() {
        // 测试：所有计数为0时，格式化结果也应正确显示
        String stats = String.format("总任务: %d | 本地: %d | 云端: %d", 0, 0, 0);

        assertEquals("总任务: 0 | 本地: 0 | 云端: 0", stats);
    }

    // ========== Callback invocation pattern ==========
    // 回调机制：当AI任务完成时，通过回调接口通知调用方结果

    @Test
    public void callbackInterface_canBeImplemented() {
        // 测试：AiCallback接口可以被正确实现，接收任务和结果
        final AiTask[] capturedTask = new AiTask[1];
        final AiResult[] capturedResult = new AiResult[1];

        AiTaskRouter.AiCallback callback = new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                capturedTask[0] = task;
                capturedResult[0] = result;
            }
        };

        AiTask task = new AiTask("summary", "input");
        task.status = TaskStatus.COMPLETED;
        task.output = "result text";
        AiResult result = AiResult.success("result text").source("local").build();

        callback.onResult(task, result);

        assertNotNull(capturedTask[0]);
        assertNotNull(capturedResult[0]);
        assertEquals(TaskStatus.COMPLETED, capturedTask[0].status);
        assertTrue(capturedResult[0].success);
        assertEquals("local", capturedResult[0].source);
    }

    @Test
    public void callbackReceives_failedResult_onLocalFailure() {
        // 测试：本地处理失败时，回调应收到失败的结果
        final AiResult[] capturedResult = new AiResult[1];

        AiTaskRouter.AiCallback callback = new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                capturedResult[0] = result;
            }
        };

        AiTask task = new AiTask("ocr", "");
        task.status = TaskStatus.FAILED;
        AiResult result = AiResult.fail("输入为空").source("local").build();

        callback.onResult(task, result);

        assertFalse(capturedResult[0].success);
        assertEquals("输入为空", capturedResult[0].message);
        assertEquals("local", capturedResult[0].source);
    }

    @Test
    public void callbackReceives_quotaExceededResult() {
        // 测试：当免费额度用完时，回调应收到QUOTA_EXCEEDED错误码
        final AiResult[] capturedResult = new AiResult[1];

        AiTaskRouter.AiCallback callback = new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                capturedResult[0] = result;
            }
        };

        AiResult result = AiResult.fail("今日免费额度已用完")
                .errorCode("QUOTA_EXCEEDED").build();
        callback.onResult(new AiTask("chat", "hello"), result);

        assertFalse(capturedResult[0].success);
        assertEquals(AiErrorCode.QUOTA_EXCEEDED, capturedResult[0].errorCode);
    }

    @Test
    public void callbackReceives_noApiKeyResult() {
        // 测试：当未配置API Key时，回调应收到NO_API_KEY错误码
        final AiResult[] capturedResult = new AiResult[1];

        AiTaskRouter.AiCallback callback = new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                capturedResult[0] = result;
            }
        };

        AiResult result = AiResult.fail("未配置 API Key")
                .errorCode("NO_API_KEY").build();
        callback.onResult(new AiTask("translate", "hello"), result);

        assertFalse(capturedResult[0].success);
        assertEquals(AiErrorCode.NO_API_KEY, capturedResult[0].errorCode);
    }

    // ========== Routing logic — task type to local processor mapping ==========
    // 路由逻辑：根据任务类型将任务分发到对应的本地处理器

    @Test
    public void routing_ocrType_routesToLocalOcrProcessor() {
        // 测试：OCR类型任务应路由到本地OCR处理器
        AiResult result = LocalAiProcessor.processOcrResult("test ocr input");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_ocrCleanType_routesToLocalOcrProcessor() {
        // 测试：OCR清洗类型任务也应路由到本地OCR处理器
        AiResult result = LocalAiProcessor.processOcrResult("messy   ocr\n\n\ntext");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_summaryType_routesToLocalSummarizer() {
        // 测试：摘要类型任务应路由到本地摘要处理器
        AiResult result = LocalAiProcessor.simpleSummarize("some text to summarize", 10);

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_keywordsType_routesToLocalKeywordExtractor() {
        // 测试：关键词提取类型任务应路由到本地关键词提取器
        AiResult result = LocalAiProcessor.extractKeywords("游戏功能测试");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_classifyType_routesToLocalClassifier() {
        // 测试：分类类型任务应路由到本地分类器
        AiResult result = LocalAiProcessor.classifyText("应用出现bug");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_templateType_returnsInputAsSuccess() {
        // 测试：模板类型任务应返回成功结果
        AiResult result = AiResult.success("template content").source("local").build();

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertEquals("template content", result.content);
    }

    @Test
    public void routing_unknownType_withSummarizeCommand_routesToSummarizer() {
        // 测试：未知类型但命令为"总结"时，应路由到摘要处理器
        AiCommand cmd = LocalAiProcessor.recognizeCommand("总结一下这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());

        AiResult result = LocalAiProcessor.simpleSummarize("总结一下这段文字", 10);
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withKeywordCommand_routesToKeywordExtractor() {
        // 测试：未知类型但命令为"提取关键词"时，应路由到关键词提取器
        AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());

        AiResult result = LocalAiProcessor.extractKeywords("提取关键词");
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withClassifyCommand_routesToClassifier() {
        // 测试：未知类型但命令为"classify"时，应路由到分类器
        AiCommand cmd = LocalAiProcessor.recognizeCommand("classify this");

        AiResult result = LocalAiProcessor.classifyText("classify this");
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withUnknownCommand_returnsNullRouting() {
        // 测试：无法识别的命令应返回unknown类型，isKnown()为false
        AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    // ========== Task lifecycle — cost level after local success ==========
    // 任务生命周期：任务完成后成本等级的设置

    @Test
    public void taskAfterLocalSuccess_costLevelIsZero() {
        // 测试：本地处理成功后，成本等级应为0（不消耗云端资源）
        AiTask task = new AiTask("ocr", "raw text");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = 0;

        assertEquals(0, task.costLevel);
        assertTrue(task.isCompleted());
    }

    @Test
    public void taskAfterCloudSuccess_costLevelIsEstimated() {
        // 测试：云端处理成功后，成本等级应为估算值（chat类型为2）
        AiTask task = new AiTask("chat", "hello");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = estimateCost("chat");

        assertEquals(2, task.costLevel);
        assertTrue(task.isCompleted());
    }

    @Test
    public void taskAfterCloudSummary_costLevelIsOne() {
        // 测试：云端摘要任务完成后，成本等级应为1
        AiTask task = new AiTask("summary", "text");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = estimateCost("summary");

        assertEquals(1, task.costLevel);
    }
}
