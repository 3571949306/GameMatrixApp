package com.gamecenter.app.game2048;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;
import org.json.JSONObject;

public class Game2048Fragment extends Fragment {

    private static final String TAG = "Game2048Fragment";
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F5);
        root.setPadding(0, (int) (28 * dp), 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("2048");
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = (int) (16 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(18);
        tvScore.setTextColor(0xFF757575);
        tvScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.gravity = Gravity.CENTER;
        scoreLp.topMargin = (int) (8 * dp);
        tvScore.setLayoutParams(scoreLp);
        root.addView(tvScore);

        tvHighScore = new TextView(ctx);
        tvHighScore.setTextSize(16);
        tvHighScore.setTextColor(0xFF888888);
        tvHighScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams highScoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        highScoreLp.gravity = Gravity.CENTER;
        highScoreLp.topMargin = (int) (4 * dp);
        tvHighScore.setLayoutParams(highScoreLp);
        root.addView(tvHighScore);

        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.setMargins((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp));
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        game2048View = new Game2048View(ctx);
        gameContainer.addView(game2048View);

        LinearLayout buttonBar = new LinearLayout(ctx);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), (int) (16 * dp));
        buttonBar.setLayoutParams(barLp);
        root.addView(buttonBar);

        Button btnRestart = new Button(ctx);
        btnRestart.setText("重新开始");
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                (int) (120 * dp), (int) (48 * dp));
        restartLp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnRestart.setLayoutParams(restartLp);
        buttonBar.addView(btnRestart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);

        game = new Game2048Game();
        loadSavedState();
        game2048View.setGame(game);
        highScore = loadHighScore();
        updateScore();

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
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

        LinearLayout buttonBar = (LinearLayout) ((LinearLayout) view).getChildAt(4);
        Button btnRestart = (Button) buttonBar.getChildAt(0);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            game2048View.invalidate();
            updateScore();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        });

        requireView().setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
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
    public void onPause() {
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
            Log.w(TAG, "存档损坏", e);
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
            Log.w(TAG, "存档错误", e);
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
            Log.w(TAG, "存档操作失败", e);
        }
    }
}
