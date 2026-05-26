package com.gamecenter.app.ai.local;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地 LLM 输出守卫 — 对本地模型推理结果进行质量与安全校验。
 * <p>
 * 你可以把这个类想象成一个"质检员"：
 * 本地小模型（如 Gemma）在手机上运行时，有时会"犯糊涂"——
 * 比如不停地重复同一句话、输出一堆乱码、或者绕圈子说废话。
 * 质检员的工作就是检查这些情况，一旦发现问题就拦截，不让劣质输出展示给用户。
 * <p>
 * 本地端侧模型（如 Gemma）在资源受限的移动设备上运行时，容易出现输出退化问题，
 * 包括：连续重复字符、信息密度过低、段落循环等。此类问题在端侧推理中较为常见，
 * 通常由上下文过长、采样参数不当或模型能力不足引起。
 * <p>
 * 设计决策：
 * <ul>
 *   <li>采用纯静态工具类设计，无状态、无需实例化，方便在任意位置调用；</li>
 *   <li>校验按严重程度从高到低排列：空输出 → 连续重复 → 信息密度 → 段落重复 → 片段循环；</li>
 *   <li>返回 null 表示通过校验，返回非空字符串表示拒绝原因（可直接展示给用户）；</li>
 *   <li>短文本（压缩后不足 80 字符）跳过大部分校验，避免误判。</li>
 * </ul>
 */
public final class LocalLlmOutputGuard {

    // 私有构造方法，防止外部创建实例（因为所有方法都是静态的，不需要实例）
    private LocalLlmOutputGuard() {
    }

    /**
     * 对本地模型的输出文本进行质量校验。
     * <p>
     * 校验流程依次为（就像工厂质检流水线，一道一道检查）：
     * <ol>
     *   <li>空值或空白检查</li>
     *   <li>短文本豁免（压缩后不足 80 字符则跳过后续检查）</li>
     *   <li>连续重复字符检测</li>
     *   <li>单字符占比过高（信息密度过低）检测</li>
     *   <li>重复行检测</li>
     *   <li>循环片段检测</li>
     * </ol>
     *
     * @param output 本地模型生成的原始输出文本
     * @return null 表示校验通过；非空字符串表示拒绝展示的原因（用户可见提示）
     */
    public static String validate(String output) {
        // 第1关：检查是否为空或全是空白
        if (output == null || output.trim().isEmpty()) {
            return "本地模型没有返回有效内容，请换一种问法或切换到云端模式。";
        }

        // 去除所有空白字符后得到紧凑文本，用于后续统计类校验
        String compact = output.replaceAll("\\s+", "");
        // 短文本豁免：压缩后不足 80 字符时，重复问题概率极低，跳过后续检查避免误判
        if (compact.length() < 80) {
            return null;
        }

        // 第2关：检查是否有同一字符连续出现太多次（如 "aaaaaa"）
        if (hasLongCharacterRun(compact, 16)) {
            return "本地模型输出出现连续重复字符，已停止展示。请缩短输入、重新提问，或切换到云端模式。";
        }

        // 第3关：检查是否某个字符占比过高（如整段文字都是"的"字）
        if (isDominatedByOneCharacter(compact)) {
            return "本地模型输出信息密度过低，已停止展示。请换一种问法，或切换到云端模式。";
        }

        // 第4关：检查是否有重复的段落
        if (hasRepeatedLine(output)) {
            return "本地模型输出出现重复段落，已停止展示。请重新生成，或切换到云端模式。";
        }

        // 第5关：检查是否有循环片段（一段话翻来覆去地说）
        if (hasRepeatedSegment(compact)) {
            return "本地模型输出出现循环片段，已停止展示。请缩短输入、重新提问，或切换到云端模式。";
        }

        return null; // 所有检查通过，输出质量合格
    }

    /**
     * 检测文本中是否存在同一字符连续出现超过阈值的情况。
     * <p>
     * 例如 "aaaaaa"（16 个 a）会被判定为连续重复。这是端侧模型常见的退化模式之一。
     *
     * @param text      已去除空白的紧凑文本
     * @param threshold 连续重复的判定阈值（字符数）
     * @return true 表示存在超过阈值的连续重复字符
     */
    private static boolean hasLongCharacterRun(String text, int threshold) {
        int run = 1;  // 当前连续计数
        char previous = text.charAt(0);
        for (int i = 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == previous) {
                run++;
                if (run >= threshold) {
                    return true;  // 连续重复超过阈值，判定为退化
                }
            } else {
                previous = current;
                run = 1;  // 遇到不同字符，重置计数
            }
        }
        return false;
    }

    /**
     * 检测文本是否被单一字符主导（信息密度过低）。
     * <p>
     * 统计所有非空白、非标点字符的频率，若某个字符占比超过 55% 且总有效字符数
     * 不低于 80，则判定为信息密度过低。这种模式常见于模型输出退化为单一符号
     * 重复的情况（如不断输出"的"字）。
     *
     * @param text 已去除空白的紧凑文本
     * @return true 表示文本被单一字符主导
     */
    private static boolean isDominatedByOneCharacter(String text) {
        Map<Character, Integer> counts = new HashMap<>();
        int max = 0;  // 最高频字符的出现次数
        int counted = 0;  // 有效字符总数（排除标点和空白）
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 跳过空白和中英文常见标点，只统计有实际语义的字符
            if (Character.isWhitespace(c) || isCommonPunctuation(c)) {
                continue;
            }
            counted++;
            int count = counts.getOrDefault(c, 0) + 1;
            counts.put(c, count);
            max = Math.max(max, count);
        }
        // 双重条件：有效字符总数 ≥ 80 且最高频字符占比 ≥ 55%
        return counted >= 80 && max >= counted * 0.55;
    }

    /**
     * 检测原始输出中是否存在重复行。
     * <p>
     * 按换行符拆分后，若某一行（去除首尾空白后长度 ≥ 8）出现 4 次及以上，
     * 则判定为重复段落。这是模型陷入循环输出的典型表现。
     *
     * @param output 原始输出文本（保留换行和空白）
     * @return true 表示存在重复行
     */
    private static boolean hasRepeatedLine(String output) {
        // \R+ 匹配所有 Unicode 换行符序列（包括 \r\n、\r、\n）
        String[] lines = output.split("\\R+");
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            String normalized = line.trim();
            // 过短行不参与重复检测，避免误判短标题、分隔符等
            if (normalized.length() < 8) {
                continue;
            }
            int count = counts.getOrDefault(normalized, 0) + 1;
            if (count >= 4) {
                return true;  // 同一行出现 4 次以上，判定为重复
            }
            counts.put(normalized, count);
        }
        return false;
    }

    /**
     * 检测紧凑文本中是否存在循环片段。
     * <p>
     * 使用多尺度滑动窗口（6、8、12、16、24 字符宽度），在文本上以窗口大小为步长
     * 逐段比较相邻片段。若连续重复片段的总长度 ≥ 64 字符，则判定为循环输出。
     * <p>
     * 多尺度设计的原因：模型退化的循环单元长度不固定，短窗口捕捉细粒度循环，
     * 长窗口捕捉大段重复，兼顾检测灵敏度与误判率。
     * 就像用不同大小的筛子来筛沙子，粗筛子找大块，细筛子找小块。
     *
     * @param text 已去除空白的紧凑文本
     * @return true 表示存在循环片段
     */
    private static boolean hasRepeatedSegment(String text) {
        // 多尺度窗口：从小到大覆盖不同粒度的循环模式
        int[] windows = {6, 8, 12, 16, 24};
        for (int window : windows) {
            // 文本长度不足窗口的 8 倍时，该窗口尺度的检测无意义
            if (text.length() < window * 8) {
                continue;
            }
            int repeats = 1;  // 连续重复次数
            String previous = text.substring(0, window);
            for (int i = window; i + window <= text.length(); i += window) {
                String current = text.substring(i, i + window);
                if (current.equals(previous)) {
                    repeats++;
                    // 循环片段总长度 ≥ 64 字符即判定为循环
                    if (repeats * window >= 64) {
                        return true;
                    }
                } else {
                    previous = current;
                    repeats = 1;  // 遇到不同片段，重置计数
                }
            }
        }
        return false;
    }

    /**
     * 判断字符是否为中文或英文常见标点符号。
     * <p>
     * 在信息密度检测中，标点符号不作为有效语义字符参与统计，
     * 避免标点密集的正常文本被误判为信息密度过低。
     *
     * @param c 待判断的字符
     * @return true 表示该字符是常见标点
     */
    private static boolean isCommonPunctuation(char c) {
        return "，。！？、；：,.!?;:\u201C\u201D\u2018\u2019（）()[]【】{}<>《》-—_…".indexOf(c) >= 0;
    }
}
