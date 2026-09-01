package com.gamecenter.app.doudizhu.save;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.Rank;
import com.gamecenter.app.doudizhu.model.Suit;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主对局快照（可序列化值对象，纯 Java 可单测）。
 *
 * <p>承载恢复一局所需的全部状态：阶段、回合、地主、叫分、倍数因子、
 * 三座位手牌、底牌、桌面当前一手牌、各座位出牌手数与"不出"标记。</p>
 *
 * <p>序列化格式：{@code |} 分隔的 19 个字段（首字段为格式版本号）；
 * 牌用 {@code 花色代号_牌值符号}（如 {@code s_3}、{@code x_joker_small}），
 * 同一手牌内以 {@code ,} 分隔。手写紧凑格式而非 org.json：模块单测运行在
 * JVM（org.json 为 stub，见 build.gradle 的 returnDefaultValues），手写解析
 * 可全量单测，且格式自描述、无需转义。</p>
 */
public class DoudizhuSnapshot {

    /** 当前序列化格式版本（字段布局变更时递增） */
    public static final int FORMAT_VERSION = 2;

    /** 炸弹/王炸次数上限（真实对局至多约 10 次炸弹；超限视为坏档，防 1<<bombCount 溢出） */
    public static final int MAX_BOMB_COUNT = 20;

    /** 序列化字段总数（含版本号与末尾桌面牌字段） */
    private static final int FIELD_COUNT = 19;

    /** 游戏阶段（对齐 DouDiZhuGameStateManager 状态常量，1=叫分 2=出牌） */
    public int phase;
    /** 当前回合座位 */
    public int currentTurn;
    /** 地主座位，-1 未确定 */
    public int landlordSeat;
    /** 当前最高叫分（0=无人叫） */
    public int highestBid;
    /** 本轮已叫分人数（0-3） */
    public int bidPlacedCount;
    /** 已重新发牌次数 */
    public int redealCount;
    /** 最终叫分倍数（1-3；未确定地主时为 0） */
    public int bidScore;
    /** 炸弹/王炸触发次数 */
    public int bombCount;
    /** 难度档位（0/1/2） */
    public int difficulty;
    /** 对局开始时间戳（毫秒，用于耗时统计） */
    public long startedAtMs;
    /** 各座位累计出牌手数（长度 3，春天判定用） */
    public int[] playCounts;
    /** 各座位"不出"标记（长度 3） */
    public boolean[] passed;
    /** 三座位手牌（长度 3；地主手牌已含底牌） */
    public List<Card>[] hands;
    /** 底牌（3 张） */
    public List<Card> bottomCards;
    /** 桌面当前一手牌（null/空=自由出牌） */
    public List<Card> lastPlayed;
    /** 桌面当前一手的出牌者座位（lastPlayed 为空时无效，-1） */
    public int lastPlayerSeat;

    /**
     * 序列化为紧凑字符串。
     *
     * @return 可持久化的字符串
     * @throws SerializeException 牌列表包含非法牌
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(FORMAT_VERSION).append('|')
                .append(phase).append('|')
                .append(currentTurn).append('|')
                .append(landlordSeat).append('|')
                .append(highestBid).append('|')
                .append(bidPlacedCount).append('|')
                .append(redealCount).append('|')
                .append(bidScore).append('|')
                .append(bombCount).append('|')
                .append(difficulty).append('|')
                .append(startedAtMs).append('|');
        for (int i = 0; i < 3; i++) {
            sb.append(playCounts[i]).append(',');
        }
        sb.append('|');
        for (int i = 0; i < 3; i++) {
            sb.append(passed[i] ? 1 : 0).append(',');
        }
        sb.append('|');
        sb.append(cards(hands[0])).append('|');
        sb.append(cards(hands[1])).append('|');
        sb.append(cards(hands[2])).append('|');
        sb.append(cards(bottomCards)).append('|');
        sb.append(cards(lastPlayed == null ? new ArrayList<Card>() : lastPlayed)).append('|');
        sb.append(lastPlayed == null || lastPlayed.isEmpty() ? -1 : lastPlayerSeat);
        return sb.toString();
    }

    /**
     * 从字符串反序列化。
     *
     * @param data 存档字符串
     * @return 快照对象
     * @throws SerializeException 格式损坏/版本不识别/字段非法
     */
    public static DoudizhuSnapshot deserialize(String data) {
        if (data == null || data.isEmpty()) {
            throw new SerializeException("空存档");
        }
        String[] f = data.split("\\|", -1);
        if (f.length != FIELD_COUNT) {
            throw new SerializeException("字段数不符: " + f.length);
        }
        try {
            DoudizhuSnapshot s = new DoudizhuSnapshot();
            int version = Integer.parseInt(f[0].trim());
            if (version != FORMAT_VERSION) {
                throw new SerializeException("不识别的版本: " + version);
            }
            s.phase = Integer.parseInt(f[1]);
            s.currentTurn = Integer.parseInt(f[2]);
            s.landlordSeat = Integer.parseInt(f[3]);
            s.highestBid = Integer.parseInt(f[4]);
            s.bidPlacedCount = Integer.parseInt(f[5]);
            s.redealCount = Integer.parseInt(f[6]);
            s.bidScore = Integer.parseInt(f[7]);
            s.bombCount = Integer.parseInt(f[8]);
            s.difficulty = Integer.parseInt(f[9]);
            s.startedAtMs = Long.parseLong(f[10]);
            s.playCounts = parseIntArray(f[11], 3);
            s.passed = parseBooleanArray(f[12], 3);
            s.hands = new List[]{parseCards(f[13]), parseCards(f[14]), parseCards(f[15])};
            s.bottomCards = parseCards(f[16]);
            String lastRaw = f[17];
            s.lastPlayed = lastRaw.isEmpty() ? null : parseCards(lastRaw);
            s.lastPlayerSeat = Integer.parseInt(f[18]);
            validateRanges(s);
            return s;
        } catch (SerializeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializeException("解析失败: " + e.getMessage());
        }
    }

    /**
     * 标量字段范围校验（P3 复查修复 B/C）：构造性坏档（如 currentTurn=5、
     * bombCount=9999999）不得流入恢复路径——currentTurn/landlordSeat 越界会致
     * 恢复后 AIOOBE，bombCount 超上限会致 {@code 1<<bombCount} 溢出与
     * registerBomb 循环上千万次。任一非法即抛 {@link SerializeException}，
     * 调用方（canRestore/restoreFromSave）按既有约定走清档路径。
     */
    private static void validateRanges(DoudizhuSnapshot s) throws SerializeException {
        if (s.phase < 1 || s.phase > 3) {
            throw new SerializeException("非法阶段: " + s.phase);
        }
        if (s.currentTurn < 0 || s.currentTurn >= 3) {
            throw new SerializeException("非法回合座位: " + s.currentTurn);
        }
        if (s.landlordSeat < -1 || s.landlordSeat >= 3) {
            throw new SerializeException("非法地主座位: " + s.landlordSeat);
        }
        if (s.highestBid < 0 || s.highestBid > 3) {
            throw new SerializeException("非法最高叫分: " + s.highestBid);
        }
        if (s.bidPlacedCount < 0 || s.bidPlacedCount > 3) {
            throw new SerializeException("非法叫分人数: " + s.bidPlacedCount);
        }
        if (s.redealCount < 0) {
            throw new SerializeException("非法重发次数: " + s.redealCount);
        }
        if (s.bidScore < 0 || s.bidScore > 3) {
            throw new SerializeException("非法叫分倍数: " + s.bidScore);
        }
        if (s.bombCount < 0 || s.bombCount > MAX_BOMB_COUNT) {
            throw new SerializeException("非法炸弹次数: " + s.bombCount);
        }
        if (s.difficulty < 0 || s.difficulty > 2) {
            throw new SerializeException("非法难度: " + s.difficulty);
        }
        if (s.lastPlayerSeat < -1 || s.lastPlayerSeat >= 3) {
            throw new SerializeException("非法桌面出牌者座位: " + s.lastPlayerSeat);
        }
        if (s.playCounts != null) {
            for (int i = 0; i < s.playCounts.length; i++) {
                if (s.playCounts[i] < 0) {
                    throw new SerializeException("非法出牌手数: seat=" + i
                            + " count=" + s.playCounts[i]);
                }
            }
        }
    }

    private static String cards(List<Card> list) {
        StringBuilder sb = new StringBuilder();
        for (Card c : list) {
            if (sb.length() > 0) sb.append(',');
            // 牌编码为 "权重-花色序号"（纯整数对）：权重 3~17 唯一对应 Rank，
            // 花色序号 0~5 唯一对应 Suit；避免花色代号 "d_j"（大王）与分隔符冲突
            sb.append(c.getWeight()).append('-').append(c.getSuit().ordinal());
        }
        return sb.toString();
    }

    private static List<Card> parseCards(String raw) {
        List<Card> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String token : raw.split(",")) {
            String[] parts = token.split("-");
            if (parts.length != 2) {
                throw new SerializeException("非法牌: " + token);
            }
            int weight = Integer.parseInt(parts[0].trim());
            int suitOrd = Integer.parseInt(parts[1].trim());
            out.add(Card.create(suitByOrdinal(suitOrd), rankByWeight(weight)));
        }
        return out;
    }

    private static Suit suitByOrdinal(int ordinal) {
        Suit[] values = Suit.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new SerializeException("非法花色序号: " + ordinal);
        }
        return values[ordinal];
    }

    private static Rank rankByWeight(int weight) {
        for (Rank rank : Rank.values()) {
            if (rank.getWeight() == weight) return rank;
        }
        throw new SerializeException("非法牌值: " + weight);
    }

    private static int[] parseIntArray(String raw, int len) {
        String[] parts = raw.split(",");
        if (parts.length != len) {
            throw new SerializeException("整数数组长度不符: " + raw);
        }
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private static boolean[] parseBooleanArray(String raw, int len) {
        String[] parts = raw.split(",");
        if (parts.length != len) {
            throw new SerializeException("布尔数组长度不符: " + raw);
        }
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = "1".equals(parts[i].trim());
        }
        return out;
    }

    /** 存档损坏异常。 */
    public static class SerializeException extends RuntimeException {
        public SerializeException(String message) {
            super(message);
        }
    }
}
