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

public class Game2048Activity extends AppCompatActivity {

    private static final String GAME_ID = "2048";
    private static final String SLOT_AUTO = "auto";

    private Game2048View game2048View;
    private Game2048Game game;
    private GestureDetector gestureDetector;
    private TextView tvScore;
    private TextView tvHighScore;
    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private int highScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        saveManager = SaveManager.getInstance(this);
        usageStore = new GameUsageStore(this);

        tvScore = findViewById(R.id.tv_game_score);
        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_2048);

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
        ((LinearLayout) tvScore.getParent()).addView(tvHighScore, 2);

        game2048View = new Game2048View(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(game2048View);

        game = new Game2048Game();
        loadSavedState();
        game2048View.setGame(game);
        highScore = loadHighScore();
        updateScore();

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

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
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showGame2048Tutorial(this));
    }

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

    @Override
    protected void onPause() {
        super.onPause();
        if (game != null && !game.isGameOver()) {
            saveGameState();
        }
    }

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

    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception e) {
            // 忽略
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
