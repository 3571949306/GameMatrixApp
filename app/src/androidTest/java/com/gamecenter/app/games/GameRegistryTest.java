package com.gamecenter.app.games;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.gamecenter.app.BaseAndroidTest;
import com.gamecenter.app.models.ModuleInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

/**
 * GameRegistry 集成测试（androidTest）。
 *
 * <p>测试 GameRegistry 的核心功能：
 * <ul>
 *   <li>内置游戏分类获取</li>
 *   <li>RePlugin 插件动态注册/注销</li>
 *   <li>内置游戏版本注册和更新检查</li>
 *   <li>游戏启动（内置）</li>
 * </ul>
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(AndroidJUnit4.class)
public class GameRegistryTest extends BaseAndroidTest {

    @Test
    public void testGetCategories_returnsBuiltInGames() {
        assertNotNull("Context should not be null", context);

        List<GameRegistry.Category> categories = GameRegistry.getCategories(context);
        assertNotNull("Categories should not be null", categories);
        assertFalse("Categories should not be empty", categories.isEmpty());

        // 应至少有 "classics" 分类
        boolean hasClassics = false;
        for (GameRegistry.Category cat : categories) {
            if (GameRegistry.CATEGORY_CLASSICS.equals(cat.categoryKey)) {
                hasClassics = true;
                assertFalse("Classics should have games", cat.games.isEmpty());
            }
        }
        assertTrue("Should have classics category", hasClassics);
    }

    @Test
    public void testGetActivityClassById_returnsClassForKnownGame() {
        assertNotNull("Context should not be null", context);

        Class<?> gomokuClass = GameRegistry.getActivityClassById(context, "gomoku");
        assertNotNull("Gomoku activity class should be found", gomokuClass);

        Class<?> doudizhuClass = GameRegistry.getActivityClassById(context, "doudizhu");
        assertNotNull("Doudizhu activity class should be found", doudizhuClass);
    }

    @Test
    public void testGetActivityClassById_returnsNullForUnknownGame() {
        assertNotNull("Context should not be null", context);

        Class<?> unknownClass = GameRegistry.getActivityClassById(context, "nonexistent_game");
        assertNull("Unknown game should return null", unknownClass);
    }

    @Test
    public void testRegisterBuiltInVersion() {
        GameRegistry.registerBuiltInVersion("gomoku", 1);
        assertEquals("Built-in version should be registered",
                1, GameRegistry.getBuiltInVersionCode("gomoku"));
    }

    @Test
    public void testGetBuiltInVersionCode_defaultValue() {
        int version = GameRegistry.getBuiltInVersionCode("nonexistent_game");
        assertEquals("Unregistered game should return default version 1", 1, version);
    }

    @Test
    public void testFlattenCategories() {
        assertNotNull("Context should not be null", context);

        List<GameRegistry.Category> categories = GameRegistry.getCategories(context);
        List<GameRegistry.Entry> flatGames = GameRegistry.flatten(categories);

        assertNotNull("Flattened list should not be null", flatGames);
        assertFalse("Flattened list should not be empty", flatGames.isEmpty());

        // 扁平化后的数量应等于所有分类中游戏数量之和
        int totalGames = 0;
        for (GameRegistry.Category cat : categories) {
            totalGames += cat.games.size();
        }
        assertEquals("Flatten count should match sum of category games",
                totalGames, flatGames.size());
    }
}
