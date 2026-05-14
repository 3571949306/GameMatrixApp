package com.gamecenter.app.ai.template;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AiTemplateManager {

    private AiTemplateManager() {
    }

    public static List<Template> getTemplates() {
        return Collections.unmodifiableList(Arrays.asList(
                new Template("会议纪要", "summary", "请总结以下会议内容，输出：结论、待办、负责人、截止时间。\n\n"),
                new Template("代码报错", "qa", "请解释以下报错的原因，并给出排查步骤和修复建议。\n\n"),
                new Template("文案润色", "rewrite", "请把以下内容润色为更清晰、专业、适合发布的表达。\n\n"),
                new Template("中英翻译", "translate", "请翻译以下内容，保持原意和语气。\n\n"),
                new Template("复习问答", "qa", "请根据以下材料生成问答对，适合复习使用。\n\n")
        ));
    }

    public static final class Template {
        public final String title;
        public final String taskType;
        public final String prompt;

        public Template(String title, String taskType, String prompt) {
            this.title = title;
            this.taskType = taskType;
            this.prompt = prompt;
        }
    }
}
