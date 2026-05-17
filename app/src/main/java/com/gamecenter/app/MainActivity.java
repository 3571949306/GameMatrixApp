package com.gamecenter.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.gamecenter.app.update.UpdatePresenter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 应用主界面 Activity，作为所有 Fragment 的宿主。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>导航管理：使用 Navigation Component 管理底部导航栏与 Fragment 页面切换</li>
 *   <li>权限处理：首次启动时弹出权限申请对话框，后续启动不再重复申请</li>
 *   <li>更新检查：延迟 2 秒后自动检查应用更新，使用 WeakReference 防止内存泄漏</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 Hilt（{@code @AndroidEntryPoint}）进行依赖注入</li>
 *   <li>更新检查采用延迟 + WeakReference 方案，避免 Activity 销毁后仍持有引用导致泄漏</li>
 *   <li>权限结果通过 ActivityResultLauncher（新 API）处理，替代已废弃的 onRequestPermissionsResult</li>
 * </ul>
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    /** Navigation Component 的导航控制器，用于管理 Fragment 导航 */
    private NavController navController;

    /** 权限申请辅助类，封装权限判断与对话框逻辑 */
    private PermissionHelper permissionHelper;

    /** 权限申请启动器，基于 Activity Result API，替代传统的 onRequestPermissionsResult */
    private ActivityResultLauncher<String[]> permissionLauncher;

    /** 更新检查的 Presenter，负责与更新服务交互并展示更新提示 */
    private UpdatePresenter updatePresenter;

    /**
     * Activity 创建时的初始化入口。
     * <p>
     * 初始化顺序：设置布局 → 注册权限回调 → 首次启动权限申请 →
     * 初始化导航 → 初始化更新检查。
     *
     * @param savedInstanceState 保存的实例状态，非 null 时表示 Activity 正在恢复
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化权限辅助类和权限申请启动器
        permissionHelper = new PermissionHelper(this);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // 将 Map<String, Boolean> 转换为 boolean[]，与 PermissionHelper 的接口对齐
                    boolean[] grantResults = new boolean[result.size()];
                    int i = 0;
                    for (Boolean granted : result.values()) {
                        // granted 可能为 null（理论上不会），做防御性判断
                        grantResults[i++] = granted != null && granted;
                    }
                    permissionHelper.onPermissionsResult(grantResults);
                }
        );

        // 仅在首次启动时弹出权限申请对话框，避免每次进入都打扰用户
        if (permissionHelper.isFirstLaunch()) {
            permissionHelper.showPermissionDialog(permissionLauncher);
        }

        // 初始化 Navigation Component：从 NavHostFragment 获取 NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        // 将底部导航栏与 NavController 绑定，实现点击导航栏自动切换 Fragment
        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavigationUI.setupWithNavController(navView, navController);

        // 初始化更新检查 Presenter 并调度延迟自动检查
        updatePresenter = new UpdatePresenter(this);
        scheduleAutoUpdateCheck();
    }

    /**
     * 调度延迟自动更新检查。
     * <p>
     * 延迟 2 秒执行，目的是让主界面先完成渲染，避免更新检查的网络请求
     * 与启动阶段的资源竞争。使用 {@link SafeUpdateCheckRunnable} 包装，
     * 通过 WeakReference 持有 Activity 引用，防止 Activity 已销毁时仍执行回调。
     */
    private void scheduleAutoUpdateCheck() {
        if (!(getApplication() instanceof App)) return;
        App app = (App) getApplication();
        new Handler(Looper.getMainLooper()).postDelayed(
                new SafeUpdateCheckRunnable(this, app), 2000);
    }

    /**
     * 安全的更新检查 Runnable，使用 WeakReference 持有 Activity 引用。
     * <p>
     * 防止场景：Handler 的消息队列中仍持有 Runnable 引用，但 Activity 已被销毁，
     * 此时若强引用 Activity 会导致内存泄漏或在已销毁的 Activity 上操作 UI。
     * <p>
     * 执行条件（全部满足才触发检查）：
     * <ol>
     *   <li>Activity 仍存活（未被回收、未 finishing、未 destroyed）</li>
     *   <li>App 标记本次启动尚未执行过自动检查（{@link App#shouldAutoCheckUpdate()}）</li>
     *   <li>用户设置中开启了自动更新检查</li>
     * </ol>
     */
    private static class SafeUpdateCheckRunnable implements Runnable {
        /** WeakReference 持有 Activity，允许 GC 在 Activity 销毁后回收 */
        private final WeakReference<MainActivity> activityRef;
        /** App 引用，用于调用 shouldAutoCheckUpdate() 门控方法 */
        private final App app;

        /**
         * @param activity 主界面 Activity，以弱引用方式持有
         * @param app      Application 实例，用于判断是否应执行自动更新检查
         */
        SafeUpdateCheckRunnable(MainActivity activity, App app) {
            this.activityRef = new WeakReference<>(activity);
            this.app = app;
        }

        @Override
        public void run() {
            MainActivity activity = activityRef.get();
            // 三重防御：引用已被 GC 回收 / Activity 正在结束 / Activity 已销毁
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            SettingsManager sm = SettingsManager.getInstance(activity);
            if (app.shouldAutoCheckUpdate() && sm.isAutoCheckUpdate()) {
                // false 表示不显示 Toast 提示（自动检查静默进行）
                activity.updatePresenter.checkUpdate(false);
            }
        }
    }

    /**
     * 手动触发更新检查。
     * <p>
     * 通常由用户在设置页面点击"检查更新"按钮时调用。
     *
     * @param showToast true 表示无更新时显示 Toast 提示；false 表示静默检查
     */
    public void checkUpdate(boolean showToast) {
        if (updatePresenter != null) {
            updatePresenter.checkUpdate(showToast);
        }
    }

    /**
     * 处理 Activity 结果回调，转发给 UpdatePresenter 处理。
     * <p>
     * 主要用于更新下载安装流程中的返回结果处理（如安装确认）。
     *
     * @param requestCode 请求码
     * @param resultCode  结果码
     * @param data        返回的 Intent 数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (updatePresenter != null) {
            updatePresenter.handleActivityResult(requestCode, resultCode);
        }
    }

    /**
     * Activity 销毁时清理资源。
     * <p>
     * 释放 UpdatePresenter 的引用，防止 Activity 销毁后 Presenter 仍持有 Context 导致泄漏。
     * 必须在 super.onDestroy() 之前执行清理，确保子类资源先于父类释放。
     */
    @Override
    protected void onDestroy() {
        if (updatePresenter != null) {
            updatePresenter.onDestroy();
            updatePresenter = null;
        }
        super.onDestroy();
    }

    /**
     * 处理 Navigation UI 的向上导航（Toolbar 返回按钮）。
     *
     * @return true 表示导航已由 NavController 处理；false 表示交由父类默认处理
     */
    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
