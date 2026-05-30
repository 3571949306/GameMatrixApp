package com.gamecenter.app.online;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.net.Socket;

/**
 * WebSocket 协议处理器。
 * 
 * <p>负责处理 WebSocket 协议的握手和消息帧的编解码。
 * 支持文本消息的发送和接收。</p>
 * 
 * <p>WebSocket 协议参考：RFC 6455</p>
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class WebSocketHandler {
    
    private static final String TAG = "WebSocketHandler";
    
    /** WebSocket 握手 GUID */
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    /** 已建立连接的客户端集合（Socket -> 输出流） */
    private final Map<Socket, OutputStream> connectedClients;
    
    /** 消息监听器 */
    private MessageListener messageListener;
    
    /**
     * 消息监听器接口。
     */
    public interface MessageListener {
        /**
         * 收到消息时回调。
         * 
         * @param client 发送消息的客户端 Socket
         * @param message 消息内容
         */
        void onMessage(@NonNull Socket client, @NonNull String message);
        
        /**
         * 客户端断开连接时回调。
         * 
         * @param client 断开连接的客户端 Socket
         */
        void onDisconnected(@NonNull Socket client);
    }
    
    /**
     * 构造函数。
     */
    public WebSocketHandler() {
        this.connectedClients = new ConcurrentHashMap<>();
    }
    
    /**
     * 设置消息监听器。
     * 
     * @param listener 消息监听器
     */
    public void setMessageListener(@Nullable MessageListener listener) {
        this.messageListener = listener;
    }
    
    /**
     * 处理 WebSocket 握手请求。
     * 
     * @param clientSocket 客户端 Socket
     * @return 握手成功返回 true，否则返回 false
     */
    public boolean handleHandshake(@NonNull Socket clientSocket) {
        try {
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();
            
            // 读取 HTTP 请求头
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder requestHeaders = new StringBuilder();
            String line;
            String webSocketKey = null;
            
            // 读取请求行和请求头
            line = reader.readLine();
            if (line == null || !line.startsWith("GET")) {
                Log.w(TAG, "无效的 WebSocket 握手请求");
                return false;
            }
            
            // 读取所有请求头
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break; // 空行表示请求头结束
                }
                requestHeaders.append(line).append("\r\n");
                
                // 提取 Sec-WebSocket-Key
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    webSocketKey = line.substring("sec-websocket-key:".length()).trim();
                }
            }
            
            if (webSocketKey == null) {
                Log.w(TAG, "缺少 Sec-WebSocket-Key");
                return false;
            }
            
            // 生成握手响应
            String acceptKey = generateAcceptKey(webSocketKey);
            if (acceptKey == null) {
                Log.e(TAG, "生成 Accept-Key 失败");
                return false;
            }
            
            // 发送握手响应
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";
            
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
            
            // 将客户端添加到已连接集合
            connectedClients.put(clientSocket, output);
            
            Log.d(TAG, "WebSocket 握手成功: " + clientSocket.getInetAddress().getHostAddress());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "WebSocket 握手失败", e);
            return false;
        }
    }
    
    /**
     * 生成 WebSocket Accept-Key。
     * 
     * @param webSocketKey 客户端发送的 Sec-WebSocket-Key
     * @return Accept-Key，失败返回 null
     */
    @Nullable
    private String generateAcceptKey(@NonNull String webSocketKey) {
        try {
            String combined = webSocketKey + WEBSOCKET_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-1 算法不可用", e);
            return null;
        }
    }
    
    /**
     * 读取并解析 WebSocket 消息帧。
     * 
     * @param clientSocket 客户端 Socket
     * @return 解析得到的文本消息，失败或连接关闭返回 null
     */
    @Nullable
    public String readMessage(@NonNull Socket clientSocket) {
        try {
            InputStream input = clientSocket.getInputStream();
            
            // 读取第一个字节（FIN + RSV + Opcode）
            int firstByte = input.read();
            if (firstByte == -1) {
                // 连接已关闭
                handleClientDisconnect(clientSocket);
                return null;
            }
            
            // 检查是否为文本帧 (opcode = 0x1) 或连接关闭帧 (opcode = 0x8)
            int opcode = firstByte & 0x0F;
            if (opcode == 0x8) {
                // 连接关闭帧
                Log.d(TAG, "收到连接关闭帧");
                handleClientDisconnect(clientSocket);
                return null;
            }
            
            if (opcode != 0x1 && opcode != 0x0) {
                // 仅支持文本帧和继续帧
                Log.w(TAG, "不支持的 opcode: " + opcode);
                return null;
            }
            
            // 读取第二个字节（Mask + Payload length）
            int secondByte = input.read();
            if (secondByte == -1) {
                handleClientDisconnect(clientSocket);
                return null;
            }
            
            boolean masked = (secondByte & 0x80) != 0;
            long payloadLength = secondByte & 0x7F;
            
            // 处理扩展长度
            if (payloadLength == 126) {
                // 后续 2 字节为 16 位长度
                int b1 = input.read();
                int b2 = input.read();
                if (b1 == -1 || b2 == -1) {
                    handleClientDisconnect(clientSocket);
                    return null;
                }
                payloadLength = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
            } else if (payloadLength == 127) {
                // 后续 8 字节为 64 位长度（此处简化，仅读取低 32 位）
                long length = 0;
                for (int i = 0; i < 8; i++) {
                    int b = input.read();
                    if (b == -1) {
                        handleClientDisconnect(clientSocket);
                        return null;
                    }
                    length = (length << 8) | (b & 0xFF);
                }
                payloadLength = length;
            }
            
            // 读取 Masking-key（如果客户端发送的消息需要解掩码）
            byte[] maskingKey = null;
            if (masked) {
                maskingKey = new byte[4];
                int readBytes = 0;
                while (readBytes < 4) {
                    int b = input.read();
                    if (b == -1) {
                        handleClientDisconnect(clientSocket);
                        return null;
                    }
                    maskingKey[readBytes++] = (byte) b;
                }
            }
            
            // 读取 Payload
            byte[] payload = new byte[(int) payloadLength];
            int totalRead = 0;
            while (totalRead < payloadLength) {
                int bytesRead = input.read(payload, totalRead, (int) (payloadLength - totalRead));
                if (bytesRead == -1) {
                    handleClientDisconnect(clientSocket);
                    return null;
                }
                totalRead += bytesRead;
            }
            
            // 解掩码（如果消息被掩码）
            if (masked && maskingKey != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
                }
            }
            
            // 转换为字符串（假设为 UTF-8 编码的文本消息）
            String message = new String(payload, StandardCharsets.UTF_8);
            
            Log.d(TAG, "收到消息: " + message);
            
            // 回调消息监听器
            if (messageListener != null) {
                messageListener.onMessage(clientSocket, message);
            }
            
            return message;
            
        } catch (IOException e) {
            Log.e(TAG, "读取消息失败", e);
            handleClientDisconnect(clientSocket);
            return null;
        }
    }
    
    /**
     * 发送文本消息给指定客户端。
     * 
     * @param client 目标客户端 Socket
     * @param message 文本消息
     * @return 发送成功返回 true，否则返回 false
     */
    public boolean sendMessage(@NonNull Socket client, @NonNull String message) {
        try {
            OutputStream output = connectedClients.get(client);
            if (output == null) {
                Log.w(TAG, "客户端未连接");
                return false;
            }
            
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            
            // 构建 WebSocket 帧
            // FIN=1, RSV=0, Opcode=0x1 (文本帧)
            output.write(0x81);
            
            // Payload length（不使用掩码，因为服务器发送的消息不需要掩码）
            if (payload.length <= 125) {
                output.write(payload.length);
            } else if (payload.length <= 65535) {
                output.write(126);
                output.write((payload.length >> 8) & 0xFF);
                output.write(payload.length & 0xFF);
            } else {
                // 长度超过 65535 的情况（简化实现中不支持）
                Log.w(TAG, "消息过长，不支持");
                return false;
            }
            
            // 写入 Payload
            output.write(payload);
            output.flush();
            
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "发送消息失败", e);
            handleClientDisconnect(client);
            return false;
        }
    }
    
    /**
     * 广播消息给所有已连接的客户端。
     * 
     * @param message 要广播的消息
     */
    public void broadcastMessage(@NonNull String message) {
        for (Socket client : new ConcurrentHashMap<>(connectedClients).keySet()) {
            if (!sendMessage(client, message)) {
                Log.w(TAG, "广播消息失败: " + client.getInetAddress().getHostAddress());
            }
        }
    }
    
    /**
     * 处理客户端断开连接。
     * 
     * @param client 断开连接的客户端 Socket
     */
    private void handleClientDisconnect(@NonNull Socket client) {
        try {
            connectedClients.remove(client);
            if (!client.isClosed()) {
                client.close();
            }
            
            Log.d(TAG, "客户端已断开: " + client.getInetAddress().getHostAddress());
            
            // 回调监听器
            if (messageListener != null) {
                messageListener.onDisconnected(client);
            }
            
        } catch (IOException e) {
            Log.w(TAG, "关闭客户端 Socket 失败", e);
        }
    }
    
    /**
     * 获取当前连接的客户端数量。
     * 
     * @return 客户端数量
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }
    
    /**
     * 关闭所有连接并释放资源。
     */
    public void release() {
        for (Socket client : new ConcurrentHashMap<>(connectedClients).keySet()) {
            handleClientDisconnect(client);
        }
        connectedClients.clear();
        Log.d(TAG, "WebSocketHandler 资源已释放");
    }
}
