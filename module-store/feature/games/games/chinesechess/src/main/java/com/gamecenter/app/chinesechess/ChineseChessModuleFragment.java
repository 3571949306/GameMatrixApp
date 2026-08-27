package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋人机对战主界面 Fragment。
 */
public class ChineseChessModuleFragment extends Fragment {

    private static final String TAG = "ChineseChessModule";
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {140L, 220L, 340L, 480L};

    /** 游戏标识符，用于使用统计记录 */
    private static final String GAME_ID = "chinesechess";

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

    /** 难度说明及对局元信息。 */
    private TextView tvDifficultyDescription;
    private TextView tvGameMeta;

    /** 四个难度按钮，用于明确显示当前选中项。 */
    private final Button[] difficultyButtons = new Button[4];

    /** 简洁棋盘开关（默认关闭，即增强棋盘）。 */
    private CheckBox simpleBoardCheck;
    private Button boardStyleButton;

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

    /**
     * 每次开局/重开递增。后台搜索和动画回调必须匹配该代次，防止旧结果落入新棋盘。
     */
    private int gameGeneration = 0;

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

    private static final String[] DIFFICULTY_DESCRIPTIONS = {
        "适合首次体验：会保留变化，但不再随机送出大子。",
        "适合休闲对局：兼顾响应速度和基础战术。",
        "推荐进阶玩家：四层搜索、低随机性与强化将区判断。",
        "适合挑战：稳定择优，残局会自动加深搜索。"
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
            Toast.makeText(requireContext(), R.string.klotski_get_resource_failed, Toast.LENGTH_SHORT).show();
            return new FrameLayout(requireContext());
        }

        // 获取模块的 DexClassLoader，供 LayoutInflater 加载模块内的自定义 View（如 ChineseChessView）
        // 若不重写 getClassLoader()，LayoutInflater 会使用宿主 ClassLoader，导致 ClassNotFoundException。
        final ClassLoader moduleClassLoader =
                com.gamecenter.app.modules.ModuleLoader.INSTANCE.getModuleClassLoader("chinesechess");

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

            @Override
            public ClassLoader getClassLoader() {
                // 优先使用模块的 DexClassLoader，使 LayoutInflater 能加载模块自定义 View
                return moduleClassLoader != null ? moduleClassLoader : super.getClassLoader();
            }
        };

        LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
        int layoutId = moduleRes.getLayoutResId("activity_chinese_chess");
        if (layoutId == 0) {
            // 模块 APK 损坏或资源包名解析失败时 getLayoutResId 返回 0，直接 inflate 会触发
            // Resources$NotFoundException 闪退。此处兜底返回空 FrameLayout，与 moduleRes==null 分支保持一致。
            Log.e("ChineseChessModuleFragment",
                    "布局资源未找到: activity_chinese_chess (moduleRes=" + moduleRes + ")");
            Toast.makeText(requireContext(), R.string.klotski_get_resource_failed, Toast.LENGTH_SHORT).show();
            return new FrameLayout(requireContext());
        }
        View view = localInflater.inflate(layoutId, container, false);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        chessView = view.findViewById(getResId("chess_view", "id"));
        difficultyPanel = view.findViewById(getResId("difficulty_panel", "id"));
        controlPanel = view.findViewById(getResId("control_panel", "id"));
        tvStatus = view.findViewById(getResId("tv_status", "id"));
        tvDifficultyLabel = view.findViewById(getResId("tv_difficulty_label", "id"));
        tvDifficultyDescription = view.findViewById(getResId("tv_difficulty_description", "id"));
        tvGameMeta = view.findViewById(getResId("tv_game_meta", "id"));
        simpleBoardCheck = view.findViewById(getResId("check_simple_board", "id"));
        boardStyleButton = view.findViewById(getResId("btn_board_style", "id"));

        game = new ChineseChessGame();
        chessView.bindGame(game);
        applySavedBoardStyle();
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
            // P2 明确下线：联机基础设施未接入生产环境（动态模块侧为空存根），
            // 经 OnlinePlayGate 统一下线，避免用户进入空实现；恢复联机需先置 ENABLED=true。
            if (!com.gamecenter.app.core.common.OnlinePlayGate.ENABLED) {
                android.widget.Toast.makeText(
                        getContext(),
                        com.gamecenter.app.core.common.OnlinePlayGate.COMING_SOON_MESSAGE,
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }
            getParentFragmentManager().beginTransaction()
                    .replace(com.gamecenter.app.R.id.fragment_container, new ChineseChessOnlineFragment())
                    .addToBackStack(null)
                    .commit();
        });
        if (simpleBoardCheck != null) {
            simpleBoardCheck.setOnCheckedChangeListener((buttonView, checked) ->
                    setSimpleBoardEnabled(checked, true));
        }
        if (boardStyleButton != null) {
            boardStyleButton.setOnClickListener(v ->
                    setSimpleBoardEnabled(!chessView.isSimpleMode(), true));
        }

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

        // 外层大厅只提供推荐值；模块仍必须展示四档选择并由用户显式点击“开始游戏”。
        if (getActivity() != null && getActivity().getIntent() != null) {
            int prefilledIndex = getActivity().getIntent().getIntExtra(
                    "game_difficulty_index", -1);
            if (prefilledIndex >= 0) {
                // GameStartDialog 使用 0-based index，映射到 1-4 难度
                int mappedDifficulty = Math.min(prefilledIndex + 1, MAX_AI_DIFFICULTY);
                selectDifficulty(mappedDifficulty);
            }
        }

        renderGameMeta();

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
            Button button = view.findViewById(getResId(ids[i], "id"));
            difficultyButtons[i] = button;
            if (button != null) {
                button.setOnClickListener(v -> selectDifficulty(difficulty));
            }
        }
        selectDifficulty(aiDifficulty);
    }

    private void selectDifficulty(int difficulty) {
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        if (tvDifficultyLabel != null) {
            tvDifficultyLabel.setText(getString(R.string.game_difficulty_format, DIFFICULTY_NAMES[aiDifficulty - 1])
                    + " (" + aiDifficulty + "/" + MAX_AI_DIFFICULTY + ")");
        }
        if (tvDifficultyDescription != null) {
            tvDifficultyDescription.setText(DIFFICULTY_DESCRIPTIONS[aiDifficulty - 1]);
        }
        for (int i = 0; i < difficultyButtons.length; i++) {
            Button button = difficultyButtons[i];
            if (button == null) continue;
            boolean selected = i == aiDifficulty - 1;
            button.setText((selected ? "✓ " : "") + DIFFICULTY_NAMES[i]);
            button.setAlpha(selected ? 1f : 0.68f);
            button.setSelected(selected);
        }
        renderGameMeta();
    }

    private void beginGame(int difficulty) {
        gameGeneration++;
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
        renderGameMeta();
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
        final int generation = gameGeneration;

        chessView.animateMove(fromX, fromY, toX, toY, () -> {
            if (generation != gameGeneration) return;
            // 集中闸门：校验 + 落子 + 切换 + 记录 + 终局判定原子完成。
            // 玩家着法来自 currentValidMoves（已为合法着法），但再次经 isMoveLegal 防御性把关。
            ChineseChessGame.MoveRecord rec = game.commitMove(fromX, fromY, toX, toY);
            if (rec == null) {
                // 理论上不会发生（UI 仅允许合法着法），保险起见回退到玩家回合。
                isProcessing = false;
                showStatus("你的回合");
                return;
            }

            chessView.setLastMove(fromX, fromY, toX, toY);
            chessView.invalidate();
            renderGameMeta();

            if (game.isGameOver()) {
                isProcessing = false;
                showGameEndStatus();
                return;
            }

            startAITurn();
        });
    }

    /** 终局状态展示与胜负数统计（人机模式）。 */
    private void showGameEndStatus() {
        if (game.getWinner() == null) {
            showStatus("和棋！");
        } else if (game.getWinner() == ChineseChessGame.Side.RED) {
            showStatus("🎉 恭喜获胜！");
            usageStore.recordWin(GAME_ID);
        } else {
            showStatus("AI获胜！");
            usageStore.recordLoss(GAME_ID);
        }
        renderGameMeta();
    }

    private void startAITurn() {
        isProcessing = true;
        chessView.setLocked(true);
        showStatus("AI思考中...");
        renderGameMeta();
        final long startMs = System.currentTimeMillis();
        final int generation = gameGeneration;
        final ChineseChessAI currentAi = ai;
        final int[][] boardSnapshot = game.getBoardAsIntArray();
        final List<Long> positionHistorySnapshot = game.getPositionHistory();
        final List<int[]> recentBlackMoves = buildRecentAiMoveHistory(ChineseChessGame.Side.BLACK);

        aiExecutor.execute(() -> {
            currentAi.setPositionHistory(positionHistorySnapshot);
            currentAi.setRecentMoveHistory(recentBlackMoves);
            int[] aiRaw = currentAi.getBestMove(boardSnapshot, aiDifficulty);
            final int[] move;
            if (aiRaw == null) {
                move = null;
            } else {
                // AI 返回 [fromRow, fromCol, toRow, toCol]（行优先），转换为游戏通用的
                // [fromX, fromY, toX, toY] = [col, row, col, row] 格式，与 getAllMoves 一致。
                move = new int[]{aiRaw[1], aiRaw[0], aiRaw[3], aiRaw[2]};
            }

            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(getAiMinResponseDelayMs() - elapsed, 0L);
            if (delay > 0L) {
                uiHandler.postDelayed(() -> applyAIMove(move, generation), delay);
            } else {
                uiHandler.post(() -> applyAIMove(move, generation));
            }
        });
    }

    private long getAiMinResponseDelayMs() {
        int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
        return AI_MIN_RESPONSE_DELAYS_MS[idx];
    }

    /** 先解析出真正会执行的合法 AI 着法，确保动画坐标与最终落子一致。 */
    private int[] resolveLegalAIMove(int[] candidate) {
        if (candidate != null && game.isMoveLegal(candidate[0], candidate[1], candidate[2], candidate[3])) {
            return candidate;
        }
        List<int[]> legal = game.getAllMoves(ChineseChessGame.Side.BLACK);
        if (legal.isEmpty()) return null;

        String reason = candidate == null ? "engine_returned_null" : "engine_returned_illegal";
        Log.e(TAG, "AI_CONTRACT_VIOLATION reason=" + reason
                + " raw=" + formatMove(candidate) + " legalCount=" + legal.size());
        // 仅作为崩溃保护：不再无脑执行裁判枚举的第一着，而是在中央合法候选中
        // 选择吃子收益、将军和落点安全性更好的着法。验收日志仍必须保证 fallback=0。
        return chooseSafeFallbackMove(legal);
    }

    private void applyAIMove(int[] candidate, int generation) {
        if (generation != gameGeneration || game == null || game.isGameOver()
                || game.getCurrentSide() != ChineseChessGame.Side.BLACK) {
            return;
        }
        int[] move = resolveLegalAIMove(candidate);
        if (move != null) {
            chessView.animateMove(move[0], move[1], move[2], move[3], () -> {
                if (generation != gameGeneration) return;
                // 预检查只用于选择动画；真实落子仍必须再次通过集中闸门。
                ChineseChessGame.MoveRecord rec = game.commitMove(move[0], move[1], move[2], move[3]);
                isProcessing = false;
                chessView.setLocked(false);
                chessView.invalidate();
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();
                if (rec == null) {
                    Log.e(TAG, "AI_COMMIT_REJECTED move=" + formatMove(move));
                    showStatus("AI落子校验失败，请重新开始");
                    renderGameMeta();
                    return;
                }
                chessView.setLastMove(move[0], move[1], move[2], move[3]);
                showStatus("你的回合");
                renderGameMeta();
                if (game.isGameOver()) {
                    showGameEndStatus();
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
                showGameEndStatus();
            }
            renderGameMeta();
        }
    }

    private List<int[]> buildRecentAiMoveHistory(ChineseChessGame.Side side) {
        List<int[]> result = new ArrayList<>();
        List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
        int start = Math.max(0, history.size() - 16);
        for (int i = start; i < history.size(); i++) {
            ChineseChessGame.MoveRecord record = history.get(i);
            if (record.piece != null && record.piece.side == side) {
                // 游戏逻辑 [x,y,x,y] -> AI [row,col,row,col]，仅在此边界转换。
                result.add(new int[]{record.fromY, record.fromX, record.toY, record.toX});
            }
        }
        return result;
    }

    private int[] chooseSafeFallbackMove(List<int[]> legalMoves) {
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] move : legalMoves) {
            ChineseChessGame.Piece moving = game.getBoard()[move[1]][move[0]];
            ChineseChessGame.Piece target = game.getBoard()[move[3]][move[2]];
            ChineseChessGame simulation = game.deepCopy();
            if (simulation.commitMove(move[0], move[1], move[2], move[3]) == null) continue;

            int score = target == null ? 0 : pieceValue(target.type) * 10;
            if (simulation.isGameOver()) {
                if (simulation.getWinner() == ChineseChessGame.Side.BLACK) score += 100_000;
                else if (simulation.getWinner() == ChineseChessGame.Side.RED) score -= 100_000;
            } else {
                if (simulation.isInCheck(ChineseChessGame.Side.RED)) score += 350;
                int movingValue = moving == null ? 0 : pieceValue(moving.type);
                for (int[] reply : simulation.getAllMoves(ChineseChessGame.Side.RED)) {
                    if (reply[2] == move[2] && reply[3] == move[3]) {
                        score -= movingValue * 2;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private int pieceValue(ChineseChessGame.PieceType type) {
        switch (type) {
            case GENERAL: return 10_000;
            case CHARIOT: return 900;
            case CANNON: return 450;
            case HORSE: return 400;
            case ADVISOR:
            case ELEPHANT: return 200;
            case SOLDIER: return 100;
            default: return 0;
        }
    }

    private String formatMove(int[] move) {
        if (move == null) return "null";
        return move[0] + "," + move[1] + "->" + move[2] + "," + move[3];
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
            renderGameMeta();
        }
    }

    private void showHint() {
        if (isProcessing || game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;
        showStatus("正在计算提示...");
        final int generation = gameGeneration;
        final int[][] boardSnapshot = game.getBoardAsIntArray();
        final List<Long> positionHistorySnapshot = game.getPositionHistory();
        final List<int[]> recentRedMoves = buildRecentAiMoveHistory(ChineseChessGame.Side.RED);
        aiExecutor.execute(() -> {
            ChineseChessAI hintAi = new ChineseChessAI(Math.max(1, Math.min(aiDifficulty, MAX_AI_DIFFICULTY)));
            hintAi.setPositionHistory(positionHistorySnapshot);
            hintAi.setRecentMoveHistory(recentRedMoves);
            // 提示是给红方（人类）的，必须传 aiSide=1（红方）。
            // ChineseChessAI.getBestMove 返回 [fromRow, fromCol, toRow, toCol]（行优先），
            // 而游戏其余接口（getLegalMoves/setSelected 等）使用 [x, y] = [col, row]（列优先）。
            // 此处将 AI 返回值转换为 [fromX, fromY, toX, toY] = [col, row, col, row]，与 getAllMoves 格式对齐。
            int[] raw = hintAi.getBestMove(boardSnapshot, aiDifficulty, 1);
            final int[] move;
            if (raw == null) {
                move = null;
            } else {
                move = new int[]{raw[1], raw[0], raw[3], raw[2]};
            }
            uiHandler.post(() -> {
                if (generation != gameGeneration || move == null || game.isGameOver()
                        || game.getCurrentSide() != ChineseChessGame.Side.RED
                        || !game.isMoveLegal(move[0], move[1], move[2], move[3])) {
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

    private void applySavedBoardStyle() {
        boolean simple = ChineseChessUiPreferences.isSimpleMode(requireContext());
        chessView.setSimpleMode(simple);
        if (simpleBoardCheck != null) simpleBoardCheck.setChecked(simple);
        updateBoardStyleControls();
    }

    private void setSimpleBoardEnabled(boolean enabled, boolean persist) {
        if (chessView == null) return;
        chessView.setSimpleMode(enabled);
        if (persist) ChineseChessUiPreferences.setSimpleMode(requireContext(), enabled);
        if (simpleBoardCheck != null && simpleBoardCheck.isChecked() != enabled) {
            simpleBoardCheck.setChecked(enabled);
        }
        updateBoardStyleControls();
        renderGameMeta();
    }

    private void updateBoardStyleControls() {
        if (boardStyleButton != null && chessView != null) {
            boardStyleButton.setText(chessView.isSimpleMode() ? "棋盘：简洁" : "棋盘：增强");
        }
    }

    /** 集中渲染难度、回合、将军和棋盘样式，避免多个回调拼出互相矛盾的状态。 */
    private void renderGameMeta() {
        if (tvGameMeta == null || chessView == null) return;
        String style = chessView.isSimpleMode() ? "简洁棋盘" : "增强棋盘";
        if (difficultyPanel == null || difficultyPanel.getVisibility() != View.GONE) {
            tvGameMeta.setText("已选" + DIFFICULTY_NAMES[aiDifficulty - 1] + "难度 · " + style
                    + " · 可在下方重新选择");
            return;
        }
        int plies = game == null ? 0 : game.getMoveHistory().size();
        int round = plies / 2 + 1;
        String turn = game != null && game.getCurrentSide() == ChineseChessGame.Side.BLACK
                ? "黑方 AI" : "红方 你";
        String check = game != null && !game.isGameOver() && game.isInCheck(game.getCurrentSide())
                ? " · 将军" : "";
        tvGameMeta.setText(DIFFICULTY_NAMES[aiDifficulty - 1] + "难度 · 第" + round
                + "回合 · " + turn + "走" + check + " · " + style);
    }

    private void restartGame() {
        gameGeneration++;
        isProcessing = false;
        if (ai != null) ai.cancel();
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
        renderGameMeta();
    }

    @Override
    public void onDestroy() {
        gameGeneration++;
        if (ai != null) ai.cancel();
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}
