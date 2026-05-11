package com.gamecenter.app.games.tetris;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 俄罗斯方块游戏 Activity
 *
 * 功能：
 * - 三种难度，下落速度不同
 * - 旋转/左右移动/加速下落控制
 * - 自动下落
 *
 * 布局：res/layout/activity_tetris.xml
 */
public class TetrisActivity extends AppCompatActivity {

    private TetrisView tetrisView;
    private LinearLayout difficultyPanel;
    private LinearLayout controlPanel;

    private TetrisGame game;
    private Handler gameHandler;
    private Runnable gameRunnable;
    private boolean isRunning = false;

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

        // 控制按钮
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
     * 开始新游戏
     * @param difficulty 难度 1-3
     */
    private void startGame(int difficulty) {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);

        game.reset();

        // 根据难度设置速度
        int speed;
        switch (difficulty) {
            case 1: speed = 600; break;
            case 3: speed = 150; break;
            default: speed = 300; break;
        }

        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);

        isRunning = true;
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !game.isGameOver()) {
                    game.moveDown();
                    tetrisView.invalidate();
                    gameHandler.postDelayed(this, game.getDropInterval());
                } else if (game.isGameOver()) {
                    isRunning = false;
                    tetrisView.invalidate();
                }
            }
        };
        gameHandler.postDelayed(gameRunnable, game.getDropInterval());
    }

    private void handleRestart() {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);
        game.reset();
        tetrisView.invalidate();
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            handleRestart();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);
    }
}
