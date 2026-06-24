package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * 2026-06-23: Base64 编解码工具。
 */
public class Base64ToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        com.google.android.material.textfield.TextInputEditText input = contentView.findViewById(R.id.et_text_transform_input);
        com.google.android.material.textfield.TextInputEditText output = contentView.findViewById(R.id.et_text_transform_output);
        com.google.android.material.button.MaterialButton action = contentView.findViewById(R.id.btn_text_transform_action);
        com.google.android.material.button.MaterialButton clear = contentView.findViewById(R.id.btn_text_transform_clear);
        var modeRef = new boolean[]{true}; // true=encode, false=decode

        action.setOnClickListener(v -> {
            String s = input.getText().toString();
            if (s.isEmpty()) {
                Toast.makeText(context, "请输入文本", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                String result;
                try {
                    if (modeRef[0]) {
                        result = Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);
                    } else {
                        byte[] decoded = Base64.decode(s, Base64.DEFAULT);
                        result = new String(decoded);
                    }
                } catch (Exception e) {
                    result = "错误: " + e.getMessage();
                }
                final String finalResult = result;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> output.setText(finalResult));
            });
        });
        clear.setOnClickListener(v -> {
            input.setText("");
            output.setText("");
        });
        output.setOnLongClickListener(v -> {
            modeRef[0] = !modeRef[0];
            action.setText(modeRef[0] ? "Base64 编码" : "Base64 解码");
            return true;
        });
        action.setText("Base64 编码（长按切换解码）");
    }
}