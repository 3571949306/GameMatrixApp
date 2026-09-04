package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.doudizhu.save.DoudizhuSaveSink;
import com.gamecenter.app.doudizhu.score.ScoreBoard;
import com.gamecenter.app.games.GameUsageStore;

/**
 * 斗地主模块主 Fragment。
 *
 * <p>承载两种内容视图：菜单页（默认）与单机牌桌页（{@link DoudizhuGameScreen}），
 * 通过替换 contentRoot 子视图切换（不使用子 Fragment 事务，避免动态模块类
 * 在 FragmentManager 恢复时经过宿主 classloader 的加载风险）。</p>
 *
 * <p>对局控制器 {@link DoudizhuGameController} 持有于本 Fragment（retained），
 * 旋转屏幕后重建视图并通过 attachUi 重放对局状态。</p>
 *
 * <p>P3 存档：onPause 时把进行中的对局序列化落到 {@link SaveManager}（auto 槽）；
 * 菜单页检测到有效存档时显示"继续对局"，恢复失败/对局结束后清档。
 * 对局结束经 {@code GameUsageStore} 上报胜负与得分（对齐 game2048 先例）。</p>
 */
public class DoudizhuModuleFragment extends Fragment {

    private static final String TAG = "DoudizhuFragment";

    /**
     * 宿主透传的难度参数 key（GameLauncherHelper.EXTRA_DIFFICULTY_INDEX）。
     * 值为 0=简单 / 1=普通 / 2=困难；缺省时视为普通档（点"单机模式"自选难度不受影响）。
     */
    static final String ARG_DIFFICULTY_INDEX = "game_difficulty_index";

    /** 存档 GAME_ID 与槽位（对齐 game2048 先例） */
    private static final String GAME_ID = "doudizhu";
    private static final String SLOT_AUTO = "auto";

    /** 对局控制器：跨配置变更保留（setRetainInstance），无 View 引用 */
    private DoudizhuGameController controller;

    /** 宿主推荐的难度档位，-1 表示未传入 */
    private int prefilledDifficulty = -1;

    private SaveManager saveManager;
    private GameUsageStore usageStore;

    private FrameLayout contentRoot;

    public DoudizhuModuleFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 斗地主牌桌为横屏体验：模块自行请求方向，退出时还原为宿主的竖屏默认，
        // 不影响其他动态模块游戏（不改宿主清单）
        requireActivity().setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 读取宿主透传的难度推荐（DynamicGameActivity 写入 arguments）
        android.os.Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_DIFFICULTY_INDEX)) {
            prefilledDifficulty = args.getInt(ARG_DIFFICULTY_INDEX, -1);
        }
        Context ctx = requireContext().getApplicationContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
    }

    @Override
    public void onDestroy() {
        requireActivity().setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        persistSaveIfPlaying();
    }

    /**
     * 对局进行中则落存档（供进程被杀/重进恢复）；
     * 对局已结束则清档；菜单态不动存档（保留"继续对局"入口）。
     */
    private void persistSaveIfPlaying() {
        if (saveManager == null || controller == null) return;
        if (controller.isInGame() && !controller.isGameOver()) {
            String json = controller.captureSaveJson();
            if (json != null) {
                saveManager.save(GAME_ID, SLOT_AUTO, json);
            }
        } else if (controller.isGameOver()) {
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
        }
    }

    private boolean isNightMode(@Nullable Context context) {
        if (context == null) return false;
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        contentRoot = new FrameLayout(ctx);
        if (controller != null && controller.isInGame()) {
            showGameScreen();
        } else {
            showMenu();
        }
        return contentRoot;
    }

    // ============ 菜单页 ============

    private void showMenu() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode(ctx);

        int bgColor = night ? 0xFF121212 : 0xFFF5F5F5;
        int textColor = night ? 0xFFEEEEEE : 0xFF212121;
        int accentColor = night ? 0xFFBB86FC : 0xFF6200EE;

        LinearLayout menu = new LinearLayout(ctx);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(bgColor);
        menu.setGravity(Gravity.CENTER_HORIZONTAL);
        menu.setPadding(0, (int) (48 * dp), 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_doudizhu));
        tvTitle.setTextSize(32);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(accentColor);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        menu.addView(tvTitle);

        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText(getString(R.string.game_title_doudizhu_subtitle));
        tvSubtitle.setTextSize(16);
        tvSubtitle.setTextColor(textColor);
        tvSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (8 * dp);
        tvSubtitle.setLayoutParams(subLp);
        menu.addView(tvSubtitle);

        String saved = loadValidSaveJson();
        if (saved != null) {
            Button btnResume = new Button(ctx);
            btnResume.setText(getString(R.string.game_ddz_resume_game));
            btnResume.setTextColor(Color.WHITE);
            btnResume.setBackgroundColor(0xFF2E7D32);
            LinearLayout.LayoutParams resumeLp = new LinearLayout.LayoutParams(
                    (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
            resumeLp.topMargin = (int) (24 * dp);
            btnResume.setLayoutParams(resumeLp);
            btnResume.setOnClickListener(v -> resumeGame(saved));
            menu.addView(btnResume);
        }

        Button btnSingle = new Button(ctx);
        btnSingle.setText(getString(R.string.game_doudizhu_single));
        btnSingle.setTextColor(Color.WHITE);
        btnSingle.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (24 * dp);
        btnSingle.setLayoutParams(btnLp);
        btnSingle.setOnClickListener(v -> {
            // 宿主已透传难度推荐：直接以该档位开局；否则弹三档选择
            if (prefilledDifficulty >= 0) {
                startGame(prefilledDifficulty);
            } else {
                showDifficultyDialog(ctx);
            }
        });
        menu.addView(btnSingle);

        Button btnRules = new Button(ctx);
        btnRules.setText(getString(R.string.game_btn_rules));
        btnRules.setTextColor(textColor);
        btnRules.setBackgroundColor(night ? 0xFF2D2D2D : 0xFFE0E0E0);
        LinearLayout.LayoutParams rulesLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        rulesLp.topMargin = (int) (16 * dp);
        btnRules.setLayoutParams(rulesLp);
        btnRules.setOnClickListener(v -> showRulesDialog(ctx));
        menu.addView(btnRules);

        swapContent(menu);
    }

    /** 读取有效存档（不存在或损坏时顺带清档并返回 null）。 */
    @Nullable
    private String loadValidSaveJson() {
        if (saveManager == null || !saveManager.hasSave(GAME_ID, SLOT_AUTO)) return null;
        String saved = saveManager.load(GAME_ID, SLOT_AUTO);
        if (saved == null || !DoudizhuGameController.canRestore(saved)) {
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            return null;
        }
        return saved;
    }

    private void showDifficultyDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.game_doudizhu_choose_difficulty)
                .setItems(
                        new CharSequence[]{
                                getString(R.string.doudizhu_diff_easy),
                                getString(R.string.doudizhu_diff_normal),
                                getString(R.string.doudizhu_diff_hard)},
                        (d, which) -> {
                            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
                            startGame(which);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRulesDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.game_doudizhu_rules_title))
                .setMessage(getString(R.string.game_doudizhu_rules_msg)
                        + getString(R.string.game_ddz_rules_extra))
                .setPositiveButton(R.string.game_doudizhu_got_it, null)
                .show();
    }

    // ============ 牌桌页 ============

    private void startGame(int difficulty) {
        ensureController();
        controller.startNewGame(difficulty);
        if (contentRoot != null) {
            showGameScreen();
        }
    }

    /** 从存档恢复对局并进入牌桌。 */
    private void resumeGame(String saveJson) {
        ensureController();
        if (controller.restoreFromSave(saveJson)) {
            if (contentRoot != null) {
                showGameScreen();
            }
        } else {
            saveManager.deleteSave(GAME_ID, SLOT_AUTO);
            android.widget.Toast.makeText(requireContext(),
                    R.string.game_ddz_save_broken, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureController() {
        if (controller == null) {
            controller = new DoudizhuGameController();
        }
    }

    private void showGameScreen() {
        Context ctx = requireContext();
        DoudizhuGameScreen screen = new DoudizhuGameScreen(ctx, controller,
                this::exitToMenu, this::onSettled);
        swapContent(screen);
    }

    /** 结算上报：胜负与得分写入宿主统计（对齐 game2048 recordScore 先例）。 */
    private void onSettled(ScoreBoard.Settlement settlement) {
        try {
            if (settlement.humanWon()) {
                usageStore.recordWin(GAME_ID);
            } else {
                usageStore.recordLoss(GAME_ID);
            }
            if (settlement.humanScoreDelta > 0) {
                usageStore.recordScore(GAME_ID, settlement.humanScoreDelta);
            }
        } catch (Exception e) {
            Log.w(TAG, "成绩上报失败", e);
        }
        // 一局结束清档（文档 P3：一局结束清档）
        saveManager.deleteSave(GAME_ID, SLOT_AUTO);
    }

    private void exitToMenu() {
        if (controller != null) {
            // 退出确认弹窗承诺"退出前将保存进度"（P3 复查修复 A）：
            // 对局进行中在 shutdown()（清空对局状态）之前落存档，已结束/大厅态清档
            controller.persistOnExit(new DoudizhuSaveSink() {
                @Override
                public void save(String json) {
                    saveManager.save(GAME_ID, SLOT_AUTO, json);
                }

                @Override
                public void clear() {
                    saveManager.deleteSave(GAME_ID, SLOT_AUTO);
                }
            });
            controller.shutdown();
            controller = null;
        }
        if (contentRoot != null) {
            showMenu();
        }
    }

    private void swapContent(View view) {
        contentRoot.removeAllViews();
        contentRoot.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
