package com.gamecenter.app.chinesechess;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;

public class ChineseChessModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "chinesechess";
    }

    @Override
    public String getName() {
        return "中国象棋";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public String getDescription() {
        return "经典中国象棋对弈游戏，支持单机与联机入口。";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Fragment createFragment(Context context) {
        return new ChineseChessModuleFragment();
    }
}
