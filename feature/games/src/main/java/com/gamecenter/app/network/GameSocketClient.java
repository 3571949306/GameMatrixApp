package com.gamecenter.app.network;

import android.content.Context;
import org.json.JSONObject;
import java.util.function.Consumer;

public class GameSocketClient {
    
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        ERROR
    }
    
    public interface Callback {
        void onStateChanged(ConnectionState state);
        void onMessageReceived(String message);
        void onError(String error);
    }
    
    public interface ConnectedListener {
        void onConnected();
    }
    
    public interface DisconnectedListener {
        void onDisconnected();
    }
    
    public interface StateChangedListener {
        void onStateChanged(ConnectionState state);
    }
    
    public interface MessageReceivedListener {
        void onMessageReceived(String message);
    }
    
    public interface ErrorListener {
        void onError(String error);
    }
    
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private Callback callback;
    private ConnectedListener connectedListener;
    private DisconnectedListener disconnectedListener;
    private StateChangedListener stateChangedListener;
    private MessageReceivedListener messageReceivedListener;
    private ErrorListener errorListener;
    private String playerName = "Player";
    
    public static GameSocketClient getInstance(Context context) {
        return null;
    }
    
    public void connect(String host, int port, String roomCode, Callback callback) {
        this.callback = callback;
        this.state = ConnectionState.CONNECTING;
    }
    
    public void connect(String ip, int port) {
        this.state = ConnectionState.CONNECTING;
    }
    
    public void connectWebSocket(String wsUrl) {}
    
    public void disconnect() {
        this.state = ConnectionState.DISCONNECTED;
    }
    
    public void sendMessage(String message) {}
    
    public void sendMessage(JSONObject msg) {
        sendMessage(msg.toString());
    }
    
    public ConnectionState getState() { return state; }
    
    public boolean isConnected() { 
        return state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED; 
    }
    
    public void setPlayerName(String name) {
        this.playerName = name;
    }
    
    public void setOnConnectedListener(ConnectedListener listener) {
        this.connectedListener = listener;
    }
    
    public void setOnDisconnectedListener(DisconnectedListener listener) {
        this.disconnectedListener = listener;
    }
    
    public void setOnConnectedListener(Object listener) {
        this.connectedListener = null;
    }
    
    public void setOnStateChangedListener(StateChangedListener listener) {
        this.stateChangedListener = listener;
    }
    
    public void setOnStateChangedListener(Consumer<ConnectionState> listener) {
        this.stateChangedListener = null;
    }
    
    public void setOnMessageReceivedListener(MessageReceivedListener listener) {
        this.messageReceivedListener = listener;
    }
    
    public void setOnMessageReceivedListener(Object listener) {
        this.messageReceivedListener = null;
    }
    
    public void setOnErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }
    
    public void setOnErrorListener(Consumer<String> listener) {
        this.errorListener = null;
    }
}
