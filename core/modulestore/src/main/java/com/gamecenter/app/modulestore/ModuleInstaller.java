package com.gamecenter.app.modulestore;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader;
import com.gamecenter.app.interfaces.IModuleStore.ModuleInstallException;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.moduleloader.ModuleLoaderV2;
import com.gamecenter.app.moduleloader.ModuleVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * 模块安装器。
 * 
 * 负责模块 APK 的安装：
 * - 校验 APK（调用 ModuleVerifier）
 * - 复制到私有目录
 * - 加载模块（调用 ModuleLoaderV2）
 * - 注册到 GameRegistry（如果是游戏模块）
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleInstaller {
    
    private static final String TAG = "ModuleInstaller";
    
    /** 单例实例 */
    private static volatile ModuleInstaller instance;
    
    /** 上下文 */
    private final Context context;
    
    /** 模块加载器 */
    private final ModuleLoaderV2 moduleLoader;
    
    /** 缓冲区大小：8KB */
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * 获取单例实例（双重检查锁定）。
     * 
     * @param context Android Context
     * @return ModuleInstaller 单例
     */
    @NonNull
    public static ModuleInstaller getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ModuleInstaller.class) {
                if (instance == null) {
                    instance = new ModuleInstaller(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数。
     */
    private ModuleInstaller(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.moduleLoader = ModuleLoaderV2.getInstance(this.context);
    }
    
    /**
     * 安装模块（从 APK 文件）。
     * 
     * @param apkFile 模块 APK 文件
     * @param moduleInfo 模块信息
     * @return 安装成功返回 true，否则返回 false
     * @throws ModuleInstallException 安装失败时抛出
     */
    public boolean installModule(@NonNull File apkFile, 
                                @NonNull ModuleInfo moduleInfo) 
            throws ModuleInstallException {
        if (context == null) {
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "Context 为 null"
            );
        }
        
        if (apkFile == null || !apkFile.exists() || !apkFile.isFile()) {
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "APK 文件不存在: " + apkFile
            );
        }
        
        if (moduleInfo == null) {
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "模块信息为 null"
            );
        }
        
        String moduleId = moduleInfo.getModuleId();
        Log.d(TAG, "开始安装模块: " + moduleId);
        
        try {
            // 1. 校验 APK
            ModuleVerifier.VerifyResult verifyResult = ModuleVerifier.verify(
                    context,
                    apkFile,
                    moduleInfo.getSha256(),
                    moduleInfo.getFileSize(),
                    moduleInfo.getMinFrameworkVersion(),
                    getFrameworkVersionCode()
            );
            
            if (!verifyResult.isSuccess()) {
                throw new ModuleInstallException(
                        ModuleInstallException.ERROR_VERIFICATION_FAILED,
                        "模块校验失败: " + verifyResult.getErrorMessage()
                );
            }
            
            Log.d(TAG, "APK 校验通过: " + moduleId);
            
            // 2. 复制到私有目录
            File destFile = copyApkToPrivateDir(apkFile, moduleInfo);
            if (destFile == null) {
                throw new ModuleInstallException(
                        ModuleInstallException.ERROR_INSTALLATION_FAILED,
                        "复制 APK 到私有目录失败"
                );
            }
            
            Log.d(TAG, "APK 已复制: " + moduleId + " -> " + destFile.getAbsolutePath());
            
            // 3. 加载模块
            IModule module = moduleLoader.loadModuleFromFile(
                    destFile.getAbsolutePath(), 
                    moduleInfo
            );
            
            if (module == null) {
                throw new ModuleInstallException(
                        ModuleInstallException.ERROR_INSTALLATION_FAILED,
                        "模块加载失败"
                );
            }
            
            Log.d(TAG, "模块已加载: " + moduleId);
            
            // 4. 注册到 GameRegistry（如果是游戏模块）
            if ("game".equals(moduleInfo.getType())) {
                registerGameModule(moduleInfo, module);
            }
            
            Log.i(TAG, "模块安装成功: " + moduleId + " v" + moduleInfo.getVersionName());
            return true;
            
        } catch (ModuleInstallException e) {
            // 回滚安装（删除已复制的文件）
            rollbackInstall(moduleInfo);
            throw e;
            
        } catch (Exception e) {
            Log.e(TAG, "模块安装异常: " + moduleId, e);
            rollbackInstall(moduleInfo);
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "模块安装异常: " + e.getMessage(),
                    e
            );
        }
    }
    
    /**
     * 安装内置模块（内置游戏）。
     * 
     * @param moduleInfo 模块信息
     * @return 安装成功返回 true，否则返回 false
     * @throws ModuleInstallException 安装失败时抛出
     */
    public boolean installBuiltInModule(@NonNull ModuleInfo moduleInfo) 
            throws ModuleInstallException {
        if (moduleInfo == null) {
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "模块信息为 null"
            );
        }
        
        if (!moduleInfo.isBuiltIn()) {
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "非内置模块，请使用 installModule()"
            );
        }
        
        String moduleId = moduleInfo.getModuleId();
        Log.d(TAG, "开始安装内置模块: " + moduleId);
        
        try {
            // 内置模块无需复制 APK，直接加载
            IModule module = moduleLoader.loadModule(moduleInfo);
            
            if (module == null) {
                throw new ModuleInstallException(
                        ModuleInstallException.ERROR_INSTALLATION_FAILED,
                        "内置模块加载失败"
                );
            }
            
            Log.i(TAG, "内置模块安装成功: " + moduleId);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "内置模块安装异常: " + moduleId, e);
            throw new ModuleInstallException(
                    ModuleInstallException.ERROR_INSTALLATION_FAILED,
                    "内置模块安装异常: " + e.getMessage(),
                    e
            );
        }
    }
    
    /**
     * 回滚安装（删除已复制的 APK 文件）。
     * 
     * @param moduleInfo 模块信息
     */
    public void rollbackInstall(@NonNull ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            return;
        }
        
        String moduleId = moduleInfo.getModuleId();
        File modulesDir = new File(context.getFilesDir(), "modules");
        if (!modulesDir.exists()) {
            return;
        }
        
        // 删除匹配的 APK 文件
        File[] files = modulesDir.listFiles((dir, name) -> 
                name.startsWith(moduleId) && name.endsWith(".apk"));
        
        if (files != null) {
            for (File f : files) {
                if (f.delete()) {
                    Log.d(TAG, "回滚：已删除文件 " + f.getName());
                } else {
                    Log.w(TAG, "回滚：删除文件失败 " + f.getName());
                }
            }
        }
        
        Log.d(TAG, "安装回滚完成: " + moduleId);
    }
    
    // ========== 私有辅助方法 ==========
    
    /**
     * 复制 APK 文件到私有目录。
     * 
     * @param sourceFile 源 APK 文件
     * @param moduleInfo 模块信息
     * @return 目标文件，失败返回 null
     */
    @Nullable
    private File copyApkToPrivateDir(@NonNull File sourceFile, 
                                      @NonNull ModuleInfo moduleInfo) {
        if (context == null) {
            return null;
        }
        
        File modulesDir = new File(context.getFilesDir(), "modules");
        if (!modulesDir.exists() && !modulesDir.mkdirs()) {
            Log.e(TAG, "创建 modules 目录失败");
            return null;
        }
        
        // 目标文件名：{moduleId}_v{versionCode}.apk
        String fileName = moduleInfo.getModuleId() + "_v" 
                + moduleInfo.getVersionCode() + ".apk";
        File destFile = new File(modulesDir, fileName);
        
        // 如果目标文件已存在，先删除
        if (destFile.exists() && !destFile.delete()) {
            Log.w(TAG, "删除已存在的目标文件失败: " + fileName);
        }
        
        // 复制文件
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileChannel inChannel = fis.getChannel();
             FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel outChannel = fos.getChannel()) {
            
            inChannel.transferTo(0, inChannel.size(), outChannel);
            
            Log.d(TAG, "文件复制成功: " + sourceFile.getName() + " -> " + fileName);
            return destFile;
            
        } catch (IOException e) {
            Log.e(TAG, "文件复制失败", e);
            return null;
        }
    }
    
    /**
     * 注册游戏模块到 GameRegistry（简化实现）。
     * 
     * @param moduleInfo 模块信息
     * @param module 模块实例
     */
    private void registerGameModule(@NonNull ModuleInfo moduleInfo, 
                                   @NonNull IModule module) {
        // 简化实现：实际应调用 GameRegistry.registerModule()
        Log.d(TAG, "注册游戏模块: " + moduleInfo.getModuleId());
    }
    
    /**
     * 获取框架版本号（简化实现）。
     * 
     * @return 版本号
     */
    private int getFrameworkVersionCode() {
        // 实际应从 BuildConfig 读取
        return 343; // versionCode=343 (v1.4.0)
    }
}
