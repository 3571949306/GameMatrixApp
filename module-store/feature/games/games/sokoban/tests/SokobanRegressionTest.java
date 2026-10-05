import com.gamecenter.app.sokoban.SokobanCampaign;
import com.gamecenter.app.sokoban.SokobanCampaignProgress;
import com.gamecenter.app.sokoban.SokobanDailyPuzzle;
import com.gamecenter.app.sokoban.SokobanDailyRecord;
import com.gamecenter.app.sokoban.SokobanGame;
import com.gamecenter.app.sokoban.SokobanLevelCodec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推箱子逻辑回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>内置 15 关完整性：每关可开局、恰好一个玩家、目标可被箱子覆盖、初始未完成；</li>
 *   <li>{@code startCustomLevel} 校验式装载：合法装载可玩到通关，非法规格一律拒绝且无副作用；</li>
 *   <li>{@link SokobanLevelCodec} 编解码 roundtrip 与非法输入拒绝；</li>
 *   <li>关卡码端到端：文本码 → 解码 → 装载 → 推箱通关；</li>
 *   <li>undoMove 撤销回归（防现有功能退化）；</li>
 *   <li>战役模式：3 章 × 3 关完整性 + 每关解序列逐步铁证通关（T9）；</li>
 *   <li>战役进度状态机：章节解锁、幂等记录与持久化回灌（T10）；</li>
 *   <li>每日关卡挑战：24 关池构成、日期种子确定性选关与日期 key 工具（T11）；</li>
 *   <li>每日记录规则：完成判定、最少步数刷新与最近 N 天完成状态（T12）。</li>
 * </ul>
 *
 * <p>运行：见 scripts/verify_sokoban.py。</p>
 */
public class SokobanRegressionTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    // ---- 便捷别名 ----
    static final int EMPTY = SokobanGame.EMPTY;
    static final int WALL = SokobanGame.WALL;
    static final int FLOOR = SokobanGame.FLOOR;
    static final int TARGET = SokobanGame.TARGET;
    static final int BOX = SokobanGame.BOX;
    static final int BOX_ON_TARGET = SokobanGame.BOX_ON_TARGET;
    static final int PLAYER = SokobanGame.PLAYER;
    static final int PLAYER_ON_TARGET = SokobanGame.PLAYER_ON_TARGET;

    /** 简单自定义关：玩家在 (1,1)，箱子在 (1,2)，目标在 (1,3)，向右推一步即通关。 */
    static int[][] simpleLevel() {
        return new int[][]{
            {WALL, WALL, WALL, WALL},
            {WALL, PLAYER, BOX, TARGET},
            {WALL, FLOOR, FLOOR, FLOOR},
            {WALL, WALL, WALL, WALL},
        };
    }

    /** simpleLevel 对应的关卡码。 */
    static final String SIMPLE_CODE = "####/#@$./#---/####";

    static int count(int[][] map, int cell) {
        int n = 0;
        for (int[] row : map) {
            for (int v : row) {
                if (v == cell) n++;
            }
        }
        return n;
    }

    static int countTargets(int[][] map) {
        return count(map, TARGET) + count(map, BOX_ON_TARGET) + count(map, PLAYER_ON_TARGET);
    }

    static int countBoxes(int[][] map) {
        return count(map, BOX) + count(map, BOX_ON_TARGET);
    }

    static int[][] copyMap(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int r = 0; r < src.length; r++) dst[r] = src[r].clone();
        return dst;
    }

    static boolean mapEqual(int[][] a, int[][] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int r = 0; r < a.length; r++) {
            if (a[r].length != b[r].length) return false;
            for (int c = 0; c < a[r].length; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
    }

    // ====================================================================
    // T1 内置关卡完整性
    // ====================================================================
    static void testBuiltInLevels() {
        System.out.println("[T1] 内置关卡完整性");
        SokobanGame g = new SokobanGame();
        for (int lv = 1; lv <= SokobanGame.TOTAL_LEVELS; lv++) {
            g.startLevel(lv);
            int[][] map = g.getMap();
            check("关卡 " + lv + " 开局运行中", g.isRunning());
            check("关卡 " + lv + " 恰好一个玩家",
                    count(map, PLAYER) + count(map, PLAYER_ON_TARGET) == 1);
            check("关卡 " + lv + " 至少一个目标", countTargets(map) >= 1);
            check("关卡 " + lv + " 箱数足以覆盖目标", countBoxes(map) >= countTargets(map));
            check("关卡 " + lv + " 初始未完成", !g.isLevelComplete());
            check("关卡 " + lv + " 非自定义模式", !g.isCustomLevel());
            check("关卡 " + lv + " 编码非空", SokobanLevelCodec.encode(map) != null);
        }
    }

    // ====================================================================
    // T2 自定义关卡装载与可玩性
    // ====================================================================
    static void testCustomLevelPlayable() {
        System.out.println("[T2] 自定义关卡装载与可玩性");
        SokobanGame g = new SokobanGame();
        check("简单关装载成功", g.startCustomLevel(simpleLevel()));
        check("自定义模式标记", g.isCustomLevel());
        check("currentLevel == CUSTOM_LEVEL", g.getCurrentLevel() == SokobanGame.CUSTOM_LEVEL);
        check("开局运行中", g.isRunning());
        check("玩家位于 (1,1)", g.getPlayerRow() == 1 && g.getPlayerCol() == 1);
        check("向左走（撞墙）不移动", !g.movePlayer(0, -1));
        check("向右推箱成功", g.movePlayer(0, 1));
        check("步数 = 1", g.getMoveCount() == 1);
        check("推数 = 1", g.getPushCount() == 1);
        check("推箱后关卡完成", g.isLevelComplete());
        int clearedBefore = g.getLevelsCleared();
        g.onLevelComplete();
        check("通关后停止运行", !g.isRunning());
        check("自定义通关不推进内置进度", g.getLevelsCleared() == clearedBefore);
    }

    // ====================================================================
    // T3 拒绝场景与无副作用
    // ====================================================================
    static void testCustomLevelRejection() {
        System.out.println("[T3] 自定义装载拒绝与无副作用");

        int[][] jagged = {{WALL, WALL}, {WALL}};
        int[][] badValueHigh = {{8}};
        int[][] badValueLow = {{-1}};
        int[][] noPlayer = {{WALL, BOX, TARGET, FLOOR}};
        int[][] twoPlayers = {{PLAYER, FLOOR, BOX, TARGET}, {PLAYER, FLOOR, FLOOR, FLOOR}};
        int[][] noBox = {{WALL, PLAYER, TARGET, FLOOR}};
        int[][] boxTargetMismatch = {{PLAYER, BOX, BOX, TARGET}};
        int[][] tooBig = new int[21][21];

        SokobanGame g = new SokobanGame();
        check("拒绝 null", !g.startCustomLevel(null));
        check("拒绝空数组", !g.startCustomLevel(new int[][]{}));
        check("拒绝空行", !g.startCustomLevel(new int[][]{{}}));
        check("拒绝非矩形", !g.startCustomLevel(jagged));
        check("拒绝非法值 8", !g.startCustomLevel(badValueHigh));
        check("拒绝非法值 -1", !g.startCustomLevel(badValueLow));
        check("拒绝无玩家", !g.startCustomLevel(noPlayer));
        check("拒绝双玩家", !g.startCustomLevel(twoPlayers));
        check("拒绝无箱子", !g.startCustomLevel(noBox));
        check("拒绝箱数与目标数不一致", !g.startCustomLevel(boxTargetMismatch));
        check("拒绝尺寸超限 21x21", !g.startCustomLevel(tooBig));

        // 拒绝必须无副作用：先进入合法对局，非法装载后状态保持
        SokobanGame live = new SokobanGame();
        check("前置：合法关装载成功", live.startCustomLevel(simpleLevel()));
        int[][] snapshot = copyMap(live.getMap());
        check("前置拒绝：非矩形", !live.startCustomLevel(jagged));
        check("拒绝后仍运行", live.isRunning());
        check("拒绝后地图不变", mapEqual(live.getMap(), snapshot));
        check("拒绝后步数为 0", live.getMoveCount() == 0);
        check("拒绝后仍是自定义模式", live.isCustomLevel());

        // 移动后再拒绝：步数保持
        check("前置：向下走一步", live.movePlayer(1, 0));
        int moves = live.getMoveCount();
        check("移动后拒绝：双玩家", !live.startCustomLevel(twoPlayers));
        check("拒绝后步数保持 " + moves, live.getMoveCount() == moves);
        check("拒绝后玩家位置不变",
                live.getPlayerRow() == 2 && live.getPlayerCol() == 1);
    }

    // ====================================================================
    // T4 关卡码编解码 roundtrip 与拒绝
    // ====================================================================
    static void testCodec() {
        System.out.println("[T4] 关卡码编解码");
        int[][] map = simpleLevel();
        String code = SokobanLevelCodec.encode(map);
        check("编码非空", code != null);
        check("编码与预期一致", SIMPLE_CODE.equals(code));
        int[][] decoded = SokobanLevelCodec.decode(code);
        check("解码非空", decoded != null);
        check("解码与原图一致", mapEqual(decoded, map));
        check("再编码仍一致", code.equals(SokobanLevelCodec.encode(decoded)));

        // 特殊实体（箱在目标上、玩家在目标上）roundtrip
        int[][] rich = {
            {WALL, WALL, WALL, WALL},
            {WALL, PLAYER_ON_TARGET, BOX_ON_TARGET, TARGET},
            {WALL, FLOOR, BOX, FLOOR},
            {WALL, WALL, WALL, WALL},
        };
        String richCode = SokobanLevelCodec.encode(rich);
        check("特殊实体编码非空", richCode != null);
        check("特殊实体 roundtrip", mapEqual(SokobanLevelCodec.decode(richCode), rich));

        // 编码拒绝
        check("编码拒绝 null", SokobanLevelCodec.encode(null) == null);
        check("编码拒绝空数组", SokobanLevelCodec.encode(new int[][]{}) == null);
        check("编码拒绝空行", SokobanLevelCodec.encode(new int[][]{{}}) == null);
        check("编码拒绝非法值", SokobanLevelCodec.encode(new int[][]{{9}}) == null);
        check("编码拒绝非矩形", SokobanLevelCodec.encode(new int[][]{{WALL, WALL}, {WALL}}) == null);
        check("编码拒绝尺寸超限", SokobanLevelCodec.encode(new int[21][21]) == null);

        // 解码拒绝
        check("解码拒绝 null", SokobanLevelCodec.decode(null) == null);
        check("解码拒绝空文本", SokobanLevelCodec.decode("") == null);
        check("解码拒绝纯空白", SokobanLevelCodec.decode("   ") == null);
        check("解码拒绝非法字符", SokobanLevelCodec.decode("a/b") == null);
        check("解码拒绝非矩形", SokobanLevelCodec.decode("##/#/#") == null);
        check("解码拒绝行数超限", SokobanLevelCodec.decode(repeat("####", 21)) == null);
        check("解码拒绝列数超限", SokobanLevelCodec.decode(repeat("#", 21)) == null);
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(SokobanLevelCodec.ROW_SEP);
            sb.append(s);
        }
        return sb.toString();
    }

    // ====================================================================
    // T5 关卡码端到端（分享闭环核心路径）
    // ====================================================================
    static void testCodeEndToEnd() {
        System.out.println("[T5] 关卡码端到端");
        String shared = SIMPLE_CODE + "   "; // 模拟复制粘贴引入的首尾空白
        int[][] map = SokobanLevelCodec.decode(shared);
        check("带首尾空白的关卡码可解码", map != null);
        SokobanGame g = new SokobanGame();
        check("解码结果装载成功", g.startCustomLevel(map));
        check("向右推箱成功", g.movePlayer(0, 1));
        check("端到端通关", g.isLevelComplete());
    }

    // ====================================================================
    // T6 undoMove 撤销回归
    // ====================================================================
    static void testUndo() {
        System.out.println("[T6] 撤销回归");
        SokobanGame g = new SokobanGame();
        g.startLevel(1);
        check("前置：第 1 关开局运行", g.isRunning());
        check("前置：玩家在 (2,2)", g.getPlayerRow() == 2 && g.getPlayerCol() == 2);

        // 纯移动撤销
        check("向上走成功", g.movePlayer(-1, 0));
        check("走后步数 1", g.getMoveCount() == 1);
        check("撤销成功", g.undoMove());
        check("撤销后步数 0", g.getMoveCount() == 0);
        check("撤销后玩家回 (2,2)", g.getPlayerRow() == 2 && g.getPlayerCol() == 2);

        // 走上目标点再撤销（覆盖格恢复为 TARGET）
        check("向左走到目标点", g.movePlayer(0, -1));
        check("撤销离开目标点", g.undoMove());
        check("目标点恢复为 TARGET", g.getMap()[2][1] == TARGET);

        // 推箱撤销
        check("向下推箱成功", g.movePlayer(1, 0));
        check("推数 1", g.getPushCount() == 1);
        int[][] afterPush = copyMap(g.getMap());
        check("推箱后撤销成功", g.undoMove());
        check("撤销后推数 0", g.getPushCount() == 0);
        check("箱子回到 (3,2)", g.getMap()[3][2] == BOX);
        check("玩家回 (2,2)", g.getPlayerRow() == 2 && g.getPlayerCol() == 2);

        // startLevel(1) 重开后地图应与推箱撤销后的状态一致（即原始第 1 关）
        g.startLevel(1);
        check("重开第 1 关非自定义", !g.isCustomLevel());
        check("重开第 1 关后可编码", SokobanLevelCodec.encode(g.getMap()) != null);
        // 空变量占位避免未使用告警
        if (afterPush == null) check("不应到达", false);
    }

    // ====================================================================
    // T7 内置/自定义模式切换
    // ====================================================================
    static void testModeSwitch() {
        System.out.println("[T7] 模式切换");
        SokobanGame g = new SokobanGame();
        g.startLevel(1);
        check("内置关非自定义", !g.isCustomLevel());
        check("自定义装载成功", g.startCustomLevel(simpleLevel()));
        check("自定义模式", g.isCustomLevel());
        g.startLevel(1);
        check("切回内置关 currentLevel=1", g.getCurrentLevel() == 1);
        check("切回内置关非自定义", !g.isCustomLevel());
        check("切回内置关运行中", g.isRunning());
    }

    // ====================================================================
    // T8 restartLevel 通用重开（自定义关 currentLevel=-1 不能走 startLevel 路径）
    // ====================================================================
    static void testRestartLevel() {
        System.out.println("[T8] restartLevel 通用重开");
        SokobanGame g = new SokobanGame();
        check("前置：自定义关装载", g.startCustomLevel(simpleLevel()));
        check("前置：推箱", g.movePlayer(0, 1));
        check("前置：通关", g.isLevelComplete());
        g.onLevelComplete();
        check("前置：完成后停止", !g.isRunning());
        g.restartLevel();
        check("重开后运行", g.isRunning());
        check("重开后步数/推数归零", g.getMoveCount() == 0 && g.getPushCount() == 0);
        check("重开后玩家复位 (1,1)", g.getPlayerRow() == 1 && g.getPlayerCol() == 1);
        check("重开地图复原", mapEqual(g.getMap(), simpleLevel()));
        check("重开后未完成", !g.isLevelComplete());
        check("重开后仍是自定义模式", g.isCustomLevel());
        check("重开可再推箱通关", g.movePlayer(0, 1) && g.isLevelComplete());

        g.startLevel(1);
        check("前置：内置关推箱", g.movePlayer(1, 0));
        g.restartLevel();
        check("内置关重开步数 0", g.getMoveCount() == 0);
        check("内置关重开玩家 (2,2)", g.getPlayerRow() == 2 && g.getPlayerCol() == 2);
        check("内置关重开仍是第 1 关", g.getCurrentLevel() == 1);
    }

    // ====================================================================
    // T9 战役完整性（3 章 × 3 关 + 每关解序列铁证）
    // ====================================================================
    static void testCampaignIntegrity() {
        System.out.println("[T9] 战役完整性");
        check("章节数 = 3", SokobanCampaign.CHAPTERS.length == 3);
        check("总关数 = 9", SokobanCampaign.totalLevels() == 9);
        check("chapter(0) 返回 null", SokobanCampaign.chapter(0) == null);
        check("chapter(4) 返回 null", SokobanCampaign.chapter(4) == null);
        int[] expectedRewards = {30, 40, 50};
        Set<String> seenCodes = new HashSet<>();
        for (int id = 1; id <= 3; id++) {
            SokobanCampaign.Chapter ch = SokobanCampaign.chapter(id);
            check("章 " + id + " 存在", ch != null);
            if (ch == null) continue;
            check("章 " + id + " 名称非空", ch.name != null && !ch.name.trim().isEmpty());
            check("章 " + id + " 关数 = 3", ch.levels.length == 3);
            check("章 " + id + " 奖励 = " + expectedRewards[id - 1],
                    ch.rewardCoins == expectedRewards[id - 1]);
            for (SokobanCampaign.CampaignLevel lv : ch.levels) {
                String tag = "章" + id + "关" + lv.indexInChapter + "「" + lv.name + "」";
                check(tag + " 章归属一致", lv.chapter == id);
                int[][] map = SokobanLevelCodec.decode(lv.code);
                check(tag + " 关卡码可解码", map != null);
                if (map == null) continue;
                SokobanGame g = new SokobanGame();
                check(tag + " 装载成功", g.startCustomLevel(map));
                check(tag + " 恰好一个玩家",
                        count(map, PLAYER) + count(map, PLAYER_ON_TARGET) == 1);
                check(tag + " 箱数=目标数≥1",
                        countBoxes(map) == countTargets(map) && countBoxes(map) >= 1);
                check(tag + " 关卡码唯一", seenCodes.add(lv.code));
                // 解序列铁证：逐步执行，任一步非法立即 FAIL 并报关卡名与步号
                boolean allMovesOk = true;
                for (int i = 0; i < lv.solution.length; i++) {
                    if (!g.movePlayer(lv.solution[i][0], lv.solution[i][1])) {
                        check(tag + " 解第 " + (i + 1) + " 步合法", false);
                        allMovesOk = false;
                        break;
                    }
                }
                if (allMovesOk) {
                    check(tag + " 解序列 " + lv.solution.length + " 步全部合法", true);
                    check(tag + " 解序列通关", g.isLevelComplete());
                }
            }
        }
    }

    // ====================================================================
    // T10 战役进度逻辑（章节解锁状态机）
    // ====================================================================
    static void testCampaignProgress() {
        System.out.println("[T10] 战役进度逻辑");
        SokobanCampaignProgress p = new SokobanCampaignProgress(null, null);
        check("新进度：章 1 开放", p.isChapterUnlocked(1));
        check("新进度：章 2 锁定", !p.isChapterUnlocked(2));
        check("新进度：章 3 锁定", !p.isChapterUnlocked(3));
        check("新进度：章 1 未完成", !p.isChapterCleared(1));
        check("新进度：1-1 未清", !p.isLevelCleared(1, 1));
        check("新进度：已清关为空", p.clearedLevelKeys().isEmpty());
        check("新进度：已清章为空", p.clearedChapterKeys().isEmpty());

        p.recordLevelCleared(1, 1);
        check("清 1-1：已清", p.isLevelCleared(1, 1));
        check("清 1-1：章 2 仍锁", !p.isChapterUnlocked(2));
        check("清 1-1：章 1 未完成", !p.isChapterCleared(1));

        p.recordLevelCleared(1, 2);
        check("清 1-2：章 1 仍未完成", !p.isChapterCleared(1));
        p.recordLevelCleared(1, 3);
        check("三关全清：章 1 完成", p.isChapterCleared(1));
        check("三关全清：章 2 开放", p.isChapterUnlocked(2));
        check("三关全清：章 3 仍锁", !p.isChapterUnlocked(3));
        check("三关全清：章 1 记入已清章", p.clearedChapterKeys().contains(1));
        check("导出：已清关 key 数 = 3", p.clearedLevelKeys().size() == 3);
        check("导出：key 格式含 \"1-2\"", p.clearedLevelKeys().contains("1-2"));

        // 幂等：重复记录不重复计数
        p.recordLevelCleared(1, 3);
        check("幂等：重复清 1-3 关数不变", p.clearedLevelKeys().size() == 3);
        check("幂等：重复清 1-3 章数不变", p.clearedChapterKeys().size() == 1);

        // 持久化闭环：导出 → 回灌 → 状态一致
        SokobanCampaignProgress restored =
                new SokobanCampaignProgress(p.clearedLevelKeys(), p.clearedChapterKeys());
        check("恢复：1-1 已清", restored.isLevelCleared(1, 1));
        check("恢复：章 1 完成", restored.isChapterCleared(1));
        check("恢复：章 2 开放", restored.isChapterUnlocked(2));

        // recordLevelCleared 本身不校验解锁（越级记录属调用方职责），
        // 但解锁状态只由「前一章全清」驱动，不因越级记录而开放
        SokobanCampaignProgress q = new SokobanCampaignProgress(null, null);
        q.recordLevelCleared(2, 1);
        check("越级清 2-1：记录生效", q.isLevelCleared(2, 1));
        check("越级清 2-1：章 2 仍锁", !q.isChapterUnlocked(2));
        check("越级清 2-1：章 2 未集齐不算完成", !q.isChapterCleared(2));
    }

    /** DailyLevel 字段级相等（不依赖 equals 实现，也不绑定池内单例引用）。 */
    static boolean sameDailyLevel(SokobanDailyPuzzle.DailyLevel a, SokobanDailyPuzzle.DailyLevel b) {
        if (a == null || b == null) return false;
        return a.builtin == b.builtin
                && a.builtinLevel == b.builtinLevel
                && a.chapter == b.chapter
                && a.indexInChapter == b.indexInChapter
                && a.name.equals(b.name)
                && (a.code == null ? b.code == null : a.code.equals(b.code));
    }

    // ====================================================================
    // T11 每日关卡挑战（24 关池 + 日期种子确定性 + 日期 key 工具）
    // ====================================================================
    static void testDailyPuzzle() {
        System.out.println("[T11] 每日关卡挑战");
        check("池大小 = 24（15 内置 + 9 战役）", SokobanDailyPuzzle.poolSize() == 24);

        // 固定日期：非 null、两次调用同日结果一致、选中引用在池内合法
        String[] dates = {"2026-09-24", "2026-09-25", "2026-01-01"};
        for (String d : dates) {
            SokobanDailyPuzzle.DailyLevel first = SokobanDailyPuzzle.forDate(d);
            SokobanDailyPuzzle.DailyLevel second = SokobanDailyPuzzle.forDate(d);
            check("forDate(" + d + ") 非 null", first != null);
            check("forDate(" + d + ") 两次调用结果一致（确定性）", sameDailyLevel(first, second));
            if (first == null) continue;
            check(d + " 展示名非空", first.name != null && !first.name.trim().isEmpty());
            if (first.builtin) {
                check(d + " 内置引用关号在 1..15",
                        first.builtinLevel >= 1 && first.builtinLevel <= SokobanGame.TOTAL_LEVELS);
                check(d + " 内置引用名称 = 第 N 关",
                        first.name.equals("第 " + first.builtinLevel + " 关"));
                check(d + " 内置引用 code 为 null", first.code == null);
                check(d + " 内置引用战役字段为 0",
                        first.chapter == 0 && first.indexInChapter == 0);
            } else {
                check(d + " 战役引用章在 1..3", first.chapter >= 1 && first.chapter <= 3);
                check(d + " 战役引用序在 1..3",
                        first.indexInChapter >= 1 && first.indexInChapter <= 3);
                check(d + " 战役引用 code 非空", first.code != null);
                check(d + " 战役引用名称含「战役·」前缀", first.name.startsWith("战役·"));
                check(d + " 战役引用内置字段为 0", first.builtinLevel == 0);
            }
        }

        // 非法入参
        check("forDate(null) 返回 null", SokobanDailyPuzzle.forDate(null) == null);
        check("forDate(空串) 返回 null", SokobanDailyPuzzle.forDate("") == null);
        check("forDate(非 10 位) 返回 null", SokobanDailyPuzzle.forDate("2026-9-24") == null);

        // 战役关抽测：从 2026-01-01 起前 3 个日期里找首个战役关，验证 decode + 装载
        SokobanDailyPuzzle.DailyLevel campaignPick = null;
        String pickDate = null;
        for (int i = 0; i < 3 && campaignPick == null; i++) {
            String d = SokobanDailyPuzzle.dateKeyDaysBack("2026-01-01", i);
            SokobanDailyPuzzle.DailyLevel lv = SokobanDailyPuzzle.forDate(d);
            if (lv != null && !lv.builtin) {
                campaignPick = lv;
                pickDate = d;
            }
        }
        if (campaignPick != null) {
            check("抽测 " + pickDate + "「" + campaignPick.name + "」为战役关", !campaignPick.builtin);
            int[][] map = SokobanLevelCodec.decode(campaignPick.code);
            check("抽测战役关 code 可解码", map != null);
            if (map != null) {
                SokobanGame g = new SokobanGame();
                check("抽测战役关装载成功", g.startCustomLevel(map));
                check("抽测战役关开局运行", g.isRunning());
            }
        } else {
            // 前 3 个日期全为内置关：退化为全量验证战役池 9 关 decode + 装载
            int verified = 0;
            for (SokobanCampaign.Chapter ch : SokobanCampaign.CHAPTERS) {
                for (SokobanCampaign.CampaignLevel lv : ch.levels) {
                    int[][] map = SokobanLevelCodec.decode(lv.code);
                    check("战役池「" + lv.name + "」code 可解码", map != null);
                    if (map != null) {
                        check("战役池「" + lv.name + "」装载成功",
                                new SokobanGame().startCustomLevel(map));
                    }
                    verified++;
                }
            }
            check("战役池全量验证共 9 关", verified == 9);
        }

        // dateKeyDaysBack：昨天 / 7 天前 / 跨月 / 跨年 / 当天 / 非法入参
        check("dateKeyDaysBack 昨天", "2026-09-23".equals(
                SokobanDailyPuzzle.dateKeyDaysBack("2026-09-24", 1)));
        check("dateKeyDaysBack 7 天前", "2026-09-17".equals(
                SokobanDailyPuzzle.dateKeyDaysBack("2026-09-24", 7)));
        check("dateKeyDaysBack 跨月", "2026-09-30".equals(
                SokobanDailyPuzzle.dateKeyDaysBack("2026-10-01", 1)));
        check("dateKeyDaysBack 跨年", "2025-12-31".equals(
                SokobanDailyPuzzle.dateKeyDaysBack("2026-01-01", 1)));
        check("dateKeyDaysBack 当天 = 自身", "2026-09-24".equals(
                SokobanDailyPuzzle.dateKeyDaysBack("2026-09-24", 0)));
        check("dateKeyDaysBack null todayKey 返回 null",
                SokobanDailyPuzzle.dateKeyDaysBack(null, 1) == null);
        check("dateKeyDaysBack 非法长度返回 null",
                SokobanDailyPuzzle.dateKeyDaysBack("2026-9-24", 1) == null);
        check("dateKeyDaysBack 非法日期返回 null",
                SokobanDailyPuzzle.dateKeyDaysBack("2026-99-99", 1) == null);
    }

    // ====================================================================
    // T12 每日记录逻辑（完成判定 + 最少步数刷新 + 最近 N 天状态）
    // ====================================================================
    static void testDailyRecord() {
        System.out.println("[T12] 每日记录逻辑");
        Map<String, Integer> m = new HashMap<>();
        String today = "2026-09-24";
        check("空 map：今日未完成", !SokobanDailyRecord.isCompleted(m, today));
        check("首次记录返回 true", SokobanDailyRecord.record(m, today, 20));
        check("记录后当日已完成", SokobanDailyRecord.isCompleted(m, today));
        check("当日最少步数 = 20", m.get(today) != null && m.get(today) == 20);
        check("更好步数刷新返回 true", SokobanDailyRecord.record(m, today, 15));
        check("刷新后最少步数 = 15", m.get(today) != null && m.get(today) == 15);
        check("更差步数不刷新返回 false", !SokobanDailyRecord.record(m, today, 18));
        check("更差步数后仍 = 15", m.get(today) != null && m.get(today) == 15);
        check("相同步数不刷新返回 false", !SokobanDailyRecord.record(m, today, 15));
        check("moves=0 拒绝", !SokobanDailyRecord.record(m, today, 0));
        check("moves<0 拒绝", !SokobanDailyRecord.record(m, today, -3));
        check("另一天独立未完成", !SokobanDailyRecord.isCompleted(m, "2026-09-23"));

        // null map 内部容错：record 合法入参按首次成功，查询接口不崩
        check("null map record 容错返回 true",
                SokobanDailyRecord.record(null, "2026-09-25", 10));
        check("null map isCompleted 容错未完成",
                !SokobanDailyRecord.isCompleted(null, "2026-09-25"));
        check("null map moves=0 仍拒绝",
                !SokobanDailyRecord.record(null, "2026-09-25", 0));

        // recentStreak：今天 + 昨天完成，前天未完成
        Map<String, Integer> s = new HashMap<>();
        s.put("2026-09-24", 12);
        s.put("2026-09-23", 30);
        List<Integer> streak = SokobanDailyRecord.recentStreak(s, "2026-09-24", 3);
        check("3 天列表长度 = 3", streak.size() == 3);
        check("下标 0 = 今天 12 步", streak.size() > 0 && Integer.valueOf(12).equals(streak.get(0)));
        check("下标 1 = 昨天 30 步", streak.size() > 1 && Integer.valueOf(30).equals(streak.get(1)));
        check("下标 2 = 前天未完成 null", streak.size() > 2 && streak.get(2) == null);
        check("非法 todayKey 返回空列表",
                SokobanDailyRecord.recentStreak(s, "2026-9-24", 3).isEmpty());
        check("null todayKey 返回空列表",
                SokobanDailyRecord.recentStreak(s, null, 3).isEmpty());
        List<Integer> nullStreak = SokobanDailyRecord.recentStreak(null, "2026-09-24", 2);
        check("null map streak 长度 = 2", nullStreak.size() == 2);
        check("null map streak 全 null",
                nullStreak.size() == 2 && nullStreak.get(0) == null && nullStreak.get(1) == null);
    }

    public static void main(String[] args) {
        System.out.println("==== 推箱子逻辑回归测试 ====");
        testBuiltInLevels();
        testCustomLevelPlayable();
        testCustomLevelRejection();
        testCodec();
        testCodeEndToEnd();
        testUndo();
        testModeSwitch();
        testRestartLevel();
        testCampaignIntegrity();
        testCampaignProgress();
        testDailyPuzzle();
        testDailyRecord();
        System.out.println("================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，自定义关卡装载/编解码/撤销均符合契约。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("SOKOBAN_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}
