package com.gamecenter.app.network;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class LANManager {
    private static final String TAG = "LANManager";
    private static final int DISCOVERY_PORT = 9877;
    private static final int BROADCAST_INTERVAL = 3000;
    private static final int DISCOVERY_TIMEOUT = 8000;

    private DatagramSocket socket;
    private volatile boolean running = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<DiscoveredHost> discoveredHosts = Collections.synchronizedList(new ArrayList<>());
    private OnHostDiscoveredListener listener;

    private final String gameName;
    private final String playerName;
    private final int serverPort;

    private Thread broadcastThread;
    private Thread receiveThread;

    public LANManager(String gameName, String playerName, int serverPort) {
        this.gameName = gameName;
        this.playerName = playerName;
        this.serverPort = serverPort;
    }

    public void startDiscovery() {
        stopDiscovery();
        discoveredHosts.clear();
        running = true;

        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create socket: " + e.getMessage());
            return;
        }

        broadcastThread = new Thread(this::broadcastLoop, "LANBroadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();

        receiveThread = new Thread(this::receiveLoop, "LANReceive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void stopDiscovery() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }

        if (broadcastThread != null && broadcastThread.isAlive()) {
            broadcastThread.interrupt();
            broadcastThread = null;
        }

        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
            receiveThread = null;
        }
    }

    public List<DiscoveredHost> getDiscoveredHosts() {
        return Collections.unmodifiableList(discoveredHosts);
    }

    private void broadcastLoop() {
        while (running) {
            try {
                JSONObject broadcast = new JSONObject();
                broadcast.put("type", "DISCOVERY");
                broadcast.put("game", gameName);
                broadcast.put("player", playerName);
                broadcast.put("port", serverPort);

                byte[] data = broadcast.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, getBroadcastAddress(), DISCOVERY_PORT);

                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.send(packet);
                    } catch (Exception e) {
                    }
                }

                Thread.sleep(BROADCAST_INTERVAL);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting: " + e.getMessage());
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[1024];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                if (socket != null && !socket.isClosed()) {
                    socket.receive(packet);
                }

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(received);

                if ("DISCOVERY".equals(json.optString("type"))) {
                    String game = json.optString("game");
                    String player = json.optString("player");
                    int port = json.optInt("port", 0);
                    String hostIp = packet.getAddress().getHostAddress();

                    if (!game.equals(gameName)) continue;

                    DiscoveredHost host = new DiscoveredHost(hostIp, port, player);

                    boolean exists = false;
                    for (DiscoveredHost h : discoveredHosts) {
                        if (h.getIp().equals(hostIp)) {
                            h.updateLastSeen();
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        discoveredHosts.add(host);
                        postHostDiscovered(host);
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    private InetAddress getBroadcastAddress() throws UnknownHostException {
        return InetAddress.getByName("255.255.255.255");
    }

    private void postHostDiscovered(DiscoveredHost host) {
        if (listener != null) {
            mainHandler.post(() -> listener.onHostDiscovered(host));
        }
    }

    public void setOnHostDiscoveredListener(OnHostDiscoveredListener listener) {
        this.listener = listener;
    }

    public interface OnHostDiscoveredListener {
        void onHostDiscovered(DiscoveredHost host);
    }

    public static class DiscoveredHost {
        private final String ip;
        private final int port;
        private final String playerName;
        private long lastSeen;

        public DiscoveredHost(String ip, int port, String playerName) {
            this.ip = ip;
            this.port = port;
            this.playerName = playerName;
            this.lastSeen = System.currentTimeMillis();
        }

        public String getIp() { return ip; }
        public int getPort() { return port; }
        public String getPlayerName() { return playerName; }
        public long getLastSeen() { return lastSeen; }
        public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastSeen > DISCOVERY_TIMEOUT;
        }
    }
}
