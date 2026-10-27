package com.gamecenter.app.network.p2p.nat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;

/**
 * 突破校园网/企业残废 WiFi 的核心武器：
 * <p>
 * 在保持当前 WiFi 连接的同时，强行激活手机 4G/5G 蜂窝网卡并将特定打洞 Socket 绑定至蜂窝通道，
 * 直接利用三大运营商 5G 网络 100% 普及的原生公网 IPv6 完成秒级穿透。
 */
public class MultiNetworkBinder {

    private static final String TAG = "MultiNetworkBinder";

    private final ConnectivityManager connectivityManager;
    private volatile Network cellularNetwork;
    private ConnectivityManager.NetworkCallback networkCallback;

    public MultiNetworkBinder(@Nullable Context context) {
        if (context != null) {
            this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        } else {
            this.connectivityManager = null;
        }
    }

    /**
     * 向系统申请激活蜂窝移动数据网络 (TRANSPORT_CELLULAR)。
     */
    public synchronized void requestCellularChannel() {
        if (connectivityManager == null) return;

        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "Cellular channel available: " + network);
                    cellularNetwork = network;
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.i(TAG, "Cellular channel lost: " + network);
                    if (network.equals(cellularNetwork)) {
                        cellularNetwork = null;
                    }
                }
            };

            connectivityManager.requestNetwork(request, networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Failed requesting cellular channel: " + e.getMessage());
        }
    }

    /**
     * 将指定的 UDP 打洞套接字强制绑定到 5G 蜂窝网卡上。
     */
    public boolean bindSocketToCellular(@NonNull DatagramSocket socket) {
        Network net = this.cellularNetwork;
        if (net == null) return false;
        try {
            net.bindSocket(socket);
            Log.i(TAG, "Bound DatagramSocket to cellular network successfully");
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed binding DatagramSocket to cellular: " + e.getMessage());
            return false;
        }
    }

    /**
     * 将指定的 TCP 套接字强制绑定到 5G 蜂窝网卡上。
     */
    public boolean bindSocketToCellular(@NonNull Socket socket) {
        Network net = this.cellularNetwork;
        if (net == null) return false;
        try {
            net.bindSocket(socket);
            Log.i(TAG, "Bound Socket to cellular network successfully");
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed binding Socket to cellular: " + e.getMessage());
            return false;
        }
    }

    /**
     * 释放蜂窝网络请求。
     */
    public synchronized void release() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
            cellularNetwork = null;
        }
    }

    @Nullable
    public Network getCellularNetwork() {
        return cellularNetwork;
    }
}
