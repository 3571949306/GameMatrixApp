package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * 剪贴板工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 提供剪贴板的读取、设置和清空三项基本操作，方便用户查看和操作系统剪贴板内容。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>读取操作：从系统剪贴板获取文本内容并显示，非文本内容显示 "(非文本内容)"</li>
 *   <li>设置操作：将用户输入的文本写入系统剪贴板，空输入时提示用户</li>
 *   <li>清空操作：通过设置空文本覆盖剪贴板内容实现清空效果</li>
 * </ul>
 * <p>
 * 注意：Android 10+ 对后台应用读取剪贴板有限制，本工具仅在前台时使用。
 */
public final class ClipboardToolBinder implements ToolBinder {

    public ClipboardToolBinder() {
    }

    /**
     * 绑定剪贴板工具的 UI 交互。
     * <p>
     * 绑定三个按钮的点击事件：
     * <ul>
     *   <li>读取剪贴板：获取系统剪贴板中的文本内容并显示</li>
     *   <li>设置剪贴板：将输入框中的文本写入系统剪贴板</li>
     *   <li>清空剪贴板：清空系统剪贴板内容</li>
     * </ul>
     *
     * @param context     上下文，用于获取 ClipboardManager 系统服务和显示 Toast
     * @param contentView 工具页面的根视图，用于查找输入框、文本框和按钮
     * @param executor    线程池（本工具未使用，因为剪贴板操作均为轻量级主线程操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (context == null || contentView == null) {
            return;
        }

        TextView tvContent = contentView.findViewById(R.id.tv_clipboard_content);
        EditText etSet = contentView.findViewById(R.id.et_clipboard_set);

        // 读取剪贴板按钮
        View btnRead = contentView.findViewById(R.id.btn_read_clipboard);
        if (btnRead != null) {
            btnRead.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    // coerceToText 可处理非纯文本剪贴板项（如 URI），转为文本表示
                    String text = item.getText() != null ? item.getText().toString() : "(非文本内容)";
                    if (tvContent != null) {
                        tvContent.setText(text);
                    }
                } else if (tvContent != null) {
                    tvContent.setText("剪贴板为空");
                }
            });
        }

        // 设置剪贴板按钮
        View btnSet = contentView.findViewById(R.id.btn_set_clipboard);
        if (btnSet != null) {
            btnSet.setOnClickListener(v -> {
                String text = etSet != null ? etSet.getText().toString() : "";
                if (text.isEmpty()) {
                    Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("tools", text));
                    Toast.makeText(context, "已设置", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 清空剪贴板按钮：通过设置空文本覆盖原有内容
        View btnClear = contentView.findViewById(R.id.btn_clear_clipboard);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    // Android 没有直接清空剪贴板的 API，通过设置空文本实现
                    cm.setPrimaryClip(ClipData.newPlainText("", ""));
                    if (tvContent != null) {
                        tvContent.setText("已清空");
                    }
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
