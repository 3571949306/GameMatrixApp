package com.gamecenter.app.doudizhu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * P5 审查修复 A：特效代际计数器契约测试。
 *
 * <p>锁定"入场/清除递增代际、陈旧回调失效"的核心语义——EffectsView 的
 * cancel → onAnimationEnd → 延迟清除级联依赖该语义截断。纯 JVM 可测，
 * 动画时序本身仍靠编译 + 复查 + 真机冒烟兜底。</p>
 */
public class EffectGenerationTest {

    @Test
    public void beginReturnsMonotonicallyIncreasingGenerations() {
        EffectGeneration gen = new EffectGeneration();
        int first = gen.begin();
        int second = gen.begin();
        int third = gen.begin();
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        assertTrue(third > second);
        assertTrue(second > first);
    }

    @Test
    public void currentGenerationValidatesTrue() {
        EffectGeneration gen = new EffectGeneration();
        int current = gen.begin();
        assertTrue(gen.isCurrent(current));
    }

    @Test
    public void staleGenerationAfterNewBeginIsRejected() {
        // 复现级联场景：炸弹特效入场（gen1）→ 播完自然结束排了延迟清除 →
        // 用户立刻开始新特效（gen2）→ 陈旧的 gen1 清除回调执行时必须失效
        EffectGeneration gen = new EffectGeneration();
        int bombGeneration = gen.begin();
        assertTrue(gen.isCurrent(bombGeneration));

        int newGeneration = gen.begin(); // 新特效入场
        assertFalse("陈旧特效的延迟清除回调不得再清除新特效",
                gen.isCurrent(bombGeneration));
        assertTrue(gen.isCurrent(newGeneration));
    }

    @Test
    public void staleGenerationAfterCancelThenBeginIsRejected() {
        // 复现 cancel 级联场景：旧动画被 clearEffect cancel（begin 使其失效），
        // 其 onAnimationEnd 携带旧代际执行时必须被拦下，不得再排清除
        EffectGeneration gen = new EffectGeneration();
        int oldGeneration = gen.begin();
        gen.begin(); // clearEffect 的失效动作
        assertFalse(gen.isCurrent(oldGeneration));
    }

    @Test
    public void freshInstanceRejectsNonZeroGenerations() {
        // 新实例初始代际为 0（占位值）；EffectsView 中任何回调都诞生于
        // begin() 之后，不会携带 0，故非 0 代际在未开场时一律拒绝
        EffectGeneration gen = new EffectGeneration();
        assertFalse(gen.isCurrent(1));
        assertFalse(gen.isCurrent(-1));
    }

    @Test
    public void distinctInstancesAreIndependent() {
        // A 实例推进不得影响 B 实例的归属判定
        EffectGeneration genA = new EffectGeneration();
        EffectGeneration genB = new EffectGeneration();
        int a1 = genA.begin();
        int b1 = genB.begin();
        genA.begin(); // A 前进
        assertFalse(genA.isCurrent(a1));
        assertTrue("A 的推进不影响 B 的归属", genB.isCurrent(b1));
    }
}
