package com.gamecenter.app.moduleloader;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 模块资源加载器（增强版）。
 * 
 * 负责加载模块 APK 中的资源（布局、图片、字符串等）。
 * 使用 AssetManager.addAssetPath() 加载插件资源。
 * 
 * @author Software Engineer (Alex)
 * @version 2.0
 * @since 2026-05-26
 */
public class ModuleResourceLoader {
    
    private static final String TAG = "ModuleResourceLoader";
    
    /** 资源缓存（模块 ID -> Resources） */
    private final Map<String, Resources> resourcesCache;
    
    /** Context 引用 */
    private final Context context;
    
    /** 主包名（框架 APK 的包名） */
    private String mainPackageName;
    
    /**
     * 构造函数。
     * 
     * @param context Android Context
     */
    public ModuleResourceLoader(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.resourcesCache = new HashMap<>();
        this.mainPackageName = this.context != null ? 
                this.context.getPackageName() : "com.gamecenter.app";
    }
    
    /**
     * 加载模块 APK 的资源。
     * 
     * @param apkFile 模块 APK 文件
     * @return 模块资源的 Resources 对象，失败返回 null
     */
    @Nullable
    public Resources loadResources(@NonNull File apkFile) {
        if (context == null) {
            Log.e(TAG, "Context 为 null");
            return null;
        }
        
        if (apkFile == null || !apkFile.exists()) {
            Log.e(TAG, "APK 文件不存在: " + apkFile);
            return null;
        }
        
        // 检查缓存
        String cacheKey = apkFile.getAbsolutePath();
        Resources cached = resourcesCache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "使用缓存的 Resources: " + apkFile.getName());
            return cached;
        }
        
        try {
            // 创建 AssetManager
            AssetManager assetManager = createAssetManager(apkFile.getAbsolutePath());
            if (assetManager == null) {
                return null;
            }
            
            // 获取主包的 Resources 作为配置模板
            Resources mainResources = context.getResources();
            if (mainResources == null) {
                Log.e(TAG, "主 Resources 为 null");
                return null;
            }
            
            // 创建新的 Resources (使用复制的 Configuration 避免污染宿主全局配置)
            android.content.res.Configuration config = new android.content.res.Configuration(mainResources.getConfiguration());
            Resources moduleResources = new Resources(
                    assetManager,
                    mainResources.getDisplayMetrics(),
                    config
            );
            
            // 缓存
            resourcesCache.put(cacheKey, moduleResources);
            
            Log.d(TAG, "资源加载成功: " + apkFile.getName());
            return moduleResources;
            
        } catch (Exception e) {
            Log.e(TAG, "加载资源失败: " + apkFile.getName(), e);
            return null;
        }
    }
    
    /**
     * 创建 AssetManager 并添加 APK 路径。
     * 
     * @param apkPath APK 文件路径
     * @return AssetManager 实例，失败返回 null
     */
    @Nullable
    private AssetManager createAssetManager(@NonNull String apkPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            
            // 调用 addAssetPath(String path)
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            int result = (int) addAssetPath.invoke(assetManager, apkPath);
            
            if (result == 0) {
                Log.e(TAG, "addAssetPath 失败: " + apkPath);
                return null;
            }
            
            Log.d(TAG, "AssetManager 创建成功，已添加路径: " + apkPath);
            return assetManager;
            
        } catch (Exception e) {
            Log.e(TAG, "创建 AssetManager 失败", e);
            return null;
        }
    }
    
    /**
     * 从模块 APK 中加载 Drawable。
     * 
     * @param apkFile 模块 APK 文件
     * @param resId 资源 ID
     * @return Drawable 对象，失败返回 null
     */
    @Nullable
    public android.graphics.drawable.Drawable loadDrawable(@NonNull File apkFile, int resId) {
        Resources resources = loadResources(apkFile);
        if (resources == null) {
            return null;
        }
        
        try {
            return resources.getDrawable(resId, context.getTheme());
        } catch (Exception e) {
            Log.e(TAG, "加载 Drawable 失败: resId=0x" + Integer.toHexString(resId), e);
            return null;
        }
    }
    
    /**
     * 从模块 APK 中加载布局（简化实现，使用模块资源 Context）。
     * 
     * @param apkFile 模块 APK 文件
     * @param layoutId 布局 ID
     * @return View 对象，失败返回 null
     */
    @Nullable
    public android.view.View loadLayout(@NonNull File apkFile, int layoutId) {
        // 调用新的重载方法，parent 传 null
        return loadLayout(apkFile, layoutId, null);
    }
    
    /**
     * 从模块 APK 中加载字符串。
     * 
     * @param apkFile 模块 APK 文件
     * @param resId 字符串资源 ID
     * @return 字符串，失败返回 null
     */
    @Nullable
    public String loadString(@NonNull File apkFile, int resId) {
        Resources resources = loadResources(apkFile);
        if (resources == null) {
            return null;
        }
        
        try {
            return resources.getString(resId);
        } catch (Exception e) {
            Log.e(TAG, "加载字符串失败: resId=0x" + Integer.toHexString(resId), e);
            return null;
        }
    }
    
    /**
     * 获取模块 APK 的包名（简化实现）。
     * 
     * @param apkFile 模块 APK 文件
     * @return 包名，失败返回 null
     */
    @Nullable
    public String getModulePackageName(@NonNull File apkFile) {
        // 简化实现：实际应从 AndroidManifest.xml 解析
        // 约定：模块包名为 com.gamecenter.module.{moduleId}
        Log.w(TAG, "getModulePackageName 为简化实现，需完善");
        return null;
    }
    
    /**
     * 清除指定模块的 Resources 缓存。
     * 
     * @param apkPath APK 文件路径
     */
    public void clearCache(@NonNull String apkPath) {
        resourcesCache.remove(apkPath);
        Log.d(TAG, "已清除资源缓存: " + apkPath);
    }
    
    /**
     * 清除所有 Resources 缓存。
     */
    public void clearAllCache() {
        resourcesCache.clear();
        Log.d(TAG, "已清除所有资源缓存");
    }
    
    /**
     * 获取当前缓存的模块数量。
     * 
     * @return 缓存数量
     */
    public int getCachedModuleCount() {
        return resourcesCache.size();
    }
    
    /**
     * 创建模块资源的 Context（用于正确加载模块布局）。
     * 
     * @param apkFile 模块 APK 文件
     * @param baseContext 基础 Context
     * @return 包含模块资源的 Context，失败返回 baseContext
     */
    @NonNull
    public Context createModuleContext(@NonNull File apkFile, @NonNull Context baseContext) {
        Resources moduleResources = loadResources(apkFile);
        if (moduleResources == null) {
            Log.w(TAG, "无法加载模块资源，使用基础 Context");
            return baseContext;
        }
        
        // 创建 ContextWrapper，重写 getResources() 返回模块资源
        return new ContextWrapper(baseContext) {
            @Override
            public Resources getResources() {
                return moduleResources;
            }
            
            @Override
            public AssetManager getAssets() {
                return moduleResources.getAssets();
            }
            
            @Override
            public Context getApplicationContext() {
                return baseContext.getApplicationContext();
            }
        };
    }
    
    /**
     * 从模块 APK 中加载布局（使用模块资源 Context）。
     * 
     * @param apkFile 模块 APK 文件
     * @param layoutId 布局 ID
     * @param parent 父 View（可选，用于正确解析 LayoutParams）
     * @return View 对象，失败返回 null
     */
    @Nullable
    public android.view.View loadLayout(@NonNull File apkFile, int layoutId, 
                                        @Nullable android.view.ViewGroup parent) {
        Context moduleContext = createModuleContext(apkFile, context);
        
        try {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(moduleContext);
            return inflater.inflate(layoutId, parent, false);
        } catch (Exception e) {
            Log.e(TAG, "加载布局失败: layoutId=0x" + Integer.toHexString(layoutId), e);
            return null;
        }
    }
}
