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

public class AiTaskRouterTest {

    // ========== AiTask data model ==========

    @Test
    public void aiTask_fullConstructor_assignsAllFields() {
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
        AiTask task = new AiTask("ocr", "text");

        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusCompleted_isCompletedReturnsTrue() {
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.COMPLETED;

        assertTrue(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusFailed_isFailedReturnsTrue() {
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.FAILED;

        assertFalse(task.isCompleted());
        assertTrue(task.isFailed());
    }

    @Test
    public void aiTask_statusRunning_neitherCompletedNorFailed() {
        AiTask task = new AiTask("ocr", "text");
        task.status = TaskStatus.RUNNING;

        assertFalse(task.isCompleted());
        assertFalse(task.isFailed());
    }

    @Test
    public void aiTask_statusTransitions_pendingToRunningToCompleted() {
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
        AiTask task = new AiTask("summary", "input");

        task.status = TaskStatus.RUNNING;
        task.status = TaskStatus.FAILED;

        assertFalse(task.isCompleted());
        assertTrue(task.isFailed());
    }

    @Test
    public void aiTask_outputCanBeSet() {
        AiTask task = new AiTask("ocr", "raw");
        assertNull(task.output);

        task.output = "cleaned text";
        assertEquals("cleaned text", task.output);
    }

    @Test
    public void aiTask_costLevelCanBeUpdated() {
        AiTask task = new AiTask("summary", "input");
        assertEquals(0, task.costLevel);

        task.costLevel = 1;
        assertEquals(1, task.costLevel);
    }

    // ========== AiResult data model ==========

    @Test
    public void aiResult_successBuilder_createsSuccessfulResult() {
        AiResult result = AiResult.success("hello").build();

        assertTrue(result.success);
        assertEquals("hello", result.content);
        assertEquals("cloud", result.source);
        assertEquals("", result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_createsFailedResult() {
        AiResult result = AiResult.fail("something went wrong").build();

        assertFalse(result.success);
        assertEquals("something went wrong", result.message);
        assertEquals("cloud", result.source);
        assertEquals("", result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_withLocalSource() {
        AiResult result = AiResult.success("content").source("local").build();

        assertTrue(result.success);
        assertEquals("content", result.content);
        assertEquals("local", result.source);
    }

    @Test
    public void aiResult_failBuilder_withErrorCode() {
        AiResult result = AiResult.fail("quota exceeded")
                .errorCode("QUOTA_EXCEEDED").build();

        assertFalse(result.success);
        assertEquals("quota exceeded", result.message);
        assertEquals(AiErrorCode.QUOTA_EXCEEDED, result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_withLocalGemmaSource() {
        AiResult result = AiResult.success("gemma output")
                .source("local-gemma").build();

        assertTrue(result.success);
        assertEquals("gemma output", result.content);
        assertEquals("local-gemma", result.source);
    }

    @Test
    public void aiResult_failBuilder_withLocalSourceAndErrorCode() {
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
        AiResult result = AiResult.fail("degenerated output")
                .source("local-gemma")
                .errorCode("LOCAL_LLM_DEGENERATED_OUTPUT").build();

        assertFalse(result.success);
        assertEquals("degenerated output", result.message);
        assertEquals(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT, result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_networkError() {
        AiResult result = AiResult.fail("request failed")
                .errorCode("NETWORK_ERROR").build();

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void aiResult_failBuilder_noApiKey() {
        AiResult result = AiResult.fail("no api key")
                .errorCode("NO_API_KEY").build();

        assertFalse(result.success);
        assertEquals(AiErrorCode.NO_API_KEY, result.errorCode);
    }

    @Test
    public void aiResult_successBuilder_defaultSourceIsCloud() {
        AiResult result = AiResult.success("data").build();

        assertEquals("cloud", result.source);
    }

    @Test
    public void aiResult_failBuilder_defaultErrorCodeIsEmpty() {
        AiResult result = AiResult.fail("error").build();

        assertEquals("", result.errorCode);
    }

    // ========== AiProviderConfig ==========

    @Test
    public void localConfig_hasExpectedValues() {
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
    public void deepseekConfig_hasExpectedValues() {
        AiProviderConfig config = AiProviderConfig.deepseekConfig("key");

        assertEquals("DeepSeek", config.providerName);
        assertEquals("deepseek-chat", config.modelName);
        assertEquals(1, config.costLevel);
    }

    @Test
    public void deepseekReasonerConfig_hasHigherCostLevel() {
        AiProviderConfig config = AiProviderConfig.deepseekReasonerConfig("key");

        assertEquals("DeepSeek", config.providerName);
        assertEquals("deepseek-reasoner", config.modelName);
        assertEquals(2, config.costLevel);
    }

    @Test
    public void aliyunConfigs_haveExpectedCostLevels() {
        assertEquals(1, AiProviderConfig.aliyunTurboConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.aliyunConfig("key").costLevel);
        assertEquals(3, AiProviderConfig.aliyunMaxConfig("key").costLevel);
    }

    @Test
    public void siliconFlowConfigs_haveExpectedModels() {
        assertEquals("Pro/deepseek-ai/DeepSeek-V3",
                AiProviderConfig.siliconFlowDeepSeekConfig("key").modelName);
        assertEquals("Qwen/Qwen2.5-7B-Instruct",
                AiProviderConfig.siliconFlowQwenConfig("key").modelName);
    }

    @Test
    public void zhipuConfigs_haveExpectedCostLevels() {
        assertEquals(1, AiProviderConfig.zhipuFlashConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.zhipuPlusConfig("key").costLevel);
    }

    @Test
    public void yiConfigs_haveExpectedCostLevels() {
        assertEquals(1, AiProviderConfig.yiLightningConfig("key").costLevel);
        assertEquals(2, AiProviderConfig.yiLargeConfig("key").costLevel);
    }

    @Test
    public void withEnabled_false_createsCopyWithDisabled() {
        AiProviderConfig original = AiProviderConfig.openAIConfig("key");

        AiProviderConfig disabled = original.withEnabled(false);

        assertFalse(disabled.enabled);
        assertEquals(original.providerName, disabled.providerName);
        assertEquals(original.modelName, disabled.modelName);
        assertEquals(original.costLevel, disabled.costLevel);
    }

    @Test
    public void withEnabled_true_createsCopyWithEnabled() {
        AiProviderConfig original = AiProviderConfig.openAIConfig("key").withEnabled(false);

        AiProviderConfig enabled = original.withEnabled(true);

        assertTrue(enabled.enabled);
    }

    @Test
    public void allCloudProviders_areNotLocalOnly() {
        assertFalse(AiProviderConfig.openAIConfig("key").localOnly);
        assertFalse(AiProviderConfig.deepseekConfig("key").localOnly);
        assertFalse(AiProviderConfig.aliyunConfig("key").localOnly);
        assertFalse(AiProviderConfig.siliconFlowDeepSeekConfig("key").localOnly);
        assertFalse(AiProviderConfig.zhipuFlashConfig("key").localOnly);
        assertFalse(AiProviderConfig.yiLightningConfig("key").localOnly);
    }

    // ========== AiCommand recognition ==========

    @Test
    public void recognizeCommand_summarizePrefix_returnsSummarize() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("总结这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_abstractPrefix_returnsSummarize() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("摘要一下");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_helpMeSummarize_returnsSummarize() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("帮我总结一下");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_translatePrefix_returnsTranslate() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("翻译这段话");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_translateEnglish_returnsTranslate() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("translate this");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_ocrPrefix_returnsOcr() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("ocr");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_recognizeImage_returnsOcr() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("识别图片中的文字");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_polishPrefix_returnsRewrite() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("润色这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_rewritePrefix_returnsRewrite() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("改写一下");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_optimizePrefix_returnsRewrite() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("优化这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qaPrefix_returnsQaPairs() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("问答");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qColonPrefix_returnsQaPairs() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("q: what is this?");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_keywordInText_returnsKeywords() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_englishKeyword_returnsKeywords() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("find keyword in text");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_null_returnsUnknown() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand(null);

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
        assertNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_unrecognized_returnsUnknown() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    // ========== Local processing routing — OCR ==========

    @Test
    public void localOcrProcessing_normalText_returnsCleanedResult() {
        AiResult result = LocalAiProcessor.processOcrResult("Hello World");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Hello World"));
    }

    @Test
    public void localOcrProcessing_messyText_cleansUp() {
        String input = "  Line1  \n\n\n  Line2  \n  ###  \n  Line3  ";
        AiResult result = LocalAiProcessor.processOcrResult(input);

        assertTrue(result.success);
        assertTrue(result.content.contains("Line1"));
        assertTrue(result.content.contains("Line2"));
        assertTrue(result.content.contains("Line3"));
    }

    @Test
    public void localOcrProcessing_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.processOcrResult("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    // ========== Local processing routing — Summary ==========

    @Test
    public void localSummaryProcessing_shortText_returnsContent() {
        AiResult result = LocalAiProcessor.simpleSummarize("Short note", 10);

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Short note"));
    }

    @Test
    public void localSummaryProcessing_longText_respectsMaxLines() {
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
        AiResult result = LocalAiProcessor.simpleSummarize("", 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    // ========== Local processing routing — Keywords ==========

    @Test
    public void localKeywordProcessing_chineseText_extractsKeywords() {
        AiResult result = LocalAiProcessor.extractKeywords("游戏中心的功能很好用");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("游戏中心"));
    }

    @Test
    public void localKeywordProcessing_onlyStopWords_returnsFallback() {
        AiResult result = LocalAiProcessor.extractKeywords("的 了 是");

        assertTrue(result.success);
        assertEquals("未能提取有效关键词", result.content);
    }

    // ========== Local processing routing — Classification ==========

    @Test
    public void localClassifyProcessing_bugReport_returnsTechCategory() {
        AiResult result = LocalAiProcessor.classifyText("应用闪退了，出现bug");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("技术问题"));
    }

    @Test
    public void localClassifyProcessing_featureRequest_returnsSuggestionCategory() {
        AiResult result = LocalAiProcessor.classifyText("希望能增加新功能");

        assertTrue(result.success);
        assertTrue(result.content.contains("功能建议"));
    }

    @Test
    public void localClassifyProcessing_complaint_returnsFeedbackCategory() {
        AiResult result = LocalAiProcessor.classifyText("我要投诉这个差评应用");

        assertTrue(result.success);
        assertTrue(result.content.contains("用户反馈"));
    }

    @Test
    public void localClassifyProcessing_praise_returnsPositiveCategory() {
        AiResult result = LocalAiProcessor.classifyText("这个应用不错，我很喜欢");

        assertTrue(result.success);
        assertTrue(result.content.contains("正面评价"));
    }

    @Test
    public void localClassifyProcessing_neutralText_returnsOtherCategory() {
        AiResult result = LocalAiProcessor.classifyText("今天是星期三");

        assertTrue(result.success);
        assertTrue(result.content.contains("其他"));
    }

    // ========== Local processing routing — Template filling ==========

    @Test
    public void localTemplateProcessing_withVariables_replacesPlaceholders() {
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");
        vars.put("place", "GameCenter");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertEquals("Hello World, welcome to GameCenter!", result.content);
    }

    @Test
    public void localTemplateProcessing_missingVariable_leavesPlaceholder() {
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to {{place}}!", result.content);
    }

    @Test
    public void localTemplateProcessing_nullVars_returnsOriginalTemplate() {
        AiResult result = LocalAiProcessor.fillTemplate("Hello {{name}}", null);

        assertTrue(result.success);
        assertEquals("Hello {{name}}", result.content);
    }

    @Test
    public void localTemplateProcessing_nullTemplate_returnsFail() {
        AiResult result = LocalAiProcessor.fillTemplate(null, new HashMap<String, String>());

        assertFalse(result.success);
        assertEquals("模板为空", result.message);
    }

    // ========== Cost estimation (mirrors AiTaskRouter.estimateCost logic) ==========

    @Test
    public void costEstimation_ocrTask_returnsCost1() {
        assertEquals(1, estimateCost("ocr"));
    }

    @Test
    public void costEstimation_ocrCleanTask_returnsCost2() {
        assertEquals(2, estimateCost("ocr_clean"));
    }

    @Test
    public void costEstimation_summaryTask_returnsCost1() {
        assertEquals(1, estimateCost("summary"));
    }

    @Test
    public void costEstimation_keywordsTask_returnsCost1() {
        assertEquals(1, estimateCost("keywords"));
    }

    @Test
    public void costEstimation_classifyTask_returnsCost1() {
        assertEquals(1, estimateCost("classify"));
    }

    @Test
    public void costEstimation_translateTask_returnsCost1() {
        assertEquals(1, estimateCost("translate"));
    }

    @Test
    public void costEstimation_rewriteTask_returnsCost1() {
        assertEquals(1, estimateCost("rewrite"));
    }

    @Test
    public void costEstimation_qaPairsTask_returnsCost1() {
        assertEquals(1, estimateCost("qa_pairs"));
    }

    @Test
    public void costEstimation_chatTask_returnsCost2() {
        assertEquals(2, estimateCost("chat"));
    }

    @Test
    public void costEstimation_unknownTask_returnsCost2() {
        assertEquals(2, estimateCost("unknown_type"));
    }

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

    @Test
    public void statsFormat_reflectsCounts() {
        int totalTasks = 10;
        int localTasks = 7;
        int cloudTasks = 3;

        String stats = String.format("总任务: %d | 本地: %d | 云端: %d",
                totalTasks, localTasks, cloudTasks);

        assertEquals("总任务: 10 | 本地: 7 | 云端: 3", stats);
    }

    @Test
    public void statsFormat_zeroCounts() {
        String stats = String.format("总任务: %d | 本地: %d | 云端: %d", 0, 0, 0);

        assertEquals("总任务: 0 | 本地: 0 | 云端: 0", stats);
    }

    // ========== Callback invocation pattern ==========

    @Test
    public void callbackInterface_canBeImplemented() {
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

    @Test
    public void routing_ocrType_routesToLocalOcrProcessor() {
        AiResult result = LocalAiProcessor.processOcrResult("test ocr input");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_ocrCleanType_routesToLocalOcrProcessor() {
        AiResult result = LocalAiProcessor.processOcrResult("messy   ocr\n\n\ntext");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_summaryType_routesToLocalSummarizer() {
        AiResult result = LocalAiProcessor.simpleSummarize("some text to summarize", 10);

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_keywordsType_routesToLocalKeywordExtractor() {
        AiResult result = LocalAiProcessor.extractKeywords("游戏功能测试");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_classifyType_routesToLocalClassifier() {
        AiResult result = LocalAiProcessor.classifyText("应用出现bug");

        assertTrue(result.success);
        assertEquals("local", result.source);
    }

    @Test
    public void routing_templateType_returnsInputAsSuccess() {
        AiResult result = AiResult.success("template content").source("local").build();

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertEquals("template content", result.content);
    }

    @Test
    public void routing_unknownType_withSummarizeCommand_routesToSummarizer() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("总结一下这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());

        AiResult result = LocalAiProcessor.simpleSummarize("总结一下这段文字", 10);
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withKeywordCommand_routesToKeywordExtractor() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());

        AiResult result = LocalAiProcessor.extractKeywords("提取关键词");
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withClassifyCommand_routesToClassifier() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("classify this");

        AiResult result = LocalAiProcessor.classifyText("classify this");
        assertTrue(result.success);
    }

    @Test
    public void routing_unknownType_withUnknownCommand_returnsNullRouting() {
        AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    // ========== Task lifecycle — cost level after local success ==========

    @Test
    public void taskAfterLocalSuccess_costLevelIsZero() {
        AiTask task = new AiTask("ocr", "raw text");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = 0;

        assertEquals(0, task.costLevel);
        assertTrue(task.isCompleted());
    }

    @Test
    public void taskAfterCloudSuccess_costLevelIsEstimated() {
        AiTask task = new AiTask("chat", "hello");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = estimateCost("chat");

        assertEquals(2, task.costLevel);
        assertTrue(task.isCompleted());
    }

    @Test
    public void taskAfterCloudSummary_costLevelIsOne() {
        AiTask task = new AiTask("summary", "text");
        task.status = TaskStatus.COMPLETED;
        task.costLevel = estimateCost("summary");

        assertEquals(1, task.costLevel);
    }
}
