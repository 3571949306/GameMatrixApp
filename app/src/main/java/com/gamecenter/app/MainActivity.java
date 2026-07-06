package com.gamecenter.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.modules.ModuleStoreActivity;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.recovery.CrashDetector;
import com.gamecenter.app.update.DownloadState;
import com.gamecenter.app.update.UpdateCheckState;
import com.gamecenter.app.update.UpdateInfo;
import com.gamecenter.app.update.UpdateViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 应用的主界面（入口页面）。
 * <p>
 * 【初学者理解】MainActivity 就像是整个应用的"大厅"，用户打开应用后看到的第一个页面。
 * 它负责把各个功能区域（如游戏列表、分类、设置等）组织在一起，
 * 就像一栋大楼的大厅连接着各个楼层和房间。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>权限管理：首次启动时引导用户授权必要的权限（位置、相机、存储等）</li>
 *   <li>导航管理：通过底部导航栏切换不同的功能页面（Fragment）</li>
 *   <li>应用更新：自动检查更新、显示更新弹窗、下载进度、安装引导</li>
 * </ul>
 * <p>
 * 关键技术点：
 * <ul>
 *   <li>使用 Navigation Component 管理页面跳转，而不是手动切换 Fragment
 *       【初学者理解】Navigation 就像一个"自动导航仪"，你告诉它要去哪个页面，
 *       它就帮你处理页面切换的所有细节（动画、返回栈等）</li>
 *   <li>使用 Hilt（{@code @AndroidEntryPoint}）自动注入依赖
 *       【初学者理解】@AndroidEntryPoint 告诉 Hilt："这个页面需要你的帮助，
 *       请帮我把需要的对象准备好"</li>
 *   <li>使用 ViewModel 管理更新相关的数据和状态
 *       【初学者理解】ViewModel 就像一个"数据保险箱"，即使页面因为旋转屏幕等操作重建，
 *       里面的数据也不会丢失</li>
 * </ul>
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_NAV_TAB = "extra_nav_tab";

    // 导航控制器，用于管理页面之间的跳转
    // 【初学者理解】就像地铁的调度中心，控制着列车（页面）该开往哪个方向
    private NavController navController;

    // 权限请求助手，帮助处理运行时权限的申请和结果
    private PermissionHelper permissionHelper;

    // 权限请求启动器，用于弹出系统权限授权窗口
    // 【初学者理解】这是 Android 新的权限请求方式，替代了旧版的 onRequestPermissionsResult
    // 就像用新的预约系统代替旧的排队方式，更方便也更安全
    private ActivityResultLauncher<String[]> permissionLauncher;

    // 更新相关的 ViewModel，负责管理应用更新的数据和业务逻辑
    private UpdateViewModel updateViewModel;

    // 更新提示对话框（告诉用户有新版本可用）
    private AlertDialog updateDialog;

    // 下载进度对话框（显示下载进度条）
    private AlertDialog progressDialog;

    /**
     * 页面创建时的初始化方法。
     * <p>
     * 【初学者理解】这是页面的"出生时刻"，系统创建这个页面时会自动调用。
     * 我们在这里完成所有初始化工作，就像搬进新房子时要先通水通电、摆好家具。
     * <p>
     * 初始化顺序：
     * 1. 设置页面布局（加载 XML 界面）
     * 2. 初始化权限相关组件
     * 3. 如果是首次启动，弹出权限说明对话框
     * 4. 设置底部导航栏和页面跳转
     * 5. 初始化更新检查功能
     *
     * @param savedInstanceState 保存的实例状态，页面因配置变更（如旋转屏幕）重建时可恢复之前的状态；
     *                           首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 15+ 适配：启用 Edge-to-Edge 显示，内容延伸到状态栏/导航栏下方
        EdgeToEdge.enable(this);
        if (CrashDetector.INSTANCE.shouldLaunchRecovery(this)) {
            startActivity(new android.content.Intent(this, com.gamecenter.app.recovery.RecoveryActivity.class));
            finish();
            return;
        }
        CrashDetector.INSTANCE.markAppRunning(this);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        // 初始化权限助手
        permissionHelper = new PermissionHelper(this);

        // 注册权限请求的回调，当用户在权限弹窗中做出选择后，结果会传回这里
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // 将授权结果转为 boolean 数组，传给 PermissionHelper 处理
                    boolean[] grantResults = new boolean[result.size()];
                    int i = 0;
                    for (Boolean granted : result.values()) {
                        grantResults[i++] = granted != null && granted;
                    }
                    permissionHelper.onPermissionsResult(grantResults);
                }
        );

        // 如果是首次启动应用，弹出权限说明对话框
        if (permissionHelper.isFirstLaunch()) {
            permissionHelper.showPermissionDialog(permissionLauncher);
        }

        // 设置导航控制器，用于管理 Fragment 页面之间的跳转
        // 【初学者理解】NavHostFragment 就像一个"容器"，里面装着各个功能页面，
        // NavController 负责控制在这个容器中显示哪个页面
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            KeepStateNavigator keepStateNavigator = new KeepStateNavigator(
                    this, navHostFragment.getChildFragmentManager(), R.id.nav_host_fragment);
            navController.getNavigatorProvider().addNavigator(keepStateNavigator);
            navController.setGraph(R.navigation.mobile_navigation);
        }

        // 设置底部导航栏，让导航栏的点击事件与 NavController 关联
        // 【初学者理解】这样用户点击底部导航栏的不同按钮时，就会自动切换到对应的页面
        BottomNavigationView navView = findViewById(R.id.nav_view);
        setupDynamicNavigation(navView);

        handleNavTabIntent();

        // 创建 UpdateViewModel，用于管理应用更新相关的数据和状态
        updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
        // 开始监听更新状态变化
        observeUpdateStates();
        // 延迟2秒后自动检查更新
        scheduleAutoUpdateCheck();

        // 自动静默下载必需的核心模块
        downloadCoreModulesIfMissing();
    }

    private void setupDynamicNavigation(BottomNavigationView navView) {
        Set<String> installedIds = ModuleManager.INSTANCE.getInstalledModuleIds(this);

        Menu menu = navView.getMenu();
        menu.clear();

        // games_hall 现在是动态模块，但作为主页必须保留入口，如果未安装则点击后进入占位页面
        menu.add(Menu.NONE, R.id.navigation_games, Menu.NONE, R.string.nav_games)
                .setIcon(R.drawable.ic_games);

        if (installedIds.contains("browser")) {
            menu.add(Menu.NONE, R.id.navigation_browser, Menu.NONE, R.string.nav_browser)
                    .setIcon(R.drawable.ic_browser);
        }

        if (installedIds.contains("tools")) {
            menu.add(Menu.NONE, R.id.navigation_tools, Menu.NONE, R.string.nav_tools)
                    .setIcon(R.drawable.ic_tools);
        }

        if (installedIds.contains("ai")) {
            menu.add(Menu.NONE, R.id.navigation_ai, Menu.NONE, R.string.nav_ai)
                    .setIcon(R.drawable.ic_ai);
        }

        if (installedIds.contains("vpn")) {
            menu.add(Menu.NONE, R.id.navigation_vpn, Menu.NONE, R.string.nav_vpn)
                    .setIcon(R.drawable.ic_vpn);
        }

        if (BuildConfig.ENABLE_WRONGBOOK && installedIds.contains("wrongbook")) {
            menu.add(Menu.NONE, R.id.navigation_wrongbook, Menu.NONE, R.string.nav_wrongbook)
                    .setIcon(R.drawable.ic_nav_wrongbook);
        }

        navView.setOnItemSelectedListener(item -> {
            if (navController != null) {
                navController.navigate(item.getItemId());
            }
            return true;
        });

        navView.setOnItemReselectedListener(item -> {});
        if (navController != null && navController.getCurrentDestination() != null) {
            int currentId = navController.getCurrentDestination().getId();
            if (menu.findItem(currentId) != null) {
                menu.findItem(currentId).setChecked(true);
            }
        }
    }

    private void downloadCoreModulesIfMissing() {
        String[] coreModules = {"games_hall", "browser"};
        for (String moduleId : coreModules) {
            if (!ModuleManager.INSTANCE.isModuleInstalled(this, moduleId)) {
                // 延迟下载，避免阻塞启动
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    ModuleManager.INSTANCE.downloadModule(this, moduleId, new com.gamecenter.app.modules.ModuleDownloader.Callback() {
                        @Override
                        public void onProgress(String id, long downloaded, long total, long speed) {}

                        @Override
                        public void onComplete(String id, File file) {
                            runOnUiThread(() -> {
                                BottomNavigationView navView = findViewById(R.id.nav_view);
                                if (navView != null) {
                                    setupDynamicNavigation(navView);
                                }
                            });
                        }

                        @Override
                        public void onError(String id, String message) {
                            Log.e("MainActivity", "Core module download failed: " + id + " - " + message);
                        }

                        @Override
                        public void onError(String id, int errorCode, String message) {
                            onError(id, message);
                        }

                        @Override
                        public void onSourceSwitch(String id, int sourceIndex, String url) {}
                    });
                }, 1000);
            }
        }
    }

    private void handleNavTabIntent() {
        if (getIntent() == null) return;
        String tab = getIntent().getStringExtra(EXTRA_NAV_TAB);
        if (tab == null || navController == null) return;

        int destId;
        switch (tab) {
            case "games_hall":
                destId = R.id.navigation_games;
                break;
            case "browser":
                destId = R.id.navigation_browser;
                break;
            case "tools":
                destId = R.id.navigation_tools;
                break;
            case "ai":
                destId = R.id.navigation_ai;
                break;
            case "vpn":
                destId = R.id.navigation_vpn;
                break;
            case "wrongbook":
                destId = R.id.navigation_wrongbook;
                break;
            default:
                return;
        }
        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (navView != null && navView.getMenu().findItem(destId) != null) {
            navView.setSelectedItemId(destId);
        } else {
            navController.navigate(destId);
        }
        getIntent().removeExtra(EXTRA_NAV_TAB);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            setupDynamicNavigation(navView);
        }
        handleNavTabIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            setupDynamicNavigation(navView);
        }
        handleNavTabIntent();
    }

    private void applySystemBarInsets() {
        View container = findViewById(R.id.container);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (container == null) return;

        final int left = container.getPaddingLeft();
        final int top = container.getPaddingTop();
        final int right = container.getPaddingRight();
        final int bottom = container.getPaddingBottom();
        final int navLeft = navView != null ? navView.getPaddingLeft() : 0;
        final int navTop = navView != null ? navView.getPaddingTop() : 0;
        final int navRight = navView != null ? navView.getPaddingRight() : 0;
        final int navBottom = navView != null ? navView.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom);
            if (navView != null) {
                navView.setPadding(navLeft, navTop, navRight, navBottom + bars.bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(container);
    }

    /**
     * 监听更新相关的状态变化，包括"检查更新"和"下载"两个阶段。
     * <p>
     * 【初学者理解】这个方法就像一个"观察哨"，时刻盯着更新过程的每一步变化。
     * 当检查到有新版本时，弹出更新提示；当下载进度变化时，更新进度条；
     * 当下载完成时，弹出安装提示。每个状态变化都会触发对应的 UI 更新。
     * <p>
     * 使用 LiveData 的 observe 方法实现"观察者模式"：
     * 当 ViewModel 中的数据发生变化时，观察者会自动收到通知并更新界面。
     */
    private void observeUpdateStates() {
        // 监听"更新检查"状态
        updateViewModel.getUpdateCheckState().observe(this, state -> {
            // 如果页面正在关闭或已销毁，不处理任何状态变化，避免崩溃
            if (isFinishing() || isDestroyed()) return;

            if (state instanceof UpdateCheckState.Available) {
                // 有新版本可用，弹出更新提示对话框
                UpdateInfo info = ((UpdateCheckState.Available) state).getInfo();
                showUpdateDialog(info);
            } else if (state instanceof UpdateCheckState.NotAvailable) {
                // 已经是最新版本，显示提示
                Toast.makeText(this, R.string.update_no_update, Toast.LENGTH_SHORT).show();
            } else if (state instanceof UpdateCheckState.BetaOnly) {
                // 仅有测试版可用，弹出测试版说明对话框
                UpdateInfo info = ((UpdateCheckState.BetaOnly) state).getInfo();
                showBetaOnlyNoticeDialog(info);
            } else if (state instanceof UpdateCheckState.BetaBlocked) {
                // 当前版本太旧，仅测试版可用但用户未开启测试版通道
                Toast.makeText(this, R.string.update_beta_only_toast, Toast.LENGTH_LONG).show();
            } else if (state instanceof UpdateCheckState.Error) {
                // 检查更新时发生错误
                String message = ((UpdateCheckState.Error) state).getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        // 监听"下载"状态
        updateViewModel.getDownloadState().observe(this, state -> {
            if (isFinishing() || isDestroyed()) return;

            if (state instanceof DownloadState.Downloading) {
                // 正在下载中，更新进度条
                DownloadState.Downloading dl = (DownloadState.Downloading) state;
                if (progressDialog != null && progressDialog.isShowing()) {
                    updateProgressDialog(dl.getDownloaded(), dl.getTotal());
                }
            } else if (state instanceof DownloadState.Verifying) {
                // 下载完成，正在验证文件完整性
                if (progressDialog != null && progressDialog.isShowing()) {
                    updateProgressVerifying();
                }
            } else if (state instanceof DownloadState.Completed) {
                // 下载并验证完成，关闭进度对话框，弹出安装提示
                dismissProgressDialog();
                File apkFile = ((DownloadState.Completed) state).getApkFile();
                showInstallDialog(apkFile);
            } else if (state instanceof DownloadState.Error) {
                // 下载出错，关闭进度对话框，显示错误提示
                dismissProgressDialog();
                String message = ((DownloadState.Error) state).getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else if (state instanceof DownloadState.Cancelled) {
                // 下载被取消，关闭进度对话框
                dismissProgressDialog();
            }
        });
    }

    /**
     * 延迟2秒后自动检查更新。
     * <p>
     * 【初学者理解】应用启动后不立刻检查更新，而是等2秒，
     * 就像客人进门后先让他坐下喝杯水，再问要不要升级房间，
     * 避免一进门就推销让人反感。
     * <p>
     * 使用 SafeUpdateCheckRunnable 包装，通过 WeakReference 持有 Activity 引用，
     * 防止延迟执行时 Activity 已被销毁导致的内存泄漏或崩溃。
     *
     * 【初学者理解】WeakReference（弱引用）就像"临时通行证"，
     * 当 Activity 被销毁时，通行证自动失效，不会阻止垃圾回收器清理内存。
     */
    private void scheduleAutoUpdateCheck() {
        if (!(getApplication() instanceof App)) return;
        App app = (App) getApplication();
        // 在主线程上延迟2秒执行更新检查
        new Handler(Looper.getMainLooper()).postDelayed(
                new SafeUpdateCheckRunnable(this, app), 2000);
    }

    /**
     * 安全的更新检查任务，使用 WeakReference 防止内存泄漏。
     * <p>
     * 【初学者理解】这是一个"小心谨慎的检查员"，在执行更新检查前会先确认：
     * 1. 页面还在不在？（可能用户已经退出了）
     * 2. 应用是否允许自动检查？（通过 shouldAutoCheckUpdate 控制，只检查一次）
     * 3. 用户是否开启了自动检查功能？（通过设置项控制）
     * 只有全部满足，才会真正去检查更新。
     */
    private static class SafeUpdateCheckRunnable implements Runnable {
        // 使用弱引用持有 Activity，避免阻止 Activity 被垃圾回收
        private final WeakReference<MainActivity> activityRef;
        // App 实例，用于检查是否应该自动更新
        private final App app;

        SafeUpdateCheckRunnable(MainActivity activity, App app) {
            this.activityRef = new WeakReference<>(activity);
            this.app = app;
        }

        @Override
        public void run() {
            // 从弱引用中获取 Activity，如果已被回收则为 null
            MainActivity activity = activityRef.get();
            // 安全检查：Activity 存在且未被销毁时才继续
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            SettingsManager sm = SettingsManager.getInstance(activity);
            // 仅在本次启动首次检查 且 用户开启了自动检查时才执行
            if (app.shouldAutoCheckUpdate() && sm.isAutoCheckUpdate()) {
                activity.updateViewModel.checkUpdate(activity, false);
            }
        }
    }

    /**
     * 手动触发更新检查。
     * <p>
     * 【初学者理解】这是供外部调用的"手动检查更新"方法，
     * 比如用户在设置页面点击"检查更新"按钮时调用。
     *
     * @param showToast 是否在没有新版本时显示提示信息；
     *                  true 表示显示"已是最新版本"的提示，false 表示静默检查
     */
    public void checkUpdate(boolean showToast) {
        if (updateViewModel != null) {
            updateViewModel.checkUpdate(this, showToast);
        }
    }

    /**
     * 显示"发现新版本"的更新提示对话框。
     * <p>
     * 【初学者理解】当检查到有新版本时，弹出一个对话框告诉用户：
     * 新版本号是多少、更新了什么内容、安装包多大等。
     * 如果是强制更新，用户只能点"下载"不能跳过；
     * 如果不是强制更新，用户可以选择"以后再说"。
     *
     * @param info 新版本的详细信息（版本号、更新日志、安装包大小等）
     */
    private void showUpdateDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        // 根据是否强制更新，设置不同的标题
        String title = info.isForceUpdate()
                ? getString(R.string.update_force) + " - " + getString(R.string.update_new_version)
                : getString(R.string.update_new_version);

        // 拼接更新信息：版本名、渠道、版本号、文件大小、更新日志
        StringBuilder message = new StringBuilder();
        message.append(String.format(getString(R.string.update_version), info.getVersionName()));
        message.append("\n");
        message.append(getString(R.string.update_channel_label, info.getChannelLabel()));
        message.append("\n");
        message.append(getString(R.string.update_version_code, info.getVersionCode()));
        message.append("\n");
        message.append(String.format(getString(R.string.update_size), info.getFileSizeFormatted()));
        message.append("\n\n");
        message.append(getString(R.string.update_changelog));
        message.append("\n");
        message.append(info.getChangelog());

        // 构建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_download, (dialog, which) -> {
                    // 用户点击"下载"按钮，开始下载并显示进度对话框
                    startDownloadWithProgressDialog(info);
                });

        if (!info.isForceUpdate()) {
            // 非强制更新：允许用户跳过
            builder.setNegativeButton(R.string.update_later, (dialog, which) -> dialog.dismiss());
        } else {
            // 强制更新：不允许用户关闭对话框
            builder.setCancelable(false);
        }

        updateDialog = builder.create();
        updateDialog.show();
    }

    /**
     * 开始下载更新包，并显示下载进度对话框。
     * <p>
     * 【初学者理解】当用户同意下载更新后，弹出一个带进度条的对话框，
     * 让用户看到下载进度，知道还要等多久。就像下载文件时看到的进度条一样。
     *
     * @param info 新版本信息，包含下载地址和是否强制更新等
     */
    private void startDownloadWithProgressDialog(UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        // 加载进度对话框的自定义布局
        final android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_update_progress, null);
        // 获取布局中的进度条和文字控件
        android.widget.ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        android.widget.TextView tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        android.widget.TextView tvProgressSize = dialogView.findViewById(R.id.tv_progress_size);

        // 初始化进度条：最大值100，当前进度0
        progressBar.setMax(100);
        progressBar.setProgress(0, true);

        // 构建并显示进度对话框
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_downloading)
                .setView(dialogView)
                .setCancelable(!info.isForceUpdate());

        progressDialog = builder.create();
        progressDialog.show();

        // 通知 ViewModel 开始下载
        updateViewModel.startDownload(this, info);
    }

    /**
     * 更新下载进度对话框中的进度信息。
     * <p>
     * 【初学者理解】每次下载了一部分数据，就调用这个方法更新进度条和文字，
     * 让用户看到"已下载 XX%"和"已下载 XX MB / 共 XX MB"。
     *
     * @param downloaded 已下载的字节数
     * @param total      总字节数
     */
    private void updateProgressDialog(long downloaded, long total) {
        if (progressDialog == null || !progressDialog.isShowing()) return;
        android.view.View decorView = progressDialog.getWindow() != null ? progressDialog.getWindow().getDecorView() : null;
        if (decorView == null) return;

        // 从对话框中找到进度条和文字控件
        android.widget.ProgressBar progressBar = decorView.findViewById(R.id.progress_bar);
        android.widget.TextView tvProgressPercent = decorView.findViewById(R.id.tv_progress_percent);
        android.widget.TextView tvProgressSize = decorView.findViewById(R.id.tv_progress_size);

        // 计算下载百分比
        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (progressBar != null) progressBar.setProgress(percent, true);
        if (tvProgressPercent != null) tvProgressPercent.setText(percent + "%");
        if (tvProgressSize != null)
            tvProgressSize.setText(formatDownloadProgress(downloaded, total));
    }

    /**
     * 更新进度对话框为"验证中"状态。
     * <p>
     * 【初学者理解】下载完成后，需要验证文件是否完整（没有被篡改或损坏），
     * 这时进度条的文字会变成"验证中..."，让用户知道还在处理。
     */
    private void updateProgressVerifying() {
        if (progressDialog == null || !progressDialog.isShowing()) return;
        android.view.View decorView = progressDialog.getWindow() != null ? progressDialog.getWindow().getDecorView() : null;
        if (decorView == null) return;

        android.widget.TextView tvProgressPercent = decorView.findViewById(R.id.tv_progress_percent);
        if (tvProgressPercent != null)
            tvProgressPercent.setText(getString(R.string.update_verifying));
    }

    /**
     * 显示"仅测试版可用"的说明对话框。
     * <p>
     * 【初学者理解】当最新版本是测试版，而用户当前使用的是正式版时，
     * 弹出这个对话框告诉用户：最新版是测试版，你可以选择开启测试版通道来获取，
     * 也可以继续等待正式版发布。
     *
     * @param info 新版本信息
     */
    private void showBetaOnlyNoticeDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        // 获取上一个正式版的版本名，如果没有则显示默认文字
        String lastStableName = info.getLastStableVersionName().isEmpty()
                ? getString(R.string.update_last_stable_default)
                : info.getLastStableVersionName();
        // 拼接提示信息
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.update_beta_only_msg,
                info.getVersionName(), info.getVersionCode(),
                info.getLocalVersionCode(), lastStableName));
        if (info.getLastStableVersionCode() > 0) {
            message.append(getString(R.string.update_beta_only_stable_code,
                    info.getLastStableVersionCode()));
        }
        message.append(getString(R.string.update_beta_only_hint));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_beta_only_title)
                .setMessage(message.toString())
                // 用户选择开启测试版通道，重新检查更新
                .setPositiveButton(R.string.update_beta_only_enable, (dialog, which) -> {
                    updateViewModel.enableBetaAndRecheck(this);
                })
                // 用户选择继续等待正式版
                .setNegativeButton(R.string.update_beta_only_wait, null)
                .show();
    }

    /**
     * 显示"安装确认"对话框。
     * <p>
     * 【初学者理解】下载完成后，弹出这个对话框问用户：
     * 要现在安装吗？还是打开下载文件夹？还是取消？
     *
     * @param apkFile 下载好的 APK 安装包文件
     */
    private void showInstallDialog(final File apkFile) {
        if (isFinishing() || isDestroyed()) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_new_version)
                .setMessage(R.string.update_install_prompt)
                // 立即安装
                .setPositiveButton(R.string.update_install, (dialog, which) -> updateViewModel.installApk(this))
                // 打开下载目录，方便用户用文件管理器查看
                .setNeutralButton(R.string.update_open_directory, (dialog, which) ->
                        updateViewModel.openDownloadDirectory(this))
                // 取消安装
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 处理 Activity 结果回调。
     * <p>
     * 【初学者理解】当从其他页面（如系统设置页面）返回时，系统会调用这个方法，
     * 告诉我们操作的结果。这里主要处理"安装未知应用"权限的授权结果。
     *
     * @param requestCode  请求码，用于区分不同的请求来源
     * @param resultCode   结果码，表示操作是否成功
     * @param data         返回的数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UpdateViewModel.REQUEST_INSTALL_PERMISSION) {
            // 处理安装权限的授权结果
            updateViewModel.onInstallPermissionResult(this, resultCode);
        }
    }

    /**
     * 页面销毁时的清理工作。
     * <p>
     * 【初学者理解】当页面被关闭（比如用户退出应用）时，需要把弹出中的对话框关掉，
     * 否则对话框会"悬空"导致应用崩溃。就像离开房间前要关灯关空调一样。
     */
    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        if (updateDialog != null && updateDialog.isShowing()) {
            try { updateDialog.dismiss(); } catch (Exception e) { Log.d("MainActivity", "Dialog dismiss failed", e); }
        }
        updateDialog = null;
        super.onDestroy();
    }

    /**
     * 关闭下载进度对话框并释放引用。
     * <p>
     * 【初学者理解】安全地关闭进度对话框。用 try-catch 包裹是因为
     * 在某些极端情况下（比如对话框关联的窗口已销毁），关闭操作可能抛出异常，
     * 我们捕获异常避免应用崩溃。
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception e) { Log.d("MainActivity", "Dialog dismiss failed", e); }
        }
        progressDialog = null;
    }

    /**
     * 格式化下载进度文字，如 "12.5 MB / 50.0 MB (25%)"。
     * <p>
     * 【初学者理解】把"已下载字节数"和"总字节数"转换成人类易读的格式，
     * 比如 5242880 字节 → "5.0 MB"，让用户一眼就能看懂进度。
     *
     * @param downloaded 已下载的字节数
     * @param total      总字节数
     * @return 格式化后的进度文字
     */
    private String formatDownloadProgress(long downloaded, long total) {
        String downloadedStr = formatFileSize(downloaded);
        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (total > 0) {
            String totalStr = formatFileSize(total);
            return downloadedStr + " / " + totalStr + " (" + percent + "%)";
        }
        return downloadedStr + " (" + percent + "%)";
    }

    /**
     * 将字节数转换为易读的文件大小字符串。
     * <p>
     * 【初学者理解】计算机用"字节"来衡量文件大小，但对人来说数字太大了不好读。
     * 这个方法把字节转换成我们熟悉的单位：B（字节）、KB（千字节）、MB（兆字节）。
     * 例如：5242880 → "5.0 MB"
     *
     * @param size 文件大小（字节数）
     * @return 易读的文件大小字符串
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /**
     * 处理导航栏的"向上"按钮点击事件。
     * <p>
     * 【初学者理解】当页面左上角有返回箭头时，点击后会调用这个方法。
     * 它先尝试让 NavController 返回上一页，如果没有上一页则交给系统默认处理。
     *
     * @return true 表示已处理该事件，false 表示交给系统默认处理
     */
    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
