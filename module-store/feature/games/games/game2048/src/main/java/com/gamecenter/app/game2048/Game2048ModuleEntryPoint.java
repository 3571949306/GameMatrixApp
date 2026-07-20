package com.gamecenter.app.game2048;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;

import java.util.Arrays;
import java.util.List;

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

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new Game2048NavContribution());
    }

    private static class Game2048NavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "game_2048"; }

        @Override
        public String getTitle(Context context) { return "2048"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 100; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new Game2048Fragment(); }
    }
}
