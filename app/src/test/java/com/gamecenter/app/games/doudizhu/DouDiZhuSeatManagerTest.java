package com.gamecenter.app.games.doudizhu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class DouDiZhuSeatManagerTest {

    private DouDiZhuSeatManager seatManager;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        seatManager = new DouDiZhuSeatManager();
        seatManager.setContext(context);
    }

    @Test
    public void testInitialState() {
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, seatManager.getSeatType(0));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, seatManager.getSeatType(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, seatManager.getSeatType(2));
        
        assertEquals(-1, seatManager.getClientId(1));
        assertEquals(-1, seatManager.getClientId(2));
    }

    @Test
    public void testAssignSeat_SimpleAssignment() {
        // 大厅状态下分配座位
        int seat1 = seatManager.assignSeatToClient(101, "192.168.1.101", "token1", 0, -1);
        assertEquals(1, seat1);
        seatManager.updateSeat(seat1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);

        int seat2 = seatManager.assignSeatToClient(102, "192.168.1.102", "token2", 0, -1);
        assertEquals(2, seat2);
        seatManager.updateSeat(seat2, 102, "192.168.1.102", "token2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);

        // 第三个客户端尝试连接
        int seat3 = seatManager.assignSeatToClient(103, "192.168.1.103", "token3", 0, -1);
        assertEquals(-1, seat3); // 座位已满
    }

    @Test
    public void testAssignSeat_ClientIdMatch() {
        // 先分配一个座位
        seatManager.updateSeat(1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        
        // 相同的 clientId 再次连接
        int seat = seatManager.assignSeatToClient(101, "192.168.1.101", "token1", 0, -1);
        assertEquals(1, seat);
    }

    @Test
    public void testAssignSeat_PeerTokenMatch() {
        seatManager.updateSeat(2, 102, "192.168.1.102", "token2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        
        // 不同的 clientId，但是 peerToken 相同
        int seat = seatManager.assignSeatToClient(105, "192.168.1.105", "token2", 0, -1);
        assertEquals(2, seat);
    }

    @Test
    public void testAssignSeat_IpMatchInGame() {
        seatManager.updateSeat(1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        // clientId 变为 -1 模拟断开，但座位保留为 REMOTE
        seatManager.updateSeat(1, -1, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        
        // 不同的 clientId 和 peerToken，但 IP 相同，且在游戏中(gameState=1)
        int seat = seatManager.assignSeatToClient(109, "192.168.1.101", "newToken", 1, -1);
        assertEquals(1, seat);
    }

    @Test
    public void testHandleClientDisconnect_Lobby() {
        seatManager.updateSeat(1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        
        DouDiZhuSeatManager.AICallback mockCallback = mock(DouDiZhuSeatManager.AICallback.class);
        
        // 大厅状态下断开
        int disconnectedSeat = seatManager.handleClientDisconnect(101, false, 0, mockCallback);
        
        assertEquals(1, disconnectedSeat);
        assertEquals(-1, seatManager.getClientId(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, seatManager.getSeatType(1)); // 替换为AI
        verify(mockCallback).initAIForSeat(1);
    }

    @Test
    public void testHandleClientDisconnect_InGame() {
        seatManager.updateSeat(1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        
        DouDiZhuSeatManager.AICallback mockCallback = mock(DouDiZhuSeatManager.AICallback.class);
        
        // 游戏进行中(gameState=1)断开
        int disconnectedSeat = seatManager.handleClientDisconnect(101, false, 1, mockCallback);
        
        assertEquals(1, disconnectedSeat);
        assertEquals(-1, seatManager.getClientId(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, seatManager.getSeatType(1)); // 保留为 REMOTE 等待重连
        verify(mockCallback).showSeatToast(eq(1), anyString());
    }

    @Test
    public void testGetLocalPeerToken() {
        String token1 = seatManager.getLocalPeerToken();
        assertTrue(token1 != null && !token1.isEmpty());
        
        String token2 = seatManager.getLocalPeerToken();
        assertEquals(token1, token2); // 应该一致
    }

    @Test
    public void testResetAllSeats() {
        seatManager.updateSeat(1, 101, "192.168.1.101", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        seatManager.setRemoteRoomInfo("ROOM", "ADDRESS", "HOST");
        
        seatManager.resetAllSeats();
        
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, seatManager.getSeatType(0));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, seatManager.getSeatType(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, seatManager.getSeatType(2));
        assertEquals(-1, seatManager.getClientId(1));
        assertEquals("", seatManager.getRemoteRoomCode());
    }
}
