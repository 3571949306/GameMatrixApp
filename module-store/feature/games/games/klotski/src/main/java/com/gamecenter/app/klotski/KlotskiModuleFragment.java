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

        // 获胜监听
        klotskiView.setOnWinListener(() -> {
            int moves = game.getMoves();
            updateStatus("🎉 完成！曹操逃出华容道！");
            Toast.makeText(requireContext(), getString(R.string.klotski_win_format, moves), Toast.LENGTH_LONG).show();
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            saveBestMoves(moves);
            if (elapsedMs > 0) {
                usageStore.recordPlayTime(GAME_ID, elapsedMs);
            }
        });
        klotskiView.setOnMoveListener(() -> updateStatus("继续移动，目标是让曹操到达下方出口"));

        gameActive = true;
        gameStartTime = System.currentTimeMillis();

        // 重新开始按钮
        Button btnRestart = view.findViewById(getResId("btn_game_restart", "id"));
        btnRestart.setOnClickListener(v -> {
            game.reset();
            klotskiView.clearHint();
            klotskiView.invalidate();
            updateStatus("已重新开始");
            tvMoves.setText("");
            isHintSearching = false;
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            resetTimer();
            Toast.makeText(requireContext(), R.string.klotski_reset, Toast.LENGTH_SHORT).show();
        });

        // 打乱按钮
        Button btnShuffle = view.findViewById(getResId("btn_shuffle", "id"));
        btnShuffle.setOnClickListener(v -> {
            game.shuffle();
            klotskiView.clearHint();
            klotskiView.invalidate();
            updateStatus("🔀 已随机打乱（可解）");
            tvMoves.setText("");
            isHintSearching = false;
            Toast.makeText(requireContext(), R.string.klotski_shuffled, Toast.LENGTH_SHORT).show();
        });

        // 提示按钮
        Button btnHint = view.findViewById(getResId("btn_hint", "id"));
        btnHint.setOnClickListener(v -> {
            if (game.isWon()) {
                Toast.makeText(requireContext(), R.string.klotski_already_won, Toast.LENGTH_SHORT).show();
                return;
            }
            if (isHintSearching) {
                Toast.makeText(requireContext(), R.string.klotski_computing, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(requireContext(), R.string.klotski_hint_tip, Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus("未找到解法，请尝试其他走法");
                        Toast.makeText(requireContext(), R.string.klotski_compute_retry, Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        // 教程按钮
        Button btnTutorial = view.findViewById(getResId("btn_game_tutorial", "id"));
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showKlotskiTutorial(requireContext()));

        klotskiView.invalidate();

        return view;
    }

    private String getDirection(int dx, int dy) {
        if (dx > 0) return "right".equals(moduleRes.getString("direction_right")) ? "右" : "右";
        if (dx < 0) return "左";
        if (dy > 0) return "下";
        if (dy < 0) return "上";
        return "";
    }

    private void updateStatus(String status) {
        if (tvStatus != null) {
            tvStatus.setText(status);
        }
        if (tvMoves != null) {
            if (game.getMoves() > 0) {
                tvMoves.setText(getString(R.string.klotski_moves_format, game.getMoves()));
            } else {
                tvMoves.setText("");
            }
        }
    }

    @Override
    public void onPause() {
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
    public void onResume() {
        super.onResume();
        if (gameActive && !game.isWon()) {
            gameStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (klotskiView != null) {
            klotskiView.clearHint();
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
        gameActive = false;
        elapsedMs = 0;
        gameStartTime = System.currentTimeMillis();
    }
}
