package com.gamecenter.app.tts;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;

import java.util.Arrays;
import java.util.List;

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
        // 注意：不在 GameRegistry 中注册 Activity 类 ——
        // 动态模块通过 DexClassLoader 加载的 Activity 无法被 Android 系统直接启动。
        // TTS 使用 Fragment 模式，由 DynamicGameActivity 承载。
    }

    @Override
    public void stop() {
        running = false;
        // 无需反注册（未注册到 GameRegistry）
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
    public Fragment createFragment(Context context) { return new TtsFragment(); }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new TtsNavContribution());
    }

    private static class TtsNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "tts_voice"; }

        @Override
        public String getTitle(Context context) { return "语音合成"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 130; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new TtsFragment(); }
    }
}
