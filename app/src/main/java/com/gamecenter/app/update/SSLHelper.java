package com.gamecenter.app.update;

import android.util.Log;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SSLHelper {

    private static final String TAG = "SSLHelper";
    private static volatile boolean initialized = false;
    private static final Set<String> trustedHosts = new HashSet<>();

    public static void trustHost(String host) {
        synchronized (SSLHelper.class) {
            trustedHosts.add(host);
            if (!initialized) {
                initSSL();
            }
        }
    }

    public static void trustUpdateServer(String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl);
            trustHost(url.getHost());
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse update server URL: " + e.getMessage());
        }
    }

    private static void initSSL() {
        if (initialized) return;
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
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

            SSLSocketFactory factory = sc.getSocketFactory();
            HostnameVerifier verifier = new HostnameVerifier() {
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                    return trustedHosts.contains(hostname);
                }
            };

            HttpsURLConnection.setDefaultSSLSocketFactory(factory);
            HttpsURLConnection.setDefaultHostnameVerifier(verifier);

            initialized = true;
            Log.d(TAG, "SSL initialized, trusted hosts: " + trustedHosts);
        } catch (Exception e) {
            Log.e(TAG, "SSL init failed: " + e.getMessage());
        }
    }
}
