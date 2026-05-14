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

public class AppSettingsDialog {

    private interface OnUpdateSourceSelectedListener {
        void onSelected(int source);
    }

    private final Fragment fragment;
    private final Runnable onCheckUpdate;
    private final Runnable onFeedback;

    private String[] getUpdateSourceNames() {
        return new String[]{"自动", "香港 VPS", "美国 VPS", "GitHub Releases"};
    }

    private String getUpdateSourceName(int source) {
        String[] names = getUpdateSourceNames();
        if (source >= 0 && source < names.length) return names[source];
        return "自动";
    }

    public AppSettingsDialog(
            @NonNull Fragment fragment,
            @Nullable Runnable onCheckUpdate,
            @Nullable Runnable onFeedback) {
        this.fragment = fragment;
        this.onCheckUpdate = onCheckUpdate;
        this.onFeedback = onFeedback;
    }

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
        int currentTheme = settings.getThemeMode();
        if (currentTheme == SettingsManager.THEME_LIGHT) {
            rbLight.setChecked(true);
        } else if (currentTheme == SettingsManager.THEME_DARK) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
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
            tvVersion.setText("当前版本: " + BuildConfig.VERSION_NAME + channelLabel
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
                .setTitle("设置")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
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

                    settings.setThemeMode(newThemeMode);
                    settings.setColorSchemeIndex(currentSchemeIndex[0]);

                    if (themeChanged || schemeChanged) {
                        if (activity.getApplication() instanceof App) {
                            ((App) activity.getApplication()).applyTheme();
                        }
                        activity.recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showUpdateSettingsDialog(Context context, SettingsManager settings) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_settings, null);

        TextView tvVersion = dialogView.findViewById(R.id.tv_current_version);
        String channelLabel = "beta".equalsIgnoreCase(BuildConfig.VERSION_CHANNEL) ? " beta" : " 正式版";
        tvVersion.setText("当前版本: " + BuildConfig.VERSION_NAME + channelLabel
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
                .setTitle("版本更新")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    settings.setAutoCheckUpdate(switchAutoCheck.isChecked());
                    settings.setAcceptBetaUpdate(switchAcceptBeta.isChecked());
                    settings.setAutoDownloadUpdate(switchAutoDownload.isChecked());
                    settings.setPromptInstallAfterAutoDownload(
                            switchAutoDownload.isChecked() && switchPromptInstall.isChecked());
                    settings.setUpdateSource(currentUpdateSource[0]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

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
                .setTitle("选择配色方案")
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
        tvName.setText(scheme.name);
    }

    private static void tintSwatch(View view, int color) {
        if (view != null && view.getBackground() != null) {
            view.getBackground().mutate().setTint(color);
        }
    }

    private void showUpdateSourcePicker(
            Context context,
            int currentSource,
            OnUpdateSourceSelectedListener listener) {
        String[] items = {"自动（推荐）", "香港 VPS", "美国 VPS", "GitHub Releases"};
        int checkedItem = currentSource >= 0 && currentSource <= 3 ? currentSource : 0;
        new AlertDialog.Builder(context)
                .setTitle("选择更新源")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    listener.onSelected(which);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
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
            tvName.setText(scheme.name);

            RadioButton rbSelected = view.findViewById(R.id.rb_selected);
            rbSelected.setChecked(position == selectedIndex);
            return view;
        }
    }
}
