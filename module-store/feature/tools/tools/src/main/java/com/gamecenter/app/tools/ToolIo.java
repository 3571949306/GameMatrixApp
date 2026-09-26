package com.gamecenter.app.tools;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具箱线程与缓存生命周期助手（纯 Java 可测逻辑 + 一次性共享兜底线程）。
 * <p>
 * 此前多个 ToolBinder 在调用方 executor 已 shutdown 时 {@code newSingleThreadExecutor()}
 * 兜底且从不 shutdown，按次泄漏线程。本类提供：
 * </p>
 * <ul>
 *   <li>{@link #require}: 优先用调用方池；否则用进程内共享单线程（daemon，不按次新建）</li>
 *   <li>{@link ShareCachePruner}: 分享 cache 目录按保留个数修剪，避免无限堆积</li>
 *   <li>{@link BitmapHold}: lastBitmap 替换时的回收保护（持有中不 recycle）</li>
 * </ul>
 */
final class ToolIo {

    private ToolIo() {
    }

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
    private static volatile ExecutorService sharedFallback;

    /**
     * 优先使用调用方提供的 executor；已 shutdown / null 时退回共享单线程。
     * 共享线程为 daemon，进程退出不阻塞；不按调用次数创建，避免线程泄漏。
     */
    static ExecutorService require(ExecutorService preferred) {
        if (preferred != null && !preferred.isShutdown() && !preferred.isTerminated()) {
            return preferred;
        }
        ExecutorService existing = sharedFallback;
        if (existing != null && !existing.isShutdown()) {
            return existing;
        }
        synchronized (ToolIo.class) {
            if (sharedFallback == null || sharedFallback.isShutdown()) {
                sharedFallback = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "tool-io-fallback-" + THREAD_SEQ.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
            }
            return sharedFallback;
        }
    }

    /** 仅测试用：当前共享兜底池是否已创建。 */
    static boolean sharedFallbackAlive() {
        ExecutorService existing = sharedFallback;
        return existing != null && !existing.isShutdown();
    }

    /** 仅测试用：关闭共享兜底池（测试隔离）。 */
    static void shutdownSharedFallbackForTest() {
        synchronized (ToolIo.class) {
            if (sharedFallback != null) {
                sharedFallback.shutdownNow();
                sharedFallback = null;
            }
        }
    }

    /**
     * Bitmap 持有计数：save/share 进行中不得 recycle 旧图。
     * 纯状态机，便于 JVM 单测。
     */
    static final class BitmapHold {
        private final AtomicInteger holds = new AtomicInteger();

        public void acquire() {
            holds.incrementAndGet();
        }

        public void release() {
            holds.decrementAndGet();
        }

        /** 当前是否有进行中的持有。 */
        public boolean isHeld() {
            return holds.get() > 0;
        }

        /**
         * 是否允许 recycle 旧图：无人持有时允许。
         */
        public boolean canRecycle() {
            return holds.get() <= 0;
        }
    }

    /**
     * 分享 cache 目录修剪：仅保留最近 {@code keep} 个文件，其余删除。
     * 纯文件策略，便于 JVM 单测。
     */
    static final class ShareCachePruner {
        private final int keep;

        ShareCachePruner(int keep) {
            this.keep = Math.max(1, keep);
        }

        /**
         * 修剪目录，返回删除的文件个数。
         */
        public int prune(File dir) {
            if (dir == null || !dir.isDirectory()) return 0;
            File[] files = dir.listFiles(File::isFile);
            if (files == null || files.length <= keep) return 0;
            // 最新的 keep 个保留（按最后修改时间降序）
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            int deleted = 0;
            for (int i = keep; i < files.length; i++) {
                if (files[i].delete()) deleted++;
            }
            return deleted;
        }
    }
}
