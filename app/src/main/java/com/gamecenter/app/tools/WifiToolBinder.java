package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * WiFi 信号工具绑定器
 */
public class WifiToolBinder implements ToolBinder {
    private android.telephony.TelephonyManager telephonyManager;
    private int mobileSignalDbm = 0;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        telephonyManager = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        TextView tvWifiSignal = contentView.findViewById(R.id.tv_wifi_signal);
        if (tvWifiSignal != null) tvWifiSignal.setText(ToolHelper.getWifiSignalStrength(context));

        TextView tvMobileType = contentView.findViewById(R.id.tv_mobile_type);
        TextView tvMobileSignal = contentView.findViewById(R.id.tv_mobile_signal);
        TextView tvMobileOperator = contentView.findViewById(R.id.tv_mobile_operator);

        if (tvMobileType != null) tvMobileType.setText("网络类型: " + getMobileNetworkType());
        if (tvMobileSignal != null) tvMobileSignal.setText("信号强度: " + getMobileSignalText(context));
        if (tvMobileOperator != null) tvMobileOperator.setText("运营商: " + getMobileOperator());
    }

    private String getMobileNetworkType() {
        try {
            return ToolHelper.getMobileNetworkType(telephonyManager);
        } catch (Exception ignored) {
            return "未连接";
        }
    }

    private String getMobileSignalText(Context context) {
        if (mobileSignalDbm == 0) {
            try { fetchCellSignal(context); } catch (Exception ignored) {}
        }
        if (mobileSignalDbm == 0) return "无信号或无SIM卡";
        int dbm = mobileSignalDbm;
        if (dbm == Integer.MAX_VALUE) return "无信号";
        if (dbm > -1) return "获取中...";
        String level;
        if (dbm >= -70) level = "极好";
        else if (dbm >= -85) level = "良好";
        else if (dbm >= -100) level = "一般";
        else if (dbm >= -115) level = "较弱";
        else level = "极弱";
        return level + " (" + dbm + " dBm)";
    }

    private String getMobileOperator() {
        if (telephonyManager == null) return "不可用";
        try {
            String name = telephonyManager.getNetworkOperatorName();
            return name != null && !name.isEmpty() ? name : "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void fetchCellSignal(Context context) {
        if (telephonyManager == null) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            java.util.List<android.telephony.CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList != null && !cellInfoList.isEmpty()) {
                for (android.telephony.CellInfo info : cellInfoList) {
                    if (info.isRegistered()) {
                        android.telephony.CellSignalStrength css = info.getCellSignalStrength();
                        if (css != null) {
                            mobileSignalDbm = css.getDbm();
                            if (mobileSignalDbm < 0) break;
                        }
                    }
                }
                if (mobileSignalDbm == 0 && !cellInfoList.isEmpty()) {
                    android.telephony.CellSignalStrength css = cellInfoList.get(0).getCellSignalStrength();
                    if (css != null) mobileSignalDbm = css.getDbm();
                }
            }
        } catch (Exception ignored) {}
    }
}
