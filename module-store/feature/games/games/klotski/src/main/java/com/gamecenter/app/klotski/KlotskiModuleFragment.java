package com.gamecenter.app.klotski;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import android.widget.Button;

import org.json.JSONObject;

/**
 * 华容道游戏 Fragment。
 */
public class KlotskiModuleFragment extends Fragment {

    private static final String GAME_ID = "klotski";
    private static final String TAG = "KlotskiFragment";
    private static final String SLOT_AUTO = "auto";

    private static final String LEVEL_CLASSIC = "classic";
    private static final String LEVEL_RANDOM = "random";
    private static final String LEVEL_FREE = "legacy_free";
    private static final int SAVE_FORMAT_VERSION = 1;

    private String selectedLevelId = LEVEL_CLASSIC;
    private String restartStateCsv;
    private String sourceLevelId;
    private JSONObject saveEnvelope = new JSONObject();
    private boolean allowAutoSave = true;
    private long sessionRevision;
    private long hintRequestId;
    private Button btnHint;
    private AlertDialog levelDialog;
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
        moduleRes = com.gamecenter.app.modules.ModuleManager.INSTANCE.getModuleResources("klotski");
        if (moduleRes == null) {
            Toast.makeText(requireContext(), R.string.klotski_get_resource_failed, Toast.LENGTH_SHORT).show();
            return new FrameLayout(requireContext());
        }

        // 获取模块的 DexClassLoader，供 LayoutInflater 加载模块内的自定义 View（如 KlotskiView）
        // 若不重写 getClassLoader()，LayoutInflater 会使用宿主 ClassLoader，导致 ClassNotFoundException。
        final ClassLoader moduleClassLoader =
                com.gamecenter.app.modules.ModuleLoader.INSTANCE.getModuleClassLoader("klotski");

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
        int layoutId = moduleRes.getLayoutResId("activity_klotski");
        if (layoutId == 0) {
            // 模块 APK 损坏或资源包名解析失败时 getLayoutResId 返回 0，直接 inflate 会触发
            // Resources$NotFoundException 闪退。此处兜底返回空 FrameLayout，与 moduleRes==null 分支保持一致。
            Log.e("KlotskiModuleFragment",
                    "布局资源未找到: activity_klotski (moduleRes=" + moduleRes + ")");
            Toast.makeText(requireContext(), R.string.klotski_get_resource_failed, Toast.LENGTH_SHORT).show();
            return new FrameLayout(requireContext());
        }
        View view = localInflater.inflate(layoutId, container, false);

        saveManager = SaveManager.getInstance(requireContext());
        usageStore = new GameUsageStore(requireContext());
        mainHandler = new Handler(Looper.getMainLooper());

        TextView tvTitle = view.findViewById(getResId("tv_game_title", "id"));
        if (tvTitle != null) {
            String titleText = moduleRes.getString("game_klotski");
            if (titleText != null) {
                tvTitle.setText(titleText);
            }
        }

        tvStatus = view.findViewById(getResId("tv_game_status", "id"));
        tvMoves = view.findViewById(getResId("tv_moves", "id"));
        klotskiView = view.findViewById(getResId("klotski_view", "id"));
        btnHint = view.findViewById(getResId("btn_hint", "id"));

        KlotskiGame initialGame = new KlotskiGame();
        allowAutoSave = true;
        adoptGame(initialGame, LEVEL_CLASSIC, initialGame.serializeState(), null,
                new JSONObject(), "滑动方块，帮助曹操到达下方出口");
        String saved = saveManager.load(GAME_ID, SLOT_AUTO);
        if (saved != null && !restoreSavedGame(saved)) {
            allowAutoSave = false;
            updateStatus("原存档保留，可选局或重开开始新局");
        }

        klotskiView.setOnWinListener(this::onGameWon);
        klotskiView.setOnMoveListener(() -> {
            invalidateHintRequest();
            updateStatus("继续移动，让曹操到达下方出口");
        });

        Button btnRestart = view.findViewById(getResId("btn_game_restart", "id"));
        btnRestart.setOnClickListener(v -> restartCurrentGame());

        Button btnSelect = view.findViewById(getResId("btn_shuffle", "id"));
        btnSelect.setOnClickListener(v -> showLevelPicker());

        btnHint.setOnClickListener(v -> {
            if (game == null) return;
            if (game.isWon()) {
                advanceAfterPractice();
            } else {
                requestHint();
            }
        });

        Button btnTutorial = view.findViewById(getResId("btn_game_tutorial", "id"));
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showKlotskiTutorial(requireContext()));
        return view;
    }

    private void showLevelPicker() {
        if (levelDialog != null) levelDialog.dismiss();
        java.util.List<KlotskiPracticeLevels.Level> levels = KlotskiPracticeLevels.all();
        String[] labels = new String[levels.size() + 2];
        labels[0] = "经典局 · 横刀立马";
        for (int i = 0; i < levels.size(); i++) {
            KlotskiPracticeLevels.Level level = levels.get(i);
            labels[i + 1] = "练习 " + (i + 1) + " · " + level.title
                    + "（参考 " + level.referenceMoves + " 步）";
        }
        labels[labels.length - 1] = "随机打乱 · 新棋局";
        final KlotskiView ownerView = klotskiView;
        levelDialog = new AlertDialog.Builder(requireContext())
                .setTitle("选择棋局")
                .setItems(labels, (dialog, which) -> {
                    if (klotskiView != ownerView || getView() == null) return;
                    if (which == 0) startSelectedGame(LEVEL_CLASSIC);
                    else if (which == labels.length - 1) startSelectedGame(LEVEL_RANDOM);
                    else startSelectedGame(levels.get(which - 1).id);
                })
                .setNegativeButton("取消", null)
                .create();
        levelDialog.show();
    }

    private void startSelectedGame(String levelId) {
        KlotskiGame nextGame = new KlotskiGame();
        KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(levelId);
        if (level != null) {
            if (!nextGame.restoreState(level.initialStateCsv)) {
                Log.e(TAG, "Invalid built-in practice: " + levelId);
                return;
            }
        } else if (LEVEL_RANDOM.equals(levelId)) {
            nextGame.shuffle();
        } else if (!LEVEL_CLASSIC.equals(levelId)) {
            return;
        }
        allowAutoSave = true;
        adoptGame(nextGame, levelId, nextGame.serializeState(), null, new JSONObject(),
                level == null ? "滑动方块，帮助曹操到达下方出口" : "每次滑动一格，试着接近参考步数");
        saveCurrentGame();
    }

    private void restartCurrentGame() {
        KlotskiGame nextGame = new KlotskiGame();
        if (!nextGame.restoreState(restartStateCsv)) {
            Log.w(TAG, "Cannot restore current starting board");
            return;
        }
        allowAutoSave = true;
        adoptGame(nextGame, selectedLevelId, restartStateCsv, sourceLevelId, saveEnvelope,
                "已重开当前棋局");
        saveCurrentGame();
        Toast.makeText(requireContext(), R.string.klotski_reset, Toast.LENGTH_SHORT).show();
    }

    private void adoptGame(KlotskiGame nextGame, String levelId, String initialState,
                           String originalId, JSONObject envelope, String message) {
        sessionRevision++;
        invalidateHintRequest();
        game = nextGame;
        selectedLevelId = levelId;
        restartStateCsv = initialState;
        sourceLevelId = originalId;
        saveEnvelope = envelope;
        klotskiView.setGame(nextGame);
        resetTimer();
        updateStatus(game.isWon() ? "已完成，可重开或选择棋局" : message);
        refreshHintButton();
    }

    private boolean restoreSavedGame(String saved) {
        try {
            KlotskiGame restored = new KlotskiGame();
            if (!saved.trim().startsWith("{")) {
                if (!restored.restoreState(saved)) return false;
                adoptGame(restored, LEVEL_FREE, "0," + restored.serializeBoardState(), null,
                        new JSONObject(), "已恢复历史棋盘与步数");
                return true;
            }
            JSONObject envelope = new JSONObject(saved);
            if (envelope.optInt("formatVersion", -1) != SAVE_FORMAT_VERSION
                    || !restored.restoreState(envelope.optString("state", null))) return false;
            String levelId = envelope.optString("levelId", "");
            String originalId = null;
            String initialState;
            KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(levelId);
            if (level != null) {
                initialState = level.initialStateCsv;
            } else if (LEVEL_CLASSIC.equals(levelId)) {
                initialState = new KlotskiGame().serializeState();
            } else {
                if (!LEVEL_RANDOM.equals(levelId) && !LEVEL_FREE.equals(levelId)) {
                    originalId = levelId;
                    levelId = LEVEL_FREE;
                } else if (LEVEL_FREE.equals(levelId)) {
                    originalId = envelope.optString("sourceLevelId", null);
                }
                KlotskiGame initial = new KlotskiGame();
                if (initial.restoreState(envelope.optString("initialState", null)) && !initial.isWon()) {
                    initialState = "0," + initial.serializeBoardState();
                } else {
                    initialState = "0," + restored.serializeBoardState();
                }
            }
            adoptGame(restored, levelId, initialState, originalId, envelope, "已恢复棋局与步数");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Restore saved game: " + e.getMessage());
            return false;
        }
    }

    private void saveCurrentGame() {
        if (!allowAutoSave || saveManager == null || game == null) return;
        if (game.isWon() && KlotskiPracticeLevels.find(selectedLevelId) == null) return;
        try {
            saveEnvelope.put("formatVersion", SAVE_FORMAT_VERSION);
            saveEnvelope.put("levelId", selectedLevelId);
            saveEnvelope.put("state", game.serializeState());
            saveEnvelope.put("initialState", restartStateCsv);
            if (sourceLevelId != null) saveEnvelope.put("sourceLevelId", sourceLevelId);
            else saveEnvelope.remove("sourceLevelId");
            saveManager.save(GAME_ID, SLOT_AUTO, saveEnvelope.toString());
        } catch (Exception e) {
            Log.w(TAG, "Save game: " + e.getMessage());
        }
    }

    private void onGameWon() {
        if (!gameActive || game == null || !game.isWon()) return;
        gameActive = false;
        invalidateHintRequest();
        int moves = game.getMoves();
        KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(selectedLevelId);
        if (level != null) saveCurrentGame();
        else if (allowAutoSave) saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        if (level != null) savePracticeBestMoves(level.id, moves);
        else if (sourceLevelId == null) saveBestMoves(moves);
        updateStatus("已用 " + moves + " 步通关！" + (level == null ? "可重开或选局" : "点击下方继续"));
        refreshHintButton();
        Toast.makeText(requireContext(), getString(R.string.klotski_win_format, moves), Toast.LENGTH_LONG).show();
        if (elapsedMs > 0) usageStore.recordPlayTime(GAME_ID, elapsedMs);
    }

    private void advanceAfterPractice() {
        java.util.List<KlotskiPracticeLevels.Level> levels = KlotskiPracticeLevels.all();
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).id.equals(selectedLevelId)) {
                startSelectedGame(i + 1 < levels.size() ? levels.get(i + 1).id : LEVEL_CLASSIC);
                return;
            }
        }
    }

    private void requestHint() {
        if (isHintSearching || game == null || game.isWon()) return;
        final KlotskiGame snapshot = new KlotskiGame();
        if (!snapshot.restoreState(game.serializeState())) return;
        final KlotskiGame ownerGame = game;
        final KlotskiView ownerView = klotskiView;
        final Handler callbackHandler = mainHandler;
        final String boardBeforeSearch = game.serializeBoardState();
        final long revision = sessionRevision;
        final long request = ++hintRequestId;
        isHintSearching = true;
        refreshHintButton();
        updateStatus("正在计算最优解...");
        executeHintSearch(() -> {
            final KlotskiGame.HintResult hint = snapshot.getHint();
            callbackHandler.post(() -> {
                // Check ownership before changing the busy flag, button, status or arrow.
                if (request != hintRequestId || revision != sessionRevision
                        || game != ownerGame || klotskiView != ownerView
                        || !isAdded() || getView() == null
                        || !boardBeforeSearch.equals(game.serializeBoardState())) return;
                isHintSearching = false;
                refreshHintButton();
                if (hint != null) {
                    KlotskiGame.Block block = game.getBlocks().get(hint.blockId);
                    klotskiView.showHint(hint);
                    updateStatus("移动「" + block.name + "」向" + getDirection(hint.dx, hint.dy)
                            + "，距出口 " + hint.totalSteps + " 步");
                    Toast.makeText(requireContext(), R.string.klotski_hint_tip, Toast.LENGTH_LONG).show();
                } else {
                    updateStatus("未找到解法，请尝试其他走法");
                    Toast.makeText(requireContext(), R.string.klotski_compute_retry, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // A scheduling seam for tests; the work always solves the captured real board.
    void executeHintSearch(Runnable search) {
        new Thread(search, "klotski-hint").start();
    }

    private void invalidateHintRequest() {
        hintRequestId++;
        isHintSearching = false;
        if (klotskiView != null) klotskiView.clearHint();
        refreshHintButton();
    }

    private void refreshHintButton() {
        if (btnHint == null || game == null) return;
        KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(selectedLevelId);
        if (game.isWon() && level != null) {
            java.util.List<KlotskiPracticeLevels.Level> levels = KlotskiPracticeLevels.all();
            boolean last = levels.get(levels.size() - 1).id.equals(level.id);
            btnHint.setText(last ? "经典局" : "下一关");
            btnHint.setContentDescription(last ? "进入经典局" : "进入下一练习关");
            btnHint.setEnabled(true);
        } else {
            btnHint.setText("提示");
            btnHint.setContentDescription("提示下一步");
            btnHint.setEnabled(!game.isWon() && !isHintSearching);
        }
    }

    private String getDirection(int dx, int dy) {
        if (dx > 0) return "右";
        if (dx < 0) return "左";
        if (dy > 0) return "下";
        if (dy < 0) return "上";
        return "";
    }

    private String getSessionTitle() {
        KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(selectedLevelId);
        if (level != null) {
            return "练习 " + (KlotskiPracticeLevels.all().indexOf(level) + 1) + "/"
                    + KlotskiPracticeLevels.all().size() + " · " + level.title;
        }
        if (LEVEL_CLASSIC.equals(selectedLevelId)) return "经典局 · 横刀立马";
        if (LEVEL_RANDOM.equals(selectedLevelId)) return "随机局 · 重开保留本局布局";
        return sourceLevelId == null ? "自由局 · 历史进度" : "自由局 · 未知棋局";
    }

    private void updateStatus(String status) {
        if (tvStatus != null) tvStatus.setText(getSessionTitle() + "\n" + status);
        if (tvMoves != null && game != null) {
            String moves = "步数 " + game.getMoves();
            KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(selectedLevelId);
            if (level != null) {
                int best = loadPracticeBestMoves(level.id);
                moves += " · 参考 " + level.referenceMoves + " · 最佳 " + (best > 0 ? best : "—");
            }
            tvMoves.setText(moves);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isHintSearching) updateStatus("提示已取消，可重新请求");
        invalidateHintRequest();
        if (levelDialog != null) {
            levelDialog.dismiss();
            levelDialog = null;
        }
        if (gameActive && elapsedMs > 0) {
            elapsedMs = System.currentTimeMillis() - gameStartTime;
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }
        saveCurrentGame();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (gameActive && game != null && !game.isWon()) {
            gameStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onDestroyView() {
        sessionRevision++;
        invalidateHintRequest();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (levelDialog != null) {
            levelDialog.dismiss();
            levelDialog = null;
        }
        if (klotskiView != null) {
            klotskiView.setOnWinListener(null);
            klotskiView.setOnMoveListener(null);
            klotskiView.setGame(null);
        }
        klotskiView = null;
        btnHint = null;
        tvStatus = null;
        tvMoves = null;
        game = null;
        gameActive = false;
        super.onDestroyView();
    }

    private int loadPracticeBestMoves(String levelId) {
        try {
            String saved = saveManager.loadProgress(GAME_ID);
            if (saved == null) return 0;
            JSONObject records = new JSONObject(saved).optJSONObject("practiceBestMoves");
            return records == null ? 0 : records.optInt(levelId, 0);
        } catch (Exception e) {
            Log.w(TAG, "Read practice progress: " + e.getMessage());
            return 0;
        }
    }

    private void savePracticeBestMoves(String levelId, int moves) {
        try {
            String saved = saveManager.loadProgress(GAME_ID);
            JSONObject progress = saved == null ? new JSONObject() : new JSONObject(saved);
            JSONObject records = progress.optJSONObject("practiceBestMoves");
            if (records == null) {
                if (progress.has("practiceBestMoves")) {
                    Log.w(TAG, "Unrecognized practice records retained");
                    return;
                }
                records = new JSONObject();
            }
            int previous = records.optInt(levelId, Integer.MAX_VALUE);
            if (moves < previous) {
                records.put(levelId, moves);
                progress.put("practiceBestMoves", records);
                saveManager.saveProgress(GAME_ID, progress.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Save practice progress: " + e.getMessage());
        }
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
            Log.w(TAG, "Save progress: " + e.getMessage());
        }
    }

    private void resetTimer() {
        gameActive = game != null && !game.isWon();
        elapsedMs = 0;
        gameStartTime = System.currentTimeMillis();
    }
}
