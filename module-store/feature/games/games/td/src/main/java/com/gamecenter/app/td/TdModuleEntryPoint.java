package com.gamecenter.app.td;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleNavigationContribution;
import com.gamecenter.app.core.common.NavigationSlot;
import com.gamecenter.app.core.common.UnityModuleLauncher;

import java.util.Arrays;
import java.util.List;

/**
 * 塔防「保卫蛋蛋」模块入口。
 *
 * 固定路径 + 槽位放塔：怪物沿路径走向蛋蛋吉祥物，
 * 玩家用击杀所得金币建塔、升级、卖塔，守完所有波次即胜利。
 * 逻辑与渲染分离：TdGame 为无 Android 依赖的确定性引擎（JVM 可测），
 * TdView 仅读取快照渲染。
 */
public class TdModuleEntryPoint implements ModuleInterface, FeatureModule {

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
    public String getId() { return "td"; }

    @Override
    public String getName() { return "保卫蛋蛋"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "塔防：固定路径建塔，守住蛋蛋别被怪物吃掉！"; }

    @Override
    public String getModuleType() { return "game"; }

    @Override
    public List<String> getRequiredPermissions() { return java.util.Collections.emptyList(); }

    @Override
    public List<String> getDependencies() { return java.util.Collections.emptyList(); }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public UnityModuleLauncher createUnityLauncher() { return null; }

    @Override
    public boolean shouldPreload() { return false; }

    @Override
    public Fragment createFragment(Context context) {
        return new TdModuleFragment();
    }

    @Override
    public List<ModuleNavigationContribution> getNavigationContributions(Context context) {
        return Arrays.<ModuleNavigationContribution>asList(new TdNavContribution());
    }

    private static class TdNavContribution implements ModuleNavigationContribution {
        @Override
        public String getContributionId() { return "td"; }

        @Override
        public String getTitle(Context context) { return "保卫蛋蛋"; }

        @Override
        public int getIconResId() { return 0; }

        @Override
        public int getOrder() { return 60; }

        @Override
        public NavigationSlot getSlot() { return NavigationSlot.GAMES_HALL; }

        @Override
        public Fragment createFragment(Context context) { return new TdModuleFragment(); }

        @Override
        public boolean isEnabled() { return true; }
    }
}