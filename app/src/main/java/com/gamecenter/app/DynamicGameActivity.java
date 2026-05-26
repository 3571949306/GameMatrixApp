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
            if (gameId != null) {
                loadGame(gameId);
            }
        }
    }

    private void loadGame(String gameId) {
        Class<? extends Fragment> fragmentClass = GameRegistry.getFragmentClassById(this, gameId);
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
        if (activityClass != null) {
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
            finish();
            return;
        }

        if (tryLoadModuleGame(gameId)) {
            return;
        }

        Toast.makeText(this, "游戏未安装，请前往模块商店下载", Toast.LENGTH_SHORT).show();
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
