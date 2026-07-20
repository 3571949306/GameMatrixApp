package com.gamecenter.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.modules.ModuleManifest;

import java.io.File;
import java.text.DecimalFormat;

/**
 * 设置页面 — 模块管理、缓存清理、主题切换、应用信息。
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 返回
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 版本号
        showVersion();

        // 模块商店
        View btnStore = findViewById(R.id.btn_module_store);
        if (btnStore != null) {
            btnStore.setOnClickListener(v -> {
                startActivity(new Intent(this, com.gamecenter.app.modules.ModuleStoreActivity.class));
            });
        }

        // 已安装模块管理（跳转到已安装模块列表页）
        View btnManageModules = findViewById(R.id.btn_manage_modules);
        if (btnManageModules != null) {
            btnManageModules.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this,
                            com.gamecenter.app.modules.InstalledModulesActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "已安装模块页面未找到，尝试 ModuleStoreActivity", e);
                    startActivity(new Intent(this,
                            com.gamecenter.app.modules.ModuleStoreActivity.class));
                }
            });
        }

        // 已安装模块数量
        updateInstalledCount();

        // 清除缓存
        View btnCache = findViewById(R.id.btn_clear_cache);
        if (btnCache != null) {
            btnCache.setOnClickListener(v -> clearCache());
        }
        // 缓存大小
        updateCacheSize();

        // 主题模式
        View btnTheme = findViewById(R.id.btn_theme);
        if (btnTheme != null) {
            btnTheme.setOnClickListener(v -> showThemeDialog());
        }

        // 语言设置
        View btnLanguage = findViewById(R.id.btn_language);
        if (btnLanguage != null) {
            btnLanguage.setOnClickListener(v -> showLanguageDialog());
        }

        // 游戏战绩
        View btnStats = findViewById(R.id.btn_stats);
        if (btnStats != null) {
            btnStats.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, com.gamecenter.app.games.StatsActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "战绩页面未找到", e);
                    Toast.makeText(this, "战绩功能暂不可用", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 服务器状态
        checkServerStatus();

        // 新手引导（U2 免登录上手：允许用户重置引导并重新查看）
        View btnOnboarding = findViewById(R.id.btn_onboarding_reset);
        if (btnOnboarding != null) {
            btnOnboarding.setOnClickListener(v -> showOnboardingResetDialog());
        }

        // 关于
        View btnAbout = findViewById(R.id.btn_about);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> showAboutDialog());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateInstalledCount();
    }

    private void showVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            TextView tv = findViewById(R.id.tv_app_version);
            if (tv != null) tv.setText("v" + info.versionName + " (" + info.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本信息失败", e);
        }
    }

    private void updateInstalledCount() {
        try {
            File modulesDir = new File(getFilesDir(), "modules");
            int count = 0;
            if (modulesDir.exists() && modulesDir.isDirectory()) {
                File[] files = modulesDir.listFiles((dir, name) -> name.endsWith(".apk"));
                count = files != null ? files.length : 0;
            }
            TextView tv = findViewById(R.id.tv_installed_count);
            if (tv != null) tv.setText(count + " 个");
        } catch (Exception e) {
            Log.e(TAG, "获取已安装模块数量失败", e);
            TextView tv = findViewById(R.id.tv_installed_count);
            if (tv != null) tv.setText("—");
        }
    }

    private void updateCacheSize() {
        try {
            long size = getDirSize(getCacheDir());
            TextView tv = findViewById(R.id.tv_cache_size);
            if (tv != null) tv.setText(formatSize(size));
        } catch (Exception e) {
            Log.e(TAG, "计算缓存大小失败", e);
            TextView tv = findViewById(R.id.tv_cache_size);
            if (tv != null) tv.setText("—");
        }
    }

    private void clearCache() {
        new AlertDialog.Builder(this)
                .setTitle("清除缓存")
                .setMessage("确定要清除所有缓存数据吗？")
                .setPositiveButton("清除", (dialog, which) -> {
                    deleteDir(getCacheDir());
                    updateCacheSize();
                    Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showThemeDialog() {
        String[] themes = {"跟随系统", "浅色模式", "深色模式"};
        // 读取当前保存值
        int currentWhich = getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("night_mode", 0);
        new AlertDialog.Builder(this)
                .setTitle("主题模式")
                .setSingleChoiceItems(themes, currentWhich, (dialog, which) -> {
                    int mode;
                    switch (which) {
                        case 1: mode = AppCompatDelegate.MODE_NIGHT_NO; break;
                        case 2: mode = AppCompatDelegate.MODE_NIGHT_YES; break;
                        default: mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
                    }
                    AppCompatDelegate.setDefaultNightMode(mode);
                    TextView tv = findViewById(R.id.tv_theme_mode);
                    if (tv != null) tv.setText(themes[which]);
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit().putInt("night_mode", which).apply();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"跟随系统", "中文", "English"};
        new AlertDialog.Builder(this)
                .setTitle("应用语言")
                .setItems(languages, (dialog, which) -> {
                    TextView tv = findViewById(R.id.tv_language);
                    if (tv != null) tv.setText(languages[which]);
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit().putInt("app_language", which).apply();
                    Toast.makeText(this, "语言设置已保存，重启生效", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void checkServerStatus() {
        // Fix memory leak: use WeakReference so thread doesn't pin Activity
        final java.lang.ref.WeakReference<TextView> tvRef =
                new java.lang.ref.WeakReference<>((TextView) findViewById(R.id.tv_server_status));
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(BuildConfig.MODULES_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("HEAD");
                int code = conn.getResponseCode();
                conn.disconnect();
                final int finalCode = code;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView tv = tvRef.get();
                    if (tv == null) return;
                    tv.setText(finalCode == 200 ? "已连接" : "异常 (" + finalCode + ")");
                    tv.setTextColor(finalCode == 200 ? 0xFF4CAF50 : 0xFFF44336);
                });
            } catch (Exception e) {
                Log.w(TAG, "服务器检测失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView tv = tvRef.get();
                    if (tv == null) return;
                    tv.setText("无法连接");
                    tv.setTextColor(0xFFF44336);
                });
            }
        }).start();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于 GameCenter")
                .setMessage("GameCenter — 游戏中心\n\n"
                        + "一个模块化的游戏平台，\n"
                        + "所有游戏内容通过模块商店下载。\n\n"
                        + "服务器: " + BuildConfig.MODULE_HOST + "\n"
                        + "模块存储: /data/data/包名/files/modules/")
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * 新手引导重置对话框（U2 免登录上手）。
     *
     * <p>项目无账号体系，引导完成态持久化在 SharedPreferences("onboarding") 中。
     * 这里提供「全部重置 / 选择性重置」入口，让用户能重新查看引导。</p>
     */
    private void showOnboardingResetDialog() {
        String[] items = {"全部重置", "斗地主引导", "围棋引导"};
        new AlertDialog.Builder(this)
                .setTitle("新手引导")
                .setItems(items, (dialog, which) -> {
                    android.content.SharedPreferences prefs =
                            getSharedPreferences("onboarding", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    switch (which) {
                        case 0:
                            editor.clear();
                            break;
                        case 1:
                            editor.putBoolean(
                                    com.gamecenter.app.ui.onboarding.DoudizhuOnboarding.STORAGE_KEY,
                                    false);
                            break;
                        case 2:
                            editor.putBoolean(
                                    com.gamecenter.app.ui.onboarding.GoOnboarding.STORAGE_KEY,
                                    false);
                            break;
                    }
                    editor.apply();
                    Toast.makeText(this,
                            "已重置，下次进入对应游戏将重新显示引导",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======== 工具方法 ========

    private long getDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            size += f.isDirectory() ? getDirSize(f) : f.length();
        }
        return size;
    }

    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDir(child);
            }
        }
        return dir.delete();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#.#");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        return df.format(bytes / (1024.0 * 1024.0)) + " MB";
    }
}
