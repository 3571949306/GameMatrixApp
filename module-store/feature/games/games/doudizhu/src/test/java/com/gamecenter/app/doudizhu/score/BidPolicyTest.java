package com.gamecenter.app.doudizhu.score;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * BidPolicy 单测：叫分档位校验、AI 叫分决策（含简单档保守）、重发判定。
 */
public class BidPolicyTest {

    @Test
    public void isValidBidAcceptsHigherBidsOnly() {
        assertTrue(BidPolicy.isValidBid(1, 0));
        assertTrue(BidPolicy.isValidBid(2, 1));
        assertTrue(BidPolicy.isValidBid(3, 2));
        assertFalse("不高于当前最高分的叫分无效", BidPolicy.isValidBid(1, 1));
        assertFalse(BidPolicy.isValidBid(2, 3));
        assertFalse("超出上限无效", BidPolicy.isValidBid(4, 0));
        assertFalse("低于下限无效", BidPolicy.isValidBid(0, 0));
    }

    @Test
    public void decideAiBidBidsOnlyWhenHigherThanHighest() {
        assertEquals("意向 2、无人叫 → 叫 2", 2, BidPolicy.decideAiBid(2, 0, false));
        assertEquals("意向 2、已有人叫 2 → 不叫", 0, BidPolicy.decideAiBid(2, 2, false));
        assertEquals("意向 3、已有人叫 2 → 加叫 3", 3, BidPolicy.decideAiBid(3, 2, false));
        assertEquals("无意向不叫", 0, BidPolicy.decideAiBid(0, 0, false));
    }

    @Test
    public void easyDifficultyIsMoreConservative() {
        assertEquals("简单档意向 2 折算为 1", 1, BidPolicy.decideAiBid(2, 0, true));
        assertEquals("简单档意向 1 不叫", 0, BidPolicy.decideAiBid(1, 0, true));
        assertEquals("普通档意向 1 叫 1", 1, BidPolicy.decideAiBid(1, 0, false));
        assertEquals("简单档意向 3 仍可叫 2", 2, BidPolicy.decideAiBid(3, 0, true));
        assertEquals("意向档位越界收敛到 3", 2, BidPolicy.decideAiBid(9, 0, true));
    }

    @Test
    public void shouldRedealRespectsLimit() {
        assertTrue("一轮全过且未超上限 → 重发", BidPolicy.shouldRedeal(3, 0));
        assertTrue(BidPolicy.shouldRedeal(3, 2));
        assertFalse("已达重发上限 → 保底强制", BidPolicy.shouldRedeal(3, 3));
        assertFalse("未满一轮不触发", BidPolicy.shouldRedeal(2, 0));
    }

    @Test
    public void constantsMatchDocumentRules() {
        assertEquals(1, BidPolicy.MIN_BID);
        assertEquals(3, BidPolicy.MAX_BID);
        assertEquals(3, BidPolicy.MAX_REDEAL_TIMES);
        assertEquals(1, BidPolicy.FORCED_BID);
    }
}
