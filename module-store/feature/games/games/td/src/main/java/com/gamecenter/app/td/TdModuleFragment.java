package com.gamecenter.app.td;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevels;
import com.gamecenter.app.td.engine.TowerType;

import java.util.List;
import java.util.Locale;

/**
 * 塔防「保卫蛋蛋」主 Fragment — 成品版。
 *
 * 结构：HUD（金币/波次/生命 + 下一波预告）→ 棋盘 → 消息条 → 塔栏 → 控制栏。
 * 覆盖层：选关面板（星级/解锁/难度选择）、结算面板（胜负统计、下一关/重玩）。
 *
 * 生命周期纪律：对局主循环挂 mainHandler，onDestroyView/onHiddenChanged 必须取消；
 * 程序化 Button 必须 setStateListAnimator(null)（避免宿主主题资源 ID 冲突）。
 */
public class TdModuleFragment extends Fragment {

    private static final long TICK_INTERVAL_MS = 16; // ≈60Hz
    /** 空闲态（暂停/准备/结算/覆盖层打开）的降频刷新间隔，避免 60Hz 软渲染空转耗电 */
    private static final long IDLE_TICK_INTERVAL_MS = 200;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private TdGame game;
    private TdView tdView;
    private TextView tvCoin, tvWave, tvHp, tvNext, tvMsg, tvLevelLabel;
    private Button btnPrevLevel, btnNextLevel, btnSpeed, btnNextWave, btnQuitToMenu, btnSelectTower;
    private Button btnUpgrade, btnSell, btnTarget, btnDeselect;
    private final Runnable tickLoop = this::tickOnce;
    private TowerType selectedType = null;
    /** 合成源塔；非空时下一次点选塔会作为合成目标。 */
    private TdGame.Tower mergeSource;
    private int selectedLevelIdx = 0;
    private int gameSession = 0;
    private boolean paused = false;
    private boolean gameEnded = false;

    private FrameLayout overlayRoot;
    private TdSaveManager save;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        save = new TdSaveManager(requireContext().getApplicationContext());
        return buildUi();
    }

    // ===== UI 构建 =====

    private View buildUi() {
        Context ctx = requireContext();
        root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF1E5C33);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(buildHud(ctx));
        col.addView(buildBoardArea(ctx), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        col.addView(buildMessageBar(ctx));
        col.addView(buildTowerBar(ctx));
        col.addView(buildControlBar(ctx));
        col.addView(buildTowerOps(ctx));

        overlayRoot = new FrameLayout(ctx);
        root.addView(overlayRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 系统栏 inset 适配：内容列顶部避开状态栏、底部避开导航条/手势区。
        // 只信任系统实时 WindowInsets（动态模块 Activity 若为非 edge-to-edge，系统已自动避让）。
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            col.setPadding(0, Math.max(top, 0), 0, Math.max(bottom, 0));
            return insets;
        });

        // 首次进入：直接显示选关面板（避免空棋盘）
        showLevelSelect();
        return root;
    }

    private View buildHud(Context ctx) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(dp(6), dp(4), dp(6), dp(4));
        hud.setBackgroundColor(0xFF2A2318);

        tvLevelLabel = hudText(ctx, 12, 0xFFFFD54F, true);
        tvCoin = hudText(ctx, 14, 0xFFFFF176, true);
        tvWave = hudText(ctx, 13, 0xFFFFFFFF, false);
        tvHp = hudText(ctx, 14, 0xFFEF9A9A, true);
        tvNext = hudText(ctx, 11, 0xFFB0BEC5, false);

        LinearLayout firstRow = new LinearLayout(ctx);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout secondRow = new LinearLayout(ctx);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] tvs = {tvLevelLabel, tvCoin, tvHp, tvWave, tvNext};
        float[] weights = {1.15f, 1f, 1f, 1.15f, 1.7f};
        for (int i = 0; i < tvs.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x664A4238);
            bg.setCornerRadius(dp(8));
            tvs[i].setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(24), weights[i]);
            lp.setMargins(dp(2), 0, dp(2), 0);
            (i < 3 ? firstRow : secondRow).addView(tvs[i], lp);
        }
        hud.addView(firstRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        secondLp.topMargin = dp(2);
        hud.addView(secondRow, secondLp);
        return hud;
    }

    private View buildBoardArea(Context ctx) {
        tdView = new TdView(ctx);
        // 动态模块的 View 使用的是宿主 Context；精灵资源必须从模块 Resources 显式取得。
        com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleResources =
                ModuleManager.INSTANCE.getModuleResources("td");
        if (moduleResources != null) {
            tdView.loadSpriteSheets(
                    moduleResources.getResources(),
                    moduleResources.getResId("td_towers", "drawable"),
                    moduleResources.getResId("td_monsters", "drawable"));
        }
        tdView.setListener(new TdView.OnTowerActionListener() {
            @Override public void onTowerPlaced(int row, int col, TowerType type) {
                if (game == null) return;
                if (game.placeTower(type, row, col) != null) {
                    showMsg(game.getLastActionMessage(), game.getLastActionTone());
                    updateHud();
                } else {
                    showMsg(game.getLastActionMessage(), "err");
                }
            }
            @Override public void onTowerSelected(int row, int col) {
                if (game == null) return;
                TdGame.Tower tapped = game.getTowerAt(row, col);
                if (mergeSource != null) {
                    game.mergeTowers(mergeSource.row, mergeSource.col, row, col);
                    showMsg(game.getLastActionMessage(), game.getLastActionTone());
                    mergeSource = null;
                    hideTowerOps();
                } else {
                    showTowerOps(tapped);
                }
                updateHud();
            }
            @Override public void onTowerDeselected() {
                if (mergeSource != null) {
                    mergeSource = null;
                    showMsg("已取消合成", "info");
                }
                hideTowerOps();
            }
        });
        return tdView;
    }

    private View buildMessageBar(Context ctx) {
        tvMsg = new TextView(ctx);
        tvMsg.setTextSize(13);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvMsg.setBackgroundColor(0xFF3A3124);
        tvMsg.setTextColor(0xFFFFF8E1);
        return tvMsg;
    }

    private View buildTowerBar(Context ctx) {
        HorizontalScrollView scroller = new HorizontalScrollView(ctx);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackgroundColor(0xFF2A2318);
        for (final TowerType t : TowerType.values()) {
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setTag(t);

            // 图标色块
            View dot = new View(ctx);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(towerUiColor(t));
            dot.setBackground(dotBg);

            TextView name = new TextView(ctx);
            name.setText(t.displayName);
            name.setTextSize(10);
            name.setTextColor(0xFFFFF8E1);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);

            TextView price = new TextView(ctx);
            price.setText(String.format(Locale.US, "₿%d", t.baseCost));
            price.setTextSize(10);
            price.setTextColor(0xFFFFD54F);
            price.setGravity(Gravity.CENTER);
            price.setSingleLine(true);

            TextView role = new TextView(ctx);
            role.setText(towerRole(t));
            role.setTextSize(8);
            role.setTextColor(0xFFB8E9D0);
            role.setGravity(Gravity.CENTER);
            role.setSingleLine(true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF4A4238);
            bg.setStroke(dp(1), 0xFF8D8169);
            bg.setCornerRadius(dp(10));
            item.setBackground(bg);

            item.setOnClickListener(v -> {
                selectedType = t;
                tdView.setSelectedType(t);
                tdView.clearSelection();
                hideTowerOps();
                updateTowerBarSelection();
                showMsg("选择 " + t.displayName + "，点击棋盘绿色空格建造", "info");
            });

            item.addView(dot, new LinearLayout.LayoutParams(dp(18), dp(18)));
            LinearLayout.LayoutParams dotLp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            dotLp.setMargins(0, dp(2), 0, dp(1));
            item.addView(name, new LinearLayout.LayoutParams(dp(58), dp(15)));
            item.addView(role, new LinearLayout.LayoutParams(dp(58), dp(12)));
            item.addView(price, new LinearLayout.LayoutParams(dp(58), dp(14)));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(66), dp(66));
            cardLp.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(item, cardLp);
            towerItems.add(item);
        }
        scroller.addView(bar);
        return scroller;
    }

    private final java.util.List<View> towerItems = new java.util.ArrayList<>();

    /** 塔栏选中高亮：金边 + 亮底 */
    private void updateTowerBarSelection() {
        for (View item : towerItems) {
            TowerType t = (TowerType) item.getTag();
            boolean sel = t == selectedType;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? 0xFF5A5238 : 0xFF4A4238);
            bg.setStroke(dp(sel ? 2 : 1), sel ? 0xFFFFC107 : 0xFF8D8169);
            bg.setCornerRadius(dp(10));
            item.setBackground(bg);
        }
    }

    private static int towerUiColor(TowerType t) {
        switch (t) {
            case BOTTLE: return 0xFF63B3ED;
            case SUN: return 0xFFFFD54F;
            case SNOW: return 0xFFB3E5FC;
            case FAN: return 0xFFB0BEC5;
            case POISON: return 0xFFCE93D8;
            case ROCKET: return 0xFFFF8A80;
            default: return 0xFF9E9E9E;
        }
    }

    private static String towerRole(TowerType t) {
        switch (t) {
            case BOTTLE: return "单体 · 对空";
            case SUN: return "经济增益";
            case SNOW: return "减速控制";
            case FAN: return "范围清群";
            case POISON: return "持续伤害";
            case ROCKET: return "重炮爆发";
            default: return "防御塔";
        }
    }

    private View buildControlBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(4), dp(6), dp(4));
        bar.setBackgroundColor(0xFF2A2318);

        btnPrevLevel = ctrlButton(ctx, "◀");
        btnNextLevel = ctrlButton(ctx, "▶");
        btnSpeed = ctrlButton(ctx, "⏸");
        btnNextWave = ctrlButton(ctx, "▶ 开战");
        btnQuitToMenu = ctrlButton(ctx, "☰ 关卡");
        btnSelectTower = ctrlButton(ctx, "塔");

        // 开战按钮主色化
        GradientDrawable wb = new GradientDrawable();
        wb.setColor(0xFF2E9E4F);
        wb.setCornerRadius(dp(8));
        btnNextWave.setBackground(wb);
        btnNextWave.setTextColor(0xFFFFFFFF);

        btnPrevLevel.setOnClickListener(v -> cycleLevel(-1));
        btnNextLevel.setOnClickListener(v -> cycleLevel(1));
        btnSpeed.setOnClickListener(v -> {
            if (game == null || game.isEnded() || game.getState() == TdGame.State.PREPARING) return;
            paused = !paused;
            btnSpeed.setText(paused ? "▶" : "⏸");
            btnSpeed.setTextColor(paused ? 0xFFFF8A80 : 0xFFFFF8E1);
        });
        btnNextWave.setOnClickListener(v -> {
            if (game == null) return;
            int waveBefore = game.getWaveIndex();
            boolean ok = game.startNextWaveEarly();
            if (game.getState() == TdGame.State.RUNNING && game.getWaveIndex() != waveBefore) {
                tdView.showWaveBanner(game.getWaveIndex(), game.getTotalWaves());
            }
            showMsg(game.getLastActionMessage(), game.getLastActionTone());
            updateHud();
        });
        btnQuitToMenu.setOnClickListener(v -> showLevelSelect());
        btnSelectTower.setOnClickListener(v -> {
            selectedType = selectedType == null ? TowerType.BOTTLE : null;
            tdView.setSelectedType(selectedType);
            updateTowerBarSelection();
            showMsg(selectedType == null ? "已取消选择" : "请选择塔并点击棋盘", "info");
        });

        bar.addView(btnQuitToMenu, new LinearLayout.LayoutParams(dp(56), dp(36)));
        bar.addView(btnPrevLevel, new LinearLayout.LayoutParams(dp(42), dp(36)));
        bar.addView(btnNextLevel, new LinearLayout.LayoutParams(dp(42), dp(36)));
        bar.addView(btnSpeed, new LinearLayout.LayoutParams(dp(42), dp(36)));
        bar.addView(btnNextWave, new LinearLayout.LayoutParams(0, dp(36), 1f));
        bar.addView(btnSelectTower, new LinearLayout.LayoutParams(dp(44), dp(36)));
        return bar;
    }

    private View buildTowerOps(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        towerOpsBar = bar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(2), dp(6), dp(2));
        bar.setBackgroundColor(0xFF433A2C);

        btnUpgrade = ctrlButton(ctx, "升级");
        btnSell = ctrlButton(ctx, "卖出");
        btnTarget = ctrlButton(ctx, "目标");
        btnDeselect = ctrlButton(ctx, "取消");
        btnUpgrade.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            if (t.level >= 3) {
                showMsg("Lv3 已是最高等级", "info");
                return;
            }
            mergeSource = t;
            showMsg("已选择 " + t.type.displayName + " Lv" + t.level
                    + "，请点击另一座同级同类塔合成", "info");
            hideTowerOps();
            updateHud();
        });
        btnSell.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            game.sellTower(t.row, t.col);
            showMsg(game.getLastActionMessage(), "info");
            hideTowerOps();
            updateHud();
        });
        btnTarget.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            game.cycleTowerTargetMode(t.row, t.col);
            showMsg(game.getLastActionMessage(), game.getLastActionTone());
            showTowerOps(t);
        });
        btnDeselect.setOnClickListener(v -> hideTowerOps());

        bar.addView(btnUpgrade, new LinearLayout.LayoutParams(0, dp(34), 1f));
        bar.addView(btnTarget, new LinearLayout.LayoutParams(0, dp(34), 1f));
        bar.addView(btnSell, new LinearLayout.LayoutParams(0, dp(34), 1f));
        bar.addView(btnDeselect, new LinearLayout.LayoutParams(0, dp(34), 1f));
        bar.setVisibility(View.GONE);
        return bar;
    }

    private LinearLayout towerOpsBar;

    private void showTowerOps(TdGame.Tower t) {
        towerOpsBar.setVisibility(View.VISIBLE);
        btnUpgrade.setText(t.level >= 3 ? "已满级 Lv3" : "合成 Lv" + (t.level + 1));
        btnUpgrade.setEnabled(t.level < 3);
        btnTarget.setEnabled(t.type != TowerType.SUN);
        btnTarget.setText(t.type == TowerType.SUN ? "经济塔" : "目标·" + t.targetMode.displayName);
    }

    private void hideTowerOps() {
        if (towerOpsBar != null) towerOpsBar.setVisibility(View.GONE);
        tdView.clearSelection();
    }

    // ===== 选关 / 难度 =====

    private void cycleLevel(int delta) {
        int n = save.getUnlockedLevelCount();
        int next = (selectedLevelIdx + delta + TdLevels.levelIds().size())
                % TdLevels.levelIds().size();
        if (next + 1 > n) {
            showMsg("先通过前面的关卡解锁", "err");
            return;
        }
        restartLevel(next);
    }

    /** 主入口：进入选关面板 */
    public void showLevelSelect() {
        if (game != null && !game.isEnded()) {
            gameSession++;
        }
        clearOverlay();
        int unlocked = save.getUnlockedLevelCount();
        List<String> ids = TdLevels.levelIds();

        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView title = new TextView(requireContext());
        title.setText("选择关卡");
        title.setTextSize(22);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView sub = new TextView(requireContext());
        sub.setText("保护蛋蛋，击退怪物");
        sub.setTextSize(13);
        sub.setTextColor(0xFFCCFFE0);
        sub.setGravity(Gravity.CENTER);
        panel.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            String id = ids.get(i);
            boolean locked = idx >= unlocked;
            int stars = save.getBestStars(idx);

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(locked ? 0xFF3A3A3A : 0xFF4A4238);
            bg.setStroke(dp(1), locked ? 0xFF555555 : 0xFFFFD54F);
            bg.setCornerRadius(dp(12));
            card.setBackground(bg);

            TextView name = new TextView(requireContext());
            name.setText((idx + 1) + ". " + TdLevels.levelDisplayName(idx, id));
            name.setTextSize(16);
            name.setTextColor(locked ? 0xFF777777 : 0xFFFFFFFF);
            name.setTypeface(Typeface.DEFAULT_BOLD);

            TextView meta = new TextView(requireContext());
            meta.setText(locked ? "🔒 未解锁" : TdLevels.levelSub(idx, id) + "  ·  " + starsText(stars));
            meta.setTextSize(11);
            meta.setTextColor(0xFFB0BEC5);

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.addView(name);
            inner.addView(meta);

            card.addView(inner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!locked) {
                Button play = new Button(requireContext());
                play.setText("▶ 玩");
                play.setTextSize(12);
                play.setAllCaps(false);
                play.setStateListAnimator(null);
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(0xFF2E9E4F);
                pb.setCornerRadius(dp(8));
                ((Button) play).setBackground(pb);
                play.setTextColor(0xFFFFFFFF);
                play.setOnClickListener(v -> showDifficultySelect(idx));
                card.addView(play, new LinearLayout.LayoutParams(dp(56), dp(36)));
            }
            panel.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = dp(10);
        }

        TextView stats = new TextView(requireContext());
        stats.setText("已通关 " + countCleared(unlocked) + " · 总击杀 " + save.getTotalKills()
                + " · 游玩 " + save.getPlayCount() + " 次");
        stats.setTextSize(12);
        stats.setTextColor(0xFFB0BEC5);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(dp(8), dp(14), dp(8), dp(4));
        panel.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        Button back = ctrlButton(requireContext(), "返回大厅");
        back.setOnClickListener(v -> exitToHall());
        panel.addView(back, new LinearLayout.LayoutParams(dp(120), dp(40)));
        ((LinearLayout.LayoutParams) back.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF01E2A1F);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showDifficultySelect(final int levelIdx) {
        clearOverlay();
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20), dp(40), dp(20), dp(20));
        panel.setBackgroundColor(0xF01E2A1F);

        TextView title = new TextView(requireContext());
        title.setText("难度 · " + TdLevels.levelDisplayName(levelIdx, TdLevels.levelIds().get(levelIdx)));
        title.setTextSize(20);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        for (final TdGame.Difficulty d : TdGame.Difficulty.values()) {
            Button b = ctrlButton(requireContext(), d.displayName);
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(0xFF4A4238);
            gb.setStroke(dp(1), 0xFFFFD54F);
            gb.setCornerRadius(dp(10));
            b.setBackground(gb);
            b.setTextSize(15);
            b.setOnClickListener(v -> {
                save.recordPlay();
                startLevel(levelIdx, d);
                clearOverlay();
            });
            panel.addView(b, new LinearLayout.LayoutParams(dp(180), dp(46)));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).topMargin = dp(12);
            ((LinearLayout.LayoutParams) b.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        }

        TextView hint = new TextView(requireContext());
        hint.setText("简单：金币×1.3 怪弱 · 普通：标准 · 困难：金币×0.8 怪强");
        hint.setTextSize(11);
        hint.setTextColor(0xFFB0BEC5);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        ((LinearLayout.LayoutParams) hint.getLayoutParams()).topMargin = dp(10);

        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private int countCleared(int unlocked) {
        int c = 0;
        for (int i = 0; i < TdLevels.levelIds().size(); i++) {
            if (save.getBestStars(i) > 0) c++;
        }
        return c;
    }

    private void clearOverlay() {
        overlayRoot.removeAllViews();
    }

    /** 真正返回大厅：把「系统返回键」语义交还宿主容器决定后续动作。 */
    private void exitToHall() {
        // - V2 独立 Activity 容器：命中宿主统一退出确认框，确认后 finish 回到大厅；
        // - P4 addToBackStack 路径：由 FragmentActivity 内建回退栈弹出回到大厅视图。
        // 不在此移除 tick 循环：宿主确认取消时对局需继续，onPause/onDestroyView 钩子负责兜底清理。
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // ===== 对局管理 =====

    private void startLevel(int idx, TdGame.Difficulty diff) {
        gameSession++;
        paused = false;
        gameEnded = false;
        selectedLevelIdx = idx;
        btnSpeed.setText("⏸");
        btnNextWave.setText("▶ 开战");
        game = TdLevels.buildLevel(TdLevels.levelIds().get(idx));
        game.applyDifficulty(diff);
        tdView.bind(game);
        tdView.setSelectedType(null);
        selectedType = null;
        updateTowerBarSelection();
        hideTowerOps();
        tvLevelLabel.setText("关" + (idx + 1) + "/" + TdLevels.levelIds().size());
        updateHud();
        showMsg("准备！建好防御塔后点击「▶ 开战」", "info");
        mainHandler.removeCallbacks(tickLoop);
        mainHandler.post(tickLoop);
    }

    private void restartLevel(int idx) {
        mainHandler.removeCallbacks(tickLoop);
        startLevel(idx, game != null ? game.getDifficulty() : TdGame.Difficulty.NORMAL);
    }

    private void tickOnce() {
        if (game == null) return;
        int session = gameSession;
        if (isDetached()) return;
        // 选关/难度/结算等覆盖层打开时视为菜单暂停：怪物不得在菜单里继续前进甚至判负
        boolean overlayOpen = overlayRoot != null && overlayRoot.getChildCount() > 0;
        boolean combatLive = !paused && !overlayOpen
                && game.getState() == TdGame.State.RUNNING;
        if (!game.isEnded() && combatLive) {
            game.tick();
            tdView.drainKillEvents(game.drainKillEvents());
            if (game.isEnded()) onGameEnded();
        }
        if (gameSession == session && !isDetached()) {
            // 战斗活跃期保持 60Hz；其余空闲态降到 5Hz，只维持 HUD/画布的低频一致性
            long delay = combatLive ? TICK_INTERVAL_MS : IDLE_TICK_INTERVAL_MS;
            updateHud();
            tdView.invalidate();
            mainHandler.removeCallbacks(tickLoop);
            mainHandler.postDelayed(tickLoop, delay);
        }
    }

    private void onGameEnded() {
        if (gameEnded) return;
        gameEnded = true;
        if (game.getState() == TdGame.State.WON) {
            int stars = game.starsEarned();
            save.recordWin(selectedLevelIdx);
            save.setBestStars(selectedLevelIdx, stars);
            save.addKills(game.getMonstersKilled());
            save.setBestTimeSec(selectedLevelIdx, (int) game.getElapsedSeconds());
            if (game.getDifficulty() == TdGame.Difficulty.EASY) save.setEasyCleared(true);
            if (game.getDifficulty() == TdGame.Difficulty.HARD) save.setHardCleared(true);
            showResult("🎉 通关成功", "星级 ★★★★★".replace("★★★★★", starsText(stars)),
                    "击杀 " + game.getMonstersKilled() + " · 用时 "
                            + (int) game.getElapsedSeconds() + "s", 0xFF66BB6A);
        } else {
            showResult("💔 蛋蛋被吃掉了", "",
                    "坚持到第 " + game.getWaveIndex() + " 波  ·  击杀 " + game.getMonstersKilled(),
                    0xFFE57373);
        }
    }

    private void showResult(String title, String line2, String line3, int color) {
        clearOverlay();
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xE61E2A1F);
        panel.setPadding(dp(20), dp(30), dp(20), dp(20));

        TextView t1 = new TextView(requireContext());
        t1.setText(title);
        t1.setTextSize(22);
        t1.setTextColor(color);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setGravity(Gravity.CENTER);
        panel.addView(t1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        if (!line2.isEmpty()) {
            TextView t2 = new TextView(requireContext());
            t2.setText(line2);
            t2.setTextSize(20);
            t2.setTextColor(0xFFFFC107);
            t2.setGravity(Gravity.CENTER);
            panel.addView(t2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        }

        TextView t3 = new TextView(requireContext());
        t3.setText(line3);
        t3.setTextSize(13);
        t3.setTextColor(0xFFB0BEC5);
        t3.setGravity(Gravity.CENTER);
        panel.addView(t3, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        boolean won = game.getState() == TdGame.State.WON;
        if (won && selectedLevelIdx + 1 < TdLevels.levelIds().size()) {
            Button next = ctrlButton(requireContext(), "下一关 ▶");
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(0xFF2E9E4F);
            gb.setCornerRadius(dp(10));
            next.setBackground(gb);
            next.setTextColor(0xFFFFFFFF);
            final int nIdx = selectedLevelIdx + 1;
            next.setOnClickListener(v -> {
                clearOverlay();
                startLevel(nIdx, game.getDifficulty());
            });
            panel.addView(next, new LinearLayout.LayoutParams(dp(160), dp(44)));
            ((LinearLayout.LayoutParams) next.getLayoutParams()).topMargin = dp(10);
            ((LinearLayout.LayoutParams) next.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        }

        Button retry = ctrlButton(requireContext(), "↺ 重玩本关");
        retry.setOnClickListener(v -> {
            clearOverlay();
            restartLevel(selectedLevelIdx);
        });
        panel.addView(retry, new LinearLayout.LayoutParams(dp(160), dp(44)));
        ((LinearLayout.LayoutParams) retry.getLayoutParams()).topMargin = dp(10);
        ((LinearLayout.LayoutParams) retry.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        Button menu = ctrlButton(requireContext(), "☰ 选关");
        menu.setOnClickListener(v -> showLevelSelect());
        panel.addView(menu, new LinearLayout.LayoutParams(dp(160), dp(44)));
        ((LinearLayout.LayoutParams) menu.getLayoutParams()).topMargin = dp(10);
        ((LinearLayout.LayoutParams) menu.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ===== HUD =====

    private void updateHud() {
        if (game == null) return;
        tvCoin.setText("₿ " + game.getCoin());
        tvWave.setText("波 " + game.getWaveIndex() + "/" + game.getTotalWaves()
                + (game.getState() == TdGame.State.PREPARING ? " 准备" : ""));
        tvHp.setText("🥚 " + game.getMascotHp() + "/" + game.getMaxMascotHp());
        // 下一波预告
        String next = nextWavePreview();
        tvNext.setText(next.isEmpty() ? "守住最后防线" : "预告 " + next);
        tvLevelLabel.setText("关" + (selectedLevelIdx + 1) + "·" + game.getDifficulty().displayName);
        if (!game.isEnded()) {
            if (game.getState() == TdGame.State.PREPARING) {
                btnNextWave.setText("▶ 开战");
            } else if (game.isWaveSpawning()) {
                btnNextWave.setText("⚡ 加速召唤");
            } else {
                btnNextWave.setText("▶ 下一波");
            }
        }
    }

    private String nextWavePreview() {
        if (game == null) return "";
        int nextCount = game.nextWaveCount();
        if (nextCount <= 0) return "";
        int route = game.nextWaveRouteIndex();
        return "路线" + (route + 1) + "·" + game.nextWaveTypeName() + "×" + nextCount;
    }

    private void showMsg(String msg, String tone) {
        if (tvMsg == null || msg == null || msg.isEmpty()) return;
        boolean error = "err".equals(tone);
        boolean success = "ok".equals(tone);
        tvMsg.setText((error ? "⚠ " : success ? "✓ " : "✦ ") + msg);
        tvMsg.setTextColor(error ? 0xFFFF8A80 : success ? 0xFF9CFFB0 : 0xFFFFF8E1);
    }

    private TextView hudText(Context ctx, float sp, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button ctrlButton(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF4A4238);
        d.setStroke(dp(1), 0xFF8D8169);
        d.setCornerRadius(dp(8));
        b.setBackground(d);
        b.setTextColor(0xFFFFF8E1);
        return b;
    }

    private static String starsText(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(0, n); i++) sb.append('★');
        return n > 0 ? sb.toString() : "☆ ☆ ☆";
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            mainHandler.removeCallbacks(tickLoop);
        } else if (game != null && !game.isEnded() && isAdded()) {
            mainHandler.post(tickLoop);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(tickLoop);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (game != null && !game.isEnded() && isAdded()) {
            mainHandler.post(tickLoop);
        }
    }

    @Override
    public void onDestroyView() {
        gameSession++;
        mainHandler.removeCallbacks(tickLoop);
        tdView = null;
        super.onDestroyView();
    }

    /** 首次进入时显示选关面板（由外部/首次调用触发） */
    public void openMenuOnStart() {
        if (save != null) {
            showLevelSelect();
        }
    }
}
