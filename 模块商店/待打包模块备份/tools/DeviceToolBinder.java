package com.gamecenter.app.tools;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 设备信息工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 读取并显示当前设备的基本硬件和系统信息，包括品牌、型号和操作系统版本。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有信息均来自 {@link Build} 类的静态常量，无需异步获取，
 *       因此不使用 executor 参数</li>
 *   <li>信息在 bind 时一次性设置，无需监听变化</li>
 * </ul>
 */
public final class DeviceToolBinder implements ToolBinder {

    /**
     * 绑定设备信息工具的 UI 交互。
     * <p>
     * 从 {@link Build} 类读取设备品牌、型号和系统版本信息，
     * 并设置到对应的 TextView 中。由于 Build 类的信息是静态常量，
     * 无需异步加载，直接在主线程设置即可。
     *
     * @param context     上下文（本工具未使用）
     * @param contentView 工具页面的根视图，用于查找品牌、型号、系统版本的 TextView
     * @param executor    线程池（本工具未使用，设备信息为静态常量无需异步获取）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvBrand = contentView.findViewById(R.id.tv_device_brand);
        TextView tvModel = contentView.findViewById(R.id.tv_device_model);
        TextView tvOs = contentView.findViewById(R.id.tv_device_os);
        // Build.BRAND：设备品牌（如 Xiaomi、HUAWEI）
        if (tvBrand != null) tvBrand.setText(Build.BRAND);
        // Build.MODEL：设备型号（如 MI 9、Pixel 4）
        if (tvModel != null) tvModel.setText(Build.MODEL);
        // 系统版本：Android 版本号 + API 级别（如 13 (API 33)）
        if (tvOs != null) tvOs.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }
}
