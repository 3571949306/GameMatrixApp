package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

public final class SubnetToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_calc_subnet);
        if (btn != null) btn.setOnClickListener(v -> {
            EditText etInput = contentView.findViewById(R.id.et_subnet_ip);
            TextView tvResult = contentView.findViewById(R.id.tv_subnet_result);
            String input = etInput != null ? etInput.getText().toString().trim() : "192.168.1.1/24";
            if (input.isEmpty()) input = "192.168.1.1/24";
            if (tvResult != null) tvResult.setText(ToolHelper.calculateSubnet(input));
        });
    }
}
