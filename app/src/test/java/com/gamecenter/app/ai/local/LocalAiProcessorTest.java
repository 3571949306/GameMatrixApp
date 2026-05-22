package com.gamecenter.app.ai.local;

import com.gamecenter.app.ai.data.AiResult;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 本地AI处理器的单元测试类。
 *
 * LocalAiProcessor 是不依赖网络的本地AI处理器，它可以在没有网络的情况下
 * 提供基本的AI功能（如OCR清洗、简单摘要、关键词提取、翻译草稿等）。
 *
 * 为什么要测试本地处理器？本地处理器是AI功能的"兜底方案"，
 * 当用户没有网络或不想消耗云端额度时，本地处理器能提供基本的服务。
 * 测试它确保了即使在离线状态下，应用也能正常工作。
 *
 * 本测试类主要测试：
 * 1. OCR结果处理 —— 清洗OCR识别的杂乱文字
 * 2. 简单摘要 —— 对长文本进行摘要提取
 * 3. 关键词提取 —— 从中文文本中提取关键词
 * 4. 翻译 —— 提供基础的本地翻译草稿
 * 5. 润色 —— 清理文本中的格式问题
 * 6. 问答对生成 —— 根据文本生成问答对
 * 7. 模板填充 —— 替换模板中的占位符
 * 8. 文本分类 —— 将文本分类到不同类别
 * 9. 命令识别 —— 根据用户输入判断操作类型
 */
public class LocalAiProcessorTest {

    // --- processOcrResult ---
    // OCR结果处理测试

    @Test
    public void processOcrResult_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败，提示"输入为空"
        AiResult result = LocalAiProcessor.processOcrResult(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void processOcrResult_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.processOcrResult("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void processOcrResult_normalText_returnsCleaned() {
        // 测试：正常文字应成功返回，内容包含原文
        AiResult result = LocalAiProcessor.processOcrResult("Hello World");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Hello World"));
    }

    @Test
    public void processOcrResult_extraWhitespaceAndNewlines_cleansUp() {
        // 测试：包含多余空格和换行的文字，清洗后应保留有效内容，去除多余空白
        String input = "  Line1  \n\n\n  Line2  \n  \n  Line3  ";
        AiResult result = LocalAiProcessor.processOcrResult(input);

        assertTrue(result.success);
        assertFalse(result.content.contains("  "));
        assertTrue(result.content.contains("Line1"));
        assertTrue(result.content.contains("Line2"));
        assertTrue(result.content.contains("Line3"));
    }

    // --- simpleSummarize ---
    // 简单摘要测试

    @Test
    public void simpleSummarize_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.simpleSummarize(null, 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void simpleSummarize_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.simpleSummarize("", 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void simpleSummarize_shortText_returnsContent() {
        // 测试：短文本直接返回内容（无需截取摘要）
        AiResult result = LocalAiProcessor.simpleSummarize("Short note", 5);

        assertTrue(result.success);
        assertTrue(result.content.contains("Short note"));
    }

    @Test
    public void simpleSummarize_longText_respectsMaxLines() {
        // 测试：长文本摘要应遵守最大行数限制
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("This is line number ").append(i).append(" of the document.\n");
        }
        String input = sb.toString();

        AiResult result = LocalAiProcessor.simpleSummarize(input, 3);

        assertTrue(result.success);
        String[] outputLines = result.content.split("\n");
        assertTrue(outputLines.length <= 3);
    }

    // --- extractKeywords ---
    // 关键词提取测试

    @Test
    public void extractKeywords_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.extractKeywords(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void extractKeywords_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.extractKeywords("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void extractKeywords_chineseText_extractsKeywords() {
        // 测试：中文文本应能提取出关键词，如"游戏中心"、"功能"、"很好用"
        AiResult result = LocalAiProcessor.extractKeywords("游戏中心的功能很好用");

        assertTrue(result.success);
        assertTrue(result.content.contains("游戏中心"));
        assertTrue(result.content.contains("功能"));
        assertTrue(result.content.contains("很好用"));
    }

    @Test
    public void extractKeywords_onlyStopWords_returnsFallbackMessage() {
        // 测试：只包含停用词（如"的"、"了"、"是"、"在"）的文本，无法提取有效关键词
        AiResult result = LocalAiProcessor.extractKeywords("的 了 是 在");

        assertTrue(result.success);
        assertEquals("未能提取有效关键词", result.content);
    }

    // --- translateText ---
    // 翻译测试（本地翻译为草稿级别，建议用户配置API Key获取完整翻译）

    @Test
    public void translateText_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.translateText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void translateText_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.translateText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void translateText_chineseText_returnsDraftWithOriginal() {
        // 测试：中文文本翻译应返回草稿，包含原文和建议配置API Key的提示
        AiResult result = LocalAiProcessor.translateText("这是一个游戏");

        assertTrue(result.success);
        assertTrue(result.content.contains("本地翻译草稿"));
        assertTrue(result.content.contains("这是一个游戏"));
        assertTrue(result.content.contains("建议配置 API Key"));
    }

    @Test
    public void translateText_englishText_replacesKnownWords() {
        // 测试：英文文本翻译应替换已知的词汇（如game→游戏、server→服务器、error→错误）
        AiResult result = LocalAiProcessor.translateText("The game server has an error");

        assertTrue(result.success);
        assertTrue(result.content.contains("本地翻译草稿"));
        assertTrue(result.content.contains("游戏"));
        assertTrue(result.content.contains("服务器"));
        assertTrue(result.content.contains("错误"));
        assertTrue(result.content.contains("完整翻译请配置 API Key"));
    }

    // --- polishText ---
    // 润色测试

    @Test
    public void polishText_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.polishText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void polishText_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.polishText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void polishText_extraWhitespaceAndPunctuation_cleansUp() {
        // 测试：包含多余空格和标点符号间距问题的文本，润色后应清理格式
        String input = "  这是第一句 。  这是第二句！  这是第三句？  ";
        AiResult result = LocalAiProcessor.polishText(input);

        assertTrue(result.success);
        assertTrue(result.content.startsWith("润色稿:"));
        assertFalse(result.content.contains("  "));
        assertTrue(result.content.contains("这是第一句。"));
        assertTrue(result.content.contains("这是第二句！"));
    }

    // --- generateQaPairs ---
    // 问答对生成测试

    @Test
    public void generateQaPairs_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.generateQaPairs(null, 3);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void generateQaPairs_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.generateQaPairs("", 3);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void generateQaPairs_multipleSentences_generatesPairs() {
        // 测试：包含多个句子的文本，应生成至少2组问答对（Q1/A1、Q2/A2）
        String input = "这是第一个要点的详细描述内容。这是第二个要点的详细描述内容。这是第三个要点的详细描述内容。";
        AiResult result = LocalAiProcessor.generateQaPairs(input, 5);

        assertTrue(result.success);
        assertTrue(result.content.contains("Q1:"));
        assertTrue(result.content.contains("A1:"));
        assertTrue(result.content.contains("Q2:"));
        assertTrue(result.content.contains("A2:"));
    }

    @Test
    public void generateQaPairs_maxPairsLimit_respectsLimit() {
        // 测试：问答对数量应遵守最大限制（设为2时，不应出现Q3）
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            sb.append("这是第").append(i).append("个要点的详细描述内容，长度足够不会被过滤掉。");
        }
        AiResult result = LocalAiProcessor.generateQaPairs(sb.toString(), 2);

        assertTrue(result.success);
        assertTrue(result.content.contains("Q1:"));
        assertTrue(result.content.contains("Q2:"));
        assertFalse(result.content.contains("Q3:"));
    }

    // --- fillTemplate ---
    // 模板填充测试

    @Test
    public void fillTemplate_nullTemplate_returnsFail() {
        // 测试：模板为null时，应返回失败
        AiResult result = LocalAiProcessor.fillTemplate(null, new HashMap<String, String>());

        assertFalse(result.success);
        assertEquals("模板为空", result.message);
    }

    @Test
    public void fillTemplate_emptyTemplate_returnsEmpty() {
        // 测试：空模板应返回空内容
        AiResult result = LocalAiProcessor.fillTemplate("", null);

        assertTrue(result.success);
        assertEquals("", result.content);
    }

    @Test
    public void fillTemplate_templateWithVariables_replacesVariables() {
        // 测试：模板中的{{name}}和{{place}}应被替换为变量值
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");
        vars.put("place", "GameMatrix");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to GameMatrix!", result.content);
    }

    @Test
    public void fillTemplate_missingVariables_leavesPlaceholder() {
        // 测试：变量缺失时，对应的占位符保持不变
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to {{place}}!", result.content);
    }

    // --- classifyText ---
    // 文本分类测试

    @Test
    public void classifyText_nullInput_returnsFail() {
        // 测试：输入null时，应返回失败
        AiResult result = LocalAiProcessor.classifyText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void classifyText_emptyInput_returnsFail() {
        // 测试：输入空字符串时，应返回失败
        AiResult result = LocalAiProcessor.classifyText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void classifyText_technicalQuestion_returnsTechCategory() {
        // 测试：包含"闪退"、"bug"等关键词的文本应分类为"技术问题"
        AiResult result = LocalAiProcessor.classifyText("应用闪退了，出现bug和错误");

        assertTrue(result.success);
        assertTrue(result.content.contains("技术问题"));
    }

    @Test
    public void classifyText_featureSuggestion_returnsSuggestionCategory() {
        // 测试：包含"希望增加"等关键词的文本应分类为"功能建议"
        AiResult result = LocalAiProcessor.classifyText("希望能增加新功能");

        assertTrue(result.success);
        assertTrue(result.content.contains("功能建议"));
    }

    @Test
    public void classifyText_positiveFeedback_returnsPositiveCategory() {
        // 测试：包含"不错"、"喜欢"等关键词的文本应分类为"正面评价"
        AiResult result = LocalAiProcessor.classifyText("这个应用不错，我很喜欢");

        assertTrue(result.success);
        assertTrue(result.content.contains("正面评价"));
    }

    // --- recognizeCommand ---
    // 命令识别测试

    @Test
    public void recognizeCommand_nullInput_returnsUnknown() {
        // 测试：输入null时，应返回unknown类型
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand(null);

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
        assertNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_emptyInput_returnsUnknown() {
        // 测试：输入空字符串时，应返回unknown类型
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_summarize_returnsSummarize() {
        // 测试：输入"总结"应识别为summarize命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("总结一下这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
        assertNotNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_translate_returnsTranslate() {
        // 测试：输入"翻译"应识别为translate命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("翻译这段话");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_ocr_returnsOcr() {
        // 测试：输入"ocr"应识别为ocr命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("ocr");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_rewrite_returnsRewrite() {
        // 测试：输入"润色"应识别为rewrite命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("润色这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qaPairs_returnsQaPairs() {
        // 测试：输入"问答"应识别为qa_pairs命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("问答");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_keywords_returnsKeywords() {
        // 测试：输入"提取关键词"应识别为keywords命令
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_unknownCommand_returnsUnknown() {
        // 测试：无法识别的输入应返回unknown类型
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }
}
