package com.gamecenter.app.games.config;

import android.content.Context;

import com.gamecenter.app.games.model.GameConfig;

import java.util.Collections;
import java.util.List;

/**
 * 游戏成就配置加载器（占位实现）。
 *
 * <p>P0 修复：将原 :module-store:feature:games:games 模块中的 GameConfigLoader 迁回 app 模块。
 * 当前实现返回空列表，成就中心将显示"暂无成就"。
 * 后续可在此处加载 assets 中的 JSON 配置文件以恢复完整功能。</p>
 */
public class GameConfigLoader {

    @SuppressWarnings("unused")
    public GameConfigLoader(Context context) {
    }

    public List<GameConfig> loadAllConfigs() {
        return Collections.emptyList();
    }
}