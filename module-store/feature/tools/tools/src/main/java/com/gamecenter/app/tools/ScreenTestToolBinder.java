package com.gamecenter.app.tools;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.util.concurrent.ExecutorService;

public final class ScreenTestToolBinder implements ToolBinder {

    private static final String FULL = "com.gamecenter.app.tools.ScreenTestFullActivity";
    private static final String TOUCH = "com.gamecenter.app.tools.ScreenTestTouchActivity";
    private static final String GREYSCALE = "com.gamecenter.app.tools.ScreenTestGreyscaleActivity";

    private static final String EXTRA_TEST_TYPE = "test_type";
    private static final String TYPE_FULL = "full";
    private static final String TYPE_TOUCH = "touch";
    private static final String TYPE_GREYSCALE = "greyscale";

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        Button btnFull = contentView.findViewById(R.id.btn_color_full);
        Button btnTouch = contentView.findViewById(R.id.btn_color_touch);
        Button btnGreyscale = contentView.findViewById(R.id.btn_color_greyscale);

        if (btnFull != null) {
            btnFull.setOnClickListener(v -> launchTest(context, FULL, TYPE_FULL));
        }
        if (btnTouch != null) {
            btnTouch.setOnClickListener(v -> launchTest(context, TOUCH, TYPE_TOUCH));
        }
        if (btnGreyscale != null) {
            btnGreyscale.setOnClickListener(v -> launchTest(context, GREYSCALE, TYPE_GREYSCALE));
        }
    }

    private void launchTest(Context context, String className, String testType) {
        try {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), className);
            intent.putExtra(EXTRA_TEST_TYPE, testType);
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(context, "功能暂未实现", Toast.LENGTH_SHORT).show();
        }
    }
}
