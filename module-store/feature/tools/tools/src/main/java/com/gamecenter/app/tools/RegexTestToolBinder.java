package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 2026-07-25: 正则表达式测试工具（改进版）。
 * <p>
 * 改进点：
 * <ul>
 *   <li>使用独立的双输入框布局（正则 + 待匹配文本），替代原 item_tool_text_transform 的单输入框两行格式</li>
 *   <li>移除硬编码中文，全部使用字符串资源</li>
 *   <li>新增"匹配汇总"行，显示匹配总数</li>
 *   <li>错误信息使用本地化字符串</li>
 * </ul>
 * </p>
 */
public class RegexTestToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        TextInputEditText patternInput = contentView.findViewById(R.id.et_regex_pattern);
        TextInputEditText textInput = contentView.findViewById(R.id.et_regex_text);
        MaterialButton runBtn = contentView.findViewById(R.id.btn_regex_run);
        MaterialButton clearBtn = contentView.findViewById(R.id.btn_regex_clear);
        TextView resultView = contentView.findViewById(R.id.tv_regex_result);
        TextView summaryView = contentView.findViewById(R.id.tv_regex_summary);

        runBtn.setText(context.getString(R.string.tool_regex_test_label));
        runBtn.setOnClickListener(v -> {
            String patternStr = patternInput.getText() == null ? "" : patternInput.getText().toString();
            String testText = textInput.getText() == null ? "" : textInput.getText().toString();
            if (patternStr.isEmpty() || testText.isEmpty()) {
                Toast.makeText(context, R.string.tool_input_regex_two_lines, Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                StringBuilder result = new StringBuilder();
                int count = 0;
                try {
                    Pattern p = Pattern.compile(patternStr);
                    Matcher m = p.matcher(testText);
                    while (m.find()) {
                        count++;
                        result.append(context.getString(R.string.tool_regex_match_item_format,
                                count, m.group(), m.start(), m.end())).append("\n");
                    }
                    if (count == 0) {
                        result.append(context.getString(R.string.tool_regex_no_match));
                    }
                } catch (PatternSyntaxException e) {
                    result.append(context.getString(R.string.tool_regex_syntax_error_format, e.getMessage()));
                }
                final String finalResult = result.toString();
                final int finalCount = count;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    resultView.setText(finalResult);
                    if (finalCount > 0) {
                        summaryView.setText(context.getString(R.string.tool_regex_match_count_format, finalCount));
                        summaryView.setVisibility(View.VISIBLE);
                    } else {
                        summaryView.setVisibility(View.GONE);
                    }
                });
            });
        });
        clearBtn.setOnClickListener(v -> {
            patternInput.setText("");
            textInput.setText("");
            resultView.setText("");
            summaryView.setVisibility(View.GONE);
        });
    }
}
