package com.gamecenter.app.ai.local;

import java.util.HashMap;
import java.util.Map;

public final class LocalLlmOutputGuard {

    private LocalLlmOutputGuard() {
    }

    public static String validate(String output) {
        if (output == null || output.trim().isEmpty()) {
            return "本地模型没有返回有效内容，请换一种问法或切换到云端模式。";
        }

        String compact = output.replaceAll("\\s+", "");
        if (compact.length() < 80) {
            return null;
        }

        if (hasLongCharacterRun(compact, 16)) {
            return "本地模型输出出现连续重复字符，已停止展示。请缩短输入、重新提问，或切换到云端模式。";
        }

        if (isDominatedByOneCharacter(compact)) {
            return "本地模型输出信息密度过低，已停止展示。请换一种问法，或切换到云端模式。";
        }

        if (hasRepeatedLine(output)) {
            return "本地模型输出出现重复段落，已停止展示。请重新生成，或切换到云端模式。";
        }

        if (hasRepeatedSegment(compact)) {
            return "本地模型输出出现循环片段，已停止展示。请缩短输入、重新提问，或切换到云端模式。";
        }

        return null;
    }

    private static boolean hasLongCharacterRun(String text, int threshold) {
        int run = 1;
        char previous = text.charAt(0);
        for (int i = 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == previous) {
                run++;
                if (run >= threshold) {
                    return true;
                }
            } else {
                previous = current;
                run = 1;
            }
        }
        return false;
    }

    private static boolean isDominatedByOneCharacter(String text) {
        Map<Character, Integer> counts = new HashMap<>();
        int max = 0;
        int counted = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || isCommonPunctuation(c)) {
                continue;
            }
            counted++;
            int count = counts.getOrDefault(c, 0) + 1;
            counts.put(c, count);
            max = Math.max(max, count);
        }
        return counted >= 80 && max >= counted * 0.55;
    }

    private static boolean hasRepeatedLine(String output) {
        String[] lines = output.split("\\R+");
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            String normalized = line.trim();
            if (normalized.length() < 8) {
                continue;
            }
            int count = counts.getOrDefault(normalized, 0) + 1;
            if (count >= 4) {
                return true;
            }
            counts.put(normalized, count);
        }
        return false;
    }

    private static boolean hasRepeatedSegment(String text) {
        int[] windows = {6, 8, 12, 16, 24};
        for (int window : windows) {
            if (text.length() < window * 8) {
                continue;
            }
            int repeats = 1;
            String previous = text.substring(0, window);
            for (int i = window; i + window <= text.length(); i += window) {
                String current = text.substring(i, i + window);
                if (current.equals(previous)) {
                    repeats++;
                    if (repeats * window >= 64) {
                        return true;
                    }
                } else {
                    previous = current;
                    repeats = 1;
                }
            }
        }
        return false;
    }

    private static boolean isCommonPunctuation(char c) {
        return "，。！？、；：,.!?;:\"'“”‘’（）()[]【】{}<>《》-—_…".indexOf(c) >= 0;
    }
}
