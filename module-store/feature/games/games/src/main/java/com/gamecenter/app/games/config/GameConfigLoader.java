package com.gamecenter.app.games.config;

import android.content.Context;

import com.gamecenter.app.games.model.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏配置加载器
 * <p>
 * 从 JSON 配置文件加载各游戏的配置数据。
 * </p>
 */
public class GameConfigLoader {

    private final Context context;

    public GameConfigLoader(Context context) {
        this.context = context;
    }

    /**
     * 加载所有游戏的配置
     */
    public List<GameConfig> loadAllConfigs() {
        // 暂时返回空列表，后续接入 JSON 配置文件
        return new ArrayList<>();
    }

    /**
     * 加载指定游戏的配置
     */
    public GameConfig loadConfig(String gameId) {
        return new GameConfig(gameId, gameId);
    }
}
