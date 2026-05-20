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

public class LocalAiProcessorTest {

    // --- processOcrResult ---

    @Test
    public void processOcrResult_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.processOcrResult(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void processOcrResult_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.processOcrResult("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void processOcrResult_normalText_returnsCleaned() {
        AiResult result = LocalAiProcessor.processOcrResult("Hello World");

        assertTrue(result.success);
        assertEquals("local", result.source);
        assertTrue(result.content.contains("Hello World"));
    }

    @Test
    public void processOcrResult_extraWhitespaceAndNewlines_cleansUp() {
        String input = "  Line1  \n\n\n  Line2  \n  \n  Line3  ";
        AiResult result = LocalAiProcessor.processOcrResult(input);

        assertTrue(result.success);
        assertFalse(result.content.contains("  "));
        assertTrue(result.content.contains("Line1"));
        assertTrue(result.content.contains("Line2"));
        assertTrue(result.content.contains("Line3"));
    }

    // --- simpleSummarize ---

    @Test
    public void simpleSummarize_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.simpleSummarize(null, 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
        assertEquals("local", result.source);
    }

    @Test
    public void simpleSummarize_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.simpleSummarize("", 5);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void simpleSummarize_shortText_returnsContent() {
        AiResult result = LocalAiProcessor.simpleSummarize("Short note", 5);

        assertTrue(result.success);
        assertTrue(result.content.contains("Short note"));
    }

    @Test
    public void simpleSummarize_longText_respectsMaxLines() {
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

    @Test
    public void extractKeywords_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.extractKeywords(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void extractKeywords_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.extractKeywords("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void extractKeywords_chineseText_extractsKeywords() {
        AiResult result = LocalAiProcessor.extractKeywords("游戏中心的功能很好用");

        assertTrue(result.success);
        assertTrue(result.content.contains("游戏中心"));
        assertTrue(result.content.contains("功能"));
        assertTrue(result.content.contains("很好用"));
    }

    @Test
    public void extractKeywords_onlyStopWords_returnsFallbackMessage() {
        AiResult result = LocalAiProcessor.extractKeywords("的 了 是 在");

        assertTrue(result.success);
        assertEquals("未能提取有效关键词", result.content);
    }

    // --- translateText ---

    @Test
    public void translateText_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.translateText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void translateText_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.translateText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void translateText_chineseText_returnsDraftWithOriginal() {
        AiResult result = LocalAiProcessor.translateText("这是一个游戏");

        assertTrue(result.success);
        assertTrue(result.content.contains("本地翻译草稿"));
        assertTrue(result.content.contains("这是一个游戏"));
        assertTrue(result.content.contains("建议配置 API Key"));
    }

    @Test
    public void translateText_englishText_replacesKnownWords() {
        AiResult result = LocalAiProcessor.translateText("The game server has an error");

        assertTrue(result.success);
        assertTrue(result.content.contains("本地翻译草稿"));
        assertTrue(result.content.contains("游戏"));
        assertTrue(result.content.contains("服务器"));
        assertTrue(result.content.contains("错误"));
        assertTrue(result.content.contains("完整翻译请配置 API Key"));
    }

    // --- polishText ---

    @Test
    public void polishText_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.polishText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void polishText_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.polishText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void polishText_extraWhitespaceAndPunctuation_cleansUp() {
        String input = "  这是第一句 。  这是第二句！  这是第三句？  ";
        AiResult result = LocalAiProcessor.polishText(input);

        assertTrue(result.success);
        assertTrue(result.content.startsWith("润色稿:"));
        assertFalse(result.content.contains("  "));
        assertTrue(result.content.contains("这是第一句。"));
        assertTrue(result.content.contains("这是第二句！"));
    }

    // --- generateQaPairs ---

    @Test
    public void generateQaPairs_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.generateQaPairs(null, 3);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void generateQaPairs_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.generateQaPairs("", 3);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void generateQaPairs_multipleSentences_generatesPairs() {
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

    @Test
    public void fillTemplate_nullTemplate_returnsFail() {
        AiResult result = LocalAiProcessor.fillTemplate(null, new HashMap<String, String>());

        assertFalse(result.success);
        assertEquals("模板为空", result.message);
    }

    @Test
    public void fillTemplate_emptyTemplate_returnsEmpty() {
        AiResult result = LocalAiProcessor.fillTemplate("", null);

        assertTrue(result.success);
        assertEquals("", result.content);
    }

    @Test
    public void fillTemplate_templateWithVariables_replacesVariables() {
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");
        vars.put("place", "GameCenter");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to GameCenter!", result.content);
    }

    @Test
    public void fillTemplate_missingVariables_leavesPlaceholder() {
        String template = "Hello {{name}}, welcome to {{place}}!";
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "World");

        AiResult result = LocalAiProcessor.fillTemplate(template, vars);

        assertTrue(result.success);
        assertEquals("Hello World, welcome to {{place}}!", result.content);
    }

    // --- classifyText ---

    @Test
    public void classifyText_nullInput_returnsFail() {
        AiResult result = LocalAiProcessor.classifyText(null);

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void classifyText_emptyInput_returnsFail() {
        AiResult result = LocalAiProcessor.classifyText("");

        assertFalse(result.success);
        assertEquals("输入为空", result.message);
    }

    @Test
    public void classifyText_technicalQuestion_returnsTechCategory() {
        AiResult result = LocalAiProcessor.classifyText("应用闪退了，出现bug和错误");

        assertTrue(result.success);
        assertTrue(result.content.contains("技术问题"));
    }

    @Test
    public void classifyText_featureSuggestion_returnsSuggestionCategory() {
        AiResult result = LocalAiProcessor.classifyText("希望能增加新功能");

        assertTrue(result.success);
        assertTrue(result.content.contains("功能建议"));
    }

    @Test
    public void classifyText_positiveFeedback_returnsPositiveCategory() {
        AiResult result = LocalAiProcessor.classifyText("这个应用不错，我很喜欢");

        assertTrue(result.success);
        assertTrue(result.content.contains("正面评价"));
    }

    // --- recognizeCommand ---

    @Test
    public void recognizeCommand_nullInput_returnsUnknown() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand(null);

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
        assertNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_emptyInput_returnsUnknown() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_summarize_returnsSummarize() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("总结一下这段文字");

        assertEquals("summarize", cmd.type);
        assertTrue(cmd.isKnown());
        assertNotNull(cmd.rawText);
    }

    @Test
    public void recognizeCommand_translate_returnsTranslate() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("翻译这段话");

        assertEquals("translate", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_ocr_returnsOcr() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("ocr");

        assertEquals("ocr", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_rewrite_returnsRewrite() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("润色这段文字");

        assertEquals("rewrite", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_qaPairs_returnsQaPairs() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("问答");

        assertEquals("qa_pairs", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_keywords_returnsKeywords() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("提取关键词");

        assertEquals("keywords", cmd.type);
        assertTrue(cmd.isKnown());
    }

    @Test
    public void recognizeCommand_unknownCommand_returnsUnknown() {
        LocalAiProcessor.AiCommand cmd = LocalAiProcessor.recognizeCommand("随便说点什么");

        assertEquals("unknown", cmd.type);
        assertFalse(cmd.isKnown());
    }
}
