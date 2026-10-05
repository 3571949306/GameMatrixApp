package com.gamecenter.app.games.coin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 金币钱包（Android 门面层）。
 *
 * <p>金币经济宿主侧唯一入口：把纯 Java 的 {@link CoinLedger}（真源账本）、
 * {@link EarnRuleEngine}（赚取规则）与 {@link TitleCatalog}（称号商品）组合起来，
 * 并经 SharedPreferences 持久化。余额与流水的持久化真源在 SP（"coin_wallet"），
 * 每次变更先从 SP 重建账本、操作成功后整体写回。</p>
 *
 * <p>流水采用行式存储：每行 {@code timestamp|delta|reason|dateKey|balanceAfter}，
 * 行间以 \n 分隔，按时间正序（旧→新）追加，持久化时最多保留最近 60 行（裁掉最旧）。</p>
 *
 * <p>本类无任何 UI 依赖（只依赖 Context + SP），便于将来补测试；
 * 流水解析对坏行做防御处理（丢弃并留日志、不抛异常），
 * 因为 {@code GameUsageStore.recordWin/recordLoss} 是全部游戏的对局终点，必须稳定。</p>
 */
public class CoinWallet {

    private static final String TAG = "CoinWallet";
    private static final String PREFS_NAME = "coin_wallet";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_OWNED_TITLES = "owned_titles";
    private static final String KEY_EQUIPPED_TITLE = "equipped_title";
    /** 持久化时最多保留的流水行数，超出裁掉最旧的。 */
    private static final int MAX_ENTRIES = 60;

    private final SharedPreferences prefs;
    private final EarnRuleEngine engine = new EarnRuleEngine();

    /**
     * @param context 任意 Context，内部取 applicationContext，无持有泄漏
     */
    public CoinWallet(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 当前金币余额。 */
    public long balance() {
        return prefs.getLong(KEY_BALANCE, 0L);
    }

    /**
     * 对局结束入账：胜利按连胜天数加成（10..20 金币），失败得安慰奖励 3 金币；
     * 奖励经每日上限（200）裁剪，实得为 0（今日已达上限）时直接不入账。
     *
     * @param win        本局是否获胜
     * @param streakDays 当前每日活跃连胜天数（仅作胜局加成参考，宁缺勿错）
     * @return 实际入账金币数（0 = 今日已达上限或入账被拒）
     */
    public int recordGameResult(boolean win, int streakDays) {
        String today = todayDateKey();
        CoinLedger ledger = loadLedger();
        int reward = win ? engine.winReward(streakDays) : engine.lossReward();
        int earned = engine.clampToCap(reward, ledger.earnedOnDate(today));
        if (earned <= 0) {
            return 0;
        }
        String reason = win ? "对局胜利" : "对局完成";
        if (ledger.apply(earned, reason, today) == null) {
            return 0;
        }
        persist(ledger);
        return earned;
    }

    /**
     * 每日挑战完成奖励入账：固定奖励（{@code EarnRuleEngine.CHALLENGE_REWARD}）
     * 经每日上限裁剪，实得为 0（今日已达上限）时直接不入账。
     * 与 {@link #recordGameResult} 同构：SP 重建账本 → 裁剪 → 入账 → 持久化。
     *
     * @return 实际入账金币数（0 = 今日已达上限或入账被拒）
     */
    public int grantChallengeReward() {
        String today = todayDateKey();
        CoinLedger ledger = loadLedger();
        int reward = engine.challengeReward();
        int earned = engine.clampToCap(reward, ledger.earnedOnDate(today));
        if (earned <= 0) {
            return 0;
        }
        if (ledger.apply(earned, "每日挑战完成", today) == null) {
            return 0;
        }
        persist(ledger);
        return earned;
    }

    /**
     * 通用金币授予（战役章节奖励等非对局奖励）。与对局奖励共用每日上限（clampToCap）。
     *
     * @param amount 申请授予的金币数
     * @param reason 入账原因（写入流水；null/空串会被账本拒绝）
     * @return 实得金币（今日已达上限返回 0）
     */
    public int grantBonus(int amount, String reason) {
        if (amount <= 0) {
            return 0;
        }
        String today = todayDateKey();
        CoinLedger ledger = loadLedger();
        int earned = engine.clampToCap(amount, ledger.earnedOnDate(today));
        if (earned <= 0) {
            return 0;
        }
        if (ledger.apply(earned, reason, today) == null) {
            return 0;
        }
        persist(ledger);
        return earned;
    }

    /**
     * 消费金币（如兑换称号）。
     *
     * @param amount 消费金额（须 &gt; 0）
     * @param reason 消费原因（写入流水；null/空串会被账本拒绝）
     * @return true=扣款成功并已持久化；false=金额非法/原因为空/余额不足
     */
    public boolean spend(int amount, String reason) {
        if (amount <= 0) {
            return false;
        }
        CoinLedger ledger = loadLedger();
        if (ledger.apply(-amount, reason, todayDateKey()) == null) {
            return false;
        }
        persist(ledger);
        return true;
    }

    /**
     * 最近流水（最新在前）。
     *
     * @param limit 最多返回条数，&lt;= 0 时返回空列表
     */
    public List<CoinLedger.Entry> recentEntries(int limit) {
        List<CoinLedger.Entry> all = loadLedger().entries();
        List<CoinLedger.Entry> result = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && result.size() < limit; i--) {
            result.add(all.get(i));
        }
        return result;
    }

    /** 今日已赚取金币总额（与每日上限 200 同一口径，仅统计正向流水）。 */
    public long earnedToday() {
        return loadLedger().earnedOnDate(todayDateKey());
    }

    /**
     * 兑换称号：扣款成功后加入拥有列表并自动佩戴。
     *
     * @return false=称号不存在/已拥有/为成就解锁型/余额不足
     */
    public boolean buyTitle(int titleId) {
        TitleCatalog.Title title = TitleCatalog.find(titleId);
        if (title == null || isOwned(titleId)) {
            return false;
        }
        // 成就解锁型称号不能用金币购买（cost 恒 0），只能走 grantAchievementTitle 免费授予
        if (title.requiredAchievements > 0) {
            return false;
        }
        if (!spend(title.cost, "兑换称号·" + title.name)) {
            return false;
        }
        List<Integer> owned = readOwnedTitleIds();
        owned.add(titleId);
        prefs.edit()
                .putString(KEY_OWNED_TITLES, joinIds(owned))
                .putInt(KEY_EQUIPPED_TITLE, titleId)
                .apply();
        return true;
    }

    /**
     * 授予成就解锁型称号：称号在 {@link TitleCatalog#achievementTitles()} 内且未拥有时，
     * 免费加入拥有列表（不消费金币、不自动佩戴）。
     *
     * @return true=本次授予成功；false=非成就型称号/已拥有/称号不存在
     */
    public boolean grantAchievementTitle(int titleId) {
        TitleCatalog.Title title = TitleCatalog.find(titleId);
        if (title == null || title.requiredAchievements <= 0 || isOwned(titleId)) {
            return false;
        }
        List<Integer> owned = readOwnedTitleIds();
        owned.add(titleId);
        prefs.edit().putString(KEY_OWNED_TITLES, joinIds(owned)).apply();
        return true;
    }

    /** 是否已拥有某称号。 */
    public boolean isOwned(int titleId) {
        return readOwnedTitleIds().contains(titleId);
    }

    /** 当前佩戴的称号 id；0 = 未佩戴（equipped 不在拥有列表时视为 0，防脏数据）。 */
    public int equippedTitleId() {
        int equipped = prefs.getInt(KEY_EQUIPPED_TITLE, 0);
        return equipped != 0 && isOwned(equipped) ? equipped : 0;
    }

    /**
     * 佩戴称号：须已拥有，否则忽略（不抛异常、不改状态）。
     */
    public void equipTitle(int titleId) {
        if (!isOwned(titleId)) {
            return;
        }
        prefs.edit().putInt(KEY_EQUIPPED_TITLE, titleId).apply();
    }

    /** 当前佩戴的称号名；未佩戴（或称号已不存在）返回空串。 */
    public String equippedTitleName() {
        int equipped = equippedTitleId();
        if (equipped == 0) {
            return "";
        }
        TitleCatalog.Title title = TitleCatalog.find(equipped);
        return title != null ? title.name : "";
    }

    // ==================== 持久化（内部） ====================

    /** 从 SP 重建账本（余额 + 全部流水）。 */
    private CoinLedger loadLedger() {
        List<CoinLedger.Entry> entries = new ArrayList<>();
        String stored = prefs.getString(KEY_ENTRIES, "");
        if (stored != null && !stored.isEmpty()) {
            for (String line : stored.split("\n")) {
                CoinLedger.Entry entry = parseEntry(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return new CoinLedger(prefs.getLong(KEY_BALANCE, 0L), entries);
    }

    /** 把账本整体写回 SP（流水裁剪到最近 60 行，裁掉最旧的）。 */
    private void persist(CoinLedger ledger) {
        List<CoinLedger.Entry> entries = ledger.entries();
        int from = Math.max(0, entries.size() - MAX_ENTRIES);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < entries.size(); i++) {
            CoinLedger.Entry e = entries.get(i);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(e.timestamp).append('|')
                    .append(e.delta).append('|')
                    .append(sanitize(e.reason)).append('|')
                    .append(e.dateKey).append('|')
                    .append(e.balanceAfter);
        }
        prefs.edit()
                .putLong(KEY_BALANCE, ledger.balance())
                .putString(KEY_ENTRIES, sb.toString())
                .apply();
    }

    /** 解析一行流水；格式非法返回 null（坏行丢弃并留日志，不影响其余数据）。 */
    private static CoinLedger.Entry parseEntry(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5) {
            return null;
        }
        try {
            long timestamp = Long.parseLong(parts[0]);
            int delta = Integer.parseInt(parts[1]);
            String reason = parts[2];
            String dateKey = parts[3];
            long balanceAfter = Long.parseLong(parts[4]);
            if (reason.isEmpty() || dateKey.isEmpty()) {
                return null;
            }
            return new CoinLedger.Entry(timestamp, delta, reason, dateKey, balanceAfter);
        } catch (NumberFormatException e) {
            Log.w(TAG, "丢弃无法解析的金币流水行: " + line, e);
            return null;
        }
    }

    /** 原因文本清洗：剥离行式存储的分隔符，防止破坏行格式。 */
    private static String sanitize(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.replace("|", " ").replace("\n", " ");
    }

    /** 读取已拥有称号 id 列表（逗号分隔存储）。 */
    private List<Integer> readOwnedTitleIds() {
        List<Integer> ids = new ArrayList<>();
        String stored = prefs.getString(KEY_OWNED_TITLES, "");
        if (stored == null || stored.isEmpty()) {
            return ids;
        }
        for (String part : stored.split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                Log.w(TAG, "丢弃无法解析的已拥有称号 id: " + part, e);
            }
        }
        return ids;
    }

    /** id 列表转逗号分隔字符串。 */
    private static String joinIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** 今日日期键（yyyy-MM-dd）；仿 GameUsageStore.todayDateKey 的实现，自持私有方法。 */
    private static String todayDateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
