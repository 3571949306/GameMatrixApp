package com.gamecenter.app.tic;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.List;

public class TicTacToeModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "tic";
    }

    @Override
    public String getName() {
        return "井字棋";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "经典井字棋人机对战";
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
        return new TicTacToeModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new TicTacToeNavContribution());
    }

    private static class TicTacToeNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "tic"; }

        @Override
        public String getTitle(Context context) { return "井字棋"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 120; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new TicTacToeModuleFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}
