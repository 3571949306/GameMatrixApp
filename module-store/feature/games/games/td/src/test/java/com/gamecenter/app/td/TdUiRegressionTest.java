package com.gamecenter.app.td;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    // ===== 绘制热路径文案缓存：构造/setter 期解析一次，结果与直接 getString 逐字符一致 =====

    @Test
    public void multipleEntrancesRenderNumbersMatchingWavePreviewAndResetOnRebind() {
        BannerHarness harness = newTdView();
        int[][] upper = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7},
                {1, 7}, {2, 7}, {3, 7}, {4, 7}};
        int[][] lower = {{4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}, {4, 5}, {4, 6}, {4, 7}};
        List<TdGame.Wave> waves = java.util.Collections.singletonList(
                new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0f, 1f, 1f));
        final List<String> drawnText = new ArrayList<>();
        Canvas canvas = new Canvas() {
            @Override public void drawText(String text, float x, float y, Paint paint) {
                drawnText.add(text);
            }
        };
        harness.view.layout(0, 0, 960, 720);
        harness.view.bind(new TdGame(8, 5, new int[][][] {upper, lower}, 4, 7, 240, 5, waves));
        harness.view.onDraw(canvas);
        assertTrue("Entrance one must be distinguishable from entrance two", drawnText.contains("1"));
        assertTrue("The second route must have its own visible marker", drawnText.contains("2"));
        assertFalse("Two entrances must not share an ambiguous generic sign",
                drawnText.contains(RecordingResources.stub(R.string.game_td_sign_entrance)));

        drawnText.clear();
        harness.view.bind(new TdGame(8, 5, upper, 4, 7, 240, 5, waves));
        harness.view.onDraw(canvas);
        assertTrue("Returning to a single-route level must restore its localized entrance sign",
                drawnText.contains(RecordingResources.stub(R.string.game_td_sign_entrance)));
        assertFalse("Rebinding cannot retain an obsolete second entrance label", drawnText.contains("2"));
    }

    @Test
    public void drawTextCachesMatchDirectLookupsAfterConstruction() throws Exception {
        BannerHarness harness = newTdView();

        // 关键字符串抽查：木牌 / 建塔模式提示 / 拖拽合成字符 / 结算胜负文案，
        // 均为 onDraw 每帧路径原 getString 直查点，缓存化后必须逐字符一致
        assertEquals("入口木牌文案缓存必须等于直接 getString 结果",
                RecordingResources.stub(R.string.game_td_sign_entrance),
                privateStringField(harness.view, "signEntranceText"));
        assertEquals("建塔模式提示缓存必须等于直接 getString 结果",
                RecordingResources.stub(R.string.game_td_hint_build_mode),
                privateStringField(harness.view, "hintBuildModeText"));
        assertEquals("拖拽合成字符缓存必须等于直接 getString 结果",
                RecordingResources.stub(R.string.game_td_drag_merge_char),
                privateStringField(harness.view, "dragMergeText"));
        assertEquals("胜利结算文案缓存必须等于直接 getString 结果",
                RecordingResources.stub(R.string.game_td_overlay_win),
                privateStringField(harness.view, "overlayWinText"));
        assertEquals("失败结算文案缓存必须等于直接 getString 结果",
                RecordingResources.stub(R.string.game_td_overlay_lose),
                privateStringField(harness.view, "overlayLoseText"));
    }

    @Test
    public void towerNameCacheCoversAllTypesWithDirectLookupValues() throws Exception {
        BannerHarness harness = newTdView();

        Field cacheField = TdView.class.getDeclaredField("towerNameCache");
        cacheField.setAccessible(true);
        String[] cache = (String[]) cacheField.get(harness.view);

        TowerType[] types = TowerType.values();
        assertEquals("塔名缓存必须覆盖每个 TowerType（按 ordinal 对齐）", types.length, cache.length);
        // 抽查首尾两档：缓存值必须等于各自资源键的直接 getString 结果
        assertEquals("BOTTLE 塔名缓存必须来自 game_td_tower_bottle",
                RecordingResources.stub(R.string.game_td_tower_bottle),
                cache[TowerType.BOTTLE.ordinal()]);
        assertEquals("AMPLIFIER 塔名缓存必须来自 game_td_tower_amplifier",
                RecordingResources.stub(R.string.game_td_tower_amplifier),
                cache[TowerType.AMPLIFIER.ordinal()]);
    }

    @Test
    public void selectionHintCacheRefreshedOnSetSelectedType() throws Exception {
        BannerHarness harness = newTdView();

        harness.view.setSelectedType(TowerType.BOTTLE);

        String bottleName = RecordingResources.stub(R.string.game_td_tower_bottle);
        assertEquals("可建造提示必须在 setSelectedType 变异点解析并代入塔名",
                RecordingResources.stub(R.string.game_td_hint_place, bottleName),
                privateStringField(harness.view, "hintPlaceText"));
        assertEquals("金币不足提示必须在 setSelectedType 变异点解析并代入塔名与造价",
                RecordingResources.stub(R.string.game_td_hint_not_enough, bottleName,
                        TowerType.BOTTLE.baseCost),
                privateStringField(harness.view, "hintNotEnoughText"));

        harness.view.setSelectedType(null);

        assertEquals("取消选塔后选塔提示缓存必须清空（绘制回落到建塔模式文案）",
                "", privateStringField(harness.view, "hintPlaceText"));
        assertEquals("取消选塔后金币不足提示缓存必须同步清空",
                "", privateStringField(harness.view, "hintNotEnoughText"));
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

    // ===== 成就分组（TdAchievement × 战绩面板）：行数/标题/达成状态接线 =====

    @Test
    public void statsPanelShowsAchievementGroupWithOneRowPerAchievement() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "achv-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        // 写入点检测接线见证：recordWin(main_001) 必须已解锁 FIRST_WIN（不依赖面板补漏）
        save.recordWin("main_001");
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);

        invokePrivateMethod(fragment, "showStatsPanel");

        assertNotNull("成就分组标题必须存在（game_td_achv_section_achievements）",
                findViewByStubText(overlayRoot, R.string.game_td_achv_section_achievements));
        assertEquals("战绩面板必须为每个成就各建一行",
                TdAchievement.values().length, collectViewsByTagPrefix(overlayRoot, "td_achv:").size());

        // 已解锁行显示 ✓ 已达成，未解锁行显示 未达成（复用既有达成状态文案键）
        ViewGroup firstWinRow = (ViewGroup) findTagged(overlayRoot, "td_achv:FIRST_WIN");
        assertNotNull("成就行必须挂 tag=td_achv:FIRST_WIN", firstWinRow);
        assertTrue("已解锁成就行必须显示达成状态（game_td_stats_value_achieved）",
                hasTextViewWithText(firstWinRow,
                        RecordingResources.stub(R.string.game_td_stats_value_achieved)));
        ViewGroup ch4Row = (ViewGroup) findTagged(overlayRoot, "td_achv:CHAPTER4_CLEARED");
        assertNotNull("成就行必须挂 tag=td_achv:CHAPTER4_CLEARED", ch4Row);
        assertTrue("未解锁成就行必须显示未达成状态（game_td_stats_value_not_achieved）",
                hasTextViewWithText(ch4Row,
                        RecordingResources.stub(R.string.game_td_stats_value_not_achieved)));
    }

    @Test
    public void statsResetShowsSuccessNoticeInsideRebuiltPanel() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "stats-reset-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.addKills(12);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);

        invokePrivateMethod(fragment, "showStatsPanel");
        TextView reset = findViewByStubText(overlayRoot, R.string.game_td_stats_btn_reset);
        assertNotNull("战绩面板必须包含清空按钮", reset);
        reset.performClick();

        TextView confirm = findViewByStubText(overlayRoot,
                R.string.game_td_stats_reset_confirm_yes);
        assertNotNull("清空操作必须先显示二次确认按钮", confirm);
        confirm.performClick();

        assertEquals("清空确认后数据必须归零", 0, save.getTotalKills());
        assertNotNull("清空成功提示必须位于重建后的战绩浮层内，而非被遮挡的 HUD 消息条",
                findViewByStubText(overlayRoot, R.string.game_td_stats_reset_done));
        assertNotNull("清空成功提示必须挂在战绩面板通知位",
                findTagged(overlayRoot, "td_stats_notice"));
    }

    // ===== 塔组开局剧情：走真实按钮回调，防止 startLevel 后清浮层误删刚创建的故事 =====

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void deckStartKeepsStoryVisibleUntilPlayerContinuesToBattle() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "story-start-host").commitNow();
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment, "save", save);

        // 复用生产 UI 构造，保留 startLevel 消费的真实 HUD/棋盘/开战按钮，
        // 仅由 StatsHostFragment 绕过 ModuleManager 的宿主资源装载。
        ViewGroup ui = (ViewGroup) invokePrivateMethod(fragment, "buildUi", new Class<?>[0]);
        ((ViewGroup) fragment.requireView()).addView(ui);
        try {
            invokePrivateMethod(fragment, "showDeckSelect",
                    new Class<?>[] {int.class, TdGame.Difficulty.class, boolean.class},
                    0, TdGame.Difficulty.NORMAL, false);
            TextView deckStart = findViewByStubText(ui, R.string.game_td_btn_start_level);
            assertNotNull("塔组面板必须提供真实开局按钮", deckStart);
            assertTrue(deckStart.performClick());

            TextView intro = (TextView) findTagged(ui, "td_story_intro");
            assertNotNull("塔组开局回调返回后，剧情不得被尾随 clearOverlay 删除", intro);
            String levelId = TdLevels.levelIds().get(0);
            assertFalse("前置：生产首关必须有故事", TdLevels.levelStoryIntro(levelId).isEmpty());
            assertEquals("故事正文必须接入当前关卡资产", TdLevels.levelStoryIntro(levelId),
                    intro.getText().toString());
            assertEquals("一次开局只计一局", 1, save.getPlayCount());

            Field gameField = TdModuleFragment.class.getDeclaredField("game");
            gameField.setAccessible(true);
            TdGame game = (TdGame) gameField.get(fragment);
            assertNotNull(game);
            assertEquals(TdGame.State.PREPARING, game.getState());
            invokePrivateMethod(fragment, "tickOnce");
            assertEquals("阅读剧情期间不得推进战斗时间", 0L, game.getElapsedTicksForDisplay());
            assertNotNull("剧情只能由玩家主动关闭", findTagged(ui, "td_story_intro"));

            TextView storyStart = findViewByStubText(ui, R.string.game_td_story_start);
            assertNotNull("剧情必须提供出战按钮", storyStart);
            assertTrue(storyStart.performClick());
            assertNull("点击出战后剧情必须关闭", findTagged(ui, "td_story_intro"));
            assertEquals("关闭剧情后仍允许先布防", TdGame.State.PREPARING, game.getState());

            TextView fight = findViewByStubText(ui, R.string.game_td_btn_fight);
            assertNotNull("剧情关闭后必须保留真实开战按钮", fight);
            assertTrue(fight.performClick());
            assertEquals(TdGame.State.RUNNING, game.getState());
            long elapsedBeforeTick = game.getElapsedTicksForDisplay();
            invokePrivateMethod(fragment, "tickOnce");
            assertEquals("出战后不能残留阻断战斗的覆盖层或暂停状态",
                    elapsedBeforeTick + 1L, game.getElapsedTicksForDisplay());
        } finally {
            fragment.onPause(); // startLevel/tickOnce 排入的循环不能泄漏到其他测试。
        }
    }

    // ===== 跨局合成选择：旧局源塔不得把新局普通选塔变成一次消费塔的合成 =====

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void pendingMergeDoesNotConsumeNewTowersAfterSettlementRetry() throws Exception {
        assertPendingMergeDoesNotCrossNewGame(true);
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void pendingMergeDoesNotConsumeNewTowersAfterReturningThroughLevelSelect() throws Exception {
        assertPendingMergeDoesNotCrossNewGame(false);
    }

    private void assertPendingMergeDoesNotCrossNewGame(boolean retryAfterLoss) throws Exception {
        ActivityController<PanelHostActivity> controller =
                Robolectric.buildActivity(PanelHostActivity.class).setup();
        PanelHostActivity activity = controller.get();
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "merge-session-host").commitNow();
        setPrivateField(fragment, "save", new TdSaveManager(new MemoryPrefs()));
        ViewGroup ui = (ViewGroup) invokePrivateMethod(fragment, "buildUi", new Class<?>[0]);
        ((ViewGroup) fragment.requireView()).addView(ui);
        try {
            invokePrivateMethod(fragment, "showDeckSelect",
                    new Class<?>[] {int.class, TdGame.Difficulty.class, boolean.class},
                    0, TdGame.Difficulty.NORMAL, false);
            clickMergeFlowButton(ui, R.string.game_td_btn_start_level);
            clickMergeFlowButton(ui, R.string.game_td_story_start);
            TdGame oldGame = (TdGame) readFragmentField(fragment, "game");
            TdView board = (TdView) readFragmentField(fragment, "tdView");

            // SUN is unlocked in the real first-level deck. It cannot kill enemies, so the
            // retry branch can reach a real production loss without forcing engine state.
            selectSunCardForMergeFlow(ui);
            tapBoardCellForMergeFlow(board, 1, 0);
            TdGame.Tower oldSource = oldGame.getTowerAt(1, 0);
            assertNotNull("真实塔牌和棋盘触摸必须建出源塔", oldSource);
            assertEquals(TowerType.SUN, oldSource.type);
            tapBoardCellForMergeFlow(board, 1, 0);
            assertSame(oldSource, board.getHoverTower());
            assertTrue(((Button) readFragmentField(fragment, "btnUpgrade")).performClick());
            assertSame("前置：真实合成按钮必须选中旧局源塔", oldSource,
                    readFragmentField(fragment, "mergeSource"));

            if (retryAfterLoss) {
                clickMergeFlowButton(ui, R.string.game_td_btn_fight);
                driveToEnd(oldGame);
                assertEquals("仅太阳花布防必须经真实引擎到达败局", TdGame.State.LOST,
                        oldGame.getState());
                invokePrivateMethod(fragment, "onGameEnded");
                assertNotNull(findViewByStubText(ui, R.string.game_td_result_lose));
                clickMergeFlowButton(ui, R.string.game_td_btn_retry);
            } else {
                clickMergeFlowButton(ui, R.string.game_td_btn_levels);
                ViewGroup firstCard = (ViewGroup) findTagged(ui,
                        "td_level_card:" + TdLevels.levelIds().get(0));
                assertNotNull("退出旧局后必须出现真实首关卡片", firstCard);
                clickMergeFlowButton(firstCard, R.string.game_td_btn_play);
                clickMergeFlowButton(ui, R.string.game_td_difficulty_normal);
                clickMergeFlowButton(ui, R.string.game_td_btn_start_level);
            }
            clickMergeFlowButton(ui, R.string.game_td_story_start);
            TdGame newGame = (TdGame) readFragmentField(fragment, "game");
            assertNotSame("导航必须创建新局，不能继续使用旧引擎", oldGame, newGame);
            assertEquals(TdGame.State.PREPARING, newGame.getState());
            assertEquals(0, newGame.getTowers().size());

            // Reuse the old source coordinates in the new game. No merge was requested in
            // this game: both placements and the subsequent selection use real UI callbacks.
            selectSunCardForMergeFlow(ui);
            tapBoardCellForMergeFlow(board, 1, 0);
            tapBoardCellForMergeFlow(board, 1, 1);
            TdGame.Tower first = newGame.getTowerAt(1, 0);
            TdGame.Tower target = newGame.getTowerAt(1, 1);
            assertNotNull(first);
            assertNotNull(target);
            assertEquals(2, newGame.getTowers().size());
            assertEquals(1, target.level);
            int coinBeforeSelection = newGame.getCoin();

            tapBoardCellForMergeFlow(board, 1, 1);

            assertSame("新局普通选塔不得消费旧源坐标上的新塔", first, newGame.getTowerAt(1, 0));
            assertSame(target, newGame.getTowerAt(1, 1));
            assertEquals("没有发起新合成时两座塔都必须保留", 2, newGame.getTowers().size());
            assertEquals("普通选中不能把目标升级", 1, target.level);
            assertEquals("普通选中不能改变金币", coinBeforeSelection, newGame.getCoin());
            assertSame("棋盘必须正常选中目标塔", target, board.getHoverTower());
            assertEquals("正常选塔必须显示操作栏", View.VISIBLE,
                    ((View) readFragmentField(fragment, "towerOpsBar")).getVisibility());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static void clickMergeFlowButton(ViewGroup root, int resourceId) {
        TextView button = findViewByStubText(root, resourceId);
        assertNotNull("合成跨局流程缺少按钮资源 " + resourceId, button);
        assertTrue("必须点击生产 Button", button instanceof Button);
        assertTrue("必须执行生产点击回调", button.performClick());
    }

    private static void selectSunCardForMergeFlow(ViewGroup root) {
        TextView name = findViewByStubText(root, R.string.game_td_tower_sun);
        assertNotNull("真实塔栏必须有已解锁的太阳花", name);
        View card = (View) name.getParent();
        assertEquals(View.VISIBLE, card.getVisibility());
        assertTrue("必须通过生产塔牌点击选择类型", card.performClick());
    }

    private static void tapBoardCellForMergeFlow(TdView board, int row, int col) throws Exception {
        board.layout(0, 0, 720, 720);
        Field cellSize = TdView.class.getDeclaredField("cellSize");
        Field originX = TdView.class.getDeclaredField("originX");
        Field originY = TdView.class.getDeclaredField("originY");
        cellSize.setAccessible(true);
        originX.setAccessible(true);
        originY.setAccessible(true);
        float size = cellSize.getFloat(board);
        assertTrue("生产布局必须计算出可触摸棋盘", size > 0f);
        float x = originX.getFloat(board) + (col + .5f) * size;
        float y = originY.getFloat(board) + (row + .5f) * size;
        assertArrayEquals("触摸坐标必须来自当前棋盘几何", new int[] {row, col}, board.cellAt(x, y));
        MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_UP, x, y, 0);
        try {
            assertTrue(board.onTouchEvent(down));
            assertTrue(board.onTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static Object readFragmentField(TdModuleFragment fragment, String name) throws Exception {
        Field field = TdModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fragment);
    }

    // ===== 结算面板成就行（回归：成就提示曾走 tvMsg 弹条，弹出瞬间被 0xE6 结算浮层覆盖不可读） =====

    @Test
    public void resultPanelRendersDrainedAchievementLineAsOwnRow() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "result-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordWin("main_001"); // 结算写入点产生一枚待提示成就（FIRST_WIN）
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", TdLevels.buildLevel("main_001", TdGame.Mode.CAMPAIGN));

        // 结算时序接线：写入点之后 showResult 之前 drain，文案作为参数传入面板
        Method drain = TdModuleFragment.class.getDeclaredMethod("drainAchievementLine");
        drain.setAccessible(true);
        String achvLine = (String) drain.invoke(fragment);
        assertEquals("成就行文案必须来自 game_td_achv_unlocked 并代入成就名",
                RecordingResources.stub(R.string.game_td_achv_unlocked,
                        RecordingResources.stub(R.string.game_td_achv_name_first_win)),
                achvLine);

        Method showResult = TdModuleFragment.class.getDeclaredMethod("showResult",
                String.class, String.class, String.class, String.class, int.class);
        showResult.setAccessible(true);
        showResult.invoke(fragment, "title", "stars", "stats", achvLine, 0xFF66BB6A);

        assertNotNull("结算面板必须包含成就解锁附加行（星级/统计行之后，game_td_achv_unlocked）",
                findViewByStubText(overlayRoot, R.string.game_td_achv_unlocked,
                        RecordingResources.stub(R.string.game_td_achv_name_first_win)));
    }

    // ===== 结算写入点：击杀计入所有结局（回归：修复前仅 WON 分支 addKills，败局/无尽击杀全部丢失） =====

    @Test
    public void onGameEndedCampaignLossStillCountsKillsTowardTotalAndAchievements() throws Exception {
        // 确定性败局：第 1 波 8 只普通怪被塔群部分击杀（kills ≥ 1），蛋 1 点血——首只漏怪即判负；
        // 第 2 波 hpMul=1000 兜底（塔群不可能清掉），保证战役败局而非胜利
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 8, 0.5f, 0.1f, 1f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1000f, 1f));
        TdGame game = minimalEndableGame(waves, 1, TdGame.Mode.CAMPAIGN);
        placeKillzoneDefenses(game);
        driveToEnd(game);
        assertEquals(TdGame.State.LOST, game.getState());
        assertTrue("败局必须携带击杀（修复前该击杀被整体丢弃）", game.getMonstersKilled() >= 1);

        // 预置 99 击杀：败局击杀 ≥1 即跨越 KILLS_100 阈值，写入点必须即时解锁并随面板提示
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_kills_total", 99);
        TdSaveManager save = new TdSaveManager(prefs);
        FrameLayout overlayRoot = wireAndEndGame(game, save, null);

        assertEquals("败局击杀必须计入总击杀（修复前丢失）",
                99 + game.getMonstersKilled(), save.getTotalKills());
        assertTrue("败局击杀跨阈值必须即时解锁 KILLS_100（addKills 内部写入点判定）",
                save.isAchievementUnlocked(TdAchievement.KILLS_100));
        assertNotNull("败局结算面板必须带出成就解锁行（game_td_achv_unlocked）",
                findViewByStubText(overlayRoot, R.string.game_td_achv_unlocked,
                        RecordingResources.stub(R.string.game_td_achv_name_kills_100)));
    }

    @Test
    public void onGameEndedEndlessLossStillCountsKillsTowardTotal() throws Exception {
        // 无尽败局：唯一出口是蛋死亡（绝无 WON）；定义波 8 只怪被塔群部分击杀后首漏即终，
        // 若定义波被全清则由工厂合成后续波继续（驱动循环自动开波），结局仍必为败局
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 8, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 1, TdGame.Mode.ENDLESS);
        placeKillzoneDefenses(game);
        // 驱动预算（局部 final；同文件其余结算用例仍走 driveToEnd 默认 60*240，不受影响）。
        // 推演依据（终态 = 首只漏怪，蛋 1 血；同引擎 60Hz 确定性仿真 + 工厂曲线上界）：
        //  - 下界：定义波 8×NORMAL（34 血）必被清空——塔群中 (1,4) 单塔即覆盖整条路径
        //    （射程 5.5，路径距塔心仅 dy=1），全程 ~4.7s 可倾泻 ~15 发 ×26 ≈ 390 伤害 ≫ 34 血，
        //    故首漏必发生在工厂合成的无尽波（getEndlessWaveReached() ≥ 2）；
        //  - 实测：第 16 波首漏，13736 tick ≈ 228.9 游戏秒；
        //  - 上界：当前数值下 BOSS 波 20（600×1.1^19 ≈ 3669 血）全程最多承受
        //    3×26/0.32×8.75s ≈ 2100 伤害必漏 → 终态最迟 ~第 20 波 ≈ 350 游戏秒
        //    （塔伤加倍探针仿真：第 20 波首漏、321 游戏秒，仍在本预算内）。
        // 旧预算 60*240 = 240 游戏秒对实测值仅 ~5% 余量，任何平衡微调都会以「未达终态」误红；
        // 放宽到 60*900 = 900 游戏秒（对推演上界仍 ~2.5× 余量）不改变判别力：终态判定仍是
        // isEnded()，修复前「败局不计击杀」照样必红；预算耗尽未终态时 driveToEnd 守卫式
        // 失败并输出已达波次，直接给出诊断。
        final int endlessDriveBudgetTicks = 60 * 900;
        driveToEnd(game, endlessDriveBudgetTicks);
        assertEquals("无尽唯一结束方式是蛋死亡", TdGame.State.LOST, game.getState());
        // 隐含前提显式化：败局必须发生在工厂合成的无尽波（下界推演见上），证明驱动循环
        // 真实走过了 TdEndlessWaveFactory 的合成路径，而非在定义波内结束。
        assertTrue("无尽败局必须发生在工厂合成的无尽波（下界 ≥2；当前平衡实测第 16 波，上界第 20 波）",
                game.getEndlessWaveReached() >= 2);
        assertTrue(game.getMonstersKilled() >= 1);

        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        assertEquals(0, save.getTotalKills());
        FrameLayout overlayRoot = wireAndEndGame(game, save, null);

        assertEquals("无尽局击杀必须计入总击杀（修复前丢失）",
                game.getMonstersKilled(), save.getTotalKills());
        assertTrue("无尽结算面板必须渲染（game_td_result_lose 无尽分支）",
                overlayRoot.getChildCount() > 0);
        assertNotNull(findViewByStubText(overlayRoot, R.string.game_td_result_lose));
    }

    @Test
    public void onGameEndedWinCountsKillsExactlyOnceViaGameEndedGate() throws Exception {
        // 确定性胜局：两波各 1 只必被击杀的普通怪，蛋 5 点血 → WON 且 kills=2；
        // addKills 已提升至三分支共通处，须验证胜局仍恰好计一次（gameEnded 闸幂等）
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 5, TdGame.Mode.CAMPAIGN);
        placeKillzoneDefenses(game);
        driveToEnd(game);
        assertEquals(TdGame.State.WON, game.getState());
        assertEquals(2, game.getMonstersKilled());

        // 手动接线（不经 wireAndEndGame）：需要对本局二次触发 onGameEnded 验证闸幂等
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "ended-host").commitNow();
        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);
        setPrivateField(fragment, "selectedLevelIdx", 0); // WON 分支读 TdLevels.levelIds().get(idx)

        invokePrivateMethod(fragment, "onGameEnded");
        assertEquals("胜局击杀必须计入总击杀", 2, save.getTotalKills());

        invokePrivateMethod(fragment, "onGameEnded"); // gameEnded 闸：重复结算不得重复累加
        assertEquals("每局击杀只计一次（gameEnded 闸幂等）", 2, save.getTotalKills());
    }

    // ===== 放弃局击杀入账（回归：放弃局 recordPlay 已计总局数，但不经过 onGameEnded，
    // 该局击杀整体丢失——玩家直觉：杀了的怪就该算）=====

    /**
     * 确定性放弃局：进行中（未终态）且已携带击杀。1 波 8 只普通怪、蛋 100 血（漏怪不终局），
     * 塔群覆盖整条路径，守卫式驱动到首次击杀即停——首杀必早于终局（8 只怪远多于首杀前
     * 击杀数，WON 需整波清空；100 血不会被 8 次漏怪打穿），对局必仍处 RUNNING。
     */
    private static TdGame runningGameWithKills() {
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 8, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 100, TdGame.Mode.CAMPAIGN);
        placeKillzoneDefenses(game);
        assertTrue("前置：首波必须成功开战（PREPARING → RUNNING）", game.startNextWaveEarly());
        for (int i = 0; i < 60 * 30 && game.getMonstersKilled() < 1; i++) {
            assertFalse("放弃局构造必须在终态前停止（首杀不该晚于终局）", game.isEnded());
            game.tick();
        }
        assertTrue("前置：放弃局必须已携带击杀", game.getMonstersKilled() >= 1);
        assertFalse("前置：退出时对局必须仍在进行（未走过结算）", game.isEnded());
        return game;
    }

    @Test
    public void abandonedQuitToLevelSelectCountsKillsOnceTowardTotal() throws Exception {
        // 真实退出回调链：对局中点「选关」（btnQuitToMenu → showLevelSelect，无确认框）。
        // 修复前该路径零存档写入：总局数已在开局 recordPlay +1，击杀却整体丢失。
        TdGame game = runningGameWithKills();
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "abandon-host").commitNow();
        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);
        assertEquals("前置：退出前总击杀未入账", 0, save.getTotalKills());

        invokePrivateMethod(fragment, "showLevelSelect");

        assertEquals("放弃局击杀必须在返回选关时计入总击杀（修复前丢失）",
                game.getMonstersKilled(), save.getTotalKills());

        invokePrivateMethod(fragment, "showLevelSelect"); // 选关 → 战绩/图鉴 → 再次返回选关
        assertEquals("同一放弃局多次返回选关不得重复累加（killsRecordedForSession 守卫）",
                game.getMonstersKilled(), save.getTotalKills());
    }

    @Test
    public void abandonedTeardownOnDestroyViewCountsKillsOnceTowardTotal() throws Exception {
        // 退出大厅（宿主确认框 → finish）不走 showLevelSelect，销毁即放弃局：
        // onDestroyView 首次入账；先选关后销毁的复合路径不得双计。
        TdGame game = runningGameWithKills();
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "teardown-host").commitNow();
        setPrivateField(fragment, "overlayRoot", new FrameLayout(activity));
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);

        activity.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();

        assertEquals("销毁路径必须入账放弃局击杀（修复前退出大厅同样丢失）",
                game.getMonstersKilled(), save.getTotalKills());

        // 复合路径：先选关放弃（入账）再销毁 → killsRecordedForSession 防双计
        TdGame game2 = runningGameWithKills();
        StatsHostFragment fragment2 = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment2, "teardown-host-2").commitNow();
        TdSaveManager save2 = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment2, "overlayRoot", new FrameLayout(activity));
        setPrivateField(fragment2, "save", save2);
        setPrivateField(fragment2, "game", game2);
        invokePrivateMethod(fragment2, "showLevelSelect");
        int afterSelect = save2.getTotalKills();
        assertEquals("前置：选关路径已入账", game2.getMonstersKilled(), afterSelect);

        activity.getSupportFragmentManager().beginTransaction().remove(fragment2).commitNow();

        assertEquals("销毁不得与选关路径双计同一局击杀", afterSelect, save2.getTotalKills());
    }

    @Test
    public void settledGameQuitToLevelSelectDoesNotDoubleCountKills() throws Exception {
        // 已结算局（onGameEnded 已走 addKills）再点「选关」（结算面板 menu 按钮同路径）：
        // game != null && !game.isEnded() 闸与 gameEnded 闸双重保证不重复累加。
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 5, TdGame.Mode.CAMPAIGN);
        placeKillzoneDefenses(game);
        driveToEnd(game);
        assertEquals(TdGame.State.WON, game.getState());
        assertEquals(2, game.getMonstersKilled());

        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "settled-host").commitNow();
        FrameLayout overlayRoot = new FrameLayout(activity);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);
        setPrivateField(fragment, "selectedLevelIdx", 0); // WON 分支读 TdLevels.levelIds().get(idx)

        invokePrivateMethod(fragment, "onGameEnded");
        assertEquals("前置：结算路径已计击杀", 2, save.getTotalKills());

        invokePrivateMethod(fragment, "showLevelSelect");
        assertEquals("已结算局返回选关不得重复计击杀", 2, save.getTotalKills());
    }

    @Test
    public void abandonedQuitCrossingKillsThresholdUnlocksSilentlyWithRetentionSemantics()
            throws Exception {
        // 放弃局击杀跨阈值：KILLS_100 在 addKills 写入点即时解锁（存档真源）；
        // 提示按方案 a 静默——选关浮层不得出现成就行；pendingUnlocks 滞留语义钉住：
        // 下一次结算 drainAchievementLine 顺带带出（不复活），战绩面板 sync 补漏对
        // 已解锁键幂等跳过，不产生重复提示。
        TdGame game = runningGameWithKills();
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_kills_total", 99); // 放弃局击杀 ≥1 即跨 KILLS_100 阈值
        TdSaveManager save = new TdSaveManager(prefs);
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "abandon-achv-host").commitNow();
        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);

        invokePrivateMethod(fragment, "showLevelSelect");

        assertEquals("放弃局击杀必须计入总击杀",
                99 + game.getMonstersKilled(), save.getTotalKills());
        assertTrue("放弃局击杀跨阈值必须即时解锁 KILLS_100（存档写入点判定）",
                save.isAchievementUnlocked(TdAchievement.KILLS_100));
        assertNull("放弃路径必须静默：选关浮层不得出现成就提示行（方案 a）",
                findViewByStubText(overlayRoot, R.string.game_td_achv_unlocked,
                        RecordingResources.stub(R.string.game_td_achv_name_kills_100)));

        // 滞留语义 1：下一次结算 drainAchievementLine 把滞留项一次性带出（不复活不补发错时机）
        Method drain = TdModuleFragment.class.getDeclaredMethod("drainAchievementLine");
        drain.setAccessible(true);
        assertEquals("滞留的 KILLS_100 必须由下一次结算提示顺带带出",
                RecordingResources.stub(R.string.game_td_achv_unlocked,
                        RecordingResources.stub(R.string.game_td_achv_name_kills_100)),
                drain.invoke(fragment));

        // 滞留语义 2：战绩面板 sync 补漏对已解锁键幂等跳过——sync 后不再产生重复提示
        save.syncAchievementsFromState();
        assertEquals("sync 补漏不得把已解锁成就重新入列（不复活）", "", drain.invoke(fragment));
    }

    // ===== 结算接线与确定性对局构造辅助 =====

    /**
     * 结算接线壳：注入 overlayRoot/save/game 后反射触发 onGameEnded（与战绩面板同接缝，
     * 屏蔽 onCreateView 的宿主依赖）。selectedLevelIdx 供 WON 分支取关 id，其余分支传 null。
     */
    private static FrameLayout wireAndEndGame(TdGame game, TdSaveManager save,
            Integer selectedLevelIdx) throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "ended-host").commitNow();
        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);
        setPrivateField(fragment, "game", game);
        if (selectedLevelIdx != null) setPrivateField(fragment, "selectedLevelIdx", selectedLevelIdx);
        invokePrivateMethod(fragment, "onGameEnded");
        return overlayRoot;
    }

    /** 确定性小对局（与 TdGameTest.minimalGame 同构）：8×2 地图、单行路径、蛋在 (0,7)、金币 1000。 */
    private static TdGame minimalEndableGame(List<TdGame.Wave> waves, int mascotHp,
            TdGame.Mode mode) {
        int[][] path = new int[8][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        return new TdGame(8, 2, path, 0, 7, 1000, mascotHp, waves).setMode(mode);
    }

    /** 三座瓶子塔（射程 5.5 覆盖整条 8 格路径，普通怪 34 血必被击杀）：保证败局携带击杀。 */
    private static void placeKillzoneDefenses(TdGame game) {
        assertNotNull("布防失败（金币不足或占位非法）", game.placeTower(TowerType.BOTTLE, 1, 2));
        assertNotNull(game.placeTower(TowerType.BOTTLE, 1, 4));
        assertNotNull(game.placeTower(TowerType.BOTTLE, 1, 6));
    }

    /** 推进对局至终态：波次清空即开下一波（镜像 TdGameTest.playThrough 的驱动方式）。 */
    private static void driveToEnd(TdGame game) {
        driveToEnd(game, 60 * 240);
    }

    /**
     * 带显式预算的守卫式驱动：预算耗尽仍未终态则失败，并在断言消息中输出已达波次/击杀，
     * 让「平衡调整把终态推到预算外」的回归直接可诊断。预算只影响驱动时长，不影响判别力——
     * 终态判定仍是 isEnded()。
     */
    private static void driveToEnd(TdGame game, int maxTicks) {
        for (int i = 0; i < maxTicks && !game.isEnded(); i++) {
            game.tick();
            if (!game.isEnded() && !game.isWaveSpawning() && game.getMonsters().isEmpty()) {
                game.startNextWaveEarly();
            }
        }
        assertTrue("对局必须到达终态（预算 " + maxTicks + " tick 内未达：state=" + game.getState()
                        + "，已达波次=" + game.getEndlessWaveReached()
                        + "，击杀=" + game.getMonstersKilled() + "）",
                game.isEnded());
    }

    // ===== achvName 漏 case 拦截：default 兜底回落 saveKey，缺专属 case 在此显式失败 =====

    @Test
    public void achvNameResolvesDedicatedLocalizedCaseForEachAchievement() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "achv-name-host").commitNow();

        Method achvName = TdModuleFragment.class.getDeclaredMethod("achvName", TdAchievement.class);
        achvName.setAccessible(true);

        Set<String> names = new HashSet<>();
        for (TdAchievement a : TdAchievement.values()) {
            String name = (String) achvName.invoke(fragment, a);
            assertFalse("成就 " + a.name() + " 必须有专属本地化 case（不得回落 saveKey="
                    + a.saveKey + "）", a.saveKey.equals(name));
            names.add(name);
        }
        assertEquals("每个成就的本地化名必须互不相同（case 间不得共用/错用资源键）",
                TdAchievement.values().length, names.size());
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

    // ===== 图鉴数据补全：收益周期 / 有效直伤（引擎常量提为 TowerType 数据源后的接线） =====

    @Test
    public void codexIncomeCycleAndDirectDamageRowsPinnedToTowerTypeFields() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "codex-data-host").commitNow();

        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", new TdSaveManager(new MemoryPrefs()));

        invokePrivateMethod(fragment, "showCodexPanel");

        // SUN 收益行：产币周期（TowerType.incomeIntervalSec）+ 单次金额（income），双参数接线
        ViewGroup sunSection = (ViewGroup) findTagged(overlayRoot, "td_codex_tower:SUN");
        assertNotNull("图鉴必须存在太阳花段", sunSection);
        assertTrue("收益行必须展示产币周期与单次产币金额（game_td_codex_value_income）",
                hasTextViewWithText(sunSection, RecordingResources.stub(
                        R.string.game_td_codex_value_income,
                        formatCodexNumber(TowerType.SUN.incomeIntervalSec),
                        Math.round(TowerType.SUN.income))));

        // 直伤塔（系数 < 1）：有效直伤行 = 单发伤害 × directHitMultiplier（SNOW: 8×0.4=3.2）
        ViewGroup snowSection = (ViewGroup) findTagged(overlayRoot, "td_codex_tower:SNOW");
        assertNotNull("图鉴必须存在雪花段", snowSection);
        assertTrue("雪花必须展示有效直伤行（game_td_codex_value_direct_damage）",
                hasTextViewWithText(snowSection, RecordingResources.stub(
                        R.string.game_td_codex_value_direct_damage,
                        formatCodexNumber(TowerType.SNOW.damage),
                        formatCodexNumber(TowerType.SNOW.directHitMultiplier),
                        formatCodexNumber(TowerType.SNOW.damage * TowerType.SNOW.directHitMultiplier))));

        // 全额直伤塔（BOTTLE 系数 = 1）不得出现有效直伤行
        ViewGroup bottleSection = (ViewGroup) findTagged(overlayRoot, "td_codex_tower:BOTTLE");
        assertNotNull("图鉴必须存在瓶炮段", bottleSection);
        assertFalse("全额直伤塔不得展示有效直伤行",
                hasTextViewWithText(bottleSection,
                        RecordingResources.stub(R.string.game_td_codex_label_direct_damage)));

        // POISON 毒 DOT 行：每秒毒伤与持续时间取 TowerType.POISON_DPS/POISON_SEC
        // （与 TdGame 毒伤同源），插在有效直伤行后、仅毒泡泡展示。
        // 实战毒 DOT 按塔等级缩放（TdGame.fire(): POISON_DPS * (0.7f + 0.3f * level)），
        // 成长注记（「随等级成长」/ "scales with level"）由宿主键值尾承载，stub 只钉键与参数
        ViewGroup poisonSection = (ViewGroup) findTagged(overlayRoot, "td_codex_tower:POISON");
        assertNotNull("图鉴必须存在毒泡泡段", poisonSection);
        assertTrue("毒泡泡必须展示毒伤行（game_td_codex_value_poison_dot）",
                hasTextViewWithText(poisonSection, RecordingResources.stub(
                        R.string.game_td_codex_value_poison_dot,
                        formatCodexNumber(TowerType.POISON_DPS),
                        formatCodexNumber(TowerType.POISON_SEC))));
        assertFalse("毒伤行仅 POISON 展示，其他塔不得出现",
                hasTextViewWithText(bottleSection, RecordingResources.stub(
                        R.string.game_td_codex_value_poison_dot,
                        formatCodexNumber(TowerType.POISON_DPS),
                        formatCodexNumber(TowerType.POISON_SEC))));
        // 毒伤行走特性行插桩（tag=td_codex_trait），冒烟按首个 tag 命中会先读到毒伤行；
        // 此处补钉真特性行，防止特性行 switch 漏 case 的守卫被毒伤行架空
        assertTrue("毒泡泡特性行必须非空（game_td_codex_trait_tower_poison）",
                hasTextViewWithText(poisonSection,
                        RecordingResources.stub(R.string.game_td_codex_trait_tower_poison)));
    }

    // ===== 真实选塔的目标模式：狙击固定强敌，普通攻击塔仍可循环 =====

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void sniperTowerOpsShowsFixedStrongTargetAndRejectsUserCycling() throws Exception {
        assertTargetModeControlsThroughRealSelection(true);
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void ordinaryTowerOpsRestoresTargetCyclingAfterSelectingSniper() throws Exception {
        assertTargetModeControlsThroughRealSelection(false);
    }

    private void assertTargetModeControlsThroughRealSelection(boolean verifySniper) throws Exception {
        ActivityController<PanelHostActivity> controller =
                Robolectric.buildActivity(PanelHostActivity.class).setup();
        PanelHostActivity activity = controller.get();
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "target-mode-host").commitNow();
        try {
            // A saved three-level completion unlocks the actual fourth-level sniper card.
            // The live game still uses production NORMAL resources, deck selection and taps.
            TdSaveManager save = new TdSaveManager(new MemoryPrefs());
            for (int level = 0; level < 3; level++) save.recordWin(TdLevels.levelIds().get(level));
            assertEquals(4, save.getUnlockedLevelCount());
            setPrivateField(fragment, "save", save);
            ViewGroup ui = (ViewGroup) invokePrivateMethod(fragment, "buildUi", new Class<?>[0]);
            ((ViewGroup) fragment.requireView()).addView(ui);
            invokePrivateMethod(fragment, "showDeckSelect",
                    new Class<?>[] {int.class, TdGame.Difficulty.class, boolean.class},
                    3, TdGame.Difficulty.NORMAL, false);
            View deckSniper = findTargetModeCardByDescription(ui, RecordingResources.stub(
                    R.string.game_td_cd_unselected, RecordingResources.stub(R.string.game_td_tower_sniper)));
            assertNotNull("The unlocked sniper must be selectable in the real deck", deckSniper);
            assertTrue(deckSniper.isEnabled());
            assertTrue(deckSniper.performClick());
            clickMergeFlowButton(ui, R.string.game_td_btn_start_level);
            clickMergeFlowButton(ui, R.string.game_td_story_start);
            TdGame game = (TdGame) readFragmentField(fragment, "game");
            TdView board = (TdView) readFragmentField(fragment, "tdView");
            assertEquals(TdGame.State.PREPARING, game.getState());
            assertEquals(TdGame.Difficulty.NORMAL, game.getDifficulty());
            int coinBefore = game.getCoin();
            assertTrue("The real fourth-level budget must cover these two towers",
                    coinBefore >= TowerType.SNIPER.baseCost + TowerType.BOTTLE.baseCost);
            List<int[]> cells = new ArrayList<>();
            for (int row = 0; row < game.getRows() && cells.size() < 2; row++) {
                for (int col = 0; col < game.getCols() && cells.size() < 2; col++) {
                    if (!game.isPathCell(row, col) && !game.isEggCell(row, col)) {
                        cells.add(new int[]{row, col});
                    }
                }
            }
            assertEquals(2, cells.size());
            TowerType[] types = {TowerType.SNIPER, TowerType.BOTTLE};
            for (int index = 0; index < types.length; index++) {
                View paletteCard = ui.findViewWithTag(types[index]);
                assertNotNull("The active deck must expose the real palette card", paletteCard);
                assertEquals(View.VISIBLE, paletteCard.getVisibility());
                assertTrue(paletteCard.performClick());
                tapBoardCellForMergeFlow(board, cells.get(index)[0], cells.get(index)[1]);
                assertNotNull(game.getTowerAt(cells.get(index)[0], cells.get(index)[1]));
                assertEquals(types[index], game.getTowerAt(cells.get(index)[0], cells.get(index)[1]).type);
            }
            assertEquals(coinBefore - TowerType.SNIPER.baseCost - TowerType.BOTTLE.baseCost, game.getCoin());
            TdGame.Tower sniper = game.getTowerAt(cells.get(0)[0], cells.get(0)[1]);
            TdGame.Tower bottle = game.getTowerAt(cells.get(1)[0], cells.get(1)[1]);
            tapBoardCellForMergeFlow(board, sniper.row, sniper.col);
            assertSame("Tower operations must be reached through actual board selection", sniper, board.getHoverTower());
            assertEquals(View.VISIBLE, ((View) readFragmentField(fragment, "towerOpsBar")).getVisibility());
            Button target = (Button) readFragmentField(fragment, "btnTarget");

            if (verifySniper) {
                String strongText = RecordingResources.stub(R.string.game_td_btn_target_mode,
                        RecordingResources.stub(R.string.game_td_target_strong));
                assertEquals("Sniper UI must report the STRONG rule actually used by acquireTarget",
                        strongText, target.getText().toString());
                assertFalse("A fixed-target sniper must not offer a misleading target-mode switch", target.isEnabled());
                TdGame.TargetMode before = sniper.targetMode;
                // Disabled user input must not become a mode change. performClick would
                // bypass Android's enabled-state handling, so send actual touch events here.
                target.layout(0, 0, 240, 80);
                MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 120f, 40f, 0);
                MotionEvent up = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_UP, 120f, 40f, 0);
                try {
                    target.dispatchTouchEvent(down);
                    target.dispatchTouchEvent(up);
                } finally {
                    down.recycle();
                    up.recycle();
                }
                assertEquals("Touching the disabled control cannot mutate the sniper's stored mode", before, sniper.targetMode);
                assertEquals(strongText, target.getText().toString());
            } else {
                tapBoardCellForMergeFlow(board, bottle.row, bottle.col);
                assertSame(bottle, board.getHoverTower());
                assertTrue("Selecting an ordinary tower after a sniper must restore mode switching", target.isEnabled());
                assertEquals(TdGame.TargetMode.FIRST, bottle.targetMode);
                assertEquals(RecordingResources.stub(R.string.game_td_btn_target_mode,
                        RecordingResources.stub(R.string.game_td_target_first)), target.getText().toString());
                TdGame.TargetMode[] modes = {TdGame.TargetMode.STRONG, TdGame.TargetMode.WEAK, TdGame.TargetMode.FIRST};
                int[] names = {R.string.game_td_target_strong, R.string.game_td_target_weak, R.string.game_td_target_first};
                for (int index = 0; index < modes.length; index++) {
                    assertTrue("The real target Button must remain enabled for an ordinary tower", target.isEnabled());
                    assertTrue(target.performClick());
                    assertEquals("The ordinary tower's engine mode must cycle", modes[index], bottle.targetMode);
                    assertEquals("The operation bar must report each actual selected mode",
                            RecordingResources.stub(R.string.game_td_btn_target_mode, RecordingResources.stub(names[index])),
                            target.getText().toString());
                }
            }
            assertEquals("Selecting a target mode cannot change tower count", 2, game.getTowers().size());
            assertEquals("Selecting a target mode cannot spend coins",
                    coinBefore - TowerType.SNIPER.baseCost - TowerType.BOTTLE.baseCost, game.getCoin());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static View findTargetModeCardByDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findTargetModeCardByDescription(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ===== 塔升级预览（showTowerOps）：下一级数值 / 满级 / 增幅加成注记 / 经济与光环塔专属口径 =====

    @Test
    public void towerOpsUpgradePreviewPinsNextLevelValuesAndMaxLevel() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "preview-host").commitNow();

        // 与战绩/图鉴面板同接缝：onCreateView 被屏蔽，操作条由 buildTowerOps 单独构建，
        // 对局状态（game）显式注入后再反射触发 showTowerOps
        ViewGroup opsBar = (ViewGroup) invokePrivateMethod(fragment, "buildTowerOps",
                new Class<?>[] {Context.class}, activity);

        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 5, TdGame.Mode.CAMPAIGN);
        assertNotNull(game.placeTower(TowerType.BOTTLE, 1, 2));
        setPrivateField(fragment, "game", game);
        TdGame.Tower tower = game.getTowers().get(0);

        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class}, tower);

        TextView preview = (TextView) findTagged(opsBar, "td_upgrade_preview");
        assertNotNull("塔操作条必须包含升级预览行（tag=td_upgrade_preview）", preview);
        int next = tower.level + 1;
        assertEquals("升级预览必须钉住下一级数值且不标虚构金币数（资源键+参数+赋值结果三元组；"
                        + "真实升级路径为拖拽合成/点选合成，均不按 upgradeCost 扣金）",
                RecordingResources.stub(R.string.game_td_upgrade_preview_next, next,
                        formatCodexNumber(TowerType.BOTTLE.damageAt(next)),
                        formatCodexNumber(TowerType.BOTTLE.rangeAt(next)),
                        formatCodexNumber(TowerType.BOTTLE.fireIntervalAt(next))),
                preview.getText().toString());

        // 满级：预览行回落「已满级」
        tower.level = 3;
        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class}, tower);
        assertEquals("满级塔预览行必须提示已满级",
                RecordingResources.stub(R.string.game_td_upgrade_preview_max),
                preview.getText().toString());
    }

    @Test
    public void towerOpsUpgradePreviewAppendsAmplifierNoteOnlyWhenAmplifierInRange() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "preview-note-host").commitNow();

        ViewGroup opsBar = (ViewGroup) invokePrivateMethod(fragment, "buildTowerOps",
                new Class<?>[] {Context.class}, activity);

        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 5, TdGame.Mode.CAMPAIGN);
        assertNotNull(game.placeTower(TowerType.BOTTLE, 1, 2));
        // 增幅塔在射程内（塔心距 1 ≤ 光环半径 2.35；Lv1 攻速加成 10% > 0）
        assertNotNull(game.placeTower(TowerType.AMPLIFIER, 1, 3));
        setPrivateField(fragment, "game", game);
        TdGame.Tower tower = game.getTowers().get(0);
        assertTrue("前置：增幅塔必须对目标塔生效",
                game.getAttackSpeedBonus(tower) > 0f || game.getRangeBonus(tower) > 0f);

        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class}, tower);

        TextView preview = (TextView) findTagged(opsBar, "td_upgrade_preview");
        assertNotNull("塔操作条必须包含升级预览行（tag=td_upgrade_preview）", preview);
        assertEquals("增幅塔在射程内时预览行必须追加基础值注记（面板不展示加成数值）",
                RecordingResources.stub(R.string.game_td_upgrade_preview_next, 2,
                        formatCodexNumber(TowerType.BOTTLE.damageAt(2)),
                        formatCodexNumber(TowerType.BOTTLE.rangeAt(2)),
                        formatCodexNumber(TowerType.BOTTLE.fireIntervalAt(2)))
                        + RecordingResources.stub(R.string.game_td_upgrade_preview_amplified_note),
                preview.getText().toString());
    }

    @Test
    public void towerOpsUpgradePreviewPinsSunIncomeAndAmplifierAuraRows() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "preview-economy-host").commitNow();

        ViewGroup opsBar = (ViewGroup) invokePrivateMethod(fragment, "buildTowerOps",
                new Class<?>[] {Context.class}, activity);

        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame game = minimalEndableGame(waves, 5, TdGame.Mode.CAMPAIGN);
        assertNotNull(game.placeTower(TowerType.SUN, 1, 2));
        // 增幅塔贴身摆放仍不得改变下述断言：引擎对 SUN/AMPLIFIER 恒不提供光环
        // （strongestAmplifierFor 直接排除这两类目标），攻击塔分支的 amplified 注记不适用
        assertNotNull(game.placeTower(TowerType.AMPLIFIER, 1, 3));
        setPrivateField(fragment, "game", game);

        // 太阳花：预览改示下一级单次产币金额 incomeAt(n+1)（取整口径与图鉴收益行一致），
        // 不再展示恒为 0 的伤害/攻速；整行等值断言同时钉住攻击塔口径不误入经济塔预览
        TdGame.Tower sun = game.getTowers().get(0);
        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class}, sun);
        TextView preview = (TextView) findTagged(opsBar, "td_upgrade_preview");
        assertNotNull("塔操作条必须包含升级预览行（tag=td_upgrade_preview）", preview);
        assertEquals("太阳花升级预览必须钉住 game_td_upgrade_preview_sun 与 incomeAt(2)",
                RecordingResources.stub(R.string.game_td_upgrade_preview_sun, 2,
                        Math.round(TowerType.SUN.incomeAt(2))),
                preview.getText().toString());
        sun.level = 2;
        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class}, sun);
        assertEquals("太阳花 Lv2→Lv3 预览必须按 incomeAt(3) 成长",
                RecordingResources.stub(R.string.game_td_upgrade_preview_sun, 3,
                        Math.round(TowerType.SUN.incomeAt(3))),
                preview.getText().toString());

        // 增幅塔：预览改示下一级光环攻速/射程加成（×100 取整为百分数）
        TdGame.Tower amplifier = game.getTowers().get(1);
        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class},
                amplifier);
        assertEquals("增幅塔升级预览必须钉住 game_td_upgrade_preview_amplifier 与光环加成百分数",
                RecordingResources.stub(R.string.game_td_upgrade_preview_amplifier, 2,
                        Math.round(TowerType.AMPLIFIER.amplifierAttackSpeedBonusAt(2) * 100),
                        Math.round(TowerType.AMPLIFIER.amplifierRangeBonusAt(2) * 100)),
                preview.getText().toString());

        // 满级回落「已满级」：经济/光环塔与攻击塔同口径
        amplifier.level = 3;
        invokePrivateMethod(fragment, "showTowerOps", new Class<?>[] {TdGame.Tower.class},
                amplifier);
        assertEquals("满级增幅塔预览行必须提示已满级",
                RecordingResources.stub(R.string.game_td_upgrade_preview_max),
                preview.getText().toString());
    }

    // ===== 选关面板（按章节分组）：章头顺序 / 本地化章节名 / 每章卡片数与滚动顺序 =====

    @Test
    public void levelSelectGroupsLevelsByChapterInOrder() throws Exception {
        FragmentActivity activity = Robolectric.setupActivity(PanelHostActivity.class);
        StatsHostFragment fragment = new StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "level-select-host").commitNow();

        // 进度口径见证：main_001（第 1 章）与 main_015（第 2 章）有星 → 前两章各「已通 1」，
        // 其余章「已通 0」；星数非零即已通，与战绩面板 countCleared 同口径
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.setBestStars("main_001", 3);
        save.setBestStars("main_015", 2);

        FrameLayout overlayRoot = new FrameLayout(activity);
        setPrivateField(fragment, "overlayRoot", overlayRoot);
        setPrivateField(fragment, "save", save);

        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                readAsset(findAssetsRoot().resolve("td/manifest.json")));
        // 章节数随战役扩展动态增长（不写死），两组断言都以 manifest 为唯一真相源
        int expectedChapters = manifest.chapters.size();
        assertTrue("前置：manifest 至少注册 4 个章节（含第 5 章）", expectedChapters >= 4);

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            invokePrivateMethod(fragment, "showLevelSelect");

            List<View> sections = collectViewsByTagPrefix(overlayRoot, "td_chapter:");
            assertEquals("选关面板必须按章节分段（每章一个 section）", expectedChapters, sections.size());
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

    /** 按包内约定反射读取 TdView 私有 String 字段（不改生产可见性，与 bannerText 同款）。 */
    private static String privateStringField(TdView view, String name) throws Exception {
        Field field = TdView.class.getDeclaredField(name);
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

    /** 带参反射调用（buildTowerOps/showTowerOps 等需要参数的私有方法），返回方法结果。 */
    private static Object invokePrivateMethod(Object target, String name,
            Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = TdModuleFragment.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
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
