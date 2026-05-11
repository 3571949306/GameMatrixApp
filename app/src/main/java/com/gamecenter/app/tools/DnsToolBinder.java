package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * DNS 信息查询工具绑定器
 */
public class DnsToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        List<String> dnsServers = ToolHelper.getDnsServers(context);
        TextView tvDns1 = contentView.findViewById(R.id.tv_dns1);
        TextView tvDns2 = contentView.findViewById(R.id.tv_dns2);
        if (tvDns1 != null) tvDns1.setText(dnsServers.size() > 0 ? dnsServers.get(0) : "未获取到");
        if (tvDns2 != null) tvDns2.setText(dnsServers.size() > 1 ? dnsServers.get(1) : "未获取到");
    }
}
