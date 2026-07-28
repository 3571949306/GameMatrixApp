package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;

/**
 * JWT 解析工具绑定器（2026-07-25 新增，受 ENABLE_TOOLS_ENHANCEMENT 控制）。
 * <p>
 * 解码 JWT 的三段（Header、Payload、Signature）：
 * <ul>
 *   <li>Header 与 Payload 使用 Base64 URL-safe 解码后以 UTF-8 文本展示</li>
 *   <li>Signature 段保持原 Base64 字符串展示（仅用于查看，不验证签名）</li>
 * </ul>
 * </p>
 * <p>
 * 安全说明：本工具仅做本地解码展示，不进行签名验证，不发起网络请求。
 * </p>
 */
public final class JwtParserToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (context == null || contentView == null) return;

        TextView etInput = contentView.findViewById(R.id.et_jwt_input);
        View btnDecode = contentView.findViewById(R.id.btn_jwt_decode);
        View btnCopy = contentView.findViewById(R.id.btn_jwt_copy);
        TextView tvHeader = contentView.findViewById(R.id.tv_jwt_header);
        TextView tvPayload = contentView.findViewById(R.id.tv_jwt_payload);
        TextView tvSignature = contentView.findViewById(R.id.tv_jwt_signature);

        if (btnDecode == null) return;

        btnDecode.setOnClickListener(v -> {
            String jwt = etInput == null || etInput.getText() == null
                    ? "" : etInput.getText().toString().trim();
            if (jwt.isEmpty()) {
                Toast.makeText(context, R.string.tool_jwt_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                Toast.makeText(context, R.string.tool_jwt_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            String header = decodeJwtPart(parts[0]);
            String payload = decodeJwtPart(parts[1]);
            String signature = parts.length >= 3 ? parts[2] : "";
            if (tvHeader != null) tvHeader.setText(header);
            if (tvPayload != null) tvPayload.setText(payload);
            if (tvSignature != null) tvSignature.setText(signature);
        });

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                StringBuilder sb = new StringBuilder();
                if (tvHeader != null) sb.append("Header:\n").append(tvHeader.getText()).append("\n\n");
                if (tvPayload != null) sb.append("Payload:\n").append(tvPayload.getText()).append("\n\n");
                if (tvSignature != null) sb.append("Signature:\n").append(tvSignature.getText());
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("JWT", sb.toString()));
                    Toast.makeText(context, R.string.tool_jwt_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** 解码 JWT 的 Header / Payload 段（Base64 URL-safe，无 padding） */
    private String decodeJwtPart(String part) {
        try {
            // 补齐 padding
            String padded = part;
            int rem = padded.length() % 4;
            if (rem != 0) {
                padded = padded + "====".substring(0, 4 - rem);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(padded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Invalid: " + e.getMessage();
        }
    }
}
