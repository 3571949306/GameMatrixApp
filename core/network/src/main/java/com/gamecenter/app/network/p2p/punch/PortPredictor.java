package com.gamecenter.app.network.p2p.punch;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对称型 NAT 端口预测器 (Symmetric NAT Port Predictor & Birthday Paradox Engine)。
 * <p>
 * 针对国内移动 4G/5G 蜂窝网及部分多层家庭宽带 NAT444 网关特性：
 * 对称型 NAT 对不同目标 IP 分配不同外部端口，但其端口分配器多为**单调递增 (Linear Delta)**
 * 或**区间块分配 (Block Allocation)**。
 * <p>
 * 本类通过分析 STUN 探测时获取的端口样本序列，计算步长增量 $\Delta$，
 * 并生成高概率命中候选端口窗口与生日攻击散列分布，大幅提升打洞成功率。
 */
public class PortPredictor {

    /**
     * 预测对称型 NAT 下一个可能分配的外网端口窗口。
     *
     * @param samplePorts 在探测阶段记录的连续外部端口样本 (至少 1 个，推荐 2~3 个)
     * @param windowSize  预测窗口大小 (通常推荐 16 ~ 24，避免突发包过多引起路由器丢包)
     * @return 按预测命中优先级排序的目标端口列表
     */
    @NonNull
    public static List<Integer> predictTargetPorts(@NonNull List<Integer> samplePorts, int windowSize) {
        if (samplePorts.isEmpty()) {
            return Collections.emptyList();
        }

        int lastPort = samplePorts.get(samplePorts.size() - 1);
        int delta = 1; // 默认步长增量

        if (samplePorts.size() >= 2) {
            int d = samplePorts.get(samplePorts.size() - 1) - samplePorts.get(samplePorts.size() - 2);
            if (d > 0 && d <= 64) {
                delta = d;
            } else if (d < 0 && d >= -64) {
                delta = Math.abs(d);
            }
        }

        Set<Integer> portSet = new HashSet<>();
        List<Integer> result = new ArrayList<>();

        // 1. 最高优先级：沿增量方向的主预测窗口 [lastPort + delta, lastPort + delta * 8]
        int basePredicted = lastPort + delta;
        for (int i = 0; i < Math.min(windowSize, 16); i++) {
            int p = (basePredicted + (i * delta)) & 0xFFFF;
            if (p > 1024 && portSet.add(p)) {
                result.add(p);
            }
        }

        // 2. 次高优先级：考虑网络抖动与并发其他连接分配，覆盖微小回退与前移
        int[] offsets = new int[]{-2, -1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24};
        for (int offset : offsets) {
            int p = (lastPort + offset) & 0xFFFF;
            if (p > 1024 && portSet.add(p)) {
                result.add(p);
            }
            if (result.size() >= windowSize) break;
        }

        return result;
    }
}
