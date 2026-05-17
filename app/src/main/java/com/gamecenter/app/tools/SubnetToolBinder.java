package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

/**
 * 子网计算工具绑定器。
 * <p>
 * 负责将子网掩码计算功能绑定到工具页面的 UI 控件上。
 * 用户输入 CIDR 格式的 IP 地址（如 192.168.1.1/24），
 * 点击计算按钮后调用 ToolHelper.calculateSubnet 进行子网划分计算，
 * 并将结果展示在 TextView 中。
 * </p>
 * <p>
 * 设计决策：该工具为纯计算操作，无需异步执行，因此不使用 ExecutorService。
 * 输入为空时默认使用 192.168.1.1/24 作为示例值，降低使用门槛。
 * </p>
 */
public final class SubnetToolBinder implements ToolBinder {

    /**
     * 将子网计算功能绑定到视图。
     *
     * @param context     上下文
     * @param contentView 工具卡片的根视图，包含输入框、按钮和结果文本
     * @param executor    线程池（本工具未使用，因为计算在主线程即可完成）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_calc_subnet);
        if (btn != null) btn.setOnClickListener(v -> {
            EditText etInput = contentView.findViewById(R.id.et_subnet_ip);
            TextView tvResult = contentView.findViewById(R.id.tv_subnet_result);
            String input = etInput != null ? etInput.getText().toString().trim() : "192.168.1.1/24";
            // 输入为空时使用默认示例值，避免空字符串导致计算异常
            if (input.isEmpty()) input = "192.168.1.1/24";
            if (tvResult != null) tvResult.setText(ToolHelper.calculateSubnet(input));
        });
    }
}
