package com.gamecenter.app.tiles;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.List;

public class TilesModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "tiles";
    }

    @Override
    public String getName() {
        return "麻将连连看";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "在网格中找出两个相同的牌并消除";
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
        return new TilesModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new TilesNavContribution());
    }

    private static class TilesNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "tiles"; }

        @Override
        public String getTitle(Context context) { return "麻将连连看"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 100; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new TilesModuleFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}
