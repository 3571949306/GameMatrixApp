package com.gamecenter.app.core.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** Launch-only host contract: never accepts a device, command, or executable task. */
public final class AdbWorkbenchLauncher {
    public static final String ACTIVITY_CLASS = "com.gamecenter.app.adb.AdbWorkbenchActivity";
    public static final String EXTRA_SOURCE = "source";
    public static final String SOURCE_HALL = "hall_avatar";
    public static final String SOURCE_TOOLS = "tools";

    private AdbWorkbenchLauncher() {}

    public static Intent createIntent(Context context, String source) {
        if (!SOURCE_HALL.equals(source) && !SOURCE_TOOLS.equals(source)) {
            throw new IllegalArgumentException("Unsupported ADB workbench entry source");
        }
        Intent intent = new Intent().setClassName(context.getPackageName(), ACTIVITY_CLASS);
        intent.putExtra(EXTRA_SOURCE, source);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static void launch(Context context, String source) {
        context.startActivity(createIntent(context, source));
    }
}
