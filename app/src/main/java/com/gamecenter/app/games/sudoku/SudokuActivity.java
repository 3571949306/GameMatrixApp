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

/**
 * 数独游戏 Activity
 *
 * <p>职责：作为数独游戏的入口界面，负责初始化游戏视图、管理数字输入、
 * 计时器、存档恢复/保存以及完成判定。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 SaveManager 实现自动存档（onPause 保存）和恢复（onCreate 检测）</li>
 *   <li>通过 Handler + Runnable 实现每秒更新的计时器</li>
 *   <li>数字输入通过9个按钮 + 清除按钮实现，选中格子后点击数字填入</li>
 *   <li>完成时通过 OnSolvedListener 回调记录用时并显示祝贺</li>
 * </ul>
 *
 * <p>布局：res/layout/activity_sudoku.xml</p>
 */
public class SudokuActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于 SaveManager 和 GameUsageStore */
    private static final String GAME_ID = "sudoku";

    /** 自动存档槽位名称 */
    private static final String SLOT_AUTO = "auto";

    /** 自定义数独视图，负责绘制棋盘和处理格子选择 */
    private SudokuView sudokuView;

    /** 游戏逻辑核心，管理棋盘数据、冲突检测和序列化 */
    private SudokuGame game;

    /** 状态提示文本 */
    private TextView tvStatus;

    /** 计时器显示文本 */
    private TextView tvTimer;

    /** 存档管理器，用于保存/恢复游戏进度 */
    private SaveManager saveManager;

    /** 游戏使用统计存储，记录完成用时 */
    private GameUsageStore usageStore;

    /** 本局游戏开始时间戳（毫秒） */
    private long gameStartTime;

    /** 计时器调度 Handler */
    private Handler timerHandler;

    /** 计时器周期性任务 */
    private Runnable timerRunnable;

    /** 已经过时间（毫秒） */
    private long elapsedMs = 0;

    /** 游戏是否活跃（计时器是否运行） */
    private boolean gameActive = true;

    /**
     * Activity 创建回调
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置布局、绑定标题和状态栏</li>
     *   <li>创建 SudokuGame 并关联到视图</li>
     *   <li>初始化计时器（每秒更新一次）</li>
     *   <li>检测是否有存档，弹出恢复对话框</li>
     *   <li>绑定数字输入按钮（1-9 + 清除）</li>
     *   <li>绑定重启和教程按钮</li>
     *   <li>注册完成监听器</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
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

    /**
     * 启动计时器
     *
     * <p>记录当前时间戳为起始时间，将 gameActive 设为 true，
     * 并通过 Handler 投递计时器任务（每秒执行一次）。</p>
     */
    private void startTimer() {
        gameStartTime = System.currentTimeMillis();
        elapsedMs = 0;
        gameActive = true;
        timerHandler.post(timerRunnable);
    }

    /**
     * 停止计时器
     *
     * <p>将 gameActive 设为 false 并移除 Handler 中的计时器回调。</p>
     */
    private void stopTimer() {
        gameActive = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    /**
     * 重置计时器
     *
     * <p>停止计时器，将经过时间归零，更新显示为"0秒"。</p>
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
     * 将毫秒数格式化为可读时间字符串
     *
     * @param ms 经过的毫秒数
     * @return 格式化后的时间字符串，如"2分30秒"或"45秒"
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
     * 获取当前已用时间（毫秒）
     *
     * <p>供 SudokuView 的 OnSolvedListener 回调使用，
     * 用于在完成时获取实际用时。</p>
     *
     * @return 已经过的毫秒数
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * 处理数字输入
     *
     * <p>输入逻辑：</p>
     * <ol>
     *   <li>检查是否已选中格子（selectedX/Y >= 0），未选中则提示</li>
     *   <li>检查选中格子是否为固定格子（题目给定），不可修改则提示</li>
     *   <li>调用 game.setNumber() 填入数字（0 表示清除）</li>
     *   <li>刷新视图选中状态</li>
     *   <li>若数独已解完，停止计时器、显示祝贺、删除存档、记录用时</li>
     * </ol>
     *
     * @param num 要填入的数字（1-9），0 表示清除
     */
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

    /**
     * Activity 暂停时保存游戏进度
     *
     * <p>停止计时器，若游戏未完成则将当前状态序列化保存到自动存档槽位。</p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        if (game != null && !game.isSolved()) {
            saveManager.save(GAME_ID, SLOT_AUTO, game.serializeState());
        }
    }

    /**
     * Activity 恢复时重启计时器
     *
     * <p>若游戏未完成，重新启动计时器继续计时。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (game != null && !game.isSolved()) {
            startTimer();
        }
    }
}
