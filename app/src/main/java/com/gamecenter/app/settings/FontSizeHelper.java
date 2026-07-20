package com.gamecenter.app.settings;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import com.gamecenter.app.SettingsManager;

/**
 * 字号应用助手（Feature B / SETTINGS_ENHANCE）。
 *
 * <p>在 Activity 创建时调用 {@link #apply(Activity)}，将用户在设置中心选择的字号
 * 应用到当前 Activity 的 {@link Resources} 上，使所有 sp 文本尺寸随之缩放。</p>
 *
 * <p>用户在 {@link com.gamecenter.app.settings.AppSettingsDialog} 改字号后，
 * 需调用 {@link Activity#recreate()} 重建当前 Activity 才能完全生效。</p>
 */
public final class FontSizeHelper {

    private FontSizeHelper() { }

    /**
     * 将用户偏好的字号应用到指定 Activity。
     *
     * @param activity 目标 Activity
     */
    public static void apply(@NonNull Activity activity) {
        float scale = SettingsManager.getInstance(activity).getFontScale();
        Resources res = activity.getResources();
        Configuration config = res.getConfiguration();
        if (config.fontScale != scale) {
            config.fontScale = scale;
            // updateConfiguration 在 API 25 已废弃，但 minSdk 24 仍可用且最简方案
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }
}
