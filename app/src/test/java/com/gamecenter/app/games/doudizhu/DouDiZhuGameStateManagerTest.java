package com.gamecenter.app.games.doudizhu;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class DouDiZhuGameStateManagerTest {
    private DouDiZhuGameStateManager stateManager;

    @Before
    public void setUp() {
        stateManager = new DouDiZhuGameStateManager();
    }

    @Test
    public void testInitialState() {
        assertEquals(DouDiZhuGameStateManager.STATE_LOBBY, stateManager.getGameState());
        assertEquals(0, stateManager.getCurrentTurn());
        assertEquals(-1, stateManager.getLandlordIndex());
        assertEquals(-1, stateManager.getWinnerIndex());
        assertEquals(-1, stateManager.getLastPlayerWhoPlayed());
    }
}
