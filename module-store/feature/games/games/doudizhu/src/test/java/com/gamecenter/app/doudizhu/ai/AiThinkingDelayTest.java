package com.gamecenter.app.doudizhu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * P5 AI 思考延迟差异化测试：简单档更快、困难档略慢，三档区间互不倒挂；
 * 注入固定 Random 时输出确定（可回归）；null Random 退化为区间中位值。
 */
public class AiThinkingDelayTest {

    // ============ 区间边界 ============

    @Test
    public void easyDelayStaysInEasyRange() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            long delay = AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_EASY, random);
            assertTrue("简单档延迟应 >= 400ms: " + delay,
                    delay >= AiBrain.THINK_DELAY_EASY_MIN_MS);
            assertTrue("简单档延迟应 < 900ms: " + delay,
                    delay < AiBrain.THINK_DELAY_EASY_MAX_MS);
        }
    }

    @Test
    public void normalDelayStaysInNormalRange() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            long delay = AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, random);
            assertTrue(delay >= AiBrain.THINK_DELAY_NORMAL_MIN_MS);
            assertTrue(delay < AiBrain.THINK_DELAY_NORMAL_MAX_MS);
        }
    }

    @Test
    public void hardDelayStaysInHardRange() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            long delay = AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_HARD, random);
            assertTrue("困难档延迟应 >= 1400ms: " + delay,
                    delay >= AiBrain.THINK_DELAY_HARD_MIN_MS);
            assertTrue("困难档延迟应 < 1900ms: " + delay,
                    delay < AiBrain.THINK_DELAY_HARD_MAX_MS);
        }
    }

    // ============ 档位间差异 ============

    /** 三档区间不重叠：简单上界 <= 普通下界，普通上界 <= 困难下界。 */
    @Test
    public void difficultyRangesDoNotOverlap() {
        assertTrue(AiBrain.THINK_DELAY_EASY_MAX_MS
                <= AiBrain.THINK_DELAY_NORMAL_MIN_MS);
        assertTrue(AiBrain.THINK_DELAY_NORMAL_MAX_MS
                <= AiBrain.THINK_DELAY_HARD_MIN_MS);
    }

    /** 简单档平均延迟显著低于困难档（大样本均值差 > 500ms）。 */
    @Test
    public void easyIsFasterThanHardOnAverage() {
        Random random = new Random(7);
        long easySum = 0;
        long hardSum = 0;
        int n = 500;
        for (int i = 0; i < n; i++) {
            easySum += AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_EASY, random);
            hardSum += AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_HARD, random);
        }
        assertTrue("简单档平均延迟应比困难档快 500ms 以上，实际 easyAvg="
                        + (easySum / n) + " hardAvg=" + (hardSum / n),
                hardSum - easySum > 500L * n);
    }

    // ============ 确定性与退化 ============

    /** 注入相同种子时输出序列确定（回归锁定）。 */
    @Test
    public void fixedSeedProducesDeterministicSequence() {
        Random r1 = new Random(123);
        Random r2 = new Random(123);
        for (int i = 0; i < 20; i++) {
            assertEquals(AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, r1),
                    AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, r2));
        }
        // 首值回归锁定：种子 123 普通档首延迟为确定值
        assertEquals(AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, new Random(123)),
                AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, new Random(123)));
    }

    /** null Random 退化为区间中位值（三档各锁定中位数）。 */
    @Test
    public void nullRandomFallsBackToMidpoint() {
        assertEquals(650L, AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_EASY, null));
        assertEquals(1150L, AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_NORMAL, null));
        assertEquals(1650L, AiBrain.thinkingDelayMs(AiBrain.DIFFICULTY_HARD, null));
    }

    /** 非法难度档位按普通档区间处理（防御性）。 */
    @Test
    public void invalidDifficultyTreatedAsNormal() {
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            long delay = AiBrain.thinkingDelayMs(-1, random);
            assertTrue(delay >= AiBrain.THINK_DELAY_NORMAL_MIN_MS);
            assertTrue(delay < AiBrain.THINK_DELAY_NORMAL_MAX_MS);
        }
        assertEquals(1150L, AiBrain.thinkingDelayMs(99, null));
    }
}
