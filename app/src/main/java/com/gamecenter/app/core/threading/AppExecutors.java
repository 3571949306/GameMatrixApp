package com.gamecenter.app.core.threading;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用级统一线程管理器
 * <p>
 * 集中管理所有线程池，避免各模块自行创建导致线程爆炸。
 * 采用单例模式，全局唯一。
 * </p>
 *
 * <h3>线程池规划（共 8-10 个常驻线程）：</h3>
 * <ul>
 *   <li><b>主线程</b>：UI 渲染、生命周期回调</li>
 *   <li><b>IO 线程池</b>（4个）：网络请求、文件读写、数据库操作</li>
 *   <li><b>计算线程池</b>（2个）：AI 规则引擎、数据处理</li>
 *   <li><b>AI 推理线程</b>（1个）：本地 LLM 推理（Gemma 等大模型）</li>
 *   <li><b>后台线程</b>（1个）：更新检查、统计上报等低优先级任务</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * // 执行 IO 任务
 * AppExecutors.io().execute(() -> { ... });
 *
 * // 执行计算任务
 * AppExecutors.compute().execute(() -> { ... });
 *
 * // 切换到主线程
 * AppExecutors.main().post(() -> { ... });
 *
 * // 执行 AI 推理
 * AppExecutors.ai().execute(() -> { ... });
 * </pre>
 */
public final class AppExecutors {

    private static final String TAG = "AppExecutors";

    /** 主线程 Handler */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * IO 线程池（4个线程）
     * <p>
     * 用途：网络请求、文件读写、数据库操作
     * 特点：IO 密集型，大部分时间在等待，可以多开
     * </p>
     */
    private static final ExecutorService IO = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GC-IO-" + count.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    });

    /**
     * 计算线程池（2个线程）
     * <p>
     * 用途：AI 规则引擎、数据处理、JSON 解析
     * 特点：CPU 密集型，限制并发避免上下文切换
     * </p>
     */
    private static final ExecutorService COMPUTE = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GC-Compute-" + count.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    });

    /**
     * AI 推理线程（1个线程）
     * <p>
     * 用途：本地 LLM 推理（Gemma 等大模型）
     * 特点：单线程，避免多模型同时推理导致 OOM
     * </p>
     */
    private static final ExecutorService AI = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GC-AI-Inference");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // 低优先级，不阻塞 UI
        return t;
    });

    /**
     * 后台线程（1个线程）
     * <p>
     * 用途：更新检查、统计上报、日志写入
     * 特点：低优先级，不影响用户体验
     * </p>
     */
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GC-Background");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private AppExecutors() {
        // 不可实例化
    }

    /**
     * 获取 IO 线程池
     */
    public static ExecutorService io() {
        return IO;
    }

    /**
     * 获取计算线程池
     */
    public static ExecutorService compute() {
        return COMPUTE;
    }

    /**
     * 获取 AI 推理线程池
     */
    public static ExecutorService ai() {
        return AI;
    }

    /**
     * 获取后台线程池
     */
    public static ExecutorService background() {
        return BACKGROUND;
    }

    /**
     * 获取主线程 Handler
     */
    public static Handler main() {
        return MAIN_HANDLER;
    }

    /**
     * 在主线程执行任务
     */
    public static void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            MAIN_HANDLER.post(task);
        }
    }

    /**
     * 获取线程统计信息
     */
    public static String getStats() {
        return String.format(
            "IO: %d | Compute: %d | AI: 1 | Background: 1 | Total: ~8",
            4, 2
        );
    }
}
