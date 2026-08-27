package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal section: shell command execution, history, shortcuts.
 * Output is appended; running commands show progress in bottom status.
 */
public final class TerminalSection extends BaseSection {

    private static final String PREFS = "mod_adb__shell";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_SHORTCUTS = "shortcuts";
    private static final int MAX_HISTORY = 50;

    private EditText commandInput;
    private TextView outputView;
    private ScrollView outputScroll;

    private List<String> history = new ArrayList<>();
    private List<String> shortcuts = new ArrayList<>();

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_terminal, null);

        commandInput = view.findViewById(R.id.adb_shell_input);
        outputView = view.findViewById(R.id.adb_shell_output);
        outputScroll = view.findViewById(R.id.adb_shell_output_scroll);

        setupButtons(view);
        loadHistory();
        loadShortcuts();

        return view;
    }

    private void setupButtons(View root) {
        TextView send = root.findViewById(R.id.adb_shell_send);
        if (send != null) send.setOnClickListener(v -> executeCommand());

        TextView historyBtn = root.findViewById(R.id.adb_shell_history_btn);
        if (historyBtn != null) {
            historyBtn.setOnClickListener(v -> showHistoryDialog());
        }

        TextView shortcutsBtn = root.findViewById(R.id.adb_shell_shortcuts_btn);
        if (shortcutsBtn != null) {
            shortcutsBtn.setOnClickListener(v -> showShortcutsDialog());
        }

        TextView clearBtn = root.findViewById(R.id.adb_shell_clear_btn);
        if (clearBtn != null) {
            clearBtn.setOnClickListener(v -> {
                if (outputView != null) outputView.setText("");
            });
        }
    }

    private void executeCommand() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine eng = engine();
        AdbEngine.Session selected = eng.selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_shell_not_connected));
            return;
        }
        String cmd = commandInput.getText().toString().trim();
        if (cmd.isEmpty()) return;

        eng.shell(selected.id, cmd);
        appendOutput("$ " + cmd + "\n");
        showBottomMessage(act.getString(R.string.adb_shell_running));

        addToHistory(cmd);
        commandInput.setText("");
    }

    private void appendOutput(String text) {
        if (outputView == null) return;
        CharSequence current = outputView.getText();
        String newText = (current.length() > 0 ? current.toString() + "\n" : "") + text;
        outputView.setText(newText);
        if (outputScroll != null) {
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void addToHistory(String command) {
        history.remove(command);
        history.add(0, command);
        if (history.size() > MAX_HISTORY) {
            history = new ArrayList<>(history.subList(0, MAX_HISTORY));
        }
        saveHistory();
    }

    private void showHistoryDialog() {
        Activity act = activity();
        if (act == null) return;
        if (history.isEmpty()) {
            showBottomMessage("暂无历史记录");
            return;
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_shell_history)
                .setItems(history.toArray(new String[0]), (d, which) -> {
                    commandInput.setText(history.get(which));
                    commandInput.setSelection(commandInput.getText().length());
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .setNeutralButton("清空", (d, w) -> {
                    history.clear();
                    saveHistory();
                })
                .show();
    }

    private void showShortcutsDialog() {
        Activity act = activity();
        if (act == null) return;

        List<String> items = new ArrayList<>();
        items.add(act.getString(R.string.adb_shell_add_shortcut));
        items.addAll(shortcuts);

        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_shell_shortcuts)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        addShortcut();
                    } else {
                        String[] parts = items.get(which).split("：", 2);
                        commandInput.setText(parts.length > 1 ? parts[1] : parts[0]);
                        commandInput.setSelection(commandInput.getText().length());
                    }
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    private void addShortcut() {
        Activity act = activity();
        if (act == null) return;

        final EditText input = new EditText(act);
        input.setHint(R.string.adb_shell_shortcut_name);
        new android.app.AlertDialog.Builder(act)
                .setTitle(R.string.adb_shell_add_shortcut)
                .setView(input)
                .setPositiveButton(R.string.adb_ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    String cmd = commandInput.getText().toString().trim();
                    if (!name.isEmpty() && !cmd.isEmpty()) {
                        shortcuts.add(name + "：" + cmd);
                        saveShortcuts();
                    }
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    private void loadHistory() {
        Activity act = activity();
        if (act == null) return;
        SharedPreferences prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_HISTORY, "");
        if (!raw.isEmpty()) {
            for (String cmd : raw.split("\u0000")) {
                if (!cmd.isEmpty()) history.add(cmd);
            }
        }
    }

    private void saveHistory() {
        Activity act = activity();
        if (act == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append('\u0000');
            sb.append(history.get(i));
        }
        act.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_HISTORY, sb.toString()).apply();
    }

    private void loadShortcuts() {
        Activity act = activity();
        if (act == null) return;
        SharedPreferences prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SHORTCUTS, "");
        if (!raw.isEmpty()) {
            for (String s : raw.split("\u0000")) {
                if (!s.isEmpty()) shortcuts.add(s);
            }
        }
    }

    private void saveShortcuts() {
        Activity act = activity();
        if (act == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shortcuts.size(); i++) {
            if (i > 0) sb.append('\u0000');
            sb.append(shortcuts.get(i));
        }
        act.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SHORTCUTS, sb.toString()).apply();
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        AdbEngine.Session selected = engine.selected();
        if (selected != null) {
            engine.observe(() -> {
                AdbEngine.Session current = engine.selected();
                if (current != null && current.log != null && !current.log.isEmpty()) {
                    // Only append new content, not the entire log
                    String logContent = current.log;
                    CharSequence existing = outputView.getText();
                    if (existing == null || existing.length() == 0 || !logContent.equals(existing.toString())) {
                        outputView.post(() -> {
                            outputView.setText(logContent);
                            if (outputScroll != null) {
                                outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    protected void onEngineUnbound() {
    }

    @Override
    public void onDestroy() {
        activityRef = null;
    }
}
