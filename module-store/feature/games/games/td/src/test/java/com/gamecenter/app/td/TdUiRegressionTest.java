package com.gamecenter.app.td;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import androidx.test.core.app.ApplicationProvider;

import com.gamecenter.app.R;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevelJsonParser;
import com.gamecenter.app.td.engine.TdLevels;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * td UI 层回归测试（Robolectric，sdk 35）。
 *
 * <p><b>可测面与接缝（为何这样断言）：</b>
 * <ul>
 *   <li>宿主资源键（com.gamecenter.app.R）在编译期被内联为 int 常量，而 td 模块的
 *       Robolectric 资源表只含本模块资源，没有宿主的 game_td_* 字符串条目——若断言真实
 *       本地化文案，getString 会直接 NotFoundException。因此用 {@link RecordingResources}
 *       拦截 getString，把回归面钉在「资源键 + 格式化参数 + 赋值结果」三元组上：
 *       资源键被改、参数漏传/顺序颠倒、结果未写入字段任一回归都会被拦下；具体文案内容
 *       属于宿主资源资产，不在本模块可测范围。</li>
 *   <li>波次横幅：TdView 构造轻（无 game 依赖），直接实例化；waveBannerText 为 private
 *       字段，按包内约定用反射读取，不改生产可见性。</li>
 *   <li>战绩面板：showStatsPanel 是 Fragment 私有方法且依赖 overlayRoot/save 状态。
 *       onCreateView 的完整链路（ModuleManager 模块资源加载 + 宿主目录信任状态）依赖宿主
 *       进程，且宿主 app-classes.jar 是 compileOnly 不在测试运行时 classpath，Robolectric
 *       单测无法复现——故用 {@link StatsHostFragment} 屏蔽 onCreateView，仅注入面板真正
 *       消费的三个状态（overlayRoot/save/TdLevels catalog）后反射调用，做覆盖层冒烟：
 *       子 View 数量、标题键、返回按钮键、存档数据到 UI 行的接线。</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TdUiRegressionTest {

    @BeforeClass
    public static void loadProductionCampaignData() throws Exception {
        // 生产内容经严格解析器从模块真实资产装载（与 TdGameTest 同一模式）：application
        // 模块的 unit test fixture 不合并 assets（AGP 仅对 library 模块做 unitTest 资产合并），
        // Robolectric AssetManager 读不到 td/manifest.json，故走文件系统 + installForTesting。
        TdLevels.installForTesting(
                TdLevelJsonParser.parseChapter(readAsset(findAssetsRoot()
                        .resolve("td/chapters/chapter_main_01.json"))).levels);
    }

    private static Path findAssetsRoot() {
        Path[] candidates = new Path[] {
                Paths.get("src/main/assets"),
                Paths.get("module-store/feature/games/games/td/src/main/assets")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("td/manifest.json"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("production TD campaign asset was not found for JVM test");
    }

    private static String readAsset(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    // ===== 波次横幅（TdView）：资源键 + 参数 + 赋值结果 =====

    @Test
    public void campaignWaveBannerPinsResourceKeyFormatArgsAndText() throws Exception {
        BannerHarness harness = newTdView();

        harness.view.showWaveBanner(3, 10);

        assertEquals("campaign 横幅文案必须来自 game_td_banner_wave 并代入 (当前波, 总波)",
                RecordingResources.stub(R.string.game_td_banner_wave, 3, 10), bannerText(harness.view));
        harness.resources.assertLastLookup(R.string.game_td_banner_wave, 3, 10);
    }

    @Test
    public void endlessWaveBannerPinsResourceKeyFormatArgsAndText() throws Exception {
        BannerHarness harness = newTdView();

        harness.view.showWaveBannerEndless(7);

        assertEquals("无尽横幅文案必须来自 game_td_banner_wave_endless 并代入当前波数",
                RecordingResources.stub(R.string.game_td_banner_wave_endless, 7), bannerText(harness.view));
        harness.resources.assertLastLookup(R.string.game_td_banner_wave_endless, 7);
    }

    // ===== 战绩面板（TdModuleFragment）：覆盖层冒烟 + 数据接线 =====

    @Test
    public void statsPanelBuildsOverlayWithRowsAndBackButton() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "stats-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordEndlessWaves(TdGame.Difficulty.NORMAL, 11); // 验证存档数据 → 面板行的接线
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);

        invokePrivateMethod(fragment, "showStatsPanel");

        assertTrue("战绩面板必须向 overlayRoot 至少添加一个覆盖层子 View",
                overlayRoot.getChildCount() > 0);
        assertNotNull("战绩面板必须包含标题（game_td_stats_title）",
                findViewByStubText(overlayRoot, R.string.game_td_stats_title));
        TextView back = findViewByStubText(overlayRoot, R.string.game_td_stats_btn_back);
        assertNotNull("战绩面板必须包含返回按钮（game_td_stats_btn_back）", back);
        assertTrue("返回入口必须是 Button", back instanceof Button);
        assertNotNull("无尽最佳行必须由存档数据接线（game_td_stats_value_waves + 11）",
                findViewByStubText(overlayRoot, R.string.game_td_stats_value_waves, 11));
    }

    // ===== 测试基建：资源拦截 / 视图注入 / 反射辅助 =====

    /** TdView + 拦截资源的一体句柄：断言既看写入结果也看资源键/参数。 */
    private static final class BannerHarness {
        final TdView view;
        final RecordingResources resources;

        BannerHarness(TdView view, RecordingResources resources) {
            this.view = view;
            this.resources = resources;
        }
    }

    private BannerHarness newTdView() {
        Context appContext = ApplicationProvider.getApplicationContext();
        RecordingResources resources = new RecordingResources(appContext.getResources());
        return new BannerHarness(new TdView(new InterceptingContext(appContext, resources)), resources);
    }

    private static String bannerText(TdView view) throws Exception {
        Field field = TdView.class.getDeclaredField("waveBannerText");
        field.setAccessible(true);
        return (String) field.get(view);
    }

    /**
     * 拦截 getString 的 Resources：所有文案变成确定性 stub 文本 TD_RES#<id>#<args>，
     * 使断言可以在无宿主资源表的环境下钉住资源键与格式化参数。
     */
    private static final class RecordingResources extends Resources {
        private int lastResId;
        private Object[] lastArgs;

        RecordingResources(Resources delegate) {
            super(delegate.getAssets(), delegate.getDisplayMetrics(), delegate.getConfiguration());
        }

        @Override
        public String getString(int resId, Object... formatArgs) {
            lastResId = resId;
            lastArgs = formatArgs;
            return stub(resId, formatArgs);
        }

        @Override
        public String getString(int resId) {
            // Fragment.getString(int) 走 Resources.getString(int)（内部 getText 查真实资源表，
            // td 模块表中没有宿主字符串条目），必须一并拦截。
            lastResId = resId;
            lastArgs = null;
            return stub(resId);
        }

        void assertLastLookup(int expectedResId, Object... expectedArgs) {
            assertEquals("横幅必须引用宿主资源键", expectedResId, lastResId);
            assertArrayEquals("横幅格式化参数必须逐位一致", expectedArgs, lastArgs);
        }

        /** 确定性 stub 文本；formatArgs 为 null（getString(int) 单参路径）时不追加参数段。 */
        static String stub(int resId, Object... formatArgs) {
            StringBuilder text = new StringBuilder("TD_RES#").append(resId);
            if (formatArgs != null) {
                for (Object arg : formatArgs) text.append('#').append(arg);
            }
            return text.toString();
        }
    }

    /** getResources 返回拦截版 Resources 的上下文；TdView.getContext() 会原样持有它。 */
    private static final class InterceptingContext extends ContextWrapper {
        private final RecordingResources resources;

        InterceptingContext(Context base, RecordingResources resources) {
            super(base);
            this.resources = resources;
        }

        @Override
        public Resources getResources() {
            return resources;
        }
    }

    /** 仅承载 Fragment 生命周期的空 Activity：getString 经此到达拦截资源。 */
    public static class PanelHostActivity extends FragmentActivity {
        private RecordingResources recording;

        @Override
        public Resources getResources() {
            if (recording == null) {
                recording = new RecordingResources(super.getResources());
            }
            return recording;
        }
    }

    /**
     * 屏蔽宿主依赖（ModuleManager/模块资源目录信任）的 Fragment 测试壳：
     * onCreateView 只提供最小视图，面板所需状态由测试显式注入。
     */
    public static class StatsHostFragment extends TdModuleFragment {
        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return new FrameLayout(requireContext());
        }
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        Field field = TdModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokePrivateMethod(Object target, String name) throws Exception {
        Method method = TdModuleFragment.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    /** 广度优先查找文本等于 stub(resId, args) 的 TextView（Button 亦是其子类）。 */
    private static TextView findViewByStubText(ViewGroup root, int resId, Object... args) {
        String expected = RecordingResources.stub(resId, args);
        Deque<View> queue = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) queue.add(root.getChildAt(i));
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view instanceof TextView && expected.equals(((TextView) view).getText().toString())) {
                return (TextView) view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return null;
    }

    /**
     * 进程内 SharedPreferences 假实现（与 TdSaveManagerTest.MemoryPrefs 同构，但两个测试类
     * 分属不同 ClassLoader：纯 JVM 与 Robolectric sandbox，不能共享类型）。
     */
    private static final class MemoryPrefs implements SharedPreferences {
        private final java.util.Map<String, Object> data = new java.util.HashMap<>();

        @Override public java.util.Map<String, ?> getAll() { return new java.util.HashMap<>(data); }
        @Override public String getString(String key, String defValue) {
            Object v = data.get(key);
            return v instanceof String ? (String) v : defValue;
        }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
            return defValues;
        }
        @Override public int getInt(String key, int defValue) {
            Object v = data.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object v = data.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object v = data.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = data.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }

        private final class MemoryEditor implements Editor {
            private final java.util.Map<String, Object> pending = new java.util.HashMap<>();

            @Override public Editor putString(String key, String value) {
                pending.put(key, value); return this;
            }
            @Override public Editor putStringSet(String key, java.util.Set<String> values) {
                pending.put(key, values); return this;
            }
            @Override public Editor putInt(String key, int value) {
                pending.put(key, value); return this;
            }
            @Override public Editor putLong(String key, long value) {
                pending.put(key, value); return this;
            }
            @Override public Editor putFloat(String key, float value) {
                pending.put(key, value); return this;
            }
            @Override public Editor putBoolean(String key, boolean value) {
                pending.put(key, value); return this;
            }
            @Override public Editor remove(String key) {
                data.remove(key); return this;
            }
            @Override public Editor clear() {
                data.clear(); return this;
            }
            @Override public boolean commit() {
                data.putAll(pending);
                return true;
            }
            @Override public void apply() {
                data.putAll(pending);
            }
        }
    }
}
