package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import org.junit.Test;

/**
 * 工具箱线程/缓存生命周期回归（JVM）。
 * <p>
 * 覆盖：executor 兜底不再按次泄漏（共享单线程）、BitmapHold 持有保护、
 * 分享 cache 按保留个数修剪。修复前 4 处 Binder 每次点击泄漏一个线程。
 * </p>
 */
public class ToolIoTest {

    @Test
    public void requireUsesPreferredWhenLive() {
        ExecutorService preferred = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertSame(preferred, ToolIo.require(preferred));
        } finally {
            preferred.shutdownNow();
        }
    }

    @Test
    public void requireFallsBackToSharedWhenPreferredShutdown() {
        ToolIo.shutdownSharedFallbackForTest();
        ExecutorService dead = java.util.concurrent.Executors.newSingleThreadExecutor();
        dead.shutdownNow();
        ExecutorService a = ToolIo.require(dead);
        ExecutorService b = ToolIo.require(null);
        assertNotNull(a);
        assertSame(a, b);
        assertTrue(ToolIo.sharedFallbackAlive());
        ToolIo.shutdownSharedFallbackForTest();
    }

    @Test
    public void bitmapHoldBlocksRecycleUntilReleased() {
        ToolIo.BitmapHold hold = new ToolIo.BitmapHold();
        assertTrue(hold.canRecycle());
        hold.acquire();
        assertFalse(hold.canRecycle());
        assertTrue(hold.isHeld());
        hold.release();
        assertTrue(hold.canRecycle());
        assertFalse(hold.isHeld());
    }

    @Test
    public void shareCachePrunerKeepsNewestFiles() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "qr_share_prune_" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            // 创建 7 个文件，保留 5
            File[] created = new File[7];
            for (int i = 0; i < 7; i++) {
                created[i] = new File(dir, "f" + i + ".png");
                try (FileOutputStream fos = new FileOutputStream(created[i])) {
                    fos.write(i);
                }
                // 保证 mtime 递增
                assertTrue(created[i].setLastModified(1_000_000L + i * 1_000L));
            }
            ToolIo.ShareCachePruner pruner = new ToolIo.ShareCachePruner(5);
            int deleted = pruner.prune(dir);
            assertEquals(2, deleted);
            File[] left = dir.listFiles(File::isFile);
            assertEquals(5, left.length);
            // 最新的 f5/f6 应保留
            assertTrue(new File(dir, "f5.png").exists());
            assertTrue(new File(dir, "f6.png").exists());
            assertFalse(new File(dir, "f0.png").exists());
        } finally {
            File[] rest = dir.listFiles();
            if (rest != null) {
                for (File f : rest) f.delete();
            }
            dir.delete();
        }
    }

    @Test
    public void shareCachePrunerIgnoresMissingDir() {
        assertEquals(0, new ToolIo.ShareCachePruner(3).prune(null));
        assertEquals(0, new ToolIo.ShareCachePruner(3)
                .prune(new File(System.getProperty("java.io.tmpdir"), "no_such_qr_dir_xyz")));
    }

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        for (Method method : ToolIoTest.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class) || method.getParameterCount() != 0) continue;
            try {
                method.invoke(new ToolIoTest());
                System.out.println("  PASS  " + method.getName());
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.println("  FAIL  " + method.getName() + " — " + cause);
                failed++;
            }
        }
        System.out.println("TOOL_IO_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL")
                + " (passed=" + passed + " failed=" + failed + ")");
        if (failed > 0) System.exit(1);
    }
}
