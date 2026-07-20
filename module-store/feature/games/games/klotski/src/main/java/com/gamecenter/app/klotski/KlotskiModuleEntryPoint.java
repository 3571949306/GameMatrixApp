package com.gamecenter.app.klotski;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;

import java.util.Arrays;
import java.util.List;

public class KlotskiModuleEntryPoint implements ModuleInterface, FeatureModule {

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
        return "klotski";
    }

    @Override
    public String getName() {
        return "华容道";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public String getDescription() {
        return "经典华容道滑块益智游戏，移动方块帮助曹操从出口逃脱。";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Fragment createFragment(Context context) {
        return new KlotskiModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new KlotskiNavContribution());
    }

    private static class KlotskiNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "klotski"; }

        @Override
        public String getTitle(Context context) { return "华容道"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 110; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new KlotskiModuleFragment(); }
    }
}
