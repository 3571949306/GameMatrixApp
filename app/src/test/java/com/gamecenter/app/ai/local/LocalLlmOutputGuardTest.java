package com.gamecenter.app.ai.local;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class LocalLlmOutputGuardTest {

    @Test
    public void repeatedDigitsAreRejected() {
        String output = "移动通信技术" + repeat("2", 120);

        assertNotNull(LocalLlmOutputGuard.validate(output));
    }

    @Test
    public void repeatedSegmentsAreRejected() {
        String output = repeat("这是一个循环片段", 16);

        assertNotNull(LocalLlmOutputGuard.validate(output));
    }

    @Test
    public void normalShortAnswerIsAccepted() {
        String output = "可以。建议先缩短输入，再重新生成；如果仍然异常，可以切换到云端模式。";

        assertNull(LocalLlmOutputGuard.validate(output));
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
