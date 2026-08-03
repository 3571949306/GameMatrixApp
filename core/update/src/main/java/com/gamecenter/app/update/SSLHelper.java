package com.gamecenter.app.update;

import android.util.Log;
import com.gamecenter.app.update.BuildConfig;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

/**
 * SSL 信任助手，用于处理更新服务器的 SSL/TLS 连接配置。
 * <p>
 * 为更新服务器提供主机名验证策略：
 * <ul>
 *   <li>维护一组受信任的主机名，仅对这些主机的 SSL 连接放宽主机名验证</li>
 *   <li>证书验证始终使用系统默认逻辑，不修改证书信任链</li>
 *   <li>Debug 模式：仅检查主机名是否在信任列表中</li>
 *   <li>Release 模式：主机名必须在信任列表中，且通过系统默认验证器的验证</li>
 * </ul>
 * </p>
 * <p>
 * 安全说明：
 * <ul>
 *   <li>不再支持 TrustAll 证书信任，Debug 和 Release 模式均使用系统默认证书验证</li>
 *   <li>主机名验证在 Debug 和 Release 模式下均要求主机在信任列表中</li>
 *   <li>Release 模式下还会额外调用系统默认的主机名验证器，双重保障</li>
 *   <li>开发时如需调试自签名证书，应通过 Android 网络安全配置
 *       (network_security_config.xml) 添加，而非在代码中信任所有证书</li>
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
     * 对 HTTPS 连接应用自定义 SSL 配置。
     * <p>
     * 仅设置自定义的 HostnameVerifier，证书验证使用系统默认逻辑。
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
        conn.setHostnameVerifier(getHostnameVerifier());
    }
}
