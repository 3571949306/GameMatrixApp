package com.gamecenter.app.games.doudizhu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.gamecenter.app.R;
import com.gamecenter.app.games.doudizhu.model.Card;

import java.util.List;

/**
 * 斗地主 UI 控制器。
 *
 * <p>负责管理斗地主在线模式的所有 UI 组件，包括视图初始化、事件监听绑定、
 * 界面状态切换（大厅/叫地主/出牌/游戏结束）、牌桌更新、聊天面板、对话框等。
 * 通过 {@link GameActionCallback} 回调接口将用户操作转发给 Activity 处理，
 * 实现了 UI 逻辑与业务逻辑的分离。</p>
 *
 * <p>你可以把UIController想象成一个"遥控器"——它只负责显示画面和接收按钮点击，
 * 真正的游戏操作都交给Activity（"主机"）去处理。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用回调模式（{@link GameActionCallback}），UI 控制器不直接操作游戏状态
 *       （遥控器不直接换台，而是发信号给电视机）</li>
 *   <li>所有 UI 组件通过 findViewById 获取，由 {@link #initViews()} 统一初始化</li>
 *   <li>界面状态切换通过显示/隐藏容器布局实现，而非替换 Activity
 *       （就像翻卡片，正面是大厅，翻过来是游戏界面）</li>
 *   <li>房间码输入框自动过滤非字母数字字符并转大写，提升用户体验</li>
 * </ul>
 */
public final class DouDiZhuUIController {

    private static final String TAG = "DouDiZhuUIController";

    /**
     * 游戏操作回调接口。
     *
     * <p>由 Activity 实现，将 UI 操作转发到业务逻辑层。
     * 这个接口就像"遥控器的按键"——按下后，信号传给电视（Activity）去处理。</p>
     */
    public interface GameActionCallback {
        /** 创建房间 */
        void onCreateRoom();
        /** 加入房间 */
        void onJoinRoom();
        /** 复制房间地址/房间码 */
        void onCopyRoomAddress();
        /** 开始游戏 */
        void onStartGame();
        /** 断开连接 */
        void onDisconnect();
        /** 叫地主 */
        void onCallLandlord();
        /** 不叫地主 */
        void onNoCall();
        /** 出牌 */
        void onPlayCard();
        /** 提示 */
        void onHint();
        /** 不出 */
        void onPass();
        /** 再来一局 */
        void onPlayAgain();
        /** 退出 */
        void onExit();
        /** 发送聊天消息 */
        void onSendChat(String message);
        /** 手动输入 IP 加入 */
        void onManualJoin(String ip, int port);
        /** 通过房间码加入云联机 */
        void onRemoteJoin(String roomCode);
    }

    /** 自定义牌桌视图 */
    private DouDiZhuTableView tableView;
    /** 大厅容器布局 */
    private LinearLayout lobbyContainer;
    /** 服务器信息文本 */
    private TextView tvServerInfo;
    /** 房间列表文本 */
    private TextView tvRoomList;
    /** 连接状态文本 */
    private TextView tvConnectionStatus;
    /** 创建房间按钮 */
    private Button btnCreateRoom;
    /** 加入房间按钮 */
    private Button btnJoinRoom;
    /** 复制房间地址按钮 */
    private Button btnCopyRoomAddress;
    /** 开始游戏按钮 */
    private Button btnStartGame;
    /** 断开连接按钮 */
    private Button btnDisconnect;
    /** 操作按钮容器 */
    private LinearLayout buttonContainer;
    /** 叫地主按钮布局 */
    private LinearLayout bidButtonLayout;
    /** 出牌按钮布局 */
    private LinearLayout playButtonLayout;
    /** 叫地主按钮 */
    private Button btnCallLandlord;
    /** 不叫按钮 */
    private Button btnNoCall;
    /** 出牌按钮 */
    private Button btnPlayCard;
    /** 提示按钮 */
    private Button btnHint;
    /** 不出按钮 */
    private Button btnPass;
    /** 加载进度条 */
    private ProgressBar progressLoading;
    /** 游戏结束对话框布局 */
    private LinearLayout gameOverDialog;
    /** 游戏结束标题 */
    private TextView tvGameOverTitle;
    /** 游戏结束结果 */
    private TextView tvGameOverResult;
    /** 得分详情 */
    private TextView tvScoreDetail;
    /** 再来一局按钮 */
    private Button btnPlayAgain;
    /** 退出按钮 */
    private Button btnExit;
    /** 聊天容器布局 */
    private LinearLayout chatContainer;
    /** 聊天滚动视图 */
    private ScrollView chatScrollView;
    /** 聊天消息文本 */
    private TextView tvChatMessages;
    /** 聊天输入框 */
    private EditText etChatInput;
    /** 发送聊天按钮 */
    private Button btnSendChat;
    /** 顶部状态栏 */
    private LinearLayout topStatusBar;
    /** 地主指示器 */
    private TextView tvLandlordIndicator;
    /** 回合指示器 */
    private TextView tvTurnIndicator;

    /** 关联的 Activity 实例（UI操作需要Activity上下文） */
    private final Activity activity;
    /** 游戏操作回调（按钮点击后通知Activity） */
    private GameActionCallback callback;
    /** 聊天日志缓冲区（累积所有聊天消息） */
    private final StringBuilder chatLog = new StringBuilder();

    /**
     * 构造 UI 控制器。
     *
     * @param activity 关联的 Activity 实例
     */
    public DouDiZhuUIController(Activity activity) {
        this.activity = activity;
    }

    /**
     * 设置游戏操作回调。
     *
     * @param callback 回调接口实现
     */
    public void setCallback(GameActionCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化所有 UI 视图引用。
     *
     * <p>通过 findViewById 获取布局中所有控件的引用，
     * 必须在 setContentView 之后调用。</p>
     */
    public void initViews() {
        tableView = activity.findViewById(R.id.tableView);
        lobbyContainer = activity.findViewById(R.id.lobbyContainer);
        tvServerInfo = activity.findViewById(R.id.tvServerInfo);
        tvRoomList = activity.findViewById(R.id.tvRoomList);
        tvConnectionStatus = activity.findViewById(R.id.tvConnectionStatus);
        btnCreateRoom = activity.findViewById(R.id.btnCreateRoom);
        btnJoinRoom = activity.findViewById(R.id.btnJoinRoom);
        btnCopyRoomAddress = activity.findViewById(R.id.btnCopyRoomAddress);
        btnStartGame = activity.findViewById(R.id.btnStartGame);
        btnDisconnect = activity.findViewById(R.id.btnDisconnect);
        buttonContainer = activity.findViewById(R.id.buttonContainer);
        bidButtonLayout = activity.findViewById(R.id.bidButtonLayout);
        playButtonLayout = activity.findViewById(R.id.playButtonLayout);
        btnCallLandlord = activity.findViewById(R.id.btnCallLandlord);
        btnNoCall = activity.findViewById(R.id.btnNoCall);
        btnPlayCard = activity.findViewById(R.id.btnPlayCard);
        btnHint = activity.findViewById(R.id.btnHint);
        btnPass = activity.findViewById(R.id.btnPass);
        progressLoading = activity.findViewById(R.id.progressLoading);
        gameOverDialog = activity.findViewById(R.id.gameOverDialog);
        tvGameOverTitle = activity.findViewById(R.id.tvGameOverTitle);
        tvGameOverResult = activity.findViewById(R.id.tvGameOverResult);
        tvScoreDetail = activity.findViewById(R.id.tvScoreDetail);
        btnPlayAgain = activity.findViewById(R.id.btnPlayAgain);
        btnExit = activity.findViewById(R.id.btnExit);
        chatContainer = activity.findViewById(R.id.chatContainer);
        chatScrollView = activity.findViewById(R.id.chatScrollView);
        tvChatMessages = activity.findViewById(R.id.tvChatMessages);
        etChatInput = activity.findViewById(R.id.etChatInput);
        btnSendChat = activity.findViewById(R.id.btnSendChat);
        topStatusBar = activity.findViewById(R.id.topStatusBar);
        tvLandlordIndicator = activity.findViewById(R.id.tvLandlordIndicator);
        tvTurnIndicator = activity.findViewById(R.id.tvTurnIndicator);
    }

    /**
     * 初始化所有按钮的点击事件监听器。
     *
     * <p>将每个按钮的点击事件转发给 {@link GameActionCallback} 对应的方法。</p>
     *
     * @param remoteP2PMode 是否为远程 P2P 模式
     */
    public void initListeners(boolean remoteP2PMode) {
        if (btnCreateRoom != null) btnCreateRoom.setOnClickListener(v -> {
            if (callback != null) callback.onCreateRoom();
        });
        if (btnJoinRoom != null) btnJoinRoom.setOnClickListener(v -> {
            if (callback != null) callback.onJoinRoom();
        });
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setOnClickListener(v -> {
            if (callback != null) callback.onCopyRoomAddress();
        });
        if (btnStartGame != null) btnStartGame.setOnClickListener(v -> {
            if (callback != null) callback.onStartGame();
        });
        if (btnDisconnect != null) btnDisconnect.setOnClickListener(v -> {
            if (callback != null) callback.onDisconnect();
        });
        if (btnCallLandlord != null) btnCallLandlord.setOnClickListener(v -> {
            if (callback != null) callback.onCallLandlord();
        });
        if (btnNoCall != null) btnNoCall.setOnClickListener(v -> {
            if (callback != null) callback.onNoCall();
        });
        if (btnPlayCard != null) btnPlayCard.setOnClickListener(v -> {
            if (callback != null) callback.onPlayCard();
        });
        if (btnHint != null) btnHint.setOnClickListener(v -> {
            if (callback != null) callback.onHint();
        });
        if (btnPass != null) btnPass.setOnClickListener(v -> {
            if (callback != null) callback.onPass();
        });
        if (btnPlayAgain != null) btnPlayAgain.setOnClickListener(v -> {
            if (callback != null) callback.onPlayAgain();
        });
        if (btnExit != null) btnExit.setOnClickListener(v -> {
            if (callback != null) callback.onExit();
        });
        if (btnSendChat != null) btnSendChat.setOnClickListener(v -> {
            if (etChatInput != null && callback != null) {
                String msg = etChatInput.getText().toString().trim();
                if (!msg.isEmpty()) {
                    callback.onSendChat(msg);
                }
            }
        });
        if (tableView != null) tableView.setOnCardTouchListener(cards -> {});
    }

    /**
     * 显示大厅界面。
     *
     * <p>隐藏游戏相关的 UI 组件，显示大厅相关的按钮和提示文本。
     * 根据 remoteP2PMode 显示不同的文案（云联机/局域网）。</p>
     *
     * @param remoteP2PMode 是否为远程 P2P 模式
     */
    public void showLobby(boolean remoteP2PMode) {
        if (lobbyContainer != null) lobbyContainer.setVisibility(View.VISIBLE);
        if (topStatusBar != null) topStatusBar.setVisibility(View.GONE);
        if (buttonContainer != null) buttonContainer.setVisibility(View.GONE);
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.GONE);
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (btnCreateRoom != null) {
            btnCreateRoom.setVisibility(View.VISIBLE);
            btnCreateRoom.setText(remoteP2PMode ? "云开房" : "创建房间");
        }
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setVisibility(View.GONE);
        if (btnJoinRoom != null) {
            btnJoinRoom.setVisibility(View.VISIBLE);
            btnJoinRoom.setText(remoteP2PMode ? "输入房间码" : "加入房间");
        }
        if (btnStartGame != null) btnStartGame.setVisibility(View.GONE);
        if (tvServerInfo != null) tvServerInfo.setText(remoteP2PMode ? "斗地主云联机" : "选择操作创建或加入房间");
        if (tvRoomList != null) {
            tvRoomList.setText(remoteP2PMode
                    ? "房主点\u201C云开房\u201D生成 6 位房间码。\n\n其他玩家点\u201C输入房间码\u201D，输入或粘贴房间码即可加入。\n旧版 p2p://IP:端口 地址仍可作为高级直连入口。"
                    : "点击\"加入房间\"搜索局域网房间");
        }
    }

    /**
     * 显示叫地主操作按钮。
     *
     * <p>显示操作按钮容器和叫地主按钮布局，隐藏出牌按钮布局。</p>
     */
    public void showBidUI() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.VISIBLE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.VISIBLE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.GONE);
    }

    /**
     * 显示出牌操作按钮。
     *
     * <p>显示操作按钮容器和出牌按钮布局，隐藏叫地主按钮布局。</p>
     */
    public void showPlayUI() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.VISIBLE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.GONE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏所有操作按钮。
     *
     * <p>隐藏操作按钮容器及其内部的叫地主和出牌按钮布局。</p>
     */
    public void hideAllButtons() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.GONE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.GONE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.GONE);
    }

    /**
     * 显示游戏主界面框架。
     *
     * <p>隐藏大厅和加载进度，显示顶部状态栏和聊天面板。</p>
     */
    public void showGameChrome() {
        if (lobbyContainer != null) lobbyContainer.setVisibility(View.GONE);
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (topStatusBar != null) topStatusBar.setVisibility(View.VISIBLE);
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 更新连接状态文本。
     *
     * <p>根据是否为房主、是否已连接、是否为云联机模式显示不同的状态文案和颜色。</p>
     *
     * @param isHost 是否为房主
     * @param isConnected 是否已连接
     * @param remoteP2PMode 是否为远程 P2P 模式
     */
    public void updateConnectedStatusText(boolean isHost, boolean isConnected, boolean remoteP2PMode) {
        if (tvConnectionStatus == null) return;
        if (isHost) {
            tvConnectionStatus.setText(remoteP2PMode ? "云联机房主" : "主机");
            tvConnectionStatus.setTextColor(0xFF4CAF50);
        } else if (isConnected) {
            tvConnectionStatus.setText(remoteP2PMode ? "已连接云房间" : "已连接主机");
            tvConnectionStatus.setTextColor(0xFF4CAF50);
        }
    }

    /**
     * 启用或禁用玩家出牌控制按钮。
     *
     * <p>控制出牌、提示、不出按钮的可用状态和可见性。</p>
     *
     * @param enable true 启用并显示，false 禁用并隐藏
     */
    public void enablePlayerControls(boolean enable) {
        if (btnPlayCard != null) btnPlayCard.setEnabled(enable);
        if (btnHint != null) btnHint.setEnabled(enable);
        if (btnPass != null) btnPass.setEnabled(enable);
        if (playButtonLayout != null) {
            playButtonLayout.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 更新牌桌视图。
     *
     * <p>将所有游戏状态数据传递给自定义牌桌视图进行渲染，
     * 包括手牌、底牌、各座位已出的牌、手牌数、地主标识、玩家标签、
     * 不出状态和记牌器数据。</p>
     *
     * @param playerHand 玩家手牌
     * @param bottomCards 底牌
     * @param playerPlayed 玩家已出的牌
     * @param leftPlayed 左边座位已出的牌
     * @param rightPlayed 右边座位已出的牌
     * @param leftCardCount 左边座位剩余手牌数
     * @param rightCardCount 右边座位剩余手牌数
     * @param landlordStatuses 各座位的地主状态（0=未定，1=农民，2=地主）
     * @param playerLabels 各座位的显示名称
     * @param leftPassed 左边座位是否"不出"
     * @param rightPassed 右边座位是否"不出"
     * @param cardCounterCounts 记牌器数据
     */
    public void updateTableView(List<Card> playerHand, List<Card> bottomCards,
                                List<Card> playerPlayed, List<Card> leftPlayed, List<Card> rightPlayed,
                                int leftCardCount, int rightCardCount,
                                int[] landlordStatuses, String[] playerLabels,
                                boolean leftPassed, boolean rightPassed,
                                int[] cardCounterCounts) {
        if (tableView == null) return;
        tableView.setPlayerHandCards(playerHand);
        tableView.setBottomCards(bottomCards);
        tableView.setPlayerPlayedCards(playerPlayed);
        tableView.setLeftAIPlayedCards(leftPlayed);
        tableView.setRightAIPlayedCards(rightPlayed);
        tableView.setAICardCounts(leftCardCount, rightCardCount);
        tableView.setAllLandlordStatus(landlordStatuses);
        tableView.setPlayerLabels(playerLabels);
        tableView.setPassStates(leftPassed, rightPassed);
        tableView.setCardCounterCounts(cardCounterCounts);
    }

    /**
     * 更新地主指示器文本。
     *
     * @param landlordName 地主名称，"待定"表示尚未确定
     */
    public void updateLandlordIndicator(String landlordName) {
        if (tvLandlordIndicator == null) return;
        tvLandlordIndicator.setText("地主：" + landlordName);
    }

    /**
     * 更新回合指示器文本。
     *
     * @param turnText 回合提示文本，如"你出牌"或"人机叫地主"
     */
    public void updateTurnIndicator(String turnText) {
        if (tvTurnIndicator == null) return;
        tvTurnIndicator.setText("轮到：" + turnText);
    }

    /**
     * 显示游戏结束对话框。
     *
     * @param title 对话框标题
     * @param result 游戏结果文本（如"你赢了！"）
     * @param scoreDetail 得分详情，null 则不显示
     */
    public void showGameOverDialog(String title, String result, String scoreDetail) {
        if (tvGameOverTitle != null) tvGameOverTitle.setText(title);
        if (tvGameOverResult != null) tvGameOverResult.setText(result);
        if (tvScoreDetail != null) tvScoreDetail.setText(scoreDetail != null ? scoreDetail : "");
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏游戏结束对话框。
     */
    public void hideGameOverDialog() {
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.GONE);
    }

    /**
     * 设置游戏结束结果文本。
     *
     * @param text 要显示的结果文本
     */
    public void setGameOverResultText(String text) {
        if (tvGameOverResult != null) tvGameOverResult.setText(text);
    }

    /**
     * 追加一条聊天消息到聊天面板。
     *
     * <p>将消息追加到聊天日志缓冲区，更新文本显示，并自动滚动到底部。</p>
     *
     * @param message 聊天消息文本
     */
    public void appendChat(String message) {
        chatLog.append(message).append("\n");
        if (tvChatMessages != null) tvChatMessages.setText(chatLog.toString());
        if (chatScrollView != null) chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * 设置服务器信息文本。
     *
     * @param text 服务器信息
     */
    public void setServerInfoText(String text) {
        if (tvServerInfo != null) tvServerInfo.setText(text);
    }

    /**
     * 设置房间列表文本。
     *
     * @param text 房间列表内容
     */
    public void setRoomListText(String text) {
        if (tvRoomList != null) tvRoomList.setText(text);
    }

    /**
     * 设置创建房间按钮的可见性。
     *
     * @param visibility View.VISIBLE / View.GONE / View.INVISIBLE
     */
    public void setCreateRoomButtonVisibility(int visibility) {
        if (btnCreateRoom != null) btnCreateRoom.setVisibility(visibility);
    }

    /**
     * 设置复制房间地址按钮的可见性。
     *
     * @param visibility View.VISIBLE / View.GONE / View.INVISIBLE
     */
    public void setCopyRoomButtonVisibility(int visibility) {
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setVisibility(visibility);
    }

    /**
     * 设置加入房间按钮的可见性。
     *
     * @param visibility View.VISIBLE / View.GONE / View.INVISIBLE
     */
    public void setJoinRoomButtonVisibility(int visibility) {
        if (btnJoinRoom != null) btnJoinRoom.setVisibility(visibility);
    }

    /**
     * 设置加入房间按钮的文本。
     *
     * @param text 按钮文本
     */
    public void setJoinRoomButtonText(String text) {
        if (btnJoinRoom != null) btnJoinRoom.setText(text);
    }

    /**
     * 设置加入房间按钮的点击监听器。
     *
     * @param listener 点击事件监听器
     */
    public void setJoinRoomButtonListener(View.OnClickListener listener) {
        if (btnJoinRoom != null) btnJoinRoom.setOnClickListener(listener);
    }

    /**
     * 设置开始游戏按钮的可见性。
     *
     * @param visibility View.VISIBLE / View.GONE / View.INVISIBLE
     */
    public void setStartGameButtonVisibility(int visibility) {
        if (btnStartGame != null) btnStartGame.setVisibility(visibility);
    }

    /**
     * 设置加载进度条的可见性。
     *
     * @param visibility View.VISIBLE / View.GONE / View.INVISIBLE
     */
    public void setProgressVisibility(int visibility) {
        if (progressLoading != null) progressLoading.setVisibility(visibility);
    }

    /**
     * 设置聊天输入框的文本。
     *
     * @param text 输入框文本
     */
    public void setChatInputText(String text) {
        if (etChatInput != null) etChatInput.setText(text);
    }

    /**
     * 获取聊天输入框的文本内容。
     *
     * @return 输入框文本，去除首尾空格
     */
    public String getChatInputText() {
        return etChatInput != null ? etChatInput.getText().toString().trim() : "";
    }

    /**
     * 清空聊天输入框。
     */
    public void clearChatInput() {
        if (etChatInput != null) etChatInput.setText("");
    }

    /**
     * 显示 Toast 提示。
     *
     * @param message 提示文本
     */
    public void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示 Toast 提示（字符串资源 ID）。
     *
     * @param stringResId 字符串资源 ID
     */
    public void showToast(int stringResId) {
        Toast.makeText(activity, stringResId, Toast.LENGTH_SHORT).show();
    }

    /**
     * 复制文本到系统剪贴板。
     *
     * @param label 剪贴板数据的标签
     * @param text 要复制的文本
     */
    public void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            showToast("已复制到剪贴板");
        }
    }

    /**
     * 从系统剪贴板获取文本。
     *
     * @return 剪贴板中的文本，获取失败返回空字符串
     */
    public String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    return item.getText().toString();
                }
            }
        } catch (Exception e) {
            // 忽略剪贴板访问异常
            Log.d(TAG, "剪贴板访问异常", e);
        }
        return "";
    }

    /**
     * 显示手动输入 IP 加入房间的对话框。
     *
     * <p>包含 IP 地址和端口两个输入框，IP 输入框预填本机 IP 前缀，
     * 端口默认 8765。</p>
     *
     * @param ipPrefix IP 地址前缀，用于预填
     * @param joinCallback 加入回调
     */
    public void showManualJoinDialog(String ipPrefix, ManualJoinCallback joinCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("手动输入IP加入");
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        final EditText ipInput = new EditText(activity);
        ipInput.setHint("IP地址");
        if (ipPrefix != null && !ipPrefix.isEmpty()) ipInput.setText(ipPrefix);
        layout.addView(ipInput);
        final EditText portInput = new EditText(activity);
        portInput.setHint("端口");
        portInput.setText("8765");
        layout.addView(portInput);
        builder.setView(layout);
        builder.setPositiveButton("加入", (dialog, which) -> {
            String ip = ipInput.getText().toString().trim();
            int port = 8765;
            try { port = Integer.parseInt(portInput.getText().toString().trim()); } catch (NumberFormatException ignored) { Log.w(TAG, "Invalid number format: " + ignored.getMessage()); }
            joinCallback.onJoin(ip, port);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示输入房间码加入云联机的对话框。
     *
     * <p>输入框自动过滤非字母数字字符并转为大写，支持从剪贴板自动填入 6 位房间码。</p>
     *
     * @param joinCallback 加入回调
     */
    public void showRemoteJoinDialog(RemoteJoinCallback joinCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("输入房间码");
        final EditText input = new EditText(activity);
        input.setHint("6位房间码");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(50, 40, 50, 10);
        input.setLayoutParams(lp);
        // 自动过滤非字母数字字符并转大写
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String text = s.toString().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (!text.equals(s.toString())) {
                    s.clear();
                    s.append(text);
                }
            }
        });
        // 尝试从剪贴板自动填入 6 位房间码
        String clipText = getClipboardText();
        if (clipText != null && clipText.matches("[A-Za-z0-9]{6}")) {
            input.setText(clipText.toUpperCase());
        }
        builder.setView(input);
        builder.setPositiveButton("加入", (dialog, which) -> {
            String code = input.getText().toString().trim().toUpperCase();
            joinCallback.onJoin(code);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示房间选择对话框。
     *
     * @param roomNames 房间名称数组
     * @param selectionCallback 选择回调
     */
    public void showRoomSelectionDialog(String[] roomNames, RoomSelectionCallback selectionCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("选择房间");
        builder.setItems(roomNames, (dialog, which) -> selectionCallback.onSelected(which));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 获取牌桌视图实例。
     *
     * @return 牌桌视图，可能为 null
     */
    public DouDiZhuTableView getTableView() {
        return tableView;
    }

    /** 手动输入 IP 加入的回调接口（用户填完IP和端口后通知Activity） */
    public interface ManualJoinCallback {
        /** 用户确认加入时回调 */
        void onJoin(String ip, int port);
    }

    /** 远程房间码加入的回调接口（用户输入房间码后通知Activity） */
    public interface RemoteJoinCallback {
        /** 用户确认加入时回调 */
        void onJoin(String roomCode);
    }

    /** 房间选择的回调接口（用户从列表中选择一个房间后通知Activity） */
    public interface RoomSelectionCallback {
        /** 用户选择某个房间时回调 */
        void onSelected(int index);
    }
}
