package com.gamecenter.app.core.moduleloader;

import android.content.Context;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dalvik.system.DexClassLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3.4: Dynamic module loading test.
 *
 * Verifies core architecture invariants (no JUnit assertions, just logs):
 * 1. Module APK can be loaded from disk via DexClassLoader
 * 2. Loaded module can reference host classes (via parent ClassLoader)
 * 3. No duplicate classes (games module is compileOnly, not in host APK)
 *
 * Run: adb shell am instrument -w -e class com.gamecenter.app.core.moduleloader.ModuleLoadingTest \
 *   com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4.class)
public class ModuleLoadingTest {

    private static final String TAG = "ModuleLoadingTest";
    // static so it accumulates across all @Test methods (JUnit 4 creates new instance per test)
    private static final List<String> FAILURES = new ArrayList<>();

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private void assertEq(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            String msg = "FAIL: " + label + " expected=[" + expected + "] actual=[" + actual + "]";
            Log.e(TAG, msg);
            FAILURES.add(msg);
        } else {
            Log.d(TAG, "OK: " + label + " = " + actual);
        }
    }

    private void assertTrue(String label, boolean cond) {
        if (!cond) {
            String msg = "FAIL: " + label;
            Log.e(TAG, msg);
            FAILURES.add(msg);
        } else {
            Log.d(TAG, "OK: " + label);
        }
    }

    private void assertClassMissing(String className) {
        try {
            Class.forName(className, false, context().getClassLoader());
            String msg = "FAIL: " + className + " should NOT be in host APK (compileOnly change)";
            Log.e(TAG, msg);
            FAILURES.add(msg);
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "OK: " + className + " correctly NOT in host APK");
        }
    }

    private void assertClassPresent(String className) {
        try {
            Class<?> cls = Class.forName(className, false, context().getClassLoader());
            Log.d(TAG, "OK: " + className + " exists: " + cls.getSimpleName());
        } catch (ClassNotFoundException e) {
            String msg = "FAIL: " + className + " should be in host APK but not found: " + e.getMessage();
            Log.e(TAG, msg);
            FAILURES.add(msg);
        }
    }

    @Test
    public void test_HostHasNoGameClasses() {
        // Phase 3.4: games module 改成 compileOnly, host APK 不应再有游戏�?        assertClassMissing("com.gamecenter.app.games.breakout.BreakoutActivity");
        assertClassMissing("com.gamecenter.app.games.doudizhu.DouDiZhuRoomHelper");
        assertClassMissing("com.gamecenter.app.games.chinesechess.ChineseChessActivity");
        assertClassMissing("com.gamecenter.app.games.klotski.KlotskiActivity");
    }

    @Test
    public void test_HostHasCoreClasses() {
        assertClassPresent("com.gamecenter.app.GameMatrixApp");
        assertClassPresent("com.gamecenter.app.MainActivity");
        assertClassPresent("com.gamecenter.app.core.moduleloader.ModuleLoader");
        assertClassPresent("com.gamecenter.app.core.modulestore.ModuleDownloadManager");
    }

    @Test
    public void test_DexClassLoader_CanLoadModuleApk() throws Exception {
        File moduleApk = locateTestModuleApk();
        if (moduleApk == null) {
            FAILURES.add("Test needs chinesechess-debug.apk, not found. " +
                         "Run: ./gradlew :module-store:feature:games:games:chinesechess:assembleDebug");
            return;
        }
        ClassLoader hostClassLoader = context().getClassLoader();
        File optimizedDir = new File(context().getCacheDir(), "test-dex");
        if (!optimizedDir.exists()) optimizedDir.mkdirs();

        String modulePath = moduleApk.getAbsolutePath();
        String optimizedPath = optimizedDir.getAbsolutePath();
        Log.d(TAG, "Loading module: " + modulePath);
        Log.d(TAG, "Parent ClassLoader: " + hostClassLoader);

        DexClassLoader moduleLoader = new DexClassLoader(
            modulePath,
            optimizedPath,
            null,
            hostClassLoader
        );

        Class<?> moduleClass = moduleLoader.loadClass("com.gamecenter.app.games.chinesechess.ChineseChessActivity");
        assertTrue("Module class loaded", moduleClass != null);
        assertEq("Module class simpleName", "ChineseChessActivity", moduleClass != null ? moduleClass.getSimpleName() : null);
    }

    @Test
    public void test_LoadedModule_CanReferenceHostClass() throws Exception {
        File moduleApk = locateTestModuleApk();
        if (moduleApk == null) {
            FAILURES.add("Test needs chinesechess-debug.apk, not found");
            return;
        }
        ClassLoader hostClassLoader = context().getClassLoader();
        File optimizedDir = new File(context().getCacheDir(), "test-dex-2");
        if (!optimizedDir.exists()) optimizedDir.mkdirs();

        DexClassLoader moduleLoader = new DexClassLoader(
            moduleApk.getAbsolutePath(),
            optimizedDir.getAbsolutePath(),
            null,
            hostClassLoader
        );

        Class<?> activityClass = moduleLoader.loadClass("com.gamecenter.app.games.chinesechess.ChineseChessActivity");
        Constructor<?> constructor = activityClass.getDeclaredConstructor();
        Log.d(TAG, "OK: Module Activity constructor accessible: " + constructor);

        Class<?> saveManagerCls = Class.forName("com.gamecenter.app.SaveManager", false, hostClassLoader);
        assertTrue("host has SaveManager", saveManagerCls != null);
        assertEq("SaveManager simpleName", "SaveManager", saveManagerCls != null ? saveManagerCls.getSimpleName() : null);
    }

    @Test
    public void test_AllChecks() {
        // Aggregated check at end - if any earlier @Test failed, fail this one
        if (!FAILURES.isEmpty()) {
            throw new AssertionError("Test failures: " + FAILURES.size() + "\n" +
                String.join("\n", FAILURES));
        }
    }

    private File locateTestModuleApk() {
        String[] candidates = {
            "module-store/feature/games/games/chinesechess/build/outputs/apk/debug/chinesechess-debug.apk",
            "module-store/feature/games/games/klotski/build/outputs/apk/debug/klotski-debug.apk",
            "module-store/feature/games/games/game2048/build/outputs/apk/debug/game2048-debug.apk"
        };
        for (String path : candidates) {
            File file = new File(path);
            if (file.exists()) {
                Log.d(TAG, "Found test module APK: " + path + " (" + file.length() + " bytes)");
                return file;
            }
        }
        return null;
    }
}
