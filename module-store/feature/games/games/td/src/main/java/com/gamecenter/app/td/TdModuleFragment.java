package com.gamecenter.app.td;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.MotionEvent;
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

import com.gamecenter.app.R;
import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.td.engine.MonsterType;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevels;
import com.gamecenter.app.td.engine.TdTowerProgression;
import com.gamecenter.app.td.engine.TowerType;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

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

    /** PVZ 式开局塔组：每局只带 5 张牌，避免小屏横向挤压。 */
    private static final int DECK_SIZE = 5;
    private final List<TowerType> activeDeck = new ArrayList<>(Arrays.asList(
            TowerType.BOTTLE, TowerType.SUN, TowerType.SNOW));
    private final Map<TowerType, View> towerItemByType = new HashMap<>();
    /** Android DragEvent 在部分系统版本的 STARTED 阶段不提供 ClipData，保留本地拖拽类型。 */
    private TowerType paletteDragType;

    private FrameLayout overlayRoot;
    private TdSaveManager save;
    /** 模块 APK 的真实资源；宿主 Context 不能替代它读取本模块 assets。 */
    private com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleResources;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        save = new TdSaveManager(requireContext().getApplicationContext());
        try {
            moduleResources = ModuleManager.INSTANCE.getModuleResources("td");
            if (moduleResources == null) {
                throw new IllegalStateException("TD module resources are unavailable");
            }
            // 关卡数据是模块资产真源。加载器会先完成 schema、路径、波次和枚举校验，
            // 绝不回退到陈旧 Java 关卡，避免内容版本与 UI/存档悄悄错配。
            TdLevels.initialize(moduleResources.getAssetManager());
        } catch (RuntimeException contentFailure) {
            return buildCampaignUnavailableView();
        }
        return buildUi();
    }

    private View buildCampaignUnavailableView() {
        TextView message = new TextView(requireContext());
        message.setText(getString(R.string.game_td_msg_campaign_unavailable));
        message.setTextColor(0xFFFFFFFF);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(24), dp(24), dp(24), dp(24));
        message.setBackgroundColor(0xFF1E2A1F);
        return message;
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
        if (moduleResources != null) {
            tdView.loadSpriteSheets(
                    moduleResources.getResources(),
                    moduleResources.getResId("td_towers", "drawable"),
                    moduleResources.getResId("td_monsters", "drawable"),
                    moduleResources.getResId("td_towers_expansion_v1", "drawable"),
                    moduleResources.getResId("td_monsters_expansion_v1", "drawable"));
        }
        tdView.setListener(new TdView.OnTowerActionListener() {
            @Override public void onTowerPlaced(int row, int col, TowerType type) {
                if (game == null) return;
                if (game.placeTower(type, row, col) != null) {
                    showEngineMsg();
                    updateHud();
                } else {
                    showEngineMsg();
                }
            }
            @Override public void onTowerSelected(int row, int col) {
                if (game == null) return;
                TdGame.Tower tapped = game.getTowerAt(row, col);
                if (mergeSource != null) {
                    game.mergeTowers(mergeSource.row, mergeSource.col, row, col);
                    showEngineMsg();
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
                    showMsg(getString(R.string.game_td_msg_merge_cancelled), "info");
                }
                hideTowerOps();
            }

            @Override public void onTowerDragged(int sourceRow, int sourceCol, int targetRow, int targetCol) {
                if (game == null) return;
                boolean ok = game.mergeTowers(sourceRow, sourceCol, targetRow, targetCol);
                showEngineMsg();
                if (ok) {
                    mergeSource = null;
                    hideTowerOps();
                }
                updateHud();
            }

            @Override public void onPaletteTowerDropped(int row, int col, TowerType type) {
                handlePaletteDrop(row, col, type);
            }
        });
        tdView.setOnDragListener((v, event) -> {
            TowerType dragType = parsePaletteDragType(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription() != null
                            && event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_LOCATION: {
                    if (dragType == null) dragType = paletteDragType;
                    if (dragType == null) return true;
                    int[] cell = tdView.cellAt(event.getX(), event.getY());
                    if (cell == null) {
                        tdView.setPaletteDragTarget(dragType, -1, -1, false);
                    } else {
                        tdView.setPaletteDragTarget(dragType, cell[0], cell[1],
                                canDropPalette(dragType, cell[0], cell[1]));
                    }
                    return true;
                }
                case DragEvent.ACTION_DROP: {
                    if (dragType == null) dragType = paletteDragType;
                    if (dragType == null) return false;
                    int[] cell = tdView.cellAt(event.getX(), event.getY());
                    tdView.clearPaletteDragTarget();
                    if (cell == null) {
                        showMsg(getString(R.string.game_td_msg_drop_inside_board), "err");
                        return true;
                    }
                    if (canDropPalette(dragType, cell[0], cell[1])) {
                        handlePaletteDrop(cell[0], cell[1], dragType);
                    } else {
                        showMsg(getString(R.string.game_td_msg_invalid_drop_cell), "err");
                    }
                    return true;
                }
                case DragEvent.ACTION_DRAG_ENDED:
                    paletteDragType = null;
                    tdView.clearPaletteDragTarget();
                    return true;
                default:
                    return true;
            }
        });
        return tdView;
    }

    private TowerType parsePaletteDragType(DragEvent event) {
        if (event == null || event.getClipDescription() == null
                || !event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                || event.getClipData() == null || event.getClipData().getItemCount() == 0) return null;
        CharSequence text = event.getClipData().getItemAt(0).getText();
        if (text == null) return null;
        String value = text.toString();
        if (!value.startsWith("palette:")) return null;
        try {
            TowerType type = TowerType.valueOf(value.substring("palette:".length()));
            return activeDeck.contains(type) ? type : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean canDropPalette(TowerType type, int row, int col) {
        if (game == null || type == null || row < 0 || col < 0
                || row >= game.getRows() || col >= game.getCols()) return false;
        TdGame.Tower target = game.getTowerAt(row, col);
        if (target != null) {
            return target.type == type && target.level < 3 && game.getCoin() >= type.baseCost;
        }
        if (game.getCoin() < type.baseCost || game.isEggCell(row, col) || game.isPathCell(row, col)) {
            return false;
        }
        return type != TowerType.MINE || game.isMinePlacementCell(row, col);
    }

    private void handlePaletteDrop(int row, int col, TowerType type) {
        if (game == null) return;
        boolean ok = game.placeOrMergeTower(type, row, col);
        showEngineMsg();
        if (ok) {
            selectedType = null;
            tdView.setSelectedType(null);
            updateTowerBarSelection();
            hideTowerOps();
        }
        updateHud();
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

            // 卡牌图标使用和棋盘一致的程序化小模型，缺少精灵资源时仍然清晰可辨。
            View dot = new TowerGlyphView(ctx, t);

            TextView name = new TextView(ctx);
            name.setText(towerName(t));
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
                showMsg(getString(R.string.game_td_msg_select_tower_build, towerName(t)), "info");
            });
            final float[] dragDown = new float[2];
            final boolean[] dragStarted = {false};
            item.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragDown[0] = event.getRawX();
                        dragDown[1] = event.getRawY();
                        dragStarted[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragStarted[0] && Math.hypot(event.getRawX() - dragDown[0],
                                event.getRawY() - dragDown[1]) >= dp(8)) {
                            ClipData data = ClipData.newPlainText("td-tower", "palette:" + t.name());
                            View.DragShadowBuilder shadow = new View.DragShadowBuilder(item);
                            paletteDragType = t;
                            dragStarted[0] = item.startDragAndDrop(data, shadow, null, 0);
                            if (!dragStarted[0]) paletteDragType = null;
                            if (dragStarted[0]) showMsg(getString(R.string.game_td_msg_drag_tower, towerName(t)), "info");
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragStarted[0]) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dragStarted[0] = false;
                        return true;
                    default:
                        return true;
                }
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
            towerItemByType.put(t, item);
            item.setVisibility(activeDeck.contains(t) ? View.VISIBLE : View.GONE);
        }
        scroller.addView(bar);
        return scroller;
    }

    private final java.util.List<View> towerItems = new java.util.ArrayList<>();

    private void refreshTowerDeck() {
        for (TowerType t : TowerType.values()) {
            View item = towerItemByType.get(t);
            if (item != null) item.setVisibility(activeDeck.contains(t) ? View.VISIBLE : View.GONE);
        }
        if (selectedType != null && !activeDeck.contains(selectedType)) {
            selectedType = null;
            if (tdView != null) tdView.setSelectedType(null);
        }
        updateTowerBarSelection();
    }

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
            case LIGHTNING: return 0xFFB388FF;
            case SNIPER: return 0xFF80DEEA;
            case MINE: return 0xFF78909C;
            case AMPLIFIER: return 0xFF26C6DA;
            default: return 0xFF9E9E9E;
        }
    }

    /** 塔定位说明（塔栏卡片副标题），经宿主资源本地化。 */
    private String towerRole(TowerType t) {
        switch (t) {
            case BOTTLE: return getString(R.string.game_td_role_bottle);
            case SUN: return getString(R.string.game_td_role_sun);
            case SNOW: return getString(R.string.game_td_role_snow);
            case FAN: return getString(R.string.game_td_role_fan);
            case POISON: return getString(R.string.game_td_role_poison);
            case ROCKET: return getString(R.string.game_td_role_rocket);
            case LIGHTNING: return getString(R.string.game_td_role_lightning);
            case SNIPER: return getString(R.string.game_td_role_sniper);
            case MINE: return getString(R.string.game_td_role_mine);
            case AMPLIFIER: return getString(R.string.game_td_role_amplifier);
            default: return getString(R.string.game_td_role_default);
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
        btnNextWave = ctrlButton(ctx, getString(R.string.game_td_btn_fight));
        btnQuitToMenu = ctrlButton(ctx, getString(R.string.game_td_btn_levels));
        btnSelectTower = ctrlButton(ctx, getString(R.string.game_td_btn_tower));

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
            showEngineMsg();
            updateHud();
        });
        btnQuitToMenu.setOnClickListener(v -> showLevelSelect());
        btnSelectTower.setOnClickListener(v -> {
            selectedType = selectedType == null
                    ? (activeDeck.isEmpty() ? null : activeDeck.get(0))
                    : null;
            tdView.setSelectedType(selectedType);
            updateTowerBarSelection();
            showMsg(getString(selectedType == null
                    ? R.string.game_td_msg_selection_cancelled
                    : R.string.game_td_msg_select_tower_hint), "info");
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

        btnUpgrade = ctrlButton(ctx, getString(R.string.game_td_btn_upgrade));
        btnSell = ctrlButton(ctx, getString(R.string.game_td_btn_sell));
        btnTarget = ctrlButton(ctx, getString(R.string.game_td_btn_target));
        btnDeselect = ctrlButton(ctx, getString(R.string.game_td_btn_cancel));
        btnUpgrade.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            if (t.level >= 3) {
                showMsg(getString(R.string.game_td_msg_max_level), "info");
                return;
            }
            mergeSource = t;
            showMsg(getString(R.string.game_td_msg_merge_source_selected, towerName(t.type), t.level),
                    "info");
            hideTowerOps();
            updateHud();
        });
        btnSell.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            game.sellTower(t.row, t.col);
            showEngineMsg();
            hideTowerOps();
            updateHud();
        });
        btnTarget.setOnClickListener(v -> {
            TdGame.Tower t = tdView.getHoverTower();
            if (t == null) return;
            game.cycleTowerTargetMode(t.row, t.col);
            showEngineMsg();
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
        btnUpgrade.setText(t.level >= 3 ? getString(R.string.game_td_btn_merge_max)
                : getString(R.string.game_td_btn_merge_to, t.level + 1));
        btnUpgrade.setEnabled(t.level < 3);
        btnTarget.setEnabled(t.type != TowerType.SUN);
        btnTarget.setText(t.type == TowerType.SUN ? getString(R.string.game_td_btn_target_economy)
                : getString(R.string.game_td_btn_target_mode, targetModeName(t.targetMode)));
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
            showMsg(getString(R.string.game_td_msg_level_locked), "err");
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
        title.setText(getString(R.string.game_td_title_level_select));
        title.setTextSize(22);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView sub = new TextView(requireContext());
        sub.setText(getString(R.string.game_td_subtitle_protect_egg));
        sub.setTextSize(13);
        sub.setTextColor(0xFFCCFFE0);
        sub.setGravity(Gravity.CENTER);
        panel.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            String id = ids.get(i);
            boolean locked = idx >= unlocked;
            int stars = save.getBestStars(id);

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
            meta.setText(locked ? getString(R.string.game_td_label_locked)
                    : TdLevels.levelSub(idx, id) + "  ·  " + starsText(stars));
            meta.setTextSize(11);
            meta.setTextColor(0xFFB0BEC5);

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.addView(name);
            inner.addView(meta);

            card.addView(inner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!locked) {
                Button play = new Button(requireContext());
                play.setText(getString(R.string.game_td_btn_play));
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
        stats.setText(getString(R.string.game_td_stats_summary,
                countCleared(), save.getTotalKills(), save.getPlayCount()));
        stats.setTextSize(12);
        stats.setTextColor(0xFFB0BEC5);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(dp(8), dp(14), dp(8), dp(4));
        panel.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        Button back = ctrlButton(requireContext(), getString(R.string.game_td_btn_back_hall));
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
        title.setText(getString(R.string.game_td_title_difficulty,
                TdLevels.levelDisplayName(levelIdx, TdLevels.levelIds().get(levelIdx))));
        title.setTextSize(20);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        for (final TdGame.Difficulty d : TdGame.Difficulty.values()) {
            Button b = ctrlButton(requireContext(), difficultyName(d));
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(0xFF4A4238);
            gb.setStroke(dp(1), 0xFFFFD54F);
            gb.setCornerRadius(dp(10));
            b.setBackground(gb);
            b.setTextSize(15);
            b.setOnClickListener(v -> {
                showDeckSelect(levelIdx, d);
            });
            panel.addView(b, new LinearLayout.LayoutParams(dp(180), dp(46)));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).topMargin = dp(12);
            ((LinearLayout.LayoutParams) b.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        }

        TextView hint = new TextView(requireContext());
        hint.setText(getString(R.string.game_td_difficulty_hint));
        hint.setTextSize(11);
        hint.setTextColor(0xFFB0BEC5);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        ((LinearLayout.LayoutParams) hint.getLayoutParams()).topMargin = dp(10);

        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 开局选塔：最多 5 张牌，底部只显示本次选中的塔。 */
    private void showDeckSelect(final int levelIdx, final TdGame.Difficulty difficulty) {
        clearOverlay();
        final List<TowerType> availableTowers = TdTowerProgression
                .availableForUnlockedLevelCount(save.getUnlockedLevelCount());
        final Set<TowerType> availableSet = new LinkedHashSet<>(availableTowers);
        final Set<TowerType> draft = new LinkedHashSet<>();
        for (TowerType tower : activeDeck) {
            if (availableSet.contains(tower)) {
                draft.add(tower);
            }
        }
        // 旧版本曾默认把后期塔带入首关。修复到新进度规则时，补齐一个可直接开局的基础塔组，
        // 但不替玩家覆盖已有的有效选择。
        for (TowerType tower : availableTowers) {
            if (draft.size() >= 3) break;
            draft.add(tower);
        }
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(16));
        panel.setBackgroundColor(0xF01E2A1F);

        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.game_td_title_deck_select));
        title.setTextSize(22);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView hint = new TextView(requireContext());
        hint.setText(getString(R.string.game_td_deck_hint,
                availableTowers.size(), TowerType.values().length, DECK_SIZE));
        hint.setTextSize(12);
        hint.setTextColor(0xFFCCFFE0);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        final TextView count = new TextView(requireContext());
        count.setText(deckCountText(draft.size(), availableTowers.size()));
        count.setTextSize(14);
        count.setTextColor(0xFFFFF176);
        count.setGravity(Gravity.CENTER);
        panel.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int start = 0; start < TowerType.values().length; start += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int offset = 0; offset < 2 && start + offset < TowerType.values().length; offset++) {
                final TowerType type = TowerType.values()[start + offset];
                final boolean towerUnlocked = availableSet.contains(type);
                LinearLayout card = new LinearLayout(requireContext());
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(7), dp(5), dp(7), dp(5));
                card.addView(new TowerGlyphView(requireContext(), type),
                        new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView label = new TextView(requireContext());
                label.setText(towerUnlocked
                        ? towerName(type) + "\n" + towerRole(type)
                        : "🔒 " + towerName(type) + "\n" + unlockRequirementText(type));
                label.setTextSize(10);
                label.setTextColor(towerUnlocked ? 0xFFFFFFFF : 0xFF9E9E9E);
                card.addView(label, new LinearLayout.LayoutParams(0, dp(38), 1f));
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
                cardLp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(card, cardLp);
                card.setOnClickListener(v -> {
                    if (!towerUnlocked) {
                        showMsg(getString(R.string.game_td_cd_locked,
                                towerName(type), unlockRequirementText(type)), "info");
                        return;
                    }
                    if (draft.contains(type)) {
                        draft.remove(type);
                    } else if (draft.size() < DECK_SIZE) {
                        draft.add(type);
                    } else {
                        showMsg(getString(R.string.game_td_msg_deck_max, DECK_SIZE), "info");
                        return;
                    }
                    updateDeckCard(card, draft.contains(type), true);
                    count.setText(deckCountText(draft.size(), availableTowers.size()));
                });
                card.setContentDescription(towerUnlocked
                        ? getString(draft.contains(type)
                                ? R.string.game_td_cd_selected : R.string.game_td_cd_unselected,
                                towerName(type))
                        : getString(R.string.game_td_cd_locked,
                                towerName(type), unlockRequirementText(type)));
                updateDeckCard(card, draft.contains(type), towerUnlocked);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        }
        panel.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button start = ctrlButton(requireContext(), getString(R.string.game_td_btn_start_level));
        start.setTextSize(15);
        start.setOnClickListener(v -> {
            if (draft.size() < 3) {
                showMsg(getString(R.string.game_td_msg_deck_min), "err");
                return;
            }
            activeDeck.clear();
            activeDeck.addAll(draft);
            save.recordPlay();
            startLevel(levelIdx, difficulty);
            clearOverlay();
        });
        panel.addView(start, new LinearLayout.LayoutParams(dp(220), dp(44)));
        ((LinearLayout.LayoutParams) start.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private String deckCountText(int selectedCount, int availableCount) {
        return getString(R.string.game_td_deck_count,
                selectedCount, DECK_SIZE, availableCount, TowerType.values().length);
    }

    private void updateDeckCard(View card, boolean selected, boolean unlocked) {
        GradientDrawable bg = new GradientDrawable();
        if (!unlocked) {
            bg.setColor(0xFF2F302D);
            bg.setStroke(dp(1), 0xFF555B55);
        } else {
            bg.setColor(selected ? 0xFF5A5238 : 0xFF3A3A3A);
            bg.setStroke(dp(selected ? 2 : 1), selected ? 0xFFFFC107 : 0xFF666666);
        }
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
    }

    private int countCleared() {
        int c = 0;
        for (String levelId : TdLevels.levelIds()) {
            if (save.getBestStars(levelId) > 0) c++;
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
        btnNextWave.setText(getString(R.string.game_td_btn_fight));
        game = TdLevels.buildLevel(TdLevels.levelIds().get(idx));
        game.applyDifficulty(diff);
        tdView.bind(game);
        tdView.setSelectedType(null);
        selectedType = null;
        refreshTowerDeck();
        updateTowerBarSelection();
        hideTowerOps();
        tvLevelLabel.setText(getString(R.string.game_td_hud_level_short,
                idx + 1, TdLevels.levelIds().size()));
        updateHud();
        showMsg(getString(R.string.game_td_msg_preparing), "info");
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
            int unlockedBefore = save.getUnlockedLevelCount();
            String levelId = TdLevels.levelIds().get(selectedLevelIdx);
            save.recordWin(levelId);
            List<TowerType> newlyUnlocked = TdTowerProgression.newlyUnlockedBetween(
                    unlockedBefore, save.getUnlockedLevelCount());
            save.setBestStars(levelId, stars);
            save.addKills(game.getMonstersKilled());
            save.setBestTimeSec(levelId, (int) game.getElapsedSeconds());
            if (game.getDifficulty() == TdGame.Difficulty.EASY) save.setEasyCleared(true);
            if (game.getDifficulty() == TdGame.Difficulty.HARD) save.setHardCleared(true);
            String resultStats = getString(R.string.game_td_result_stats,
                    game.getMonstersKilled(), (int) game.getElapsedSeconds());
            if (!newlyUnlocked.isEmpty()) {
                resultStats += getString(R.string.game_td_result_new_towers, towerNames(newlyUnlocked));
            }
            showResult(getString(R.string.game_td_result_win),
                    getString(R.string.game_td_result_stars, starsText(stars)),
                    resultStats, 0xFF66BB6A);
        } else {
            showResult(getString(R.string.game_td_result_lose), "",
                    getString(R.string.game_td_result_lose_stats,
                            game.getWaveIndex(), game.getMonstersKilled()),
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
        t3.setMaxLines(2);
        panel.addView(t3, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(line3.contains("\n") ? 52 : 30)));

        boolean won = game.getState() == TdGame.State.WON;
        if (won && selectedLevelIdx + 1 < TdLevels.levelIds().size()) {
            Button next = ctrlButton(requireContext(), getString(R.string.game_td_btn_next_level));
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

        Button retry = ctrlButton(requireContext(), getString(R.string.game_td_btn_retry));
        retry.setOnClickListener(v -> {
            clearOverlay();
            restartLevel(selectedLevelIdx);
        });
        panel.addView(retry, new LinearLayout.LayoutParams(dp(160), dp(44)));
        ((LinearLayout.LayoutParams) retry.getLayoutParams()).topMargin = dp(10);
        ((LinearLayout.LayoutParams) retry.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        Button menu = ctrlButton(requireContext(), getString(R.string.game_td_btn_select_level));
        menu.setOnClickListener(v -> showLevelSelect());
        panel.addView(menu, new LinearLayout.LayoutParams(dp(160), dp(44)));
        ((LinearLayout.LayoutParams) menu.getLayoutParams()).topMargin = dp(10);
        ((LinearLayout.LayoutParams) menu.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ===== HUD =====

    /** 底部塔牌/选塔面板使用的轻量单位模型；不依赖模块资源，所有塔都有可辨识轮廓。 */
    private final class TowerGlyphView extends View {
        private final TowerType type;
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TowerGlyphView(Context context, TowerType type) {
            super(context);
            this.type = type;
            glyphPaint.setStrokeCap(Paint.Cap.ROUND);
            glyphPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) * .34f;
            glyphPaint.setStyle(Paint.Style.FILL);
            glyphPaint.setColor(0x33000000);
            canvas.drawOval(cx - r * 1.15f, cy + r * .52f, cx + r * 1.15f, cy + r * .86f, glyphPaint);
            glyphPaint.setColor(towerUiColor(type));
            canvas.drawCircle(cx, cy, r, glyphPaint);
            glyphPaint.setStyle(Paint.Style.STROKE);
            glyphPaint.setStrokeWidth(Math.max(1.2f, r * .12f));
            glyphPaint.setColor(0xCCFFFFFF);
            canvas.drawCircle(cx, cy, r * .94f, glyphPaint);
            glyphPaint.setStyle(Paint.Style.FILL);
            switch (type) {
                case BOTTLE:
                    glyphPaint.setColor(0xFF4FC3F7);
                    canvas.drawRoundRect(cx - r * .35f, cy - r * .05f, cx + r * .35f, cy + r * .62f, r * .14f, r * .14f, glyphPaint);
                    canvas.drawRect(cx - r * .13f, cy - r * .6f, cx + r * .13f, cy - r * .1f, glyphPaint);
                    glyphPaint.setColor(0xFF795548);
                    canvas.drawRect(cx - r * .16f, cy - r * .7f, cx + r * .16f, cy - r * .55f, glyphPaint);
                    break;
                case SUN:
                    glyphPaint.setColor(0xFFFFB300);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4d;
                        canvas.drawCircle(cx + (float) Math.cos(a) * r * .62f,
                                cy + (float) Math.sin(a) * r * .62f, r * .16f, glyphPaint);
                    }
                    glyphPaint.setColor(0xFF795548);
                    canvas.drawCircle(cx, cy, r * .34f, glyphPaint);
                    glyphPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx - r * .12f, cy - r * .06f, r * .05f, glyphPaint);
                    canvas.drawCircle(cx + r * .12f, cy - r * .06f, r * .05f, glyphPaint);
                    break;
                case SNOW:
                    glyphPaint.setColor(0xFF0D47A1);
                    glyphPaint.setStrokeWidth(r * .13f);
                    glyphPaint.setStyle(Paint.Style.STROKE);
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI / 3d;
                        canvas.drawLine(cx - (float) Math.cos(a) * r * .58f, cy - (float) Math.sin(a) * r * .58f,
                                cx + (float) Math.cos(a) * r * .58f, cy + (float) Math.sin(a) * r * .58f, glyphPaint);
                    }
                    glyphPaint.setStyle(Paint.Style.FILL);
                    break;
                case FAN:
                    glyphPaint.setColor(0xFF607D8B);
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI * 2d / 3d;
                        Path blade = new Path();
                        blade.moveTo(cx, cy);
                        blade.lineTo(cx + (float) Math.cos(a) * r * .7f, cy + (float) Math.sin(a) * r * .7f);
                        blade.lineTo(cx + (float) Math.cos(a + .45) * r * .42f, cy + (float) Math.sin(a + .45) * r * .42f);
                        blade.close();
                        canvas.drawPath(blade, glyphPaint);
                    }
                    glyphPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx, cy, r * .14f, glyphPaint);
                    break;
                case POISON:
                    glyphPaint.setColor(0xFF7B1FA2);
                    canvas.drawCircle(cx - r * .2f, cy + r * .12f, r * .26f, glyphPaint);
                    canvas.drawCircle(cx + r * .25f, cy - r * .08f, r * .2f, glyphPaint);
                    canvas.drawCircle(cx, cy - r * .42f, r * .12f, glyphPaint);
                    break;
                case ROCKET:
                    glyphPaint.setColor(0xFFE53935);
                    canvas.drawRoundRect(cx - r * .22f, cy - r * .62f, cx + r * .22f, cy + r * .45f, r * .12f, r * .12f, glyphPaint);
                    glyphPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx, cy - r * .1f, r * .12f, glyphPaint);
                    glyphPaint.setColor(0xFFFFD54F);
                    canvas.drawCircle(cx, cy + r * .62f, r * .18f, glyphPaint);
                    break;
                case LIGHTNING:
                    glyphPaint.setColor(0xFFFFF176);
                    Path bolt = new Path();
                    bolt.moveTo(cx + r * .12f, cy - r * .7f);
                    bolt.lineTo(cx - r * .36f, cy + r * .02f);
                    bolt.lineTo(cx - r * .02f, cy + r * .02f);
                    bolt.lineTo(cx - r * .18f, cy + r * .7f);
                    bolt.lineTo(cx + r * .4f, cy - r * .12f);
                    bolt.lineTo(cx + r * .05f, cy - r * .12f);
                    bolt.close();
                    canvas.drawPath(bolt, glyphPaint);
                    break;
                case SNIPER:
                    glyphPaint.setColor(0xFF37474F);
                    canvas.drawRoundRect(cx - r * .2f, cy - r * .58f, cx + r * .2f, cy + r * .58f, r * .08f, r * .08f, glyphPaint);
                    glyphPaint.setStyle(Paint.Style.STROKE);
                    glyphPaint.setStrokeWidth(r * .1f);
                    glyphPaint.setColor(0xFFB2EBF2);
                    canvas.drawCircle(cx, cy - r * .1f, r * .3f, glyphPaint);
                    canvas.drawLine(cx - r * .3f, cy - r * .1f, cx + r * .3f, cy - r * .1f, glyphPaint);
                    canvas.drawLine(cx, cy - r * .4f, cx, cy + r * .2f, glyphPaint);
                    glyphPaint.setStyle(Paint.Style.FILL);
                    break;
                case MINE:
                    glyphPaint.setColor(0xFF263238);
                    canvas.drawCircle(cx, cy, r * .5f, glyphPaint);
                    glyphPaint.setColor(0xFFFF5252);
                    canvas.drawCircle(cx, cy, r * .13f, glyphPaint);
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2d + Math.PI / 4d;
                        canvas.drawCircle(cx + (float) Math.cos(a) * r * .62f,
                                cy + (float) Math.sin(a) * r * .62f, r * .11f, glyphPaint);
                    }
                    break;
                case AMPLIFIER:
                    glyphPaint.setStyle(Paint.Style.STROKE);
                    glyphPaint.setStrokeWidth(r * .15f);
                    glyphPaint.setColor(0xFFB2EBF2);
                    canvas.drawCircle(cx, cy, r * .5f, glyphPaint);
                    glyphPaint.setStyle(Paint.Style.FILL);
                    glyphPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx, cy, r * .16f, glyphPaint);
                    break;
            }
        }
    }

    private void updateHud() {
        if (game == null) return;
        tvCoin.setText("₿ " + game.getCoin());
        tvWave.setText(getString(R.string.game_td_hud_wave, game.getWaveIndex(), game.getTotalWaves())
                + (game.getState() == TdGame.State.PREPARING
                        ? " " + getString(R.string.game_td_hud_wave_preparing) : ""));
        tvHp.setText("🥚 " + game.getMascotHp() + "/" + game.getMaxMascotHp());
        // 下一波预告
        String next = nextWavePreview();
        tvNext.setText(next.isEmpty() ? getString(R.string.game_td_hud_final_line)
                : getString(R.string.game_td_hud_preview, next));
        tvLevelLabel.setText(getString(R.string.game_td_hud_level_diff,
                selectedLevelIdx + 1, difficultyName(game.getDifficulty())));
        if (!game.isEnded()) {
            if (game.getState() == TdGame.State.PREPARING) {
                btnNextWave.setText(getString(R.string.game_td_btn_fight));
            } else if (game.isWaveSpawning()) {
                btnNextWave.setText(getString(R.string.game_td_btn_rush_spawn));
            } else {
                btnNextWave.setText(getString(R.string.game_td_btn_next_wave));
            }
        }
    }

    /** HUD 的下一波预告：引擎给中性构成数据，这里负责本地化（路线/混编前缀/本地化怪名）。 */
    private String nextWavePreview() {
        if (game == null) return "";
        int nextCount = game.nextWaveCount();
        if (nextCount <= 0) return "";
        int route = game.nextWaveRouteIndex();
        List<MonsterType> composition = game.nextWaveComposition();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < composition.size(); i++) {
            if (i > 0) names.append('+');
            names.append(monsterName(composition.get(i)));
        }
        String compositionText = composition.size() > 1
                ? getString(R.string.game_td_wave_mixed, names.toString())
                : names.toString();
        for (int i = 0; i < composition.size(); i++) {
            if (i > 0) names.append('+');
            names.append(monsterName(composition.get(i)));
        }
        return getString(R.string.game_td_hud_preview_route, route + 1, compositionText, nextCount);
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

    /** 结算面板的解锁塔名列表（顿号等分隔符按 locale 取）。 */
    private String towerNames(List<TowerType> towers) {
        String separator = getString(R.string.game_td_list_separator);
        StringBuilder out = new StringBuilder();
        for (TowerType tower : towers) {
            if (out.length() > 0) out.append(separator);
            out.append(towerName(tower));
        }
        return out.toString();
    }

    // ===== 本地化辅助：引擎只给中性枚举/码，名字一律经宿主资源解析 =====

    /** 塔的本地化名（与 TowerType 一一对应，宿主资源 game_td_tower_*）。 */
    private String towerName(TowerType t) {
        if (t == null) return "";
        switch (t) {
            case BOTTLE: return getString(R.string.game_td_tower_bottle);
            case SUN: return getString(R.string.game_td_tower_sun);
            case SNOW: return getString(R.string.game_td_tower_snow);
            case FAN: return getString(R.string.game_td_tower_fan);
            case POISON: return getString(R.string.game_td_tower_poison);
            case ROCKET: return getString(R.string.game_td_tower_rocket);
            case LIGHTNING: return getString(R.string.game_td_tower_lightning);
            case SNIPER: return getString(R.string.game_td_tower_sniper);
            case MINE: return getString(R.string.game_td_tower_mine);
            case AMPLIFIER: return getString(R.string.game_td_tower_amplifier);
            default: return t.displayName; // 引擎英文名兜底
        }
    }

    /** 怪的本地化名（与 MonsterType 一一对应，宿主资源 game_td_monster_*）。 */
    private String monsterName(MonsterType m) {
        if (m == null) return "";
        switch (m) {
            case NORMAL: return getString(R.string.game_td_monster_normal);
            case FAST: return getString(R.string.game_td_monster_fast);
            case TANK: return getString(R.string.game_td_monster_tank);
            case FLY: return getString(R.string.game_td_monster_fly);
            case SWARM: return getString(R.string.game_td_monster_swarm);
            case HEALER: return getString(R.string.game_td_monster_healer);
            case SHIELD: return getString(R.string.game_td_monster_shield);
            case BOSS: return getString(R.string.game_td_monster_boss);
            case SPLITTER: return getString(R.string.game_td_monster_splitter);
            case CHARGER: return getString(R.string.game_td_monster_charger);
            case SHIELD_GENERATOR: return getString(R.string.game_td_monster_shield_generator);
            case SUMMONER: return getString(R.string.game_td_monster_summoner);
            case RESISTANT: return getString(R.string.game_td_monster_resistant);
            case RAGER: return getString(R.string.game_td_monster_rager);
            default: return m.displayName; // 引擎英文名兜底
        }
    }

    /** 难度本地化名（game_td_difficulty_*）。 */
    private String difficultyName(TdGame.Difficulty d) {
        if (d == null) return "";
        switch (d) {
            case EASY: return getString(R.string.game_td_difficulty_easy);
            case HARD: return getString(R.string.game_td_difficulty_hard);
            case NORMAL:
            default: return getString(R.string.game_td_difficulty_normal);
        }
    }

    /** 目标优先级本地化名（game_td_target_*）。 */
    private String targetModeName(TdGame.TargetMode mode) {
        if (mode == null) return "";
        switch (mode) {
            case STRONG: return getString(R.string.game_td_target_strong);
            case WEAK: return getString(R.string.game_td_target_weak);
            case FIRST:
            default: return getString(R.string.game_td_target_first);
        }
    }

    /** 塔牌解锁条件（替代引擎层旧 unlockRequirement 文案；引擎只出 unlockLevel 数据）。 */
    private String unlockRequirementText(TowerType t) {
        int level = TdTowerProgression.unlockLevel(t);
        return level <= 1 ? getString(R.string.game_td_unlock_start)
                : getString(R.string.game_td_unlock_after_level, level - 1);
    }

    /** 显示引擎最近一次操作结果：中性 ActionMsg + 参数 → 本地化文案。 */
    private void showEngineMsg() {
        if (game == null) return;
        showMsg(formatEngineMsg(game.getLastActionMsg(), game.getLastActionArgs()),
                game.getLastActionTone());
    }

    private String formatEngineMsg(TdGame.ActionMsg msg, Object[] args) {
        if (msg == null) return "";
        switch (msg) {
            case INVALID_TOWER_TYPE: return getString(R.string.game_td_act_invalid_tower_type);
            case GAME_ENDED: return getString(R.string.game_td_act_game_ended);
            case OUT_OF_BOUNDS: return getString(R.string.game_td_act_out_of_bounds);
            case MINE_NEEDS_PATH_SIDE: return getString(R.string.game_td_act_mine_needs_path);
            case BLOCKS_PATH: return getString(R.string.game_td_act_blocks_path);
            case BLOCKS_EGG: return getString(R.string.game_td_act_blocks_egg);
            case CELL_OCCUPIED: return getString(R.string.game_td_act_cell_occupied);
            case NOT_ENOUGH_COIN: return getString(R.string.game_td_act_not_enough_coin, (int) args[0]);
            case PLACED: return getString(R.string.game_td_act_placed, towerName((TowerType) args[0]));
            case DRAG_SAME_TYPE_ONLY: return getString(R.string.game_td_act_drag_same_type);
            case MAX_LEVEL_REACHED: return getString(R.string.game_td_act_max_level);
            case MERGED: return getString(R.string.game_td_act_merged,
                    towerName((TowerType) args[0]), (int) args[1]);
            case UPGRADE_DEPRECATED: return getString(R.string.game_td_act_upgrade_deprecated,
                    towerName((TowerType) args[0]));
            case MERGE_PICK_ANOTHER: return getString(R.string.game_td_act_merge_pick_another);
            case MERGE_NEEDS_TWO_TOWERS: return getString(R.string.game_td_act_merge_need_two);
            case MERGE_TYPE_MISMATCH: return getString(R.string.game_td_act_merge_type_mismatch);
            case MERGE_LEVEL_MISMATCH: return getString(R.string.game_td_act_merge_level_mismatch);
            case NO_TOWER_HERE: return getString(R.string.game_td_act_no_tower_here);
            case SUN_NO_TARGET: return getString(R.string.game_td_act_sun_no_target);
            case TARGET_MODE_SET: return getString(R.string.game_td_act_target_set,
                    towerName((TowerType) args[0]), targetModeName((TdGame.TargetMode) args[1]));
            case SOLD: return getString(R.string.game_td_act_sold, (int) args[0]);
            case FIRST_WAVE_INCOMING: return getString(R.string.game_td_act_first_wave);
            case WAVE_INCOMING: return getString(R.string.game_td_act_wave_incoming, (int) args[0]);
            case RUSH_SUMMONED: return getString(R.string.game_td_act_rush_summoned,
                    (int) args[0], (int) args[1]);
            case WAVE_FULLY_SPAWNED: return getString(R.string.game_td_act_wave_fully_spawned);
            case LAST_WAVE_REACHED: return getString(R.string.game_td_act_last_wave);
            case EGG_HIT: return getString(R.string.game_td_act_egg_hit,
                    monsterName((MonsterType) args[0]), (int) args[1], (int) args[2]);
            case VICTORY: return getString(R.string.game_td_act_victory);
            default: return "";
        }
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
