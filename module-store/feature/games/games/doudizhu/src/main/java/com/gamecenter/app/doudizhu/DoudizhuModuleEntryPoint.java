package com.gamecenter.app.doudizhu;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.R;
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

    /**
     * P6 复查确认：全仓库 grep 无任何 UI 消费方调用 ModuleInterface#getName()
     * （UI 可见标题走 {@link DoudizhuNavContribution#getTitle} -> R.string.game_ddz_module_title，
     * zh/en 双语）。除本方法外其余 getName() 均为各 EntryPoint 的接口定义/覆写，
     * 无调用点，故保留中文字面量、不迁移宿主 strings，不为改而改。
     */
    @Override
    public String getName() {
        return "斗地主";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    /**
     * P6 复查确认：全仓库 grep 无任何调用点消费 ModuleInterface#getDescription()
     * （除本方法外其余 getDescription() 均为各 EntryPoint/接口的定义或覆写，
     * 唯一运行时调用点是 WebView 的 WebResourceError#getDescription，与本接口无关）。
     * 确无消费方，保留中文字面量，不为改而改。
     */
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
        public String getTitle(Context context) {
            return context.getString(R.string.game_ddz_module_title);
        }

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
