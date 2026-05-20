package com.gamecenter.app.games.sokoban;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;

/**
 * 推箱子游戏 Activity
 *
 * <p>管理推箱子游戏的 UI 交互、生命周期、存档恢复、计时器和关卡进度。
 * 作为 MVC 中的 Controller，协调 {@link SokobanGame}（模型）和 {@link SokobanView}（视图）。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用自动存档槽位（"auto"）在 onPause 时保存进度，onCreate 时检测并提示恢复</li>
 *   <li>方向按钮和滑动手势均可控制移动，按钮移动后手动检查关卡完成</li>
 *   <li>计时器在游戏活跃时每秒更新，暂停/恢复时正确处理时间偏移</li>
 * </ul>
 * </p>
 */
public class SokobanActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于存档和统计 */
    private static final String GAME_ID = "sokoban";
    /** 日志标签 */
    private static final String TAG = "SokobanActivity";
    /** 自动存档槽位名称 */
    private static final String SLOT_AUTO = "auto";

    /** 推箱子自定义绘制视图 */
    private SokobanView sokobanView;
    /** 推箱子游戏逻辑 */
    private SokobanGame game;
    /** 状态文本显示 */
    private TextView tvStatus;
    /** 计时器文本显示 */
    private TextView tvTimer;
    /** 存档管理器 */
    private SaveManager saveManager;
    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;
    /** 游戏开始时间戳（毫秒） */
    private long gameStartTime;
    /** 计时器 Handler，用于定时刷新 */
    private Handler timerHandler;
    /** 计时器定时任务 */
    private Runnable timerRunnable;
    /** 已经过时间（毫秒） */
    private long elapsedMs = 0;
    /** 游戏是否处于活跃状态（计时器运行中） */
    private boolean gameActive = false;

    /**
     * Activity 创建回调
     *
     * <p>初始化视图、游戏逻辑、存档系统、计时器，以及所有按钮的事件监听。
     * 如果检测到自动存档，弹出对话框让用户选择恢复或重新开始。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
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
        // 防御性处理：布局中可能缺少计时器视图
        if (tvTimer == null) {
            tvTimer = new TextView(this);
        }

        sokobanView = findViewById(R.id.sokoban_view);
        game = new SokobanGame();
        sokobanView.setGame(game);

        // 计时器定时任务：每秒更新一次已用时间
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

        // 检测自动存档，提示用户是否恢复
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
        // 无存档则直接开始计时
        if (!hasRestore) {
            startTimer();
        }

        // 关卡完成监听：记录步数、保存进度、自动进入下一关
        sokobanView.setOnLevelCompleteListener(() -> {
            int moves = game.getMoves();
            tvStatus.setText("完成！步数: " + moves + "  第" + (game.getCurrentLevel() + 1) + "关");
            Toast.makeText(this, "恭喜过关！滑动继续下一关", Toast.LENGTH_SHORT).show();
            saveLevelProgress(game.getCurrentLevel(), moves);
            if (elapsedMs > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsedMs);
            }
            resetTimer();
            // 过关后删除自动存档并加载下一关
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            game.nextLevel();
            sokobanView.invalidate();
            startTimer();
        });

        // 重新开始按钮
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            sokobanView.invalidate();
            tvStatus.setText("");
            resetTimer();
            startTimer();
        });

        // 上移按钮
        MaterialButton btnUp = findViewById(R.id.btn_up);
        btnUp.setOnClickListener(v -> {
            game.move(0, -1);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        // 下移按钮
        MaterialButton btnDown = findViewById(R.id.btn_down);
        btnDown.setOnClickListener(v -> {
            game.move(0, 1);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        // 左移按钮
        MaterialButton btnLeft = findViewById(R.id.btn_left);
        btnLeft.setOnClickListener(v -> {
            game.move(-1, 0);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        // 右移按钮
        MaterialButton btnRight = findViewById(R.id.btn_right);
        btnRight.setOnClickListener(v -> {
            game.move(1, 0);
            if (game.isLevelComplete()) {
                sokobanView.getOnLevelCompleteListener().onComplete();
            }
            sokobanView.invalidate();
        });

        // 教程按钮
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> {
            GameTutorialHelper.showSokobanTutorial(this);
        });

        sokobanView.invalidate();
    }

    /**
     * 启动计时器
     *
     * <p>记录当前时间作为起始点，并启动每秒刷新的定时任务。</p>
     */
    private void startTimer() {
        gameActive = true;
        gameStartTime = System.currentTimeMillis();
        elapsedMs = 0;
        timerHandler.post(timerRunnable);
    }

    /**
     * 停止计时器
     */
    private void stopTimer() {
        gameActive = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    /**
     * 重置计时器，清零并更新显示
     */
    private void resetTimer() {
        stopTimer();
        elapsedMs = 0;
        if (tvTimer != null) {
            tvTimer.setText("时间: 0秒");
        }
    }

    /**
     * 更新计时器显示文本
     */
    private void updateTimerDisplay() {
        if (tvTimer != null) {
            tvTimer.setText("时间: " + formatTime(elapsedMs));
        }
    }

    /**
     * 将毫秒数格式化为可读的时间字符串
     *
     * @param ms 毫秒数
     * @return 格式化后的时间字符串，如 "1分30秒" 或 "45秒"
     */
    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "分" + seconds + "秒";
        }
        return seconds + "秒";
    }

    /**
     * Activity 暂停回调
     *
     * <p>停止计时器，记录游戏时长，并自动保存当前游戏状态。</p>
     */
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

    /**
     * Activity 恢复回调
     *
     * <p>如果游戏之前处于活跃状态，根据已用时间修正起始时间戳并重启计时器。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (gameActive && elapsedMs > 0) {
            // 修正起始时间：当前时间减去已用时间
            gameStartTime = System.currentTimeMillis() - elapsedMs;
            timerHandler.post(timerRunnable);
        }
    }

    /**
     * 保存关卡进度到持久化存储
     *
     * <p>记录已解锁的最高关卡和每个关卡的最佳步数。
     * 如果当前关卡超过已解锁关卡，则更新解锁进度；
     * 如果当前步数优于历史最佳，则更新最佳记录。</p>
     *
     * @param levelIndex 关卡索引
     * @param moves      完成步数
     */
    private void saveLevelProgress(int levelIndex, int moves) {
        try {
            String progressJson = saveManager.loadProgress(GAME_ID);
            JSONObject progress;
            if (progressJson != null) {
                progress = new JSONObject(progressJson);
            } else {
                progress = new JSONObject();
            }

            // 更新已解锁关卡
            int unlockedLevel = progress.optInt("unlockedLevel", 0);
            if (levelIndex >= unlockedLevel) {
                progress.put("unlockedLevel", levelIndex + 1);
            }

            // 更新最佳步数
            String bestKey = "best_" + levelIndex;
            int bestMoves = progress.optInt(bestKey, Integer.MAX_VALUE);
            if (moves < bestMoves) {
                progress.put(bestKey, moves);
            }

            saveManager.saveProgress(GAME_ID, progress.toString());
        } catch (Exception e) {
            Log.w(TAG, "Save progress: " + e.getMessage());
        }
    }

    /**
     * Activity 销毁回调
     *
     * <p>停止计时器并释放引用，避免内存泄漏。</p>
     */
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
