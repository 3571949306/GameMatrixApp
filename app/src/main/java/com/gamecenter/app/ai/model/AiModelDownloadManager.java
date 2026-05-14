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

public final class AiModelDownloadManager {
    private static final String MODEL_DIR = "ai_models";
    private static final String MANIFEST_PATH = "/ai-models/models.json";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void fetchModels(Callback<List<AiModelInfo>> callback) {
        executor.execute(() -> {
            try {
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

    public void download(Context context, AiModelInfo model, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                if (!model.enabled || model.downloadUrl.isEmpty()) {
                    throw new IllegalStateException(model.note.isEmpty()
                            ? "Model package is not enabled on VPS"
                            : model.note);
                }
                File dir = getModelDir(context);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Cannot create model directory");
                }
                File target = new File(dir, model.fileName);
                File temp = new File(dir, model.fileName + ".download");
                downloadFile(model.downloadUrl, temp, model.sizeBytes, callback);
                if (!model.sha256.isEmpty()) {
                    String actual = sha256(temp);
                    if (!model.sha256.equalsIgnoreCase(actual)) {
                        temp.delete();
                        throw new IllegalStateException("Model SHA-256 verification failed");
                    }
                }
                if (target.exists()) {
                    target.delete();
                }
                if (!temp.renameTo(target)) {
                    throw new IllegalStateException("Cannot finalize model file");
                }
                callback.onComplete(target);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public File getModelDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(base, MODEL_DIR);
    }

    public File getModelFile(Context context, AiModelInfo model) {
        return new File(getModelDir(context), model.fileName);
    }

    public boolean isDownloaded(Context context, AiModelInfo model) {
        File file = getModelFile(context, model);
        return file.exists() && (model.sizeBytes <= 0 || file.length() == model.sizeBytes);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private JSONObject fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
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

    private void downloadFile(String urlStr, File target, long expectedSize, DownloadCallback callback) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(300000);
        long total = conn.getContentLengthLong();
        if (total <= 0) {
            total = expectedSize;
        }
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
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

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest()) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total);
        void onComplete(File file);
        void onError(Exception error);
    }
}
