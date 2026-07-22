package com.gamecenter.app.games;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;
import com.gamecenter.app.fragments.GamesFragment;

import java.util.Arrays;
import java.util.List;

public class GamesHallModuleEntryPoint implements ModuleInterface, FeatureModule {

    private boolean running = false;

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
        return "games_hall";
    }

    @Override
    public String getName() {
        return "游戏大厅";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "聚合各种精美游戏的模块化大厅。";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public UnityModuleLauncher createUnityLauncher() {
        return null;
    }

    @Override
    public boolean shouldPreload() {
        return false;
    }

    @Override
    public Fragment createFragment(Context context) {
        return new GamesFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new GamesHallNavContribution());
    }

    private static class GamesHallNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "games_hall"; }

        @Override
        public String getTitle(Context context) { return "游戏大厅"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 10; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.BOTTOM_NAV; }

        @Override
        public Fragment createFragment(Context context) { return new GamesFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}
