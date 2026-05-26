package com.gamecenter.app.ai.legal;

import com.gamecenter.app.ai.model.AiModelInfo;

/**
 * AI 法律声明与条款管理 — 集中管理 AI 功能相关的法律文本和合规提示。
 * <p>
 * 你可以把这个类想象成应用里的"法务部"：
 * 它负责准备各种法律声明文本，确保用户在使用 AI 功能前了解相关风险和条款。
 * 比如，下载本地模型前要让用户确认条款，使用 AI 功能时要有免责声明。
 * <p>
 * 本类负责生成用户可见的法律声明和合规提示文本，包括：
 * <ul>
 *   <li>Gemma 模型下载前的条款确认提示</li>
 *   <li>应用 AI 功能的通用免责声明</li>
 * </ul>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>采用纯静态工具类设计，所有方法为静态方法，无需实例化；</li>
 *   <li>条款版本号（GEMMA_NOTICE_VERSION）与同意状态配合使用，
 *       当条款内容更新时递增版本号，用户需重新同意；</li>
 *   <li>法律文本硬编码在代码中而非资源文件，确保文本完整性不被翻译流程修改。</li>
 * </ul>
 */
public final class AiLegalNotices {

    /**
     * Gemma 条款版本标识。
     * <p>
     * 当 Gemma 使用条款内容发生变更时，需更新此版本号。
     * 用户已同意的版本号会记录在 AiPreferences 中，
     * 若本地记录的版本号与当前版本号不一致，将要求用户重新同意。
     * 就像软件更新了用户协议，版本号变了就要重新同意。
     */
    public static final String GEMMA_NOTICE_VERSION = "gemma-terms-2026-05-14";

    // Google Gemma 使用条款的在线地址
    public static final String GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms";

    // 私有构造方法，防止实例化
    private AiLegalNotices() {
    }

    /**
     * 构建 Gemma 模型下载前的法律声明文本。
     * <p>
     * 该文本在用户首次下载 Gemma 模型时展示，要求用户确认以下事项：
     * <ol>
     *   <li>已阅读并同意 Google Gemma Terms</li>
     *   <li>理解模型输出可能不准确，不可作为高风险决策依据</li>
     *   <li>了解模型存储位置及卸载/清理数据的影响</li>
     *   <li>了解本地推理与云端推理的隐私差异</li>
     *   <li>承诺不使用模型生成违法有害内容</li>
     *   <li>不同意条款应取消下载</li>
     * </ol>
     *
     * @param model 待下载的模型信息，用于获取模型名称、上游地址和许可证 URL；
     *              若为 null 则使用默认值 "Gemma"
     * @return 格式化后的法律声明文本
     */
    public static String buildGemmaDownloadNotice(AiModelInfo model) {
        String modelName = model == null ? "Gemma" : model.name;
        String upstreamUrl = model == null ? "" : model.upstreamUrl;
        // 优先使用模型自带的许可证 URL，否则回退到 Gemma 通用条款 URL
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

    /**
     * 构建应用 AI 功能的通用免责声明文本。
     * <p>
     * 该文本用于应用设置或关于页面，说明 AI 功能的基本使用须知：
     * <ul>
     *   <li>本地模型和云端模型两种处理方式的区别</li>
     *   <li>AI 输出仅供参考，用户需自行判断</li>
     * </ul>
     *
     * @return AI 功能通用免责声明文本
     */
    public static String buildAppAiNotice() {
        return "AI 功能说明：本应用可能提供本地模型和云端模型两种处理方式。"
                + "本地模型在设备上运行，云端模型会把你的输入发送给所选服务商。"
                + "AI 输出仅供参考，用户应自行判断其准确性、合法性和适用性。";
    }
}
