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
 * 功能：
 * - 三种难度，速度不同
 * - 支持手势滑动控制
 * - 支持方向按钮控制
 * - 实时得分显示
 *
 * 布局：res/layout/activity_snake.xml
 */
public class SnakeActivity extends AppCompatActivity {

    private SnakeView snakeView;
    private LinearLayout difficultyPanel;
    private LinearLayout controlPanel;
    private LinearLayout scorePanel;

    private SnakeGame game;
    private Handler gameHandler;
    private Runnable gameRunnable;
    private boolean isRunning = false;
    private GameUsageStore usageStore;
    private static final String GAME_ID = "snake";

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

        snakeView.setOnClickListener(v -> {
            if (game.isGameOver()) {
                startGame(2);
            }
        });

        // 手势滑动控制
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

        // 方向按钮控制
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
            case 1: speed = 300; break;
            case 3: speed = 100; break;
            default: speed = 180; break;
        }

        game = new SnakeGame();
        snakeView.setGame(game);

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
                    isRunning = false;
                    snakeView.invalidate();
                    usageStore.recordScore(GAME_ID, game.getScore());
                }
            }
        };
        gameHandler.postDelayed(gameRunnable, game.getSpeed());
    }

    private void updateScore() {
        android.widget.TextView tvScore = findViewById(R.id.tv_score);
        if (tvScore != null) {
            tvScore.setText("得分: " + game.getScore());
        }
    }

    private void handleRestart() {
        isRunning = false;
        gameHandler.removeCallbacks(gameRunnable);
        game.reset();
        snakeView.invalidate();
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        scorePanel.setVisibility(View.GONE);
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
