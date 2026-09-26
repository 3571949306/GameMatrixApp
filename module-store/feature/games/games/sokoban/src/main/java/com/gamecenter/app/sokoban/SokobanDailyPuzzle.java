package com.gamecenter.app.sokoban;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * 推箱子每日关卡挑战（纯 Java，无 Android 依赖）。
 *
 * <p>Wordle 模式：每天（yyyy-MM-dd 日期 key）由日期种子从固定关卡池
 * 确定性选出当日关卡，全体玩家同关；同日可重复挑战刷新最少步数，
 * 连续天数形成回访理由（见 {@link SokobanDailyRecord}）。</p>
 *
 * <p>关卡池为「内置 15 关 + 战役 9 关」的引用，零新内容设计：
 * 内置关直接引用 {@link SokobanGame#TOTAL_LEVELS} 序号，战役关引用
 * {@link SokobanCampaign} 的关卡码。确定性来自 Java 规范固定的
 * {@code String.hashCode()} 与 {@code java.util.Random} LCG 算法：
 * 同一日期 key 在任何 JVM 上恒选出同一关。</p>
 */
public final class SokobanDailyPuzzle {

    /** 每日关卡引用：内置关或战役关（二选一，另一组字段为 0/null）。 */
    public static final class DailyLevel {
        /** true=内置关（level 1..15），false=战役关。 */
        public final boolean builtin;
        /** 内置关号（builtin 时 1..15，否则 0）。 */
        public final int builtinLevel;
        /** 战役章（战役关时 1..3，否则 0）。 */
        public final int chapter;
        /** 战役关序（战役关时 1..3，否则 0）。 */
        public final int indexInChapter;
        /** 展示名：内置关「第 N 关」；战役关「战役·章名·关名」。 */
        public final String name;
        /** 战役关时为 {@link SokobanLevelCodec} 关卡码；内置关时为 null。 */
        public final String code;

        public DailyLevel(boolean builtin, int builtinLevel, int chapter,
                int indexInChapter, String name, String code) {
            this.builtin = builtin;
            this.builtinLevel = builtinLevel;
            this.chapter = chapter;
            this.indexInChapter = indexInChapter;
            this.name = name;
            this.code = code;
        }
    }

    /** 日期 key 长度（"yyyy-MM-dd" 恰好 10 字符）。 */
    private static final int DATE_KEY_LENGTH = 10;

    /** 每日关卡池：内置 15 条 + 战役 9 条（静态构建一次，引用复用）。 */
    private static final DailyLevel[] POOL = buildPool();

    private SokobanDailyPuzzle() {
    }

    /**
     * 构建关卡池：内置 15 条（引用 {@link SokobanGame} 内置关卡号）+
     * 战役 9 条（遍历 {@link SokobanCampaign#CHAPTERS}，引用关卡码）。
     */
    private static DailyLevel[] buildPool() {
        List<DailyLevel> pool = new ArrayList<>();
        for (int n = 1; n <= SokobanGame.TOTAL_LEVELS; n++) {
            pool.add(new DailyLevel(true, n, 0, 0, "第 " + n + " 关", null));
        }
        for (SokobanCampaign.Chapter ch : SokobanCampaign.CHAPTERS) {
            for (SokobanCampaign.CampaignLevel lv : ch.levels) {
                pool.add(new DailyLevel(false, 0, lv.chapter, lv.indexInChapter,
                        "战役·" + ch.name + "·" + lv.name, lv.code));
            }
        }
        return pool.toArray(new DailyLevel[0]);
    }

    /**
     * 池总大小（15 内置 + 9 战役 = 24）。
     */
    public static int poolSize() {
        return POOL.length;
    }

    /**
     * 日期种子确定性选关：以 dateKey 的 hash 为种子在池内取一个。
     *
     * <p>同一日期 key 任意次调用结果恒定（Wordle 同日同关）；
     * {@code new Random(dateKey.hashCode()).nextInt(poolSize())} 中
     * hashCode 与 Random 算法均为 Java 规范固定，跨 JVM 确定。</p>
     *
     * @param dateKey 日期 key（yyyy-MM-dd）
     * @return 当日关卡引用；dateKey 为 null/空/格式非法（长度≠10）时返回 null
     */
    public static DailyLevel forDate(String dateKey) {
        if (dateKey == null || dateKey.length() != DATE_KEY_LENGTH) {
            return null;
        }
        return POOL[new Random(dateKey.hashCode()).nextInt(POOL.length)];
    }

    /**
     * 工具：从 todayKey 回退 daysBack 天的日期 key（纯日历运算）。
     *
     * @param todayKey 基准日期 key（yyyy-MM-dd）
     * @param daysBack 回退天数（0 即当天，1 即昨天）
     * @return 回退后的日期 key；todayKey 非法或 daysBack 为负时返回 null
     */
    public static String dateKeyDaysBack(String todayKey, int daysBack) {
        if (todayKey == null || todayKey.length() != DATE_KEY_LENGTH || daysBack < 0) {
            return null;
        }
        Date base;
        try {
            base = parseDateKey(todayKey);
        } catch (ParseException e) {
            // 棘轮门禁：catch 不允许静默吞异常；纯 Java 无 android.util.Log，以标准错误留痕。
            System.err.println("SokobanDailyPuzzle: invalid date key " + todayKey + " -> " + e);
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.DAY_OF_MONTH, -daysBack);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    /**
     * 严格解析日期 key：lenient=false 拒绝越界日期（如 2026-02-30），
     * 再以 roundtrip 比对拒绝可被部分解析的伪格式（如 "202609-24x"）。
     */
    private static Date parseDateKey(String dateKey) throws ParseException {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setLenient(false);
        Date d = fmt.parse(dateKey);
        if (!new SimpleDateFormat("yyyy-MM-dd").format(d).equals(dateKey)) {
            throw new ParseException("非 yyyy-MM-dd 格式: " + dateKey, 0);
        }
        return d;
    }
}
