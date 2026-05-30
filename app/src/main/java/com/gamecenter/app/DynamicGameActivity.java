package com.gamecenter.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.core.common.FeatureModule;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.ui.GameLauncherHelper;
import com.gamecenter.app.modules.ModuleLoader;
import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.modules.ModuleManifest;
import java.io.File;

public class DynamicGameActivity extends AppCompatActivity {
    private static final String TAG = "DynamicGameActivity";
    public static final String EXTRA_GAME_ID = "gameId";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_game);

        if (savedInstanceState == null) {
            String gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
            Log.d(TAG, "onCreate, gameId=" + gameId);
            if (gameId != null) {
                loadGame(gameId);
            } else {
                Log.w(TAG, "gameId is null, showing empty DynamicGameActivity");
            }
        }
    }

    private void loadGame(String gameId) {
        Log.d(TAG, "loadGame: gameId=" + gameId);
        Class<? extends Fragment> fragmentClass = GameRegistry.getFragmentClassById(this, gameId);
        Log.d(TAG, "fragmentClass=" + fragmentClass);
        if (fragmentClass != null) {
            try {
                Fragment fragment = fragmentClass.getDeclaredConstructor().newInstance();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                return;
            } catch (Exception e) {
                Log.e(TAG, "内置 Fragment 加载失败: " + gameId, e);
            }
        }

        Class<?> activityClass = GameRegistry.getActivityClassById(this, gameId);
        Log.d(TAG, "activityClass=" + (activityClass != null ? activityClass.getSimpleName() : "null"));
        // 如果 activityClass 就是 DynamicGameActivity 本身，跳过避免无限循环
        if (activityClass != null && activityClass != DynamicGameActivity.class) {
            Log.d(TAG, "found real activityClass, forwarding to " + activityClass.getSimpleName());
            Intent intent = new Intent(this, activityClass);
            // 转发难度索引等额外参数
            intent.putExtra(GameLauncherHelper.EXTRA_DIFFICULTY_INDEX,
                    getIntent().getIntExtra(GameLauncherHelper.EXTRA_DIFFICULTY_INDEX, -1));
            intent.putExtra(GameLauncherHelper.EXTRA_ONLINE_MODE,
                    getIntent().getBooleanExtra(GameLauncherHelper.EXTRA_ONLINE_MODE, false));
            startActivity(intent);
            finish();
            return;
        }

        Log.d(TAG, "activityClass is null or DynamicGameActivity, calling tryLoadModuleGame");
        if (tryLoadModuleGame(gameId)) {
            return;
        }

        Toast.makeText(this, "游戏未安装，正在前往模块商店…", Toast.LENGTH_SHORT).show();
        try {
            Intent storeIntent = new Intent(this, com.gamecenter.app.modules.ModuleStoreActivity.class);
            storeIntent.putExtra("filter_game_id", gameId);
            startActivity(storeIntent);
        } catch (Exception e) {
            Log.e(TAG, "启动模块商店失败，直接 finish", e);
        }
        finish();
    }

    private boolean tryLoadModuleGame(String gameId) {
        ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(this);
        String moduleId = "game_" + gameId;
        ModuleManifest manifest = ModuleManager.INSTANCE.getModuleManifest(moduleId);
        if (manifest == null) {
            Log.d(TAG, "模块 " + moduleId + " 未找到，尝试 " + gameId);
            manifest = ModuleManager.INSTANCE.getModuleManifest(gameId);
        }
        if (manifest == null) {
            Log.d(TAG, "模块 " + gameId + " 也未找到");
            return false;
        }

        Log.d(TAG, "找到模块: " + manifest.getId() + ", fileName=" + manifest.getFileName());

        if (manifest.getFileName() != null && !manifest.getFileName().isEmpty()) {
            File moduleFile = new File(getFilesDir(), "modules/" + manifest.getFileName());
            Log.d(TAG, "检查模块文件: " + moduleFile.getAbsolutePath() + ", exists=" + moduleFile.exists());
            if (!moduleFile.exists()) {
                return false;
            }
        } else if (!ModuleManager.INSTANCE.isModuleInstalled(this, manifest.getId())) {
            Log.d(TAG, "模块未安装: " + manifest.getId());
            return false;
        }

        String hostGameActivityClassName = ModuleManager.INSTANCE.getHostGameActivityClassName(
                manifest.getGameId().isEmpty() ? manifest.getId() : manifest.getGameId());
        if (hostGameActivityClassName != null) {
            try {
                Class<?> hostGameActivity = Class.forName(hostGameActivityClassName);
                Intent intent = new Intent(this, hostGameActivity);
                // 转发难度索引等额外参数
                intent.putExtra(GameLauncherHelper.EXTRA_DIFFICULTY_INDEX,
                        getIntent().getIntExtra(GameLauncherHelper.EXTRA_DIFFICULTY_INDEX, -1));
                intent.putExtra(GameLauncherHelper.EXTRA_ONLINE_MODE,
                        getIntent().getBooleanExtra(GameLauncherHelper.EXTRA_ONLINE_MODE, false));
                startActivity(intent);
                finish();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "宿主游戏 Activity 启动失败: " + hostGameActivityClassName, e);
                return false;
            }
        }

        Log.d(TAG, "开始加载模块: " + manifest.getId());
        ModuleInterface moduleInstance = ModuleLoader.INSTANCE.loadModule(this, manifest);
        if (moduleInstance == null) {
            Log.e(TAG, "模块加载返回 null: " + manifest.getId());
            return false;
        }

        Log.d(TAG, "模块加载成功: " + moduleInstance.getClass().getName() + ", isFeatureModule=" + (moduleInstance instanceof FeatureModule));

        if (moduleInstance instanceof FeatureModule) {
            try {
                Fragment gameFragment = ((FeatureModule) moduleInstance).createFragment(this);
                Log.d(TAG, "Fragment 创建成功: " + gameFragment.getClass().getName());
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, gameFragment)
                        .commit();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "模块游戏 Fragment 创建失败: " + gameId, e);
            }
        }

        return false;
    }
}
