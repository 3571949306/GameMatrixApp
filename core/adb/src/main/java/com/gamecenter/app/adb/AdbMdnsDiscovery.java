package com.gamecenter.app.adb;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds only Android devices that voluntarily advertise a wireless-ADB endpoint
 * on the current LAN. This deliberately does not sweep subnets or probe ports.
 */
public final class AdbMdnsDiscovery implements Closeable {
    public static final String SERVICE_TYPE = "_adb-tls-connect._tcp.";
    private static final long SCAN_WINDOW_MS = 12_000L;

    public interface Listener {
        void onStarted();
        void onEndpoint(Endpoint endpoint);
        void onFinished();
        void onError(String message);
    }

    public static final class Endpoint {
        public final String name;
        public final String host;
        public final int port;

        Endpoint(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
    }

    private final NsdManager nsdManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> delivered = new HashSet<>();
    private int generation;
    private boolean scanning;
    private Listener listener;
    private NsdManager.DiscoveryListener discoveryListener;
    private Runnable timeout;

    public AdbMdnsDiscovery(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        Context app = context.getApplicationContext();
        nsdManager = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) throw new IllegalStateException("mDNS service is unavailable");
    }

    public synchronized boolean isScanning() {
        return scanning;
    }

    public synchronized void start(Listener nextListener) {
        if (nextListener == null) throw new IllegalArgumentException("Listener is required");
        stopLocked(false);
        int token = ++generation;
        listener = nextListener;
        scanning = true;
        delivered.clear();
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) {
                notifyStarted(token);
            }

            @Override public void onServiceFound(NsdServiceInfo service) {
                if (!SERVICE_TYPE.equals(service.getServiceType())) return;
                resolve(token, service);
            }

            @Override public void onServiceLost(NsdServiceInfo service) { }

            @Override public void onDiscoveryStopped(String serviceType) {
                finish(token, true);
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                fail(token, "mDNS 扫描启动失败（" + errorCode + "）");
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                finish(token, true);
            }
        };
        timeout = () -> finish(token, true);
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            main.postDelayed(timeout, SCAN_WINDOW_MS);
        } catch (RuntimeException error) {
            fail(token, "mDNS 扫描不可用：" + AdbEngine.explain(error));
        }
    }

    public synchronized void stop() {
        stopLocked(true);
    }

    private void resolve(int token, NsdServiceInfo service) {
        try {
            nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo failed, int errorCode) { }

                @Override public void onServiceResolved(NsdServiceInfo resolved) {
                    InetAddress address = resolved.getHost();
                    int port = resolved.getPort();
                    if (address == null || port < 1 || port > 65535) return;
                    Endpoint endpoint = new Endpoint(resolved.getServiceName(), address.getHostAddress(), port);
                    synchronized (AdbMdnsDiscovery.this) {
                        if (!isCurrent(token)) return;
                        String key = endpoint.host + ':' + endpoint.port;
                        if (!delivered.add(key)) return;
                    }
                    notifyEndpoint(token, endpoint);
                }
            });
        } catch (RuntimeException ignored) {
            // Discovery can stop while a resolve request is queued; no UI error is needed.
        }
    }

    private void notifyStarted(int token) {
        Listener target;
        synchronized (this) { target = isCurrent(token) ? listener : null; }
        if (target != null) target.onStarted();
    }

    private void notifyEndpoint(int token, Endpoint endpoint) {
        Listener target;
        synchronized (this) { target = isCurrent(token) ? listener : null; }
        if (target != null) target.onEndpoint(endpoint);
    }

    private void fail(int token, String message) {
        Listener target;
        synchronized (this) {
            if (!isCurrent(token)) return;
            target = listener;
            stopLocked(false);
        }
        if (target != null) target.onError(message);
    }

    private void finish(int token, boolean notify) {
        Listener target;
        synchronized (this) {
            if (!isCurrent(token)) return;
            target = listener;
            stopLocked(false);
        }
        if (notify && target != null) target.onFinished();
    }

    private boolean isCurrent(int token) {
        return scanning && token == generation;
    }

    private void stopLocked(boolean notify) {
        Listener target = listener;
        NsdManager.DiscoveryListener active = discoveryListener;
        if (!scanning && active == null) return;
        scanning = false;
        listener = null;
        discoveryListener = null;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        if (active != null) {
            try { nsdManager.stopServiceDiscovery(active); } catch (IllegalArgumentException ignored) { }
        }
        if (notify && target != null) target.onFinished();
    }

    @Override public synchronized void close() {
        stopLocked(false);
    }
}
