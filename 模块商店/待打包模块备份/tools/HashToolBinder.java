package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
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

/**
 * 哈希计算与反查工具绑定器。
 * <p>
 * 职责：提供文本哈希计算（MD5、SHA-1、SHA-256）和哈希值反查（本地字典 + 在线 API）功能，
 * 并将结果展示在 UI 上。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>哈希计算在主线程执行（计算量小，无需异步）</li>
 *   <li>哈希反查在后台线程执行（涉及网络 I/O），通过 contentView.post() 回到主线程更新 UI</li>
 *   <li>反查策略采用"本地优先、在线兜底"的两级查找机制，优先匹配本地常用密码字典</li>
 * </ul>
 * </p>
 */
public final class HashToolBinder implements ToolBinder {

    private static final String TAG = "HashToolBinder";

    public HashToolBinder() {
    }

    /**
     * 将哈希工具的 UI 逻辑绑定到指定的内容视图上。
     * <p>
     * 绑定三个哈希计算按钮（MD5、SHA-1、SHA-256）和一个哈希反查按钮的点击事件。
     * 哈希计算即时执行，反查操作提交到线程池异步执行。
     * </p>
     *
     * @param context         应用上下文，用于显示 Toast 提示
     * @param contentView     工具的根视图容器，需包含哈希输入框、结果文本、反查输入框等控件
     * @param executorService 线程池执行器，用于异步执行哈希反查的网络请求
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executorService) {
        if (context == null || contentView == null || executorService == null) {
            return;
        }

        EditText etInput = contentView.findViewById(R.id.et_hash_input);
        TextView tvResult = contentView.findViewById(R.id.tv_hash_result);

        // MD5 哈希计算按钮
        View btnMd5 = contentView.findViewById(R.id.btn_hash_md5);
        if (btnMd5 != null) {
            btnMd5.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("MD5: " + hashText(text, "MD5"));
                }
            });
        }

        // SHA-1 哈希计算按钮
        View btnSha1 = contentView.findViewById(R.id.btn_hash_sha1);
        if (btnSha1 != null) {
            btnSha1.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("SHA1: " + hashText(text, "SHA-1"));
                }
            });
        }

        // SHA-256 哈希计算按钮
        View btnSha256 = contentView.findViewById(R.id.btn_hash_sha256);
        if (btnSha256 != null) {
            btnSha256.setOnClickListener(v -> {
                String text = etInput != null ? etInput.getText().toString() : "";
                if (tvResult != null) {
                    tvResult.setText("SHA256: " + hashText(text, "SHA-256"));
                }
            });
        }

        // 哈希反查区域
        EditText etReverse = contentView.findViewById(R.id.et_hash_reverse);
        TextView tvReverseResult = contentView.findViewById(R.id.tv_hash_reverse_result);
        View btnReverse = contentView.findViewById(R.id.btn_hash_reverse);
        if (btnReverse != null) {
            btnReverse.setOnClickListener(v -> {
                String hash = etReverse != null ? etReverse.getText().toString().trim() : "";
                // 边界条件：空输入时提示用户
                if (hash.isEmpty()) {
                    Toast.makeText(context, "请输入哈希值", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (tvReverseResult != null) {
                    tvReverseResult.setText("查询中...");
                }
                // 统一转为小写，确保与字典计算结果格式一致
                final String normalizedHash = hash.toLowerCase(Locale.ROOT);
                // 提交到后台线程执行反查，避免网络请求阻塞主线程
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

    /**
     * 对文本进行指定算法的哈希计算。
     *
     * @param text      待计算哈希的原始文本
     * @param algorithm 哈希算法名称，如 "MD5"、"SHA-1"、"SHA-256"
     * @return 哈希值的十六进制小写字符串；计算失败时返回错误提示信息
     */
    private static String hashText(String text, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            // 将每个字节转换为两位十六进制字符串
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 哈希值反查入口方法。
     * <p>
     * 采用两级查找策略：先查本地字典（快速、离线），再查在线 API（覆盖面广）。
     * 任一级查找成功即返回结果，两级均失败则返回未找到提示。
     * </p>
     *
     * @param hash 待反查的哈希值（已转为小写）
     * @return 反查结果字符串，格式为 "哈希值 -> \"明文\""；未找到时返回提示信息
     */
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

    /**
     * 在本地字典中查找哈希值对应的明文。
     * <p>
     * 查找范围包括两部分：
     * <ol>
     *   <li>常见密码字典（约60个高频密码）</li>
     *   <li>0-9999 的纯数字组合</li>
     * </ol>
     * 对每个候选明文分别计算 MD5 和 SHA-1，与目标哈希值进行比对。
     * </p>
     *
     * @param hash 待反查的哈希值（已转为小写）
     * @return 匹配结果字符串，格式为 "哈希值 -> \"明文\""；未匹配时返回空字符串
     */
    private static String lookupLocalDictionary(String hash) {
        // 常见密码字典，涵盖高频弱密码和常见品牌名
        String[] common = {
                "", "123456", "password", "123456789", "12345", "1234", "12345678",
                "admin", "1234567890", "qwerty", "abc123", "111111", "iloveyou",
                "123123", "000000", "hello", "monkey", "dragon", "master", "654321",
                "1", "12", "123", "1234567", "123456789", "987654321",
                "test", "root", "user", "pass", "guest", "login", "secret",
                "Android", "android", "google", "apple", "facebook", "twitter",
                "samsung", "xiaomi", "huawei", "oppo", "vivo", "oneplus",
                "GameMatrix", "ttc", "GameMatrix", "tools", "toolbox",
                "1q2w3e4r", "1qaz2wsx", "qwerty123", "password123",
                "letmein", "welcome", "football", "baseball", "starwars",
                "startrek", "nintendo", "playstation", "xbox", "minecraft"
        };
        // 逐个计算常见密码的哈希值并比对
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

        // 遍历 0-9999 的纯数字，覆盖常见的数字型弱密码
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

    /**
     * 通过在线 API 进行哈希值反查。
     * <p>
     * 依次尝试多个在线哈希反查服务，任一 API 返回有效结果即停止。
     * API 返回结果需满足以下条件才视为有效：非空、不包含 "error"、
     * 不包含 "not found"、不等于原始哈希值（即确实找到了明文）。
     * </p>
     *
     * @param hash 待反查的哈希值（已转为小写）
     * @return 匹配结果字符串，格式为 "在线匹配: 哈希值 -> \"明文\""；未匹配时返回空字符串
     */
    private static String lookupOnlineApi(String hash) {
        // 多个在线反查 API，按优先级排列
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
                conn.setRequestProperty("User-Agent", "GameMatrixApp/1.0");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    resp.append(line);
                }
                reader.close();
                String response = resp.toString().trim();
                // 过滤无效响应：空结果、错误信息、未找到提示、与输入相同（表示未解密）
                if (!response.isEmpty()
                        && !response.contains("error")
                        && !response.contains("not found")
                        && !response.equals(hash)) {
                    return "在线匹配: " + hash + " -> \"" + response + "\"";
                }
            } catch (Exception ignored) {
                Log.w(TAG, "Hash reverse lookup failed: " + ignored.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return "";
    }
}
