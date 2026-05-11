package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.gamecenter.app.R;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class HashToolBinder implements ToolBinder {

    public HashToolBinder() {
    }

    @Override
    public void bind(Context context, View contentView, ExecutorService executorService) {
        if (context == null || contentView == null || executorService == null) {
            return;
        }

        EditText etInput = contentView.findViewById(R.id.et_hash_input);
        TextView tvResult = contentView.findViewById(R.id.tv_hash_result);

        View btnMd5 = contentView.findViewById(R.id.btn_hash_md5);
        if (btnMd5 != null) {
            btnMd5.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("MD5: " + hashText(text, "MD5"));
                }
            });
        }

        View btnSha1 = contentView.findViewById(R.id.btn_hash_sha1);
        if (btnSha1 != null) {
            btnSha1.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("SHA1: " + hashText(text, "SHA-1"));
                }
            });
        }

        View btnSha256 = contentView.findViewById(R.id.btn_hash_sha256);
        if (btnSha256 != null) {
            btnSha256.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("SHA256: " + hashText(text, "SHA-256"));
                }
            });
        }

        EditText etReverse = contentView.findViewById(R.id.et_hash_reverse);
        TextView tvReverseResult = contentView.findViewById(R.id.tv_hash_reverse_result);
        View btnReverse = contentView.findViewById(R.id.btn_hash_reverse);
        if (btnReverse != null) {
            btnReverse.setOnClickListener(v -> {
                String hash = etReverse != null ? etReverse.getText().toString().trim() : "";
                if (hash.isEmpty()) {
                    Toast.makeText(context, "请输入哈希值", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (tvReverseResult != null) {
                    tvReverseResult.setText("查询中...");
                }
                final String normalizedHash = hash.toLowerCase(Locale.ROOT);
                executorService.execute(() -> {
                    String result = reverseHashLookup(normalizedHash);
                    contentView.post(() -> {
                        if (tvReverseResult != null) {
                            tvReverseResult.setText(result);
                        }
                    });
                });
            });
        }
    }

    private static String hashText(String text, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    private static String reverseHashLookup(String hash) {
        String local = lookupLocalDictionary(hash);
        if (!local.isEmpty()) {
            return local;
        }

        String online = lookupOnlineApi(hash);
        if (!online.isEmpty()) {
            return online;
        }

        return "未找到\n该哈希值在本地字典和在线数据库中均未匹配。\n建议尝试其他在线工具如:crackstation.net、hashes.org";
    }

    private static String lookupLocalDictionary(String hash) {
        String[] common = {
                "", "123456", "password", "123456789", "12345", "1234", "12345678",
                "admin", "1234567890", "qwerty", "abc123", "111111", "iloveyou",
                "123123", "000000", "hello", "monkey", "dragon", "master", "654321",
                "1", "12", "123", "1234567", "123456789", "987654321",
                "test", "root", "user", "pass", "guest", "login", "secret",
                "Android", "android", "google", "apple", "facebook", "twitter",
                "samsung", "xiaomi", "huawei", "oppo", "vivo", "oneplus",
                "GameCenter", "ttc", "gamecenter", "tools", "toolbox",
                "1q2w3e4r", "1qaz2wsx", "qwerty123", "password123",
                "letmein", "welcome", "football", "baseball", "starwars",
                "startrek", "nintendo", "playstation", "xbox", "minecraft"
        };
        for (String word : common) {
            String md5 = hashText(word, "MD5");
            if (md5.equalsIgnoreCase(hash)) {
                return md5 + " -> \"" + word + "\"";
            }
            String sha1 = hashText(word, "SHA-1");
            if (sha1.equalsIgnoreCase(hash)) {
                return sha1 + " -> \"" + word + "\"";
            }
        }

        for (int i = 0; i < 10000; i++) {
            String number = String.valueOf(i);
            String md5 = hashText(number, "MD5");
            if (md5.equalsIgnoreCase(hash)) {
                return md5 + " -> \"" + number + "\"";
            }
            String sha1 = hashText(number, "SHA-1");
            if (sha1.equalsIgnoreCase(hash)) {
                return sha1 + " -> \"" + number + "\"";
            }
        }
        return "";
    }

    private static String lookupOnlineApi(String hash) {
        String[] apis = {
                "https://www.nitrxgen.in/md5db/" + hash,
                "https://md5.joerick.me/api/reverse.php?hash=" + hash,
        };
        for (String apiUrl : apis) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "GameCenterApp/1.0");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    resp.append(line);
                }
                reader.close();
                String response = resp.toString().trim();
                if (!response.isEmpty()
                        && !response.contains("error")
                        && !response.contains("not found")
                        && !response.equals(hash)) {
                    return "在线匹配: " + hash + " -> \"" + response + "\"";
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return "";
    }
}
