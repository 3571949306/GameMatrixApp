package com.gamecenter.app.games.config;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.gamecenter.app.R;
import com.gamecenter.app.games.model.GameConfig;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 游戏成就配置加载器。
 *
 * <p>P0 修复：将原 :module-store:feature:games:games 模块中的 GameConfigLoader 迁回 app 模块。</p>
 *
 * <p>BUG-004 修复：之前 {@link #loadAllConfigs()} 直接返回 {@link Collections#emptyList()}，
 * 导致 {@link com.gamecenter.app.games.achievement.AchievementCenterActivity} 的 RecyclerView
 * 拿不到任何 {@link GameConfig}，最终顶部汇总显示 "0 / 0"，但每个游戏卡片却因为
 * GameRegistry 仍能枚举游戏而提示"可解锁"——出现"0/0 但显示可解锁"的不一致。
 * 现从 {@code R.raw.game_configs} 读取 27 个游戏的成就定义，并通过 Gson 反序列化
 * 为 {@link GameConfig} 列表，使成就中心能正确展示每个游戏的成就数量与解锁状态。</p>
 *
 * <p>容错策略：
 * <ul>
 *   <li>读取或解析失败时记录 ERROR 日志并返回空列表，避免让进程崩溃</li>
 *   <li>Gson 默认忽略 JSON 中存在但 Java 模型未声明的字段（如 difficultyLevels、
 *       tutorialSteps 等），因此 {@link GameConfig} 仅声明成就中心实际使用的字段即可</li>
 *   <li>每条 {@link GameConfig} 的 achievements 字段若为空/null，调用方
 *       ({@link AchievementCenterActivity}) 已在循环中跳过，无需在此二次过滤</li>
 * </ul>
 * </p>
 */
public class GameConfigLoader {

    private static final String TAG = "GameConfigLoader";

    private final Context context;
    private final Gson gson = new Gson();

    public GameConfigLoader(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 加载全部游戏成就配置。
     *
     * @return 配置列表；读取或解析失败时返回空列表（不返回 null）
     */
    public List<GameConfig> loadAllConfigs() {
        return loadFromRawResource(R.raw.game_configs);
    }

    /**
     * 从指定 raw 资源中读取并解析游戏配置列表。
     * 拆分为独立方法便于单元测试 mock。
     */
    @NonNull
    private List<GameConfig> loadFromRawResource(@RawRes int rawResId) {
        try (InputStream is = context.getResources().openRawResource(rawResId);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            Type listType = new TypeToken<List<GameConfig>>() {}.getType();
            List<GameConfig> configs = gson.fromJson(reader, listType);
            if (configs == null) {
                Log.w(TAG, "game_configs.json 解析结果为 null，返回空列表");
                return Collections.emptyList();
            }
            return configs;
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "game_configs.json 格式错误，解析失败", e);
            return Collections.emptyList();
        } catch (IOException e) {
            Log.e(TAG, "读取 game_configs.json 失败", e);
            return Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "加载游戏配置时出现未预期异常", e);
            return Collections.emptyList();
        }
    }
}
