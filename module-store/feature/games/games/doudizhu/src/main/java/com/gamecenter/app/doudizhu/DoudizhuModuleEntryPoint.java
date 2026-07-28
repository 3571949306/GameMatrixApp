package com.gamecenter.app.doudizhu;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 斗地主模块入口点。
 */
public class DoudizhuModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "doudizhu";
    }

    @Override
    public String getName() {
        return "斗地主";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "经典三人斗地主对战游戏";
    }

    @Override
    public String getModuleType() {
        return "game";
    }

    @Override
    public List<String> getRequiredPermissions() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getDependencies() {
        return Collections.emptyList();
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
        return new DoudizhuModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new DoudizhuNavContribution());
    }

    private static class DoudizhuNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "doudizhu"; }

        @Override
        public String getTitle(Context context) { return "斗地主"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 200; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new DoudizhuModuleFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}
