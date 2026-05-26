package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋人机对战主界面 Fragment。
 */
public class ChineseChessModuleFragment extends Fragment {

    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {140L, 220L, 340L, 480L};

    /** 游戏标识符，用于使用统计记录 */
    private static final String GAME_ID = "chinese_chess";

    /** 棋盘自定义视图，负责渲染棋盘、棋子及交互高亮 */
    private ChineseChessView chessView;

    /** 难度选择面板（开局前显示） */
    private LinearLayout difficultyPanel;

    /** 游戏控制面板（对局中显示，含悔棋/重开等按钮） */
    private LinearLayout controlPanel;

    /** 状态文本：显示当前回合、胜负等信息 */
    private TextView tvStatus;

    /** 难度标签文本 */
    private TextView tvDifficultyLabel;

    /** 游戏逻辑核心对象 */
    private ChineseChessGame game;

    /** AI引擎对象 */
    private ChineseChessAI ai;

    /** 当前AI难度等级（1~4） */
    private int aiDifficulty = 2;

    private static final int MAX_AI_DIFFICULTY = 4;

    /** UI线程Handler，用于从AI后台线程切换回主线程更新界面 */
    private Handler uiHandler;

    /** AI搜索专用单线程线程池 */
    private ExecutorService aiExecutor;

    /**
     * 是否正在处理中（AI思考或走棋动画播放期间）。
     * 使用 volatile 保证多线程可见性。
     */
    private volatile boolean isProcessing = false;

    /** 当前选中的棋子坐标 [col, row]，null 表示未选中 */
    private int[] selectedPos = null;

    /** 当前选中棋子的合法走法列表，每个元素为 [toCol, toRow] */
    private List<int[]> currentValidMoves = null;

    /** 游戏使用统计存储，用于记录胜/负次数 */
    private GameUsageStore usageStore;

    /** 4档难度对应的中文标签名称 */
    private static final String[] DIFFICULTY_NAMES = {
        "低", "中", "高", "大师"
    };

    private com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleRes;

    private int getResId(String name, String type) {
        if (moduleRes != null) {
            return moduleRes.getResId(name, type);
        }
        return 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        moduleRes = com.gamecenter.app.modules.ModuleManager.INSTANCE.getModuleResources("chinesechess");
        if (moduleRes == null) {
            Toast.makeText(requireContext(), "获取模块资源失败", Toast.LENGTH_SHORT).show();
            return new FrameLayout(requireContext());
        }

        // 使用插件资源 Context 覆盖
        Context contextThemeWrapper = new ContextThemeWrapper(requireContext(), com.gamecenter.app.R.style.Theme_GameMatrixApp) {
            @Override
            public Resources getResources() {
                return moduleRes.getResources();
            }

            @Override
            public AssetManager getAssets() {
                return moduleRes.getAssetManager();
            }
        };

        LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
        int layoutId = moduleRes.getLayoutResId("activity_chinese_chess");
        View view = localInflater.inflate(layoutId, container, false);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        chessView = view.findViewById(getResId("chess_view", "id"));
        difficultyPanel = view.findViewById(getResId("difficulty_panel", "id"));
        controlPanel = view.findViewById(getResId("control_panel", "id"));
        tvStatus = view.findViewById(getResId("tv_status", "id"));
        tvDifficultyLabel = view.findViewById(getResId("tv_difficulty_label", "id"));

        game = new ChineseChessGame();
        chessView.bindGame(game);
        usageStore = new GameUsageStore(requireContext());
        chessView.setOnCellClickListener(this::onCellTap);

        setupDifficultyButtons(view);

        view.findViewById(getResId("btn_start_game", "id")).setOnClickListener(v -> beginGame(aiDifficulty));
        view.findViewById(getResId("btn_tutorial", "id")).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(requireContext()));
        view.findViewById(getResId("btn_undo", "id")).setOnClickListener(v -> undoLastMove());
        view.findViewById(getResId("btn_hint", "id")).setOnClickListener(v -> showHint());
        view.findViewById(getResId("btn_restart", "id")).setOnClickListener(v -> restartGame());
        view.findViewById(getResId("btn_tutorial_ingame", "id")).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(requireContext()));
        view.findViewById(getResId("btn_online", "id")).setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(com.gamecenter.app.R.id.fragment_container, new ChineseChessOnlineFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // 拦截系统返回键
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (difficultyPanel.getVisibility() == View.GONE) {
                    restartGame();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        return view;
    }

    private void setupDifficultyButtons(View view) {
        String[] ids = {
                "btn_difficulty_1",
                "btn_difficulty_2",
                "btn_difficulty_3",
                "btn_difficulty_4"
        };
        for (int i = 0; i < ids.length; i++) {
            final int difficulty = i + 1;
            View button = view.findViewById(getResId(ids[i], "id"));
            if (button != null) {
                button.setOnClickListener(v -> selectDifficulty(difficulty));
            }
        }
        selectDifficulty(aiDifficulty);
    }

    private void selectDifficulty(int difficulty) {
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        if (tvDifficultyLabel != null) {
            tvDifficultyLabel.setText("难度：" + DIFFICULTY_NAMES[aiDifficulty - 1]
                    + " (" + aiDifficulty + "/" + MAX_AI_DIFFICULTY + ")");
        }
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
                chessView.clearHint();
            } else {
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
                chessView.clearHint();
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
        chessView.clearHint();
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
            long delay = Math.max(getAiMinResponseDelayMs() - elapsed, 0L);
            if (delay > 0L) {
                uiHandler.postDelayed(() -> applyAIMove(move), delay);
            } else {
                uiHandler.post(() -> applyAIMove(move));
            }
        });
    }

    private long getAiMinResponseDelayMs() {
        int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
        return AI_MIN_RESPONSE_DELAYS_MS[idx];
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
            chessView.clearHint();
            chessView.clearLastMove();

            List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
            if (history.size() > 0) {
                ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
                chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
            }

            showStatus("你的回合");
        }
    }

    private void showHint() {
        if (isProcessing || game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;
        showStatus("正在计算提示...");
        aiExecutor.execute(() -> {
            ChineseChessAI hintAi = new ChineseChessAI(Math.max(1, Math.min(aiDifficulty, MAX_AI_DIFFICULTY)));
            int[] move = hintAi.getBestMove(game);
            uiHandler.post(() -> {
                if (move == null || game.isGameOver() || game.getCurrentSide() != ChineseChessGame.Side.RED) {
                    showStatus("暂无可用提示");
                    return;
                }
                selectedPos = new int[]{move[0], move[1]};
                currentValidMoves = game.getLegalMoves(move[0], move[1]);
                chessView.setSelected(move[0], move[1], currentValidMoves);
                chessView.setHintMove(move[0], move[1], move[2], move[3]);

                ChineseChessGame.Piece piece = game.getBoard()[move[1]][move[0]];
                String pieceName = piece != null ? piece.getName() : "";
                String hintDesc = buildHintDescription(pieceName, move[0], move[1], move[2], move[3]);
                showStatus("💡 " + hintDesc);
            });
        });
    }

    private String buildHintDescription(String pieceName, int fromX, int fromY, int toX, int toY) {
        String[] colNames = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
        String fromCol = colNames[fromX];
        String toCol = colNames[toX];

        if (fromX == toX) {
            int steps = Math.abs(toY - fromY);
            String direction = toY < fromY ? "进" : "退";
            return pieceName + fromCol + direction + numToChinese(steps);
        } else if (fromY == toY) {
            return pieceName + fromCol + "平" + toCol;
        } else {
            String direction = toY < fromY ? "进" : "退";
            return pieceName + fromCol + direction + toCol;
        }
    }

    private String numToChinese(int num) {
        String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (num >= 0 && num < nums.length) return nums[num];
        return String.valueOf(num);
    }

    private void restartGame() {
        isProcessing = false;
        chessView.cancelAnimation();
        game.reset();
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();
        chessView.bindGame(game);
        chessView.setLocked(false);

        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}
