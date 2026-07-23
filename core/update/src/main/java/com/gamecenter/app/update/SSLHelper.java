package com.gamecenter.app.update;

import android.util.Log;
import com.gamecenter.app.update.BuildConfig;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSL 信任助手，用于处理更新服务器的 SSL/TLS 连接配置。
 * <p>
 * 为更新服务器提供灵活的 SSL 信任策略：
 * <ul>
 *   <li>维护一组受信任的主机名，仅对这些主机的 SSL 连接放宽验证</li>
 *   <li>Debug 模式下：信任所有证书（方便开发调试），但仍验证主机名在信任列表中</li>
 *   <li>Release 模式下：仅放宽主机名验证（信任列表中的主机），不修改证书验证逻辑</li>
 * </ul>
 * </p>
 * <p>
 * 安全说明：
 * <ul>
 *   <li>信任所有证书的 TrustManager 仅在 Debug 模式下启用，不会影响 Release 构建</li>
 *   <li>主机名验证在 Debug 和 Release 模式下均要求主机在信任列表中</li>
 *   <li>Release 模式下还会额外调用系统默认的主机名验证器，双重保障</li>
 * </ul>
 * </p>
 */
public class SSLHelper {

    private static final String TAG = "SSLHelper";
    /** 受信任的主机名集合，线程安全通过 synchronized 方法保证 */
    private static final Set<String> trustedHosts = new HashSet<>();

    /**
     * 将指定主机名添加到信任列表。
     *
     * @param host 要信任的主机名
     */
    public static void trustHost(String host) {
        synchronized (SSLHelper.class) {
            trustedHosts.add(host);
        }
    }

    /**
     * 从更新服务器 URL 中提取主机名并添加到信任列表。
     * 解析 URL 失败时记录警告日志。
     *
     * @param baseUrl 更新服务器的基础 URL
     */
    public static void trustUpdateServer(String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl);
            trustHost(url.getHost());
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse update server URL: " + e.getMessage());
        }
    }

    /**
     * 获取自定义主机名验证器。
     * <p>
     * 验证策略：
     * <ul>
     *   <li>Debug 模式：仅检查主机名是否在信任列表中</li>
     *   <li>Release 模式：主机名必须在信任列表中，且通过系统默认验证器的验证</li>
     * </ul>
     * </p>
     *
     * @return 自定义 HostnameVerifier 实例
     */
    public static HostnameVerifier getHostnameVerifier() {
        return (hostname, session) -> {
            if (BuildConfig.DEBUG) {
                return trustedHosts.contains(hostname);
            }
            return trustedHosts.contains(hostname)
                    && HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
        };
    }

    /**
     * 获取信任所有证书的 SSLSocketFactory。
     * <p>
     * <b>仅在 Debug 模式下启用</b>，用于开发调试时绕过自签名证书验证。
     * Release 模式下返回 null，使用系统默认的证书验证逻辑。
     * </p>
     *
     * @return 信任所有证书的 SSLSocketFactory；Release 模式下返回 null
     */
    public static SSLSocketFactory getSSLSocketFactory() {
        if (!BuildConfig.DEBUG) {
            return null;
        }
        try {
            // 创建信任所有证书的 TrustManager（仅用于 Debug 模式）
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "SSL init failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 对 HTTPS 连接应用自定义 SSL 配置。
     * <p>
     * 设置自定义的 SSLSocketFactory（仅 Debug 模式）和 HostnameVerifier。
     * 此方法应在打开连接后、发起请求前调用。
     * </p>
     *
     * @param conn 要配置的 HTTPS 连接
     */
    public static void applySsl(HttpsURLConnection conn) {
        String host = conn.getURL().getHost();
        // Only the configured update host needs the custom policy. Applying a
        // host allow-list verifier to GitHub (or its redirect hosts) rejects a
        // perfectly valid platform certificate before Android can verify it.
        synchronized (SSLHelper.class) {
            if (!trustedHosts.contains(host)) {
                return;
            }
        }
        SSLSocketFactory factory = getSSLSocketFactory();
        if (factory != null) {
            conn.setSSLSocketFactory(factory);
        }
        conn.setHostnameVerifier(getHostnameVerifier());
    }
}
