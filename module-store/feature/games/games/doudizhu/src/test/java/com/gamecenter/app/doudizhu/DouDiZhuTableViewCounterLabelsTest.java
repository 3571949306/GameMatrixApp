package com.gamecenter.app.doudizhu;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P6 复查守卫：记牌器标签构建的列序与来源。
 *
 * <p>锁定记牌器列序及 AI 徽章文案缓存，防止缓存重构时错位或重新回到绘制帧格式化。</p>
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

    @Test
    public void badgeTextUsesLocalizedPlaceholderAndClampsNegativeCount() {
        assertEquals("剩 3", DouDiZhuTableView.buildCardCountBadgeText("剩 %1$d", 3));
        assertEquals("剩 0", DouDiZhuTableView.buildCardCountBadgeText("剩 %1$d", -1));
    }

    @Test
    public void badgeTextHandlesEmptyFormat() {
        assertEquals("", DouDiZhuTableView.buildCardCountBadgeText(null, 3));
        assertEquals("", DouDiZhuTableView.buildCardCountBadgeText("", 3));
    }

    @Test
    public void changedCountUpdatesTextUsedByRedBadgePath() {
        String format = "剩 %1$d";
        String firstText = DouDiZhuTableView.buildCardCountBadgeText(format, 7);

        // setAICardCounts 通过此文案来源函数更新字段，drawRedCardCountBadge 随后读取该字段。
        String updatedText = DouDiZhuTableView.updateCardCountBadgeText(
                firstText, 7, 3, format);
        assertEquals("剩 3", updatedText);
        assertEquals("剩 3", DouDiZhuTableView.updateCardCountBadgeText(
                updatedText, 3, 3, "left %1$d"));
    }
}
