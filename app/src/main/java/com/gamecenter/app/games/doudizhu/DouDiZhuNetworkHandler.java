package com.gamecenter.app.games.doudizhu;

import android.os.Handler;
import android.util.Log;

import com.gamecenter.app.network.GameSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 斗地主网络消息处理器。
 *
 * <p>负责处理房主端（Host）和客户端（Client）之间的网络消息收发逻辑，
 * 包括消息分发、客户端意图（出牌/不出/叫地主）的发送与重试、
 * 断线重连后的意图恢复等。</p>
 *
 * <p>你可以把这个类想象成"邮局"——它负责把你的操作（出牌、叫地主等）
 * 打包成信件发出去，如果对方没收到就再发一次；同时它也负责接收别人发来的信件，
 * 转交给Activity处理。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>通过 {@link NetworkDelegate} 委托接口与 Activity 交互，避免直接持有 Activity 引用
 *       （邮局不需要知道收信人的全部信息，只要知道怎么投递就行）</li>
 *   <li>客户端意图采用"发送+两次重试"策略（220ms 和 700ms 后各重发一次），
 *       提高弱网环境下的可靠性（重要信件发三遍，确保对方收到）</li>
 *   <li>断线时将意图缓存到 pendingClientIntent，重连后自动补发
 *       （信没寄出去先存着，等网络恢复再寄）</li>
 *   <li>房主端收到客户端动作时，通过 SyncManager 的 actionId 去重，防止重复处理</li>
 * </ul>
 */
public class DouDiZhuNetworkHandler {

    private static final String TAG = "DouDiZhuNetwork";
    private static final int TOTAL_SEATS = 3;
    private static final int STATE_BIDDING = 1;
    private static final int STATE_PLAYING = 2;

    /** 主线程 Handler，用于延迟调度重试任务 */
    private final Handler handler;

    /** 断线时缓存的客户端意图，重连后自动补发 */
    private JSONObject pendingClientIntent;

    /** 网络操作委托接口，由 Activity 实现 */
    private final NetworkDelegate delegate;

    /**
     * 网络操作委托接口。
     *
     * <p>Activity 需实现此接口，提供游戏状态查询、UI 更新和消息处理等方法。
     * 这种委托模式将网络逻辑与 Activity 解耦，便于测试和维护。</p>
     */
    public interface NetworkDelegate {
        /** Activity 是否正在 finishing */
        boolean isFinishing();
        /** Activity 是否已 destroyed */
        boolean isDestroyed();
        /** 获取当前游戏状态 */
        int getGameState();
        /** 获取当前轮到的座位索引 */
        int getCurrentTurn();
        /** 获取本机座位索引 */
        int getMySeatIndex();
        /** 获取运行模式（0=房主，1=客户端） */
        int getMode();
        /** 获取各座位的客户端 ID 数组 */
        int[] getSeatClientIds();
        /** 获取各座位的类型数组 */
        int[] getSeatTypes();
        /** 设置指定座位的客户端 ID */
        void setSeatClientId(int seatIndex, int clientId);
        /** 获取网络客户端实例 */
        GameSocketClient getClient();
        /** 获取同步管理器实例 */
        DouDiZhuSyncManager getSyncManager();
        /** 显示 Toast 提示 */
        void showToast(String message);
        /** 隐藏所有操作按钮 */
        void hideAllButtons();
        /** 更新连接状态文本和颜色 */
        void updateConnectionStatus(String text, int color);
        /** 将本地状态同步到 SeatManager */
        void syncLocalToManager();
        /** 将 SeatManager 状态同步回本地变量 */
        void syncManagerToLocal();
        /** 处理客户端加入房间请求（房主端） */
        void handleClientJoin(int clientId, JSONObject msg);
        /** 处理远程玩家出牌请求（房主端） */
        void handleRemotePlayRequest(int seatIndex, int clientId, JSONObject msg);
        /** 处理远程玩家不出请求（房主端） */
        void handleRemotePass(int seatIndex, int clientId, JSONObject msg);
        /** 处理远程玩家叫地主响应（房主端） */
        void handleRemoteBidResponse(int seatIndex, int clientId, JSONObject msg);
        /** 处理状态确认消息（房主端） */
        void handleStateAck(int seatIndex, JSONObject msg);
        /** 广播聊天消息（房主端） */
        void broadcastChat(int seatIndex, String message);
        /** 处理座位分配消息（客户端） */
        void handleSeatAssigned(JSONObject msg);
        /** 处理座位更新消息（客户端） */
        void handleSeatUpdate(JSONObject msg);
        /** 处理手牌分发消息（客户端） */
        void handleHandCards(JSONObject msg);
        /** 处理叫地主请求消息（客户端） */
        void handleBidRequest(JSONObject msg);
        /** 处理游戏结束消息（客户端） */
        void handleGameOverMsg(JSONObject msg);
        /** 处理状态同步消息（客户端） */
        void handleSyncState(JSONObject msg);
        /** 处理确认应答消息（客户端） */
        void handleAck(JSONObject msg);
        /** 处理聊天历史消息（客户端） */
        void handleChatHistory(JSONObject msg);
        /** 处理聊天消息（客户端） */
        void handleChatMessage(JSONObject msg);
    }

    /**
     * 构造网络消息处理器。
     *
     * @param handler 主线程 Handler，用于延迟调度
     * @param delegate 网络操作委托接口的实现
     */
    public DouDiZhuNetworkHandler(Handler handler, NetworkDelegate delegate) {
        this.handler = handler;
        this.delegate = delegate;
    }

    /**
     * 处理房主端收到的客户端消息。
     *
     * <p>消息分发逻辑：
     * <ol>
     *   <li>JOIN 类型消息直接委托给 handleClientJoin</li>
     *   <li>其他消息根据 clientId 查找对应座位索引</li>
     *   <li>若找不到座位，尝试从消息中声明的 seatIndex 恢复</li>
     *   <li>对客户端动作类消息进行去重校验</li>
     *   <li>根据消息类型分发到对应的处理方法</li>
     * </ol>
     * </p>
     *
     * @param clientId 发送消息的客户端 ID
     * @param msg 收到的 JSON 消息
     */
    public void onServerMessageReceived(int clientId, JSONObject msg) {
        try {
            String type = msg.getString("type");
            // JOIN 消息特殊处理：客户端首次加入，尚未分配座位
            if (DouDiZhuProtocol.TYPE_JOIN.equals(type)) {
                delegate.handleClientJoin(clientId, msg);
                return;
            }
            // 根据 clientId 查找已分配的座位索引
            int seatIndex = -1;
            int[] seatClientIds = delegate.getSeatClientIds();
            for (int i = 0; i < TOTAL_SEATS; i++) {
                if (seatClientIds[i] == clientId) {
                    seatIndex = i;
                    break;
                }
            }
            if (seatIndex == -1) {
                // 未找到座位，尝试从消息中声明的 seatIndex 恢复（断线重连场景）
                int declaredSeat = msg.optInt("seatIndex", -1);
                int[] seatTypes = delegate.getSeatTypes();
                if (declaredSeat > 0 && declaredSeat < TOTAL_SEATS
                        && seatTypes[declaredSeat] == DouDiZhuSeatManager.SEAT_TYPE_REMOTE) {
                    seatIndex = declaredSeat;
                    delegate.setSeatClientId(seatIndex, clientId);
                } else {
                    // 无法识别的客户端，丢弃消息
                    return;
                }
            }
            // 对客户端动作类消息进行去重校验
            if (isClientAction(type) && !shouldProcessClientAction(seatIndex, clientId, msg, type)) {
                return;
            }
            switch (type) {
                case DouDiZhuProtocol.TYPE_REQUEST_PLAY:
                    delegate.handleRemotePlayRequest(seatIndex, clientId, msg);
                    break;
                case DouDiZhuProtocol.TYPE_PASS:
                    delegate.handleRemotePass(seatIndex, clientId, msg);
                    break;
                case DouDiZhuProtocol.TYPE_BID_RESPONSE:
                    delegate.handleRemoteBidResponse(seatIndex, clientId, msg);
                    break;
                case DouDiZhuProtocol.TYPE_STATE_ACK:
                    delegate.handleStateAck(seatIndex, msg);
                    break;
                case DouDiZhuProtocol.TYPE_CHAT:
                    String chatMsg = msg.optString("message", "");
                    delegate.broadcastChat(seatIndex, chatMsg);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing server message: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error handling server message", e);
        }
    }

    /**
     * 处理客户端收到的房主消息。
     *
     * <p>根据消息类型分发到对应的处理方法，所有处理均在主线程执行。
     * 消息类型包括：座位分配、座位更新、手牌分发、叫地主请求、
     * 游戏结束、状态同步、确认应答、聊天历史、聊天消息、错误消息。</p>
     *
     * @param msg 收到的 JSON 消息
     */
    public void onClientMessageReceived(JSONObject msg) {
        if (msg == null) return;
        String type = msg.optString("type", "");
        handler.post(() -> {
            if (delegate.isFinishing() || delegate.isDestroyed()) return;
            try {
                switch (type) {
                    case DouDiZhuProtocol.TYPE_SEAT_ASSIGNED:
                        delegate.handleSeatAssigned(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_SEAT_UPDATE:
                        delegate.handleSeatUpdate(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_HAND_CARDS:
                        delegate.handleHandCards(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_BID_REQUEST:
                        delegate.handleBidRequest(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_GAME_OVER:
                        delegate.handleGameOverMsg(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_SYNC_STATE:
                        delegate.handleSyncState(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_ACK:
                        delegate.handleAck(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_CHAT_HISTORY:
                        delegate.handleChatHistory(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_CHAT:
                        delegate.handleChatMessage(msg);
                        break;
                    case DouDiZhuProtocol.TYPE_ERROR:
                        String errorMsg = msg.optString("message", "未知错误");
                        delegate.showToast(errorMsg);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling client message: " + e.getMessage());
            }
        });
    }

    /**
     * 发送客户端意图（出牌/不出/叫地主等操作）。
     *
     * <p>发送流程：
     * <ol>
     *   <li>为消息添加 actionId、客户端状态版本、发送时间戳等元数据</li>
     *   <li>如果当前未连接，将意图缓存到 pendingClientIntent 并触发重连</li>
     *   <li>如果发送成功，调度两次重试（220ms 和 700ms 后）以提高可靠性</li>
     *   <li>如果发送失败，同样缓存意图等待重连</li>
     * </ol>
     * </p>
     *
     * @param msg 要发送的 JSON 消息
     * @return true 表示消息已发送（不保证对方收到），false 表示未连接无法发送
     */
    public boolean sendClientIntent(JSONObject msg) {
        GameSocketClient client = delegate.getClient();
        if (client == null) {
            delegate.showToast("未连接主机");
            delegate.updateConnectionStatus("未连接主机", 0xFFFF9800);
            return false;
        }
        // 为消息添加元数据：actionId、客户端状态版本、发送时间
        decorateClientIntent(msg);
        if (!client.isConnected()) {
            // 未连接时缓存意图，重连后自动补发
            queueClientIntentForReconnect(msg);
            return true;
        }
        boolean sent = client.send(msg);
        if (!sent) {
            // 发送失败，缓存意图等待重连
            queueClientIntentForReconnect(msg);
        } else {
            // 发送成功，调度重试以提高弱网环境下的可靠性
            scheduleClientIntentRepeats(msg);
        }
        return sent;
    }

    /**
     * 为客户端意图消息添加元数据。
     *
     * <p>添加的字段包括：
     * <ul>
     *   <li>actionId：唯一动作 ID，用于房主端去重</li>
     *   <li>clientStateVersion：客户端当前的状态版本，用于房主端判断消息是否过期</li>
     *   <li>sentAt：发送时间戳，用于调试和超时判断</li>
     * </ul>
     * </p>
     *
     * @param msg 要装饰的 JSON 消息
     */
    private void decorateClientIntent(JSONObject msg) {
        if (msg == null) return;
        try {
            if (!msg.has("actionId")) {
                msg.put("actionId", delegate.getSyncManager().getNextClientActionId());
            }
            msg.put("clientStateVersion", delegate.getSyncManager().getClientLastStateVersion());
            msg.put("sentAt", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "decorateClientIntent error", e);
        }
    }

    /**
     * 调度客户端意图的两次重试。
     *
     * <p>仅对关键操作（叫地主、出牌、不出）进行重试。
     * 在 220ms 和 700ms 后各重发一次消息，提高弱网环境下的可靠性。
     * 重试前会检查游戏状态和当前回合，避免发送过期的操作。</p>
     *
     * @param msg 原始发送的消息
     */
    private void scheduleClientIntentRepeats(JSONObject msg) {
        String type = msg.optString("type", "");
        // 仅对关键操作进行重试，聊天等消息不需要
        if (!DouDiZhuProtocol.TYPE_BID_RESPONSE.equals(type)
                && !DouDiZhuProtocol.TYPE_REQUEST_PLAY.equals(type)
                && !DouDiZhuProtocol.TYPE_PASS.equals(type)) {
            return;
        }
        JSONObject firstRetry;
        JSONObject secondRetry;
        try {
            firstRetry = new JSONObject(msg.toString());
            secondRetry = new JSONObject(msg.toString());
        } catch (JSONException e) {
            return;
        }
        // 两次重试间隔：220ms 和 700ms
        handler.postDelayed(() -> resendClientIntent(firstRetry), 220);
        handler.postDelayed(() -> resendClientIntent(secondRetry), 700);
    }

    /**
     * 重发客户端意图。
     *
     * <p>在重发前检查游戏状态和当前回合，确保只在操作仍然有效时才重发，
     * 避免发送过期的操作（如回合已切换后仍重发上一轮的操作）。</p>
     *
     * @param msg 要重发的 JSON 消息
     */
    private void resendClientIntent(JSONObject msg) {
        GameSocketClient client = delegate.getClient();
        if (client == null || !client.isConnected()) return;
        String type = msg.optString("type", "");
        int gameState = delegate.getGameState();
        int currentTurn = delegate.getCurrentTurn();
        int mySeatIndex = delegate.getMySeatIndex();
        // 仅在操作仍然有效时才重发
        if (DouDiZhuProtocol.TYPE_BID_RESPONSE.equals(type) && gameState == STATE_BIDDING && currentTurn == mySeatIndex) {
            client.send(msg);
        } else if ((DouDiZhuProtocol.TYPE_REQUEST_PLAY.equals(type) || DouDiZhuProtocol.TYPE_PASS.equals(type))
                && gameState == STATE_PLAYING && currentTurn == mySeatIndex) {
            client.send(msg);
        }
    }

    /**
     * 将客户端意图缓存，等待重连后补发。
     *
     * <p>当客户端与房主断开连接时，将当前操作意图缓存到 pendingClientIntent，
     * 并触发重连。重连成功后，{@link #flushPendingClientIntentIfReady} 会自动补发。</p>
     *
     * @param msg 要缓存的 JSON 消息
     */
    private void queueClientIntentForReconnect(JSONObject msg) {
        try {
            pendingClientIntent = new JSONObject(msg.toString());
        } catch (JSONException e) {
            pendingClientIntent = msg;
        }
        delegate.showToast("连接恢复后自动发送");
        delegate.updateConnectionStatus("正在重连，恢复后自动发送", 0xFFFF9800);
        delegate.hideAllButtons();
        GameSocketClient client = delegate.getClient();
        if (client != null) {
            client.reconnectNow();
        }
    }

    /**
     * 在条件满足时补发缓存的客户端意图。
     *
     * <p>当客户端重连成功且收到状态同步后调用此方法。检查缓存意图的类型
     * 是否与当前游戏状态和回合匹配，匹配则补发，否则丢弃。</p>
     *
     * <p>补发时会更新消息中的 seatIndex 和 currentTurn，
     * 因为重连后这些值可能已变化。</p>
     */
    public void flushPendingClientIntentIfReady() {
        if (pendingClientIntent == null) return;
        GameSocketClient client = delegate.getClient();
        if (client == null || !client.isConnected()) return;
        String type = pendingClientIntent.optString("type", "");
        int gameState = delegate.getGameState();
        int currentTurn = delegate.getCurrentTurn();
        int mySeatIndex = delegate.getMySeatIndex();
        // 校验缓存意图是否与当前游戏状态匹配
        if (DouDiZhuProtocol.TYPE_BID_RESPONSE.equals(type)) {
            if (gameState != STATE_BIDDING || currentTurn != mySeatIndex) return;
        } else if (DouDiZhuProtocol.TYPE_REQUEST_PLAY.equals(type) || DouDiZhuProtocol.TYPE_PASS.equals(type)) {
            if (gameState != STATE_PLAYING || currentTurn != mySeatIndex) return;
        }
        try {
            // 更新座位和回合信息，因为重连后可能已变化
            pendingClientIntent.put("seatIndex", mySeatIndex);
            pendingClientIntent.put("currentTurn", currentTurn);
        } catch (JSONException e) {
            Log.e(TAG, "flush pending intent failed to update seat", e);
        }
        boolean sent = client.send(pendingClientIntent);
        if (sent) {
            pendingClientIntent = null;
            delegate.updateConnectionStatus("重连后已发送", 0xFF4CAF50);
            delegate.hideAllButtons();
        } else if (client != null) {
            client.reconnectNow();
        }
    }

    /**
     * 发送状态确认消息（STATE_ACK）。
     *
     * <p>客户端收到 SYNC_STATE 后，向房主发送确认，表示已成功应用该状态。
     * 房主可据此判断客户端是否已同步到最新状态。</p>
     *
     * @param stateVersion 所确认的状态版本号
     */
    public void sendStateAck(long stateVersion) {
        int mode = delegate.getMode();
        GameSocketClient client = delegate.getClient();
        if (mode != 1 || client == null || !client.isConnected() || stateVersion < 0L) return;
        JSONObject ack = new JSONObject();
        try {
            ack.put("type", DouDiZhuProtocol.TYPE_STATE_ACK);
            ack.put("seatIndex", delegate.getMySeatIndex());
            ack.put("stateVersion", stateVersion);
            ack.put("time", System.currentTimeMillis());
            client.send(ack);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 判断消息类型是否为客户端动作（需要去重校验）。
     *
     * @param type 消息类型字符串
     * @return true 表示是客户端动作类消息
     */
    private boolean isClientAction(String type) {
        return DouDiZhuProtocol.TYPE_BID_RESPONSE.equals(type)
                || DouDiZhuProtocol.TYPE_REQUEST_PLAY.equals(type)
                || DouDiZhuProtocol.TYPE_PASS.equals(type);
    }

    /**
     * 判断是否应该处理客户端动作（去重校验）。
     *
     * <p>通过 SyncManager 的 actionId 去重机制，防止同一操作被重复处理。
     * 如果 actionId 小于等于该座位已处理的最新 actionId，则为重复消息，跳过处理。</p>
     *
     * @param seatIndex 客户端所在座位索引
     * @param clientId 客户端 ID
     * @param msg 原始消息
     * @param type 消息类型
     * @return true 表示应该处理，false 表示应跳过（重复或无效）
     */
    private boolean shouldProcessClientAction(int seatIndex, int clientId, JSONObject msg, String type) {
        delegate.syncLocalToManager();
        boolean result = delegate.getSyncManager().shouldProcessClientAction(seatIndex, clientId, msg, type);
        delegate.syncManagerToLocal();
        return result;
    }
}
