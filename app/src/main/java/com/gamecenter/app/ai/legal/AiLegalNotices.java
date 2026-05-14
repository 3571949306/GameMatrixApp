package com.gamecenter.app.ai.legal;

import com.gamecenter.app.ai.model.AiModelInfo;

public final class AiLegalNotices {
    public static final String GEMMA_NOTICE_VERSION = "gemma-terms-2026-05-14";
    public static final String GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms";

    private AiLegalNotices() {
    }

    public static String buildGemmaDownloadNotice(AiModelInfo model) {
        String modelName = model == null ? "Gemma" : model.name;
        String upstreamUrl = model == null ? "" : model.upstreamUrl;
        String licenseUrl = model != null && !model.licenseUrl.isEmpty()
                ? model.licenseUrl
                : GEMMA_TERMS_URL;
        StringBuilder builder = new StringBuilder();
        builder.append("你将下载并在本机运行 ").append(modelName).append(" 模型。\n\n");
        builder.append("在继续前，请确认：\n");
        builder.append("1. 你已阅读并同意 Google Gemma Terms，模型权重按该条款提供。\n");
        builder.append("2. 模型输出可能不准确、不完整或带有偏差，不能作为医疗、法律、金融、安全等高风险决策的唯一依据。\n");
        builder.append("3. 本地模型会下载到本应用私有目录，卸载应用或清理应用数据可能删除模型文件。\n");
        builder.append("4. 本地推理在你的设备上运行；下载模型会访问更新服务器，输入内容默认不因本地推理上传到服务器。\n");
        builder.append("5. 你不得使用模型生成、传播违法、有害、侵权、欺诈或违反适用法律法规及平台规则的内容。\n");
        builder.append("6. 如果你不同意这些条款，请取消下载并不要启用本地模型。\n\n");
        builder.append("Gemma Terms: ").append(licenseUrl);
        if (!upstreamUrl.isEmpty()) {
            builder.append("\nModel source: ").append(upstreamUrl);
        }
        return builder.toString();
    }

    public static String buildAppAiNotice() {
        return "AI 功能说明：本应用可能提供本地模型和云端模型两种处理方式。"
                + "本地模型在设备上运行，云端模型会把你的输入发送给所选服务商。"
                + "AI 输出仅供参考，用户应自行判断其准确性、合法性和适用性。";
    }
}
