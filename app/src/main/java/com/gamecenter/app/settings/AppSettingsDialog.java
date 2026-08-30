package com.gamecenter.app.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.App;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ColorSchemeManager;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.StatsActivity;
import com.gamecenter.app.update.UpdateManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用设置对话框。
 * 对标主流上市 app 的设计规范，采用卡片分组 + 图标 + 说明文案的布局方式。
 */
public class AppSettingsDialog {

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(4);

    private interface OnUpdateSourceSelectedListener {
        void onSelected(int source);
    }

    private final Fragment fragment;
    private final Runnable onCheckUpdate;
    private final Runnable onFeedback;
    // Batch 11-2 (DATA_BACKUP_RESTORE): SAF launcher 触发回调
    private final Runnable onExportData;
    private final Runnable onImportData;

    // BUG-003 修复：标记主题/语言变更后需要重建 Activity，待主设置对话框关闭时统一触发
    private boolean pendingRecreate = false;

    public AppSettingsDialog(
            @NonNull Fragment fragment,
            @Nullable Runnable onCheckUpdate,
            @Nullable Runnable onFeedback) {
        this(fragment, onCheckUpdate, onFeedback, null, null);
    }

    /**
     * Batch 11 构造函数：增加数据导出/导入回调。
     *
     * @param onExportData 触发 SAF ACTION_CREATE_DOCUMENT（导出 JSON）
     * @param onImportData 触发 SAF ACTION_OPEN_DOCUMENT（导入 JSON）
     */
    public AppSettingsDialog(
            @NonNull Fragment fragment,
            @Nullable Runnable onCheckUpdate,
            @Nullable Runnable onFeedback,
            @Nullable Runnable onExportData,
            @Nullable Runnable onImportData) {
        this.fragment = fragment;
        this.onCheckUpdate = onCheckUpdate;
        this.onFeedback = onFeedback;
        this.onExportData = onExportData;
        this.onImportData = onImportData;
    }

    /**
     * 获取更新源显示名称列表。
     * <p>
     * 2026-06-19: 已移除"美国 VPS"选项，仅保留 3 个选项（自动/香港/GitHub）。
     * 注意：数组索引必须与 {@link SettingsManager#UPDATE_SOURCE_AUTO/HK/GITHUB} 常量值一致，
     * 因此使用显式数组而非直接遍历常量。
     * </p>
     */
    private String[] getUpdateSourceNames() {
        Context ctx = fragment.requireContext();
        return new String[]{
                ctx.getString(R.string.settings_source_auto_recommended),   // index 0 = AUTO
                ctx.getString(R.string.settings_source_hk_vps),             // index 1 = VPS_HK
                "",                                                          // index 2 = VPS_US (deprecated, hidden)
                ctx.getString(R.string.settings_source_github)              // index 3 = GITHUB
        };
    }

    /**
     * 根据更新源常量获取显示名称。
     * 2026-06-19: 美国 VPS（已废弃）回退到"自动"。
     */
    private String getUpdateSourceName(int source) {
        if (source == SettingsManager.UPDATE_SOURCE_VPS_US) {
            // 旧用户历史选择了美国 VPS，显示为"自动"
            return fragment.requireContext().getString(R.string.settings_source_auto_recommended);
        }
        String[] names = getUpdateSourceNames();
        if (source >= 0 && source < names.length && !names[source].isEmpty()) return names[source];
        return fragment.requireContext().getString(R.string.settings_source_auto_recommended);
    }

    private String getThemeModeLabel(int mode) {
        Context ctx = fragment.requireContext();
        switch (mode) {
            case SettingsManager.THEME_LIGHT: return ctx.getString(R.string.theme_light);
            case SettingsManager.THEME_DARK: return ctx.getString(R.string.theme_dark);
            default: return ctx.getString(R.string.theme_system);
        }
    }

    private String getLanguageLabel(String lang) {
        Context ctx = fragment.requireContext();
        if (SettingsManager.LANGUAGE_ZH.equals(lang)) return ctx.getString(R.string.language_chinese);
        if (SettingsManager.LANGUAGE_EN.equals(lang)) return ctx.getString(R.string.language_english);
        return ctx.getString(R.string.language_auto);
    }

    /** Feature B: 字号偏好显示名称。 */
    private String getFontSizeLabel(int fontSize) {
        Context ctx = fragment.requireContext();
        switch (fontSize) {
            case SettingsManager.FONT_SIZE_SMALL:
                return ctx.getString(R.string.settings_font_size_small);
            case SettingsManager.FONT_SIZE_LARGE:
                return ctx.getString(R.string.settings_font_size_large);
            case SettingsManager.FONT_SIZE_MEDIUM:
            default:
                return ctx.getString(R.string.settings_font_size_medium);
        }
    }

    public void show() {
        if (!fragment.isAdded()) {
            return;
        }

        Context context = fragment.requireContext();
        Activity activity = fragment.requireActivity();
        SettingsManager settings = SettingsManager.getInstance(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_settings_modern, null);

        // 版本信息
        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel;
        if ("beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL)) {
            channelLabel = " " + context.getString(R.string.settings_channel_beta);
        } else if ("stable".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL)) {
            channelLabel = " " + context.getString(R.string.settings_channel_stable);
        } else {
            channelLabel = "";
        }
        if (tvVersion != null) {
            tvVersion.setText(context.getString(
                    R.string.settings_version_display_format,
                    BuildConfig.VERSION_NAME + channelLabel,
                    BuildConfig.VERSION_CODE));
        }

        // 当前语言
        TextView tvCurrentLanguage = dialogView.findViewById(R.id.tv_current_language);
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(getLanguageLabel(settings.getAppLanguage()));
        }

        // 当前主题
        TextView tvCurrentTheme = dialogView.findViewById(R.id.tv_current_theme);
        if (tvCurrentTheme != null) {
            tvCurrentTheme.setText(getThemeModeLabel(settings.getThemeMode()));
        }

        // 配色方案预览
        List<ColorSchemeManager.Scheme> schemes = ColorSchemeManager.getSchemes();
        final int[] currentSchemeIndex = {
                ColorSchemeManager.normalizeSchemeIndex(settings.getColorSchemeIndex())
        };

        View vPrimary = dialogView.findViewById(R.id.v_color_primary);
        View vSecondary = dialogView.findViewById(R.id.v_color_secondary);
        View vAccent = dialogView.findViewById(R.id.v_color_accent);
        TextView tvSchemeName = dialogView.findViewById(R.id.tv_scheme_name);
        updateColorSchemeRow(vPrimary, vSecondary, vAccent, tvSchemeName,
                schemes.get(currentSchemeIndex[0]));

        // 点击语言 - 弹出单选对话框
        LinearLayout llLanguage = dialogView.findViewById(R.id.ll_language);
        if (llLanguage != null) {
            llLanguage.setOnClickListener(v -> showLanguagePicker(context, settings, tvCurrentLanguage));
        }

        // 点击主题 - 弹出单选对话框
        LinearLayout llTheme = dialogView.findViewById(R.id.ll_theme);
        if (llTheme != null) {
            llTheme.setOnClickListener(v -> showThemePicker(context, settings, tvCurrentTheme));
        }

        // 配色方案
        LinearLayout llColorScheme = dialogView.findViewById(R.id.ll_color_scheme);
        if (llColorScheme != null) {
            llColorScheme.setOnClickListener(v -> showColorSchemePicker(
                    context, schemes, currentSchemeIndex, vPrimary, vSecondary, vAccent, tvSchemeName));
        }

        // 版本更新设置
        LinearLayout llUpdateSettings = dialogView.findViewById(R.id.ll_update_settings);
        if (llUpdateSettings != null) {
            llUpdateSettings.setOnClickListener(v -> showUpdateSettingsDialog(context, settings));
        }

        // 检查更新
        LinearLayout llCheckUpdate = dialogView.findViewById(R.id.ll_check_update);
        if (llCheckUpdate != null) {
            llCheckUpdate.setOnClickListener(v -> {
                if (onCheckUpdate != null) {
                    onCheckUpdate.run();
                }
            });
        }

        // 游戏战绩
        LinearLayout llViewStats = dialogView.findViewById(R.id.ll_view_stats);
        if (llViewStats != null) {
            llViewStats.setOnClickListener(v -> {
                activity.startActivity(new Intent(activity, StatsActivity.class));
            });
        }

        // 帮助与反馈
        LinearLayout llFeedback = dialogView.findViewById(R.id.ll_feedback);
        if (llFeedback != null) {
            llFeedback.setOnClickListener(v -> {
                if (onFeedback != null) {
                    onFeedback.run();
                }
            });
        }

        // Batch 7-4 (SETTINGS_ABOUT_PAGE): 关于入口
        LinearLayout llAbout = dialogView.findViewById(R.id.ll_about);
        if (llAbout != null) {
            llAbout.setOnClickListener(v -> {
                if (BuildConfig.SETTINGS_ABOUT_PAGE) {
                    com.gamecenter.app.ui.AboutDialog aboutDialog =
                            new com.gamecenter.app.ui.AboutDialog(() -> {
                                if (onCheckUpdate != null) {
                                    onCheckUpdate.run();
                                }
                            });
                    aboutDialog.show(fragment.getChildFragmentManager(), "AboutDialog");
                } else {
                    Toast.makeText(context, context.getString(R.string.settings_about_title),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ===== 声音与反馈开关 =====
        // 音效总开关（控制所有音频）
        MaterialSwitch switchSfxMaster = dialogView.findViewById(R.id.switch_sfx_master);
        if (switchSfxMaster != null) {
            switchSfxMaster.setChecked(settings.isSfxEnabled());
            switchSfxMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settings.setSfxEnabled(isChecked);
                // 音效总开关关闭时，联动禁用游戏音效和振动开关
                updateAudioSwitchStates(dialogView, isChecked);
            });
        }

        // 游戏音效开关
        MaterialSwitch switchSound = dialogView.findViewById(R.id.switch_sound);
        if (switchSound != null) {
            switchSound.setChecked(settings.isSoundEnabled());
            switchSound.setOnCheckedChangeListener((buttonView, isChecked) ->
                    settings.setSoundEnabled(isChecked));
        }

        // 振动反馈开关
        MaterialSwitch switchVibration = dialogView.findViewById(R.id.switch_vibration);
        if (switchVibration != null) {
            switchVibration.setChecked(settings.isVibrationEnabled());
            switchVibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                    settings.setVibrationEnabled(isChecked));
        }

        // 初始化开关联动状态
        updateAudioSwitchStates(dialogView, settings.isSfxEnabled());

        // ===== Feature B (SETTINGS_ENHANCE): 字号 + 缓存清理 =====
        if (BuildConfig.SETTINGS_ENHANCE) {
            initFeatureBRows(dialogView, settings);
        }

        // ===== Batch 11-2 (DATA_BACKUP_RESTORE): 数据导出/导入 =====
        if (BuildConfig.DATA_BACKUP_RESTORE) {
            initDataBackupRows(dialogView);
        } else {
            // 底部导航和收藏排序也位于这张卡片中，备份关闭时只隐藏备份行。
            View llExport = dialogView.findViewById(R.id.ll_data_export);
            View llImport = dialogView.findViewById(R.id.ll_data_import);
            if (llExport != null) llExport.setVisibility(View.GONE);
            if (llImport != null) llImport.setVisibility(View.GONE);
        }

        // ===== Batch 11-4 (GAME_FAVORITE_REORDER): 收藏置顶开关 =====
        if (BuildConfig.GAME_FAVORITE_REORDER) {
            initFavoriteReorderSwitch(dialogView);
        } else {
            View llFav = dialogView.findViewById(R.id.ll_favorite_reorder);
            if (llFav != null) llFav.setVisibility(View.GONE);
        }

        // 底部导航排序与隐藏由 Android 宿主持久化，返回主页后即时刷新。
        View llBottomNavigation = dialogView.findViewById(R.id.ll_bottom_navigation_settings);
        if (llBottomNavigation != null) {
            if (BuildConfig.BOTTOM_NAV_CUSTOMIZATION) {
                llBottomNavigation.setOnClickListener(v -> activity.startActivity(
                        new Intent(activity, BottomNavigationSettingsActivity.class)));
            } else {
                llBottomNavigation.setVisibility(View.GONE);
            }
        }

        // BUG-003 修复：保存主对话框引用，并在 dismiss 时根据 pendingRecreate 标志延迟重建 Activity
        // 这样主题/语言子对话框选择后只关闭子对话框，主对话框保留；
        // 用户关闭主对话框时才触发 Activity 重建，使主题/语言变更生效。
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_title))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.settings_ok), (d, w) -> {
                    if (pendingRecreate) {
                        fragment.requireActivity().recreate();
                    }
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .setOnDismissListener(d -> {
                    if (pendingRecreate) {
                        fragment.requireActivity().recreate();
                    }
                })
                .show();
    }

    /**
     * Batch 11-2: 绑定数据导出/导入行的点击事件。
     * 若调用方未提供 SAF 回调，则把对应行置灰并 Toast 提示。
     */
    private void initDataBackupRows(@NonNull View rootView) {
        Context ctx = fragment.requireContext();
        View llExport = rootView.findViewById(R.id.ll_data_export);
        View llImport = rootView.findViewById(R.id.ll_data_import);

        if (llExport != null) {
            if (onExportData != null) {
                llExport.setOnClickListener(v -> onExportData.run());
            } else {
                llExport.setAlpha(0.45f);
                llExport.setEnabled(false);
                llExport.setOnClickListener(v ->
                        Toast.makeText(ctx, R.string.data_backup_unavailable,
                                Toast.LENGTH_SHORT).show());
            }
        }
        if (llImport != null) {
            if (onImportData != null) {
                llImport.setOnClickListener(v -> onImportData.run());
            } else {
                llImport.setAlpha(0.45f);
                llImport.setEnabled(false);
                llImport.setOnClickListener(v ->
                        Toast.makeText(ctx, R.string.data_backup_unavailable,
                                Toast.LENGTH_SHORT).show());
            }
        }
    }

    /** Batch 11-4: 绑定收藏置顶开关。 */
    private void initFavoriteReorderSwitch(@NonNull View rootView) {
        Context ctx = fragment.requireContext();
        MaterialSwitch sw = rootView.findViewById(R.id.switch_favorite_reorder);
        if (sw == null) return;
        sw.setChecked(com.gamecenter.app.ui.GameFavoriteReorderHelper.INSTANCE.isEnabled(ctx));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            com.gamecenter.app.ui.GameFavoriteReorderHelper.INSTANCE.setEnabled(ctx, isChecked);
            Toast.makeText(ctx,
                    isChecked ? R.string.favorite_reorder_enabled_toast
                              : R.string.favorite_reorder_disabled_toast,
                    Toast.LENGTH_SHORT).show();
        });
        // 整行点击也触发开关切换
        View llFav = rootView.findViewById(R.id.ll_favorite_reorder);
        if (llFav != null) {
            llFav.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        }
    }

    /**
     * 找到"数据与排序"分组的标题 TextView。
     * 由于布局中该标题没有 ID，通过遍历父布局定位（在 card_data 之前的 TextView）。
     * 这里简化处理：直接查找紧邻 card_data 之前的 TextView。
     */
    private static View findDataBackupGroupTitle(@NonNull View rootView) {
        // 简化：card_data 之前的同级 TextView 即为分组标题
        // 由于 ScrollView+LinearLayout 结构，遍历查找包含 settings_data_backup_title 文案的 TextView
        if (!(rootView instanceof android.view.ViewGroup)) return null;
        return findTextViewByText((android.view.ViewGroup) rootView,
                rootView.getContext().getString(R.string.settings_data_backup_title));
    }

    private static View findTextViewByText(android.view.ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (text.equals(tv.getText() == null ? "" : tv.getText().toString())) {
                    return tv;
                }
            } else if (child instanceof android.view.ViewGroup) {
                View found = findTextViewByText((android.view.ViewGroup) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 根据音效总开关状态，联动更新游戏音效和振动开关的可用性。
     * <p>
     * 音效总开关关闭时，游戏音效和振动开关应禁用并变灰；
     * 开启时恢复可用状态。
     * </p>
     */
    private void updateAudioSwitchStates(View rootView, boolean masterEnabled) {
        MaterialSwitch switchSound = rootView.findViewById(R.id.switch_sound);
        MaterialSwitch switchVibration = rootView.findViewById(R.id.switch_vibration);
        LinearLayout llSound = rootView.findViewById(R.id.ll_sound);
        LinearLayout llVibration = rootView.findViewById(R.id.ll_vibration);

        float alpha = masterEnabled ? 1f : 0.45f;
        if (switchSound != null) switchSound.setEnabled(masterEnabled);
        if (switchVibration != null) switchVibration.setEnabled(masterEnabled);
        if (llSound != null) {
            llSound.setEnabled(masterEnabled);
            llSound.setAlpha(alpha);
        }
        if (llVibration != null) {
            llVibration.setEnabled(masterEnabled);
            llVibration.setAlpha(alpha);
        }
    }

    private void showLanguagePicker(Context context, SettingsManager settings, TextView tvCurrentLanguage) {
        String[] items = {
                context.getString(R.string.language_auto),
                context.getString(R.string.language_chinese),
                context.getString(R.string.language_english)
        };
        String currentLang = settings.getAppLanguage();
        int checkedItem;
        if (SettingsManager.LANGUAGE_ZH.equals(currentLang)) {
            checkedItem = 1;
        } else if (SettingsManager.LANGUAGE_EN.equals(currentLang)) {
            checkedItem = 2;
        } else {
            checkedItem = 0;
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_language))
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    String newLang;
                    if (which == 1) {
                        newLang = SettingsManager.LANGUAGE_ZH;
                    } else if (which == 2) {
                        newLang = SettingsManager.LANGUAGE_EN;
                    } else {
                        newLang = SettingsManager.LANGUAGE_SYSTEM;
                    }
                    settings.setAppLanguage(newLang);
                    tvCurrentLanguage.setText(getLanguageLabel(newLang));
                    AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(newLang));
                    if (fragment.requireActivity().getApplication() instanceof App) {
                        ((App) fragment.requireActivity().getApplication()).applyTheme();
                    }
                    // BUG-003 修复：延迟 recreate 到主对话框关闭时执行，避免主设置对话框被销毁
                    pendingRecreate = true;
                    dialog.dismiss();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    private void showThemePicker(Context context, SettingsManager settings, TextView tvCurrentTheme) {
        String[] items = {
                context.getString(R.string.theme_system),
                context.getString(R.string.theme_light),
                context.getString(R.string.theme_dark)
        };
        int currentTheme = settings.getThemeMode();
        int checkedItem;
        if (currentTheme == SettingsManager.THEME_LIGHT) {
            checkedItem = 1;
        } else if (currentTheme == SettingsManager.THEME_DARK) {
            checkedItem = 2;
        } else {
            checkedItem = 0;
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_theme))
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    int newTheme;
                    if (which == 1) {
                        newTheme = SettingsManager.THEME_LIGHT;
                    } else if (which == 2) {
                        newTheme = SettingsManager.THEME_DARK;
                    } else {
                        newTheme = SettingsManager.THEME_SYSTEM;
                    }
                    settings.setThemeMode(newTheme);
                    tvCurrentTheme.setText(getThemeModeLabel(newTheme));
                    if (fragment.requireActivity().getApplication() instanceof App) {
                        ((App) fragment.requireActivity().getApplication()).applyTheme();
                    }
                    // BUG-003 修复：延迟 recreate 到主对话框关闭时执行，避免主设置对话框被销毁
                    pendingRecreate = true;
                    dialog.dismiss();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    private void showUpdateSettingsDialog(Context context, SettingsManager settings) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_settings, null);

        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? context.getString(R.string.channel_beta_suffix) : "";
        if (tvVersion != null) {
            tvVersion.setText(context.getString(R.string.settings_version_display_format,
                    BuildConfig.VERSION_NAME + channelLabel, BuildConfig.VERSION_CODE));
        }

        TextView tvUpdateSource = dialogView.findViewById(R.id.tv_update_source);
        final int[] currentUpdateSource = {settings.getUpdateSource()};
        if (tvUpdateSource != null) {
            tvUpdateSource.setText(getUpdateSourceName(currentUpdateSource[0]));
        }

        LinearLayout llUpdateSource = dialogView.findViewById(R.id.ll_update_source);
        if (llUpdateSource != null) {
            llUpdateSource.setOnClickListener(v -> showUpdateSourcePicker(
                    context, currentUpdateSource[0], source -> {
                        if (tvUpdateSource != null) {
                            tvUpdateSource.setText(getUpdateSourceName(source));
                        }
                        currentUpdateSource[0] = source;
                    }));
        }

        MaterialSwitch switchAutoCheck = dialogView.findViewById(R.id.switch_auto_check);
        if (switchAutoCheck != null) {
            switchAutoCheck.setChecked(settings.isAutoCheckUpdate());
        }

        MaterialSwitch switchAcceptBeta = dialogView.findViewById(R.id.switch_accept_beta);
        if (switchAcceptBeta != null) {
            switchAcceptBeta.setChecked(settings.isAcceptBetaUpdate());
        }

        // 分发架构 v2：下载源自动选择三开关（主开关/移动子选项/自动关闭子开关）
        MaterialSwitch switchDlAutoSelect = dialogView.findViewById(R.id.switch_dl_auto_select);
        MaterialSwitch switchDlMobileSelect = dialogView.findViewById(R.id.switch_dl_mobile_select);
        MaterialSwitch switchDlMobileAutoDisable = dialogView.findViewById(
                R.id.switch_dl_mobile_auto_disable);
        if (switchDlAutoSelect != null) {
            switchDlAutoSelect.setChecked(settings.isDlAutoSelect());
        }
        if (switchDlMobileSelect != null) {
            switchDlMobileSelect.setChecked(settings.isDlMobileAutoSelect());
        }
        if (switchDlMobileAutoDisable != null) {
            switchDlMobileAutoDisable.setChecked(settings.isDlMobileAutoDisable());
        }
        Runnable updateDlDependents = () -> {
            boolean mainOn = switchDlAutoSelect == null || switchDlAutoSelect.isChecked();
            boolean mobileOn = settings.isDlMobileAutoSelect();
            if (switchDlMobileSelect != null) switchDlMobileSelect.setEnabled(mainOn);
            if (switchDlMobileAutoDisable != null) {
                switchDlMobileAutoDisable.setEnabled(mainOn && mobileOn);
            }
        };
        updateDlDependents.run();
        if (switchDlAutoSelect != null) {
            switchDlAutoSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settings.setDlAutoSelect(isChecked);
                updateDlDependents.run();
            });
        }
        if (switchDlMobileSelect != null) {
            switchDlMobileSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settings.setDlMobileAutoSelect(isChecked);
                updateDlDependents.run();
            });
        }
        if (switchDlMobileAutoDisable != null) {
            switchDlMobileAutoDisable.setOnCheckedChangeListener((buttonView, isChecked) ->
                    settings.setDlMobileAutoDisable(isChecked));
        }

        MaterialSwitch switchAutoDownload = dialogView.findViewById(R.id.switch_auto_download_update);
        MaterialSwitch switchPromptInstall = dialogView.findViewById(
                R.id.switch_prompt_install_after_download);
        LinearLayout llPromptInstall = dialogView.findViewById(
                R.id.ll_prompt_install_after_download);
        TextView tvPromptInstall = dialogView.findViewById(
                R.id.tv_prompt_install_after_download);

        if (switchAutoDownload != null) {
            switchAutoDownload.setChecked(settings.isAutoDownloadUpdate());
        }
        if (switchPromptInstall != null) {
            switchPromptInstall.setChecked(settings.isAutoDownloadUpdate()
                    && settings.isPromptInstallAfterAutoDownload());
        }
        updatePromptInstallControls(llPromptInstall, tvPromptInstall, switchPromptInstall,
                switchAutoDownload != null && switchAutoDownload.isChecked());

        if (switchAutoDownload != null) {
            switchAutoDownload.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updatePromptInstallControls(llPromptInstall, tvPromptInstall, switchPromptInstall, isChecked);
                if (switchPromptInstall != null && !isChecked) {
                    switchPromptInstall.setChecked(false);
                }
            });
        }
        if (llPromptInstall != null) {
            llPromptInstall.setOnClickListener(v -> {
                if (switchPromptInstall != null && switchPromptInstall.isEnabled()) {
                    switchPromptInstall.setChecked(!switchPromptInstall.isChecked());
                }
            });
        }

        LinearLayout llOpenDownloadDir = dialogView.findViewById(R.id.ll_open_download_dir);
        if (llOpenDownloadDir != null) {
            llOpenDownloadDir.setOnClickListener(v ->
                    UpdateManager.getInstance().openDownloadDirectory(fragment.requireActivity()));
        }

        com.google.android.material.button.MaterialButton btnCheckUpdate = dialogView.findViewById(R.id.btn_check_update);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                if (onCheckUpdate != null) {
                    onCheckUpdate.run();
                }
            });
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_version_update))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.settings_save), (dialog, which) -> {
                    settings.setAutoCheckUpdate(switchAutoCheck != null && switchAutoCheck.isChecked());
                    settings.setAcceptBetaUpdate(switchAcceptBeta != null && switchAcceptBeta.isChecked());
                    settings.setAutoDownloadUpdate(switchAutoDownload != null && switchAutoDownload.isChecked());
                    settings.setPromptInstallAfterAutoDownload(
                            (switchAutoDownload != null && switchAutoDownload.isChecked())
                            && (switchPromptInstall != null && switchPromptInstall.isChecked()));
                    settings.setUpdateSource(currentUpdateSource[0]);
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    private void updatePromptInstallControls(
            LinearLayout row,
            TextView label,
            MaterialSwitch switchPromptInstall,
            boolean enabled) {
        if (row != null) {
            row.setEnabled(enabled);
            row.setAlpha(enabled ? 1f : 0.45f);
        }
        if (label != null) {
            label.setEnabled(enabled);
        }
        if (switchPromptInstall != null) {
            switchPromptInstall.setEnabled(enabled);
        }
    }

    private void showColorSchemePicker(
            Context context,
            List<ColorSchemeManager.Scheme> schemes,
            int[] currentSchemeIndex,
            View vPrimary,
            View vSecondary,
            View vAccent,
            TextView tvSchemeName) {
        ColorSchemeAdapter adapter = new ColorSchemeAdapter(context, schemes, currentSchemeIndex[0]);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_color_scheme))
                .setAdapter(adapter, (d, which) -> {
                    currentSchemeIndex[0] = ColorSchemeManager.normalizeSchemeIndex(which);
                    updateColorSchemeRow(vPrimary, vSecondary, vAccent, tvSchemeName,
                            schemes.get(currentSchemeIndex[0]));

                    // Batch 3 (THEME_SWITCHER)：补全"最后一公里"
                    // 原行为：仅更新预览色块，关闭弹窗后选择丢失
                    // 新行为：持久化到 SharedPreferences + 立即应用配色 + 重建 Activity 让所有界面元素刷新
                    if (BuildConfig.THEME_SWITCHER) {
                        SettingsManager settings = SettingsManager.getInstance(context);
                        settings.setColorSchemeIndex(currentSchemeIndex[0]);
                        // 立即应用到当前 Activity（状态栏、导航栏、根视图背景等）
                        Activity activity = fragment.requireActivity();
                        App.refreshColorScheme(activity);
                        // 重建 Activity 让所有界面元素（卡片、按钮、文字等）重新读取主题色
                        activity.recreate();
                    }
                })
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                listView.setItemChecked(currentSchemeIndex[0], true);
            }
        });
        dialog.show();
    }

    private static void updateColorSchemeRow(
            View vPrimary,
            View vSecondary,
            View vAccent,
            TextView tvName,
            ColorSchemeManager.Scheme scheme) {
        tintSwatch(vPrimary, scheme.primary);
        tintSwatch(vSecondary, scheme.secondary);
        tintSwatch(vAccent, scheme.tabIndicator);
        if (tvName != null) {
            tvName.setText(scheme.name);
        }
    }

    private static void tintSwatch(View view, int color) {
        if (view != null && view.getBackground() != null) {
            view.getBackground().mutate().setTint(color);
        }
    }

    /**
     * 显示更新源选择器。
     * <p>
     * 2026-06-19: 已移除"美国 VPS"选项。使用过滤后的列表展示，但选中后映射回原始常量值。
     * 旧用户历史选择了美国 VPS 时，默认选中"自动"。
     * </p>
     */
    private void showUpdateSourcePicker(
            Context context,
            int currentSource,
            OnUpdateSourceSelectedListener listener) {
        // 2026-06-19: 美国 VPS 已下线，旧用户回退到"自动"
        int effectiveSource = (currentSource == SettingsManager.UPDATE_SOURCE_VPS_US)
                ? SettingsManager.UPDATE_SOURCE_AUTO : currentSource;

        // 构建过滤后的显示列表（跳过美国 VPS 空项）
        String[] allNames = {
                context.getString(R.string.settings_source_auto_recommended),   // 0 = AUTO
                context.getString(R.string.settings_source_hk_vps),             // 1 = VPS_HK
                "",                                                             // 2 = VPS_US (hidden)
                context.getString(R.string.settings_source_github)              // 3 = GITHUB
        };
        java.util.List<Integer> visibleIndices = new java.util.ArrayList<>();
        java.util.List<String> visibleNames = new java.util.ArrayList<>();
        for (int i = 0; i < allNames.length; i++) {
            if (!allNames[i].isEmpty()) {
                visibleIndices.add(i);
                visibleNames.add(allNames[i]);
            }
        }

        // 定位当前选中项在可见列表中的位置
        int checkedItem = 0;
        for (int i = 0; i < visibleIndices.size(); i++) {
            if (visibleIndices.get(i) == effectiveSource) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_update_source))
                .setSingleChoiceItems(visibleNames.toArray(new String[0]), checkedItem, (dialog, which) -> {
                    int originalSource = visibleIndices.get(which);
                    listener.onSelected(originalSource);
                    dialog.dismiss();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    // ==================== Feature B (SETTINGS_ENHANCE): 字号 + 缓存清理 ====================

    /**
     * 初始化 Feature B 的两行：字号 + 缓存清理。
     */
    private void initFeatureBRows(@NonNull View rootView, @NonNull SettingsManager settings) {
        Context context = fragment.requireContext();

        // ----- 字号行 -----
        LinearLayout llFontSize = rootView.findViewById(R.id.ll_font_size);
        TextView tvCurrentFontSize = rootView.findViewById(R.id.tv_current_font_size);
        if (tvCurrentFontSize != null) {
            tvCurrentFontSize.setText(getFontSizeLabel(settings.getFontSize()));
        }
        if (llFontSize != null) {
            llFontSize.setOnClickListener(v ->
                    showFontSizePicker(context, settings, tvCurrentFontSize));
        }

        // ----- 缓存清理行 -----
        LinearLayout llCacheClear = rootView.findViewById(R.id.ll_cache_clear);
        TextView tvCacheSize = rootView.findViewById(R.id.tv_cache_size);
        if (llCacheClear != null) {
            llCacheClear.setOnClickListener(v ->
                    showCacheClearDialog(context, tvCacheSize));
        }
        // 异步计算缓存大小
        updateCacheSizeLabel(tvCacheSize);
    }

    /** 弹出字号选择对话框。 */
    private void showFontSizePicker(@NonNull Context context,
                                    @NonNull SettingsManager settings,
                                    @Nullable TextView tvLabel) {
        String[] items = {
                context.getString(R.string.settings_font_size_small),
                context.getString(R.string.settings_font_size_medium),
                context.getString(R.string.settings_font_size_large)
        };
        int current = settings.getFontSize();
        int checkedItem;
        if (current == SettingsManager.FONT_SIZE_SMALL) {
            checkedItem = 0;
        } else if (current == SettingsManager.FONT_SIZE_LARGE) {
            checkedItem = 2;
        } else {
            checkedItem = 1;
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_font_size))
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    int newFontSize;
                    if (which == 0) {
                        newFontSize = SettingsManager.FONT_SIZE_SMALL;
                    } else if (which == 2) {
                        newFontSize = SettingsManager.FONT_SIZE_LARGE;
                    } else {
                        newFontSize = SettingsManager.FONT_SIZE_MEDIUM;
                    }
                    settings.setFontSize(newFontSize);
                    if (tvLabel != null) {
                        tvLabel.setText(getFontSizeLabel(newFontSize));
                    }
                    dialog.dismiss();
                    // 重建当前 Activity 让字号生效
                    fragment.requireActivity().recreate();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    /** 弹出缓存清理确认对话框。 */
    private void showCacheClearDialog(@NonNull Context context, @Nullable TextView tvCacheSize) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_cache_clear))
                .setMessage(context.getString(R.string.settings_cache_clear_desc))
                .setPositiveButton(context.getString(R.string.settings_ok), (dialog, which) -> {
                    boolean ok = clearAppCache();
                    Toast.makeText(context,
                            ok ? R.string.settings_cache_cleared
                                    : R.string.settings_cache_clear_failed,
                            Toast.LENGTH_SHORT).show();
                    if (ok) {
                        updateCacheSizeLabel(tvCacheSize);
                    }
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    /**
     * 异步计算并显示当前缓存大小。
     */
    private void updateCacheSizeLabel(@Nullable TextView tvCacheSize) {
        if (tvCacheSize == null) return;
        Context context = fragment.requireContext();
        tvCacheSize.setText(context.getString(R.string.settings_cache_calculating));
        IO_EXECUTOR.execute(() -> {
            long size = getCacheSizeBytes(context);
            String human = formatSize(size);
            tvCacheSize.post(() -> {
                if (size <= 0) {
                    tvCacheSize.setText(context.getString(R.string.settings_cache_empty));
                } else {
                    tvCacheSize.setText(context.getString(R.string.settings_cache_size, human));
                }
            });
        });
    }

    /** 递归计算缓存目录总大小（字节）。 */
    private static long getCacheSizeBytes(@NonNull Context context) {
        long total = 0;
        total += dirSize(context.getCacheDir());
        File externalCache = context.getExternalCacheDir();
        if (externalCache != null) {
            total += dirSize(externalCache);
        }
        return total;
    }

    /** 递归计算目录大小。 */
    private static long dirSize(@NonNull File dir) {
        if (!dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                size += dirSize(f);
            } else {
                size += f.length();
            }
        }
        return size;
    }

    /** 格式化字节大小为可读字符串。 */
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** 清理应用缓存。返回是否成功。 */
    private boolean clearAppCache() {
        try {
            Context context = fragment.requireContext();
            deleteRecursive(context.getCacheDir());
            File external = context.getExternalCacheDir();
            if (external != null) {
                deleteRecursive(external);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 递归删除目录及子项。 */
    private static void deleteRecursive(@NonNull File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDir.delete();
    }

    private static class ColorSchemeAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater inflater;
        private final List<ColorSchemeManager.Scheme> schemes;
        private final int selectedIndex;

        ColorSchemeAdapter(Context context, List<ColorSchemeManager.Scheme> schemes, int selectedIndex) {
            this.inflater = LayoutInflater.from(context);
            this.schemes = schemes;
            this.selectedIndex = selectedIndex;
        }

        @Override
        public int getCount() {
            return schemes.size();
        }

        @Override
        public Object getItem(int position) {
            return schemes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item_color_scheme, parent, false);
            }

            ColorSchemeManager.Scheme scheme = schemes.get(position);
            tintSwatch(view.findViewById(R.id.v_color_primary), scheme.primary);
            tintSwatch(view.findViewById(R.id.v_color_secondary), scheme.secondary);
            tintSwatch(view.findViewById(R.id.v_color_accent), scheme.tabIndicator);

            TextView tvName = view.findViewById(R.id.tv_scheme_name);
            if (tvName != null) {
                tvName.setText(scheme.name);
            }

            android.widget.RadioButton rbSelected = view.findViewById(R.id.rb_selected);
            if (rbSelected != null) {
                rbSelected.setChecked(position == selectedIndex);
            }
            return view;
        }
    }
}
