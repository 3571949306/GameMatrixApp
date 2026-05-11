package com.gamecenter.app.games.sudoku;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

public class SudokuActivity extends AppCompatActivity {

    private static final String GAME_ID = "sudoku";
    private static final String SLOT_AUTO = "auto";
    private SudokuView sudokuView;
    private SudokuGame game;
    private TextView tvStatus;
    private TextView tvTimer;
    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private long gameStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long elapsedMs = 0;
    private boolean gameActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sudoku);

        saveManager = SaveManager.getInstance(this);
        usageStore = new GameUsageStore(this);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_sudoku);

        tvStatus = findViewById(R.id.tv_game_status);
        tvTimer = findViewById(R.id.tv_game_time);
        if (tvTimer == null) {
            tvTimer = new TextView(this);
        }

        sudokuView = findViewById(R.id.sudoku_view);
        game = new SudokuGame();
        sudokuView.setGame(game);

        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameActive) {
                    elapsedMs = System.currentTimeMillis() - gameStartTime;
                    updateTimerDisplay();
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };

        if (saveManager.hasSave(GAME_ID, SLOT_AUTO)) {
            String saved = saveManager.load(GAME_ID, SLOT_AUTO);
            if (saved != null && game.restoreState(saved)) {
                new AlertDialog.Builder(this)
                        .setTitle("恢复游戏")
                        .setMessage("检测到上次未完成的数独，是否继续？")
                        .setPositiveButton("继续游戏", (d, w) -> {
                            sudokuView.invalidate();
                            tvStatus.setText("已恢复上次进度");
                            startTimer();
                        })
                        .setNegativeButton("开始新游戏", (d, w) -> {
                            game.reset();
                            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
                            sudokuView.invalidate();
                            startTimer();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                startTimer();
            }
        } else {
            startTimer();
        }

        sudokuView.setOnCellSelectedListener((x, y) -> {
            game.setSelectedValue(game.getBoard()[y][x]);
        });

        findViewById(R.id.btn_1).setOnClickListener(v -> inputNumber(1));
        findViewById(R.id.btn_2).setOnClickListener(v -> inputNumber(2));
        findViewById(R.id.btn_3).setOnClickListener(v -> inputNumber(3));
        findViewById(R.id.btn_4).setOnClickListener(v -> inputNumber(4));
        findViewById(R.id.btn_5).setOnClickListener(v -> inputNumber(5));
        findViewById(R.id.btn_6).setOnClickListener(v -> inputNumber(6));
        findViewById(R.id.btn_7).setOnClickListener(v -> inputNumber(7));
        findViewById(R.id.btn_8).setOnClickListener(v -> inputNumber(8));
        findViewById(R.id.btn_9).setOnClickListener(v -> inputNumber(9));
        findViewById(R.id.btn_clear).setOnClickListener(v -> inputNumber(0));

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            sudokuView.invalidate();
            tvStatus.setText("");
            resetTimer();
            startTimer();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showSudokuTutorial(this));

        sudokuView.setOnSolvedListener(elapsed -> {
            if (game.isSolved() && elapsed > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsed);
                Toast.makeText(this, "恭喜完成! 用时 " + formatTime(elapsed), Toast.LENGTH_LONG).show();
            }
        });

        sudokuView.invalidate();
    }

    private void startTimer() {
        gameStartTime = System.currentTimeMillis();
        elapsedMs = 0;
        gameActive = true;
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        gameActive = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void resetTimer() {
        stopTimer();
        elapsedMs = 0;
        if (tvTimer != null) {
            tvTimer.setText("时间: 0秒");
        }
    }

    private void updateTimerDisplay() {
        if (tvTimer != null) {
            tvTimer.setText("时间: " + formatTime(elapsedMs));
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "分" + seconds + "秒";
        }
        return seconds + "秒";
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    private void inputNumber(int num) {
        int x = sudokuView.getSelectedX();
        int y = sudokuView.getSelectedY();

        if (x < 0 || y < 0) {
            Toast.makeText(this, "请先点击选择一个空格", Toast.LENGTH_SHORT).show();
            return;
        }

        if (game.isFixed(x, y)) {
            Toast.makeText(this, "该位置不可修改", Toast.LENGTH_SHORT).show();
            return;
        }

        game.setNumber(x, y, num);
        game.setSelectedValue(num);
        sudokuView.refreshSelection();

        if (game.isSolved()) {
            stopTimer();
            tvStatus.setText("🎉 恭喜完成！");
            Toast.makeText(this, "恭喜你完成了数独！", Toast.LENGTH_LONG).show();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            if (elapsedMs > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsedMs);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        if (game != null && !game.isSolved()) {
            saveManager.save(GAME_ID, SLOT_AUTO, game.serializeState());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null && !game.isSolved()) {
            startTimer();
        }
    }
}
