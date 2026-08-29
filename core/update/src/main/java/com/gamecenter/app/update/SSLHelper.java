package com.gamecenter.app.update;

import android.util.Log;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

/**
 * SSL 信任助手，用于处理更新服务器的 SSL/TLS 连接配置。
 * <p>
 * 为更新服务器提供系统默认的 TLS 主机名验证策略：
 * <ul>
 *   <li>证书链和主机名验证始终使用系统默认逻辑</li>
 *   <li>代码不安装 TrustAll 证书管理器，也不关闭主机名验证</li>
 *   <li>更新 URL 的 HTTPS、来源和完整性约束由调用方的专用校验器负责</li>
 * </ul>
 * </p>
 * <p>
 * 安全说明：
 * <ul>
 *   <li>不再支持 TrustAll 证书信任，Debug 和 Release 模式均使用系统默认证书与主机名验证</li>
 *   <li>Debug 和 Release 模式均使用系统默认的主机名验证器</li>
 *   <li>开发时如需调试自签名证书，应通过 Android 网络安全配置
 *       (network_security_config.xml) 添加，而非在代码中信任所有证书</li>
 * </ul>
 * </p>
 */
public class SSLHelper {

    private static final String TAG = "SSLHelper";
    /**
     * 记录指定主机名以保留旧版 API 兼容性。
     *
     * <p>该方法不会改变证书信任链或绕过主机名验证。是否允许访问某个
     * 更新来源由 {@link UpdateUrlValidator} 在请求边界统一决定。</p>
     *
     * @param host 要信任的主机名
     */
    public static void trustHost(String host) {
        // Kept as a no-op compatibility hook. A host allow-list is not a
        // substitute for the platform's certificate and hostname checks.
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
            if (!UpdateUrlValidator.isHttps(url)) {
                Log.w(TAG, "Ignoring non-HTTPS update server URL");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse update server URL: " + e.getMessage());
        }
    }

    /**
     * 获取系统默认主机名验证器。
     * <p>
     * 验证策略：
     * <ul>
     *   <li>所有构建类型：使用 Android/Java 平台默认验证器</li>
     * </ul>
     * </p>
     *
     * @return 自定义 HostnameVerifier 实例
     */
    public static HostnameVerifier getHostnameVerifier() {
        return HttpsURLConnection.getDefaultHostnameVerifier();
    }

    /**
     * 对 HTTPS 连接应用自定义 SSL 配置。
     * <p>
     * 仅显式设置系统默认的 HostnameVerifier，证书验证使用系统默认逻辑。
     * 此方法应在打开连接后、发起请求前调用。
     * </p>
     *
     * @param conn 要配置的 HTTPS 连接
     */
    public static void applySsl(HttpsURLConnection conn) {
        if (conn == null) {
            return;
        }
        conn.setHostnameVerifier(getHostnameVerifier());
    }
}
