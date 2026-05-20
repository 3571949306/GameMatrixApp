package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * DNS 信息查询工具绑定器。
 * <p>
 * 职责：读取设备当前配置的 DNS 服务器地址，并将其展示在 UI 上。
 * 支持显示主 DNS 和备用 DNS 两个地址。
 * </p>
 * <p>
 * 设计决策：DNS 信息通过 {@link ToolHelper#getDnsServers} 统一获取，
 * 本类仅负责将结果映射到对应的 TextView 控件，保持单一职责。
 * </p>
 */
public class DnsToolBinder implements ToolBinder {

    /**
     * 将 DNS 信息查询结果绑定到内容视图的对应控件上。
     * <p>
     * 从 ToolHelper 获取 DNS 服务器列表，将前两个地址分别显示在
     * tv_dns1（主 DNS）和 tv_dns2（备用 DNS）控件中。
     * 若获取不到对应位置的 DNS 地址，则显示"未获取到"。
     * </p>
     *
     * @param context     应用上下文，用于获取系统服务（如 ConnectivityManager）
     * @param contentView 工具的根视图容器，需包含 tv_dns1 和 tv_dns2 两个 TextView
     * @param executor    线程池执行器（本方法未使用，因 DNS 信息获取为轻量操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        List<String> dnsServers = ToolHelper.getDnsServers(context);
        TextView tvDns1 = contentView.findViewById(R.id.tv_dns1);
        TextView tvDns2 = contentView.findViewById(R.id.tv_dns2);
        // 边界条件：DNS 列表可能为空或仅有一个条目，需分别判断
        if (tvDns1 != null) tvDns1.setText(dnsServers.size() > 0 ? dnsServers.get(0) : "未获取到");
        if (tvDns2 != null) tvDns2.setText(dnsServers.size() > 1 ? dnsServers.get(1) : "未获取到");
    }
}
