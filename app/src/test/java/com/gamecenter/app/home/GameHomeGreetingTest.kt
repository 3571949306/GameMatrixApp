package com.gamecenter.app.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶栏时段问候纯函数测试（批次 2a）：
 * 口径与 legacy GamesFragment.updateGreeting 一致（5-11 早 / 12-17 午 / 其余晚）。
 */
class GameHomeGreetingTest {

    @Test
    fun `早晨区间 5 到 11 点`() {
        assertEquals(GameHomeGreeting.Period.MORNING, GameHomeGreeting.periodFor(5))
        assertEquals(GameHomeGreeting.Period.MORNING, GameHomeGreeting.periodFor(11))
    }

    @Test
    fun `午后区间 12 到 17 点`() {
        assertEquals(GameHomeGreeting.Period.AFTERNOON, GameHomeGreeting.periodFor(12))
        assertEquals(GameHomeGreeting.Period.AFTERNOON, GameHomeGreeting.periodFor(17))
    }

    @Test
    fun `晚间为其余时段`() {
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(18))
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(23))
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(0))
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(4))
    }

    @Test
    fun `区间边界不串档`() {
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(4))
        assertEquals(GameHomeGreeting.Period.MORNING, GameHomeGreeting.periodFor(5))
        assertEquals(GameHomeGreeting.Period.AFTERNOON, GameHomeGreeting.periodFor(12))
        assertEquals(GameHomeGreeting.Period.EVENING, GameHomeGreeting.periodFor(18))
    }
}
