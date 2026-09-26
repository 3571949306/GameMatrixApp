package com.gamecenter.app.chinesechess;

/**
 * 局域网双机对战会话状态机（纯 Java，可用内存管道双端互联单测，不依赖 Android）。
 *
 * <p>角色：HOST 执红先行、GUEST 执黑后行；GUEST 连接后发送 HELLO，
 * HOST 分配阵营并回复 WELCOME，双方进入 PLAYING。着法走
 * {@link LanChessProtocol} 行协议经注入的 {@link Transport} 双向同步。</p>
 *
 * <p>安全边界：来自网络的 MOVE 按不可信输入处理——先经协议 decode、
 * 轮次校验（轮到对方才能动）、ply 序号校验，再经注入的 {@link MoveValidator}
 * 规则校验，全部通过才回调 UI 应用着法；任何一步失败即断开对局。
 * 真实落子仍由 UI 层经 ChineseChessGame.commitMove 集中闸门完成（会话不落子）。</p>
 *
 * <p>线程约定：Transport 的 IO 必须在后台线程（Socket 读线程由 Transport 适配器自建）；
 * 本类的全部回调（{@code TransportListener}/{@code SessionListener}）都在
 * Transport 读线程上触发，<b>UI 层必须自行切主线程</b>后再操作视图与对局渲染。
 * 会话自身的状态字段仅在回调串行路径上变更，无需额外加锁。</p>
 */
public class LanChessSession {

    /** 角色：HOST 建房（执红先行），GUEST 加入（执黑）。 */
    public enum Role {
        HOST, GUEST
    }

    /** 会话状态：握手等待 → 对局中 → 终态（断线/对方离开）。 */
    public enum State {
        WAIT_HELLO, PLAYING, DISCONNECTED, OPPONENT_LEFT
    }

    /** 传输层抽象：Socket 适配器与单测内存管道的共同接口。 */
    public interface Transport {
        /** 发送一行（不含换行符）；传输已关闭时抛 IllegalStateException。 */
        void send(String line);

        /** 设置读监听并启动读取（监听回调在读线程触发）。 */
        void setListener(TransportListener listener);

        /** 关闭传输（幂等；触发一次 onClosed）。 */
        void close();
    }

    /** 传输层读监听：由 Transport 适配器在读线程调用。 */
    public interface TransportListener {
        void onLine(String line);

        /** 读失败 / 对端关闭 / 本端主动关闭，reason 用于诊断与展示。 */
        void onClosed(String reason);
    }

    /** 对方着法规则校验回调（注入 ChineseChessGame::isMoveLegal）。 */
    public interface MoveValidator {
        boolean isLegalMove(int fromX, int fromY, int toX, int toY);
    }

    /** 会话事件回调（读线程触发，UI 层切主线程消费）。 */
    public interface SessionListener {
        /** 握手完成：iAmRed 标识本方是否执红，opponentName 为对方展示名。 */
        void onReady(boolean iAmRed, String opponentName);

        /** 对方着法已通过协议/轮次/规则校验，UI 可安全应用到本方对局。 */
        void onOpponentMove(int fromX, int fromY, int toX, int toY);

        void onOpponentResigned();

        void onOpponentLeft();

        /** 断线（读失败/被动关闭/协议违规/对方非法着法），对局终止。 */
        void onDisconnected(String reason);
    }

    /** 对方未提供名字时的兜底展示名。 */
    private static final String DEFAULT_OPPONENT_NAME = "对手";

    // 断线原因（会话层产生，UI 直接展示；传输层原因由适配器透传）
    public static final String REASON_ILLEGAL_MOVE = "对方着法非法";
    public static final String REASON_OUT_OF_TURN = "对方越序走子";
    public static final String REASON_PLY_OUT_OF_SYNC = "着法序号失步";
    public static final String REASON_SEND_FAILED = "发送失败";
    public static final String REASON_CONNECTION_CLOSED = "连接已断开";

    private final Role role;
    private final Transport transport;
    private final String myName;
    private final MoveValidator validator;
    private final SessionListener listener;

    private State state = State.WAIT_HELLO;
    /** 对局已终止（认输后抑制后续 MOVE/重复终局回调），与 state 独立。 */
    private boolean matchOver;
    private boolean iAmRed;
    private boolean myTurn;
    /** 双方共识的已走步数（=已成功的 MOVE 数），ply 序号从 1 递增。 */
    private int plyCount;

    public LanChessSession(Role role, Transport transport, String myName,
                           MoveValidator validator, SessionListener listener) {
        this.role = role;
        this.transport = transport;
        this.myName = myName == null ? "" : myName;
        this.validator = validator;
        this.listener = listener == null ? new SessionListener() {
            @Override public void onReady(boolean red, String name) { }
            @Override public void onOpponentMove(int fx, int fy, int tx, int ty) { }
            @Override public void onOpponentResigned() { }
            @Override public void onOpponentLeft() { }
            @Override public void onDisconnected(String reason) { }
        } : listener;
        // HOST 预定执红先行，待收到 HELLO 后再确认进入 PLAYING。
        this.iAmRed = (role == Role.HOST);
        this.myTurn = (role == Role.HOST);
        transport.setListener(new TransportListener() {
            @Override
            public void onLine(String line) {
                handleLine(line);
            }

            @Override
            public void onClosed(String reason) {
                handleTransportClosed(reason);
            }
        });
    }

    /** 会话启动：GUEST 主动发送 HELLO，HOST 等待对端握手。须在进入事件循环前调用一次。 */
    public void start() {
        if (role == Role.GUEST && state == State.WAIT_HELLO) {
            sendLine(LanChessProtocol.encodeHello(myName));
        }
    }

    public State getState() {
        return state;
    }

    /** 是否轮到本方走棋（UI 据此锁定/解锁棋盘）。 */
    public boolean isMyTurn() {
        return state == State.PLAYING && !matchOver && myTurn;
    }

    /**
     * 发送本方着子：校验轮次 → 编码 → transport.send → 返回 true 即本地确认
     * （UI 收到 true 才应把该着法应用到本方对局）。轮次不符/对局已结束返回 false。
     */
    public boolean sendMove(int fromX, int fromY, int toX, int toY) {
        if (state != State.PLAYING || matchOver || !myTurn) return false;
        String line = LanChessProtocol.encodeMove(fromX, fromY, toX, toY, plyCount + 1);
        if (!sendLine(line)) return false;
        plyCount++;
        myTurn = false;
        return true;
    }

    /** 本方认输：发送 RESIGN 并终止本方后续走子。 */
    public boolean sendResign() {
        if (state != State.PLAYING || matchOver) return false;
        matchOver = true;
        return sendLine(LanChessProtocol.encodeResign());
    }

    /** 本方离开：尽力发送 LEAVE 通知对方，随即进入终态（不回调 onDisconnected）。 */
    public void sendLeave() {
        if (state == State.DISCONNECTED || state == State.OPPONENT_LEFT) return;
        // 先置终态再发送：对端收到 LEAVE 会立即反向关闭连接（重入路径），
        // 终态前置可保证发起方不被该重入触发多余的 onDisconnected。
        state = State.DISCONNECTED;
        matchOver = true;
        sendLine(LanChessProtocol.encodeLeave());
        transport.close();
    }

    /** 主动关闭会话（退出页面等）：静默终态，不触发 onDisconnected。幂等。 */
    public void close() {
        if (state == State.DISCONNECTED) {
            transport.close();
            return;
        }
        state = State.DISCONNECTED;
        matchOver = true;
        transport.close();
    }

    /** 保活探测（可选）：PONG 由对端会话自动回复。 */
    public boolean sendPing() {
        if (state == State.DISCONNECTED || state == State.OPPONENT_LEFT) return false;
        return sendLine(LanChessProtocol.encodePing());
    }

    // ==================== 内部：收线处理 ====================

    private void handleLine(String raw) {
        LanChessProtocol.Message msg = LanChessProtocol.decode(raw);
        if (msg == null) {
            // 畸形/未知消息一律容错忽略（不炸读线程）；连续异常最终会因断线收口。
            return;
        }
        switch (msg.type()) {
            case LanChessProtocol.TYPE_HELLO:
                handleHello(msg);
                break;
            case LanChessProtocol.TYPE_WELCOME:
                handleWelcome(msg);
                break;
            case LanChessProtocol.TYPE_MOVE:
                handleMove(msg);
                break;
            case LanChessProtocol.TYPE_RESIGN:
                handleResign();
                break;
            case LanChessProtocol.TYPE_LEAVE:
                handleLeave();
                break;
            case LanChessProtocol.TYPE_PING:
                sendLine(LanChessProtocol.encodePong());
                break;
            case LanChessProtocol.TYPE_PONG:
                break; // 保活回声，无需处理
            default:
                break; // decode 已过滤未知类型，防御兜底
        }
    }

    /** HOST 收到 HELLO：分配阵营（HOST 恒执红），回 WELCOME，双方进入对局。 */
    private void handleHello(LanChessProtocol.Message msg) {
        if (role != Role.HOST || state != State.WAIT_HELLO) return;
        String guestName = msg.getString("name", "");
        state = State.PLAYING;
        iAmRed = true;
        myTurn = true;
        sendLine(LanChessProtocol.encodeWelcome(LanChessProtocol.COLOR_BLACK, myName));
        listener.onReady(true, guestName == null || guestName.isEmpty()
                ? DEFAULT_OPPONENT_NAME : guestName);
    }

    /** GUEST 收到 WELCOME：按 HOST 分配确定阵营与先手。 */
    private void handleWelcome(LanChessProtocol.Message msg) {
        if (role != Role.GUEST || state != State.WAIT_HELLO) return;
        state = State.PLAYING;
        iAmRed = LanChessProtocol.COLOR_RED.equals(msg.getString("color", ""));
        myTurn = iAmRed; // 红先行
        String hostName = msg.getString("hostName", "");
        listener.onReady(iAmRed, hostName == null || hostName.isEmpty()
                ? DEFAULT_OPPONENT_NAME : hostName);
    }

    /** 收到对方 MOVE：协议/轮次/ply/规则四重校验后回调 UI。 */
    private void handleMove(LanChessProtocol.Message msg) {
        if (state != State.PLAYING || matchOver) return;
        if (myTurn) {
            // 轮到本方时对方无权走子：协议违规，断开对局。
            terminate(REASON_OUT_OF_TURN);
            return;
        }
        int expectedPly = plyCount + 1;
        if (msg.getInt("ply", Integer.MIN_VALUE) != expectedPly) {
            // 着法序号失步（丢行/重放/双发），继续下去只会污染棋盘，直接断开。
            terminate(REASON_PLY_OUT_OF_SYNC);
            return;
        }
        int fromX = msg.getInt("fromX", Integer.MIN_VALUE);
        int fromY = msg.getInt("fromY", Integer.MIN_VALUE);
        int toX = msg.getInt("toX", Integer.MIN_VALUE);
        int toY = msg.getInt("toY", Integer.MIN_VALUE);
        if (validator == null || !validator.isLegalMove(fromX, fromY, toX, toY)) {
            terminate(REASON_ILLEGAL_MOVE);
            return;
        }
        plyCount++;
        myTurn = true;
        listener.onOpponentMove(fromX, fromY, toX, toY);
    }

    private void handleResign() {
        if (state != State.PLAYING || matchOver) return;
        matchOver = true;
        listener.onOpponentResigned();
    }

    private void handleLeave() {
        if (state == State.OPPONENT_LEFT || state == State.DISCONNECTED) return;
        state = State.OPPONENT_LEFT;
        matchOver = true;
        transport.close();
        listener.onOpponentLeft();
    }

    /** 读线程被动断开（读失败/对端关闭）。 */
    private void handleTransportClosed(String reason) {
        if (state == State.OPPONENT_LEFT || state == State.DISCONNECTED) return;
        state = State.DISCONNECTED;
        matchOver = true;
        listener.onDisconnected(reason == null || reason.isEmpty()
                ? REASON_CONNECTION_CLOSED : reason);
    }

    private void terminate(String reason) {
        state = State.DISCONNECTED;
        matchOver = true;
        transport.close();
        listener.onDisconnected(reason);
    }

    /** 编码发送一行；传输异常按断线收口，返回 false。 */
    private boolean sendLine(String line) {
        try {
            transport.send(line);
            return true;
        } catch (RuntimeException e) {
            handleTransportClosed(REASON_SEND_FAILED);
            return false;
        }
    }
}
