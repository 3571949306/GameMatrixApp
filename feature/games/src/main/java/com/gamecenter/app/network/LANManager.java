package com.gamecenter.app.network;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class LANManager {
    
    public interface ServiceDiscoveredListener {
        void onServiceDiscovered(Object info);
    }
    
    public interface ServiceLostListener {
        void onServiceLost(Object info);
    }
    
    public interface ErrorListener {
        void onError(String error);
    }
    
    private ServiceDiscoveredListener serviceDiscoveredListener;
    private ServiceLostListener serviceLostListener;
    private ErrorListener errorListener;
    
    public static LANManager getInstance(Context context) {
        return null;
    }
    
    public void startDiscovery(ServiceDiscoveredListener callback) {}
    
    public void stopDiscovery() {}
    
    public void unregisterService() {}
    
    public static boolean isValidIPAndPort(String address) {
        return address != null && address.matches("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+");
    }
    
    public static boolean isValidIPAddress(String address) {
        return address != null && address.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
    
    public void setOnServiceDiscoveredListener(ServiceDiscoveredListener listener) {
        this.serviceDiscoveredListener = listener;
    }
    
    public void setOnServiceLostListener(ServiceLostListener listener) {
        this.serviceLostListener = listener;
    }
    
    public void setOnErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }
    
    public String getLocalIPv4Address() {
        return "";
    }
}
