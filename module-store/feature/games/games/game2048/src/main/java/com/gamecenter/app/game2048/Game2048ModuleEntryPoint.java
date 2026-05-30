package com.gamecenter.app.game2048;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;

public class Game2048ModuleEntryPoint implements ModuleInterface, FeatureModule {

    private boolean running;

    @Override
    public void init(Context context) {}

    @Override
    public void start(Context context) {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public String getId() {
        return "game_2048";
    }

    @Override
    public String getName() {
        return "2048";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "经典2048数字合并游戏";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Fragment createFragment(Context context) {
        return new Game2048Fragment();
    }
}
