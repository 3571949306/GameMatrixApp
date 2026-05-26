package com.gamecenter.app.network;

import android.content.Context;
import org.json.JSONObject;

public class GameSocketServer {
    
    public interface ServerCallback {
        void onClientConnected(int clientId);
        void onClientDisconnected(int clientId);
        void onMessageReceived(int clientId, String message);
        void onError(String error);
    }
    
    public interface ClientConnectedListener {
        void onClientConnected(int clientId);
    }
    
    public interface ClientConnectedListenerWithInfo {
        void onClientConnected(int clientId, String info);
    }
    
    public interface ClientDisconnectedListener {
        void onClientDisconnected(int clientId);
    }
    
    public interface MessageReceivedListener {
        void onMessageReceived(int clientId, String message);
    }
    
    public interface MessageReceivedListenerWithJSON {
        void onMessageReceived(int clientId, JSONObject message);
    }
    
    public interface ErrorListener {
        void onError(String error);
    }
    
    private ServerCallback callback;
    private ClientConnectedListener clientConnectedListener;
    private ClientConnectedListenerWithInfo clientConnectedListenerWithInfo;
    private ClientDisconnectedListener clientDisconnectedListener;
    private MessageReceivedListener messageReceivedListener;
    private MessageReceivedListenerWithJSON messageReceivedListenerWithJSON;
    private ErrorListener errorListener;
    
    public GameSocketServer(Context context) {}
    
    public boolean start(int port, ServerCallback callback) { 
        this.callback = callback;
        return true; 
    }
    
    public boolean start(int port) { return true; }
    
    public boolean startWebSocket(String wsUrl) { return true; }
    
    public void stop() {}
    
    public void broadcast(String message) {}
    
    public void broadcast(JSONObject msg) {
        broadcast(msg.toString());
    }
    
    public void sendTo(int clientId, String message) {}
    
    public void sendTo(int clientId, JSONObject msg) {
        sendTo(clientId, msg.toString());
    }
    
    public void disconnectClient(int clientId, String reason) {}
    
    public int getConnectedClientCount() { return 0; }
    
    public void setOnClientConnectedListener(ClientConnectedListener listener) {
        this.clientConnectedListener = listener;
    }
    
    public void setOnClientConnectedListener(ClientConnectedListenerWithInfo listener) {
        this.clientConnectedListenerWithInfo = listener;
    }
    
    public void setOnClientDisconnectedListener(ClientDisconnectedListener listener) {
        this.clientDisconnectedListener = listener;
    }
    
    public void setOnMessageReceivedListener(MessageReceivedListener listener) {
        this.messageReceivedListener = listener;
    }
    
    public void setOnMessageReceivedListener(MessageReceivedListenerWithJSON listener) {
        this.messageReceivedListenerWithJSON = listener;
    }
    
    public void setOnErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }
    
    public void setOnErrorListener(java.util.function.Consumer<String> listener) {
        this.errorListener = null;
    }
}
