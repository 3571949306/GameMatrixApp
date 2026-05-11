package com.gamecenter.app.tools;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class DeviceToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvBrand = contentView.findViewById(R.id.tv_device_brand);
        TextView tvModel = contentView.findViewById(R.id.tv_device_model);
        TextView tvOs = contentView.findViewById(R.id.tv_device_os);
        if (tvBrand != null) tvBrand.setText(Build.BRAND);
        if (tvModel != null) tvModel.setText(Build.MODEL);
        if (tvOs != null) tvOs.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }
}
