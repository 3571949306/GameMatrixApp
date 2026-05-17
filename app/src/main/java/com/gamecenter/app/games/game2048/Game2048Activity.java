package com.gamecenter.app.games.game2048;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;

/**
 * 2048 游戏 Activity
 *
 * <p>作为2048游戏的入口界面，负责管理游戏生命周期、用户交互、
 * 分数显示和游戏状态的持久化存储。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>通过 GestureDetector 识别滑动手势，映射为上下左右移动</li>
 *   <li>实时显示当前分数和历史最高分</li>
 *   <li>游戏状态自动存档（onPause 时保存）和读档（onCreate 时恢复）</li>
 *   <li>最高分独立持久化到 SaveManager</li>
 *   <li>游戏结束时记录分数到 GameUsageStore</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用通用布局 activity_game_simple，通过代码动态添加 Game2048View</li>
 *   <li>最高分 TextView 以代码动态插入到布局中（addView 到得分 TextView 的父容器）</li>
 *   <li>游戏状态以 JSON 格式序列化（棋盘为逗号分隔字符串），通过 SaveManager 持久化</li>
 *   <li>滑动手势阈值50像素，避免误触</li>
 *   <li>重新开始时同时删除自动存档，确保干净重置</li>
 * </ul>
 */
public class Game2048Activity extends AppCompatActivity {

    /** 游戏唯一标识，用于存档和分数记录 */
    private static final String GAME_ID = "2048";

    /** 自动存档的存档槽名称 */
    private static final String SLOT_AUTO = "auto";

    /** 2048游戏画面自定义 View */
    private Game2048View game2048View;

    /** 2048游戏逻辑实例 */
    private Game2048Game game;

    /** 手势检测器，用于识别滑动手势 */
    private GestureDetector gestureDetector;

    /** 当前分数 TextView */
    private TextView tvScore;

    /** 最高分 TextView（代码动态创建并插入布局） */
    private TextView tvHighScore;

    /** 存档管理器，用于游戏状态的保存和恢复 */
    private SaveManager saveManager;

    /** 游戏使用记录存储，用于持久化分数 */
    private GameUsageStore usageStore;

    /** 历史最高分 */
    private int highScore;

    /**
     * Activity 创建回调。
     *
     * <p>初始化视图绑定、游戏实例、手势检测器、分数显示和存档恢复。
     * 最高分 TextView 以代码方式动态创建并插入到布局中。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        saveManager = SaveManager.getInstance(this);
        usageStore = new GameUsageStore(this);

        tvScore = findViewById(R.id.tv_game_score);
        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_2048);

        // 动态创建最高分 TextView 并插入到得分区域
        tvHighScore = new TextView(this);
        tvHighScore.setTextSize(16);
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
        tvHighScore.setTextColor(typedValue.data);
        tvHighScore.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        lp.topMargin = 4;
        tvHighScore.setLayoutParams(lp);
        // 插入到得分 TextView 父容器的第3个位置（索引2）
        ((LinearLayout) tvScore.getParent()).addView(tvHighScore, 2);

        // 动态创建 Game2048View 并添加到布局中
        game2048View = new Game2048View(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(game2048View);

        game = new Game2048Game();
        loadSavedState();
        game2048View.setGame(game);
        highScore = loadHighScore();
        updateScore();

        // 手势检测：识别滑动手势并映射为游戏移动方向
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                // 判断主滑动方向，阈值50像素避免误触
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 50) {
                        game.moveRight();
                    } else if (dx < -50) {
                        game.moveLeft();
                    }
                } else {
                    if (dy > 50) {
                        game.moveDown();
                    } else if (dy < -50) {
                        game.moveUp();
                    }
                }
                game2048View.invalidate();
                updateScore();
                return true;
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            game2048View.invalidate();
            updateScore();
            // 重新开始时删除自动存档
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showGame2048Tutorial(this));
    }

    /**
     * 更新分数显示。
     *
     * <p>更新当前分数和历史最高分。如果当前分数超过最高分，
     * 则更新最高分并持久化。游戏结束时记录分数到 GameUsageStore。</p>
     */
    private void updateScore() {
        int currentScore = game.getScore();
        tvScore.setText("分数: " + currentScore);

        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        tvHighScore.setText("最高分: " + highScore);

        if (game.isGameOver()) {
            game2048View.invalidate();
            usageStore.recordScore(GAME_ID, highScore);
        }
    }

    /**
     * Activity 暂停回调。
     *
     * <p>在游戏未结束时自动保存游戏状态，确保用户切换应用后可以恢复进度。</p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (game != null && !game.isGameOver()) {
            saveGameState();
        }
    }

    /**
     * 从存档中恢复游戏状态。
     *
     * <p>从 SaveManager 加载自动存档，解析 JSON 中的棋盘数据、分数和游戏结束状态，
     * 并恢复到游戏实例中。如果存档损坏则静默忽略。</p>
     *
     * <p>存档格式：{"board": "0,0,2,4,...", "score": 100, "gameOver": false}
     * 棋盘数据为16个逗号分隔的整数值，按行优先排列。</p>
     */
    private void loadSavedState() {
        String saved = saveManager.load(GAME_ID, SLOT_AUTO);
        if (saved == null) return;

        try {
            JSONObject obj = new JSONObject(saved);
            int score = obj.getInt("score");
            boolean gameOver = obj.optBoolean("gameOver", false);
            int[][] board = new int[4][4];
            String boardStr = obj.getString("board");
            String[] values = boardStr.split(",");
            // 按行优先解析4×4棋盘
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    board[y][x] = Integer.parseInt(values[y * 4 + x]);
                }
            }
            game.restoreState(board, score, gameOver);
        } catch (Exception e) {
            // 存档损坏，忽略
        }
    }

    /**
     * 保存当前游戏状态到存档。
     *
     * <p>将棋盘数据序列化为逗号分隔字符串，连同分数和游戏结束状态
     * 组成 JSON 对象，通过 SaveManager 持久化。保存失败时静默忽略。</p>
     */
    private void saveGameState() {
        try {
            StringBuilder boardStr = new StringBuilder();
            int[][] board = game.getBoardSnapshot();
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (boardStr.length() > 0) boardStr.append(",");
                    boardStr.append(board[y][x]);
                }
            }
            JSONObject obj = new JSONObject();
            obj.put("board", boardStr.toString());
            obj.put("score", game.getScore());
            obj.put("gameOver", game.isGameOver());
            saveManager.save(GAME_ID, SLOT_AUTO, obj.toString());
        } catch (Exception e) {
            // 忽略存档错误
        }
    }

    /**
     * 从持久化存储中加载历史最高分。
     *
     * <p>最高分独立于游戏状态存储在 SaveManager 的 progress 区域，
     * 即使游戏状态被重置，最高分仍然保留。</p>
     *
     * @return 历史最高分，如果无记录则返回0
     */
    private int loadHighScore() {
        String progressJson = saveManager.loadProgress(GAME_ID);
        if (progressJson != null) {
            try {
                JSONObject obj = new JSONObject(progressJson);
                return obj.optInt("highScore", 0);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 保存历史最高分到持久化存储。
     *
     * @param score 要保存的最高分值
     */
    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 处理触摸事件，委托给手势检测器。
     *
     * <p>优先由手势检测器处理（识别滑动手势），
     * 如果手势检测器未消费事件则交给父类处理。</p>
     *
     * @param event 触摸事件
     * @return 事件是否被消费
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
