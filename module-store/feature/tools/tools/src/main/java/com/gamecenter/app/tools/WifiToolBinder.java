package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * WiFi 与移动网络信号工具绑定器。
 * <p>
 * 负责将 WiFi 信号强度、移动网络类型、移动信号强度和运营商信息
 * 绑定到工具页面的 UI 控件上。页面加载时自动采集并显示所有网络信息。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>WiFi 信号和移动网络信息在 bind 时同步获取（无需异步）</li>
 *   <li>移动信号强度通过 TelephonyManager.getAllCellInfo 获取，
 *       优先使用已注册小区的信号，若无则回退到第一个小区</li>
 *   <li>信号等级划分基于常见 dBm 阈值：≥-70 极好、≥-85 良好、≥-100 一般、≥-115 较弱</li>
 * </ul>
 * </p>
 */
public class WifiToolBinder implements ToolBinder {
    private static final String TAG = "WifiToolBinder";

    /** 电话管理器，用于获取移动网络信息 */
    private android.telephony.TelephonyManager telephonyManager;

    /** 缓存的移动信号强度（dBm），0 表示未获取 */
    private int mobileSignalDbm = 0;

    /**
     * 将 WiFi 和移动网络信息绑定到视图。
     * <p>
     * 绑定时立即采集并显示 WiFi 信号强度、移动网络类型、信号强度和运营商。
     * </p>
     *
     * @param context     上下文，用于获取系统服务
     * @param contentView 工具卡片的根视图
     * @param executor    线程池（本工具未使用，信息采集在主线程完成）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        telephonyManager = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        TextView tvWifiSignal = contentView.findViewById(R.id.tv_wifi_signal);
        if (tvWifiSignal != null) tvWifiSignal.setText(ToolHelper.getWifiSignalStrength(context));

        TextView tvMobileType = contentView.findViewById(R.id.tv_mobile_type);
        TextView tvMobileSignal = contentView.findViewById(R.id.tv_mobile_signal);
        TextView tvMobileOperator = contentView.findViewById(R.id.tv_mobile_operator);

        if (tvMobileType != null) tvMobileType.setText(context.getString(R.string.tool_wifi_type_format, getMobileNetworkType()));
        if (tvMobileSignal != null) tvMobileSignal.setText(context.getString(R.string.tool_wifi_signal_format, getMobileSignalText(context)));
        if (tvMobileOperator != null) tvMobileOperator.setText(context.getString(R.string.tool_wifi_operator_format, getMobileOperator()));
    }

    /**
     * 获取当前移动网络类型（如 4G、5G 等）。
     *
     * @return 网络类型字符串，异常时返回"未连接"
     */
    private String getMobileNetworkType() {
        try {
            return ToolHelper.getMobileNetworkType(telephonyManager);
        } catch (Exception ignored) {
            return "未连接";
        }
    }

    /**
     * 获取移动信号强度的可读描述。
     * <p>
     * 信号等级划分阈值（基于 dBm）：
     * ≥-70 极好、≥-85 良好、≥-100 一般、≥-115 较弱、<-115 极弱。
     * 特殊值处理：0 表示未获取，Integer.MAX_VALUE 表示无信号，>-1 表示获取中。
     * </p>
     *
     * @param context 上下文，用于获取信号信息
     * @return 信号等级描述字符串，如"良好 (-85 dBm)"
     */
    private String getMobileSignalText(Context context) {
        // 首次调用时尝试获取信号强度
        if (mobileSignalDbm == 0) {
            try { fetchCellSignal(context); } catch (Exception ignored) { Log.w(TAG, "Fetch cell signal failed: " + ignored.getMessage()); }
        }
        if (mobileSignalDbm == 0) return "无信号或无SIM卡";
        int dbm = mobileSignalDbm;
        if (dbm == Integer.MAX_VALUE) return "无信号";
        if (dbm > -1) return "获取中...";
        // 根据 dBm 值划分信号等级
        String level;
        if (dbm >= -70) level = "极好";
        else if (dbm >= -85) level = "良好";
        else if (dbm >= -100) level = "一般";
        else if (dbm >= -115) level = "较弱";
        else level = "极弱";
        return level + " (" + dbm + " dBm)";
    }

    /**
     * 获取当前移动网络运营商名称。
     *
     * @return 运营商名称，不可用时返回"不可用"，未知时返回"未知"
     */
    private String getMobileOperator() {
        if (telephonyManager == null) return "不可用";
        try {
            String name = telephonyManager.getNetworkOperatorName();
            return name != null && !name.isEmpty() ? name : "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 通过 TelephonyManager 获取基站信号强度。
     * <p>
     * 优先使用已注册（isRegistered）的小区信号；若无已注册小区，
     * 则回退使用第一个小区的信号强度。需要 READ_PHONE_STATE 权限。
     * </p>
     *
     * @param context 上下文，用于权限检查
     */
    @android.annotation.SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void fetchCellSignal(Context context) {
        if (telephonyManager == null) return;
        // 检查 READ_PHONE_STATE 权限，未授权则直接返回
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            java.util.List<android.telephony.CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList != null && !cellInfoList.isEmpty()) {
                // 优先查找已注册的小区
                for (android.telephony.CellInfo info : cellInfoList) {
                    if (info.isRegistered()) {
                        android.telephony.CellSignalStrength css = info.getCellSignalStrength();
                        if (css != null) {
                            mobileSignalDbm = css.getDbm();
                            // 获取到有效的 dBm 值（负数）即可停止
                            if (mobileSignalDbm < 0) break;
                        }
                    }
                }
                // 若未找到已注册小区的信号，回退使用第一个小区
                if (mobileSignalDbm == 0 && !cellInfoList.isEmpty()) {
                    android.telephony.CellSignalStrength css = cellInfoList.get(0).getCellSignalStrength();
                    if (css != null) mobileSignalDbm = css.getDbm();
                }
            }
        } catch (Exception ignored) { Log.w(TAG, "Fetch cell signal failed: " + ignored.getMessage()); }
    }
}
