package com.gamecenter.app.chinesechess;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
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
import com.gamecenter.app.games.adaptive.AdaptiveAdvisor;
import com.gamecenter.app.games.rating.RatingStore;

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

    /** 当前残局关卡（null 表示普通模式）。残局中"重新开始"= 重玩当前关。 */
    private ChineseChessEndgames.EndgameSpec currentEndgame;

    /** 对局回放记录器：记录开局前与每步落子后的棋盘快照，终局后供复盘。 */
    private final ChineseChessReplay replay = new ChineseChessReplay();

    /** 是否处于复盘回放模式：回放中棋盘只读，点击不落子。 */
    private boolean isReplaying = false;

    /** 当前回放到的快照下标（0=开局前，replay.size()-1=终局）。 */
    private int replayIndex = 0;

    /** 复盘渲染用临时棋局：仅装载快照局面供视图绘制，不承载真实对局。 */
    private ChineseChessGame replayGame;

    /** 终局面板上的「复盘」入口按钮（纯代码构建，仅终局可见）。 */
    private Button btnReplayEntry;

    /** 复盘控制条（纯代码构建：|◀ / ◀ / 步数 / ▶ / ▶| / 退出复盘）。 */
    private LinearLayout replayBar;

    /** 复盘步数显示（"x/N"）。 */
    private TextView tvReplayStep;

    /** 复盘 AI 标注：下标 i-1 对应第 i 步（快照 i-1→i）；进入复盘时后台一次性计算。 */
    private List<ChineseChessReviewAnnotator.Annotation> replayAnnotations;

    /** 当前步标注短评显示（纯代码构建，挂在回放控制条下方，随导航更新）。 */
    private TextView tvReplayAnnotation;

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

    /** AI 难度自适应推荐档（0=未加载/无推荐，1..MAX_AI_DIFFICULTY=推荐档）。仅用于"荐"徽标渲染。 */
    private int recommendedDifficultyTier = 0;

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

        // AI 难度自适应推荐：面板首次显示时后台读取近期胜负并刷新"荐"徽标。
        refreshAdaptiveRecommendation();

        view.findViewById(getResId("btn_start_game", "id")).setOnClickListener(v -> beginGame(aiDifficulty));
        view.findViewById(getResId("btn_tutorial", "id")).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(requireContext()));
        view.findViewById(getResId("btn_endgame", "id")).setOnClickListener(v ->
                showEndgamePicker());
        view.findViewById(getResId("btn_undo", "id")).setOnClickListener(v -> undoLastMove());
        view.findViewById(getResId("btn_hint", "id")).setOnClickListener(v -> showHint());
        view.findViewById(getResId("btn_restart", "id")).setOnClickListener(v -> restartGame());
        view.findViewById(getResId("btn_tutorial_ingame", "id")).setOnClickListener(v ->
                GameTutorialHelper.showChineseChessTutorial(requireContext()));
        view.findViewById(getResId("btn_online", "id")).setOnClickListener(v -> {
            // 分层说明：OnlinePlayGate 管「服务器中继联机」（基础设施未接入，保持下线）；
            // 局域网 P2P 双机对战（本机直连 ServerSocket+NSD，无服务器依赖）是真实实现，
            // 不经该闸门，直接进入局域网对战页。
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

        // 复盘入口按钮与回放控制条（纯代码构建，不改动模块布局资源）。
        setupReplayUi(view);

        // 拦截系统返回键
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (difficultyPanel.getVisibility() == View.GONE) {
                    if (currentEndgame != null) {
                        // 返回键退出残局模式，回到难度选择面板。
                        currentEndgame = null;
                    }

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
            // AI 难度自适应推荐："·荐"徽标仅提示推荐档（推荐≠选中，可与"✓"同档共存）。
            button.setText((selected ? "✓ " : "") + DIFFICULTY_NAMES[i]
                    + (recommendedDifficultyTier == i + 1 ? "·荐" : ""));
            button.setAlpha(selected ? 1f : 0.68f);
            button.setSelected(selected);
        }
        renderGameMeta();
    }

    /**
     * AI 难度自适应推荐：按近期胜负在后台线程读 Room（同步读不可上主线程）并计算
     * 推荐档，回主线程仅刷新"荐"徽标。产品规范要求不得跳过用户可见的难度选择，
     * 因此本方法只做提示，绝不修改 aiDifficulty（选中档仍由用户点击决定）。
     */
    private void refreshAdaptiveRecommendation() {
        final GameUsageStore store = usageStore;
        if (store == null) return;
        // 在主线程捕获当前档位，避免后台线程读到竞态值。
        final int currentTier = aiDifficulty;
        // 一次性短任务直接开线程，读毕即回收；回调经 uiHandler 切回主线程。
        new Thread(() -> {
            try {
                int win = store.getWinCount(GAME_ID);
                int loss = store.getLossCount(GAME_ID);
                int tier = AdaptiveAdvisor.recommendedTier(win, loss, currentTier, MAX_AI_DIFFICULTY);
                uiHandler.post(() -> {
                    // Fragment 已销毁则放弃渲染（isAdded 判空保证销毁安全）。
                    if (!isAdded() || difficultyPanel == null) return;
                    recommendedDifficultyTier = tier;
                    // 面板仍在显示时立刻重渲染徽标；已进入对局则留待下次面板显示。
                    if (difficultyPanel.getVisibility() != View.GONE) {
                        selectDifficulty(aiDifficulty);
                    }
                });
            } catch (Exception e) {
                // 推荐徽标是增强信息，读取失败仅告警，不影响难度选择主流程。
                Log.w(TAG, "读取难度自适应推荐数据失败", e);
            }
        }).start();
    }

    private void beginGame(int difficulty) {
        gameGeneration++;
        isProcessing = false;
        currentEndgame = null;
        ai = new ChineseChessAI(difficulty);
        game.reset();

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearLastMove();

        // 新局：清空回放记录，保存开局前快照。
        replay.clear();
        recordReplaySnapshot();
        if (btnReplayEntry != null) btnReplayEntry.setVisibility(View.GONE);

        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        showStatus("你的回合 - 红方先行");
        renderGameMeta();
    }

    /** 残局关卡选择器：列出全部内置关卡，已通关的带 ✓ 标记。 */
    private void showEndgamePicker() {
        ChineseChessEndgames.EndgameSpec[] levels = ChineseChessEndgames.LEVELS;
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            boolean solved = ChineseChessUiPreferences.isEndgameSolved(requireContext(), levels[i].id);
            items[i] = (i + 1) + ". " + levels[i].name + "（" + levels[i].tag + "）"
                    + (solved ? " ✓已通关" : "");
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("残局挑战")
                .setItems(items, (dialog, which) -> beginEndgame(levels[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 进入残局对局：装载关卡局面，红方（玩家）先行，目标将死黑方 AI。 */
    private void beginEndgame(ChineseChessEndgames.EndgameSpec spec) {
        gameGeneration++;
        isProcessing = false;
        if (ai != null) ai.cancel();
        chessView.cancelAnimation();

        ai = new ChineseChessAI(spec.aiDifficulty);
        aiDifficulty = spec.aiDifficulty;

        game.reset();
        if (!game.loadEndgamePosition(spec.pieces, 0)) {
            // 关卡数据经回归测试全量校验，理论不可达；防御性兜底回难度面板。
            currentEndgame = null;
            Toast.makeText(requireContext(), "残局装载失败", Toast.LENGTH_SHORT).show();
            restartGame();
            return;
        }
        currentEndgame = spec;

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearLastMove();

        // 残局新局：清空回放记录，保存残局初始快照。
        replay.clear();
        recordReplaySnapshot();
        if (btnReplayEntry != null) btnReplayEntry.setVisibility(View.GONE);

        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        showStatus("残局·" + spec.name + "：" + spec.description + " 红方先行，将死黑方即通关。");
        renderGameMeta();
    }

    private void showStatus(String msg) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    private void onCellTap(int col, int row) {
        if (isReplaying) return; // 复盘回放中棋盘只读，点击不触发选子/落子
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

            recordReplaySnapshot(); // 玩家着已入盘：追加回放快照（终局着也记录）

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
        if (currentEndgame != null) {
            // 残局胜负不计入使用统计，避免污染普通对局胜率；
            // 棋力分同理不计——残局无 AI 对抗强度语义，Elo 计分会失真。
            if (game.getWinner() == null) {
                showStatus("和棋，未通关");
            } else if (game.getWinner() == ChineseChessGame.Side.RED) {
                boolean firstClear = !ChineseChessUiPreferences.isEndgameSolved(
                        requireContext(), currentEndgame.id);
                if (firstClear) {
                    ChineseChessUiPreferences.markEndgameSolved(
                            requireContext(), currentEndgame.id);
                }
                showStatus("🎉 残局《" + currentEndgame.name + "》通关"
                        + (firstClear ? "！" : "（已完成过）"));
            } else {
                showStatus("AI 防守成功，未通关，可重试");
            }
            showReplayEntry();
            renderGameMeta();
            return;
        }

        if (game.getWinner() == null) {
            showStatus("和棋！" + ratingSuffix(0.5));
        } else if (game.getWinner() == ChineseChessGame.Side.RED) {
            showStatus("🎉 恭喜获胜！" + ratingSuffix(1.0));
            usageStore.recordWin(GAME_ID);
        } else {
            showStatus("AI获胜！" + ratingSuffix(0.0));
            usageStore.recordLoss(GAME_ID);
        }
        showReplayEntry();
        renderGameMeta();
    }

    /**
     * 普通对局终局按 Elo 更新玩家棋力分，并返回追加到终局文案的后缀
     * （如" 棋力 1012（+12）"）。仅普通模式调用：残局无 AI 对抗强度语义，
     * 不计棋力分（见 showGameEndStatus 残局分支注释）。
     */
    private String ratingSuffix(double score) {
        Context context = requireContext();
        int before = RatingStore.getRating(context, GAME_ID);
        int after = RatingStore.recordResult(context, GAME_ID,
                RatingStore.aiRatingForTier(aiDifficulty), score);
        int delta = after - before;
        return " 棋力 " + after + "（" + (delta >= 0 ? "+" : "") + delta + "）";
    }

    // ==================== 对局回放（复盘） ====================

    /** 记录当前局面为回放快照：开局前与每次 commitMove 成功后各调用一次。 */
    private void recordReplaySnapshot() {
        replay.recordSnapshot(game.getBoardAsIntArray(),
                game.getCurrentSide() == ChineseChessGame.Side.RED ? 0 : 1);
    }

    /** 构建复盘入口按钮与回放控制条（纯代码构建，不改动模块布局资源）。 */
    private void setupReplayUi(View root) {
        btnReplayEntry = new Button(requireContext());
        btnReplayEntry.setText("复盘");
        btnReplayEntry.setTextSize(13);
        // 程序化 Button 必须显式关闭 stateListAnimator，避免宿主主题动画资源 ID 冲突。
        btnReplayEntry.setStateListAnimator(null);
        btnReplayEntry.setVisibility(View.GONE);
        btnReplayEntry.setOnClickListener(v -> enterReplayMode());
        if (controlPanel != null) {
            controlPanel.addView(btnReplayEntry);
        }

        replayBar = new LinearLayout(requireContext());
        replayBar.setOrientation(LinearLayout.HORIZONTAL);
        replayBar.setGravity(Gravity.CENTER);
        replayBar.setVisibility(View.GONE);

        tvReplayStep = new TextView(requireContext());
        tvReplayStep.setGravity(Gravity.CENTER);
        tvReplayStep.setTextColor(0xFF8B5A2B);
        tvReplayStep.setTextSize(14);
        tvReplayStep.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
        stepLp.setMargins(6, 0, 6, 0);
        stepLp.gravity = Gravity.CENTER_VERTICAL;
        tvReplayStep.setLayoutParams(stepLp);

        replayBar.addView(buildReplayButton("|◀", 1f, v -> seekReplay(0)));
        replayBar.addView(buildReplayButton("◀", 1f, v -> seekReplay(replayIndex - 1)));
        replayBar.addView(tvReplayStep);
        replayBar.addView(buildReplayButton("▶", 1f, v -> seekReplay(replayIndex + 1)));
        replayBar.addView(buildReplayButton("▶|", 1f, v -> seekReplay(replay.size() - 1)));
        replayBar.addView(buildReplayButton("退出复盘", 1.5f, v -> exitReplayMode()));

        // 控制条挂到布局根尾（棋盘下方、控制面板之后），默认隐藏不影响原布局。
        ((ViewGroup) root).addView(replayBar);

        // 当前步标注短评：挂在回放控制条正下方，仅复盘模式可见。
        tvReplayAnnotation = new TextView(requireContext());
        tvReplayAnnotation.setGravity(Gravity.CENTER);
        tvReplayAnnotation.setTextColor(0xFF8B5A2B);
        tvReplayAnnotation.setTextSize(13);
        tvReplayAnnotation.setVisibility(View.GONE);
        ((ViewGroup) root).addView(tvReplayAnnotation);
    }

    /** 构建回放控制条按钮（统一关闭 stateListAnimator，与模块布局内 Button 约定一致）。 */
    private Button buildReplayButton(String text, float weight, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(13);
        button.setStateListAnimator(null);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(4, 4, 4, 4);
        button.setLayoutParams(lp);
        return button;
    }

    /** 终局时展示复盘入口（残局与普通对局共用；至少有着法可回放时才展示）。 */
    private void showReplayEntry() {
        if (btnReplayEntry != null && replay.size() >= 2) {
            btnReplayEntry.setVisibility(View.VISIBLE);
        }
    }

    /** 进入复盘模式：回到开局前局面，展示回放控制条，棋盘转为只读。 */
    private void enterReplayMode() {
        if (isReplaying || replay.size() < 2 || chessView == null) return;
        if (replayGame == null) replayGame = new ChineseChessGame();
        isReplaying = true;
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearHint();
        // 复盘期间锁定棋盘触摸；onCellTap 中另有 isReplaying 短路双保险。
        chessView.setLocked(true);
        if (controlPanel != null) controlPanel.setVisibility(View.GONE);
        if (btnReplayEntry != null) btnReplayEntry.setVisibility(View.GONE);
        if (replayBar != null) replayBar.setVisibility(View.VISIBLE);
        if (tvReplayAnnotation != null) {
            tvReplayAnnotation.setVisibility(View.VISIBLE);
            tvReplayAnnotation.setText("");
        }
        computeReplayAnnotations();
        seekReplay(0);
    }

    /** 退出复盘：渲染终局快照、关闭控制条并恢复真实对局交互。 */
    private void exitReplayMode() {
        if (!isReplaying) return;
        isReplaying = false;
        if (replayBar != null) replayBar.setVisibility(View.GONE);
        if (tvReplayAnnotation != null) tvReplayAnnotation.setVisibility(View.GONE);
        // 恢复真实对局渲染：绑定回真实 game 并还原终局面板与上一着标记。
        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();
        List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
        if (!history.isEmpty()) {
            ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
            chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
        }
        chessView.invalidate();
        if (controlPanel != null) controlPanel.setVisibility(View.VISIBLE);
        if (btnReplayEntry != null) btnReplayEntry.setVisibility(View.VISIBLE);
        showStatus("已退出复盘");
    }

    /** 跳转到指定快照并渲染（自动夹取到 [0, size-1]）。 */
    private void seekReplay(int index) {
        if (!isReplaying || replay.size() == 0) return;
        replayIndex = Math.max(0, Math.min(index, replay.size() - 1));
        renderReplayPosition(replayIndex);
        updateReplayStepText();
    }

    /** 将快照装载进复盘临时棋局并驱动棋盘视图渲染。 */
    private void renderReplayPosition(int index) {
        int[][] board = replay.snapshot(index);
        int side = replay.snapshotSide(index);
        if (board == null) return;
        // 复用 loadEndgamePosition 公开口装载任意中间局面：零改动 ChineseChessGame。
        if (!replayGame.loadEndgamePosition(ChineseChessReplay.toEndgameSpec(board), side)) {
            // 理论不可达（快照均来自 commitMove 产生的合法局面）；防御性告警并保持当前画面。
            Log.w(TAG, "复盘快照装载失败 index=" + index + " side=" + side);
            return;
        }
        chessView.bindGame(replayGame);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();
        if (index > 0) {
            // 对比前一快照的差异推断本步着法，标注上一着便于阅读复盘。
            int[] move = ChineseChessReviewAnnotator.diffMove(replay.snapshot(index - 1), board);
            if (move != null) {
                chessView.setLastMove(move[0], move[1], move[2], move[3]);
            }
        }
        chessView.invalidate();
    }

    /** 更新回放步数显示与状态行（到末尾显示终局）。 */
    private void updateReplayStepText() {
        int total = replay.size() - 1; // 总着数（快照数-1）
        if (tvReplayStep != null) {
            tvReplayStep.setText(replayIndex + "/" + total);
        }
        if (replayIndex >= total) {
            showStatus("复盘：终局（" + total + "/" + total + "）");
        } else if (replayIndex == 0) {
            showStatus("复盘：开局前局面（0/" + total + "）");
        } else {
            showStatus("复盘：第 " + replayIndex + "/" + total + " 步");
        }
        updateReplayAnnotationText();
    }

    /** 刷新当前步复盘标注短评（标注未算好或开局前局面时清空显示）。 */
    private void updateReplayAnnotationText() {
        if (tvReplayAnnotation == null) return;
        tvReplayAnnotation.setText("");
        if (replayAnnotations == null || replayIndex <= 0) return;
        int idx = replayIndex - 1;
        if (idx >= replayAnnotations.size()) return;
        ChineseChessReviewAnnotator.Annotation annotation = replayAnnotations.get(idx);
        String side = replay.snapshotSide(idx) == 0 ? "红方" : "黑方";
        String label;
        int color;
        switch (annotation.grade) {
            case GOOD:
                label = "好棋";
                color = 0xFF2E7D32;
                break;
            case BLUNDER:
                label = "疑问手";
                color = 0xFFC62828;
                break;
            default:
                label = "普通";
                color = 0xFF8B5A2B;
                break;
        }
        tvReplayAnnotation.setTextColor(color);
        tvReplayAnnotation.setText(side + " " + label + "：" + annotation.comment);
    }

    /**
     * 后台计算整局复盘标注。
     *
     * <p>量级：每步 = 1 次快照装载 + 1 次对方合法着法贪心扫描（getAllMoves
     * 内每着一次 isInCheck 校验，约 90 格量级扫描），单步约 10^4 基本操作，
     * 常规对局百余步、极端长局数百步整体仍在毫秒级；为避免长局在主线程产生
     * 可感知卡顿，参照 refreshAdaptiveRecommendation 的一次性线程先例在后台
     * 执行。完成经 uiHandler 回主线程刷新当前步显示；代次不符或已退出复盘则
     * 丢弃结果，防止旧标注落入新对局。
     */
    private void computeReplayAnnotations() {
        replayAnnotations = null;
        final int generation = gameGeneration;
        new Thread(() -> {
            List<ChineseChessReviewAnnotator.Annotation> result =
                    new ChineseChessReviewAnnotator().annotateGame(replay);
            uiHandler.post(() -> {
                if (generation != gameGeneration || !isReplaying) return;
                replayAnnotations = result;
                updateReplayStepText();
            });
        }).start();
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

                recordReplaySnapshot(); // AI 着已入盘：追加回放快照（终局着也记录）

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
            // 悔棋一轮撤销玩家+AI 各一着，回放快照同步回退两个。
            replay.pop();
            replay.pop();
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
        if (currentEndgame != null) {
            int ePlies = game == null ? 0 : game.getMoveHistory().size();
            int eRound = ePlies / 2 + 1;
            String eTurn = game != null && game.getCurrentSide() == ChineseChessGame.Side.BLACK
                    ? "黑方 AI" : "红方 你";
            String eCheck = game != null && !game.isGameOver() && game.isInCheck(game.getCurrentSide())
                    ? " · 将军" : "";
            tvGameMeta.setText("残局·" + currentEndgame.name + "（" + currentEndgame.tag
                    + "）· 第" + eRound + "回合 · " + eTurn + "走" + eCheck + " · " + style);
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
        if (isReplaying) {
            // 复盘中触发重开（如返回键）：先退出复盘状态，恢复正常重开流程。
            exitReplayMode();
        }
        if (currentEndgame != null) {
            // 残局中"重新开始"= 重玩当前关卡。
            beginEndgame(currentEndgame);
            return;
        }

        gameGeneration++;
        isProcessing = false;
        if (ai != null) ai.cancel();
        chessView.cancelAnimation();
        game.reset();
        replay.clear(); // 回到难度面板：清空上一局回放记录
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();
        chessView.bindGame(game);
        chessView.setLocked(false);

        difficultyPanel.setVisibility(View.VISIBLE);
        // 对局结束回到难度面板：重读近期胜负刷新"荐"徽标（后台线程，见方法注释）。
        refreshAdaptiveRecommendation();
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
