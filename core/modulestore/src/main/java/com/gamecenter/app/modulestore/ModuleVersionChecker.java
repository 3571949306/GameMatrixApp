package com.gamecenter.app.modulestore;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.models.ModuleVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 模块版本检查器。
 *
 * <p>负责对比内置版本与商店版本，判断是否需要更新：
 * <ul>
 *   <li>从本地 assets 或服务器获取最新模块清单</li>
 *   <li>比较内置模块版本号与商店模块版本号</li>
 *   <li>提供更新建议（是否加载外置版本）</li>
 * </ul>
 *
 * <p>版本比较策略（架构决策2）：
 * <ul>
 *   <li>主逻辑：版本号判断（明确、可控）</li>
 *   <li>兜底机制：ClassLoader 优先级（防止版本判断失效）</li>
 * </ul>
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public class ModuleVersionChecker {

    private static final String TAG = "ModuleVersionChecker";

    /** 模块清单服务器 URL（默认占位，通过 setModulesJsonUrl() 设置实际地址） */
    private static final String DEFAULT_MODULES_JSON_URL =
            "https://your-server.example.com/modules.json";

    /** 连接超时：10 秒 */
    private static final int CONNECT_TIMEOUT_MS = 10000;

    /** 读取超时：30 秒 */
    private static final int READ_TIMEOUT_MS = 30000;

    /** 上下文 */
    private final Context context;

    /** 模块清单 URL */
    private String modulesJsonUrl;

    /**
     * 构造函数。
     *
     * @param context Android Context
     */
    public ModuleVersionChecker(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.modulesJsonUrl = DEFAULT_MODULES_JSON_URL;
    }

    /**
     * 设置模块清单 URL。
     *
     * @param url 服务器 URL
     */
    public void setModulesJsonUrl(@NonNull String url) {
        if (url != null && !url.isEmpty()) {
            this.modulesJsonUrl = url;
        }
    }

    /**
     * 比较版本号。
     *
     * <p>比较逻辑：
     * <ul>
     *   <li>{@code store > builtIn} → 返回 1（商店版本更高，建议更新）</li>
     *   <li>{@code store == builtIn} → 返回 0（版本相同）</li>
     *   <li>{@code store < builtIn} → 返回 -1（内置版本更高，异常场景）</li>
     * </ul>
     *
     * @param builtIn 内置模块版本号
     * @param store   商店模块版本号
     * @return 比较结果（1 / 0 / -1）
     */
    public int compareVersions(int builtIn, int store) {
        if (store > builtIn) {
            return 1;
        } else if (store == builtIn) {
            return 0;
        } else {
            return -1;
        }
    }

    /**
     * 检查指定模块是否有可用更新。
     *
     * <p>从服务器获取最新模块清单，查找对应模块的版本信息，
     * 与内置版本号进行比较。
     *
     * @param moduleInfo 当前模块信息（包含内置版本号）
     * @return 更新后的模块信息（商店版本），无更新返回 null
     */
    @Nullable
    public ModuleInfo checkForUpdates(@NonNull ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            Log.e(TAG, "moduleInfo 为 null");
            return null;
        }

        String moduleId = moduleInfo.getModuleId();
        int builtInVersion = moduleInfo.getVersionCode();

        Log.d(TAG, "检查模块更新: " + moduleId + " (内置版本: " + builtInVersion + ")");

        // 从服务器获取最新模块清单
        JSONObject modulesJson = fetchModulesJson();
        if (modulesJson == null) {
            Log.w(TAG, "无法获取模块清单");
            return null;
        }

        // 查找对应模块
        JSONObject storeModule = findModuleInJson(modulesJson, moduleId);
        if (storeModule == null) {
            Log.d(TAG, "商店中未找到模块: " + moduleId);
            return null;
        }

        // 解析商店版本号
        int storeVersion = storeModule.optInt("versionCode", 0);
        if (storeVersion <= 0) {
            Log.w(TAG, "商店模块版本号无效: " + moduleId);
            return null;
        }

        // 比较版本
        int comparison = compareVersions(builtInVersion, storeVersion);
        if (comparison >= 0) {
            Log.d(TAG, "模块已是最新版本: " + moduleId + " (内置: " + builtInVersion
                    + ", 商店: " + storeVersion + ")");
            return null;
        }

        // 商店版本更高，构造更新后的 ModuleInfo
        Log.i(TAG, "发现更新: " + moduleId + " (内置: " + builtInVersion
                + " -> 商店: " + storeVersion + ")");

        ModuleInfo updateInfo = parseModuleInfoFromJson(storeModule);
        return updateInfo;
    }

    /**
     * 判断是否应该加载外置版本。
     *
     * <p>判断逻辑（架构决策2组合方案）：
     * <ol>
     *   <li>主逻辑：版本号比较（如果外置版本更高，则加载外置版本）</li>
     *   <li>兜底机制：如果内置版本加载失败，自动回退到外置版本</li>
     * </ol>
     *
     * @param builtInVersion  内置模块版本号
     * @param externalModule  外置（商店下载的）模块信息
     * @return 应该加载外置版本返回 true，否则返回 false
     */
    public boolean shouldLoadExternal(int builtInVersion,
                                     @NonNull ModuleInfo externalModule) {
        if (externalModule == null) {
            return false;
        }

        // 主逻辑：版本号判断
        int externalVersion = externalModule.getVersionCode();
        int comparison = compareVersions(builtInVersion, externalVersion);

        if (comparison > 0) {
            // 商店版本更高
            Log.d(TAG, "shouldLoadExternal: true (内置=" + builtInVersion
                    + ", 外置=" + externalVersion + ")");
            return true;
        }

        // 内置版本相同或更高
        Log.d(TAG, "shouldLoadExternal: false (内置=" + builtInVersion
                + ", 外置=" + externalVersion + ")");
        return false;
    }

    /**
     * 从服务器获取最新模块清单（同步方法）。
     *
     * <p>获取顺序：
     * <ol>
     *   <li>从本地 assets/modules.json 读取（缓存）</li>
     *   <li>从本地 files/modules.json 读取（上次下载的）</li>
     *   <li>从服务器 HTTP GET 读取</li>
     * </ol>
     *
     * @return 模块清单 JSON，获取失败返回 null
     */
    @Nullable
    private JSONObject fetchModulesJson() {
        // 1. 尝试从本地 files 目录读取（上次下载的清单）
        JSONObject json = readLocalModulesJson();
        if (json != null) {
            Log.d(TAG, "使用本地 modules.json");
            return json;
        }

        // 2. 尝试从 assets 读取
        json = readAssetModulesJson();
        if (json != null) {
            Log.d(TAG, "使用 assets/modules.json");
            return json;
        }

        // 3. 从服务器获取
        json = fetchRemoteModulesJson();
        if (json != null) {
            Log.d(TAG, "使用远程 modules.json");
            // 保存到本地以供下次使用
            saveLocalModulesJson(json);
            return json;
        }

        Log.w(TAG, "所有获取 modules.json 的方式均失败");
        return null;
    }

    /**
     * 从本地 files 目录读取 modules.json。
     */
    @Nullable
    private JSONObject readLocalModulesJson() {
        if (context == null) return null;

        try {
            File jsonFile = new File(context.getFilesDir(), "modules.json");
            if (!jsonFile.exists()) return null;

            FileInputStream fis = new FileInputStream(jsonFile);
            Scanner scanner = new Scanner(fis, "UTF-8");
            String jsonStr = scanner.useDelimiter("\\A").next();
            scanner.close();
            fis.close();

            return new JSONObject(jsonStr);
        } catch (Exception e) {
            Log.d(TAG, "本地 modules.json 读取失败", e);
            return null;
        }
    }

    /**
     * 从 assets 目录读取 modules.json。
     */
    @Nullable
    private JSONObject readAssetModulesJson() {
        if (context == null) return null;

        try {
            InputStream is = context.getAssets().open("modules.json");
            Scanner scanner = new Scanner(is, "UTF-8");
            String jsonStr = scanner.useDelimiter("\\A").next();
            scanner.close();
            is.close();

            return new JSONObject(jsonStr);
        } catch (Exception e) {
            Log.d(TAG, "assets/modules.json 读取失败", e);
            return null;
        }
    }

    /**
     * 从远程服务器获取 modules.json。
     */
    @Nullable
    private JSONObject fetchRemoteModulesJson() {
        try {
            URL url = new URL(modulesJsonUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "服务器返回: " + responseCode);
                return null;
            }

            InputStream is = conn.getInputStream();
            Scanner scanner = new Scanner(is, "UTF-8");
            String jsonStr = scanner.useDelimiter("\\A").next();
            scanner.close();
            is.close();
            conn.disconnect();

            return new JSONObject(jsonStr);
        } catch (Exception e) {
            Log.w(TAG, "远程 modules.json 获取失败: " + modulesJsonUrl, e);
            return null;
        }
    }

    /**
     * 保存 modules.json 到本地。
     */
    private void saveLocalModulesJson(@NonNull JSONObject json) {
        if (context == null) return;

        try {
            File jsonFile = new File(context.getFilesDir(), "modules.json");
            java.io.FileWriter writer = new java.io.FileWriter(jsonFile, false);
            writer.write(json.toString(2));
            writer.close();
            Log.d(TAG, "modules.json 已保存到本地");
        } catch (Exception e) {
            Log.w(TAG, "保存 modules.json 失败", e);
        }
    }

    /**
     * 从模块清单 JSON 中查找指定模块。
     */
    @Nullable
    private JSONObject findModuleInJson(@NonNull JSONObject root, @NonNull String moduleId) {
        JSONArray modules = root.optJSONArray("modules");
        if (modules == null) return null;

        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.optJSONObject(i);
            if (module != null && moduleId.equals(module.optString("id", ""))) {
                return module;
            }
        }

        return null;
    }

    /**
     * 从 JSON 对象解析 ModuleInfo。
     */
    @NonNull
    private ModuleInfo parseModuleInfoFromJson(@NonNull JSONObject json) {
        ModuleInfo info = new ModuleInfo();
        info.setModuleId(json.optString("id", ""));
        info.setModuleName(json.optString("name", ""));
        info.setVersionName(json.optString("versionName", "1.0.0"));
        info.setVersionCode(json.optInt("versionCode", 1));
        info.setType(json.optString("type", "game"));
        info.setBuiltIn(json.optBoolean("builtIn", false));
        info.setStoreCategory(json.optString("storeCategory", "game"));
        info.setDownloadUrl(json.optString("downloadUrl", ""));
        info.setFileSize(json.optLong("fileSize", 0L));
        info.setSha256(json.optString("sha256", ""));
        info.setDescription(json.optString("description", ""));
        info.setMinFrameworkVersion(json.optInt("minAppVersion", 1));
        return info;
    }
}
