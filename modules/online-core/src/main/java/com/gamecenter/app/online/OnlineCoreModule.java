package com.gamecenter.app.online;

// [DEAD-ONLINE] 联机功能已通过 OnlinePlayGate 统一下线。
// 本类属于完全死模块：运行时入口全部被闸门挡住，全仓库零外部调用。
// 清理时连同所属 Gradle 模块整体删除。

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.models.ModuleVersion;

/**
 * 联机核心模块入口。
 * 
 * 实现 IModule 接口，作为 online-core 模块的入口点。
 * 初始化并管理联机核心组件：OnlineRoomManager、GameSocketServer/Client、RelayHttpClient、LANManager。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class OnlineCoreModule implements IModule {
    
    private static final String TAG = "OnlineCoreModule";
    
    /** 模块 ID */
    private static final String MODULE_ID = "online_core";
    
    /** 模块名称 */
    private static final String MODULE_NAME = "联机核心";
    
    /** 模块版本名 */
    private static final String VERSION_NAME = "1.0.0";
    
    /** 模块版本号 */
    private static final int VERSION_CODE = 100;
    
    /** 模块类型 */
    private static final String MODULE_TYPE = "core";
    
    /** 上下文 */
    private Context context;
    
    /** 联机房间管理器（单例） */
    private OnlineRoomManager roomManager;
    
    /** WebSocket 服务器（单机模式） */
    private GameSocketServer socketServer;
    
    /** WebSocket 客户端（联机模式） */
    private GameSocketClient socketClient;
    
    /** Relay HTTP 客户端（连接 VPS 中继服务器） */
    private RelayHttpClient relayClient;
    
    /** 局域网管理器 */
    private LANManager lanManager;
    
    /** 模块是否已启动 */
    private boolean isRunning;
    
    /**
     * 默认构造函数。
     */
    public OnlineCoreModule() {
        this.isRunning = false;
    }
    
    @Override
    @NonNull
    public String getModuleId() {
        return MODULE_ID;
    }
    
    @Override
    @NonNull
    public String getVersionName() {
        return VERSION_NAME;
    }
    
    @Override
    public int getVersionCode() {
        return VERSION_CODE;
    }
    
    @Override
    public void onLoad(@NonNull Context context) {
        Log.d(TAG, "联机核心模块加载: " + MODULE_ID);
        this.context = context.getApplicationContext();
        
        // 初始化联机核心组件（懒加载）
        Log.i(TAG, "联机核心模块加载完成");
    }
    
    @Override
    public void onUnload() {
        Log.d(TAG, "联机核心模块卸载: " + MODULE_ID);
        
        // 断开所有连接
        disconnectAll();
        
        // 释放资源
        roomManager = null;
        socketServer = null;
        socketClient = null;
        relayClient = null;
        lanManager = null;
        
        this.context = null;
        
        Log.i(TAG, "联机核心模块卸载完成");
    }
    
    @Override
    public void onUpdate(@NonNull ModuleVersion newVersion) {
        Log.d(TAG, "联机核心模块更新: " + newVersion.getVersionName());
        // 数据迁移等（简化实现）
    }
    
    @Override
    public void onStart(@NonNull Context context) {
        Log.d(TAG, "联机核心模块启动");
        this.isRunning = true;
    }
    
    @Override
    public void onStop() {
        Log.d(TAG, "联机核心模块停止");
        this.isRunning = false;
    }
    
    @Override
    @NonNull
    public String getModuleName() {
        return MODULE_NAME;
    }
    
    @Override
    @NonNull
    public String getDescription() {
        return "联机核心模块，提供房间管理、WebSocket 通信、Relay 中继、局域网联机等功能";
    }
    
    @Override
    @NonNull
    public String getModuleType() {
        return MODULE_TYPE;
    }
    
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    
    // ========== 联机核心功能接口 ==========
    
    /**
     * 获取联机房间管理器（懒加载）。
     * 
     * @return OnlineRoomManager 实例
     */
    @NonNull
    public synchronized OnlineRoomManager getRoomManager() {
        if (roomManager == null) {
            roomManager = new OnlineRoomManager(context);
            Log.d(TAG, "OnlineRoomManager 已初始化");
        }
        return roomManager;
    }
    
    /**
     * 获取 WebSocket 服务器（懒加载，单机模式）。
     * 
     * @return GameSocketServer 实例
     */
    @NonNull
    public synchronized GameSocketServer getSocketServer() {
        if (socketServer == null) {
            socketServer = new GameSocketServer(context);
            Log.d(TAG, "GameSocketServer 已初始化");
        }
        return socketServer;
    }
    
    /**
     * 获取 WebSocket 客户端（懒加载，联机模式）。
     * 
     * @return GameSocketClient 实例
     */
    @NonNull
    public synchronized GameSocketClient getSocketClient() {
        if (socketClient == null) {
            socketClient = new GameSocketClient(context);
            Log.d(TAG, "GameSocketClient 已初始化");
        }
        return socketClient;
    }
    
    /**
     * 获取 Relay HTTP 客户端（懒加载，连接 VPS 中继）。
     * 
     * @return RelayHttpClient 实例
     */
    @NonNull
    public synchronized RelayHttpClient getRelayClient() {
        if (relayClient == null) {
            relayClient = new RelayHttpClient(context);
            Log.d(TAG, "RelayHttpClient 已初始化");
        }
        return relayClient;
    }
    
    /**
     * 获取局域网管理器（懒加载）。
     * 
     * @return LANManager 实例
     */
    @NonNull
    public synchronized LANManager getLanManager() {
        if (lanManager == null) {
            lanManager = new LANManager(context);
            Log.d(TAG, "LANManager 已初始化");
        }
        return lanManager;
    }
    
    /**
     * 断开所有连接。
     */
    private void disconnectAll() {
        if (socketServer != null) {
            socketServer.stop();
        }
        
        if (socketClient != null) {
            socketClient.disconnect();
        }
        
        if (relayClient != null) {
            relayClient.disconnect();
        }
        
        if (lanManager != null) {
            lanManager.stopDiscovery();
            lanManager.stopAdvertising();
        }
        
        if (roomManager != null) {
            roomManager.leaveRoom();
        }
        
        Log.d(TAG, "所有联机连接已断开");
    }
    
    /**
     * 检查联机核心是否已初始化。
     * 
     * @return 已初始化返回 true，否则返回 false
     */
    public boolean isInitialized() {
        return context != null;
    }
    
    /**
     * 释放所有资源（应用退出时调用）。
     */
    public void release() {
        Log.d(TAG, "释放联机核心所有资源");
        onUnload();
    }
}
