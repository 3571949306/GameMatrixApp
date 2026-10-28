package com.gamecenter.app.reaction;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 反应力模块 Fragment (五星级即开即测标准)。
 *
 * <p>具备即开即测、3 档难度分级、段位评估、大卡片变色打击区与沉浸式 HUD 战况看板。</p>
 */
public class ReactionModuleFragment extends Fragment {

    private static final String GAME_ID = "reaction";
    private static final String PREF_NAME = "reaction_prefs";
    private static final String KEY_DIFFICULTY = "difficulty";

    private static final int COLOR_IDLE = 0xFF37474F;      // 灰蓝
    private static final int COLOR_WAITING = 0xFFE65100;   // 警示深橙
    private static final int COLOR_READY = 0xFF2E7D32;     // 极速翠绿
    private static final int COLOR_RESULT = 0xFF1565C0;    // 科技海蓝
    private static final int COLOR_TOO_EARLY = 0xFFC62828; // 犯规朱红

    private static final String[] DIFFICULTY_NAMES = {"休闲挑战", "标准竞技", "极限闪电"};

    private final ReactionGame game = new ReactionGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout;
    private TextView tvNavDifficulty;
    private TextView tvHudRound;
    private TextView tvHudLast;
    private TextView tvHudBest;
    private TextView tvHudRank;
    private LinearLayout targetCard;
    private TextView tvTargetTitle;
    private TextView tvTargetSubtitle;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private SharedPreferences prefs;

    private int currentDifficulty = ReactionGame.DIFFICULTY_STANDARD;
    private int totalScore = 0;
    private boolean paused = false;
    private int state = 0; // 0: idle, 1: waiting, 2: ready, 3: too_early, 4: result

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        rootLayout = new LinearLayout(ctx);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(dark ? 0xFF121418 : 0xFFF0F4F8);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. 顶部现代化标准导航栏
        View navBar = createNavBar(ctx, dp, dark);
        rootLayout.addView(navBar);

        // 2. HUD 战况看板
        View hudBar = createHudBar(ctx, dp, dark);
        rootLayout.addView(hudBar);

        // 3. 中央即开即测大靶区卡片容器
        LinearLayout cardContainer = new LinearLayout(ctx);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setGravity(Gravity.CENTER);
        cardContainer.setPadding((int) (20 * dp), (int) (16 * dp), (int) (20 * dp), (int) (16 * dp));
        LinearLayout.LayoutParams cardContParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        cardContainer.setLayoutParams(cardContParams);

        targetCard = new LinearLayout(ctx);
        targetCard.setOrientation(LinearLayout.VERTICAL);
        targetCard.setGravity(Gravity.CENTER);
        targetCard.setPadding((int) (24 * dp), (int) (32 * dp), (int) (24 * dp), (int) (32 * dp));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(24 * dp);
        cardBg.setColor(COLOR_IDLE);
        cardBg.setStroke((int) (2 * dp), 0x44FFFFFF);
        targetCard.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        targetCard.setLayoutParams(cardParams);
        targetCard.setOnClickListener(v -> onTargetClick());

        tvTargetTitle = new TextView(ctx);
        tvTargetTitle.setGravity(Gravity.CENTER);
        tvTargetTitle.setTextSize(32f);
        tvTargetTitle.setTextColor(Color.WHITE);
        tvTargetTitle.setText("轻触靶心开始");

        tvTargetSubtitle = new TextView(ctx);
        tvTargetSubtitle.setGravity(Gravity.CENTER);
        tvTargetSubtitle.setTextSize(16f);
        tvTargetSubtitle.setTextColor(0xDDFFFFFF);
        tvTargetSubtitle.setPadding(0, (int) (12 * dp), 0, 0);
        tvTargetSubtitle.setText("变绿时以最快速度点击！");

        targetCard.addView(tvTargetTitle);
        targetCard.addView(tvTargetSubtitle);
        cardContainer.addView(targetCard);
        rootLayout.addView(cardContainer);

        // 4. 底部胶囊控制栏
        View bottomBar = createBottomBar(ctx, dp, dark);
        rootLayout.addView(bottomBar);

        return rootLayout;
    }

    private View createNavBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        bar.setBackgroundColor(dark ? 0xFF1A1D24 : 0xFFFFFFFF);

        // 返回按钮
        Button btnBack = new Button(ctx);
        btnBack.setText("❮ 返回");
        btnBack.setTextSize(13f);
        btnBack.setTextColor(dark ? 0xFFEEEEEE : 0xFF333333);
        applyCapsuleStyle(btnBack, dark ? 0xFF2A2E38 : 0xFFEEEEEE, dp);
        btnBack.setOnClickListener(v -> showExitConfirmDialog());
        bar.addView(btnBack);

        // 居中标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("⚡ 反应力对决");
        tvTitle.setTextSize(16f);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextColor(dark ? 0xFFFFFFFF : 0xFF222222);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bar.addView(tvTitle, titleParams);

        // 难度胶囊
        tvNavDifficulty = new TextView(ctx);
        tvNavDifficulty.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        tvNavDifficulty.setTextSize(12f);
        tvNavDifficulty.setTextColor(0xFF1976D2);
        tvNavDifficulty.setPadding((int) (10 * dp), (int) (6 * dp), (int) (10 * dp), (int) (6 * dp));
        applyCapsuleStyle(tvNavDifficulty, dark ? 0xFF1A2A3A : 0xFFE3F2FD, dp);
        tvNavDifficulty.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvNavDifficulty);

        // 规则按钮
        Button btnRule = new Button(ctx);
        btnRule.setText("ℹ");
        btnRule.setTextSize(14f);
        btnRule.setTextColor(dark ? 0xFFB0BEC5 : 0xFF607D8B);
        applyCapsuleStyle(btnRule, dark ? 0xFF2A2E38 : 0xFFECEFF1, dp);
        btnRule.setOnClickListener(v -> showRuleDialog());
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        ruleParams.setMargins((int) (6 * dp), 0, 0, 0);
        bar.addView(btnRule, ruleParams);

        // 设置按钮
        Button btnSettings = new Button(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(14f);
        btnSettings.setTextColor(dark ? 0xFFB0BEC5 : 0xFF607D8B);
        applyCapsuleStyle(btnSettings, dark ? 0xFF2A2E38 : 0xFFECEFF1, dp);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        setParams.setMargins((int) (6 * dp), 0, 0, 0);
        bar.addView(btnSettings, setParams);

        return bar;
    }

    private View createHudBar(Context ctx, float dp, boolean dark) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
        hud.setBackgroundColor(dark ? 0xFF222630 : 0xFFE8EEF5);

        tvHudRound = createHudItem(ctx, "轮次", "0", dark, dp);
        tvHudLast = createHudItem(ctx, "最新", "--", dark, dp);
        tvHudBest = createHudItem(ctx, "最佳", "--", dark, dp);
        tvHudRank = createHudItem(ctx, "段位", "准备中", dark, dp);

        hud.addView(wrapHudItem(tvHudRound, "轮次", dark, dp));
        hud.addView(wrapHudItem(tvHudLast, "最新", dark, dp));
        hud.addView(wrapHudItem(tvHudBest, "最佳", dark, dp));
        hud.addView(wrapHudItem(tvHudRank, "段位", dark, dp));

        return hud;
    }

    private TextView createHudItem(Context ctx, String label, String initVal, boolean dark, float dp) {
        TextView tv = new TextView(ctx);
        tv.setText(initVal);
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(0xFF2E7D32);
        return tv;
    }

    private View wrapHudItem(TextView valView, String label, boolean dark, float dp) {
        LinearLayout box = new LinearLayout(valView.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        box.setLayoutParams(lp);

        TextView tvLabel = new TextView(valView.getContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setTextColor(dark ? 0xFF9E9E9E : 0xFF757575);

        box.addView(tvLabel);
        box.addView(valView);
        return box;
    }

    private View createBottomBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (14 * dp));

        Button btnRestart = new Button(ctx);
        btnRestart.setText("🔄 重新测试");
        btnRestart.setTextSize(14f);
        btnRestart.setTextColor(Color.WHITE);
        applyCapsuleStyle(btnRestart, 0xFF1976D2, dp);
        btnRestart.setOnClickListener(v -> resetRound());
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rLp.setMargins(0, 0, (int) (8 * dp), 0);
        bar.addView(btnRestart, rLp);

        Button btnResetStats = new Button(ctx);
        btnResetStats.setText("📊 重置统计");
        btnResetStats.setTextSize(14f);
        btnResetStats.setTextColor(dark ? 0xFFEEEEEE : 0xFF333333);
        applyCapsuleStyle(btnResetStats, dark ? 0xFF333842 : 0xFFCFD8DC, dp);
        btnResetStats.setOnClickListener(v -> {
            game.resetStats();
            updateHud();
            updateTargetCard(0);
            Toast.makeText(ctx, "统计数据已重置", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        sLp.setMargins((int) (8 * dp), 0, 0, 0);
        bar.addView(btnResetStats, sLp);

        return bar;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        currentDifficulty = prefs.getInt(KEY_DIFFICULTY, ReactionGame.DIFFICULTY_STANDARD);
        game.setDifficulty(currentDifficulty);
        tvNavDifficulty.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");

        loadSavedProgress();
        updateHud();
        updateTargetCard(0);
    }

    private void onTargetClick() {
        if (paused) return;

        if (state == 0 || state == 3 || state == 4) {
            // 开始新一轮
            startRound();
            return;
        }

        if (state == 1) {
            // 等待阶段点击：偷跑抢按
            handler.removeCallbacksAndMessages(null);
            game.tooEarly();
            updateTargetCard(3);
            updateHud();
            return;
        }

        if (state == 2) {
            // 变绿时点击：成功命中！
            long reactionMs = game.recordHit();
            totalScore += game.computeScore(reactionMs);

            usageStore.recordWin(GAME_ID);
            recordHighScore(game.getHighScore());

            updateTargetCard(4);
            updateHud();
        }
    }

    private void startRound() {
        if (paused) return;
        game.startRound();
        updateTargetCard(1);

        int delay = game.computeDelayMs();
        handler.postDelayed(() -> {
            if (paused) return;
            if (game.isWaitingForGreen()) {
                game.markReady();
                updateTargetCard(2);
            }
        }, delay);
    }

    private void resetRound() {
        handler.removeCallbacksAndMessages(null);
        game.pause();
        updateTargetCard(0);
        updateHud();
    }

    private void updateTargetCard(int targetState) {
        this.state = targetState;
        GradientDrawable bg = (GradientDrawable) targetCard.getBackground();
        float dp = getResources().getDisplayMetrics().density;

        switch (targetState) {
            case 0: // IDLE
                bg.setColor(COLOR_IDLE);
                tvTargetTitle.setText("🎯 轻触靶心开始");
                tvTargetSubtitle.setText("屏幕变绿时以最快速度点击！");
                break;
            case 1: // WAITING
                bg.setColor(COLOR_WAITING);
                tvTargetTitle.setText("⏳ 保持专注...");
                tvTargetSubtitle.setText("等待方块变绿，千万别偷跑！");
                break;
            case 2: // READY
                bg.setColor(COLOR_READY);
                tvTargetTitle.setText("🔥 立即点击！！🔥");
                tvTargetSubtitle.setText("点击！点击！点击！");
                break;
            case 3: // TOO EARLY
                bg.setColor(COLOR_TOO_EARLY);
                tvTargetTitle.setText("❌ 太心急了！");
                tvTargetSubtitle.setText("还没变绿就偷跑啦，轻触靶心重试");
                break;
            case 4: // RESULT
                bg.setColor(COLOR_RESULT);
                long last = game.getLastReactionTimeMs();
                tvTargetTitle.setText(last + " ms");
                String rank = game.getRankTitle(last);
                tvTargetSubtitle.setText(rank + "\n轻触靶心继续下一轮 ❯");
                break;
        }
    }

    private void updateHud() {
        if (tvHudRound == null) return;
        tvHudRound.setText(String.valueOf(game.getTotalRounds()));

        long last = game.getLastReactionTimeMs();
        tvHudLast.setText(last > 0 ? last + "ms" : "--");

        long best = game.getBestReactionTimeMs();
        tvHudBest.setText(best < Long.MAX_VALUE ? best + "ms" : "--");

        if (last > 0) {
            String rank = game.getRankTitle(last);
            int idx = rank.indexOf('(');
            tvHudRank.setText(idx > 0 ? rank.substring(0, idx).trim() : rank);
        } else {
            tvHudRank.setText("就绪");
        }
    }

    private void showDifficultyDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("选择反应力测试难度")
                .setSingleChoiceItems(new String[]{
                        "休闲挑战 (基础等待 2.5~4.0s，适合热身)",
                        "标准竞技 (基础等待 1.8~3.0s，标准手速)",
                        "极限闪电 (基础等待 0.8~1.8s，巅峰电竞)"
                }, currentDifficulty, (dialog, which) -> {
                    currentDifficulty = which;
                    game.setDifficulty(which);
                    prefs.edit().putInt(KEY_DIFFICULTY, which).apply();
                    tvNavDifficulty.setText(DIFFICULTY_NAMES[which] + " ▾");
                    resetRound();
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "已切换至: " + DIFFICULTY_NAMES[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRuleDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("⚡ 反应力对决规则")
                .setMessage("1. 规则玩法：\n"
                        + "轻触中央靶心进入等待状态。请聚精会神，当靶心瞬间由橙变绿时，以最快速度轻触屏幕！\n\n"
                        + "2. 防抢跑机制：\n"
                        + "若在尚未变绿之前提前点击，将判定为违规偷跑并作废该轮。\n\n"
                        + "3. 段位评估标准：\n"
                        + "⚡ < 200ms: 神仙反应 (闪电级)\n"
                        + "🔥 < 280ms: 电竞职业 (宗师级)\n"
                        + "👍 < 380ms: 敏捷先锋 (精锐级)\n"
                        + "😊 < 500ms: 正常水平 (标准级)\n"
                        + "🐢 ≥ 500ms: 略有迟疑 (摸鱼级)")
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("设置与选项")
                .setItems(new String[]{"切换难度档位", "重置历史最佳纪录", "查看段位说明"}, (dialog, which) -> {
                    if (which == 0) {
                        showDifficultyDialog();
                    } else if (which == 1) {
                        clearHighScore();
                    } else if (which == 2) {
                        showRuleDialog();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出确认")
                .setMessage("测试成绩已自动保存，确定要返回大厅吗？")
                .setPositiveButton("确定退出", (dialog, which) -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                })
                .setNegativeButton("继续挑战", null)
                .show();
    }

    private void clearHighScore() {
        try {
            saveManager.saveProgress(GAME_ID, new JSONObject().put("highScore", 0).toString());
            game.resetStats();
            updateHud();
            updateTargetCard(0);
            Toast.makeText(requireContext(), "最高纪录已重置", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void loadSavedProgress() {
        try {
            String json = saveManager.loadProgress(GAME_ID);
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                int savedHigh = obj.optInt("highScore", 0);
                if (savedHigh > 0) {
                    long approxBestMs = Math.max(1, 1000 - savedHigh);
                    // 反推最佳成绩显示
                    tvHudBest.setText(approxBestMs + "ms");
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void recordHighScore(int score) {
        try {
            String json = saveManager.loadProgress(GAME_ID);
            int best = 0;
            if (json != null) {
                best = new JSONObject(json).optInt("highScore", 0);
            }
            if (score > best) {
                JSONObject obj = new JSONObject();
                obj.put("highScore", score);
                saveManager.saveProgress(GAME_ID, obj.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private void applyCapsuleStyle(View view, int bgColor, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(18 * dp);
        gd.setColor(bgColor);
        view.setBackground(gd);
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacksAndMessages(null);
        game.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        paused = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
