package com.gamecenter.app.games.sokoban;

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
import org.json.JSONObject;

public class SokobanActivity extends AppCompatActivity {

    private static final String GAME_ID = "sokoban";
    private static final String SLOT_AUTO = "auto";

    private SokobanView sokobanView;
    private SokobanGame game;
    private TextView tvStatus;
    private TextView tvTimer;
    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private long gameStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long elapsedMs = 0;
    private boolean gameActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sokoban);

        saveManager = SaveManager.getInstance(this);
        usageStore = new GameUsageStore(this);
        timerHandler = new Handler(Looper.getMainLooper());

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_sokoban);

        tvStatus = findViewById(R.id.tv_game_status);
        tvTimer = findViewById(R.id.tv_game_time);
        if (tvTimer == null) {
            tvTimer = new TextView(this);
        }

        sokobanView = findViewById(R.id.sokoban_view);
        game = new SokobanGame();
        sokobanView.setGame(game);

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

        boolean hasRestore = false;
        if (saveManager.hasSave(GAME_ID, SLOT_AUTO)) {
            String saved = saveManager.load(GAME_ID, SLOT_AUTO);
            if (saved != null && game.restoreState(saved)) {
                hasRestore = true;
                new AlertDialog.Builder(this)
                        .setTitle("恢复游戏")
                        .setMessage("检测到上次未完成的推箱子，是否继续？")
                        .setPositiveButton("继续游戏", (d, w) -> {
                            sokobanView.invalidate();
                            tvStatus.setText("第" + (game.getCurrentLevel() + 1) + "关");
                            startTimer();
                        })
                        .setNegativeButton("开始新游戏", (d, w) -> {
                            game.loadLevel(0);
                            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
                            sokobanView.invalidate();
                            startTimer();
                        })
                        .setCancelable(false)
                        .show();
            }
        }
        if (!hasRestore) {
            startTimer();
        }

        sokobanView.setOnLevelCompleteListener(() -> {
            int moves = game.getMoves();
            tvStatus.setText("完成！步数: " + moves + "  第" + (game.getCurrentLevel() + 1) + "关");
            Toast.makeText(this, "恭喜过关！滑动继续下一关", Toast.LENGTH_SHORT).show();
            saveLevelProgress(game.getCurrentLevel(), moves);
            if (elapsedMs > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsedMs);
            }
            resetTimer();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            game.nextLevel();
            sokobanView.invalidate();
            startTimer();
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            sokobanView.invalidate();
            tvStatus.setText("");
            resetTimer();
            startTimer();
        });

        MaterialButton btnUp = findViewById(R.id.btn_up);
        btnUp.setOnClickListener(v -> {
            game.move(0, -1);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        MaterialButton btnDown = findViewById(R.id.btn_down);
        btnDown.setOnClickListener(v -> {
            game.move(0, 1);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        MaterialButton btnLeft = findViewById(R.id.btn_left);
        btnLeft.setOnClickListener(v -> {
            game.move(-1, 0);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        MaterialButton btnRight = findViewById(R.id.btn_right);
        btnRight.setOnClickListener(v -> {
            game.move(1, 0);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> {
            GameTutorialHelper.showSokobanTutorial(this);
        });

        sokobanView.invalidate();
    }

    private void startTimer() {
        gameActive = true;
        gameStartTime = System.currentTimeMillis();
        elapsedMs = 0;
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

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        if (elapsedMs > 0) {
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }
        if (game != null) {
            saveManager.save(GAME_ID, SLOT_AUTO, game.serializeState());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameActive && elapsedMs > 0) {
            gameStartTime = System.currentTimeMillis() - elapsedMs;
            timerHandler.post(timerRunnable);
        }
    }

    private void saveLevelProgress(int levelIndex, int moves) {
        try {
            String progressJson = saveManager.loadProgress(GAME_ID);
            JSONObject progress;
            if (progressJson != null) {
                progress = new JSONObject(progressJson);
            } else {
                progress = new JSONObject();
            }

            int unlockedLevel = progress.optInt("unlockedLevel", 0);
            if (levelIndex >= unlockedLevel) {
                progress.put("unlockedLevel", levelIndex + 1);
            }

            String bestKey = "best_" + levelIndex;
            int bestMoves = progress.optInt(bestKey, Integer.MAX_VALUE);
            if (moves < bestMoves) {
                progress.put(bestKey, moves);
            }

            saveManager.saveProgress(GAME_ID, progress.toString());
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        timerRunnable = null;
        timerHandler = null;
        sokobanView = null;
        game = null;
    }
}
