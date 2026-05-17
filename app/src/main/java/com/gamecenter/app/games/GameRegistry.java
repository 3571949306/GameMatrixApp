package com.gamecenter.app.games;

import android.content.Context;
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
import java.util.List;

/**
 * 游戏注册中心
 * <p>
 * 集中管理游戏中心内所有游戏的注册信息，包括游戏ID、图标、名称、描述和对应的Activity类。
 * 采用分类（Category）组织方式，将游戏分为经典、益智、休闲、反应力和其他五大类。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用final类+私有构造函数，确保该类仅作为静态工具类使用，不可实例化</li>
 *   <li>所有返回的列表均通过Collections.unmodifiableList包装，保证外部不可修改注册数据</li>
 *   <li>游戏ID采用字符串标识（如"gomoku"），用于持久化存储和跨组件引用</li>
 * </ul>
 * </p>
 */
public final class GameRegistry {

    /** 私有构造函数，防止外部实例化 */
    private GameRegistry() {
    }

    /**
     * 获取所有游戏分类列表
     * <p>
     * 每次调用都会重新构建分类列表，从字符串资源中读取分类名称和游戏描述，
     * 以支持多语言国际化。
     * </p>
     *
     * @param context 上下文对象，用于获取字符串资源
     * @return 不可修改的分类列表，包含经典、益智、休闲、反应力和其他五大类
     */
    public static List<Category> getCategories(Context context) {
        List<Category> categories = new ArrayList<>();

        // 从字符串资源获取各分类名称，支持国际化
        String catClassics = context.getString(R.string.category_classics);
        String catPuzzle = context.getString(R.string.category_puzzle);
        String catCasual = context.getString(R.string.category_casual);
        String catReaction = context.getString(R.string.category_reaction);
        String catOther = context.getString(R.string.category_other);

        // 经典类游戏：传统棋类和经典街机游戏
        List<Entry> classics = new ArrayList<>();
        classics.add(new Entry("gomoku", R.drawable.ic_gomoku, context.getString(R.string.gomoku), context.getString(R.string.gomoku_desc), GomokuActivity.class, catClassics));
        classics.add(new Entry("go", R.drawable.ic_go, context.getString(R.string.game_go), context.getString(R.string.game_go_desc), GoActivity.class, catClassics));
        classics.add(new Entry("chinese_chess", R.drawable.ic_chess, context.getString(R.string.chinese_chess), context.getString(R.string.chinese_chess_desc), ChineseChessActivity.class, catClassics));
        classics.add(new Entry("snake", R.drawable.ic_snake, context.getString(R.string.snake), context.getString(R.string.snake_desc), SnakeActivity.class, catClassics));
        classics.add(new Entry("tetris", R.drawable.ic_tetris, context.getString(R.string.tetris), context.getString(R.string.tetris_desc), TetrisActivity.class, catClassics));
        classics.add(new Entry("doudizhu", R.drawable.ic_game, context.getString(R.string.game_doudizhu), context.getString(R.string.game_doudizhu_desc), DouDiZhuMenuActivity.class, catClassics));
        classics.add(new Entry("brotato", R.drawable.ic_brotato, context.getString(R.string.brotato), context.getString(R.string.brotato_desc), BrotatoActivity.class, catClassics));
        categories.add(new Category(catClassics, classics));

        // 益智类游戏：需要逻辑思考的解谜游戏
        List<Entry> puzzles = new ArrayList<>();
        puzzles.add(new Entry("game_2048", R.drawable.ic_2048, context.getString(R.string.game_2048), context.getString(R.string.game_2048_desc), Game2048Activity.class, catPuzzle));
        puzzles.add(new Entry("sudoku", R.drawable.ic_game, context.getString(R.string.game_sudoku), context.getString(R.string.game_sudoku_desc), SudokuActivity.class, catPuzzle));
        puzzles.add(new Entry("sokoban", R.drawable.ic_game, context.getString(R.string.game_sokoban), context.getString(R.string.game_sokoban_desc), SokobanActivity.class, catPuzzle));
        puzzles.add(new Entry("pipeline", R.drawable.ic_game, context.getString(R.string.game_pipeline), context.getString(R.string.game_pipeline_desc), PipelineActivity.class, catPuzzle));
        puzzles.add(new Entry("klotski", R.drawable.ic_game, context.getString(R.string.game_klotski), context.getString(R.string.game_klotski_desc), KlotskiActivity.class, catPuzzle));
        categories.add(new Category(catPuzzle, puzzles));

        // 休闲类游戏：轻松娱乐的休闲游戏
        List<Entry> casual = new ArrayList<>();
        casual.add(new Entry("breakout", R.drawable.ic_breakout, context.getString(R.string.game_breakout), context.getString(R.string.game_breakout_desc), BreakoutActivity.class, catCasual));
        casual.add(new Entry("whack", R.drawable.ic_game, context.getString(R.string.game_whack), context.getString(R.string.game_whack_desc), WhackActivity.class, catCasual));
        casual.add(new Entry("match", R.drawable.ic_game, context.getString(R.string.game_match), context.getString(R.string.game_match_desc), MatchActivity.class, catCasual));
        casual.add(new Entry("blackjack", R.drawable.ic_game, context.getString(R.string.game_blackjack), context.getString(R.string.game_blackjack_desc), BlackjackActivity.class, catCasual));
        casual.add(new Entry("checkers", R.drawable.ic_game, context.getString(R.string.game_checkers), context.getString(R.string.game_checkers_desc), CheckersActivity.class, catCasual));
        categories.add(new Category(catCasual, casual));

        // 反应力类游戏：考验反应速度和手眼协调的游戏
        List<Entry> reaction = new ArrayList<>();
        reaction.add(new Entry("flappy", R.drawable.ic_flappy, context.getString(R.string.game_flappy), context.getString(R.string.game_flappy_desc), FlappyActivity.class, catReaction));
        reaction.add(new Entry("tiles", R.drawable.ic_game, context.getString(R.string.game_tiles), context.getString(R.string.game_tiles_desc), TilesActivity.class, catReaction));
        reaction.add(new Entry("plane", R.drawable.ic_game, context.getString(R.string.game_plane), context.getString(R.string.game_plane_desc), PlaneActivity.class, catReaction));
        reaction.add(new Entry("rock", R.drawable.ic_game, context.getString(R.string.game_rock), context.getString(R.string.game_rock_desc), RockActivity.class, catReaction));
        reaction.add(new Entry("reaction", R.drawable.ic_game, context.getString(R.string.game_reaction), context.getString(R.string.game_reaction_desc), ReactionActivity.class, catReaction));
        categories.add(new Category(catReaction, reaction));

        // 其他类游戏：不便归类的杂项游戏
        List<Entry> others = new ArrayList<>();
        others.add(new Entry("tic", R.drawable.ic_game, context.getString(R.string.game_tic), context.getString(R.string.game_tic_desc), TicActivity.class, catOther));
        others.add(new Entry("memory", R.drawable.ic_game, context.getString(R.string.game_memory), context.getString(R.string.game_memory_desc), MemoryActivity.class, catOther));
        others.add(new Entry("guess", R.drawable.ic_game, context.getString(R.string.game_guess), context.getString(R.string.game_guess_desc), GuessActivity.class, catOther));
        others.add(new Entry("dice", R.drawable.ic_game, context.getString(R.string.game_dice), context.getString(R.string.game_dice_desc), DiceActivity.class, catOther));
        categories.add(new Category(catOther, others));

        return Collections.unmodifiableList(categories);
    }

    /**
     * 将分类列表扁平化为游戏条目列表
     * <p>
     * 遍历所有分类，将其中的游戏条目合并为一个统一的列表，
     * 适用于需要遍历所有游戏而不关心分类的场景。
     * </p>
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
     * 游戏分类
     * <p>
     * 表示一个游戏分类，包含分类名称和该分类下的所有游戏条目。
     * 使用final修饰符确保不可变，游戏列表同样不可修改。
     * </p>
     */
    public static final class Category {
        /** 分类名称，如"经典"、"益智"等 */
        public final String name;
        /** 该分类下的所有游戏条目，不可修改 */
        public final List<Entry> games;

        private Category(String name, List<Entry> games) {
            this.name = name;
            this.games = Collections.unmodifiableList(games);
        }
    }

    /**
     * 游戏条目
     * <p>
     * 表示一个具体的游戏注册信息，包含游戏ID、图标资源、显示名称、
     * 描述、对应的Activity类和所属分类名称。
     * 所有字段均为final，确保注册信息不可变。
     * </p>
     */
    public static final class Entry {
        /** 游戏唯一标识符，用于持久化存储和跨组件引用，如"gomoku"、"snake" */
        public final String id;
        /** 游戏图标资源ID */
        public final int iconRes;
        /** 游戏显示名称 */
        public final String name;
        /** 游戏描述文本 */
        public final String desc;
        /** 游戏对应的Activity类，用于启动游戏 */
        public final Class<?> activityClass;
        /** 所属分类名称 */
        public final String category;

        private Entry(String id, int iconRes, String name, String desc, Class<?> activityClass, String category) {
            this.id = id;
            this.iconRes = iconRes;
            this.name = name;
            this.desc = desc;
            this.activityClass = activityClass;
            this.category = category;
        }
    }
}
