package com.gamecenter.app.games.klotski;

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
 * 华容道游戏 Activity
 *
 * <p>管理华容道游戏的 UI 交互、生命周期、存档恢复和最优解提示功能。
 * 作为 MVC 中的 Controller，协调 {@link KlotskiGame}（模型）和 {@link KlotskiView}（视图）。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>提示功能使用 BFS 求解最优解，在后台线程计算避免阻塞 UI</li>
 *   <li>计算提示前保存棋盘快照，计算完成后比对以防止棋盘已变化导致提示失效</li>
 *   <li>打乱功能通过合法移动随机走步实现，保证打乱后的局面一定可解</li>
 *   <li>自动存档在 onPause 时保存，仅在未获胜时保存</li>
 * </ul>
 * </p>
 */
public class KlotskiActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于存档和统计 */
    private static final String GAME_ID = "klotski";
    /** 日志标签 */
    private static final String TAG = "KlotskiActivity";
    /** 自动存档槽位名称 */
    private static final String SLOT_AUTO = "auto";

    /** 华容道自定义绘制视图 */
    private KlotskiView klotskiView;
    /** 华容道游戏逻辑 */
    private KlotskiGame game;
    /** 状态文本显示 */
    private TextView tvStatus;
    /** 步数文本显示 */
    private TextView tvMoves;
    /** 是否正在计算提示（防止重复点击） */
    private boolean isHintSearching = false;
    /** 主线程 Handler，用于从后台线程回调 UI 操作 */
    private Handler mainHandler;
    /** 存档管理器 */
    private SaveManager saveManager;
    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;
    /** 游戏开始时间戳（毫秒） */
    private long gameStartTime;
    /** 已经过时间（毫秒） */
    private long elapsedMs = 0;
    /** 游戏是否处于活跃状态 */
    private boolean gameActive = false;

    /**
     * Activity 创建回调
     *
     * <p>初始化视图、游戏逻辑、存档系统，以及所有按钮的事件监听。
     * 如果检测到自动存档则静默恢复进度。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_klotski);

        saveManager = SaveManager.getInstance(this);
        usageStore = new GameUsageStore(this);
        mainHandler = new Handler(Looper.getMainLooper());

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_klotski);

        tvStatus = findViewById(R.id.tv_game_status);
        tvMoves = findViewById(R.id.tv_moves);
        klotskiView = findViewById(R.id.klotski_view);
        game = new KlotskiGame();
        klotskiView.setGame(game);

        // 检测自动存档并恢复
        if (saveManager.hasSave(GAME_ID, SLOT_AUTO)) {
            String saved = saveManager.load(GAME_ID, SLOT_AUTO);
            if (saved != null && game.restoreState(saved)) {
                updateStatus("已恢复进度");
            } else {
                updateStatus("滑动方块，帮助曹操逃出华容道");
            }
        } else {
            updateStatus("滑动方块，帮助曹操逃出华容道");
        }

        // 获胜监听：记录最佳步数、删除存档
        klotskiView.setOnWinListener(() -> {
            int moves = game.getMoves();
            updateStatus("🎉 完成！曹操逃出华容道！");
            Toast.makeText(KlotskiActivity.this,
                "恭喜！总步数：" + moves,
                Toast.LENGTH_LONG).show();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            saveBestMoves(moves);
            if (elapsedMs > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsedMs);
            }
        });
        // 每次移动后更新状态提示
        klotskiView.setOnMoveListener(() -> updateStatus("继续移动，目标是让曹操到达下方出口"));

        gameActive = true;
        gameStartTime = System.currentTimeMillis();

        // 重新开始按钮
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            klotskiView.clearHint();
            klotskiView.invalidate();
            updateStatus("已重新开始");
            tvMoves.setText("");
            isHintSearching = false;
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            resetTimer();
            Toast.makeText(this, "已重置到初始状态", Toast.LENGTH_SHORT).show();
        });

        // 打乱按钮：通过合法移动打乱，保证可解
        MaterialButton btnShuffle = findViewById(R.id.btn_shuffle);
        btnShuffle.setOnClickListener(v -> {
            game.shuffle();
            klotskiView.clearHint();
            klotskiView.invalidate();
            updateStatus("🔀 已随机打乱（可解）");
            tvMoves.setText("");
            isHintSearching = false;
            Toast.makeText(this, "已通过合法移动打乱，保证可解", Toast.LENGTH_SHORT).show();
        });

        // 提示按钮：后台 BFS 求解最优解
        MaterialButton btnHint = findViewById(R.id.btn_hint);
        btnHint.setOnClickListener(v -> {
            if (game.isWon()) {
                Toast.makeText(this, "已经获胜，无需提示!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isHintSearching) {
                Toast.makeText(this, "正在计算中，请稍候...", Toast.LENGTH_SHORT).show();
                return;
            }

            isHintSearching = true;
            updateStatus("💡 正在计算最优解...");
            btnHint.setEnabled(false);
            // 保存计算前的棋盘状态，用于后续比对
            String boardBeforeSearch = game.serializeBoardState();

            // 在后台线程执行 BFS 搜索，避免阻塞 UI
            new Thread(() -> {
                KlotskiGame.HintResult hint = game.getHint();

                mainHandler.post(() -> {
                    isHintSearching = false;
                    btnHint.setEnabled(true);

                    // 如果计算期间棋盘已变化，提示可能失效
                    if (!boardBeforeSearch.equals(game.serializeBoardState())) {
                        updateStatus("棋盘已变化，请重新点击提示");
                        return;
                    }

                    if (hint != null) {
                        KlotskiGame.Block block = game.getBlocks().get(hint.blockId);
                        String dir = getDirection(hint.dx, hint.dy);
                        klotskiView.showHint(hint);
                        updateStatus("💡 移动「" + block.name + "」向" + dir + "\n预计 " + hint.totalSteps + " 步到出口");
                        Toast.makeText(this,
                            "按箭头移动后，再点提示查看下一步",
                            Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus("未找到解法，请尝试其他走法");
                        Toast.makeText(this, "计算未完成，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        // 教程按钮
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v ->
                GameTutorialHelper.showKlotskiTutorial(this));

        klotskiView.invalidate();
    }

    /**
     * 将移动方向转换为中文描述
     *
     * @param dx 水平移动量
     * @param dy 垂直移动量
     * @return 方向的中文字符串
     */
    private String getDirection(int dx, int dy) {
        if (dx > 0) return "右";
        if (dx < 0) return "左";
        if (dy > 0) return "下";
        if (dy < 0) return "上";
        return "";
    }

    /**
     * 更新状态文本和步数显示
     *
     * @param status 状态提示文字
     */
    private void updateStatus(String status) {
        tvStatus.setText(status);
        if (game.getMoves() > 0) {
            tvMoves.setText("步数：" + game.getMoves());
        } else {
            tvMoves.setText("");
        }
    }

    /**
     * Activity 暂停回调
     *
     * <p>记录游戏时长，仅在未获胜时保存自动存档。</p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (gameActive && elapsedMs > 0) {
            elapsedMs = System.currentTimeMillis() - gameStartTime;
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }
        if (game != null && !game.isWon()) {
            saveManager.save(GAME_ID, SLOT_AUTO, game.serializeState());
        }
    }

    /**
     * Activity 销毁回调
     *
     * <p>清除提示显示，释放资源。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        klotskiView.clearHint();
    }

    /**
     * 保存最佳步数到持久化存储
     *
     * <p>如果当前步数优于历史最佳，则更新记录并标记已完成。</p>
     *
     * @param moves 完成步数
     */
    private void saveBestMoves(int moves) {
        try {
            String progressJson = saveManager.loadProgress(GAME_ID);
            JSONObject progress;
            int bestMoves = Integer.MAX_VALUE;
            if (progressJson != null) {
                progress = new JSONObject(progressJson);
                bestMoves = progress.optInt("bestMoves", Integer.MAX_VALUE);
            } else {
                progress = new JSONObject();
            }
            if (moves < bestMoves) {
                progress.put("bestMoves", moves);
                progress.put("completed", true);
                saveManager.saveProgress(GAME_ID, progress.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Save progress: " + e.getMessage());
        }
    }

    /**
     * 重置计时器
     */
    private void resetTimer() {
        gameActive = false;
        elapsedMs = 0;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * Activity 恢复回调
     *
     * <p>如果游戏未获胜，重新记录起始时间。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (gameActive && !game.isWon()) {
            gameStartTime = System.currentTimeMillis();
        }
    }
}
