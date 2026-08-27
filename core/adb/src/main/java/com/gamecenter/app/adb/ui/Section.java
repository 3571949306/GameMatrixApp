package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.view.View;

import com.gamecenter.app.adb.AdbSessionService;

/**
 * Section interface for ADB workbench content panels.
 * Each tab in AdbWorkbenchActivity is represented by a Section implementation.
 */
public interface Section {
    View createView(Activity activity);
    void onBind(AdbSessionService service);
    void onUnbind();
    void onDestroy();
}
