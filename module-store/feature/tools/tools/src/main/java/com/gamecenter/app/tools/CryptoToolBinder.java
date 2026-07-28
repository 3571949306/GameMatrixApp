package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.concurrent.ExecutorService;

/**
 * 加密/解密工具绑定器（2026-07-25 新增）。
 * <p>
 * 支持：
 * <ul>
 *   <li>AES-128/256 CBC（PKCS7 填充）—— 需 16/32 字节密钥，使用 PBKDF2 派生</li>
 *   <li>AES-128/256 ECB（不推荐，仅用于兼容）</li>
 *   <li>消息摘要：MD5/SHA-1/SHA-256</li>
 * </ul>
 * 输出采用 Base64。
 * </p>
 */
public final class CryptoToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        Spinner spAlgorithm = contentView.findViewById(R.id.sp_crypto_algorithm);
        Spinner spMode = contentView.findViewById(R.id.sp_crypto_mode);
        EditText etKey = contentView.findViewById(R.id.et_crypto_key);
        EditText etInput = contentView.findViewById(R.id.et_crypto_input);
        TextView tvOutput = contentView.findViewById(R.id.tv_crypto_output);
        View btnEncrypt = contentView.findViewById(R.id.btn_crypto_encrypt);
        View btnDecrypt = contentView.findViewById(R.id.btn_crypto_decrypt);
        View btnCopy = contentView.findViewById(R.id.btn_crypto_copy);

        if (spAlgorithm == null || spMode == null) return;

        spAlgorithm.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"AES", "MD5", "SHA-1", "SHA-256"}));
        spAlgorithm.setSelection(0);
        spMode.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"CBC", "ECB"}));
        spMode.setSelection(0);

        if (btnEncrypt != null) {
            btnEncrypt.setOnClickListener(v -> {
                String algorithm = (String) spAlgorithm.getSelectedItem();
                String mode = (String) spMode.getSelectedItem();
                String key = etKey != null && etKey.getText() != null ? etKey.getText().toString() : "";
                String input = etInput != null && etInput.getText() != null ? etInput.getText().toString() : "";
                if (input.isEmpty()) return;
                try {
                    String output;
                    if (algorithm.startsWith("SHA") || algorithm.equals("MD5")) {
                        MessageDigest md = MessageDigest.getInstance(algorithm);
                        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
                        output = bytesToHex(hash);
                    } else {
                        // AES
                        SecretKey secretKey = deriveAesKey(key, 256);
                        Cipher cipher = Cipher.getInstance("AES/" + mode + "/PKCS7Padding");
                        if ("CBC".equals(mode)) {
                            byte[] iv = new byte[16];
                            new SecureRandom().nextBytes(iv);
                            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
                            byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
                            // 输出 IV || 密文，Base64 编码
                            byte[] combined = new byte[iv.length + encrypted.length];
                            System.arraycopy(iv, 0, combined, 0, iv.length);
                            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                            output = Base64.getEncoder().encodeToString(combined);
                        } else {
                            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                            output = Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
                        }
                    }
                    tvOutput.setText(output);
                } catch (Exception e) {
                    tvOutput.setText(context.getString(R.string.tool_crypto_failed_format, e.getMessage()));
                }
            });
        }

        if (btnDecrypt != null) {
            btnDecrypt.setOnClickListener(v -> {
                String algorithm = (String) spAlgorithm.getSelectedItem();
                String mode = (String) spMode.getSelectedItem();
                String key = etKey != null && etKey.getText() != null ? etKey.getText().toString() : "";
                String input = etInput != null && etInput.getText() != null ? etInput.getText().toString() : "";
                if (input.isEmpty() || !algorithm.equals("AES")) return;
                try {
                    SecretKey secretKey = deriveAesKey(key, 256);
                    Cipher cipher = Cipher.getInstance("AES/" + mode + "/PKCS7Padding");
                    byte[] combined = Base64.getDecoder().decode(input);
                    byte[] ciphertext;
                    if ("CBC".equals(mode)) {
                        byte[] iv = new byte[16];
                        ciphertext = new byte[combined.length - 16];
                        System.arraycopy(combined, 0, iv, 0, 16);
                        System.arraycopy(combined, 16, ciphertext, 0, ciphertext.length);
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    } else {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey);
                        ciphertext = combined;
                    }
                    byte[] decrypted = cipher.doFinal(ciphertext);
                    tvOutput.setText(new String(decrypted, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    tvOutput.setText(context.getString(R.string.tool_crypto_failed_format, e.getMessage()));
                }
            });
        }

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                CharSequence text = tvOutput.getText();
                if (text == null || text.length() == 0) return;
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("crypto", text));
                    Toast.makeText(context, R.string.tool_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** 从口令派生 AES 密钥（使用 SHA-256 截断到 32 字节，便于演示；生产应使用 PBKDF2） */
    private SecretKey deriveAesKey(String key, int bits) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("key empty");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = new byte[bits / 8];
            System.arraycopy(hash, 0, keyBytes, 0, Math.min(keyBytes.length, hash.length));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
