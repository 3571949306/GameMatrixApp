package com.gamecenter.app.interfaces;

import android.content.Context;
import androidx.annotation.NonNull;
import com.gamecenter.app.models.ModuleVersion;

/**
 * 模块接口。
 * 
 * 所有动态加载的模块必须实现此接口。
 * 定义模块的生命周期：加载、卸载、更新。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public interface IModule {
    
    /**
     * 获取模块 ID。
     * 
     * @return 模块唯一标识符
     */
    @NonNull
    String getModuleId();
    
    /**
     * 获取模块版本名称。
     * 
     * @return 版本名称（如 "1.1.0"）
     */
    @NonNull
    String getVersionName();
    
    /**
     * 获取模块版本号。
     * 
     * @return 版本号（用于版本比较）
     */
    int getVersionCode();
    
    /**
     * 模块加载时调用。
     * 
     * 在此方法中执行模块初始化操作：
     * - 初始化资源
     * - 注册服务
     * - 设置监听器
     * 
     * @param context Android Context
     */
    void onLoad(@NonNull Context context);
    
    /**
     * 模块卸载时调用。
     * 
     * 在此方法中执行模块清理操作：
     * - 释放资源
     * - 取消注册服务
     * - 断开连接
     */
    void onUnload();
    
    /**
     * 模块更新时调用。
     * 
     * 在新版本加载前调用，允许模块执行数据迁移等操作。
     * 
     * @param newVersion 新版本信息
     */
    void onUpdate(@NonNull ModuleVersion newVersion);
    
    /**
     * 模块启动（显示 UI）时调用。
     * 
     * @param context Android Context
     */
    void onStart(@NonNull Context context);
    
    /**
     * 模块停止（UI 隐藏）时调用。
     */
    void onStop();
    
    /**
     * 获取模块名称。
     * 
     * @return 模块显示名称
     */
    @NonNull
    String getModuleName();
    
    /**
     * 获取模块描述。
     * 
     * @return 模块描述信息
     */
    @NonNull
    String getDescription();
    
    /**
     * 获取模块类型。
     * 
     * @return 模块类型（game、tool、nav 等）
     */
    @NonNull
    String getModuleType();
    
    /**
     * 检查模块是否正在运行。
     * 
     * @return 运行中返回 true，否则返回 false
     */
    boolean isRunning();
}
