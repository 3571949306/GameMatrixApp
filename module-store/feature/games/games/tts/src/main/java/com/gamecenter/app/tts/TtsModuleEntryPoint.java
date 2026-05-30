package com.gamecenter.app.tts;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.games.GameRegistry;

/**
 * TTS 语音合成模块入口（供模块商店和游戏大厅识别）。
 */
public class TtsModuleEntryPoint implements ModuleInterface, FeatureModule {

    private boolean running;

    @Override
    public void init(Context context) {}

    @Override
    public void start(Context context) {
        running = true;
        // 在游戏大厅中注册入口卡片（category_key = "casual"归入休闲分类）
        try {
            GameRegistry.register(new GameRegistry.Entry(
                    "tts_voice",
                    0,                  // iconRes = 0，用代码绘制图标
                    "语音合成实验室",
                    "小米 MiMo TTS · 文字转语音 · 声音克隆",
                    TtsActivity.class,
                    "工具",
                    GameRegistry.CATEGORY_CASUAL
            ));
        } catch (Exception ignored) {
            // 已注册过则忽略
        }
    }

    @Override
    public void stop() {
        running = false;
        try { GameRegistry.unregister("tts_voice"); } catch (Exception ignored) {}
    }

    @Override
    public String getId() { return "tts_voice"; }

    @Override
    public String getName() { return "语音合成实验室"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "小米 MiMo TTS · 语音克隆 · 多音色"; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public Fragment createFragment(Context context) { return null; }
}
