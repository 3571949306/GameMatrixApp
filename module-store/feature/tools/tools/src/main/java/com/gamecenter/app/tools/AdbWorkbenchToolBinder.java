package com.gamecenter.app.tools;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;

/**
 * Stateless compatibility adapter. Older hosts have neither the ADB contract nor its R fields,
 * so this dynamic APK resolves the host component and resources without linking either one.
 */
public final class AdbWorkbenchToolBinder implements ToolBinder {
    public static final String TOOL_ID = "adb_workbench";
    private static final String ACTIVITY_CLASS = "com.gamecenter.app.adb.AdbWorkbenchActivity";

    public static boolean isAvailable(Context context) {
        try {
            ActivityInfo activity = context.getPackageManager().getActivityInfo(
                    new ComponentName(context.getPackageName(), ACTIVITY_CLASS), 0);
            return activity.enabled && activity.applicationInfo.enabled && !activity.exported;
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return false;
        }
    }

    public static ToolSection createSection(Context context) {
        if (!isAvailable(context)) return null;
        int layout = resource(context, "item_tool_adb_workbench", "layout");
        int title = resource(context, "adb_entry_title", "string");
        int description = resource(context, "adb_entry_description", "string");
        int button = resource(context, "adb_entry_open", "id");
        if (layout == 0 || title == 0 || description == 0 || button == 0) return null;
        return new ToolSection(TOOL_ID, context.getString(title), layout, true,
                "device", context.getString(description));
    }

    private static int resource(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        int buttonId = resource(context, "adb_entry_open", "id");
        View button = buttonId == 0 ? null : contentView.findViewById(buttonId);
        if (button == null) return;
        button.setOnClickListener(clicked -> {
            // Use the live view's Context; no Fragment, callback, executor, or session is retained.
            Context current = clicked.getContext();
            if (launch(current)) {
                ToolSectionStore store = new ToolSectionStore(current);
                store.incrementUsage(TOOL_ID);
                store.recordRecent(TOOL_ID);
            } else {
                int message = resource(current, "adb_entry_unavailable", "string");
                Toast.makeText(current, message == 0 ? "ADB workbench unavailable"
                        : current.getString(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    static boolean launch(Context context) {
        if (!isAvailable(context)) return false;
        Intent intent = new Intent().setClassName(context.getPackageName(), ACTIVITY_CLASS);
        intent.putExtra("source", "tools");
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
