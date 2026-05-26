package com.gamecenter.app.games.tetris;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 俄罗斯方块游戏 Activity
 *
 * <p>作为俄罗斯方块游戏的入口界面，负责管理游戏生命周期、用户交互和界面切换。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>管理难度选择面板与游戏控制面板之间的切换</li>
 *   <li>通过 Handler+Runnable 实现方块自动下落的定时驱动</li>
 *   <li>支持旋转、左右移动、加速下落四种操控</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 Handler.postDelayed 循环调度，确保 UI 更新在主线程执行</li>
 *   <li>难度通过不同的初始下落间隔实现：简单600ms、中等300ms、困难150ms</li>
 *   <li>上按钮映射为旋转而非上移，符合俄罗斯方块标准操作</li>
 *   <li>返回键在游戏中先回到难度选择，再按才退出 Activity</li>
 * </ul>
 *
 * <p>布局：res/layout/activity_tetris.xml</p>
 */
public class TetrisActivity extends AppCompatActivity {

    /** 游戏画面自定义 View */
    private TetrisView tetrisView;

    /** 难度选择面板，游戏开始前显示 */
    private LinearLayout difficultyPanel;

    /** 控制按钮面板，游戏进行中显示 */
    private LinearLayout controlPanel;

    /** 俄罗斯方块游戏逻辑实例 */
    private TetrisGame game;

    /** 主线程 Handler，用于定时调度方块下落 */
    private Handler gameHandler;

    /** 当前正在执行的下落帧 Runnable */
    private Runnable gameRunnable;

    /** 游戏是否正在运行中 */
    private boolean isRunning = false;

    /**
     * Activity 创建回调。
     *
     * <p>初始化视图绑定、游戏实例和所有按钮事件。
     * 上按钮对应旋转操作，下按钮对应加速下落，左右按钮对应水平移动。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tetris);

        gameHandler = new Handler();

        tetrisView = findViewById(R.id.tetris_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);

        game = new TetrisGame();
        tetrisView.setGame(game);

        // 游戏结束后点击画面可重新开始（默认中等难度）
        tetrisView.setOnClickListener(v -> {
            if (game.isGameOver()) {
                startGame(2);
            }
        });

        MaterialButton btnEasy = findViewById(R.id.btn_easy);
        MaterialButton btnMedium = findViewById(R.id.btn_medium);
        MaterialButton btnHard = findViewById(R.id.btn_hard);
        MaterialButton btnTutorial = findViewById(R.id.btn_tutorial);

        btnEasy.setOnClickListener(v -> startGame(1));
        btnMedium.setOnClickListener(v -> startGame(2));
        btnHard.setOnClickListener(v -> startGame(3));
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showTetrisTutorial(this));

        MaterialButton btnRestart = findViewById(R.id.btn_restart);
        MaterialButton btnTutorialIngame = findViewById(R.id.btn_tutorial_ingame);
        btnRestart.setOnClickListener(v -> handleRestart());
        btnTutorialIngame.setOnClickListener(v -> GameTutorialHelper.showTetrisTutorial(this));

        // 控制按钮：上=旋转，下=加速下落，左/右=水平移动
        MaterialButton btnUp = findViewById(R.id.btn_up);
        MaterialButton btnDown = findViewById(R.id.btn_down);
        MaterialButton btnLeft = findViewById(R.id.btn_left);
        MaterialButton btnRight = findViewById(R.id.btn_right);

        btnUp.setOnClickListener(v -> {
            if (isRunning && !game.isGameOver()) {
                game.rotate();
                tetrisView.invalidate();
            }
        });

        btnDown.setOnClickListener(v -> {
            if (isRunning && !game.isGameOver()) {
                game.moveDown();
                tetrisView.invalidate();
            }
        });

        btnLeft.setOnClickListener(v -> {
            if (isRunning && !game.isGameOver()) {
                game.moveLeft();
                tetrisView.invalidate();
            }
        });

        btnRight.setOnClickListener(v -> {
            if (isRunning && !game.isGameOver()) {
                game.moveRight();
                tetrisView.invalidate();
            }
        });
    }

    /**
     * 开始新游戏。
     *
     * <p>停止当前游戏循环，重置游戏状态，根据难度设定初始下落速度，
     * 切换界面面板可见性，并启动方块自动下落循环。</p>
     *
     * @param difficulty 难度等级：1=简单(600ms)、2=中等(300ms)、3=困难(150ms)
     */
    private void startGame(int difficulty) {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);

        game.reset();

        // 根据难度设置初始下落间隔（毫秒，值越小速度越快）
        int speed;
        switch (difficulty) {
            case 1: speed = 600; break;
            case 3: speed = 150; break;
            default: speed = 300; break;
        }

        // 切换面板：隐藏难度选择，显示控制按钮
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);

        isRunning = true;
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !game.isGameOver()) {
                    game.moveDown();
                    tetrisView.invalidate();
                    // 使用游戏动态间隔，随等级提升速度会加快
                    gameHandler.postDelayed(this, game.getDropInterval());
                } else if (game.isGameOver()) {
                    isRunning = false;
                    tetrisView.invalidate();
                }
            }
        };
        gameHandler.postDelayed(gameRunnable, game.getDropInterval());
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
        tetrisView.invalidate();
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
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
        tetrisView = null;
    }
}
