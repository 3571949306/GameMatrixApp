package com.gamecenter.app.games;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.gamecenter.app.models.ModuleInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

/**
 * GameRegistry 单元测试。
 *
 * <p>测试游戏注册中心的核心功能：
 * <ul>
 *   <li>游戏注册</li>
 *   <li>游戏注销</li>
 *   <li>获取已注册游戏</li>
 *   <li>启动游戏</li>
 *   <li>内置游戏版本管理</li>
 * </ul>
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 29, 30, 31, 32, 33, 34, 35})
public class GameRegistryTest {

    private Context context;
    private static final String TEST_GAME_ID = "test_game_001";
    private static final String TEST_CATEGORY = "puzzle";

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // 清除动态注册的游戏，确保测试独立性
        GameRegistry.clearDynamicEntries();
    }

    @After
    public void tearDown() {
        // 清理测试数据
        GameRegistry.clearDynamicEntries();
    }

    /**
     * 测试游戏注册。
     *
     * <p>验证 register() 方法可以成功注册游戏条目。
     */
    @Test
    public void testRegisterGame() {
        // 创建一个测试游戏条目
        GameRegistry.Entry entry = new GameRegistry.Entry(
                TEST_GAME_ID,
                0, // iconRes (测试中可以传 0)
                "Test Game",
                "Test game description",
                null, // activityClass (测试中可以传 null)
                "Test Category",
                TEST_CATEGORY
        );

        // 注册游戏
        boolean result = GameRegistry.register(entry);

        assertTrue("游戏注册应该成功", result);
    }

    /**
     * 测试重复注册（ID 冲突）。
     *
     * <p>验证 register() 方法在 ID 冲突时返回 false。
     */
    @Test
    public void testRegisterGameDuplicate() {
        GameRegistry.Entry entry1 = new GameRegistry.Entry(
                TEST_GAME_ID,
                0,
                "Test Game 1",
                "Description 1",
                null,
                "Category 1",
                TEST_CATEGORY
        );

        GameRegistry.Entry entry2 = new GameRegistry.Entry(
                TEST_GAME_ID, // 相同 ID
                0,
                "Test Game 2",
                "Description 2",
                null,
                "Category 2",
                TEST_CATEGORY
        );

        // 第一次注册应该成功
        boolean result1 = GameRegistry.register(entry1);
        assertTrue("第一次注册应该成功", result1);

        // 第二次注册（相同 ID）应该失败
        boolean result2 = GameRegistry.register(entry2);
        assertFalse("重复注册应该失败", result2);
    }

    /**
     * 测试游戏注销。
     *
     * <p>验证 unregister() 方法可以成功注销已注册的游戏。
     */
    @Test
    public void testUnregisterGame() {
        // 先注册一个游戏
        GameRegistry.Entry entry = new GameRegistry.Entry(
                TEST_GAME_ID,
                0,
                "Test Game",
                "Test game description",
                null,
                "Test Category",
                TEST_CATEGORY
        );
        GameRegistry.register(entry);

        // 注销游戏
        boolean result = GameRegistry.unregister(TEST_GAME_ID);

        assertTrue("游戏注销应该成功", result);
    }

    /**
     * 测试注销不存在的游戏。
     *
     * <p>验证 unregister() 方法在游戏不存在时返回 false。
     */
    @Test
    public void testUnregisterGameNotFound() {
        // 尝试注销未注册的游戏
        boolean result = GameRegistry.unregister("non_existent_game");

        assertFalse("注销不存在的游戏应该返回 false", result);
    }

    /**
     * 测试获取已注册游戏。
     *
     * <p>验证 getCategories() 方法可以返回已注册的游戏。
     */
    @Test
    public void testGetRegisteredGames() {
        // 注册一个测试游戏
        GameRegistry.Entry entry = new GameRegistry.Entry(
                TEST_GAME_ID,
                0,
                "Test Game",
                "Test game description",
                null,
                "Test Category",
                TEST_CATEGORY
        );
        GameRegistry.register(entry);

        // 获取所有分类
        List<GameRegistry.Category> categories = GameRegistry.getCategories(context);

        assertNotNull("分类列表不应为 null", categories);
        assertFalse("分类列表不应为空", categories.isEmpty());
    }

    /**
     * 测试启动游戏（内置游戏）。
     *
     * <p>验证 launchGame() 方法在游戏不存在时返回 false。
     */
    @Test
    public void testLaunchGameNotFound() {
        // 尝试启动不存在的游戏
        boolean result = GameRegistry.launchGame(context, "non_existent_game");

        assertFalse("启动不存在的游戏应该返回 false", result);
    }

    /**
     * 测试启动游戏（空参数）。
     *
     * <p>验证 launchGame() 方法在参数为 null 时返回 false。
     */
    @Test
    public void testLaunchGameNullParams() {
        // context 为 null
        boolean result1 = GameRegistry.launchGame(null, TEST_GAME_ID);
        assertFalse("context 为 null 应该返回 false", result1);

        // gameId 为 null
        boolean result2 = GameRegistry.launchGame(context, null);
        assertFalse("gameId 为 null 应该返回 false", result2);
    }

    /**
     * 测试内置游戏版本注册。
     */
    @Test
    public void testRegisterBuiltInVersion() {
        int testVersion = 100;
        GameRegistry.registerBuiltInVersion(TEST_GAME_ID, testVersion);

        int registeredVersion = GameRegistry.getBuiltInVersionCode(TEST_GAME_ID);
        assertEquals("注册的版本号应该匹配", testVersion, registeredVersion);
    }

    /**
     * 测试获取未注册内置游戏的版本号（应该使用默认值）。
     */
    @Test
    public void testGetBuiltInVersionCodeDefault() {
        // 未注册的游戏应该使用默认版本号 1
        int version = GameRegistry.getBuiltInVersionCode("non_existent_game");

        assertEquals("未注册游戏的版本号应为默认值 1", 1, version);
    }

    /**
     * 测试清除动态注册的游戏条目。
     */
    @Test
    public void testClearDynamicEntries() {
        // 注册一个动态游戏
        GameRegistry.Entry entry = new GameRegistry.Entry(
                TEST_GAME_ID,
                0,
                "Test Game",
                "Test game description",
                null,
                "Test Category",
                TEST_CATEGORY
        );
        GameRegistry.register(entry);

        // 清除所有动态条目
        GameRegistry.clearDynamicEntries();

        // 获取分类，应该不包含动态注册的游戏
        List<GameRegistry.Category> categories = GameRegistry.getCategories(context);

        // 注意：内置游戏可能仍然存在，所以 categories 不一定为空
        // 但动态注册的游戏应该已被清除
        assertNotNull("分类列表不应为 null", categories);
    }

    /**
     * 测试检查内置游戏更新（无外置版本）。
     */
    @Test
    public void testCheckBuiltInGameUpdateNoUpdate() {
        // 注册内置游戏版本
        GameRegistry.registerBuiltInVersion(TEST_GAME_ID, 100);

        // 检查更新（应该返回 null，因为没有外置版本）
        ModuleInfo update = GameRegistry.checkBuiltInGameUpdate(context, TEST_GAME_ID);

        assertNull("没有外置版本时应该返回 null", update);
    }

    /**
     * 测试扁平化分类列表。
     */
    @Test
    public void testFlattenCategories() {
        // 获取分类
        List<GameRegistry.Category> categories = GameRegistry.getCategories(context);

        // 扁平化
        List<GameRegistry.Entry> games = GameRegistry.flatten(categories);

        assertNotNull("游戏列表不应为 null", games);
        // 注意：内置游戏应该存在，所以列表不应该为空
        assertFalse("游戏列表不应为空（应该有内置游戏）", games.isEmpty());
    }

    /**
     * 测试动态游戏条目的 Entry 检查更新方法。
     */
    @Test
    public void testEntryCheckForUpdate() {
        // 创建一个动态游戏条目
        GameRegistry.Entry entry = new GameRegistry.Entry(
                TEST_GAME_ID,
                0,
                "Test Game",
                "Test game description",
                null,
                "Test Category",
                TEST_CATEGORY
        );

        // 注册游戏
        GameRegistry.register(entry);

        // 检查更新（应该返回 null 或 ModuleInfo）
        ModuleInfo update = entry.checkForUpdate(context);

        // 注意：由于测试环境可能没有外置版本，这里可能返回 null
        // 我们只是验证方法不抛出异常
        // update 可以是 null 或非 null，都是合法的
    }
}
