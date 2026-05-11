package com.gamecenter.app.games.gomoku;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GomokuActivity extends AppCompatActivity {

    private static final String GAME_ID = "gomoku";
    private GomokuView gomokuView;
    private LinearLayout difficultyPanel;
    private LinearLayout controlPanel;
    private TextView tvDifficultyLabel;
    private SeekBar seekDifficulty;

    private GomokuGame game;
    private GomokuAI ai;
    private int aiPlayer = GomokuGame.WHITE;
    private int aiDifficulty = 3;
    private GameUsageStore usageStore;

    private Handler mainHandler;
    private ExecutorService aiExecutor;

    private volatile boolean aiThinking = false;

    private static final String[] DIFFICULTY_NAMES = {
        "初识五子棋", "初级棋手", "入门学生", "中等棋力", "高手水平", "精湛大师"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gomoku);

        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        gomokuView = findViewById(R.id.gomoku_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        seekDifficulty = findViewById(R.id.seek_difficulty);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);

        game = new GomokuGame();
        ai = new GomokuAI(aiDifficulty);
        gomokuView.setGame(game);
        usageStore = new GameUsageStore(this);

        gomokuView.setOnCellClickListener((x, y) -> handleCellClick(x, y));
        gomokuView.setOnGameOverListener(this::handleGameOver);

        seekDifficulty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                aiDifficulty = progress + 1;
                if (tvDifficultyLabel != null) {
                    tvDifficultyLabel.setText("难度：" + DIFFICULTY_NAMES[progress]
                            + " (" + aiDifficulty + "/6)");
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame(aiDifficulty));
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, GomokuOnlineActivity.class);
            startActivity(intent);
        });
    }

    private void startGame(int difficulty) {
        aiDifficulty = difficulty;
        ai = new GomokuAI(difficulty);
        game.reset();
        gomokuView.setGame(game);
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        gomokuView.invalidate();
    }

    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        if (game.getCurrentPlayer() != GomokuGame.BLACK) return;
        if (aiThinking) return;
        if (!game.isValidMove(x, y)) return;

        game.makeMove(x, y, GomokuGame.BLACK);
        game.switchPlayer();
        gomokuView.invalidate();

        if (game.checkGameOver()) {
            gomokuView.invalidate();
            return;
        }

        aiThinking = true;
        gomokuView.clearHover();
        gomokuView.invalidate();
        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(game, aiPlayer);
            mainHandler.postDelayed(() -> {
                if (bestMove != null) {
                    game.makeMove(bestMove[0], bestMove[1], GomokuGame.WHITE);
                    game.switchPlayer();
                    game.checkGameOver();
                }
                aiThinking = false;
                gomokuView.invalidate();
            }, 300);
        });
    }

    private void handleUndo() {
        if (aiThinking) return;
        int undoCount = game.undoLastMoves(1);
        if (undoCount > 0) {
            gomokuView.invalidate();
        }
    }

    private void handleRestart() {
        aiThinking = false;
        game.reset();
        gomokuView.clearHover();
        gomokuView.setGame(game);
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        gomokuView.invalidate();
    }

    private void handleGameOver(Integer winner) {
        if (winner != null && winner == GomokuGame.BLACK) {
            usageStore.recordWin(GAME_ID);
        } else if (winner != null && winner == GomokuGame.WHITE) {
            usageStore.recordLoss(GAME_ID);
        }
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
        aiExecutor.shutdownNow();
    }
}
