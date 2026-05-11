package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

public class DnsLookupToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindDnsLookup(context, contentView, executor);
    }
}
