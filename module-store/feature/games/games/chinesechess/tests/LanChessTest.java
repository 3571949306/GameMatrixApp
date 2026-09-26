import com.gamecenter.app.chinesechess.ChineseChessGame;
import com.gamecenter.app.chinesechess.LanChessProtocol;
import com.gamecenter.app.chinesechess.LanChessSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 局域网双机对战（LOCAL-P2P）回归测试（纯 Java，javac 直接运行，无需 Android 运行时）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>协议编解码往返：HELLO/WELCOME/MOVE/RESIGN/LEAVE/PING/PONG encode→decode 字段一致；</li>
 *   <li>协议容错：非 JSON、未知类型、缺字段、字段类型错、数值溢出一律返回 null 不抛异常；</li>
 *   <li>会话状态机（内存管道双端互联）：握手 HELLO→WELCOME、双方交替 MOVE（host 红先行）、
 *       越序走子被拒、非法着法断开、ply 失步断开、GUEST 认输双端回调、LEAVE→对方离开、
 *       断线回调、畸形行容错、PING 自动回 PONG、关闭后发送被拒。</li>
 * </ul>
 *
 * <p>运行：python scripts/verify_chinese_chess.py（与 ChessRegressionTest 一同编译执行）。</p>
 */
public class LanChessTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    // ==================== 内存管道 fake 传输层 ====================

    /**
     * 内存双端管道：send 同步投递到对端 listener（单线程驱动，回调顺序确定），
     * close 模拟"关闭读端"——本端与对端各收到一次 onClosed。
     */
    static final class FakeTransport implements LanChessSession.Transport {
        FakeTransport peer;
        LanChessSession.TransportListener listener;
        volatile boolean closed;
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String line) {
            if (closed) throw new IllegalStateException("transport closed");
            sent.add(line);
            FakeTransport p = peer;
            if (p != null && p.listener != null) p.listener.onLine(line);
        }

        @Override
        public void setListener(LanChessSession.TransportListener l) {
            listener = l;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            LanChessSession.TransportListener l = listener;
            if (l != null) l.onClosed("local_close");
            FakeTransport p = peer;
            if (p != null && !p.closed) {
                p.closed = true;
                LanChessSession.TransportListener pl = p.listener;
                if (pl != null) pl.onClosed("peer_closed");
            }
        }

        /** 伪造被动断线（读端失效）：只通知对端，不通知本端。 */
        void dropConnection() {
            FakeTransport p = peer;
            if (p != null && !p.closed) {
                p.closed = true;
                LanChessSession.TransportListener pl = p.listener;
                if (pl != null) pl.onClosed("peer_closed");
            }
        }
    }

    /** 事件记录监听器（同步投递下无并发，字段直读）。 */
    static final class Recorder implements LanChessSession.SessionListener {
        volatile int readyCount;
        volatile boolean iAmRed;
        volatile String opponentName;
        final List<int[]> moves = new ArrayList<>();
        volatile int resignCount;
        volatile int leftCount;
        volatile int disconnectCount;
        volatile String disconnectReason;

        @Override
        public void onReady(boolean red, String name) {
            readyCount++;
            iAmRed = red;
            opponentName = name;
        }

        @Override
        public void onOpponentMove(int fromX, int fromY, int toX, int toY) {
            moves.add(new int[]{fromX, fromY, toX, toY});
        }

        @Override
        public void onOpponentResigned() {
            resignCount++;
        }

        @Override
        public void onOpponentLeft() {
            leftCount++;
        }

        @Override
        public void onDisconnected(String reason) {
            disconnectCount++;
            disconnectReason = reason;
        }
    }

    /** 一场双端互联的对局装配。 */
    static final class Match {
        final FakeTransport hostT = new FakeTransport();
        final FakeTransport guestT = new FakeTransport();
        final ChineseChessGame hostGame = new ChineseChessGame();
        final ChineseChessGame guestGame = new ChineseChessGame();
        final Recorder hostRec = new Recorder();
        final Recorder guestRec = new Recorder();
        final LanChessSession host;
        final LanChessSession guest;

        Match() {
            hostT.peer = guestT;
            guestT.peer = hostT;
            host = new LanChessSession(LanChessSession.Role.HOST, hostT, "主机",
                    (fx, fy, tx, ty) -> hostGame.isMoveLegal(fx, fy, tx, ty), hostRec);
            guest = new LanChessSession(LanChessSession.Role.GUEST, guestT, "访客",
                    (fx, fy, tx, ty) -> guestGame.isMoveLegal(fx, fy, tx, ty), guestRec);
        }

        /** 完成握手，双端进入 PLAYING。 */
        void start() {
            guest.start(); // GUEST 发 HELLO → HOST 回 WELCOME（同步管道内完成）
        }

        /** host 走子并在本端棋局落子；guest 收到后同样落子，保持双端盘面同步。 */
        boolean hostMove(int fx, int fy, int tx, int ty) {
            if (!host.sendMove(fx, fy, tx, ty)) return false;
            if (hostGame.commitMove(fx, fy, tx, ty) == null) return false;
            return applyReceivedMove(guestGame, guestRec, fx, fy, tx, ty);
        }

        boolean guestMove(int fx, int fy, int tx, int ty) {
            if (!guest.sendMove(fx, fy, tx, ty)) return false;
            if (guestGame.commitMove(fx, fy, tx, ty) == null) return false;
            return applyReceivedMove(hostGame, hostRec, fx, fy, tx, ty);
        }

        private static boolean applyReceivedMove(ChineseChessGame game, Recorder rec,
                                                 int fx, int fy, int tx, int ty) {
            if (rec.moves.isEmpty()) return false;
            int[] m = rec.moves.remove(rec.moves.size() - 1);
            return Arrays.equals(m, new int[]{fx, fy, tx, ty})
                    && game.commitMove(fx, fy, tx, ty) != null;
        }

        boolean boardsInSync() {
            return Arrays.deepEquals(hostGame.getBoardAsIntArray(), guestGame.getBoardAsIntArray());
        }
    }

    // ==================== 1. 协议编解码往返 ====================

    static void testProtocolRoundtrip() {
        System.out.println("[LAN-T1] 协议编解码往返");

        LanChessProtocol.Message move = LanChessProtocol.decode(LanChessProtocol.encodeMove(7, 7, 4, 7, 3));
        check("MOVE 往返 type", move != null && LanChessProtocol.TYPE_MOVE.equals(move.type()));
        check("MOVE 往返 fromX=7", move.getInt("fromX", -1) == 7);
        check("MOVE 往返 fromY=7", move.getInt("fromY", -1) == 7);
        check("MOVE 往返 toX=4", move.getInt("toX", -1) == 4);
        check("MOVE 往返 toY=7", move.getInt("toY", -1) == 7);
        check("MOVE 往返 ply=3", move.getInt("ply", -1) == 3);

        check("MOVE 行尾带换行仍可解码", LanChessProtocol.decode(
                LanChessProtocol.encodeMove(1, 2, 3, 4, 5) + "\r\n") != null);

        LanChessProtocol.Message hello = LanChessProtocol.decode(LanChessProtocol.encodeHello("访客"));
        check("HELLO 往返 type", hello != null && LanChessProtocol.TYPE_HELLO.equals(hello.type()));
        check("HELLO 往返 name", hello != null && "访客".equals(hello.getString("name", null)));
        check("HELLO 无名字可省略字段",
                LanChessProtocol.decode(LanChessProtocol.encodeHello(null)) != null
                        && !LanChessProtocol.decode(LanChessProtocol.encodeHello(null)).has("name"));

        LanChessProtocol.Message welcome = LanChessProtocol.decode(
                LanChessProtocol.encodeWelcome(LanChessProtocol.COLOR_BLACK, "GameMatrix-象棋-AB12"));
        check("WELCOME 往返 type", welcome != null && LanChessProtocol.TYPE_WELCOME.equals(welcome.type()));
        check("WELCOME 往返 color", welcome != null
                && LanChessProtocol.COLOR_BLACK.equals(welcome.getString("color", null)));
        check("WELCOME 往返 hostName", welcome != null
                && "GameMatrix-象棋-AB12".equals(welcome.getString("hostName", null)));

        check("RESIGN 往返", LanChessProtocol.TYPE_RESIGN.equals(
                LanChessProtocol.decode(LanChessProtocol.encodeResign()).type()));
        check("LEAVE 往返", LanChessProtocol.TYPE_LEAVE.equals(
                LanChessProtocol.decode(LanChessProtocol.encodeLeave()).type()));
        check("PING 往返", LanChessProtocol.TYPE_PING.equals(
                LanChessProtocol.decode(LanChessProtocol.encodePing()).type()));
        check("PONG 往返", LanChessProtocol.TYPE_PONG.equals(
                LanChessProtocol.decode(LanChessProtocol.encodePong()).type()));

        String tricky = "测试\"引号\"与\\反斜杠";
        LanChessProtocol.Message trickyHello = LanChessProtocol.decode(LanChessProtocol.encodeHello(tricky));
        check("字符串转义往返（引号/反斜杠/中文）",
                trickyHello != null && tricky.equals(trickyHello.getString("name", null)));
    }

    // ==================== 2. 协议容错 ====================

    static void testProtocolMalformed() {
        System.out.println("[LAN-T2] 协议容错（decode 永不抛异常）");

        try {
            check("非 JSON 返回 null", LanChessProtocol.decode("这不是json{{{") == null);
            check("null 行返回 null", LanChessProtocol.decode(null) == null);
            check("空行返回 null", LanChessProtocol.decode("   \r\n") == null);
            check("未知类型返回 null", LanChessProtocol.decode("{\"type\":\"BOGUS\"}") == null);
            check("缺 type 返回 null", LanChessProtocol.decode("{\"fromX\":1}") == null);
            check("MOVE 缺字段返回 null",
                    LanChessProtocol.decode("{\"type\":\"MOVE\",\"fromX\":1,\"fromY\":2,\"toX\":3}") == null);
            check("MOVE 字段类型错（字符串当整数）返回 null",
                    LanChessProtocol.decode("{\"type\":\"MOVE\",\"fromX\":\"1\",\"fromY\":2,\"toX\":3,\"toY\":4,\"ply\":5}") == null);
            check("MOVE 数值溢出返回 null",
                    LanChessProtocol.decode("{\"type\":\"MOVE\",\"fromX\":99999999999999999999,\"fromY\":2,\"toX\":3,\"toY\":4,\"ply\":5}") == null);
            check("WELCOME 颜色非法返回 null",
                    LanChessProtocol.decode("{\"type\":\"WELCOME\",\"color\":\"GREEN\",\"hostName\":\"h\"}") == null);
            check("数组根返回 null", LanChessProtocol.decode("[1,2,3]") == null);
            check("浮点数返回 null",
                    LanChessProtocol.decode("{\"type\":\"MOVE\",\"fromX\":1.5,\"fromY\":2,\"toX\":3,\"toY\":4,\"ply\":5}") == null);
            check("嵌套对象值返回 null",
                    LanChessProtocol.decode("{\"type\":\"MOVE\",\"fromX\":{},\"fromY\":2,\"toX\":3,\"toY\":4,\"ply\":5}") == null);
            check("对象后拖尾垃圾返回 null",
                    LanChessProtocol.decode("{\"type\":\"RESIGN\"} extra") == null);
            check("HELLO 无附加字段合法",
                    LanChessProtocol.decode("{\"type\":\"HELLO\"}") != null);
        } catch (RuntimeException e) {
            check("容错路径绝不抛异常（抛了: " + e + "）", false);
        }
    }

    // ==================== 3. 会话握手 ====================

    static void testHandshake() {
        System.out.println("[LAN-T3] HELLO→WELCOME 握手");

        Match m = new Match();
        check("握手前 host 状态 WAIT_HELLO", m.host.getState() == LanChessSession.State.WAIT_HELLO);
        m.start();
        check("握手后 host 状态 PLAYING", m.host.getState() == LanChessSession.State.PLAYING);
        check("握手后 guest 状态 PLAYING", m.guest.getState() == LanChessSession.State.PLAYING);
        check("host 执红", m.hostRec.readyCount == 1 && m.hostRec.iAmRed);
        check("guest 执黑", m.guestRec.readyCount == 1 && !m.guestRec.iAmRed);
        check("host 得知对方名", "访客".equals(m.hostRec.opponentName));
        check("guest 得知主机名", "主机".equals(m.guestRec.opponentName));
        check("host 线上发出 WELCOME",
                m.hostT.sent.stream().anyMatch(l -> LanChessProtocol.TYPE_WELCOME.equals(
                        typeOf(l))));
        check("host 先行（isMyTurn）", m.host.isMyTurn());
        check("guest 后行（非本方回合）", !m.guest.isMyTurn());
    }

    private static String typeOf(String line) {
        LanChessProtocol.Message msg = LanChessProtocol.decode(line);
        return msg == null ? "" : msg.type();
    }

    // ==================== 4. 双方交替走子 ====================

    static void testAlternatingMoves() {
        System.out.println("[LAN-T4] 双方交替 MOVE（红先行）");

        Match m = new Match();
        m.start();

        check("guest 先走被拒（未轮到）", !m.guest.sendMove(1, 2, 4, 2));
        // 红炮 (7,7)→(4,7)：初始盘面合法着（炮二平五）。
        check("host 第一手合法发出", m.hostMove(7, 7, 4, 7));
        check("host 走后非本方回合", !m.host.isMyTurn());
        check("guest 收到对方着法", m.guestRec.disconnectCount == 0 && m.hostRec.moves.isEmpty());
        check("双端盘面同步（1手）", m.boardsInSync());

        // 黑炮 (1,2)→(4,2)：对称应对。
        check("guest 第二手合法发出", m.guestMove(1, 2, 4, 2));
        check("双端盘面同步（2手）", m.boardsInSync());

        // 红兵 (4,6)→(4,5)。
        check("host 第三手合法发出", m.hostMove(4, 6, 4, 5));
        // 黑卒 (0,3)→(0,4)。
        check("guest 第四手合法发出", m.guestMove(0, 3, 0, 4));
        check("双端盘面同步（4手）", m.boardsInSync());
        check("四手后无任何断线回调",
                m.hostRec.disconnectCount == 0 && m.guestRec.disconnectCount == 0);
    }

    // ==================== 5. 对方非法着法/越序/失步 → 断开 ====================

    static void testIllegalOpponentMove() {
        System.out.println("[LAN-T5] 对方非法着法被拒并断开");

        Match m = new Match();
        m.start();
        check("host 第一手合法发出", m.hostMove(7, 7, 4, 7));
        // 黑车 (0,0)→(8,0)：跨子跳走，规则非法。
        m.guestT.send(LanChessProtocol.encodeMove(0, 0, 8, 0, 2));
        check("host 收到非法着法后断线回调", m.hostRec.disconnectCount == 1);
        check("断线原因为对方着法非法",
                LanChessSession.REASON_ILLEGAL_MOVE.equals(m.hostRec.disconnectReason));
        check("host 状态 DISCONNECTED", m.host.getState() == LanChessSession.State.DISCONNECTED);
        check("host 盘面未被非法着法污染（仅1手）", m.hostGame.getMoveHistory().size() == 1);
    }

    static void testOutOfTurnMove() {
        System.out.println("[LAN-T6] 越序走子（轮到本方时对方发 MOVE）被拒");

        Match m = new Match();
        m.start();
        // 轮到 host（红先行），guest 却发 MOVE：轮次违规。
        m.guestT.send(LanChessProtocol.encodeMove(1, 2, 4, 2, 1));
        check("host 收到越序着法后断线回调", m.hostRec.disconnectCount == 1);
        check("断线原因为越序走子",
                LanChessSession.REASON_OUT_OF_TURN.equals(m.hostRec.disconnectReason));
    }

    static void testPlyMismatch() {
        System.out.println("[LAN-T7] ply 失步断开");

        Match m = new Match();
        m.start();
        check("host 第一手合法发出", m.hostMove(7, 7, 4, 7));
        // 期望 ply=2，伪造 ply=7 的合法着（黑炮平中）——序号失步必须断开。
        m.guestT.send(LanChessProtocol.encodeMove(1, 2, 4, 2, 7));
        check("host 收到失步着法后断线回调", m.hostRec.disconnectCount == 1);
        check("断线原因为序号失步",
                LanChessSession.REASON_PLY_OUT_OF_SYNC.equals(m.hostRec.disconnectReason));
    }

    static void testDoubleMove() {
        System.out.println("[LAN-T8] 连续两手（对方走两次）被拒");

        Match m = new Match();
        m.start();
        check("host 第一手合法发出", m.hostMove(7, 7, 4, 7));
        check("guest 第二手合法发出", m.guestMove(1, 2, 4, 2));
        // guest 未等 host 回手又走一步：host 已轮到走棋，判越序。
        m.guestT.send(LanChessProtocol.encodeMove(0, 3, 0, 4, 3));
        check("host 收到连续两手后断线", m.hostRec.disconnectCount == 1);
        check("断线原因为越序走子",
                LanChessSession.REASON_OUT_OF_TURN.equals(m.hostRec.disconnectReason));
    }

    // ==================== 6. RESIGN / LEAVE / 断线 ====================

    static void testResign() {
        System.out.println("[LAN-T9] GUEST 认输双端回调");

        Match m = new Match();
        m.start();
        check("guest 认输发出", m.guest.sendResign());
        check("host 收到认输回调", m.hostRec.resignCount == 1);
        check("host 无断线误报", m.hostRec.disconnectCount == 0);
        check("认输后 host 不能继续走子", !m.host.sendMove(7, 7, 4, 7));
        check("认输后 guest 不能继续走子", !m.guest.sendMove(1, 2, 4, 2));
    }

    static void testLeave() {
        System.out.println("[LAN-T10] LEAVE → 对方 OPPONENT_LEFT");

        Match m = new Match();
        m.start();
        m.guest.sendLeave();
        check("host 收到离开回调", m.hostRec.leftCount == 1);
        check("host 状态 OPPONENT_LEFT", m.host.getState() == LanChessSession.State.OPPONENT_LEFT);
        check("guest 发起方进入 DISCONNECTED",
                m.guest.getState() == LanChessSession.State.DISCONNECTED);
        check("双方均无 onDisconnected 误报",
                m.hostRec.disconnectCount == 0 && m.guestRec.disconnectCount == 0);
    }

    static void testDisconnect() {
        System.out.println("[LAN-T11] 断线回调（对端读端失效）");

        Match m = new Match();
        m.start();
        m.guestT.dropConnection(); // 模拟 guest 掉线：host 读线程收到 onClosed
        check("host 收到断线回调", m.hostRec.disconnectCount == 1);
        check("断线原因透传", "peer_closed".equals(m.hostRec.disconnectReason));
        check("host 状态 DISCONNECTED", m.host.getState() == LanChessSession.State.DISCONNECTED);
        check("断线后 host 不能继续走子", !m.host.sendMove(7, 7, 4, 7));
    }

    static void testMalformedLineTolerated() {
        System.out.println("[LAN-T12] 畸形行容错（不炸读线程、不失状态）");

        Match m = new Match();
        m.start();
        m.guestT.send("garbage{{{");
        m.guestT.send("{\"type\":\"UNKNOWN_TYPE\",\"x\":1}");
        m.guestT.send("{\"type\":\"MOVE\",\"fromX\":\"bad\"}");
        check("host 收到 3 条畸形行后无断线回调", m.hostRec.disconnectCount == 0);
        check("host 状态保持 PLAYING", m.host.getState() == LanChessSession.State.PLAYING);
        // 畸形行只容错忽略：对局继续正常走子。
        check("畸形行后对局仍可继续", m.hostMove(7, 7, 4, 7));
        check("畸形行后 guest 仍可应手", m.guestMove(1, 2, 4, 2));
    }

    static void testPingPong() {
        System.out.println("[LAN-T13] PING 自动回 PONG");

        Match m = new Match();
        m.start();
        m.guestT.send(LanChessProtocol.encodePing());
        check("host 自动回复 PONG", m.hostT.sent.stream().anyMatch(
                l -> LanChessProtocol.TYPE_PONG.equals(typeOf(l))));
        check("PONG 期间会话不受影响", m.host.getState() == LanChessSession.State.PLAYING);
    }

    static void testSendAfterClose() {
        System.out.println("[LAN-T14] 关闭后的会话拒绝发送");

        Match m = new Match();
        m.start();
        m.host.close();
        check("close 后状态 DISCONNECTED", m.host.getState() == LanChessSession.State.DISCONNECTED);
        check("close 后 sendMove 返回 false", !m.host.sendMove(7, 7, 4, 7));
        check("close 后 sendResign 返回 false", !m.host.sendResign());
        check("close 后 sendPing 返回 false", !m.host.sendPing());
        check("主动 close 不触发 onDisconnected", m.hostRec.disconnectCount == 0);
    }

    // ==================== main ====================

    public static void main(String[] args) {
        testProtocolRoundtrip();
        testProtocolMalformed();
        testHandshake();
        testAlternatingMoves();
        testIllegalOpponentMove();
        testOutOfTurnMove();
        testPlyMismatch();
        testDoubleMove();
        testResign();
        testLeave();
        testDisconnect();
        testMalformedLineTolerated();
        testPingPong();
        testSendAfterClose();

        System.out.println("LanChessTest 结果: PASS=" + passed + " FAIL=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
