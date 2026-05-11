package com.gamecenter.app.games.chinesechess;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChineseChessActivity extends AppCompatActivity {

    private static final String GAME_ID = "chinese_chess";
    private ChineseChessView chessView;
    private LinearLayout difficultyPanel;
    private LinearLayout controlPanel;
    private TextView tvStatus;
    private SeekBar seekDifficulty;
    private TextView tvDifficultyLabel;

    private ChineseChessGame game;
    private ChineseChessAI ai;
    private int aiDifficulty = 3;

    private Handler uiHandler;
    private ExecutorService aiExecutor;

    private volatile boolean isProcessing = false;

    private int[] selectedPos = null;
    private List<int[]> currentValidMoves = null;
    private GameUsageStore usageStore;

    private static final String[] DIFFICULTY_NAMES = {
        "初识象棋", "初级棋手", "入门学生", "中等棋力", "高手水平", "国家大师"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chinese_chess);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        chessView = findViewById(R.id.chess_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        tvStatus = findViewById(R.id.tv_status);
        seekDifficulty = findViewById(R.id.seek_difficulty);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);

        game = new ChineseChessGame();
        chessView.bindGame(game);
        usageStore = new GameUsageStore(this);
        chessView.setOnCellClickListener(this::onCellTap);

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

        findViewById(R.id.btn_start_game).setOnClickListener(v -> beginGame(aiDifficulty));
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> undoLastMove());
        findViewById(R.id.btn_restart).setOnClickListener(v -> restartGame());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChineseChessOnlineActivity.class);
            startActivity(intent);
        });
    }

    private void beginGame(int difficulty) {
        isProcessing = false;
        ai = new ChineseChessAI(difficulty);
        game.reset();

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearLastMove();

        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        showStatus("你的回合 - 红方先行");
    }

    private void showStatus(String msg) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    private void onCellTap(int col, int row) {
        if (isProcessing) return;
        if (game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;

        ChineseChessGame.Piece target = game.getBoard()[row][col];

        if (selectedPos != null) {
            boolean isValidMove = false;
            if (currentValidMoves != null) {
                for (int[] mv : currentValidMoves) {
                    if (mv[0] == col && mv[1] == row) {
                        isValidMove = true;
                        break;
                    }
                }
            }

            if (isValidMove) {
                performPlayerMove(selectedPos[0], selectedPos[1], col, row);
                return;
            }

            if (target != null && target.side == ChineseChessGame.Side.RED) {
                selectedPos = new int[]{col, row};
                currentValidMoves = game.getLegalMoves(col, row);
                chessView.setSelected(col, row, currentValidMoves);
            } else {
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
            }
        } else {
            if (target != null && target.side == ChineseChessGame.Side.RED) {
                selectedPos = new int[]{col, row};
                currentValidMoves = game.getLegalMoves(col, row);
                chessView.setSelected(col, row, currentValidMoves);
            }
        }
    }

    private void performPlayerMove(int fromX, int fromY, int toX, int toY) {
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        isProcessing = true;

        chessView.animateMove(fromX, fromY, toX, toY, () -> {
            ChineseChessGame.MoveRecord rec = game.makeMoveSafe(fromX, fromY, toX, toY);
            if (rec == null) {
                isProcessing = false;
                return;
            }

            game.getMoveHistory().add(rec);
            chessView.setLastMove(fromX, fromY, toX, toY);
            chessView.invalidate();

            game.switchSide();
            game.checkGameOver();

            if (game.isGameOver()) {
                isProcessing = false;
                showStatus("🎉 恭喜获胜！");
                usageStore.recordWin(GAME_ID);
                return;
            }

            startAITurn();
        });
    }

    private void startAITurn() {
        isProcessing = true;
        chessView.setLocked(true);
        showStatus("AI思考中...");
        final long startMs = System.currentTimeMillis();

        aiExecutor.execute(() -> {
            int[] result = ai.getBestMove(game);
            if (result == null) {
                List<int[]> all = game.getAllMoves(ChineseChessGame.Side.BLACK);
                if (!all.isEmpty()) result = all.get(0);
            }
            final int[] move = result;

            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(1000 - elapsed, 100);
            uiHandler.postDelayed(() -> applyAIMove(move), delay);
        });
    }

    private void applyAIMove(int[] move) {
        if (move != null) {
            chessView.animateMove(move[0], move[1], move[2], move[3], () -> {
                ChineseChessGame.MoveRecord rec = game.makeMoveSafe(move[0], move[1], move[2], move[3]);
                if (rec != null) {
                    game.getMoveHistory().add(rec);
                    chessView.setLastMove(move[0], move[1], move[2], move[3]);
                    game.switchSide();
                }

                isProcessing = false;
                chessView.setLocked(false);
                chessView.invalidate();
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
                showStatus("你的回合");

                game.checkGameOver();
                if (game.isGameOver()) {
                    showStatus("AI获胜！");
                    usageStore.recordLoss(GAME_ID);
                }
            });
        } else {
            isProcessing = false;
            chessView.setLocked(false);
            chessView.invalidate();
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            showStatus("你的回合");

            game.checkGameOver();
            if (game.isGameOver()) {
                showStatus("AI获胜！");
                usageStore.recordLoss(GAME_ID);
            }
        }
    }

    private void undoLastMove() {
        if (isProcessing) return;
        int undone = game.undoLastMoves(1);
        if (undone > 0) {
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            chessView.clearLastMove();

            List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
            if (history.size() > 0) {
                ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
                chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
            }

            showStatus("你的回合");
        }
    }

    private void restartGame() {
        isProcessing = false;
        chessView.cancelAnimation();
        game.reset();
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearLastMove();
        chessView.bindGame(game);
        chessView.setLocked(false);

        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (difficultyPanel.getVisibility() == View.GONE) {
            restartGame();
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
