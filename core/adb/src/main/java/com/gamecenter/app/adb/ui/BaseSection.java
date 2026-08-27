package com.gamecenter.app.adb.ui;

import android.app.Activity;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.AdbWorkbenchActivity;

import java.lang.ref.WeakReference;

/**
 * Base class for all ADB workbench sections.
 * Uses WeakReference to Activity to prevent memory leaks.
 * Each section creates its own views and handles interactions.
 */
public abstract class BaseSection implements Section {

    protected WeakReference<Activity> activityRef;
    protected AdbSessionService service;

    @Override
    public final void onBind(AdbSessionService svc) {
        this.service = svc;
        onEngineBound(svc.engine());
    }

    @Override
    public final void onUnbind() {
        this.service = null;
        onEngineUnbound();
    }

    protected abstract void onEngineBound(AdbEngine engine);
    protected abstract void onEngineUnbound();

    protected Activity activity() {
        return activityRef != null ? activityRef.get() : null;
    }

    protected AdbWorkbenchActivity workbenchActivity() {
        Activity act = activity();
        return act instanceof AdbWorkbenchActivity ? (AdbWorkbenchActivity) act : null;
    }

    protected AdbEngine engine() {
        return service != null ? service.engine() : null;
    }

    protected void showBottomMessage(String msg) {
        AdbWorkbenchActivity act = workbenchActivity();
        if (act != null) act.showBottomMessage(msg);
    }
}
