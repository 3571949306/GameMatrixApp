package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * UUID 生成器工具绑定器（2026-07-25 新增）。
 * <p>
 * 支持生成 UUID v4（随机）和 UUID v7（时间戳 + 随机，RFC 9562）。
 * UUID v7 前 48 位为 Unix 毫秒时间戳，便于按时间排序。
 * </p>
 */
public final class UuidGeneratorToolBinder implements ToolBinder {

    private final SecureRandom random = new SecureRandom();

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        View btnV4 = contentView.findViewById(R.id.btn_uuid_v4);
        View btnV7 = contentView.findViewById(R.id.btn_uuid_v7);
        View btnCopy = contentView.findViewById(R.id.btn_uuid_copy);
        TextView tvResult = contentView.findViewById(R.id.tv_uuid_result);

        if (btnV4 != null) {
            btnV4.setOnClickListener(v -> {
                String uuid = UUID.randomUUID().toString();
                tvResult.setText(uuid);
            });
        }
        if (btnV7 != null) {
            btnV7.setOnClickListener(v -> tvResult.setText(generateV7()));
        }
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                CharSequence text = tvResult.getText();
                if (text == null || text.length() == 0) return;
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("uuid", text));
                    Toast.makeText(context, R.string.tool_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** 生成 RFC 9562 UUID v7：48 位毫秒时间戳 + 4 位版本号 + 12 位随机 + 2 位变体 + 62 位随机 */
    private String generateV7() {
        long timestampMs = System.currentTimeMillis();
        // 高 48 位 = 时间戳；低 4 位 = 版本(7)
        long msb = (timestampMs << 16) | 0x7000L;
        // 12 位随机 + 2 位变体(10) + 62 位随机
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }
}
