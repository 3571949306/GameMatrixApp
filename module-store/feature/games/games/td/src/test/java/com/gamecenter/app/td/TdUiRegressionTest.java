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
import com.gamecenter.app.td.engine.MonsterType;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevelDefinition;
import com.gamecenter.app.td.engine.TdLevelJsonParser;
import com.gamecenter.app.td.engine.TdLevels;
import com.gamecenter.app.td.engine.TowerType;

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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
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
 *   <li>图鉴面板：与战绩面板同接缝（反射调 showCodexPanel / showLevelSelect + 注入
 *       overlayRoot/save），断言覆盖层结构特征——段落 tag 计数（每种塔/怪恰一段）、标题键、
 *       返回按钮键、特性行非空——以及引擎枚举字段到 UI 的数值接线（造价/生命实时格式化）。</li>
 *   <li>选关面板（章节分组）：同接缝（反射调 showLevelSelect + 注入 overlayRoot/save，
 *       BeforeClass 经 installForTesting(manifest, definitions) 装填全战役 + 章节元数据）。
 *       断言章头 section tag 顺序 = manifest 章节顺序、章头文本 = TdLevels 按 locale 解析的
 *       章节名（zh 权威源 / en 读 name_en）、每章卡片数 = levelCount、卡片文档序 = 全局
 *       levelIds、进度行按「星数非零即已通」口径接线（game_td_chapter_progress stub）。</li>
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
        // 选关面板按章节分组后，这里同步装填 manifest 章节元数据，保证章头分组可断言。
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                readAsset(assetsRoot.resolve("td/manifest.json")));
        List<TdLevelDefinition> definitions = new ArrayList<>();
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            definitions.addAll(TdLevelJsonParser.parseChapter(
                    readAsset(assetsRoot.resolve("td").resolve(ref.file))).levels);
        }
        TdLevels.installForTesting(manifest, definitions);
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

    // ===== 图鉴面板（TdModuleFragment）：覆盖层冒烟 + 枚举字段到 UI 的数值接线 =====

    @Test
    public void codexPanelBuildsSectionsForEveryTowerAndMonster() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "codex-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", new TdSaveManager(new MemoryPrefs()));

        invokePrivateMethod(fragment, "showCodexPanel");

        assertTrue("图鉴面板必须向 overlayRoot 至少添加一个覆盖层子 View",
                overlayRoot.getChildCount() > 0);
        assertNotNull("图鉴面板必须包含标题（game_td_codex_title）",
                findViewByStubText(overlayRoot, R.string.game_td_codex_title));
        assertNotNull("图鉴必须包含「防御塔」分组标题",
                findViewByStubText(overlayRoot, R.string.game_td_codex_section_towers));
        assertNotNull("图鉴必须包含「怪物」分组标题",
                findViewByStubText(overlayRoot, R.string.game_td_codex_section_monsters));
        TextView back = findViewByStubText(overlayRoot, R.string.game_td_stats_btn_back);
        assertNotNull("图鉴必须包含返回选关按钮（复用 game_td_stats_btn_back）", back);
        assertTrue("返回入口必须是 Button", back instanceof Button);

        // 每种塔/怪恰一段（tag 前缀计数），且每段挂一条非空特性行——
        // 特性 switch 漏 case 会落空串，在此被拦截
        List<View> towerSections = collectViewsByTagPrefix(overlayRoot, "td_codex_tower:");
        List<View> monsterSections = collectViewsByTagPrefix(overlayRoot, "td_codex_monster:");
        assertEquals("图鉴必须为每种塔各建一个段落",
                TowerType.values().length, towerSections.size());
        assertEquals("图鉴必须为每种怪各建一个段落",
                MonsterType.values().length, monsterSections.size());
        for (View section : towerSections) {
            assertTraitLineWired((ViewGroup) section);
        }
        for (View section : monsterSections) {
            assertTraitLineWired((ViewGroup) section);
        }

        // 数值单一来源接线：造价/生命必须实时格式化自引擎枚举字段，而非复制进文案。
        // 造价断言限定在 tag=td_codex_tower:BOTTLE 段内：全面板查找 "₿60" 会与 BOSS 赏金行
        // （BOSS.value=60）碰撞，造价行接线断掉时赏金行仍会误放行。
        List<View> bottleSections = collectViewsByTagPrefix(overlayRoot, "td_codex_tower:BOTTLE");
        assertEquals("图鉴必须为 BOTTLE 恰建一个段落（精确 tag 匹配）", 1, bottleSections.size());
        assertTrue("塔造价行必须实时格式化自 TowerType.baseCost（tag=td_codex_tower:BOTTLE 段内）",
                hasTextViewWithText((ViewGroup) bottleSections.get(0), "₿" + TowerType.BOTTLE.baseCost));
        assertTrue("怪生命行必须实时格式化自 MonsterType.hp",
                hasTextViewWithText(overlayRoot, formatCodexNumber(MonsterType.NORMAL.hp)));
    }

    // ===== 选关面板（按章节分组）：章头顺序 / 本地化章节名 / 每章卡片数与滚动顺序 =====

    @Test
    public void levelSelectGroupsLevelsByChapterInOrder() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "level-select-host").commitNow();

        // 进度口径见证：main_001（第 1 章）与 main_015（第 2 章）有星 → 前两章各「已通 1」，
        // 第 3/4 章「已通 0」；星数非零即已通，与战绩面板 countCleared 同口径
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.setBestStars("main_001", 3);
        save.setBestStars("main_015", 2);

        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);

        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                readAsset(findAssetsRoot().resolve("td/manifest.json")));
        assertEquals("前置：manifest 必须注册 4 个章节（战役扩展时同步更新本用例）",
                4, manifest.chapters.size());

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            invokePrivateMethod(fragment, "showLevelSelect");

            List<View> sections = collectViewsByTagPrefix(overlayRoot, "td_chapter:");
            assertEquals("选关面板必须按章节分 4 段（每章一个 section）", 4, sections.size());
            for (int i = 0; i < manifest.chapters.size(); i++) {
                TdLevelJsonParser.ChapterRef ref = manifest.chapters.get(i);
                ViewGroup section = (ViewGroup) sections.get(i);
                assertEquals("章节分组顺序必须与 manifest 一致",
                        "td_chapter:" + ref.id, section.getTag());
                TextView name = (TextView) findTagged(overlayRoot, "td_chapter_name:" + ref.id);
                assertNotNull("章头必须存在（td_chapter_name:" + ref.id + "）", name);
                assertEquals("章头文本必须是本地化章节名（zh 读权威源）",
                        ref.name, name.getText().toString());
                assertEquals("每章下关卡卡片数必须等于该章 levelCount", ref.levelCount,
                        collectViewsByTagPrefix(section, "td_level_card:").size());
                TextView progress = (TextView) findTagged(overlayRoot,
                        "td_chapter_progress:" + ref.id);
                assertNotNull("章头进度行必须存在（td_chapter_progress:" + ref.id + "）", progress);
                assertEquals("章头进度必须按「星数非零即已通」口径接线（game_td_chapter_progress）",
                        RecordingResources.stub(R.string.game_td_chapter_progress,
                                i <= 1 ? 1 : 0, ref.levelCount),
                        progress.getText().toString());
            }
            // 滚动顺序 = 章节顺序：跨章收集卡片 tag 的文档序必须与全局 levelIds 一致
            List<String> cardOrder = new ArrayList<>();
            for (View sectionView : sections) {
                for (View card : collectViewsByTagPrefix((ViewGroup) sectionView, "td_level_card:")) {
                    cardOrder.add(((String) card.getTag()).substring("td_level_card:".length()));
                }
            }
            assertEquals("关卡卡片滚动顺序必须与全局关卡顺序一致", TdLevels.levelIds(), cardOrder);

            // en locale：章头切换为 manifest 的 name_en 英文字段
            Locale.setDefault(Locale.US);
            invokePrivateMethod(fragment, "showLevelSelect");
            for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
                TextView name = (TextView) findTagged(overlayRoot, "td_chapter_name:" + ref.id);
                assertNotNull("en 章头必须存在（td_chapter_name:" + ref.id + "）", name);
                assertEquals("en locale 章头必须读英文章节名", ref.nameEn, name.getText().toString());
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void levelSelectCodexEntryOpensCodexPanel() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "codex-entry-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", new TdSaveManager(new MemoryPrefs()));

        invokePrivateMethod(fragment, "showLevelSelect");

        TextView codexEntryView = findViewByStubText(overlayRoot, R.string.game_td_btn_codex);
        assertNotNull("选关面板必须包含图鉴入口按钮（game_td_btn_codex）", codexEntryView);
        assertTrue("图鉴入口必须是 Button", codexEntryView instanceof Button);

        codexEntryView.performClick();

        assertNotNull("点击图鉴入口后必须打开图鉴面板（game_td_codex_title）",
                findViewByStubText(overlayRoot, R.string.game_td_codex_title));
        assertEquals("打开图鉴后必须为每种塔各建一个段落",
                TowerType.values().length,
                collectViewsByTagPrefix(overlayRoot, "td_codex_tower:").size());
    }

    @Test
    public void codexNumPinsRoundingAndTrailingZeroSpec() throws Exception {
        // 直接反射调用真方法钉住展示规格（private static），而非走镜像副本——
        // 镜像会随实现一起漂移，规格回归必须落在 codexNum 本体上。
        Method codexNum = TdModuleFragment.class.getDeclaredMethod("codexNum", float.class);
        codexNum.setAccessible(true);

        // 舍入见证：2.35f 的二进制表示略小于 2.35，按 %.2f 舍入必须给 "2.35"；截断实现会给出 "2.34"
        assertEquals("codexNum 必须按 %.2f 舍入而非截断", "2.35", codexNum.invoke(null, 2.35f));
        // 去尾零见证："0.90" 必须去尾零给 "0.9"；只剥 ".00" 后缀的实现会漏掉单尾零档
        assertEquals("codexNum 必须去掉单尾零", "0.9", codexNum.invoke(null, 0.9f));
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

    /** 广度优先查找 tag 精确匹配的 View（选关章头/进度行按 tag 定位）。 */
    private static View findTagged(ViewGroup root, String tag) {
        Deque<View> queue = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) queue.add(root.getChildAt(i));
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (tag.equals(view.getTag())) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return null;
    }

    /** 广度优先收集 tag 为字符串且以 tagPrefix 开头的 View（图鉴段落计数）。 */
    private static List<View> collectViewsByTagPrefix(ViewGroup root, String tagPrefix) {
        List<View> out = new ArrayList<>();
        Deque<View> queue = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) queue.add(root.getChildAt(i));
        while (!queue.isEmpty()) {
            View view = queue.poll();
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith(tagPrefix)) {
                out.add(view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return out;
    }

    /** 广度优先判断是否存在文本精确等于 expected 的 TextView（图鉴数值接线断言）。 */
    private static boolean hasTextViewWithText(ViewGroup root, String expected) {
        Deque<View> queue = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) queue.add(root.getChildAt(i));
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view instanceof TextView && expected.equals(((TextView) view).getText().toString())) {
                return true;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return false;
    }

    /** 图鉴段落必须挂 tag=td_codex_trait 的非空特性行（特性 switch 漏 case 会落空串）。 */
    private static void assertTraitLineWired(ViewGroup section) {
        for (int i = 0; i < section.getChildCount(); i++) {
            View child = section.getChildAt(i);
            if ("td_codex_trait".equals(child.getTag())
                    && child instanceof TextView) {
                TextView trait = (TextView) child;
                assertNotNull("段落 " + section.getTag() + " 特性行文案不得为 null", trait.getText());
                assertFalse("段落 " + section.getTag() + " 特性行不得为空（特性 switch 漏 case）",
                        trait.getText().toString().isEmpty());
                return;
            }
        }
        fail("段落 " + section.getTag() + " 缺少特性行（tag=td_codex_trait）");
    }

    /** 镜像 TdModuleFragment.codexNum 的展示口径，供数值接线断言计算期望文本。 */
    private static String formatCodexNumber(float value) {
        String s = String.format(Locale.US, "%.2f", value);
        if (s.endsWith(".00")) return s.substring(0, s.length() - 3);
        if (s.endsWith("0")) return s.substring(0, s.length() - 1);
        return s;
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
