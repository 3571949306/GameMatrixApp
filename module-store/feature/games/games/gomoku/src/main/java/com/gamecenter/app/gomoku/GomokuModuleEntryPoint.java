package com.gamecenter.app.gomoku;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.List;

public class GomokuModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "gomoku";
    }

    @Override
    public String getName() {
        return "五子棋";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "经典五子棋人机对战，4级AI难度，支持悔棋、提示、禁手判定";
    }

    @Override
    public String getModuleType() {
        return "game";
    }

    @Override
    public List<String> getRequiredPermissions() {
        return java.util.Collections.emptyList();
    }

    @Override
    public List<String> getDependencies() {
        return java.util.Collections.emptyList();
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
        return new GomokuModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new GomokuNavContribution());
    }

    private static class GomokuNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "gomoku"; }

        @Override
        public String getTitle(Context context) { return "五子棋"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 160; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new GomokuModuleFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}
