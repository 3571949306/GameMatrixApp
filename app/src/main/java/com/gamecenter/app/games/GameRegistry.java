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

public final class GameRegistry {

    private GameRegistry() {
    }

    public static List<Category> getCategories(Context context) {
        List<Category> categories = new ArrayList<>();

        List<Entry> classics = new ArrayList<>();
        classics.add(new Entry("gomoku", R.drawable.ic_gomoku, context.getString(R.string.gomoku), context.getString(R.string.gomoku_desc), GomokuActivity.class, "经典"));
        classics.add(new Entry("go", R.drawable.ic_go, context.getString(R.string.game_go), context.getString(R.string.game_go_desc), GoActivity.class, "经典"));
        classics.add(new Entry("chinese_chess", R.drawable.ic_chess, context.getString(R.string.chinese_chess), context.getString(R.string.chinese_chess_desc), ChineseChessActivity.class, "经典"));
        classics.add(new Entry("snake", R.drawable.ic_snake, context.getString(R.string.snake), context.getString(R.string.snake_desc), SnakeActivity.class, "经典"));
        classics.add(new Entry("tetris", R.drawable.ic_tetris, context.getString(R.string.tetris), context.getString(R.string.tetris_desc), TetrisActivity.class, "经典"));
        classics.add(new Entry("doudizhu", R.drawable.ic_game, context.getString(R.string.game_doudizhu), context.getString(R.string.game_doudizhu_desc), DouDiZhuMenuActivity.class, "经典"));
        classics.add(new Entry("brotato", R.drawable.ic_brotato, context.getString(R.string.brotato), context.getString(R.string.brotato_desc), BrotatoActivity.class, "经典"));
        categories.add(new Category("经典", classics));

        List<Entry> puzzles = new ArrayList<>();
        puzzles.add(new Entry("game_2048", R.drawable.ic_2048, context.getString(R.string.game_2048), context.getString(R.string.game_2048_desc), Game2048Activity.class, "益智"));
        puzzles.add(new Entry("sudoku", R.drawable.ic_game, context.getString(R.string.game_sudoku), context.getString(R.string.game_sudoku_desc), SudokuActivity.class, "益智"));
        puzzles.add(new Entry("sokoban", R.drawable.ic_game, context.getString(R.string.game_sokoban), context.getString(R.string.game_sokoban_desc), SokobanActivity.class, "益智"));
        puzzles.add(new Entry("pipeline", R.drawable.ic_game, context.getString(R.string.game_pipeline), context.getString(R.string.game_pipeline_desc), PipelineActivity.class, "益智"));
        puzzles.add(new Entry("klotski", R.drawable.ic_game, context.getString(R.string.game_klotski), context.getString(R.string.game_klotski_desc), KlotskiActivity.class, "益智"));
        categories.add(new Category("益智", puzzles));

        List<Entry> casual = new ArrayList<>();
        casual.add(new Entry("breakout", R.drawable.ic_breakout, context.getString(R.string.game_breakout), context.getString(R.string.game_breakout_desc), BreakoutActivity.class, "休闲"));
        casual.add(new Entry("whack", R.drawable.ic_game, context.getString(R.string.game_whack), context.getString(R.string.game_whack_desc), WhackActivity.class, "休闲"));
        casual.add(new Entry("match", R.drawable.ic_game, context.getString(R.string.game_match), context.getString(R.string.game_match_desc), MatchActivity.class, "休闲"));
        casual.add(new Entry("blackjack", R.drawable.ic_game, context.getString(R.string.game_blackjack), context.getString(R.string.game_blackjack_desc), BlackjackActivity.class, "休闲"));
        casual.add(new Entry("checkers", R.drawable.ic_game, context.getString(R.string.game_checkers), context.getString(R.string.game_checkers_desc), CheckersActivity.class, "休闲"));
        categories.add(new Category("休闲", casual));

        List<Entry> reaction = new ArrayList<>();
        reaction.add(new Entry("flappy", R.drawable.ic_flappy, context.getString(R.string.game_flappy), context.getString(R.string.game_flappy_desc), FlappyActivity.class, "反应"));
        reaction.add(new Entry("tiles", R.drawable.ic_game, context.getString(R.string.game_tiles), context.getString(R.string.game_tiles_desc), TilesActivity.class, "反应"));
        reaction.add(new Entry("plane", R.drawable.ic_game, context.getString(R.string.game_plane), context.getString(R.string.game_plane_desc), PlaneActivity.class, "反应"));
        reaction.add(new Entry("rock", R.drawable.ic_game, context.getString(R.string.game_rock), context.getString(R.string.game_rock_desc), RockActivity.class, "反应"));
        reaction.add(new Entry("reaction", R.drawable.ic_game, context.getString(R.string.game_reaction), context.getString(R.string.game_reaction_desc), ReactionActivity.class, "反应"));
        categories.add(new Category("反应", reaction));

        List<Entry> others = new ArrayList<>();
        others.add(new Entry("tic", R.drawable.ic_game, context.getString(R.string.game_tic), context.getString(R.string.game_tic_desc), TicActivity.class, "其他"));
        others.add(new Entry("memory", R.drawable.ic_game, context.getString(R.string.game_memory), context.getString(R.string.game_memory_desc), MemoryActivity.class, "其他"));
        others.add(new Entry("guess", R.drawable.ic_game, context.getString(R.string.game_guess), context.getString(R.string.game_guess_desc), GuessActivity.class, "其他"));
        others.add(new Entry("dice", R.drawable.ic_game, context.getString(R.string.game_dice), context.getString(R.string.game_dice_desc), DiceActivity.class, "其他"));
        categories.add(new Category("其他", others));

        return Collections.unmodifiableList(categories);
    }

    public static List<Entry> flatten(List<Category> categories) {
        List<Entry> games = new ArrayList<>();
        for (Category category : categories) {
            games.addAll(category.games);
        }
        return Collections.unmodifiableList(games);
    }

    public static final class Category {
        public final String name;
        public final List<Entry> games;

        private Category(String name, List<Entry> games) {
            this.name = name;
            this.games = Collections.unmodifiableList(games);
        }
    }

    public static final class Entry {
        public final String id;
        public final int iconRes;
        public final String name;
        public final String desc;
        public final Class<?> activityClass;
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
