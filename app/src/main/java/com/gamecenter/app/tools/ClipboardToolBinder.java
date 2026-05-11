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

public final class ClipboardToolBinder implements ToolBinder {

    public ClipboardToolBinder() {
    }

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (context == null || contentView == null) {
            return;
        }

        TextView tvContent = contentView.findViewById(R.id.tv_clipboard_content);
        EditText etSet = contentView.findViewById(R.id.et_clipboard_set);

        View btnRead = contentView.findViewById(R.id.btn_read_clipboard);
        if (btnRead != null) {
            btnRead.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    String text = item.getText() != null ? item.getText().toString() : "(非文本内容)";
                    if (tvContent != null) {
                        tvContent.setText(text);
                    }
                } else if (tvContent != null) {
                    tvContent.setText("剪贴板为空");
                }
            });
        }

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

        View btnClear = contentView.findViewById(R.id.btn_clear_clipboard);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
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
