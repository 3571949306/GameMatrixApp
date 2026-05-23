package com.gamecenter.app.games.doudizhu;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DouDiZhuSeatManagerTest {

    private DouDiZhuSeatManager manager;

    @Before
    public void setUp() {
        manager = new DouDiZhuSeatManager();
    }

    @Test
    public void defaultSeatTypes_hostAiAi() {
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, manager.getSeatType(0));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(2));
    }

    @Test
    public void defaultClientIds_allNegative1() {
        assertEquals(-1, manager.getClientId(0));
        assertEquals(-1, manager.getClientId(1));
        assertEquals(-1, manager.getClientId(2));
    }

    @Test
    public void defaultClientIps_allEmpty() {
        assertEquals("", manager.getClientIp(0));
        assertEquals("", manager.getClientIp(1));
        assertEquals("", manager.getClientIp(2));
    }

    @Test
    public void defaultPeerTokens_allEmpty() {
        assertEquals("", manager.getPeerToken(0));
        assertEquals("", manager.getPeerToken(1));
        assertEquals("", manager.getPeerToken(2));
    }

    @Test
    public void defaultLastProcessedActionIds_allZero() {
        assertEquals(0L, manager.getLastProcessedActionId(0));
        assertEquals(0L, manager.getLastProcessedActionId(1));
        assertEquals(0L, manager.getLastProcessedActionId(2));
    }

    @Test
    public void seatTypeConstants_areCorrect() {
        assertEquals(0, DouDiZhuSeatManager.SEAT_TYPE_HOST);
        assertEquals(1, DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertEquals(2, DouDiZhuSeatManager.SEAT_TYPE_AI);
        assertEquals(3, DouDiZhuSeatManager.TOTAL_SEATS);
    }

    @Test
    public void updateSeat_setsAllFields() {
        manager.updateSeat(1, 100, "192.168.1.1", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertEquals(100, manager.getClientId(1));
        assertEquals("192.168.1.1", manager.getClientIp(1));
        assertEquals("token1", manager.getPeerToken(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, manager.getSeatType(1));
    }

    @Test
    public void updateSeat_seat0() {
        manager.updateSeat(0, 50, "10.0.0.1", "hostToken", DouDiZhuSeatManager.SEAT_TYPE_HOST);
        assertEquals(50, manager.getClientId(0));
        assertEquals("10.0.0.1", manager.getClientIp(0));
        assertEquals("hostToken", manager.getPeerToken(0));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, manager.getSeatType(0));
    }

    @Test
    public void updateSeat_nullIp_setsEmpty() {
        manager.updateSeat(1, 100, null, "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertEquals("", manager.getClientIp(1));
    }

    @Test
    public void updateSeat_nullPeerToken_setsEmpty() {
        manager.updateSeat(1, 100, "192.168.1.1", null, DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertEquals("", manager.getPeerToken(1));
    }

    @Test
    public void updateSeat_invalidIndexNegative_doesNothing() {
        manager.updateSeat(-1, 100, "ip", "token", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
    }

    @Test
    public void updateSeat_invalidIndexTooLarge_doesNothing() {
        manager.updateSeat(3, 100, "ip", "token", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
    }

    @Test
    public void updateSeat_overwritesPreviousValues() {
        manager.updateSeat(1, 100, "192.168.1.1", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.updateSeat(1, 200, "192.168.1.2", "token2", DouDiZhuSeatManager.SEAT_TYPE_AI);
        assertEquals(200, manager.getClientId(1));
        assertEquals("192.168.1.2", manager.getClientIp(1));
        assertEquals("token2", manager.getPeerToken(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
    }

    @Test
    public void resetAllSeats_restoresDefaultSeatTypes() {
        manager.updateSeat(1, 100, "192.168.1.1", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.updateSeat(2, 200, "192.168.1.2", "token2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.resetAllSeats();
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, manager.getSeatType(0));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(2));
    }

    @Test
    public void resetAllSeats_clearsClientIds() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.resetAllSeats();
        assertEquals(-1, manager.getClientId(0));
        assertEquals(-1, manager.getClientId(1));
        assertEquals(-1, manager.getClientId(2));
    }

    @Test
    public void resetAllSeats_clearsClientIps() {
        manager.updateSeat(1, 100, "192.168.1.1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.resetAllSeats();
        assertEquals("", manager.getClientIp(1));
    }

    @Test
    public void resetAllSeats_clearsPeerTokens() {
        manager.updateSeat(1, 100, "ip1", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.resetAllSeats();
        assertEquals("", manager.getPeerToken(1));
    }

    @Test
    public void resetAllSeats_resetsActionIds() {
        manager.setLastProcessedActionId(1, 42L);
        manager.setLastProcessedActionId(2, 99L);
        manager.resetAllSeats();
        assertEquals(0L, manager.getLastProcessedActionId(1));
        assertEquals(0L, manager.getLastProcessedActionId(2));
    }

    @Test
    public void resetAllSeats_clearsRoomInfo() {
        manager.setRemoteRoomInfo("room1", "invite_addr", "host info");
        manager.resetAllSeats();
        assertEquals("", manager.getRemoteRoomCode());
        assertEquals("", manager.getRemoteInviteAddress());
        assertEquals("", manager.getRemoteHostInfoText());
    }

    @Test
    public void assignSeatToClient_firstClient_getsSeat1() {
        int seat = manager.assignSeatToClient(100, "192.168.1.1", "token1", 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_secondClient_getsSeat2() {
        int seat1 = manager.assignSeatToClient(100, "192.168.1.1", "token1", 0, 0);
        manager.updateSeat(seat1, 100, "192.168.1.1", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat2 = manager.assignSeatToClient(200, "192.168.1.2", "token2", 0, 0);
        assertEquals(2, seat2);
    }

    @Test
    public void assignSeatToClient_fullSeats_returnsNegative1() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(300, "192.168.1.3", "token3", 0, 0);
        assertEquals(-1, seat);
    }

    @Test
    public void assignSeatToClient_sameClientId_returnsExistingSeat() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(100, "192.168.1.1", "token1", 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_sameClientIdOnSeat2_returnsSeat2() {
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.2", "token2", 0, 0);
        assertEquals(2, seat);
    }

    @Test
    public void assignSeatToClient_peerTokenMatch_returnsMatchingSeat() {
        manager.updateSeat(1, -1, "", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.2", "token1", 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_peerTokenMatchSeat2_returnsSeat2() {
        manager.updateSeat(2, -1, "", "token2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(100, "192.168.1.1", "token2", 0, 0);
        assertEquals(2, seat);
    }

    @Test
    public void assignSeatToClient_emptyPeerToken_skipsTokenMatch() {
        manager.updateSeat(1, -1, "", "", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.2", "", 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_nullPeerToken_skipsTokenMatch() {
        manager.updateSeat(1, -1, "", "token1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.2", null, 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_ipMatchDuringGame_returnsMatchingSeat() {
        manager.updateSeat(1, -1, "192.168.1.1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.1", "newToken", 2, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_ipMatchNotRemote_returnsSimpleAssignment() {
        manager.updateSeat(1, -1, "192.168.1.1", "t1", DouDiZhuSeatManager.SEAT_TYPE_AI);
        int seat = manager.assignSeatToClient(200, "192.168.1.1", "newToken", 2, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_currentTurnPriorityDuringGame() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.updateSeat(2, -1, "", "", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(300, "192.168.1.3", "t3", 2, 2);
        assertEquals(2, seat);
    }

    @Test
    public void assignSeatToClient_duringBidding_ipMatch() {
        manager.updateSeat(1, -1, "192.168.1.1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.1", "newToken", 1, 0);
        assertEquals(1, seat);
    }

    @Test
    public void assignSeatToClient_lobby_noIpMatch() {
        manager.updateSeat(1, -1, "192.168.1.1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.assignSeatToClient(200, "192.168.1.2", "t2", 0, 0);
        assertEquals(1, seat);
    }

    @Test
    public void handleClientDisconnect_remoteP2P_retainsRemoteType() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(100, true, 2, null);
        assertEquals(1, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, manager.getSeatType(1));
        assertEquals(-1, manager.getClientId(1));
    }

    @Test
    public void handleClientDisconnect_lobby_replacesWithAi() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(100, false, 0, null);
        assertEquals(1, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
    }

    @Test
    public void handleClientDisconnect_gameOver_replacesWithAi() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(100, false, 3, null);
        assertEquals(1, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
    }

    @Test
    public void handleClientDisconnect_inGame_notP2P_retainsRemoteType() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(100, false, 2, null);
        assertEquals(1, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, manager.getSeatType(1));
    }

    @Test
    public void handleClientDisconnect_inBidding_notP2P_retainsRemoteType() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(100, false, 1, null);
        assertEquals(1, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, manager.getSeatType(1));
    }

    @Test
    public void handleClientDisconnect_unknownClient_returnsNegative1() {
        int seat = manager.handleClientDisconnect(999, false, 0, null);
        assertEquals(-1, seat);
    }

    @Test
    public void handleClientDisconnect_seat2_lobby_replacesWithAi() {
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int seat = manager.handleClientDisconnect(200, false, 0, null);
        assertEquals(2, seat);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(2));
    }

    @Test
    public void handleClientDisconnect_callsInitAIForSeat_inLobby() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        final boolean[] initCalled = {false};
        final int[] seatCalled = {-1};
        DouDiZhuSeatManager.AICallback callback = new DouDiZhuSeatManager.AICallback() {
            @Override
            public void initAIForSeat(int seatIndex) {
                initCalled[0] = true;
                seatCalled[0] = seatIndex;
            }

            @Override
            public void showSeatToast(int seatIndex, String message) {
            }
        };
        manager.handleClientDisconnect(100, false, 0, callback);
        assertTrue(initCalled[0]);
        assertEquals(1, seatCalled[0]);
    }

    @Test
    public void handleClientDisconnect_callsShowToast_inP2PMode() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        final boolean[] toastCalled = {false};
        final int[] toastSeat = {-1};
        DouDiZhuSeatManager.AICallback callback = new DouDiZhuSeatManager.AICallback() {
            @Override
            public void initAIForSeat(int seatIndex) {
            }

            @Override
            public void showSeatToast(int seatIndex, String message) {
                toastCalled[0] = true;
                toastSeat[0] = seatIndex;
            }
        };
        manager.handleClientDisconnect(100, true, 2, callback);
        assertTrue(toastCalled[0]);
        assertEquals(1, toastSeat[0]);
    }

    @Test
    public void handleClientDisconnect_nullCallback_doesNotThrow() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.handleClientDisconnect(100, false, 0, null);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
    }

    @Test
    public void hasSeatTypeRemote_noRemote_returnsFalse() {
        assertFalse(manager.hasSeatTypeRemote(0, 3));
    }

    @Test
    public void hasSeatTypeRemote_withRemoteSeat1_returnsTrue() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertTrue(manager.hasSeatTypeRemote(0, 3));
    }

    @Test
    public void hasSeatTypeRemote_withRemoteSeat2_returnsTrue() {
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertTrue(manager.hasSeatTypeRemote(0, 3));
    }

    @Test
    public void hasSeatTypeRemote_remoteOutsideRange_returnsFalse() {
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertFalse(manager.hasSeatTypeRemote(0, 2));
    }

    @Test
    public void hasSeatTypeRemote_rangeIncludesRemote_returnsTrue() {
        manager.updateSeat(2, 200, "ip2", "t2", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertTrue(manager.hasSeatTypeRemote(1, 3));
    }

    @Test
    public void hasDisconnectedRemoteSeat_noRemote_returnsFalse() {
        assertFalse(manager.hasDisconnectedRemoteSeat());
    }

    @Test
    public void hasDisconnectedRemoteSeat_connectedRemote_returnsFalse() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        assertFalse(manager.hasDisconnectedRemoteSeat());
    }

    @Test
    public void hasDisconnectedRemoteSeat_disconnectedRemote_returnsTrue() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        manager.handleClientDisconnect(100, true, 2, null);
        assertTrue(manager.hasDisconnectedRemoteSeat());
    }

    @Test
    public void hasDisconnectedRemoteSeat_aiSeat_returnsFalse() {
        manager.updateSeat(1, -1, "", "", DouDiZhuSeatManager.SEAT_TYPE_AI);
        assertFalse(manager.hasDisconnectedRemoteSeat());
    }

    @Test
    public void hasDisconnectedRemoteSeat_hostSeat_returnsFalse() {
        assertFalse(manager.hasDisconnectedRemoteSeat());
    }

    @Test
    public void getSeatTypes_returnsClone() {
        int[] types = manager.getSeatTypes();
        types[1] = DouDiZhuSeatManager.SEAT_TYPE_REMOTE;
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, manager.getSeatType(1));
    }

    @Test
    public void getSeatTypes_returnsCorrectValues() {
        manager.updateSeat(1, 100, "ip1", "t1", DouDiZhuSeatManager.SEAT_TYPE_REMOTE);
        int[] types = manager.getSeatTypes();
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_HOST, types[0]);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_REMOTE, types[1]);
        assertEquals(DouDiZhuSeatManager.SEAT_TYPE_AI, types[2]);
    }

    @Test
    public void setRemoteRoomInfo_setsAllFields() {
        manager.setRemoteRoomInfo("ABC123", "192.168.1.1:8080", "Host Player");
        assertEquals("ABC123", manager.getRemoteRoomCode());
        assertEquals("192.168.1.1:8080", manager.getRemoteInviteAddress());
        assertEquals("Host Player", manager.getRemoteHostInfoText());
    }

    @Test
    public void setLastProcessedActionId_updatesValue() {
        manager.setLastProcessedActionId(1, 42L);
        assertEquals(42L, manager.getLastProcessedActionId(1));
    }

    @Test
    public void setLastProcessedActionId_differentSeats_independent() {
        manager.setLastProcessedActionId(1, 42L);
        manager.setLastProcessedActionId(2, 99L);
        assertEquals(42L, manager.getLastProcessedActionId(1));
        assertEquals(99L, manager.getLastProcessedActionId(2));
    }

    @Test
    public void pendingClientIp_putAndGet() {
        manager.putPendingClientIp(100, "192.168.1.1");
        assertEquals("192.168.1.1", manager.getPendingClientIp(100));
    }

    @Test
    public void pendingClientIp_nullIp_storesEmpty() {
        manager.putPendingClientIp(100, null);
        assertEquals("", manager.getPendingClientIp(100));
    }

    @Test
    public void pendingClientIp_unknownClient_returnsEmpty() {
        assertEquals("", manager.getPendingClientIp(999));
    }

    @Test
    public void removePendingClientIp_removesEntry() {
        manager.putPendingClientIp(100, "192.168.1.1");
        manager.removePendingClientIp(100);
        assertEquals("", manager.getPendingClientIp(100));
    }

    @Test
    public void clearPendingIps_clearsAll() {
        manager.putPendingClientIp(100, "192.168.1.1");
        manager.putPendingClientIp(200, "192.168.1.2");
        manager.clearPendingIps();
        assertEquals("", manager.getPendingClientIp(100));
        assertEquals("", manager.getPendingClientIp(200));
    }

    @Test
    public void getLocalPeerToken_noContext_returnsEmpty() {
        assertEquals("", manager.getLocalPeerToken());
    }

    @Test
    public void setContext_nullContext_getLocalPeerTokenReturnsEmpty() {
        manager.setContext(null);
        assertEquals("", manager.getLocalPeerToken());
    }
}
