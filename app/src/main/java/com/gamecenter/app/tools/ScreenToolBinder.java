package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class ScreenToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvRes = contentView.findViewById(R.id.tv_screen_resolution);
        TextView tvDpi = contentView.findViewById(R.id.tv_screen_dpi);
        TextView tvSize = contentView.findViewById(R.id.tv_screen_size);
        TextView tvRatio = contentView.findViewById(R.id.tv_screen_aspect);
        TextView tvRefresh = contentView.findViewById(R.id.tv_screen_refresh);

        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels;
            float xdpi = dm.xdpi, ydpi = dm.ydpi;
            int refresh = 60;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.Display display = activity.getDisplay();
                if (display != null) {
                    android.view.Display.Mode mode = display.getMode();
                    if (mode != null) refresh = Math.round(mode.getRefreshRate());
                }
            }

            double widthInch = w / xdpi, heightInch = h / ydpi;
            double diagonalInch = Math.sqrt(widthInch * widthInch + heightInch * heightInch);
            int ratio = ToolHelper.gcd(w, h);

            if (tvRes != null) tvRes.setText("分辨率: " + w + "x" + h + " px");
            if (tvDpi != null) tvDpi.setText(String.format(Locale.getDefault(), "DPI / 密度: %.0f x %.0f dpi", xdpi, ydpi));
            if (tvSize != null) tvSize.setText(String.format(Locale.getDefault(), "屏幕尺寸: %.2f 英寸", diagonalInch));
            if (tvRatio != null) tvRatio.setText(String.format(Locale.getDefault(), "宽高比: %d:%d", w / ratio, h / ratio));
            if (tvRefresh != null) tvRefresh.setText("刷新率: " + refresh + " Hz");
        }
    }
}
