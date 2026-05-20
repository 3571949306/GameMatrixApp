package com.gamecenter.app.ai.model;

import org.json.JSONObject;

/**
 * AI 本地模型信息 — 描述一个可下载的端侧 AI 模型的完整元数据。
 *
 * <p>该类封装了端侧 AI 模型的所有元信息，包括模型标识、运行时环境、
 * 下载地址、完整性校验以及硬件兼容性要求等。数据来源于远程 models.json 清单文件，
 * 通过 fromJson 工厂方法从 JSON 反序列化创建。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>不可变设计（final 类 + final 字段），模型信息一旦解析完成不可修改</li>
 *   <li>构造方法私有化，强制通过 fromJson 工厂方法创建，确保数据来源可控</li>
 *   <li>使用 optString/optLong/optInt/optBoolean 安全解析 JSON，缺失字段使用合理默认值</li>
 *   <li>包含硬件兼容性字段（minSdk、minRamMb），支持下载前的设备兼容性检查</li>
 *   <li>包含完整性校验字段（sha256），确保下载文件未被篡改</li>
 * </ul>
 */
public final class AiModelInfo {

    /** 模型唯一标识，对应清单文件中的 id 字段 */
    public final String id;

    /** 模型显示名称，缺失时回退为 id */
    public final String name;

    /** 运行时类型，标识模型使用的推理引擎（如 "tflite"、"onnx" 等） */
    public final String runtime;

    /** 模型版本号 */
    public final String version;

    /** 模型文件名，缺失时默认为 "{id}.task" */
    public final String fileName;

    /** 模型文件下载地址（CDN 或 VPS 直链） */
    public final String downloadUrl;

    /** 模型上游来源地址（如 HuggingFace、ModelScope 等原始仓库） */
    public final String upstreamUrl;

    /** 模型许可证地址，指向开源协议文本 */
    public final String licenseUrl;

    /** 模型文件的 SHA-256 校验值，用于下载后完整性验证 */
    public final String sha256;

    /** 模型文件大小（字节），用于下载进度计算和存储空间检查 */
    public final long sizeBytes;

    /** 模型运行时预估峰值内存（字节），用于设备兼容性判断 */
    public final long estimatedPeakMemoryBytes;

    /** 最低 Android SDK 版本要求，默认为 24（Android 7.0） */
    public final int minSdk;

    /** 最低运行内存要求（MB），默认为 2048MB（2GB） */
    public final int minRamMb;

    /** 是否在服务端启用下载，false 表示模型包尚未上传至 VPS */
    public final boolean enabled;

    /** 备注信息，通常在模型未启用时说明原因 */
    public final String note;

    /**
     * 私有构造方法，从 JSON 对象解析模型信息。
     * 所有字段均使用 optXxx 方法安全解析，缺失时使用合理默认值：
     * <ul>
     *   <li>name 缺失时回退为 id</li>
     *   <li>fileName 缺失时默认为 "{id}.task"</li>
     *   <li>minSdk 默认为 24（Android 7.0）</li>
     *   <li>minRamMb 默认为 2048（2GB RAM）</li>
     *   <li>enabled 默认为 false（安全优先，未明确启用则不可用）</li>
     * </ul>
     *
     * @param json 包含模型元数据的 JSON 对象
     */
    private AiModelInfo(JSONObject json) {
        id = json.optString("id", "");
        name = json.optString("name", id);
        runtime = json.optString("runtime", "");
        version = json.optString("version", "");
        fileName = json.optString("fileName", id + ".task");
        downloadUrl = json.optString("downloadUrl", "");
        upstreamUrl = json.optString("upstreamUrl", "");
        licenseUrl = json.optString("licenseUrl", "");
        sha256 = json.optString("sha256", "");
        sizeBytes = json.optLong("sizeBytes", 0);
        estimatedPeakMemoryBytes = json.optLong("estimatedPeakMemoryBytes", 0);
        minSdk = json.optInt("minSdk", 24);
        minRamMb = json.optInt("minRamMb", 2048);
        enabled = json.optBoolean("enabled", false);
        note = json.optString("note", "");
    }

    /**
     * 从 JSON 对象创建 AiModelInfo 实例的工厂方法。
     *
     * @param json 包含模型元数据的 JSON 对象
     * @return 解析后的 AiModelInfo 实例
     */
    public static AiModelInfo fromJson(JSONObject json) {
        return new AiModelInfo(json);
    }
}
