package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.Toast;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 2026-06-23: 正则表达式测试工具。
 * 输入：正则表达式（不含首尾/）
 * 输出：所有匹配项的列表
 */
public class RegexTestToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        com.google.android.material.textfield.TextInputEditText input = contentView.findViewById(R.id.et_text_transform_input);
        com.google.android.material.textfield.TextInputEditText output = contentView.findViewById(R.id.et_text_transform_output);
        com.google.android.material.button.MaterialButton action = contentView.findViewById(R.id.btn_text_transform_action);
        com.google.android.material.button.MaterialButton clear = contentView.findViewById(R.id.btn_text_transform_clear);

        action.setText("测试正则（输入 regex + 待匹配文本，第二行）");
        action.setOnClickListener(v -> {
            String all = input.getText().toString();
            if (all.isEmpty()) {
                Toast.makeText(context, "请输入：第一行正则，第二行文本", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] parts = all.split("\n", 2);
            if (parts.length < 2) {
                Toast.makeText(context, "需要两行：regex + 文本", Toast.LENGTH_SHORT).show();
                return;
            }
            String pattern = parts[0].trim();
            String testText = parts[1];
            executor.execute(() -> {
                StringBuilder result = new StringBuilder();
                try {
                    Pattern p = Pattern.compile(pattern);
                    Matcher m = p.matcher(testText);
                    int count = 0;
                    while (m.find()) {
                        count++;
                        result.append("匹配 #").append(count).append(": \"")
                                .append(m.group()).append("\" @ ").append(m.start())
                                .append("-").append(m.end()).append("\n");
                    }
                    if (count == 0) result.append("无匹配");
                } catch (PatternSyntaxException e) {
                    result.append("正则语法错误: ").append(e.getMessage());
                }
                final String finalResult = result.toString();
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