package com.gamecenter.app.ai.model;

import android.content.Context;
import android.os.Environment;

import com.gamecenter.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 模型下载管理器 — 负责端侧 AI 模型的清单获取、文件下载和完整性校验。
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
 *   <li>优先使用外部存储（getExternalFilesDir），不可用时回退到内部存储（getFilesDir）</li>
 * </ul>
 */
public final class AiModelDownloadManager {

    /** 模型文件存储目录名称 */
    private static final String MODEL_DIR = "ai_models";

    /** 远程模型清单文件的路径（相对于服务器根路径） */
    private static final String MANIFEST_PATH = "/ai-models/models.json";

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
                JSONArray array = root.optJSONArray("models");
                List<AiModelInfo> models = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        models.add(AiModelInfo.fromJson(array.getJSONObject(i)));
                    }
                }
                callback.onSuccess(models);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 下载指定模型文件到本地存储。
     *
     * <p>下载流程：</p>
     * <ol>
     *   <li>检查模型是否启用且下载地址有效</li>
     *   <li>确保本地模型目录存在</li>
     *   <li>下载到临时文件（.download 后缀）</li>
     *   <li>SHA-256 完整性校验（如果清单中提供了校验值）</li>
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
            try {
                // 前置检查：模型必须启用且具有有效下载地址
                if (!model.enabled || model.downloadUrl.isEmpty()) {
                    throw new IllegalStateException(model.note.isEmpty()
                            ? "Model package is not enabled on VPS"
                            : model.note);
                }
                File dir = getModelDir(context);
                // 确保模型目录存在，mkdirs() 在目录已存在时返回 false 但不报错
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Cannot create model directory");
                }
                File target = new File(dir, model.fileName);
                // 使用 .download 后缀的临时文件，防止下载中断导致损坏文件覆盖已有模型
                File temp = new File(dir, model.fileName + ".download");
                downloadFile(model.downloadUrl, temp, model.sizeBytes, callback);
                // SHA-256 完整性校验：仅在清单中提供了校验值时执行
                if (!model.sha256.isEmpty()) {
                    String actual = sha256(temp);
                    if (!model.sha256.equalsIgnoreCase(actual)) {
                        // 校验失败：删除临时文件，抛出异常，避免损坏数据残留
                        temp.delete();
                        throw new IllegalStateException("Model SHA-256 verification failed");
                    }
                }
                // 删除已存在的旧版本模型文件
                if (target.exists()) {
                    target.delete();
                }
                // 将校验通过的临时文件重命名为正式文件名，完成下载
                if (!temp.renameTo(target)) {
                    throw new IllegalStateException("Cannot finalize model file");
                }
                callback.onComplete(target);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 获取模型文件的本地存储目录。
     * 优先使用外部存储（getExternalFilesDir），当外部存储不可用时回退到内部存储（getFilesDir）。
     *
     * @param context Android 上下文
     * @return 模型存储目录的 File 对象
     */
    public File getModelDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        // 外部存储可能不可用（如 SD 卡被移除），此时回退到内部存储
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(base, MODEL_DIR);
    }

    /**
     * 获取指定模型的本地文件路径。
     *
     * @param context Android 上下文
     * @param model   模型信息
     * @return 模型文件的 File 对象（文件不一定存在）
     */
    public File getModelFile(Context context, AiModelInfo model) {
        return new File(getModelDir(context), model.fileName);
    }

    /**
     * 判断指定模型是否已下载到本地。
     *
     * <p>判断逻辑：</p>
     * <ul>
     *   <li>文件必须存在</li>
     *   <li>如果清单中指定了文件大小（sizeBytes > 0），则文件大小必须完全匹配</li>
     *   <li>如果清单中未指定文件大小（sizeBytes <= 0），则仅检查文件是否存在</li>
     * </ul>
     *
     * @param context Android 上下文
     * @param model   模型信息
     * @return true 表示模型文件已完整下载
     */
    public boolean isDownloaded(Context context, AiModelInfo model) {
        File file = getModelFile(context, model);
        return file.exists() && (model.sizeBytes <= 0 || file.length() == model.sizeBytes);
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
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        // 连接超时 10 秒，读取超时 30 秒（清单文件较小，无需过长超时）
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new JSONObject(builder.toString());
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
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        // 下载模型文件使用更长的超时：连接 15 秒，读取 300 秒（5分钟）
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(300000);
        long total = conn.getContentLengthLong();
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
                callback.onProgress(downloaded, total);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 计算文件的 SHA-256 哈希值。
     * 使用 1MB 缓冲区流式计算，避免将整个文件加载到内存。
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
