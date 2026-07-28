package com.gamecenter.app.modules;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 * P3-12 (MODULE_STORE_ENHANCE): 模块用户评分存储。
 *
 * <p>存储位置：{@code module_usage} SharedPreferences 文件，
 * key 前缀 {@link #KEY_RATING_PREFIX} + moduleId，取值 1~5（0 表示未评分）。</p>
 *
 * <p>设计参考：{@code GameRatingStore}，保持 API 一致以便统一备份/恢复。</p>
 */
public final class ModuleRatingStore {

    private static final String PREFS_NAME = "module_usage";
    private static final String KEY_RATING_PREFIX = "user_rating_";

    private final SharedPreferences prefs;

    public ModuleRatingStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 设置某模块的用户评分。stars=0 等同于清除。 */
    public void setRating(@NonNull String moduleId, @IntRange(from = 0, to = 5) int stars) {
        prefs.edit().putInt(KEY_RATING_PREFIX + moduleId, stars).apply();
    }

    /** 清除评分。 */
    public void clearRating(@NonNull String moduleId) {
        prefs.edit().remove(KEY_RATING_PREFIX + moduleId).apply();
    }

    /** 获取用户评分（0=未评分，1~5=星级）。 */
    public int getRating(@NonNull String moduleId) {
        return prefs.getInt(KEY_RATING_PREFIX + moduleId, 0);
    }

    /** 是否已评分。 */
    public boolean hasRating(@NonNull String moduleId) {
        return getRating(moduleId) > 0;
    }
}
