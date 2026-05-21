package com.gamecenter.app.games;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.gamecenter.app.R;
import com.gamecenter.app.games.blackjack.BlackjackActivity;
import com.gamecenter.app.games.breakout.BreakoutActivity;
import com.gamecenter.app.games.brotato.BrotatoActivity;
import com.gamecenter.app.games.checkers.CheckersActivity;
import com.gamecenter.app.games.chinesechess.ChineseChessActivity;
import com.gamecenter.app.games.dice.DiceActivity;
import com.gamecenter.app.games.doudizhu.DouDiZhuMenuActivity;
import com.gamecenter.app.games.flappy.FlappyActivity;
import com.gamecenter.app.games.game2048.Game2048Activity;
import com.gamecenter.app.games.go.GoActivity;
import com.gamecenter.app.games.gomoku.GomokuActivity;
import com.gamecenter.app.games.guess.GuessActivity;
import com.gamecenter.app.games.klotski.KlotskiActivity;
import com.gamecenter.app.games.match.MatchActivity;
import com.gamecenter.app.games.memory.MemoryActivity;
import com.gamecenter.app.games.pipeline.PipelineActivity;
import com.gamecenter.app.games.plane.PlaneActivity;
import com.gamecenter.app.games.reaction.ReactionActivity;
import com.gamecenter.app.games.rock.RockActivity;
import com.gamecenter.app.games.snake.SnakeActivity;
import com.gamecenter.app.games.sokoban.SokobanActivity;
import com.gamecenter.app.games.sudoku.SudokuActivity;
import com.gamecenter.app.games.tetris.TetrisActivity;
import com.gamecenter.app.games.tic.TicActivity;
import com.gamecenter.app.games.tiles.TilesActivity;
import com.gamecenter.app.games.whack.WhackActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏注册中心 —— 游戏中心的"花名册"
 *
 * <p>你可以把这个类想象成学校的花名册：它记录了游戏中心里有哪些游戏、
 * 每个游戏叫什么名字、属于哪个分类、对应哪个页面（Activity）。
 * 当游戏大厅需要展示游戏列表时，就来这里查询。</p>
 *
 * <p>注册方式有两种（双轨制）：
 * <ul>
 *   <li>静态注册：通过 {@link #buildStaticEntries(Context)} 硬编码的游戏列表，
 *       就像花名册上提前写好的名字，保证启动速度</li>
 *   <li>动态注册：通过 {@link GameEntry} 注解自动发现，或通过 {@link #register(Entry)} 手动注册，
 *       就像开学后新转来的同学，可以随时加入</li>
 * </ul>
 * 动态注册的游戏会追加到对应分类末尾，不会覆盖静态注册的条目。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用final类+私有构造函数，确保该类仅作为静态工具类使用，不可实例化
 *       （就像你不需要"创建"花名册，花名册本身就是唯一的）</li>
 *   <li>所有返回的列表均通过Collections.unmodifiableList包装，保证外部不可修改注册数据
 *       （花名册只能看，不能乱改）</li>
 *   <li>游戏ID采用字符串标识（如"gomoku"），用于持久化存储和跨组件引用
 *       （每个游戏都有唯一的学号）</li>
 *   <li>分类键名与字符串资源解耦，支持注解中的分类标识符映射到本地化分类名</li>
 * </ul>
 * </p>
 */
public final class GameRegistry {

    // 日志标签，用于在Logcat中筛选该类的日志输出
    private static final String TAG = "GameRegistry";

    /** 分类键名常量，与 {@link GameEntry#category()} 中的值对应 */
    // 以下五个常量定义了游戏的五大分类，就像学校把学生分成不同班级
    public static final String CATEGORY_CLASSICS = "classics";   // 经典游戏
    public static final String CATEGORY_PUZZLE = "puzzle";       // 益智游戏
    public static final String CATEGORY_CASUAL = "casual";       // 休闲游戏
    public static final String CATEGORY_REACTION = "reaction";   // 反应力游戏
    public static final String CATEGORY_OTHER = "other";         // 其他游戏

    /**
     * 动态注册的条目缓存，按分类键名索引。
     * LinkedHashMap保证插入顺序，先注册的游戏排在前面。
     */
    private static final Map<String, List<Entry>> dynamicEntries = new LinkedHashMap<>();

    /** 是否已完成注解扫描，避免重复扫描浪费性能 */
    private static boolean annotationScanDone = false;

    // 私有构造函数，防止外部创建实例（工具类不需要实例化）
    private GameRegistry() {
    }

    /**
     * 动态注册一个游戏条目。
     *
     * <p>就像新同学转学来要登记一样，把新游戏添加到花名册中。
     * 注册的条目会追加到对应分类的末尾。如果同 ID 的条目已存在（静态或动态），
     * 则忽略此次注册，避免重复。</p>
     *
     * @param entry 要注册的游戏条目
     * @return true 表示注册成功，false 表示已存在同 ID 条目（重复了）
     */
    public static boolean register(Entry entry) {
        // 使用synchronized保证线程安全，防止多线程同时注册导致数据错乱
        synchronized (dynamicEntries) {
            // 获取该分类下的已有条目列表，如果没有则创建新列表
            List<Entry> list = dynamicEntries.get(entry.categoryKey);
            if (list == null) {
                list = new ArrayList<>();
                dynamicEntries.put(entry.categoryKey, list);
            }
            // 检查是否已存在相同ID的条目，避免重复注册
            for (Entry e : list) {
                if (e.id.equals(entry.id)) return false;
            }
            list.add(entry);
            return true;
        }
    }

    /**
     * 批量动态注册游戏条目。
     *
     * <p>相当于一次性给多个新同学登记，返回成功登记的人数。</p>
     *
     * @param entries 要注册的游戏条目列表
     * @return 成功注册的条目数量
     */
    public static int registerAll(List<Entry> entries) {
        int count = 0;
        for (Entry entry : entries) {
            if (register(entry)) count++;
        }
        return count;
    }

    /**
     * 清除所有动态注册的条目。
     *
     * <p>主要用于测试场景，就像考试后清空草稿纸。</p>
     */
    public static void clearDynamicEntries() {
        synchronized (dynamicEntries) {
            dynamicEntries.clear();
            annotationScanDone = false; // 重置扫描标记，允许重新扫描
        }
    }

    /**
     * 扫描 APK 中所有标注了 {@link GameEntry} 注解的 Activity 类，
     * 并将它们动态注册到注册中心。
     *
     * <p>你可以把这个过程想象成老师点名：遍历所有学生（Activity），
     * 看谁举手（标注了注解），举手的就登记到花名册上。
     * 此方法仅在首次调用时执行扫描，后续调用直接返回（双重检查锁定）。</p>
     *
     * @param context 上下文，用于获取 PackageManager
     */
    public static void scanAnnotatedGames(Context context) {
        // 第一次检查：如果已经扫描过，直接返回，避免进入同步块
        if (annotationScanDone) return;
        synchronized (dynamicEntries) {
            // 第二次检查：防止多线程同时通过第一次检查
            if (annotationScanDone) return;

            try {
                // 通过PackageManager获取本应用中所有的Activity信息
                PackageInfo info = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), PackageManager.GET_ACTIVITIES);
                if (info.activities == null) {
                    annotationScanDone = true;
                    return;
                }

                // 先收集已有的静态注册ID，用于去重
                Set<String> existingIds = collectExistingIds(context);

                // 遍历所有Activity，检查是否标注了GameEntry注解
                for (android.content.pm.ActivityInfo ai : info.activities) {
                    try {
                        // 通过反射获取Activity类的Class对象
                        Class<?> clazz = Class.forName(ai.name);
                        // 检查该类是否标注了GameEntry注解
                        GameEntry ge = clazz.getAnnotation(GameEntry.class);
                        if (ge != null && !existingIds.contains(ge.id())) {
                            // 优先使用字符串资源（支持多语言），没有资源时才用硬编码字符串
                            String name = ge.nameRes() != 0
                                    ? context.getString(ge.nameRes())
                                    : ge.name();
                            String desc = ge.descRes() != 0
                                    ? context.getString(ge.descRes())
                                    : ge.desc();
                            // 创建游戏条目并添加到动态注册列表
                            Entry entry = new Entry(
                                    ge.id(),
                                    ge.iconRes(),
                                    name,
                                    desc,
                                    clazz,
                                    mapCategoryKey(ge.category()),
                                    ge.category()
                            );
                            List<Entry> list = dynamicEntries.get(entry.categoryKey);
                            if (list == null) {
                                list = new ArrayList<>();
                                dynamicEntries.put(entry.categoryKey, list);
                            }
                            list.add(entry);
                            Log.d(TAG, "Auto-registered game: " + ge.id() + " -> " + ai.name);
                        }
                    } catch (ClassNotFoundException e) {
                        Log.w(TAG, "Cannot load class: " + ai.name);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Annotation scan failed: " + e.getMessage());
            }
            annotationScanDone = true;
        }
    }

    /**
     * 收集所有已注册（静态）的游戏 ID，用于去重。
     *
     * <p>就像检查花名册上已有的学号，防止重复登记。</p>
     */
    private static Set<String> collectExistingIds(Context context) {
        Set<String> ids = new LinkedHashSet<>();
        for (Category cat : buildStaticCategories(context)) {
            for (Entry e : cat.games) {
                ids.add(e.id);
            }
        }
        return ids;
    }

    /**
     * 将注解中的分类标识符映射为标准分类键名。
     *
     * <p>支持多种别名，比如"classic"和"classics"都映射到同一个分类。
     * 就像叫"一班"和"1班"指的是同一个班级。不认识的分类统一归到"其他"。</p>
     */
    private static String mapCategoryKey(String category) {
        if (category == null || category.trim().isEmpty()) return CATEGORY_OTHER;
        String lower = category.trim().toLowerCase();
        switch (lower) {
            case "classics":
            case "classic":
                return CATEGORY_CLASSICS;
            case "puzzle":
            case "puzzles":
                return CATEGORY_PUZZLE;
            case "casual":
            case "casuals":
                return CATEGORY_CASUAL;
            case "reaction":
            case "reactions":
                return CATEGORY_REACTION;
            case "other":
            case "others":
                return CATEGORY_OTHER;
            default:
                return CATEGORY_OTHER; // 无法识别的分类统一归入"其他"
        }
    }

    /**
     * 获取所有游戏分类列表（静态 + 动态注册）。
     *
     * <p>这是外部最常用的方法，相当于翻开完整的花名册。
     * 首先构建静态分类列表，然后将动态注册的条目追加到对应分类末尾。
     * 如果动态条目的分类键名不在五大分类中，则归入"其他"类。
     * 每次调用都会重新构建分类列表，从字符串资源中读取分类名称和游戏描述，
     * 以支持多语言国际化。</p>
     *
     * @param context 上下文对象，用于获取字符串资源
     * @return 不可修改的分类列表，包含经典、益智、休闲、反应力和其他五大类
     */
    public static List<Category> getCategories(Context context) {
        // 先触发注解扫描，确保动态注册的条目是最新的
        scanAnnotatedGames(context);

        // 获取静态硬编码的分类列表
        List<Category> staticCategories = buildStaticCategories(context);

        // 准备合并：用Map按分类键名组织所有条目
        Map<String, List<Entry>> merged = new LinkedHashMap<>();
        Map<String, String> categoryNames = new LinkedHashMap<>();

        // 先把静态分类的条目复制到合并Map中
        for (Category cat : staticCategories) {
            List<Entry> copy = new ArrayList<>(cat.games);
            merged.put(cat.categoryKey, copy);
            categoryNames.put(cat.categoryKey, cat.name);
        }

        // 再把动态注册的条目追加到对应分类
        synchronized (dynamicEntries) {
            for (Map.Entry<String, List<Entry>> e : dynamicEntries.entrySet()) {
                String key = e.getKey();
                if (!merged.containsKey(key)) {
                    // 新分类，直接添加
                    merged.put(key, new ArrayList<>(e.getValue()));
                    categoryNames.put(key, context.getString(R.string.category_other));
                } else {
                    // 已有分类，追加到末尾
                    merged.get(key).addAll(e.getValue());
                }
            }
        }

        // 构建最终结果列表
        List<Category> result = new ArrayList<>();
        for (Map.Entry<String, String> nameEntry : categoryNames.entrySet()) {
            String key = nameEntry.getKey();
            result.add(new Category(nameEntry.getValue(), merged.get(key), key));
        }

        // 返回不可修改的列表，防止外部意外修改
        return Collections.unmodifiableList(result);
    }

    /**
     * 构建静态硬编码的游戏分类列表。
     *
     * <p>这是默认的游戏数据源，包含所有内置游戏。
     * 就像花名册上提前写好的名单。新增内置游戏仍需在此方法中添加条目；
     * 外部/插件游戏应通过 {@link GameEntry} 注解或 {@link #register(Entry)} 动态注册。</p>
     *
     * @param context 上下文对象
     * @return 静态分类列表
     */
    private static List<Category> buildStaticCategories(Context context) {
        List<Category> categories = new ArrayList<>();

        // 从字符串资源中读取各分类的本地化名称（支持多语言）
        String catClassics = context.getString(R.string.category_classics);
        String catPuzzle = context.getString(R.string.category_puzzle);
        String catCasual = context.getString(R.string.category_casual);
        String catReaction = context.getString(R.string.category_reaction);
        String catOther = context.getString(R.string.category_other);

        // ===== 经典游戏分类 =====
        List<Entry> classics = new ArrayList<>();
        classics.add(new Entry("gomoku", R.drawable.ic_gomoku, context.getString(R.string.gomoku), context.getString(R.string.gomoku_desc), GomokuActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("go", R.drawable.ic_go, context.getString(R.string.game_go), context.getString(R.string.game_go_desc), GoActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("chinese_chess", R.drawable.ic_chess, context.getString(R.string.chinese_chess), context.getString(R.string.chinese_chess_desc), ChineseChessActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("snake", R.drawable.ic_snake, context.getString(R.string.snake), context.getString(R.string.snake_desc), SnakeActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("tetris", R.drawable.ic_tetris, context.getString(R.string.tetris), context.getString(R.string.tetris_desc), TetrisActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("doudizhu", R.drawable.ic_game, context.getString(R.string.game_doudizhu), context.getString(R.string.game_doudizhu_desc), DouDiZhuMenuActivity.class, catClassics, CATEGORY_CLASSICS));
        classics.add(new Entry("brotato", R.drawable.ic_brotato, context.getString(R.string.brotato), context.getString(R.string.brotato_desc), BrotatoActivity.class, catClassics, CATEGORY_CLASSICS));
        categories.add(new Category(catClassics, classics, CATEGORY_CLASSICS));

        // ===== 益智游戏分类 =====
        List<Entry> puzzles = new ArrayList<>();
        puzzles.add(new Entry("game_2048", R.drawable.ic_2048, context.getString(R.string.game_2048), context.getString(R.string.game_2048_desc), Game2048Activity.class, catPuzzle, CATEGORY_PUZZLE));
        puzzles.add(new Entry("sudoku", R.drawable.ic_game, context.getString(R.string.game_sudoku), context.getString(R.string.game_sudoku_desc), SudokuActivity.class, catPuzzle, CATEGORY_PUZZLE));
        puzzles.add(new Entry("sokoban", R.drawable.ic_game, context.getString(R.string.game_sokoban), context.getString(R.string.game_sokoban_desc), SokobanActivity.class, catPuzzle, CATEGORY_PUZZLE));
        puzzles.add(new Entry("pipeline", R.drawable.ic_game, context.getString(R.string.game_pipeline), context.getString(R.string.game_pipeline_desc), PipelineActivity.class, catPuzzle, CATEGORY_PUZZLE));
        puzzles.add(new Entry("klotski", R.drawable.ic_game, context.getString(R.string.game_klotski), context.getString(R.string.game_klotski_desc), KlotskiActivity.class, catPuzzle, CATEGORY_PUZZLE));
        categories.add(new Category(catPuzzle, puzzles, CATEGORY_PUZZLE));

        // ===== 休闲游戏分类 =====
        List<Entry> casual = new ArrayList<>();
        casual.add(new Entry("breakout", R.drawable.ic_breakout, context.getString(R.string.game_breakout), context.getString(R.string.game_breakout_desc), BreakoutActivity.class, catCasual, CATEGORY_CASUAL));
        casual.add(new Entry("whack", R.drawable.ic_game, context.getString(R.string.game_whack), context.getString(R.string.game_whack_desc), WhackActivity.class, catCasual, CATEGORY_CASUAL));
        casual.add(new Entry("match", R.drawable.ic_game, context.getString(R.string.game_match), context.getString(R.string.game_match_desc), MatchActivity.class, catCasual, CATEGORY_CASUAL));
        casual.add(new Entry("blackjack", R.drawable.ic_game, context.getString(R.string.game_blackjack), context.getString(R.string.game_blackjack_desc), BlackjackActivity.class, catCasual, CATEGORY_CASUAL));
        casual.add(new Entry("checkers", R.drawable.ic_game, context.getString(R.string.game_checkers), context.getString(R.string.game_checkers_desc), CheckersActivity.class, catCasual, CATEGORY_CASUAL));
        categories.add(new Category(catCasual, casual, CATEGORY_CASUAL));

        // ===== 反应力游戏分类 =====
        List<Entry> reaction = new ArrayList<>();
        reaction.add(new Entry("flappy", R.drawable.ic_flappy, context.getString(R.string.game_flappy), context.getString(R.string.game_flappy_desc), FlappyActivity.class, catReaction, CATEGORY_REACTION));
        reaction.add(new Entry("tiles", R.drawable.ic_game, context.getString(R.string.game_tiles), context.getString(R.string.game_tiles_desc), TilesActivity.class, catReaction, CATEGORY_REACTION));
        reaction.add(new Entry("plane", R.drawable.ic_game, context.getString(R.string.game_plane), context.getString(R.string.game_plane_desc), PlaneActivity.class, catReaction, CATEGORY_REACTION));
        reaction.add(new Entry("rock", R.drawable.ic_game, context.getString(R.string.game_rock), context.getString(R.string.game_rock_desc), RockActivity.class, catReaction, CATEGORY_REACTION));
        reaction.add(new Entry("reaction", R.drawable.ic_game, context.getString(R.string.game_reaction), context.getString(R.string.game_reaction_desc), ReactionActivity.class, catReaction, CATEGORY_REACTION));
        categories.add(new Category(catReaction, reaction, CATEGORY_REACTION));

        // ===== 其他游戏分类 =====
        List<Entry> others = new ArrayList<>();
        others.add(new Entry("tic", R.drawable.ic_game, context.getString(R.string.game_tic), context.getString(R.string.game_tic_desc), TicActivity.class, catOther, CATEGORY_OTHER));
        others.add(new Entry("memory", R.drawable.ic_game, context.getString(R.string.game_memory), context.getString(R.string.game_memory_desc), MemoryActivity.class, catOther, CATEGORY_OTHER));
        others.add(new Entry("guess", R.drawable.ic_game, context.getString(R.string.game_guess), context.getString(R.string.game_guess_desc), GuessActivity.class, catOther, CATEGORY_OTHER));
        others.add(new Entry("dice", R.drawable.ic_game, context.getString(R.string.game_dice), context.getString(R.string.game_dice_desc), DiceActivity.class, catOther, CATEGORY_OTHER));
        categories.add(new Category(catOther, others, CATEGORY_OTHER));

        return categories;
    }

    /**
     * 将分类列表扁平化为游戏条目列表。
     *
     * <p>就像把分班的花名册合并成一张总名单，不再区分班级。</p>
     *
     * @param categories 游戏分类列表
     * @return 不可修改的游戏条目列表，包含所有分类下的游戏
     */
    public static List<Entry> flatten(List<Category> categories) {
        List<Entry> games = new ArrayList<>();
        for (Category category : categories) {
            games.addAll(category.games);
        }
        return Collections.unmodifiableList(games);
    }

    /**
     * 游戏分类 —— 相当于花名册中的一个"班级"
     */
    public static final class Category {
        /** 分类名称（本地化），如"经典"、"益智"等 */
        public final String name;
        /** 该分类下的所有游戏条目，不可修改 */
        public final List<Entry> games;
        /** 分类键名（非本地化），如 "classics"、"puzzle"，用于程序内部标识 */
        public final String categoryKey;

        private Category(String name, List<Entry> games, String categoryKey) {
            this.name = name;
            // 用unmodifiableList包装，防止外部修改原始数据
            this.games = Collections.unmodifiableList(games);
            this.categoryKey = categoryKey;
        }
    }

    /**
     * 游戏条目 —— 相当于花名册中一个"学生"的信息
     */
    public static final class Entry {
        /** 游戏唯一标识符，就像学生的学号，全局唯一 */
        public final String id;
        /** 游戏图标资源ID，用于在列表中显示游戏图标 */
        public final int iconRes;
        /** 游戏显示名称，如"五子棋"、"贪吃蛇" */
        public final String name;
        /** 游戏描述文本，简要介绍游戏玩法 */
        public final String desc;
        /** 游戏对应的Activity类，点击游戏图标后跳转到这个页面 */
        public final Class<?> activityClass;
        /** 所属分类名称（本地化），如"经典" */
        public final String category;
        /** 所属分类键名（非本地化），如 "classics"，用于程序内部判断分类 */
        public final String categoryKey;

        private Entry(String id, int iconRes, String name, String desc,
                      Class<?> activityClass, String category, String categoryKey) {
            this.id = id;
            this.iconRes = iconRes;
            this.name = name;
            this.desc = desc;
            this.activityClass = activityClass;
            this.category = category;
            this.categoryKey = categoryKey;
        }
    }
}
