package com.gamecenter.app.ai.local;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 本地大语言模型输出守卫的单元测试类。
 *
 * 什么是"输出守卫"？当本地AI模型（如Gemma）生成文本时，
 * 有时会出现"退化输出"——即反复重复同样的内容，比如输出120个"2"。
 * 输出守卫的作用就是检测这种异常输出，防止无意义的内容展示给用户。
 *
 * 为什么要测试输出守卫？如果守卫不能正确检测退化输出，
 * 用户可能会看到一大堆重复的无意义文字，严重影响体验。
 *
 * validate方法返回值说明：
 * - 返回null：输出正常，没有检测到退化
 * - 返回非null：检测到退化，返回的是错误描述
 */
public class LocalLlmOutputGuardTest {

    @Test
    public void repeatedDigitsAreRejected() {
        // 测试：包含大量重复数字的输出应被拒绝（检测为退化输出）
        // 这里模拟了一个正常文字后跟120个"2"的情况
        String output = "移动通信技术" + repeat("2", 120);

        assertNotNull(LocalLlmOutputGuard.validate(output));
    }

    @Test
    public void repeatedSegmentsAreRejected() {
        // 测试：包含大量重复片段的输出应被拒绝
        // 这里模拟了"这是一个循环片段"重复16次的情况
        String output = repeat("这是一个循环片段", 16);

        assertNotNull(LocalLlmOutputGuard.validate(output));
    }

    @Test
    public void normalShortAnswerIsAccepted() {
        // 测试：正常的短回答应通过验证（validate返回null表示正常）
        String output = "可以。建议先缩短输入，再重新生成；如果仍然异常，可以切换到云端模式。";

        assertNull(LocalLlmOutputGuard.validate(output));
    }

    // 辅助方法：将一段文字重复指定次数，用于构造测试数据
    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
