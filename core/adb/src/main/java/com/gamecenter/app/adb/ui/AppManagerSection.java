package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Application management section: list, search, filter, install, operations.
 */
public final class AppManagerSection extends BaseSection {

    private static final int REQUEST_PICK_APK = 1001;
    private String currentFilter = "all";
    private String searchQuery = "";
    private ArrayAdapter<AdbEngine.AppInfo> appAdapter;
    private List<AdbEngine.AppInfo> allApps = new ArrayList<>();
    private ListView appList;
    private View rootView;

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_app_manager, null);
        this.rootView = view;

        appList = view.findViewById(R.id.adb_app_list);
        EditText search = view.findViewById(R.id.adb_app_search);
        if (search != null) {
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s.toString().toLowerCase();
                    filterApps();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        TextView filterBtn = view.findViewById(R.id.adb_app_filter);
        if (filterBtn != null) filterBtn.setOnClickListener(v -> showFilterDialog());

        TextView installBtn = view.findViewById(R.id.adb_app_install);
        if (installBtn != null) installBtn.setOnClickListener(v -> pickApkFile());

        TextView installLocalBtn = view.findViewById(R.id.adb_app_install_local);
        if (installLocalBtn != null) installLocalBtn.setOnClickListener(v -> installFromLocal());

        appList.setOnItemLongClickListener((parent, view1, position, id) -> {
            AdbEngine.AppInfo info = appAdapter.getItem(position);
            if (info != null) showAppActions(info);
            return true;
        });

        appList.setOnItemClickListener((parent, v, position, id) -> {
            AdbEngine.AppInfo info = appAdapter.getItem(position);
            if (info != null) launchApp(info);
        });

        appAdapter = new AppInfoAdapter(activity, android.R.layout.simple_list_item_1);
        appList.setAdapter(appAdapter);

        return view;
    }

    private void showFilterDialog() {
        Activity act = activity();
        if (act == null) return;
        String[] options = {
                act.getString(R.string.adb_app_filter_all),
                act.getString(R.string.adb_app_filter_system),
                act.getString(R.string.adb_app_filter_user)
        };
        int checked = 0;
        switch (currentFilter) {
            case "system": checked = 1; break;
            case "user": checked = 2; break;
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle("筛选")
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    switch (which) {
                        case 0: currentFilter = "all"; break;
                        case 1: currentFilter = "system"; break;
                        case 2: currentFilter = "user"; break;
                    }
                    filterApps();
                    ((TextView) rootView.findViewById(R.id.adb_app_filter)).setText(options[which]);
                })
                .show();
    }

    private void filterApps() {
        List<AdbEngine.AppInfo> filtered = new ArrayList<>();
        for (AdbEngine.AppInfo app : allApps) {
            if (!currentFilter.equals("all")) {
                if (currentFilter.equals("system") && !app.isSystem) continue;
                if (currentFilter.equals("user") && app.isSystem) continue;
            }
            if (!searchQuery.isEmpty()) {
                if (!app.packageName.toLowerCase().contains(searchQuery) &&
                        !app.label.toLowerCase().contains(searchQuery)) continue;
            }
            filtered.add(app);
        }
        appAdapter.clear();
        appAdapter.addAll(filtered);
        appAdapter.notifyDataSetChanged();
    }

    private void showAppActions(AdbEngine.AppInfo info) {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_app_no_device));
            return;
        }

        String[] actions = {
                act.getString(R.string.adb_app_launch),
                act.getString(R.string.adb_app_force_stop),
                info.enabled ? act.getString(R.string.adb_app_disable) : act.getString(R.string.adb_app_enable),
                act.getString(R.string.adb_app_clear_data),
                act.getString(R.string.adb_app_uninstall)
        };

        new android.app.AlertDialog.Builder(act)
                .setTitle(info.label + "\n" + info.packageName)
                .setItems(actions, (d, which) -> {
                    String pkg = info.packageName;
                    switch (which) {
                        case 0: engine().launchApp(selected.id, pkg); break;
                        case 1: engine().forceStopApp(selected.id, pkg); break;
                        case 2: engine().setAppEnabled(selected.id, pkg, !info.enabled); break;
                        case 3:
                            new android.app.AlertDialog.Builder(act)
                                    .setMessage(act.getString(R.string.adb_app_clear_confirm, pkg))
                                    .setPositiveButton(R.string.adb_ok, (d2, w2) ->
                                            engine().clearAppData(selected.id, pkg))
                                    .setNegativeButton(R.string.adb_cancel, null)
                                    .show();
                            break;
                        case 4:
                            if (info.isSystem) {
                                showBottomMessage("系统应用不可卸载，可尝试停用");
                            } else {
                                new android.app.AlertDialog.Builder(act)
                                        .setMessage(act.getString(R.string.adb_app_uninstall_confirm, pkg))
                                        .setPositiveButton(R.string.adb_ok, (d2, w2) ->
                                                engine().uninstallApp(selected.id, pkg))
                                        .setNegativeButton(R.string.adb_cancel, null)
                                        .show();
                            }
                            break;
                    }
                })
                .show();
    }

    private void launchApp(AdbEngine.AppInfo info) {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_app_no_device));
            return;
        }
        engine().launchApp(selected.id, info.packageName);
    }

    private void pickApkFile() {
        Activity act = activity();
        if (act == null) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        act.startActivityForResult(Intent.createChooser(intent, act.getString(R.string.adb_app_select_apk)), REQUEST_PICK_APK);
    }

    private void installFromLocal() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        showBottomMessage("从本机安装功能开发中");
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        Activity act = activity();
        if (act == null) return;
        AdbEngine.Session selected = engine.selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_app_no_device));
            return;
        }
        showBottomMessage(act.getString(R.string.adb_loading));
        // First trigger async list with filter, then sync load after brief delay
        engine.listApps(selected.id, "");
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            List<AdbEngine.AppInfo> apps = engine.listApps(selected.id);
            act.runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(apps);
                filterApps();
                showBottomMessage("已加载 " + apps.size() + " 个应用");
            });
        }).start();
    }

    @Override
    protected void onEngineUnbound() {
        allApps.clear();
        if (appAdapter != null) {
            appAdapter.clear();
            appAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroy() {
        activityRef = null;
    }

    private static class AppInfoAdapter extends ArrayAdapter<AdbEngine.AppInfo> {
        AppInfoAdapter(android.content.Context context, int resource) {
            super(context, resource);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            AdbEngine.AppInfo info = getItem(position);
            if (info != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(info.label != null ? info.label : info.packageName);
                sb.append("\n").append(info.packageName);
                if (!info.enabled) sb.append(" [已停用]");
                if (info.isSystem) sb.append(" [系统]");
                ((TextView) view).setText(sb.toString());
            }
            return view;
        }
    }
}
