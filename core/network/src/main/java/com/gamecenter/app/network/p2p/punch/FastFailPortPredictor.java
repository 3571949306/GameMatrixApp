package com.gamecenter.app.network.p2p.punch;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 300ms 快速熔断对称型 NAT 端口预测器 (Fast-Fail Port Predictor)。
 * <p>
 * 规则：
 * <ul>
 *   <li>现代 5G 核心网 (UPF) 全面升级为伪随机端口分配 (PRNG)，死磕端口预测只会耗电并拖慢进房。</li>
 *   <li>严格校验：仅当 STUN 样本步长增量 $\Delta \in [1, 8]$ 时才判定为规律递增型，启动 16 包连发突发预测。</li>
 *   <li>否则立即判定为 PRNG 随机对称型，触发 300ms 快速熔断，将打洞权重直接让渡给公网 IPv6 或 BGP 中继。</li>
 * </ul>
 */
public class FastFailPortPredictor {

    public static final int MAX_ALLOWABLE_DELTA = 8;
    public static final int BURST_WINDOW_SIZE = 16;
    public static final long FAIL_FAST_TIMEOUT_MS = 300;

    public static class PredictionResult {
        private final boolean isPredictable;
        private final int estimatedDelta;
        private final List<Integer> targetPorts;

        public PredictionResult(boolean isPredictable, int estimatedDelta, @NonNull List<Integer> targetPorts) {
            this.isPredictable = isPredictable;
            this.estimatedDelta = estimatedDelta;
            this.targetPorts = targetPorts;
        }

        public boolean isPredictable() {
            return isPredictable;
        }

        public int getEstimatedDelta() {
            return estimatedDelta;
        }

        @NonNull
        public List<Integer> getTargetPorts() {
            return targetPorts;
        }
    }

    /**
     * 评估样本序列并生成预测端口列表。
     */
    @NonNull
    public static PredictionResult evaluateAndPredict(@NonNull List<Integer> samplePorts) {
        if (samplePorts.size() < 2) {
            // 仅 1 个样本时无法判断规律，按默认步长 1 探测但不承诺高成功率
            int p = samplePorts.isEmpty() ? 0 : samplePorts.get(0);
            return new PredictionResult(false, 0, p > 0 ? Collections.singletonList(p + 1) : Collections.emptyList());
        }

        int last = samplePorts.get(samplePorts.size() - 1);
        int prev = samplePorts.get(samplePorts.size() - 2);
        int delta = last - prev;

        // 若步长不在 [1, 8] 区间（如差值几十甚至成千上万），必定为 PRNG 伪随机分配
        if (delta < 1 || delta > MAX_ALLOWABLE_DELTA) {
            return new PredictionResult(false, delta, Collections.emptyList());
        }

        // 规律线性分配：生成高概率命中窗口 [last + delta, last + 16 * delta]
        List<Integer> ports = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();

        for (int i = 1; i <= BURST_WINDOW_SIZE; i++) {
            int target = (last + (i * delta)) & 0xFFFF;
            if (target > 1024 && unique.add(target)) {
                ports.add(target);
            }
        }

        return new PredictionResult(true, delta, ports);
    }
}
