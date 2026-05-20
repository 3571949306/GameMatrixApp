package com.gamecenter.app.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.List;

/**
 * 应用设置对话框。
 * <p>
 * 负责展示和管理游戏中心应用的全部设置项，包括：
 * <ul>
 *   <li>主题模式（跟随系统 / 浅色 / 深色）</li>
 *   <li>应用语言（跟随系统 / 中文 / 英文）</li>
 *   <li>配色方案选择</li>
 *   <li>版本更新设置（更新源、自动检查、Beta 通道、自动下载、下载后提示安装）</li>
 *   <li>检查更新、用户反馈、查看统计等快捷操作</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@code currentSchemeIndex[0]}（int 数组）在内部类 / Lambda 中实现"可变的局部变量"，
 *       因为 Java Lambda 只能捕获 effectively final 的变量，数组引用不变但元素可变</li>
 *   <li>设置变更后仅在主题、配色或语言确实发生变化时才调用 {@code activity.recreate()} 重建 Activity，
 *       避免不必要的界面闪烁</li>
 *   <li>更新设置子对话框中，"下载后提示安装"开关依赖于"自动下载"开关的状态，
 *       当自动下载关闭时，提示安装开关会被禁用并置灰</li>
 * </ul>
 */
public class AppSettingsDialog {

    /**
     * 更新源选择回调接口。
     * 当用户在更新源选择器中点选某一项后触发。
     */
    private interface OnUpdateSourceSelectedListener {
        /**
         * 用户选择了某个更新源时调用。
         *
         * @param source 选中的更新源索引，0=自动, 1=香港VPS, 2=美国VPS, 3=GitHub Releases
         */
        void onSelected(int source);
    }

    private final Fragment fragment;
    private final Runnable onCheckUpdate;
    private final Runnable onFeedback;

    /**
     * 获取更新源的显示名称数组。
     *
     * @return 更新源名称数组，索引与 {@link SettingsManager#getUpdateSource()} 的值对应
     */
    private String[] getUpdateSourceNames() {
        return new String[]{fragment.requireContext().getString(R.string.settings_source_auto), fragment.requireContext().getString(R.string.settings_source_hk_vps), fragment.requireContext().getString(R.string.settings_source_us_vps), fragment.requireContext().getString(R.string.settings_source_github)};
    }

    /**
     * 根据更新源索引获取其显示名称。
     *
     * @param source 更新源索引
     * @return 对应的显示名称；若索引越界则返回"自动"
     */
    private String getUpdateSourceName(int source) {
        String[] names = getUpdateSourceNames();
        if (source >= 0 && source < names.length) return names[source];
        return fragment.requireContext().getString(R.string.settings_source_auto);
    }

    /**
     * 构造应用设置对话框。
     *
     * @param fragment      宿主 Fragment，用于获取 Context 和 Activity，以及判断是否已添加到 Activity
     * @param onCheckUpdate 检查更新回调，可为 null；当用户点击"检查更新"按钮时触发
     * @param onFeedback    用户反馈回调，可为 null；当用户点击"反馈"按钮时触发
     */
    public AppSettingsDialog(
            @NonNull Fragment fragment,
            @Nullable Runnable onCheckUpdate,
            @Nullable Runnable onFeedback) {
        this.fragment = fragment;
        this.onCheckUpdate = onCheckUpdate;
        this.onFeedback = onFeedback;
    }

    /**
     * 显示应用设置对话框。
     * <p>
     * 对话框包含主题模式、语言、配色方案、版本信息、更新设置等设置项。
     * 用户点击"确定"后，会检测设置是否发生变化，仅在确实变化时保存设置并重建 Activity。
     * 点击"取消"则丢弃所有修改。
     */
    public void show() {
        if (!fragment.isAdded()) {
            return;
        }

        Context context = fragment.requireContext();
        Activity activity = fragment.requireActivity();
        SettingsManager settings = SettingsManager.getInstance(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null);

        RadioGroup rgThemeMode = dialogView.findViewById(R.id.rg_theme_mode);
        RadioButton rbSystem = dialogView.findViewById(R.id.rb_system);
        RadioButton rbLight = dialogView.findViewById(R.id.rb_light);
        RadioButton rbDark = dialogView.findViewById(R.id.rb_dark);
        RadioGroup rgLanguageMode = dialogView.findViewById(R.id.rg_language_mode);
        RadioButton rbLanguageSystem = dialogView.findViewById(R.id.rb_language_system);
        RadioButton rbLanguageZh = dialogView.findViewById(R.id.rb_language_zh);
        RadioButton rbLanguageEn = dialogView.findViewById(R.id.rb_language_en);

        int currentTheme = settings.getThemeMode();
        if (currentTheme == SettingsManager.THEME_LIGHT) {
            rbLight.setChecked(true);
        } else if (currentTheme == SettingsManager.THEME_DARK) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        String currentLanguage = settings.getAppLanguage();
        if (SettingsManager.LANGUAGE_ZH.equals(currentLanguage)) {
            rbLanguageZh.setChecked(true);
        } else if (SettingsManager.LANGUAGE_EN.equals(currentLanguage)) {
            rbLanguageEn.setChecked(true);
        } else {
            rbLanguageSystem.setChecked(true);
        }

        List<ColorSchemeManager.Scheme> schemes = ColorSchemeManager.getSchemes();
        final int[] currentSchemeIndex = {
                ColorSchemeManager.normalizeSchemeIndex(settings.getColorSchemeIndex())
        };

        LinearLayout llColorScheme = dialogView.findViewById(R.id.ll_color_scheme);
        View vPrimary = dialogView.findViewById(R.id.v_color_primary);
        View vSecondary = dialogView.findViewById(R.id.v_color_secondary);
        View vAccent = dialogView.findViewById(R.id.v_color_accent);
        TextView tvSchemeName = dialogView.findViewById(R.id.tv_scheme_name);
        updateColorSchemeRow(vPrimary, vSecondary, vAccent, tvSchemeName,
                schemes.get(currentSchemeIndex[0]));

        llColorScheme.setOnClickListener(v -> showColorSchemePicker(
                context, schemes, currentSchemeIndex, vPrimary, vSecondary, vAccent, tvSchemeName));

        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? " beta" : " 正式版";
        if (tvVersion != null) {
            tvVersion.setText(context.getString(R.string.settings_current_version) + BuildConfig.VERSION_NAME + channelLabel
                    + "\n内部版本号: " + BuildConfig.VERSION_CODE);
        }

        LinearLayout llUpdateSettings = dialogView.findViewById(R.id.ll_update_settings);
        if (llUpdateSettings != null) {
            llUpdateSettings.setOnClickListener(v -> showUpdateSettingsDialog(context, settings));
        }

        MaterialButton btnCheckUpdate = dialogView.findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> {
            if (onCheckUpdate != null) {
                onCheckUpdate.run();
            }
        });

        MaterialButton btnFeedback = dialogView.findViewById(R.id.btn_feedback);
        btnFeedback.setOnClickListener(v -> {
            if (onFeedback != null) {
                onFeedback.run();
            }
        });

        MaterialButton btnViewStats = dialogView.findViewById(R.id.btn_view_stats);
        btnViewStats.setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, StatsActivity.class));
        });

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_title))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.settings_ok), (dialog, which) -> {
                    int checkedId = rgThemeMode.getCheckedRadioButtonId();
                    int newThemeMode;
                    if (checkedId == R.id.rb_light) {
                        newThemeMode = SettingsManager.THEME_LIGHT;
                    } else if (checkedId == R.id.rb_dark) {
                        newThemeMode = SettingsManager.THEME_DARK;
                    } else {
                        newThemeMode = SettingsManager.THEME_SYSTEM;
                    }

                    int originalScheme = ColorSchemeManager.normalizeSchemeIndex(
                            settings.getColorSchemeIndex());
                    boolean themeChanged = newThemeMode != settings.getThemeMode();
                    boolean schemeChanged = currentSchemeIndex[0] != originalScheme;

                    String originalLanguage = settings.getAppLanguage();
                    String newLanguage;
                    int checkedLanguageId = rgLanguageMode.getCheckedRadioButtonId();
                    if (checkedLanguageId == R.id.rb_language_zh) {
                        newLanguage = SettingsManager.LANGUAGE_ZH;
                    } else if (checkedLanguageId == R.id.rb_language_en) {
                        newLanguage = SettingsManager.LANGUAGE_EN;
                    } else {
                        newLanguage = SettingsManager.LANGUAGE_SYSTEM;
                    }
                    boolean languageChanged = !newLanguage.equals(originalLanguage);

                    settings.setThemeMode(newThemeMode);
                    settings.setColorSchemeIndex(currentSchemeIndex[0]);
                    settings.setAppLanguage(newLanguage);

                    if (languageChanged) {
                        AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(newLanguage));
                    }

                    if (themeChanged || schemeChanged || languageChanged) {
                        if (activity.getApplication() instanceof App) {
                            ((App) activity.getApplication()).applyTheme();
                        }
                        activity.recreate();
                    }
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    /**
     * 显示版本更新设置子对话框。
     * <p>
     * 包含更新源选择、自动检查更新开关、接受 Beta 更新开关、
     * 自动下载更新开关（控制"下载后提示安装"的可用状态）、
     * 打开下载目录以及手动检查更新等选项。
     *
     * @param context  上下文，用于创建对话框和加载布局
     * @param settings 设置管理器实例，用于读写更新相关偏好
     */
    private void showUpdateSettingsDialog(Context context, SettingsManager settings) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_settings, null);

        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? " beta" : " 正式版";
        tvVersion.setText(context.getString(R.string.settings_current_version) + BuildConfig.VERSION_NAME + channelLabel
                + "\n内部版本号: " + BuildConfig.VERSION_CODE);

        LinearLayout llUpdateSource = dialogView.findViewById(R.id.ll_update_source);
        TextView tvUpdateSource = dialogView.findViewById(R.id.tv_update_source);
        final int[] currentUpdateSource = {settings.getUpdateSource()};
        tvUpdateSource.setText(getUpdateSourceName(currentUpdateSource[0]));

        llUpdateSource.setOnClickListener(v -> showUpdateSourcePicker(
                context, currentUpdateSource[0], source -> {
                    tvUpdateSource.setText(getUpdateSourceName(source));
                    currentUpdateSource[0] = source;
                }));

        MaterialSwitch switchAutoCheck = dialogView.findViewById(R.id.switch_auto_check);
        switchAutoCheck.setChecked(settings.isAutoCheckUpdate());

        MaterialSwitch switchAcceptBeta = dialogView.findViewById(R.id.switch_accept_beta);
        switchAcceptBeta.setChecked(settings.isAcceptBetaUpdate());

        MaterialSwitch switchAutoDownload = dialogView.findViewById(R.id.switch_auto_download_update);
        MaterialSwitch switchPromptInstall = dialogView.findViewById(
                R.id.switch_prompt_install_after_download);
        LinearLayout llPromptInstall = dialogView.findViewById(
                R.id.ll_prompt_install_after_download);
        TextView tvPromptInstall = dialogView.findViewById(
                R.id.tv_prompt_install_after_download);

        switchAutoDownload.setChecked(settings.isAutoDownloadUpdate());
        switchPromptInstall.setChecked(settings.isAutoDownloadUpdate()
                && settings.isPromptInstallAfterAutoDownload());
        updatePromptInstallControls(llPromptInstall, tvPromptInstall, switchPromptInstall,
                switchAutoDownload.isChecked());

        switchAutoDownload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePromptInstallControls(llPromptInstall, tvPromptInstall, switchPromptInstall, isChecked);
            if (!isChecked) {
                switchPromptInstall.setChecked(false);
            }
        });
        llPromptInstall.setOnClickListener(v -> {
            if (switchPromptInstall.isEnabled()) {
                switchPromptInstall.setChecked(!switchPromptInstall.isChecked());
            }
        });

        MaterialButton btnOpenDownloadDir = dialogView.findViewById(R.id.btn_open_download_dir);
        btnOpenDownloadDir.setOnClickListener(v ->
                UpdateManager.getInstance().openDownloadDirectory(fragment.requireActivity()));

        MaterialButton btnCheckUpdate = dialogView.findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> {
            if (onCheckUpdate != null) {
                onCheckUpdate.run();
            }
        });

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_version_update))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.settings_save), (dialog, which) -> {
                    settings.setAutoCheckUpdate(switchAutoCheck.isChecked());
                    settings.setAcceptBetaUpdate(switchAcceptBeta.isChecked());
                    settings.setAutoDownloadUpdate(switchAutoDownload.isChecked());
                    settings.setPromptInstallAfterAutoDownload(
                            switchAutoDownload.isChecked() && switchPromptInstall.isChecked());
                    settings.setUpdateSource(currentUpdateSource[0]);
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    /**
     * 根据"自动下载"开关的状态，更新"下载后提示安装"相关控件的可用性和视觉表现。
     * <p>
     * 当自动下载关闭时，提示安装行、标签和开关均被禁用，透明度降低至 0.45 以示不可用；
     * 当自动下载开启时，恢复为可用状态（透明度 1.0）。
     *
     * @param row               "下载后提示安装"整行容器
     * @param label             "下载后提示安装"文字标签
     * @param switchPromptInstall 提示安装开关
     * @param enabled           是否启用（即自动下载是否开启）
     */
    private void updatePromptInstallControls(
            LinearLayout row,
            TextView label,
            MaterialSwitch switchPromptInstall,
            boolean enabled) {
        row.setEnabled(enabled);
        label.setEnabled(enabled);
        switchPromptInstall.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
    }

    /**
     * 显示配色方案选择器对话框。
     * <p>
     * 使用自定义的 {@link ColorSchemeAdapter} 展示所有可选配色方案，
     * 每项显示主色、次色和强调色的色块以及方案名称。
     * 用户选择后，立即更新主对话框中的配色预览行。
     *
     * @param context            上下文
     * @param schemes            所有可选配色方案列表
     * @param currentSchemeIndex 当前选中方案索引（使用 int 数组以便在 Lambda 中修改）
     * @param vPrimary           主色预览色块
     * @param vSecondary         次色预览色块
     * @param vAccent            强调色预览色块
     * @param tvSchemeName       方案名称文本
     */
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

    /**
     * 更新主对话框中配色方案预览行的色块和名称。
     *
     * @param vPrimary   主色色块视图
     * @param vSecondary 次色色块视图
     * @param vAccent    强调色色块视图
     * @param tvName     方案名称文本视图
     * @param scheme     要展示的配色方案
     */
    private static void updateColorSchemeRow(
            View vPrimary,
            View vSecondary,
            View vAccent,
            TextView tvName,
            ColorSchemeManager.Scheme scheme) {
        tintSwatch(vPrimary, scheme.primary);
        tintSwatch(vSecondary, scheme.secondary);
        tintSwatch(vAccent, scheme.tabIndicator);
        tvName.setText(scheme.name);
    }

    /**
     * 为色块视图的背景着色。
     * <p>
     * 调用 {@code mutate()} 确保此视图的 Drawable 拥有独立的状态副本，
     * 避免与其他共享同一 Drawable 资源的视图产生着色干扰。
     *
     * @param view  要着色的视图，若为 null 或无背景则跳过
     * @param color 目标颜色值
     */
    private static void tintSwatch(View view, int color) {
        if (view != null && view.getBackground() != null) {
            view.getBackground().mutate().setTint(color);
        }
    }

    /**
     * 显示更新源选择器对话框。
     * <p>
     * 以单选列表形式展示所有可选更新源，用户点选后立即回调通知选择结果并关闭对话框。
     *
     * @param context       上下文
     * @param currentSource 当前选中的更新源索引
     * @param listener      选择回调，用户点选某项后触发
     */
    private void showUpdateSourcePicker(
            Context context,
            int currentSource,
            OnUpdateSourceSelectedListener listener) {
        String[] items = {context.getString(R.string.settings_source_auto_recommended), context.getString(R.string.settings_source_hk_vps), context.getString(R.string.settings_source_us_vps), context.getString(R.string.settings_source_github)};
        int checkedItem = currentSource >= 0 && currentSource <= 3 ? currentSource : 0;
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_select_update_source))
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    listener.onSelected(which);
                    dialog.dismiss();
                })
                .setNegativeButton(context.getString(R.string.settings_cancel), null)
                .show();
    }

    /**
     * 配色方案列表适配器。
     * <p>
     * 用于在配色方案选择对话框中展示每个方案的色块预览和名称，
     * 并通过 RadioButton 标识当前选中项。
     */
    private static class ColorSchemeAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater inflater;
        private final List<ColorSchemeManager.Scheme> schemes;
        private final int selectedIndex;

        /**
         * 构造配色方案适配器。
         *
         * @param context      上下文，用于获取 LayoutInflater
         * @param schemes      所有可选配色方案列表
         * @param selectedIndex 当前选中方案的索引位置
         */
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

        /**
         * 获取列表项视图。
         * <p>
         * 每项展示主色、次色、强调色的色块，方案名称，以及选中状态 RadioButton。
         * 复用 convertView 以优化列表滚动性能。
         *
         * @param position    列表项位置
         * @param convertView 可复用的旧视图，若为 null 则新建
         * @param parent      父视图组
         * @return 填充好数据的列表项视图
         */
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
            tvName.setText(scheme.name);

            RadioButton rbSelected = view.findViewById(R.id.rb_selected);
            rbSelected.setChecked(position == selectedIndex);
            return view;
        }
    }
}
