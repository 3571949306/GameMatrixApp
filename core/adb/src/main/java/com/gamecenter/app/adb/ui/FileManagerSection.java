package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

import java.util.ArrayList;
import java.util.List;

/**
 * File manager section: browse, upload, download, delete, rename.
 * Uses engine-level path validation (blocking traversal, root deletion).
 */
public final class FileManagerSection extends BaseSection {

    private static final String ROOT = "/sdcard";
    private String currentPath = ROOT;
    private List<String> pathStack = new ArrayList<>();
    private ListView fileList;
    private TextView pathDisplay;
    private TextView selectionInfo;
    private ArrayAdapter<String> fileAdapter;
    private List<AdbEngine.FileInfo> currentFiles = new ArrayList<>();

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_file_manager, null);

        fileList = view.findViewById(R.id.adb_file_list);
        pathDisplay = view.findViewById(R.id.adb_file_path);
        selectionInfo = view.findViewById(R.id.adb_file_selection_info);

        TextView backBtn = view.findViewById(R.id.adb_file_back);
        if (backBtn != null) backBtn.setOnClickListener(v -> navigateUp());

        TextView newDirBtn = view.findViewById(R.id.adb_file_new_dir);
        if (newDirBtn != null) newDirBtn.setOnClickListener(v -> createDirectory());

        TextView uploadBtn = view.findViewById(R.id.adb_file_upload);
        if (uploadBtn != null) uploadBtn.setOnClickListener(v -> uploadFile());

        TextView downloadBtn = view.findViewById(R.id.adb_file_download);
        if (downloadBtn != null) downloadBtn.setOnClickListener(v -> downloadSelected());

        fileList.setOnItemLongClickListener((parent, view1, position, id) -> {
            fileList.setItemChecked(position, !fileList.isItemChecked(position));
            updateSelectionInfo();
            return true;
        });

        fileList.setOnItemClickListener((parent, v, position, id) -> {
            AdbEngine.FileInfo info = currentFiles.get(position);
            if (info.isDirectory) {
                navigateTo(info.path);
            }
        });

        fileAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_multiple_choice);
        fileList.setAdapter(fileAdapter);
        fileList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        updatePathDisplay();
        return view;
    }

    private void navigateTo(String path) {
        pathStack.add(currentPath);
        currentPath = path;
        updatePathDisplay();
        loadFiles();
    }

    private void navigateUp() {
        if (pathStack.isEmpty()) return;
        currentPath = pathStack.remove(pathStack.size() - 1);
        updatePathDisplay();
        loadFiles();
    }

    private void updatePathDisplay() {
        if (pathDisplay != null) {
            pathDisplay.setText("当前路径：" + currentPath);
        }
    }

    private void updateSelectionInfo() {
        if (selectionInfo == null) return;
        int count = 0;
        for (int i = 0; i < fileList.getCount(); i++) {
            if (fileList.isItemChecked(i)) count++;
        }
        selectionInfo.setText(count > 0 ? "已选择 " + count + " 项" : "");
    }

    private void loadFiles() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_file_no_device));
            return;
        }
        showBottomMessage(act.getString(R.string.adb_loading));
        new Thread(() -> {
            engine().listFiles(selected.id, currentPath);
            act.runOnUiThread(() -> {
                currentFiles.clear();
                List<AdbEngine.FileInfo> files = engine().listFileEntries(selected.id);
                currentFiles.addAll(files);
                fileAdapter.clear();
                for (AdbEngine.FileInfo f : files) {
                    String prefix = f.isDirectory ? "[DIR] " : "";
                    String size = f.isDirectory ? "" : " (" + formatSize(f.size) + ")";
                    fileAdapter.add(prefix + f.name + size);
                }
                fileAdapter.notifyDataSetChanged();
                updateSelectionInfo();
            });
        }).start();
    }

    private void createDirectory() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_file_no_device));
            return;
        }
        final EditText input = new EditText(act);
        input.setHint(R.string.adb_file_new_dir_hint);
        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_file_btn_new_dir)
                .setView(input)
                .setPositiveButton(R.string.adb_ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        engine().createDirectory(selected.id, currentPath + "/" + name);
                        loadFiles();
                    }
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    private void uploadFile() {
        Activity act = activity();
        if (act == null) return;
        showBottomMessage("上传功能开发中");
    }

    private void downloadSelected() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_file_no_device));
            return;
        }
        List<String> selectedFiles = new ArrayList<>();
        for (int i = 0; i < fileList.getCount(); i++) {
            if (fileList.isItemChecked(i)) {
                AdbEngine.FileInfo info = currentFiles.get(i);
                if (!info.isDirectory) selectedFiles.add(info.path);
            }
        }
        if (selectedFiles.isEmpty()) {
            showBottomMessage("请先选择文件");
            return;
        }
        showBottomMessage("下载功能开发中");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        loadFiles();
    }

    @Override
    protected void onEngineUnbound() {
        currentFiles.clear();
        fileAdapter.clear();
        if (fileAdapter != null) fileAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        activityRef = null;
    }
}
