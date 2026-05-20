package com.gamecenter.app.games.snake;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * 贪吃蛇游戏 Activity
 *
 * <p>作为贪吃蛇游戏的入口界面，负责管理游戏生命周期、用户交互和界面切换。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>管理难度选择面板与游戏控制面板之间的切换</li>
 *   <li>通过 Handler+Runnable 实现定时游戏帧驱动</li>
 *   <li>支持手势滑动和方向按钮两种操控方式</li>
 *   <li>游戏结束时记录分数到 GameUsageStore</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 Handler.postDelayed 循环调度而非 Timer/Thread，确保 UI 更新在主线程执行</li>
 *   <li>难度通过不同的刷新间隔（speed）实现：简单300ms、中等180ms、困难100ms</li>
 *   <li>返回键在游戏中先回到难度选择，再按才退出 Activity</li>
 * </ul>
 *
 * <p>布局：res/layout/activity_snake.xml</p>
 */
public class SnakeActivity extends AppCompatActivity {

    /** 游戏画面自定义 View */
    private SnakeView snakeView;

    /** 难度选择面板，游戏开始前显示 */
    private LinearLayout difficultyPanel;

    /** 方向控制按钮面板，游戏进行中显示 */
    private LinearLayout controlPanel;

    /** 得分显示面板，游戏进行中显示 */
    private LinearLayout scorePanel;

    /** 贪吃蛇游戏逻辑实例 */
    private SnakeGame game;

    /** 主线程 Handler，用于定时调度游戏帧 */
    private Handler gameHandler;

    /** 当前正在执行的游戏帧 Runnable */
    private Runnable gameRunnable;

    /** 游戏是否正在运行中 */
    private boolean isRunning = false;

    /** 游戏使用记录存储，用于持久化分数 */
    private GameUsageStore usageStore;

    /** 游戏唯一标识，用于分数记录 */
    private static final String GAME_ID = "snake";

    /**
     * Activity 创建回调。
     *
     * <p>初始化视图绑定、游戏实例、手势监听和所有按钮事件。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake);

        usageStore = new GameUsageStore(this);
        gameHandler = new Handler(Looper.getMainLooper());

        snakeView = findViewById(R.id.snake_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        scorePanel = findViewById(R.id.score_panel);

        game = new SnakeGame();
        snakeView.setGame(game);

        // 游戏结束后点击画面可重新开始（默认中等难度）
        snakeView.setOnClickListener(v -> {
            if (game.isGameOver()) {
                startGame(2);
            }
        });

        // 手势滑动控制：通过计算触摸起点和终点的偏移量判断滑动方向
        snakeView.setOnTouchListener(new View.OnTouchListener() {
            private float touchStartX;
            private float touchStartY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (game.isGameOver() || !isRunning) return false;

                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    return true;
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx = event.getX() - touchStartX;
                    float dy = event.getY() - touchStartY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);

                    // 滑动距离需超过30像素阈值才视为有效滑动，避免误触
                    if (Math.max(absDx, absDy) > 30) {
                        if (absDx > absDy) {
                            game.setDirection(dx > 0 ? SnakeGame.Direction.RIGHT : SnakeGame.Direction.LEFT);
                        } else {
                            game.setDirection(dy > 0 ? SnakeGame.Direction.DOWN : SnakeGame.Direction.UP);
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        MaterialButton btnEasy = findViewById(R.id.btn_easy);
        MaterialButton btnMedium = findViewById(R.id.btn_medium);
        MaterialButton btnHard = findViewById(R.id.btn_hard);
        MaterialButton btnTutorial = findViewById(R.id.btn_tutorial);

        btnEasy.setOnClickListener(v -> startGame(1));
        btnMedium.setOnClickListener(v -> startGame(2));
        btnHard.setOnClickListener(v -> startGame(3));
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showSnakeTutorial(this));

        MaterialButton btnRestart = findViewById(R.id.btn_restart);
        MaterialButton btnTutorialIngame = findViewById(R.id.btn_tutorial_ingame);
        btnRestart.setOnClickListener(v -> handleRestart());
        btnTutorialIngame.setOnClickListener(v -> GameTutorialHelper.showSnakeTutorial(this));

        // 方向按钮控制：仅在游戏运行中响应
        MaterialButton btnUp = findViewById(R.id.btn_up);
        MaterialButton btnDown = findViewById(R.id.btn_down);
        MaterialButton btnLeft = findViewById(R.id.btn_left);
        MaterialButton btnRight = findViewById(R.id.btn_right);

        btnUp.setOnClickListener(v -> {
            if (isRunning) game.setDirection(SnakeGame.Direction.UP);
        });
        btnDown.setOnClickListener(v -> {
            if (isRunning) game.setDirection(SnakeGame.Direction.DOWN);
        });
        btnLeft.setOnClickListener(v -> {
            if (isRunning) game.setDirection(SnakeGame.Direction.LEFT);
        });
        btnRight.setOnClickListener(v -> {
            if (isRunning) game.setDirection(SnakeGame.Direction.RIGHT);
        });
    }

    /**
     * 开始新游戏。
     *
     * <p>停止当前游戏循环，重置游戏状态，根据难度设定速度，
     * 切换界面面板可见性，并启动新的游戏帧循环。</p>
     *
     * @param difficulty 难度等级：1=简单(300ms)、2=中等(180ms)、3=困难(100ms)
     */
    private void startGame(int difficulty) {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);

        game.reset();

        // 根据难度设置速度（刷新间隔，单位毫秒，值越小速度越快）
        int speed;
        switch (difficulty) {
            case 1: speed = 300; break;
            case 3: speed = 100; break;
            default: speed = 180; break;
        }

        game = new SnakeGame();
        snakeView.setGame(game);

        // 切换面板：隐藏难度选择，显示控制按钮和得分
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        scorePanel.setVisibility(View.VISIBLE);

        updateScore();

        isRunning = true;
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !game.isGameOver()) {
                    game.move();
                    snakeView.invalidate();
                    updateScore();
                    gameHandler.postDelayed(this, game.getSpeed());
                } else if (game.isGameOver()) {
                    // 游戏结束：停止循环并记录分数
                    isRunning = false;
                    snakeView.invalidate();
                    usageStore.recordScore(GAME_ID, game.getScore());
                }
            }
        };
        gameHandler.postDelayed(gameRunnable, game.getSpeed());
    }

    /**
     * 更新界面上的得分显示。
     *
     * <p>从游戏实例获取当前分数并更新 TextView。</p>
     */
    private void updateScore() {
        android.widget.TextView tvScore = findViewById(R.id.tv_score);
        if (tvScore != null) {
            tvScore.setText("得分: " + game.getScore());
        }
    }

    /**
     * 处理重新开始操作。
     *
     * <p>停止游戏循环，重置游戏状态，切换回难度选择面板。</p>
     */
    private void handleRestart() {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);
        game.reset();
        snakeView.invalidate();
        // 切换回难度选择面板
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        scorePanel.setVisibility(View.GONE);
    }

    /**
     * 返回键处理。
     *
     * <p>如果当前在游戏中，先回到难度选择面板；如果已在难度选择面板，则执行默认返回行为。</p>
     */
    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            handleRestart();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Activity 销毁回调。
     *
     * <p>停止游戏循环，清除所有 Handler 回调，释放引用以避免内存泄漏。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (gameHandler != null) {
            gameHandler.removeCallbacksAndMessages(null);
        }
        gameRunnable = null;
        game = null;
        snakeView = null;
        gameHandler = null;
    }
}
