package com.gamecenter.app.adb;

import com.gamecenter.app.adb.protocol.AdbWireConnection;
import org.conscrypt.Conscrypt;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

final class TcpAdbLink implements AdbWireConnection.Link {
    final String host;
    private final int port;
    private final AdbIdentity identity;
    private final boolean pairing;
    private volatile Socket socket = new Socket();
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile boolean closed;
    byte[] peerKey;

    TcpAdbLink(String host, int port, AdbIdentity identity, boolean pairing) {
        this.host = host.trim(); this.port = port; this.identity = identity; this.pairing = pairing;
    }
    void connect() throws IOException {
        if (port < 1 || port > 65535) throw new IOException("端口范围应为 1–65535");
        // Numeric addresses only: no uncancellable DNS lookup in a connection job.
        InetAddress address;
        if (host.contains(":")) {
            if (!host.matches("[0-9a-fA-F:]+")) throw new IOException("请输入 IPv4 或 IPv6 地址");
            address = InetAddress.getByName(host);
        } else {
            String[] pieces = host.split("\\.", -1);
            if (pieces.length != 4) throw new IOException("请输入数字 IP 地址，不包含协议或端口");
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (!pieces[i].matches("[0-9]{1,3}")) throw new IOException("IP 地址不合法");
                int value = Integer.parseInt(pieces[i]); if (value > 255) throw new IOException("IP 地址不合法"); bytes[i] = (byte) value;
            }
            address = InetAddress.getByAddress(bytes);
        }
        socket.connect(new InetSocketAddress(address, port), 10000);
        socket.setTcpNoDelay(true); socket.setSoTimeout(15000);
        input = socket.getInputStream(); output = socket.getOutputStream();
        if (closed) { socket.close(); throw new IOException("连接已取消"); }
    }
    @Override public InputStream input() { return input; }
    @Override public OutputStream output() { return output; }
    @Override public void setReadTimeout(int millis) throws IOException { socket.setSoTimeout(millis); }
    @Override public void upgradeTls() throws IOException {
        try {
            byte[] expected = pairing ? null : identity.pin(host);
            if (!pairing && expected == null) throw new IOException("请先使用此 IP 的配对端口完成无线配对");
            SSLContext context = SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider());
            X509ExtendedKeyManager keys = new X509ExtendedKeyManager() {
                @Override public String[] getClientAliases(String type, Principal[] issuers) { return new String[]{"adb"}; }
                @Override public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) { return "adb"; }
                @Override public String[] getServerAliases(String type, Principal[] issuers) { return null; }
                @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return null; }
                @Override public X509Certificate[] getCertificateChain(String alias) { return new X509Certificate[]{identity.certificate}; }
                @Override public PrivateKey getPrivateKey(String alias) { return identity.privateKey; }
            };
            X509TrustManager trust = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException { throw new CertificateException("ADB 客户端不接受服务端模式"); }
                @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                    if (chain == null || chain.length == 0 || chain.length > 8) throw new CertificateException("缺少 ADB 设备证书");
                    byte[] key = chain[0].getPublicKey().getEncoded();
                    if (!pairing && !MessageDigest.isEqual(expected, key)) throw new CertificateException("ADB 设备身份变化，请重新配对");
                    // During pairing, SPAKE2 proves the peer against the user-entered code AND TLS exporter.
                    // The key is persisted only after that proof; this context is never used for HTTP.
                    peerKey = key;
                }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            context.init(new KeyManager[]{keys}, new TrustManager[]{trust}, new SecureRandom());
            Socket original = socket;
            SSLSocket secure = (SSLSocket) context.getSocketFactory().createSocket(original, host, port, true);
            socket = secure;
            if (closed) { secure.close(); throw new IOException("TLS 连接已取消"); }
            secure.setEnabledProtocols(new String[]{"TLSv1.3"});
            secure.setSoTimeout(15000); secure.startHandshake();
            input = secure.getInputStream(); output = secure.getOutputStream();
        } catch (GeneralSecurityException e) { throw new IOException("无法建立 ADB TLS 连接", e); }
    }
    byte[] exportPairingKey() throws IOException { return Conscrypt.exportKeyingMaterial((SSLSocket) socket, "adb-label\0", null, 64); }
    @Override public void close() throws IOException { closed = true; socket.close(); }
}
