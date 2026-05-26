package com.gamecenter.app.ai.local;

import android.content.Context;

import com.gamecenter.app.ai.data.AiResult;

/**
 * 本地 AI 处理入口 — 处理低复杂度任务，不依赖云端 API。
 * <p>
 * 你可以把这个类想象成一个"万能小工具箱"：
 * 里面装着各种简单但实用的小工具（规则引擎），每个工具对应一种 AI 能力。
 * 这些工具虽然不如云端 AI 那么聪明，但胜在免费、快速、离线可用。
 * 就像家里的修车工具箱，虽然比不上专业汽修厂，但简单的问题自己就能搞定。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>所有方法均为静态方法，无状态，线程安全，可直接调用。</li>
 *   <li>采用基于规则和启发式的方法实现各类 AI 能力，无需模型推理，
 *       适用于离线场景和低延迟需求。</li>
 *   <li>对于翻译、润色、问答等任务，本地处理仅为 MVP 兜底方案，
 *       质量远低于云端 API，仅在用户未配置 API Key 时启用。</li>
 *   <li>OCR 后处理和关键词提取等任务，本地规则处理已能满足基本需求。</li>
 * </ul>
 * <p>
 * 当前支持的能力：
 * <ul>
 *   <li>OCR 后处理（文本清洗/格式化）</li>
 *   <li>简单分类（基于关键词匹配）</li>
 *   <li>关键词提取（基于规则，移除停用词后提取）</li>
 *   <li>简短总结（基于规则的摘取：前几行 + 含数字行 + 短行）</li>
 *   <li>翻译/润色/问答的本地 MVP 兜底</li>
 *   <li>指令识别（识别用户输入中的常见命令关键词）</li>
 *   <li>固定模板生成（占位符替换）</li>
 * </ul>
 */
public final class LocalAiProcessor {

    // 私有构造方法，防止实例化（所有方法都是静态的）
    private LocalAiProcessor() {}

    /**
     * OCR 后处理：清洗 OCR 输出的杂字符、修正换行。
     * <p>
     * 处理流程：合并多余空行 → 移除行首行尾空白 → 移除 OCR 常见噪点（单个特殊字符行）。
     *
     * @param rawText OCR 原始识别文本
     * @return 清洗后的文本；输入为空时返回失败结果
     */
    public static AiResult processOcrResult(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String cleaned = cleanOcrText(rawText);
        return AiResult.success(cleaned).source("local").build();
    }

    /**
     * 简单摘要：基于规则提取关键行（前 N 行 + 含数字的行 + 较短的行）。
     * <p>
     * 适用于笔记、列表等结构化文本。提取策略：
     * <ul>
     *   <li>前 3 行：通常包含标题或核心内容</li>
     *   <li>含数字的行：可能包含数据、序号等关键信息</li>
     *   <li>长度小于 80 字符的行：较短的行更可能是要点或标题</li>
     * </ul>
     *
     * @param text     待摘要的文本
     * @param maxLines 最大提取行数
     * @return 摘要文本；输入为空时返回失败结果
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
     * <p>
     * 处理流程：
     * <ol>
     *   <li>移除常见标点符号</li>
     *   <li>按空格分割为词语</li>
     *   <li>过滤长度小于 2 的词语</li>
     *   <li>移除中文停用词（的、了、是、在 等）</li>
     *   <li>剩余词语以逗号拼接返回</li>
     * </ol>
     * <p>
     * 注意：此方法对中文分词效果有限，仅适用于空格或标点已自然分词的文本。
     *
     * @param text 待提取关键词的文本
     * @return 逗号分隔的关键词字符串；无法提取时返回提示信息
     */
    public static AiResult extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        // 中文常见停用词列表，过滤无实际意义的虚词
        String[] stopWords = {"的", "了", "是", "在", "和", "有", "我", "他", "她", "它",
                "这", "那", "不", "没", "会", "能", "可以", "已经", "正在", "将要"};
        // 移除中英文标点符号，将连续空白合并为单个空格
        String cleaned = text.replaceAll("[,，。.!！?？:：;；''\"\"【】（）()#]", " ")
                .replaceAll("\\s+", " ").trim();
        String[] words = cleaned.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            // 过滤单字词（长度 < 2），单字通常不是有效关键词
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
        // 移除末尾多余的 ", " 分隔符
        String result = sb.length() > 0
                ? sb.substring(0, sb.length() - 2)
                : "未能提取有效关键词";
        return AiResult.success(result).source("local").build();
    }

    /**
     * 翻译 MVP：无云端时提供本地可读初稿。
     * <p>
     * 策略：
     * <ul>
     *   <li>中文输入：直接返回原文并提示建议配置 API Key 获取完整翻译</li>
     *   <li>英文输入：通过内置词典做常见词汇替换，生成粗略翻译草稿</li>
     * </ul>
     * <p>
     * 词典采用前后加空格的方式匹配完整单词，避免子串误替换
     * （如 "error" 不会匹配 "terrorist" 中的 "error"）。
     *
     * @param text 待翻译的文本
     * @return 翻译草稿结果；输入为空时返回失败结果
     */
    public static AiResult translateText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String trimmed = text.trim();
        // 中文输入无法本地翻译，提示用户配置 API Key
        if (containsChinese(trimmed)) {
            return AiResult.success("本地翻译草稿（建议配置 API Key 获取完整翻译）:\n\n" + trimmed)
                    .source("local").build();
        }

        // 英文输入：前后加空格确保单词边界匹配，避免子串误替换
        String translated = " " + trimmed.toLowerCase() + " ";
        // 内置英中词典，覆盖常见技术词汇
        String[][] dict = {
                {" error ", " 错误 "}, {" issue ", " 问题 "}, {" bug ", " 缺陷 "},
                {" update ", " 更新 "}, {" download ", " 下载 "}, {" install ", " 安装 "},
                {" network ", " 网络 "}, {" server ", " 服务器 "}, {" client ", " 客户端 "},
                {" game ", " 游戏 "}, {" tool ", " 工具 "}, {" user ", " 用户 "},
                {" failed ", " 失败 "}, {" success ", " 成功 "}, {" settings ", " 设置 "},
                {" history ", " 历史记录 "}, {" summary ", " 摘要 "}
        };
        // 逐个替换词典中的词汇
        for (String[] pair : dict) {
            translated = translated.replace(pair[0], pair[1]);
        }
        return AiResult.success("本地翻译草稿:\n\n" + translated.trim()
                + "\n\n注：本地模式只做常见词汇辅助翻译，完整翻译请配置 API Key。")
                .source("local").build();
    }

    /**
     * 润色 MVP：清理空白、统一标点，并输出更适合直接复制的文本。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>合并多余空格和制表符为单个空格</li>
     *   <li>移除标点符号前后的多余空白</li>
     *   <li>在句末标点后换行，改善可读性</li>
     *   <li>合并三个以上连续换行为两个</li>
     * </ol>
     *
     * @param text 待润色的文本
     * @return 润色后的文本；输入为空时返回失败结果
     */
    public static AiResult polishText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String result = text.trim()
                .replaceAll("[ \\t]+", " ")              // 合并连续空格/制表符
                .replaceAll("\\s*([，。！？；：,.!?;:])\\s*", "$1") // 标点前后去空白
                .replaceAll("([。！？.!?])", "$1\n")     // 句末标点后换行
                .replaceAll("\\n{3,}", "\n\n")           // 合并过多空行
                .trim();
        return AiResult.success("润色稿:\n\n" + result).source("local").build();
    }

    /**
     * 简单问答 MVP：从文本中抽取要点并生成可复习的问答对。
     * <p>
     * 策略：按句号、感叹号、问号等分割文本，将每个足够长的片段
     * （≥8 字符）作为一个要点，生成标准格式的问答对。
     * 若文本过短无法分割，则将整段文本作为唯一答案。
     *
     * @param text     待生成问答对的文本
     * @param maxPairs 最大生成问答对数量
     * @return 问答对文本；输入为空时返回失败结果
     */
    public static AiResult generateQaPairs(String text, int maxPairs) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        // 按中英文句末标点分割
        String[] parts = text.split("[\\n。！？.!?]");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            String item = part.trim();
            // 过滤过短片段（<8字符），通常不是完整要点
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
        // 兜底：若无法提取任何要点，将整段文本作为唯一答案
        if (count == 0) {
            sb.append("Q1: 用户输入的核心内容是什么？\n");
            sb.append("A1: ").append(text.trim()).append("\n");
        }
        return AiResult.success(sb.toString().trim()).source("local").build();
    }

    /**
     * 固定模板填充：替换模板中的占位符。
     * <p>
     * 占位符格式为 {@code {{key}}}，将模板中所有匹配的占位符替换为对应的值。
     * 若值为 null，则替换为空字符串。
     * 就像填表一样，把模板中的空白处填上实际内容。
     *
     * @param template 模板字符串，包含 {@code {{key}}} 格式的占位符
     * @param vars     变量映射表，key 为占位符名称，value 为替换值
     * @return 填充后的字符串；模板为 null 时返回失败结果
     */
    public static AiResult fillTemplate(String template, java.util.Map<String, String> vars) {
        if (template == null) {
            return AiResult.fail("模板为空").source("local").build();
        }
        String result = template;
        if (vars != null) {
            for (java.util.Map.Entry<String, String> entry : vars.entrySet()) {
                // 将 {{key}} 替换为对应的值，null 值替换为空字符串
                result = result.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return AiResult.success(result).source("local").build();
    }

    /**
     * 分类：基于关键词判断文本类别。
     * <p>
     * 当前支持的分类：
     * <ul>
     *   <li>技术问题：包含 bug、错误、崩溃、闪退、问题</li>
     *   <li>功能建议：包含建议、希望、想要、功能、改进</li>
     *   <li>用户反馈：包含反馈、投诉、不满、差评</li>
     *   <li>正面评价：包含表扬、感谢、好评、不错、喜欢</li>
     *   <li>其他：无法匹配以上任何类别</li>
     * </ul>
     * <p>
     * 注意：分类优先级按 if-else 顺序决定，首个匹配的类别即为结果。
     *
     * @param text 待分类的文本
     * @return 分类结果字符串，格式为 "分类结果: XXX"；输入为空时返回失败结果
     */
    public static AiResult classifyText(String text) {
        if (text == null || text.isEmpty()) {
            return AiResult.fail("输入为空").source("local").build();
        }
        String lower = text.toLowerCase();
        String category;
        // 按优先级依次匹配关键词
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
     * <p>
     * 通过关键词前缀匹配识别用户意图，支持以下指令：
     * <ul>
     *   <li>summarize：以"总结"、"摘要"开头或包含"帮我总结"</li>
     *   <li>translate：以"翻译"、"translate"开头</li>
     *   <li>ocr：以"ocr"、"识别图片"开头</li>
     *   <li>rewrite：以"润色"、"改写"、"优化"开头</li>
     *   <li>qa_pairs：以"问答"开头或以"q:"开头</li>
     *   <li>keywords：包含"关键词"或"keyword"</li>
     * </ul>
     * <p>
     * 注意：匹配顺序决定优先级，首个匹配的指令即为结果。
     *
     * @param text 用户输入的文本
     * @return 识别出的指令；无法识别时返回 type 为 "unknown" 的指令
     */
    public static AiCommand recognizeCommand(String text) {
        if (text == null) return new AiCommand("unknown", null);
        String lower = text.trim().toLowerCase();

        // 按优先级依次匹配指令关键词
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

    /**
     * 清洗 OCR 识别文本。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>合并连续空行为单个空行</li>
     *   <li>移除每行首尾空白</li>
     *   <li>移除 OCR 常见噪点行（仅含 1-3 个非字母数字字符的行）</li>
     * </ol>
     *
     * @param text OCR 原始文本
     * @return 清洗后的文本
     */
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
        // 移除 OCR 常见噪点（单个特殊字符行，如单独的竖线、点号等）
        return sb.toString().replaceAll("(?m)^[\\W_]{1,3}$", "").trim();
    }

    /**
     * 检查字符串中是否包含数字字符。
     *
     * @param s 待检查的字符串
     * @return 是否包含至少一个数字
     */
    private static boolean containsDigits(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) return true;
        }
        return false;
    }

    /**
     * 检查字符串中是否包含中文字符。
     * <p>
     * 通过 Unicode 范围判断：CJK 统一汉字区间 U+4E00 ~ U+9FFF。
     *
     * @param s 待检查的字符串
     * @return 是否包含至少一个中文字符
     */
    private static boolean containsChinese(String s) {
        for (char c : s.toCharArray()) {
            // CJK 统一汉字 Unicode 范围
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文本中是否包含任意一个指定关键词。
     *
     * @param text     待检查的文本
     * @param keywords 关键词数组
     * @return 是否包含至少一个关键词
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 识别的指令数据类。
     * <p>
     * 封装指令识别的结果，包含指令类型和原始输入文本。
     */
    public static class AiCommand {
        // 指令类型：summarize、translate、ocr、rewrite、qa_pairs、keywords、unknown
        public final String type;
        // 用户输入的原始文本（已转小写）
        public final String rawText;

        /**
         * 构造指令对象。
         *
         * @param type    指令类型标识
         * @param rawText 原始输入文本
         */
        public AiCommand(String type, String rawText) {
            this.type = type;
            this.rawText = rawText;
        }

        /**
         * 判断是否为已知指令（非 unknown）。
         *
         * @return 是否为可处理的已知指令
         */
        public boolean isKnown() {
            return !"unknown".equals(type);
        }
    }
}
