package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.google.android.material.chip.Chip;

import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;

/**
 * 密码生成器工具绑定器（2026-07-25 新增）。
 * <p>
 * 可配置长度（4-64）和字符集（大写/小写/数字/符号），
 * 使用 SecureRandom 生成随机密码。支持复制到剪贴板。
 * </p>
 */
public final class PasswordGeneratorToolBinder implements ToolBinder {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?";

    private final SecureRandom random = new SecureRandom();

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        EditText etLength = contentView.findViewById(R.id.et_password_length);
        Chip chipUpper = contentView.findViewById(R.id.chip_password_upper);
        Chip chipLower = contentView.findViewById(R.id.chip_password_lower);
        Chip chipDigits = contentView.findViewById(R.id.chip_password_digits);
        Chip chipSymbols = contentView.findViewById(R.id.chip_password_symbols);
        TextView tvResult = contentView.findViewById(R.id.tv_password_result);
        View btnGenerate = contentView.findViewById(R.id.btn_password_generate);
        View btnCopy = contentView.findViewById(R.id.btn_password_copy);

        if (btnGenerate != null) {
            btnGenerate.setOnClickListener(v -> {
                int length = 16;
                try {
                    length = Integer.parseInt(etLength.getText().toString().trim());
                } catch (Exception ignored) {
                }
                if (length < 4 || length > 64) {
                    Toast.makeText(context, R.string.tool_password_length_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                StringBuilder charset = new StringBuilder();
                if (chipUpper != null && chipUpper.isChecked()) charset.append(UPPER);
                if (chipLower != null && chipLower.isChecked()) charset.append(LOWER);
                if (chipDigits != null && chipDigits.isChecked()) charset.append(DIGITS);
                if (chipSymbols != null && chipSymbols.isChecked()) charset.append(SYMBOLS);
                if (charset.length() == 0) {
                    Toast.makeText(context, R.string.tool_password_no_charset, Toast.LENGTH_SHORT).show();
                    return;
                }
                StringBuilder pwd = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    pwd.append(charset.charAt(random.nextInt(charset.length())));
                }
                tvResult.setText(pwd.toString());
            });
        }

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                CharSequence text = tvResult.getText();
                if (text == null || text.length() == 0) return;
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("password", text));
                    Toast.makeText(context, R.string.tool_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
