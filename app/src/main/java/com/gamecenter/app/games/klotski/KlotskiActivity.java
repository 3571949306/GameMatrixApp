package com.gamecenter.app.games.klotski;

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

public class KlotskiActivity extends AppCompatActivity {

    private static final String GAME_ID = "klotski";
    private static final String SLOT_AUTO = "auto";

    private KlotskiView klotskiView;
    private KlotskiGame game;
    private TextView tvStatus;
    private TextView tvMoves;
    private boolean isHintSearching = false;
    private Handler mainHandler;
    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private long gameStartTime;
    private long elapsedMs = 0;
    private boolean gameActive = false;

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
        klotskiView.setOnMoveListener(() -> updateStatus("继续移动，目标是让曹操到达下方出口"));

        gameActive = true;
        gameStartTime = System.currentTimeMillis();

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
            String boardBeforeSearch = game.serializeBoardState();

            new Thread(() -> {
                KlotskiGame.HintResult hint = game.getHint();

                mainHandler.post(() -> {
                    isHintSearching = false;
                    btnHint.setEnabled(true);

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

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v ->
                GameTutorialHelper.showKlotskiTutorial(this));

        klotskiView.invalidate();
    }

    private String getDirection(int dx, int dy) {
        if (dx > 0) return "右";
        if (dx < 0) return "左";
        if (dy > 0) return "下";
        if (dy < 0) return "上";
        return "";
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
        if (game.getMoves() > 0) {
            tvMoves.setText("步数：" + game.getMoves());
        } else {
            tvMoves.setText("");
        }
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        klotskiView.clearHint();
    }

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
        }
    }

    private void resetTimer() {
        gameActive = false;
        elapsedMs = 0;
        gameStartTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameActive && !game.isWon()) {
            gameStartTime = System.currentTimeMillis();
        }
    }
}
