package com.gamecenter.app.browser.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.player.BrowserPlayerMath;

import org.junit.Test;

/**
 * 播放器计算逻辑的纯 JVM 回归测试。
 *
 * <p>只覆盖不依赖 Android 框架的 {@link BrowserPlayerMath}，
 * 因此无需 Robolectric，也不会触发 Gradle 的 mergeDebugAssets（受保护资产）。
 * 运行：{@code :app:testDebugUnitTest --tests "*BrowserPlayerMathTest*"}。
 */
public class BrowserPlayerMathTest {

    private static final float EPS = 0.0001f;

    // ===== 倍速钳制 =====

    @Test
    public void clampRate_keepsValidRate() {
        assertEquals(2.0f, BrowserPlayerMath.clampRate(2.0f), EPS);
        assertEquals(1.0f, BrowserPlayerMath.clampRate(1.0f), EPS);
    }

    @Test
    public void clampRate_clampsOutOfRange() {
        assertEquals(BrowserPlayerMath.RATE_MIN, BrowserPlayerMath.clampRate(0.1f), EPS);
        assertEquals(BrowserPlayerMath.RATE_MAX, BrowserPlayerMath.clampRate(99f), EPS);
    }

    @Test
    public void clampRate_sanitizesNonFinite() {
        assertEquals(1.0f, BrowserPlayerMath.clampRate(Float.NaN), EPS);
        assertEquals(1.0f, BrowserPlayerMath.clampRate(Float.POSITIVE_INFINITY), EPS);
    }

    // ===== 长按快进 =====

    @Test
    public void fastForward_usesConfiguredRateBeforeThreshold() {
        assertEquals(2.0f, BrowserPlayerMath.fastForwardRate(0L, 2.0f), EPS);
        assertEquals(1.5f, BrowserPlayerMath.fastForwardRate(1500L, 1.5f), EPS);
    }

    @Test
    public void fastForward_escalatesAfterExtendedHold() {
        assertEquals(BrowserPlayerMath.EXTENDED_FAST_FORWARD_RATE,
                BrowserPlayerMath.fastForwardRate(BrowserPlayerMath.EXTENDED_HOLD_MS, 2.0f), EPS);
        assertEquals(BrowserPlayerMath.EXTENDED_FAST_FORWARD_RATE,
                BrowserPlayerMath.fastForwardRate(BrowserPlayerMath.EXTENDED_HOLD_MS + 5000L, 1.5f), EPS);
    }

    @Test
    public void fastForward_neverExceedsMaxEvenIfConfigured() {
        assertEquals(BrowserPlayerMath.RATE_MAX,
                BrowserPlayerMath.fastForwardRate(BrowserPlayerMath.EXTENDED_HOLD_MS, 3.0f), EPS);
    }

    // ===== 时间格式化 =====

    @Test
    public void formatTime_handlesZeroAndNegative() {
        assertEquals("00:00", BrowserPlayerMath.formatTime(0L));
        assertEquals("00:00", BrowserPlayerMath.formatTime(-5000L));
    }

    @Test
    public void formatTime_handlesMinutes() {
        assertEquals("01:05", BrowserPlayerMath.formatTime(65_000L));
        assertEquals("59:59", BrowserPlayerMath.formatTime(3_599_000L));
    }

    @Test
    public void formatTime_handlesHours() {
        assertEquals("1:01:01", BrowserPlayerMath.formatTime(3_661_000L));
    }

    // ===== Seek =====

    @Test
    public void seekDelta_isProportionalAndSigned() {
        // 轨道 1000px、时长 100s：右移 100px（10%）→ +10s
        assertEquals(10_000L, BrowserPlayerMath.seekDeltaMs(100f, 1000, 100_000L));
        assertEquals(-10_000L, BrowserPlayerMath.seekDeltaMs(-100f, 1000, 100_000L));
    }

    @Test
    public void seekDelta_capsSingleDragAtHalfDuration() {
        // 拖满整屏也最多跳 50%
        assertEquals(50_000L, BrowserPlayerMath.seekDeltaMs(10_000f, 1000, 100_000L));
        assertEquals(-50_000L, BrowserPlayerMath.seekDeltaMs(-10_000f, 1000, 100_000L));
    }

    @Test
    public void seekDelta_isSafeOnDegenerateInput() {
        assertEquals(0L, BrowserPlayerMath.seekDeltaMs(100f, 0, 100_000L));
        assertEquals(0L, BrowserPlayerMath.seekDeltaMs(100f, 1000, 0L));
    }

    @Test
    public void clampSeek_staysWithinBounds() {
        assertEquals(0L, BrowserPlayerMath.clampSeek(-5_000L, 100_000L));
        assertEquals(100_000L, BrowserPlayerMath.clampSeek(500_000L, 100_000L));
        assertEquals(50_000L, BrowserPlayerMath.clampSeek(50_000L, 100_000L));
    }

    @Test
    public void clampSeek_returnsZeroForLiveStreams() {
        assertEquals(0L, BrowserPlayerMath.clampSeek(30_000L, 0L));
    }

    // ===== 亮度/音量 =====

    @Test
    public void verticalDelta_upIsPositiveAndClamped() {
        assertTrue(BrowserPlayerMath.verticalDelta(-50f, 500) > 0f);
        assertTrue(BrowserPlayerMath.verticalDelta(50f, 500) < 0f);
        assertEquals(0.5f, BrowserPlayerMath.verticalDelta(-250f, 500), EPS);
        assertEquals(0.5f, BrowserPlayerMath.verticalDelta(-5000f, 500), EPS);
    }

    // ===== 倍速档位 =====

    @Test
    public void nextSpeed_rotatesThroughLadder() {
        assertEquals(1.0f, BrowserPlayerMath.nextSpeed(0.75f, BrowserPlayerMath.SPEED_LADDER), EPS);
        assertEquals(1.5f, BrowserPlayerMath.nextSpeed(1.25f, BrowserPlayerMath.SPEED_LADDER), EPS);
    }

    @Test
    public void nextSpeed_wrapsAround() {
        float ladderLast = BrowserPlayerMath.SPEED_LADDER[BrowserPlayerMath.SPEED_LADDER.length - 1];
        assertEquals(BrowserPlayerMath.SPEED_LADDER[0],
                BrowserPlayerMath.nextSpeed(ladderLast, BrowserPlayerMath.SPEED_LADDER), EPS);
    }

    @Test
    public void nextSpeed_handlesOffLadderValues() {
        // 1.1 不在档位表上，取第一个大于它的档位 1.25
        assertEquals(1.25f, BrowserPlayerMath.nextSpeed(1.1f, BrowserPlayerMath.SPEED_LADDER), EPS);
    }

    @Test
    public void formatRate_rendersOneDecimal() {
        assertEquals("2.0x", BrowserPlayerMath.formatRate(2.0f));
        assertEquals("1.0x", BrowserPlayerMath.formatRate(1.0f));
        assertEquals("0.5x", BrowserPlayerMath.formatRate(0.5f));
    }
}
