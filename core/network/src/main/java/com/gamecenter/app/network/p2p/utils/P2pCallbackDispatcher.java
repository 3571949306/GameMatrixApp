package com.gamecenter.app.network.p2p.utils;

import android.os.Handler;
import android.os.Looper;

/**
 * P2P 统一事件回调调度器。
 * <p>
 * 保证在真实 Android 运行时（真机/模拟器）中将事件准确切回 UI 主线程 (MainLooper)；
 * 同时在 JVM 单元测试或无 Looper 轮询的测试环境中自动回退至直接同步执行，避免回调丢失。
 */
public final class P2pCallbackDispatcher {

    private static volatile boolean directExecutionForTesting = false;

    private P2pCallbackDispatcher() {
    }

    /**
     * 设置单元测试直接执行模式。
     */
    public static void setDirectExecutionForTesting(boolean direct) {
        directExecutionForTesting = direct;
    }

    /**
     * 调度执行回调任务。
     */
    public static void dispatch(Runnable runnable) {
        if (runnable == null) return;

        if (directExecutionForTesting) {
            runnable.run();
            return;
        }

        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null) {
                if (Looper.myLooper() == mainLooper) {
                    runnable.run();
                    return;
                }
                Handler handler = new Handler(mainLooper);
                if (handler.post(runnable)) {
                    return; // 成功投递到 Android 主线程队列
                }
            }
        } catch (Throwable ignored) {
        }

        // 环境无 MainLooper 或单元测试 Mock 返回 false 时，直接执行
        runnable.run();
    }
}
