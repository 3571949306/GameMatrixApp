package com.gamecenter.app.ai.template;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * AI 提示词模板管理 — 提供预设的提示词模板，帮助用户快速开始常见 AI 任务。
 * <p>
 * 每个模板包含三个要素：
 * <ul>
 *   <li>title — 模板显示名称，用于界面上的按钮文本</li>
 *   <li>taskType — 对应的 AI 任务类型标识，决定使用哪个处理通道</li>
 *   <li>prompt — 预设的提示词前缀，用户可在此基础上继续编辑</li>
 * </ul>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>采用纯静态工具类设计，模板列表硬编码，简单可靠；</li>
 *   <li>返回不可变列表（Collections.unmodifiableList），防止调用方意外修改模板内容；</li>
 *   <li>模板的 prompt 字段以换行符结尾，方便用户在模板后直接粘贴内容。</li>
 * </ul>
 */
public final class AiTemplateManager {

    private AiTemplateManager() {
    }

    /**
     * 获取所有可用的预设模板列表。
     * <p>
     * 当前内置模板包括：
     * <ol>
     *   <li>会议纪要 — 摘要任务，输出结论/待办/负责人/截止时间</li>
     *   <li>代码报错 — 问答任务，解释报错原因并给出排查修复建议</li>
     *   <li>文案润色 — 改写任务，润色为更清晰专业的表达</li>
     *   <li>中英翻译 — 翻译任务，保持原意和语气</li>
     *   <li>复习问答 — 问答任务，根据材料生成复习用问答对</li>
     * </ol>
     *
     * @return 不可变的模板列表
     */
    public static List<Template> getTemplates() {
        return Collections.unmodifiableList(Arrays.asList(
                new Template("会议纪要", "summary", "请总结以下会议内容，输出：结论、待办、负责人、截止时间。\n\n"),
                new Template("代码报错", "qa", "请解释以下报错的原因，并给出排查步骤和修复建议。\n\n"),
                new Template("文案润色", "rewrite", "请把以下内容润色为更清晰、专业、适合发布的表达。\n\n"),
                new Template("中英翻译", "translate", "请翻译以下内容，保持原意和语气。\n\n"),
                new Template("复习问答", "qa", "请根据以下材料生成问答对，适合复习使用。\n\n")
        ));
    }

    /**
     * 提示词模板数据类。
     * <p>
     * 不可变值对象，所有字段为 public final，构造后不可修改。
     */
    public static final class Template {

        /** 模板显示名称，用于界面按钮文本 */
        public final String title;

        /** 对应的 AI 任务类型标识（如 "summary"、"qa"、"translate" 等） */
        public final String taskType;

        /** 预设的提示词前缀文本，以换行符结尾方便用户追加内容 */
        public final String prompt;

        /**
         * 构造模板实例。
         *
         * @param title    模板显示名称
         * @param taskType AI 任务类型标识
         * @param prompt   预设提示词前缀
         */
        public Template(String title, String taskType, String prompt) {
            this.title = title;
            this.taskType = taskType;
            this.prompt = prompt;
        }
    }
}
