package com.gamecenter.app.achievement;

import com.gamecenter.app.R;

/**
 * 成就类型枚举 —— 游戏中心的"荣誉勋章目录"
 *
 * <p>你可以把这个枚举想象成一本荣誉勋章目录，里面列出了玩家可以获得的所有勋章。
 * 每种勋章都有唯一的编号（key）、勋章名称（titleResId）和获得条件说明（descriptionResId）。</p>
 *
 * <p>成就按类别分组：
 * <ul>
 *   <li>通用成就：首次胜利、连胜、游戏场次等基础里程碑</li>
 *   <li>围棋成就：与围棋相关的特殊成就</li>
 *   <li>象棋成就：与中国象棋相关的特殊成就</li>
 *   <li>五子棋成就：与五子棋相关的特殊成就</li>
 *   <li>在线成就：联机对战中获得的成就</li>
 *   <li>AI成就：与AI助手交互相关的成就</li>
 *   <li>日常成就：每日登录相关的成就</li>
 * </ul>
 * </p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>每个枚举值包含 titleResId 和 descriptionResId，指向字符串资源，
 *       便于国际化（i18n），就像勋章的名字和说明用不同语言写在不同页上</li>
 *   <li>key 字段用于 SharedPreferences 持久化存储，作为唯一标识符，
 *       类似勋章的编号，不会因为语言切换而改变</li>
 *   <li>枚举天然保证单例和不可变，适合做常量定义</li>
 * </ul>
 * </p>
 */
public enum AchievementType {

    // ==================== 通用成就 ====================

    /**
     * 首次胜利 —— 玩家赢得第一场游戏的里程碑
     * <p>就像第一次考试及格，标志着玩家从新手迈向了胜利者的行列。</p>
     */
    FIRST_WIN(
            R.string.achievement_first_win_title,
            R.string.achievement_first_win_desc,
            "first_win"
    ),

    /**
     * 连胜3次 —— 连续赢得3场游戏
     * <p>三连胜是实力的初步证明，就像连续三次考试都拿了满分。</p>
     */
    WIN_STREAK_3(
            R.string.achievement_win_streak_3_title,
            R.string.achievement_win_streak_3_desc,
            "win_streak_3"
    ),

    /**
     * 连胜5次 —— 连续赢得5场游戏
     * <p>五连胜意味着玩家已经找到了获胜的节奏，是对手需要认真对待的存在。</p>
     */
    WIN_STREAK_5(
            R.string.achievement_win_streak_5_title,
            R.string.achievement_win_streak_5_desc,
            "win_streak_5"
    ),

    /**
     * 连胜10次 —— 连续赢得10场游戏
     * <p>十连胜是顶级玩家的标志，意味着在该游戏中几乎无人能敌。</p>
     */
    WIN_STREAK_10(
            R.string.achievement_win_streak_10_title,
            R.string.achievement_win_streak_10_desc,
            "win_streak_10"
    ),

    /**
     * 累计游戏10局 —— 总共完成10局游戏
     * <p>十局是探索的开始，玩家已经对游戏中心有了基本了解。</p>
     */
    GAMES_PLAYED_10(
            R.string.achievement_games_played_10_title,
            R.string.achievement_games_played_10_desc,
            "games_played_10"
    ),

    /**
     * 累计游戏50局 —— 总共完成50局游戏
     * <p>五十局说明玩家已经是一位常客，游戏中心成了日常消遣的好去处。</p>
     */
    GAMES_PLAYED_50(
            R.string.achievement_games_played_50_title,
            R.string.achievement_games_played_50_desc,
            "games_played_50"
    ),

    /**
     * 累计游戏100局 —— 总共完成100局游戏
     * <p>百局老将，游戏中心就是你的第二个家。</p>
     */
    GAMES_PLAYED_100(
            R.string.achievement_games_played_100_title,
            R.string.achievement_games_played_100_desc,
            "games_played_100"
    ),

    // ==================== 围棋成就 ====================

    /**
     * 围棋首次提子 —— 在围棋中首次吃掉对方的棋子
     * <p>提子是围棋中最基本的攻击手段，就像学会了象棋中的"吃子"。</p>
     */
    GO_FIRST_CAPTURE(
            R.string.achievement_go_first_capture_title,
            R.string.achievement_go_first_capture_desc,
            "go_first_capture"
    ),

    // ==================== 象棋成就 ====================

    /**
     * 象棋将杀 —— 在中国象棋中完成将杀
     * <p>将杀是象棋的终极目标，就像将军在战场上取得了决定性胜利。</p>
     */
    CHESS_CHECKMATE(
            R.string.achievement_chess_checkmate_title,
            R.string.achievement_chess_checkmate_desc,
            "chess_checkmate"
    ),

    // ==================== 五子棋成就 ====================

    /**
     * 五子棋完美对局 —— 在五子棋中不落败一子获胜
     * <p>完美对局意味着对手始终没有形成任何有效威胁，是实力的绝对碾压。</p>
     */
    GOMOKU_PERFECT(
            R.string.achievement_gomoku_perfect_title,
            R.string.achievement_gomoku_perfect_desc,
            "gomoku_perfect"
    ),

    // ==================== 在线成就 ====================

    /**
     * 在线首次胜利 —— 在联机对战中首次获胜
     * <p>击败真人对手比击败AI更有成就感，这是你迈向竞技场的里程碑。</p>
     */
    ONLINE_FIRST_WIN(
            R.string.achievement_online_first_win_title,
            R.string.achievement_online_first_win_desc,
            "online_first_win"
    ),

    /**
     * 在线连胜3次 —— 在联机对战中连续赢得3场
     * <p>连续击败三位真人对手，证明你的实力不是运气使然。</p>
     */
    ONLINE_WIN_STREAK_3(
            R.string.achievement_online_win_streak_3_title,
            R.string.achievement_online_win_streak_3_desc,
            "online_win_streak_3"
    ),

    // ==================== AI成就 ====================

    /**
     * AI对话10次 —— 与AI助手累计对话10次
     * <p>十次对话说明你已经熟悉了AI助手的使用方式，它是你的得力帮手。</p>
     */
    AI_CONVERSATIONS_10(
            R.string.achievement_ai_conversations_10_title,
            R.string.achievement_ai_conversations_10_desc,
            "ai_conversations_10"
    ),

    /**
     * AI对话50次 —— 与AI助手累计对话50次
     * <p>五十次对话，AI助手已经成为你不可或缺的智能伙伴。</p>
     */
    AI_CONVERSATIONS_50(
            R.string.achievement_ai_conversations_50_title,
            R.string.achievement_ai_conversations_50_desc,
            "ai_conversations_50"
    ),

    // ==================== 日常成就 ====================

    /**
     * 每日登录 —— 首次完成每日登录
     * <p>每天打开游戏中心也是一种坚持，好的开始是成功的一半。</p>
     */
    DAILY_LOGIN(
            R.string.achievement_daily_login_title,
            R.string.achievement_daily_login_desc,
            "daily_login"
    ),

    /**
     * 连续登录7天 —— 连续7天每日登录
     * <p>一周不间断登录，习惯已经养成，游戏中心成了你日常生活的一部分。</p>
     */
    DAILY_LOGIN_STREAK_7(
            R.string.achievement_daily_login_streak_7_title,
            R.string.achievement_daily_login_streak_7_desc,
            "daily_login_streak_7"
    );

    /**
     * 成就标题的字符串资源ID
     * <p>指向 strings.xml 中定义的成就名称，如"首次胜利"、"连胜3次"等。
     * 使用资源ID而非硬编码字符串，便于多语言支持。</p>
     */
    public final int titleResId;

    /**
     * 成就描述的字符串资源ID
     * <p>指向 strings.xml 中定义的成就获得条件说明，如"赢得你的第一场游戏"等。
     * 就像勋章背面的说明文字，告诉玩家如何获得这枚勋章。</p>
     */
    public final int descriptionResId;

    /**
     * 成就的唯一存储键
     * <p>用于 SharedPreferences 持久化存储时的键名。
     * 与字符串资源ID不同，key 在所有语言环境下保持不变，
     * 就像勋章的编号，不管勋章名字翻译成什么语言，编号始终一样。</p>
     */
    public final String key;

    /**
     * 枚举构造函数
     *
     * @param titleResId     成就标题的字符串资源ID
     * @param descriptionResId 成就描述的字符串资源ID
     * @param key            成就的唯一存储键，用于SharedPreferences持久化
     */
    AchievementType(int titleResId, int descriptionResId, String key) {
        this.titleResId = titleResId;
        this.descriptionResId = descriptionResId;
        this.key = key;
    }
}
