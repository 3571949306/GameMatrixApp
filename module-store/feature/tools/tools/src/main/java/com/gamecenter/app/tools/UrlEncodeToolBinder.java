package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.Toast;
import com.gamecenter.app.R;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/**
 * 2026-06-23: URL 编解码工具（encode/decode）。
 */
public class UrlEncodeToolBinder implements ToolBinder {

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
                        result = URLEncoder.encode(s, StandardCharsets.UTF_8);
                    } else {
                        result = URLDecoder.decode(s, StandardCharsets.UTF_8);
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
        // 长按输出框切换模式（encode/decode）
        output.setOnLongClickListener(v -> {
            modeRef[0] = !modeRef[0];
            action.setText(modeRef[0] ? "URL 编码" : "URL 解码");
            return true;
        });
        action.setText("URL 编码（长按切换解码）");
    }
}