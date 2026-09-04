package com.gamecenter.app.doudizhu;

/**
 * 特效代际计数器（P5 审查修复 A）：解决"取消旧动画触发 {@code onAnimationEnd}
 * → 排延迟清除 → 陈旧回调抹掉新特效"的时序级联。
 *
 * <p>纯 JVM 实现（不依赖任何 Android 类），便于单测覆盖。核心思想：每次
 * {@link #begin()} 产生新代际（新特效入场、显式清除各算一次），动画结束回调
 * 与延迟清除回调都携带自己所属的代际号，执行前用 {@link #isCurrent(int)}
 * 校验——代际不匹配（已被更新的特效取代或清除）的回调直接失效，不再级联。</p>
 *
 * <p>选择代际方案而非 {@code animation.isCanceled()} 判断的理由：被 cancel 的
 * 旧动画回调虽然能靠 isCanceled 拦住，但"自然结束却已过时"的回调拦不住——
 * 例：炸弹 300ms 播完自然结束排了 500ms 清除，此时新特效立刻入场，500ms 后
 * 陈旧清除回调执行时旧动画并非 canceled，isCanceled 判断失效，正在播放的
 * 新特效被抹掉。代际校验在"回调执行时刻"统一判定归属，两种时序都覆盖。</p>
 */
public final class EffectGeneration {

    private int generation = 0;

    /** 开启新一轮归属（新特效入场或显式清除），返回本次的代际号（单调递增）。 */
    public synchronized int begin() {
        return ++generation;
    }

    /**
     * 校验回调携带的代际号是否仍是当前归属。
     *
     * @param generation 回调创建时记录的代际号
     * @return true 表示仍是当前代际，回调应继续执行
     */
    public synchronized boolean isCurrent(int generation) {
        return generation == this.generation;
    }
}
