package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

public class LanScanToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindLanScan(context, contentView, executor);
    }
}
