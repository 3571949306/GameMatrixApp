package com.gamecenter.app.ai.local;

import android.content.Context;

import com.gamecenter.app.ai.data.AiResult;

/**
 * 本地 AI 处理入口 — 处理低复杂度任务，不依赖云端 API。
 *
 * 当前支持的能力：
 * - OCR 后处理（文本清洗/格式化）
 * - 简单分类
     * - 关键词提取（基于规则）
     * - 简短总结（基于规则的摘取）
 * - 翻译/润色/问答的本地 MVP 兜底
     * - 指令识别
     * - 固定模板生成
 */
public final class LocalAiProcessor {

    /**
     * OCR 后处理：清洗 OCR 输出的杂字符、修正换行。
     */
    public static AiResult processOcrResult(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String cleaned = cleanOcrText(rawText);
        return AiResult.success(cleaned).source("local").build();
    }

    /**
     * 简单摘要：基于规则提取关键行（前 N 行 + 含数字的行）。
     * 适用于笔记、列表等结构化文本。
     */
    public static AiResult simpleSummarize(String text, int maxLines) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 保留：前几行、含数字的行、较短的行（可能是要点）
            if (count < 3 || containsDigits(trimmed) || trimmed.length() < 80) {
                sb.append(trimmed).append("\n");
                count++;
                if (count >= maxLines) break;
            }
        }
        return AiResult.success(sb.toString().trim()).source("local").build();
    }

    /**
     * 关键词提取：基于简单规则提取关键词（中文分词较复杂，此处用标点/空格分割）。
     */
    public static AiResult extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        // 移除常见停用词
        String[] stopWords = {"的", "了", "是", "在", "和", "有", "我", "他", "她", "它",
                "这", "那", "不", "没", "会", "能", "可以", "已经", "正在", "将要"};
        String cleaned = text.replaceAll("[,，。.!！?？:：;；''\"\"【】（）()#]", " ")
                .replaceAll("\\s+", " ").trim();
        String[] words = cleaned.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() >= 2) {
                boolean isStop = false;
                for (String sw : stopWords) {
                    if (word.equals(sw)) { isStop = true; break; }
                }
                if (!isStop) {
                    sb.append(word).append(", ");
                }
            }
        }
        String result = sb.length() > 0
                ? sb.substring(0, sb.length() - 2)
                : "未能提取有效关键词";
        return AiResult.success(result).source("local").build();
    }

    /**
     * 翻译 MVP：无云端时提供本地可读初稿。英文短语做常见词替换，中文输入则给出待翻译整理稿。
     */
    public static AiResult translateText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String trimmed = text.trim();
        if (containsChinese(trimmed)) {
            return AiResult.success("本地翻译草稿（建议配置 API Key 获取完整翻译）:\n\n" + trimmed)
                    .source("local").build();
        }

        String translated = " " + trimmed.toLowerCase() + " ";
        String[][] dict = {
                {" error ", " 错误 "}, {" issue ", " 问题 "}, {" bug ", " 缺陷 "},
                {" update ", " 更新 "}, {" download ", " 下载 "}, {" install ", " 安装 "},
                {" network ", " 网络 "}, {" server ", " 服务器 "}, {" client ", " 客户端 "},
                {" game ", " 游戏 "}, {" tool ", " 工具 "}, {" user ", " 用户 "},
                {" failed ", " 失败 "}, {" success ", " 成功 "}, {" settings ", " 设置 "},
                {" history ", " 历史记录 "}, {" summary ", " 摘要 "}
        };
        for (String[] pair : dict) {
            translated = translated.replace(pair[0], pair[1]);
        }
        return AiResult.success("本地翻译草稿:\n\n" + translated.trim()
                + "\n\n注：本地模式只做常见词汇辅助翻译，完整翻译请配置 API Key。")
                .source("local").build();
    }

    /**
     * 润色 MVP：清理空白、统一标点，并输出更适合直接复制的文本。
     */
    public static AiResult polishText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String result = text.trim()
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\s*([，。！？；：,.!?;:])\\s*", "$1")
                .replaceAll("([。！？.!?])", "$1\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return AiResult.success("润色稿:\n\n" + result).source("local").build();
    }

    /**
     * 简单问答 MVP：从文本中抽取要点并生成可复习的问答对。
     */
    public static AiResult generateQaPairs(String text, int maxPairs) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String[] parts = text.split("[\\n。！？.!?]");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            String item = part.trim();
            if (item.length() < 8) {
                continue;
            }
            count++;
            sb.append("Q").append(count).append(": 这段内容的要点是什么？\n");
            sb.append("A").append(count).append(": ").append(item).append("\n\n");
            if (count >= maxPairs) {
                break;
            }
        }
        if (count == 0) {
            sb.append("Q1: 用户输入的核心内容是什么？\n");
            sb.append("A1: ").append(text.trim()).append("\n");
        }
        return AiResult.success(sb.toString().trim()).source("local").build();
    }

    /**
     * 固定模板填充：替换模板中的占位符。
     */
    public static AiResult fillTemplate(String template, java.util.Map<String, String> vars) {
        if (template == null) {
            return AiResult.fail("模板为空").source("local").build();
        }
        String result = template;
        if (vars != null) {
            for (java.util.Map.Entry<String, String> entry : vars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return AiResult.success(result).source("local").build();
    }

    /**
     * 分类：基于关键词判断文本类别。
     */
    public static AiResult classifyText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String lower = text.toLowerCase();
        String category;
        if (containsAny(lower, "bug", "错误", "崩溃", "闪退", "问题")) {
            category = "技术问题";
        } else if (containsAny(lower, "建议", "希望", "想要", "功能", "改进")) {
            category = "功能建议";
        } else if (containsAny(lower, "反馈", "投诉", "不满", "差评")) {
            category = "用户反馈";
        } else if (containsAny(lower, "表扬", "感谢", "好评", "不错", "喜欢")) {
            category = "正面评价";
        } else {
            category = "其他";
        }
        return AiResult.success("分类结果: " + category).source("local").build();
    }

    /**
     * 指令识别：识别常见的用户指令。
     */
    public static AiCommand recognizeCommand(String text) {
        if (text == null) return new AiCommand("unknown", null);
        String lower = text.trim().toLowerCase();

        if (lower.startsWith("总结") || lower.startsWith("摘要") || lower.contains("帮我总结")) {
            return new AiCommand("summarize", lower);
        }
        if (lower.startsWith("翻译") || lower.startsWith("translate")) {
            return new AiCommand("translate", lower);
        }
        if (lower.startsWith("ocr") || lower.startsWith("识别图片")) {
            return new AiCommand("ocr", lower);
        }
        if (lower.startsWith("润色") || lower.startsWith("改写") || lower.startsWith("优化")) {
            return new AiCommand("rewrite", lower);
        }
        if (lower.startsWith("问答") || lower.startsWith("q:")) {
            return new AiCommand("qa_pairs", lower);
        }
        if (lower.contains("关键词") || lower.contains("keyword")) {
            return new AiCommand("keywords", lower);
        }
        return new AiCommand("unknown", null);
    }

    private static String cleanOcrText(String text) {
        // 合并多余空行
        text = text.replaceAll("\\n\\s*\\n", "\n\n");
        // 移除行首行尾空白
        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append("\n");
            }
        }
        // 移除 OCR 常见噪点（单个特殊字符行）
        return sb.toString().replaceAll("(?m)^[\\W_]{1,3}$", "").trim();
    }

    private static boolean containsDigits(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) return true;
        }
        return false;
    }

    private static boolean containsChinese(String s) {
        for (char c : s.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 识别的指令。
     */
    public static class AiCommand {
        public final String type;     // "summarize", "translate", "ocr", "rewrite", "qa_pairs", "keywords", "unknown"
        public final String rawText;

        public AiCommand(String type, String rawText) {
            this.type = type;
            this.rawText = rawText;
        }

        public boolean isKnown() {
            return !"unknown".equals(type);
        }
    }
}
