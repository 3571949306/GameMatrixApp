package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 屏幕信息工具绑定器。
 * <p>
 * 负责将屏幕信息工具的 UI 视图与屏幕参数读取逻辑进行绑定。
 * 读取并显示设备的屏幕分辨率、DPI、物理尺寸、宽高比和刷新率等信息。
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@code getRealMetrics()} 获取包含系统装饰区域的真实屏幕尺寸</li>
 *   <li>刷新率仅在 Android R (API 30) 及以上版本通过 Display.Mode 获取，低版本默认为 60Hz</li>
 *   <li>宽高比通过 GCD（最大公约数）化简，如 1080x1920 化简为 9:16</li>
 *   <li>物理尺寸通过勾股定理计算对角线长度得出</li>
 * </ul>
 * </p>
 */
public final class ScreenToolBinder implements ToolBinder {

    /**
     * 绑定屏幕信息工具的视图，读取并显示屏幕参数。
     * <p>
     * 查找各信息 TextView 并填充屏幕分辨率、DPI、物理尺寸、宽高比和刷新率。
     * 仅当 context 为 Activity 时才执行，因为需要通过 WindowManager 获取显示信息。
     * </p>
     *
     * @param context     上下文环境，必须为 Activity 实例才能获取屏幕信息
     * @param contentView 工具页面的根视图，包含分辨率、DPI、尺寸、宽高比、刷新率的 TextView
     * @param executor    线程池执行器（本工具未使用，因屏幕信息读取为轻量同步操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvRes = contentView.findViewById(R.id.tv_screen_resolution);
        TextView tvDpi = contentView.findViewById(R.id.tv_screen_dpi);
        TextView tvSize = contentView.findViewById(R.id.tv_screen_size);
        TextView tvRatio = contentView.findViewById(R.id.tv_screen_aspect);
        TextView tvRefresh = contentView.findViewById(R.id.tv_screen_refresh);

        // 仅 Activity 上下文才能获取 WindowManager 和屏幕信息
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            // getRealMetrics 包含系统装饰区域（状态栏、导航栏），获取真实物理屏幕尺寸
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels;
            float xdpi = dm.xdpi, ydpi = dm.ydpi;
            // 低版本 Android 无法获取精确刷新率，默认为 60Hz
            int refresh = 60;

            // Android R 及以上版本通过 Display.Mode 获取实际刷新率
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.Display display = activity.getDisplay();
                if (display != null) {
                    android.view.Display.Mode mode = display.getMode();
                    if (mode != null) refresh = Math.round(mode.getRefreshRate());
                }
            }

            // 通过像素和 DPI 计算物理尺寸（英寸），再利用勾股定理求对角线长度
            double widthInch = w / xdpi, heightInch = h / ydpi;
            double diagonalInch = Math.sqrt(widthInch * widthInch + heightInch * heightInch);
            // 通过最大公约数化简宽高比，如 1080x1920 → 9:16
            int ratio = ToolHelper.gcd(w, h);

            if (tvRes != null) tvRes.setText("分辨率: " + w + "x" + h + " px");
            if (tvDpi != null) tvDpi.setText(String.format(Locale.getDefault(), "DPI / 密度: %.0f x %.0f dpi", xdpi, ydpi));
            if (tvSize != null) tvSize.setText(String.format(Locale.getDefault(), "屏幕尺寸: %.2f 英寸", diagonalInch));
            if (tvRatio != null) tvRatio.setText(String.format(Locale.getDefault(), "宽高比: %d:%d", w / ratio, h / ratio));
            if (tvRefresh != null) tvRefresh.setText("刷新率: " + refresh + " Hz");
        }
    }
}
