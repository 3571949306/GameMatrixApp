package com.gamecenter.app.ai.model;

import android.content.Context;

import com.gamecenter.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 模型下载管理器 — 负责端侧 AI 模型的清单获取、文件下载和完整性校验。
 *
 * <p>你可以把这个类想象成一个"应用商店的下载管理器"：
 * 它先从服务器获取可下载的模型列表（就像浏览应用商店），
 * 然后下载你选择的模型文件（就像下载 App），
 * 下载完后还会校验文件是否完整（就像验证安装包的数字签名）。</p>
 *
 * <p>该类是 AI 模型离线部署流程的核心组件，承担以下职责：</p>
 * <ul>
 *   <li>从远程服务器获取可用模型清单（models.json）</li>
 *   <li>下载模型文件到本地存储，支持进度回调</li>
 *   <li>通过 SHA-256 校验确保下载文件的完整性</li>
 *   <li>管理模型文件的本地存储路径</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用单线程线程池（Executors.newSingleThreadExecutor）确保所有网络和 IO 操作
 *       在同一后台线程顺序执行，避免并发下载导致的资源竞争</li>
 *   <li>采用"先下载临时文件，校验通过后重命名"的策略，防止下载中断导致损坏文件覆盖已有模型</li>
 *   <li>使用回调接口（Callback / DownloadCallback）而非返回值，适配异步执行模型</li>
 *   <li>始终使用应用私有内部存储，避免旧版外部存储权限允许其他应用替换模型文件</li>
 * </ul>
 */
public final class AiModelDownloadManager {

    /** 模型文件存储目录名称 */
    private static final String MODEL_DIR = "ai_models";

    /** 远程模型清单文件的路径（相对于服务器根路径） */
    private static final String MANIFEST_PATH = "/ai-models/models.json";

    /** 清单文件本身也必须有上限，避免把远程响应无限读入内存。 */
    private static final long MAX_MANIFEST_SIZE_BYTES = 4L * 1024L * 1024L;

    /** 单线程执行器，确保所有下载和网络操作串行执行，避免并发问题 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 从远程服务器获取可用模型清单。
     * 在后台线程中请求 models.json，解析为 AiModelInfo 列表后通过回调返回。
     *
     * @param callback 结果回调，成功时返回模型信息列表，失败时返回异常
     */
    public void fetchModels(Callback<List<AiModelInfo>> callback) {
        executor.execute(() -> {
            try {
                // 拼接清单 URL：服务器地址 + 清单路径，需去除服务器地址末尾的斜杠避免双斜杠
                String manifestUrl = trimTrailingSlash(BuildConfig.SERVER_URL) + MANIFEST_PATH;
                JSONObject root = fetchJson(manifestUrl);
                // 解析 JSON 中的 models 数组，逐个转换为 AiModelInfo 对象
                JSONArray array = root.optJSONArray("models");
                List<AiModelInfo> models = new ArrayList<>();
                models.addAll(buildBuiltInModels());
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        AiModelInfo model = AiModelInfo.fromJson(array.getJSONObject(i));
                        if (!containsModel(models, model.id)) {
                            models.add(model);
                        }
                    }
                }
                callback.onSuccess(models);
            } catch (Exception e) {
                List<AiModelInfo> fallback = buildBuiltInModels();
                if (!fallback.isEmpty()) {
                    callback.onSuccess(fallback);
                } else {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * 下载指定模型文件到本地存储。
     *
     * <p>下载流程（就像网购：下单 → 发货 → 验货 → 签收）：</p>
     * <ol>
     *   <li>检查模型是否启用且下载地址有效</li>
     *   <li>确保本地模型目录存在</li>
     *   <li>下载到临时文件（.download 后缀）</li>
     *   <li>强制执行 SHA-256 和文件大小完整性校验</li>
     *   <li>校验通过后，将临时文件重命名为正式文件名</li>
     * </ol>
     *
     * <p>如果校验失败，会自动删除临时文件，避免残留损坏数据。</p>
     *
     * @param context  Android 上下文，用于确定存储路径
     * @param model    要下载的模型信息
     * @param callback 下载进度和结果回调
     */
    public void download(Context context, AiModelInfo model, DownloadCallback callback) {
        executor.execute(() -> {
            File temp = null;
            try {
                // 前置检查：模型必须启用且具有有效下载地址
                if (model == null) {
                    throw new IllegalArgumentException("Model metadata is null");
                }
                if (!model.enabled || model.downloadUrl.isEmpty()) {
                    throw new IllegalStateException(model.note.isEmpty()
                            ? "Model package is not enabled on VPS"
                            : model.note);
                }
                validateDownloadMetadata(model);
                File dir = getModelDir(context);
                // 确保模型目录存在，mkdirs() 在目录已存在时返回 false 但不报错
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Cannot create model directory");
                }
                File target = AiModelDownloadValidator.resolveContainedFile(dir, model.fileName);
                // 使用 .download 后缀的临时文件，防止下载中断导致损坏文件覆盖已有模型
                temp = AiModelDownloadValidator.resolveContainedFile(dir, model.fileName + ".download");
                downloadFile(model.downloadUrl, temp, model.sizeBytes, callback);
                // SHA-256 完整性校验是下载模型的强制条件，不能由清单选择性关闭。
                String actual = sha256(temp);
                if (!model.sha256.equalsIgnoreCase(actual)) {
                    throw new IllegalStateException("Model SHA-256 verification failed");
                }
                // 删除已存在的旧版本模型文件
                if (target.exists()) {
                    if (!target.delete()) {
                        throw new IllegalStateException("Cannot replace existing model file");
                    }
                }
                // 将校验通过的临时文件重命名为正式文件名，完成下载
                if (!temp.renameTo(target)) {
                    throw new IllegalStateException("Cannot finalize model file");
                }
                temp = null;
                callback.onComplete(target);
            } catch (Exception e) {
                if (temp != null && temp.exists() && !temp.delete()) {
                    // 不覆盖原始错误，但确保后续不会误用未完成的临时文件。
                    temp.deleteOnExit();
                }
                callback.onError(e);
            }
        });
    }

    /**
     * 获取模型文件的本地存储目录。
     * 使用应用私有内部存储。模型文件稍后会被完整性校验后交给推理引擎；
     * 将根目录固定在 {@link Context#getDir(String, int)} 可避免旧版外部存储权限
     * 允许其他应用在“校验通过”和“引擎打开”之间替换文件或符号链接。
     *
     * @param context Android 上下文
     * @return 模型存储目录的 File 对象
     */
    public File getModelDir(Context context) {
        return context.getDir(MODEL_DIR, Context.MODE_PRIVATE);
    }

    /**
     * 获取指定模型的本地文件路径。
     *
     * @param context Android 上下文
     * @param model   模型信息
     * @return 模型文件的 File 对象（文件不一定存在）
     */
    public File getModelFile(Context context, AiModelInfo model) {
        if (model == null) {
            throw new IllegalArgumentException("Model metadata is null");
        }
        try {
            return AiModelDownloadValidator.resolveContainedFile(getModelDir(context), model.fileName);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid model file path", error);
        }
    }

    /**
     * 判断指定模型是否已下载到本地。
     *
     * <p>判断逻辑：</p>
     * <ul>
     *   <li>文件必须存在且是普通文件</li>
     *   <li>文件名、SHA-256 和正数文件大小必须通过安全边界校验</li>
     *   <li>文件大小必须与已保存的清单元数据完全匹配</li>
     * </ul>
     *
     * @param context Android 上下文
     * @param model   模型信息
     * @return true 表示模型文件已完整下载
     */
    public boolean isDownloaded(Context context, AiModelInfo model) {
        if (model == null) {
            return false;
        }
        try {
            AiModelDownloadValidator.validateModelMetadata(model.fileName, model.sha256, model.sizeBytes);
            File file = getModelFile(context, model);
            return file.isFile() && file.length() == model.sizeBytes;
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * 在加载模型前执行不可跳过的完整性校验。
     *
     * <p>{@link #isDownloaded(Context, AiModelInfo)} 只用于主线程上的列表展示，
     * 因为重新计算大型模型的哈希会阻塞界面。所有真正进入推理引擎的调用方
     * 必须使用本方法；它会重新计算本地文件的 SHA-256，防止同尺寸的篡改文件
     * 绕过快速存在性检查。</p>
     *
     * @param context Android 上下文
     * @param model   清单或持久化偏好中的模型元数据
     * @return 文件存在、大小匹配且 SHA-256 与元数据一致时返回 true
     */
    public boolean verifyDownloadedModel(Context context, AiModelInfo model) {
        if (model == null) {
            return false;
        }
        try {
            AiModelDownloadValidator.validateModelMetadata(model.fileName, model.sha256, model.sizeBytes);
            File file = getModelFile(context, model);
            if (!file.isFile() || file.length() != model.sizeBytes) {
                return false;
            }
            return model.sha256.equalsIgnoreCase(sha256(file));
        } catch (Exception error) {
            return false;
        }
    }

    /**
     * 关闭下载管理器，立即终止所有正在执行的任务。
     * 调用 shutdownNow() 会中断正在执行的下载线程。
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 从指定 URL 获取 JSON 数据。
     *
     * @param urlStr 请求 URL
     * @return 解析后的 JSONObject
     * @throws Exception 网络请求或 JSON 解析失败时抛出
     */
    private JSONObject fetchJson(String urlStr) throws Exception {
        AiModelDownloadValidator.validateDownloadUrl(
                urlStr, BuildConfig.SERVER_URL, BuildConfig.DOWNLOAD_FALLBACK_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(false);
        // 连接超时 10 秒，读取超时 30 秒（清单文件较小，无需过长超时）
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        int responseCode = conn.getResponseCode();
        if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IOException("Model manifest HTTP " + responseCode);
        }
        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_MANIFEST_SIZE_BYTES) {
            throw new IOException("Model manifest is too large");
        }
        try (BufferedInputStream input = new BufferedInputStream(conn.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long readTotal = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                readTotal += read;
                if (readTotal > MAX_MANIFEST_SIZE_BYTES) {
                    throw new IOException("Model manifest is too large");
                }
                output.write(buffer, 0, read);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 下载文件到本地，支持进度回调。
     *
     * <p>使用 256KB 缓冲区进行流式读写，平衡内存占用和 IO 效率。
     * 如果服务器未返回 Content-Length，则使用清单中记录的预期大小作为进度参考。</p>
     *
     * @param urlStr       下载 URL
     * @param target       目标文件
     * @param expectedSize 预期文件大小（字节），用于 Content-Length 缺失时的进度计算
     * @param callback     下载进度回调
     * @throws Exception 网络或 IO 异常
     */
    private void downloadFile(String urlStr, File target, long expectedSize, DownloadCallback callback) throws Exception {
        AiModelDownloadValidator.validateDownloadUrl(
                urlStr, BuildConfig.SERVER_URL, BuildConfig.DOWNLOAD_FALLBACK_BASE_URL);
        AiModelDownloadValidator.validateSize(expectedSize);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(false);
        // 下载模型文件使用更长的超时：连接 15 秒，读取 300 秒（5分钟）
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(300000);
        int responseCode = conn.getResponseCode();
        if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IOException("Model download HTTP " + responseCode);
        }
        long total = conn.getContentLengthLong();
        if (total > AiModelDownloadValidator.MAX_MODEL_SIZE_BYTES
                || (total > 0 && total != expectedSize)) {
            throw new IOException("Model content length does not match manifest");
        }
        // 服务器未返回 Content-Length 时，使用清单中的预期大小作为进度参考
        if (total <= 0) {
            total = expectedSize;
        }
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            // 256KB 缓冲区，平衡内存占用和磁盘 IO 效率
            byte[] buffer = new byte[1024 * 256];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (downloaded > expectedSize
                        || downloaded > AiModelDownloadValidator.MAX_MODEL_SIZE_BYTES) {
                    throw new IOException("Model download exceeds manifest size");
                }
                callback.onProgress(downloaded, total);
            }
            if (downloaded != expectedSize) {
                throw new IOException("Model download size does not match manifest");
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 计算文件的 SHA-256 哈希值。
     * <p>
     * 使用 1MB 缓冲区流式计算，避免将整个文件加载到内存。
     * SHA-256 就像文件的"指纹"，每个文件的指纹都是唯一的，
     * 通过对比指纹就能判断文件是否被篡改。
     *
     * @param file 待校验的文件
     * @return 小写十六进制格式的 SHA-256 哈希值
     * @throws Exception 文件读取或哈希计算异常
     */
    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // 1MB 缓冲区，流式读取避免大文件 OOM
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        // 将字节数组转换为小写十六进制字符串
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest()) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static void validateDownloadMetadata(AiModelInfo model) {
        AiModelDownloadValidator.validateModelMetadata(model.fileName, model.sha256, model.sizeBytes);
        AiModelDownloadValidator.validateDownloadUrl(
                model.downloadUrl, BuildConfig.SERVER_URL, BuildConfig.DOWNLOAD_FALLBACK_BASE_URL);
    }

    /**
     * 去除字符串末尾的所有斜杠，避免 URL 拼接时出现双斜杠。
     *
     * @param value 待处理的字符串，可能为 null
     * @return 去除末尾斜杠后的字符串，null 输入返回空字符串
     */
    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 返回不依赖服务器清单的端侧模型选项。
     * 涵盖规则引擎兜底以及精选推荐的 Qwen2.5、DeepSeek 蒸馏与 Gemma 端侧量化模型。
     */
    private static List<AiModelInfo> buildBuiltInModels() {
        List<AiModelInfo> models = new ArrayList<>();
        try {
            // 1. 本地规则引擎
            JSONObject rules = new JSONObject();
            rules.put("id", "on-device");
            rules.put("name", "本地规则引擎（全机型）");
            rules.put("runtime", "rules");
            rules.put("version", "builtin");
            rules.put("fileName", "on-device");
            rules.put("sizeBytes", 0);
            rules.put("estimatedPeakMemoryBytes", 64L * 1024L * 1024L);
            rules.put("minSdk", 24);
            rules.put("minRamMb", 1024);
            rules.put("enabled", true);
            rules.put("note", "无需下载，0 内存开销，适合离线基础摘要、关键词提取与格式清洗。");
            models.add(AiModelInfo.fromJson(rules));

            // 2. Qwen2.5-0.5B (主流机型推荐)
            JSONObject qwen05 = new JSONObject();
            qwen05.put("id", "qwen2.5-0.5b-it-q4");
            qwen05.put("name", "Qwen2.5-0.5B Instruct (极速轻量)");
            qwen05.put("runtime", "mediapipe-llm");
            qwen05.put("version", "2.5.0");
            qwen05.put("fileName", "qwen2.5-0.5b-instruct-q4.task");
            qwen05.put("sizeBytes", 350L * 1024L * 1024L);
            qwen05.put("estimatedPeakMemoryBytes", 520L * 1024L * 1024L);
            qwen05.put("minSdk", 26);
            qwen05.put("minRamMb", 3500);
            qwen05.put("enabled", true);
            qwen05.put("note", "约 350MB，中文问答、错题初解与网页速读首选，低时延低发热。");
            models.add(AiModelInfo.fromJson(qwen05));

            // 3. Qwen2.5-1.5B (高性能机型推荐)
            JSONObject qwen15 = new JSONObject();
            qwen15.put("id", "qwen2.5-1.5b-it-q4");
            qwen15.put("name", "Qwen2.5-1.5B Instruct (进阶全能)");
            qwen15.put("runtime", "mediapipe-llm");
            qwen15.put("version", "2.5.0");
            qwen15.put("fileName", "qwen2.5-1.5b-instruct-q4.task");
            qwen15.put("sizeBytes", 980L * 1024L * 1024L);
            qwen15.put("estimatedPeakMemoryBytes", 1350L * 1024L * 1024L);
            qwen15.put("minSdk", 26);
            qwen15.put("minRamMb", 6000);
            qwen15.put("enabled", true);
            qwen15.put("note", "约 980MB，支持多步错题推导、复杂自然语言指令与棋局深度解说。");
            models.add(AiModelInfo.fromJson(qwen15));

            // 4. Gemma-3-1B-IT
            JSONObject gemma = new JSONObject();
            gemma.put("id", "gemma3-1b-it-q4");
            gemma.put("name", "Gemma3-1B-IT Q4 (Google 官方)");
            gemma.put("runtime", "mediapipe-llm");
            gemma.put("version", "3.0.0");
            gemma.put("fileName", "gemma3-1b-it-q4.task");
            gemma.put("sizeBytes", 750L * 1024L * 1024L);
            gemma.put("estimatedPeakMemoryBytes", 1100L * 1024L * 1024L);
            gemma.put("minSdk", 26);
            gemma.put("minRamMb", 4000);
            gemma.put("enabled", true);
            gemma.put("note", "Google 官方轻量模型，英语与跨语言能力强，深度结合 MediaPipe GPU 加速。");
            models.add(AiModelInfo.fromJson(gemma));

            // 5. DeepSeek-R1-Distill-Qwen-1.5B (深度思考模型)
            JSONObject deepseek = new JSONObject();
            deepseek.put("id", "deepseek-r1-distill-qwen-1.5b-q4");
            deepseek.put("name", "DeepSeek-R1-Distill-1.5B (推理增强)");
            deepseek.put("runtime", "mediapipe-llm");
            deepseek.put("version", "1.0.0");
            deepseek.put("fileName", "deepseek-r1-distill-qwen-1.5b-q4.task");
            deepseek.put("sizeBytes", 1024L * 1024L * 1024L);
            deepseek.put("estimatedPeakMemoryBytes", 1450L * 1024L * 1024L);
            deepseek.put("minSdk", 26);
            deepseek.put("minRamMb", 6000);
            deepseek.put("enabled", true);
            deepseek.put("note", "包含完整思维链（<think>...</think>），适合数学定理证明与复杂数理题目拆解。");
            models.add(AiModelInfo.fromJson(deepseek));
        } catch (Exception ignored) {
            // Built-in metadata is static; ignore and return any entries already added.
        }
        return models;
    }

    private static boolean containsModel(List<AiModelInfo> models, String id) {
        for (AiModelInfo model : models) {
            if (model.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通用回调接口，用于异步操作的结果通知。
     *
     * @param <T> 结果数据类型
     */
    public interface Callback<T> {
        /** 操作成功时调用 */
        void onSuccess(T value);
        /** 操作失败时调用 */
        void onError(Exception error);
    }

    /**
     * 下载专用回调接口，支持进度通知。
     */
    public interface DownloadCallback {
        /** 下载进度更新时调用 */
        void onProgress(long downloaded, long total);
        /** 下载完成且校验通过时调用 */
        void onComplete(File file);
        /** 下载失败时调用 */
        void onError(Exception error);
    }
}
