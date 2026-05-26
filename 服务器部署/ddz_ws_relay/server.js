const WebSocket = require('ws');
const http = require('http');
const url = require('url');

const PORT = 18080;
const HOST = '127.0.0.1';

// 房间管理：roomCode -> { host: ws, clients: Map<clientId, ws>, createdAt }
const rooms = new Map();

// 生成唯一客户端 ID
function generateClientId() {
    return Math.random().toString(36).substring(2, 10) + Date.now().toString(36).substring(2, 6);
}

function extractBearerToken(req) {
    const authHeader = req.headers['authorization'];
    if (authHeader && authHeader.startsWith('Bearer ')) {
        return authHeader.substring(7);
    }
    return null;
}

// 清理空房间（每 10 分钟）
setInterval(() => {
    const now = Date.now();
    let cleaned = 0;
    for (const [code, room] of rooms.entries()) {
        const isEmpty = !room.host && room.clients.size === 0;
        const isOld = now - room.createdAt > 3600000; // 1 小时
        if (isEmpty && isOld) {
            rooms.delete(code);
            cleaned++;
        }
    }
    if (cleaned > 0) {
        console.log(`[Cleanup] Removed ${cleaned} empty rooms, total: ${rooms.size}`);
    }
}, 600000);

// 创建 HTTP 服务器
const server = http.createServer((req, res) => {
    const parsedUrl = url.parse(req.url, true);
    
    // 健康检查
    if (parsedUrl.pathname === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ 
            status: 'ok', 
            service: 'ddz-ws-relay',
            rooms: rooms.size,
            uptime: process.uptime()
        }));
        return;
    }
    
    // 统计信息
    if (parsedUrl.pathname === '/stats') {
        const stats = {
            rooms: rooms.size,
            uptime: process.uptime(),
            memory: process.memoryUsage()
        };
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(stats));
        return;
    }
    
    res.writeHead(404);
    res.end('Not Found');
});

// 创建 WebSocket 服务器
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
    const parsedUrl = url.parse(req.url, true);
    const query = parsedUrl.query;
    const roomCode = query.room;
    const role = query.role;
    const clientId = query.clientId || generateClientId();
    const token = query.token || extractBearerToken(req);
    
    if (!roomCode || !role) {
        console.log(`[Reject] Missing room or role`);
        ws.close(1008, 'Missing room or role');
        return;
    }
    
    console.log(`[${roomCode}] ${role} connected (id: ${clientId})`);
    
    // 初始化房间
    if (!rooms.has(roomCode)) {
        rooms.set(roomCode, { host: null, clients: new Map(), createdAt: Date.now() });
    }
    const room = rooms.get(roomCode);
    
    // 标记连接已加入房间
    ws._roomCode = roomCode;
    ws._role = role;
    ws._clientId = clientId;
    ws._joined = false;
    
    if (role === 'host') {
        // 房主连接
        if (room.host) {
            console.log(`[${roomCode}] Replacing existing host`);
            room.host.close(1008, 'New host connected');
        }
        room.host = ws;
        ws._joined = true;
        
        // 发送欢迎消息
        ws.send(JSON.stringify({
            type: 'WELCOME',
            clientId: 'host',
            roomCode: roomCode,
            message: 'You are the host'
        }));
        
        ws.on('message', (data) => {
            if (room.host !== ws) return;
            
            // 转发给所有客户端
            let broadcastCount = 0;
            room.clients.forEach((clientWs) => {
                if (clientWs.readyState === WebSocket.OPEN) {
                    clientWs.send(data);
                    broadcastCount++;
                }
            });
            
            // 如果是 PING，回复 PONG 给房主自己
            try {
                const msg = JSON.parse(data);
                if (msg.type === 'PING') {
                    ws.send(JSON.stringify({ type: 'PONG', ts: Date.now() }));
                }
            } catch (e) {}
        });
        
        ws.on('close', () => {
            console.log(`[${roomCode}] host disconnected`);
            if (room.host === ws) {
                room.host = null;
                // 通知所有客户端房主已离开
                room.clients.forEach((clientWs) => {
                    if (clientWs.readyState === WebSocket.OPEN) {
                        clientWs.send(JSON.stringify({ type: 'HOST_DISCONNECTED' }));
                    }
                });
            }
        });
        
    } else {
        // 客户端连接
        room.clients.set(clientId, ws);
        ws._joined = true;
        
        // 发送欢迎消息
        ws.send(JSON.stringify({
            type: 'WELCOME',
            clientId: clientId,
            roomCode: roomCode,
            message: 'Connected to room'
        }));
        
        ws.on('message', (data) => {
            // 转发给房主
            if (room.host && room.host.readyState === WebSocket.OPEN) {
                room.host.send(data);
            }
            
            // 如果是 PING，回复 PONG
            try {
                const msg = JSON.parse(data);
                if (msg.type === 'PING') {
                    ws.send(JSON.stringify({ type: 'PONG', ts: Date.now() }));
                }
            } catch (e) {}
        });
        
        ws.on('close', () => {
            console.log(`[${roomCode}] client ${clientId} disconnected`);
            room.clients.delete(clientId);
        });
    }
    
    // 通用错误处理
    ws.on('error', (err) => {
        console.error(`[${roomCode}] ${role} error:`, err.message);
    });
    
    // 发送房间状态
    ws.send(JSON.stringify({
        type: 'ROOM_STATE',
        roomCode: roomCode,
        role: role,
        clientCount: room.clients.size,
        hasHost: room.host !== null
    }));
});

server.listen(PORT, HOST, () => {
    console.log(`DDZ WebSocket Relay listening on ${HOST}:${PORT}`);
    console.log(`Health check: http://${HOST}:${PORT}/health`);
    console.log(`Stats: http://${HOST}:${PORT}/stats`);
});
