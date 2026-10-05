package com.gamecenter.app.games.coin;

import java.util.ArrayList;
import java.util.List;

/**
 * 金币真源账本（纯 Java，无 Android 依赖）。
 *
 * <p>本类是金币余额与流水的唯一真相源：宿主侧 Android 门面负责持久化，
 * 重建时把保存的余额与流水（按时间正序，旧→新）经构造函数灌回。
 * 所有变更必须经 {@link #apply(int, String, String)} 入账；任何被拒绝的调用
 * （参数非法、余额不足）都不产生任何副作用——余额不动、流水不追加。
 */
public class CoinLedger {

    /** 一条不可变的金币流水记录。 */
    public static final class Entry {
        /** 记账时间戳（System.currentTimeMillis()）。 */
        public final long timestamp;
        /** 金币变动量：正=赚取，负=消费。 */
        public final int delta;
        /** 变动原因（业务描述，如"对局胜利"）。 */
        public final String reason;
        /** 所属日期键（如 2026-09-23），用于按日统计。 */
        public final String dateKey;
        /** 记账后余额。 */
        public final long balanceAfter;

        public Entry(long timestamp, int delta, String reason, String dateKey, long balanceAfter) {
            this.timestamp = timestamp;
            this.delta = delta;
            this.reason = reason;
            this.dateKey = dateKey;
            this.balanceAfter = balanceAfter;
        }
    }

    private long balance;
    private final List<Entry> entries = new ArrayList<>();

    /**
     * @param balance 初始余额（应为 &gt;= 0，以传入值为准，不由流水推导）
     * @param entries 历史流水，按时间正序（旧→新）灌入；允许 null
     */
    public CoinLedger(long balance, List<Entry> entries) {
        this.balance = balance;
        if (entries != null) {
            this.entries.addAll(entries);
        }
    }

    /** 当前余额。 */
    public long balance() {
        return balance;
    }

    /**
     * 记一笔账：delta 正=赚取、负=消费。
     *
     * <p>拒绝规则（拒绝时无任何副作用）：
     * <ul>
     *   <li>delta == 0；</li>
     *   <li>reason 或 dateKey 为 null / 空串；</li>
     *   <li>balance + delta &lt; 0（余额不足）。</li>
     * </ul>
     *
     * @return 成功时返回新追加的流水；被拒绝时返回 null
     */
    public Entry apply(int delta, String reason, String dateKey) {
        if (delta == 0 || reason == null || reason.isEmpty()
                || dateKey == null || dateKey.isEmpty()) {
            return null;
        }
        if (balance + delta < 0) {
            return null;
        }
        balance += delta;
        Entry entry = new Entry(System.currentTimeMillis(), delta, reason, dateKey, balance);
        entries.add(entry);
        return entry;
    }

    /** 流水列表副本（防御性拷贝，外部修改不影响账本内部状态）。 */
    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** 统计某日全部赚取流水（仅 delta &gt; 0）之和，用于每日赚取上限计算。 */
    public long earnedOnDate(String dateKey) {
        long sum = 0;
        for (Entry e : entries) {
            if (e.delta > 0 && e.dateKey.equals(dateKey)) {
                sum += e.delta;
            }
        }
        return sum;
    }
}
