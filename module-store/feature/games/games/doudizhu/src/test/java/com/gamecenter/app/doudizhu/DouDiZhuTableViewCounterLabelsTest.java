package com.gamecenter.app.doudizhu;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P6 复查守卫：记牌器标签构建的列序与来源。
 *
 * <p>drawCardCounter 原先每帧 getString + 新建标签数组（热路径分配），
 * P6 已改为字段缓存（cardCounterLabels）并由 {@link DouDiZhuTableView#buildCardCounterLabels}
 * 构建。本测试锁定列序与 cardCounterCounts 索引一一对应
 * （0-12 = 3~K、A、2，13 = 小王，14 = 大王），防止缓存重构时错位。</p>
 */
public class DouDiZhuTableViewCounterLabelsTest {

    @Test
    public void labelsAlignWithCardCounterIndices() {
        String[] labels = DouDiZhuTableView.buildCardCounterLabels("小王", "大王");
        assertEquals(15, labels.length);
        assertEquals("3", labels[0]);
        assertEquals("10", labels[7]);
        assertEquals("J", labels[8]);
        assertEquals("K", labels[10]);
        assertEquals("A", labels[11]);
        assertEquals("2", labels[12]);
        assertEquals("小王", labels[13]);
        assertEquals("大王", labels[14]);
    }

    @Test
    public void jokerLabelsComeFromInjectedStrings() {
        // 王牌标签必须来自外部传入（本地化资源 getString），不得硬编码中文
        String[] labels = DouDiZhuTableView.buildCardCounterLabels("X", "Y");
        assertEquals("X", labels[13]);
        assertEquals("Y", labels[14]);
    }
}
