package com.gamecenter.app.game2048;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.database.AppDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;

/** 通过生产 Fragment、手势与存档实现，覆盖离开游戏后重新进入的玩家流程。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class Game2048SaveLifecycleTest {
    // 右滑后仅左下角可生成方块；生成 2 或 4 都没有可合并邻居，必定终局。
    private static final int[][] ONE_SWIPE_FROM_GAME_OVER = {
            {2, 4, 8, 16},
            {32, 64, 128, 256},
            {512, 1024, 2, 4},
            {8, 16, 32, 0}
    };

    private ActivityController<GameHostActivity> controller;
    private Game2048Fragment fragment;
    private SaveManager saves;
    private AppDatabase database;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("GameMatrix_saves", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("game_usage", Context.MODE_PRIVATE)
                .edit().clear().commit();
        setSingleton(SaveManager.class, "instance", null);
        saves = SaveManager.getInstance(context);
        // GameUsageStore 的终局统计仍执行真实 DAO；所有数据仅在 Robolectric 沙箱内。
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        setSingleton(AppDatabase.class, "INSTANCE", database);
    }

    @After
    public void tearDown() throws Exception {
        try {
            destroyHost();
        } finally {
            setSingleton(SaveManager.class, "instance", null);
            setSingleton(AppDatabase.class, "INSTANCE", null);
            if (database != null) database.close();
        }
    }

    @Test
    public void terminalSwipeSurvivesPauseDestroyAndRecreation() throws Exception {
        seedAutoSave(ONE_SWIPE_FROM_GAME_OVER, 100, false);
        openHost();
        assertBoardEquals(ONE_SWIPE_FROM_GAME_OVER, game().getBoardSnapshot());
        assertFalse(game().isGameOver());

        swipeRight();
        Game2048Game endedGame = game();
        assertTrue("真实右滑必须先进入终局", endedGame.isGameOver());
        int[][] finalBoard = endedGame.getBoardSnapshot();
        int finalScore = endedGame.getScore();
        destroyHost();
        openHost();

        assertNotSame("重建应创建新的生产游戏实例", endedGame, game());
        assertTrue("退出再进入不能复活终局前的旧 auto 存档", game().isGameOver());
        assertEquals(finalScore, game().getScore());
        assertBoardEquals(finalBoard, game().getBoardSnapshot());
        assertTrue(new JSONObject(saves.load("2048", "auto")).getBoolean("gameOver"));
    }

    @Test
    public void restartClearsOldAutoAndStartsTwoTileZeroScoreGame() throws Exception {
        int[][] terminalBoard = {
                {2, 4, 8, 16},
                {32, 64, 128, 256},
                {512, 1024, 2, 4},
                {2, 8, 16, 32}
        };
        seedAutoSave(terminalBoard, 100, true);
        openHost();
        assertTrue(game().isGameOver());
        assertNotNull(saves.load("2048", "auto"));

        LinearLayout root = (LinearLayout) fragment.requireView();
        LinearLayout buttonBar = (LinearLayout) root.getChildAt(4);
        Button restart = (Button) buttonBar.getChildAt(0);
        assertTrue("点击真实重玩按钮", restart.performClick());

        assertNull("重玩立即删除上一局 auto", saves.load("2048", "auto"));
        assertEquals(0, game().getScore());
        assertFalse(game().isGameOver());
        int[][] newBoard = game().getBoardSnapshot();
        assertEquals("新局恰好生成两个初始方块", 2, countInitialTiles(newBoard));

        destroyHost();
        openHost();
        assertEquals(0, game().getScore());
        assertFalse(game().isGameOver());
        assertBoardEquals(newBoard, game().getBoardSnapshot());
    }

    @Test
    public void unfinishedSwipeSurvivesPauseDestroyAndRecreation() throws Exception {
        int[][] initialBoard = {
                {2, 0, 0, 0},
                {0, 4, 0, 0},
                {0, 0, 8, 0},
                {0, 0, 0, 0}
        };
        seedAutoSave(initialBoard, 24, false);
        openHost();
        swipeRight();
        assertFalse(game().isGameOver());
        int[][] movedBoard = game().getBoardSnapshot();
        assertEquals("实际手势必须将首行方块右移", 2, movedBoard[0][3]);
        assertEquals(24, game().getScore());
        destroyHost();
        openHost();

        assertFalse(game().isGameOver());
        assertEquals(24, game().getScore());
        assertBoardEquals(movedBoard, game().getBoardSnapshot());
    }

    private void openHost() {
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new Game2048Fragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "2048").commitNow();
        View root = fragment.requireView();
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);
    }

    private void destroyHost() {
        if (controller != null) {
            ActivityController<GameHostActivity> old = controller;
            controller = null;
            old.pause().stop().destroy();
        }
    }

    private void swipeRight() {
        LinearLayout root = (LinearLayout) fragment.requireView();
        View boardContainer = root.getChildAt(3);
        float y = boardContainer.getTop() + boardContainer.getHeight() / 2f;
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(root, downTime, downTime, MotionEvent.ACTION_DOWN, 100f, y);
        dispatchTouch(root, downTime, downTime + 30, MotionEvent.ACTION_MOVE, 300f, y);
        dispatchTouch(root, downTime, downTime + 60, MotionEvent.ACTION_UP, 500f, y);
    }

    private static void dispatchTouch(View root, long downTime, long eventTime,
                                      int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            assertTrue("生产视图应接收完整手势", root.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private Game2048Game game() throws Exception {
        // 仅读取实际 Fragment 内的游戏实例，不注入替身或跳过手势/存档调用链。
        Field field = Game2048Fragment.class.getDeclaredField("game");
        field.setAccessible(true);
        return (Game2048Game) field.get(fragment);
    }

    private void seedAutoSave(int[][] board, int score, boolean gameOver) throws Exception {
        StringBuilder encodedBoard = new StringBuilder();
        for (int[] row : board) {
            for (int tile : row) {
                if (encodedBoard.length() > 0) encodedBoard.append(',');
                encodedBoard.append(tile);
            }
        }
        JSONObject json = new JSONObject();
        json.put("board", encodedBoard.toString());
        json.put("score", score);
        json.put("gameOver", gameOver);
        saves.save("2048", "auto", json.toString());
    }

    private static void assertBoardEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals("棋盘第 " + row + " 行", expected[row], actual[row]);
        }
    }

    private static int countInitialTiles(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int tile : row) {
                if (tile != 0) {
                    assertTrue("初始方块只能为 2 或 4", tile == 2 || tile == 4);
                    count++;
                }
            }
        }
        return count;
    }

    private static void setSingleton(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    /** 动态模块资源表没有宿主的这三个字符串；只隔离文案，不替换生产视图或行为。 */
    public static class GameHostActivity extends FragmentActivity {
        private Resources strings;

        @Override
        public Resources getResources() {
            if (strings == null) {
                Resources delegate = super.getResources();
                strings = new Resources(delegate.getAssets(), delegate.getDisplayMetrics(),
                        delegate.getConfiguration()) {
                    @Override
                    public String getString(int id) {
                        if (id == R.string.game_btn_restart) return "Restart";
                        return super.getString(id);
                    }

                    @Override
                    public String getString(int id, Object... args) {
                        if (id == R.string.game_score_alt_format) return "Score: " + args[0];
                        if (id == R.string.game_high_score_format) return "High score: " + args[0];
                        return super.getString(id, args);
                    }
                };
            }
            return strings;
        }
    }
}
