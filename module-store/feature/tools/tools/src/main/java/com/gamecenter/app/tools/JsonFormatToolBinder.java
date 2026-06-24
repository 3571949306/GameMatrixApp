package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.Toast;
import com.gamecenter.app.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;

/**
 * 2026-06-23: JSON 格式化/校验工具。
 * 输入 JSON 文本 → 缩进美化输出；非合法 JSON 时显示错误位置
 */
public class JsonFormatToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        com.google.android.material.textfield.TextInputEditText input = contentView.findViewById(R.id.et_text_transform_input);
        com.google.android.material.textfield.TextInputEditText output = contentView.findViewById(R.id.et_text_transform_output);
        com.google.android.material.button.MaterialButton action = contentView.findViewById(R.id.btn_text_transform_action);
        com.google.android.material.button.MaterialButton clear = contentView.findViewById(R.id.btn_text_transform_clear);

        action.setText("格式化 JSON");
        action.setOnClickListener(v -> {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) {
                Toast.makeText(context, "请输入 JSON 文本", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                String result;
                try {
                    // 尝试解析为对象或数组，然后美化输出
                    if (s.startsWith("[")) {
                        JSONArray arr = new JSONArray(s);
                        result = arr.toString(2);
                    } else {
                        JSONObject obj = new JSONObject(s);
                        result = obj.toString(2);
                    }
                } catch (Exception e) {
                    result = "JSON 解析错误: " + e.getMessage();
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
    }
}