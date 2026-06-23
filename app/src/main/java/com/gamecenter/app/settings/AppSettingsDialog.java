package com.gamecenter.app.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import java.util.List;

/**
 * 应用设置对话框。
 * 对标主流上市 app 的设计规范，采用卡片分组 + 图标 + 说明文案的布局方式。
 */
public class AppSettingsDialog {

    private interface OnUpdateSourceSelectedListener {
        void onSelected(int source);
    }

    private final Fragment fragment;
    private final Runnable onCheckUpdate;
    private final Runnable onFeedback;

    public AppSettingsDialog(
            @NonNull Fragment fragment,
            @Nullable Runnable onCheckUpdate,
            @Nullable Runnable onFeedback) {
        this.fragment = fragment;
        this.onCheckUpdate = onCheckUpdate;
        this.onFeedback = onFeedback;
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
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? " Beta" : "";
        if (tvVersion != null) {
            tvVersion.setText("版本 " + BuildConfig.VERSION_NAME + channelLabel
                    + " · 内部版本 " + BuildConfig.VERSION_CODE);
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

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_title))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.settings_ok), null)
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
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
                    fragment.requireActivity().recreate();
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
                    fragment.requireActivity().recreate();
                    dialog.dismiss();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    private void showUpdateSettingsDialog(Context context, SettingsManager settings) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_settings, null);

        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? " Beta" : "";
        if (tvVersion != null) {
            tvVersion.setText("版本 " + BuildConfig.VERSION_NAME + channelLabel
                    + " · 内部版本 " + BuildConfig.VERSION_CODE);
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
