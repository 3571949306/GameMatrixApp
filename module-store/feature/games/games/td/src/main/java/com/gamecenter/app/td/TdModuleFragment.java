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
 * 覆盖层：选关面板（星级/解锁/难度选择/战绩与图鉴入口）、战绩/成就面板、图鉴面板（塔与怪属性速查）、
 * 结算面板（胜负统计、下一关/重玩）。
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
    /** 塔操作条顶部的升级预览行（tag=td_upgrade_preview，供回归测试定位）。 */
    private TextView tvUpgradePreview;
    private final Runnable tickLoop = this::tickOnce;
    private TowerType selectedType = null;
    /** 合成源塔；非空时下一次点选塔会作为合成目标。 */
    private TdGame.Tower mergeSource;
    private int selectedLevelIdx = 0;
    /** 本局对局模式（战役/无尽）：restartLevel 与结算据此保持模式一致。 */
    private TdGame.Mode selectedMode = TdGame.Mode.CAMPAIGN;
    private int gameSession = 0;
    private boolean paused = false;
    private boolean gameEnded = false;
    /**
     * 放弃局击杀入账守卫（每局至多一次）：放弃路径（返回选关/对局中换关/销毁）可能被
     * 连续触发（选关 → 战绩/图鉴 → 再返回选关；先选关后销毁），由本标记防止同一局
     * 重复 addKills；startLevel 开新局时复位。不复用 gameEnded 承担此职责——它语义是
     * 「结算已走过」，放弃局置位会连带改变 showLevelSelect 的 gameSession 闸等行为。
     */
    private boolean killsRecordedForSession = false;

    /** PVZ 式开局塔组：每局只带 5 张牌，避免小屏横向挤压。 */
    private static final int DECK_SIZE = 5;
    /** 每关最高星数（引擎 starsEarned 满分），战绩面板累计星级的分母 = 关卡数 × 此值。 */
    private static final int MAX_STARS_PER_LEVEL = 3;
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
        try {
            moduleResources = ModuleManager.INSTANCE.getModuleResources("td");
            if (moduleResources == null) {
                throw new IllegalStateException("TD module resources are unavailable");
            }
            // 关卡数据是模块资产真源。加载器会先完成 schema、路径、波次和枚举校验，
            // 绝不回退到陈旧 Java 关卡，避免内容版本与 UI/存档悄悄错配。
            TdLevels.initialize(moduleResources.getAssetManager());
            // 存档的历史解锁清洗依赖已加载的 campaign catalog；必须在上面初始化之后构造。
            save = new TdSaveManager(requireContext().getApplicationContext());
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
                TdGame.Tower source = mergeSource;
                mergeSource = null;
                // A sold/consumed tower or a tower from another session cannot authorize
                // merging a replacement that happens to occupy its former coordinates.
                if (source != null && game.getTowerAt(source.row, source.col) == source) {
                    game.mergeTowers(source.row, source.col, row, col);
                    showEngineMsg();
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
                if (game.getMode() == TdGame.Mode.ENDLESS) {
                    // 无尽模式 totalWaves 随合成波递增，"共 N 波"语义不成立。
                    tdView.showWaveBannerEndless(game.getEndlessWaveReached());
                } else {
                    tdView.showWaveBanner(game.getWaveIndex(), game.getTotalWaves());
                }
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
        // 竖排结构：顶部升级预览行（决策信息）+ 底部原四按钮操作行，操作行布局与历史版本一致
        LinearLayout bar = new LinearLayout(ctx);
        towerOpsBar = bar;
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(2), dp(6), dp(2));
        bar.setBackgroundColor(0xFF433A2C);

        tvUpgradePreview = new TextView(ctx);
        tvUpgradePreview.setTextSize(11);
        tvUpgradePreview.setTextColor(0xFFFFD54F);
        tvUpgradePreview.setGravity(Gravity.CENTER);
        tvUpgradePreview.setTag("td_upgrade_preview");
        bar.addView(tvUpgradePreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
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

        row.addView(btnUpgrade, new LinearLayout.LayoutParams(0, dp(34), 1f));
        row.addView(btnTarget, new LinearLayout.LayoutParams(0, dp(34), 1f));
        row.addView(btnSell, new LinearLayout.LayoutParams(0, dp(34), 1f));
        row.addView(btnDeselect, new LinearLayout.LayoutParams(0, dp(34), 1f));
        bar.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setVisibility(View.GONE);
        return bar;
    }

    private LinearLayout towerOpsBar;

    private void showTowerOps(TdGame.Tower t) {
        towerOpsBar.setVisibility(View.VISIBLE);
        btnUpgrade.setText(t.level >= 3 ? getString(R.string.game_td_btn_merge_max)
                : getString(R.string.game_td_btn_merge_to, t.level + 1));
        btnUpgrade.setEnabled(t.level < 3);
        btnTarget.setEnabled(t.type != TowerType.SUN && t.type != TowerType.SNIPER);
        btnTarget.setText(t.type == TowerType.SUN ? getString(R.string.game_td_btn_target_economy)
                : getString(R.string.game_td_btn_target_mode, targetModeName(
                        t.type == TowerType.SNIPER ? TdGame.TargetMode.STRONG : t.targetMode)));
        refreshUpgradePreview(t);
    }

    /**
     * 升级预览行：按塔型分口径展示合成升到下一级后的成长，满级提示「已满级」。
     * 不标金币数——两条真实升级路径的消耗口径不同（拖拽合成扣被拖塔 baseCost，
     * 点选合成不扣金只消耗一座塔），upgradeCost(n+1) 在生产路径零调用，标出会误导玩家。
     * <ul>
     *   <li>攻击塔：伤害/射程/攻速（damageAt/rangeAt/fireIntervalAt(n+1)，数值口径复用
     *       图鉴 codexNum）。增幅塔加成（AMPLIFIER 攻速/射程）不在本面板展示——当其确在
     *       射程内时，预览行追加注记说明数值为加成前基础值，避免玩家把面板值误读为实战值。</li>
     *   <li>太阳花（SUN）：伤害/攻速恒为 0 无信息量，改示下一级单次产币金额
     *       incomeAt(n+1)，取整口径与图鉴收益行（Math.round）一致。</li>
     *   <li>增幅塔（AMPLIFIER）：改示下一级光环成长 amplifierAttackSpeedBonusAt(n+1) /
     *       amplifierRangeBonusAt(n+1)，按百分数取整展示（引擎光环互不叠加、
     *       增幅塔与太阳花不受光环加成，故无 amplified 注记分支）。</li>
     * </ul>
     */
    private void refreshUpgradePreview(TdGame.Tower t) {
        if (tvUpgradePreview == null) return;
        if (t.level >= 3) {
            tvUpgradePreview.setText(getString(R.string.game_td_upgrade_preview_max));
            return;
        }
        int next = t.level + 1;
        String preview;
        if (t.type == TowerType.SUN) {
            // 经济塔预览：改示下一级单次产币金额（incomeAt 与图鉴收益行同源）
            preview = getString(R.string.game_td_upgrade_preview_sun, next,
                    Math.round(t.type.incomeAt(next)));
        } else if (t.type == TowerType.AMPLIFIER) {
            // 增幅塔预览：改示下一级光环攻速/射程加成（×100 取整为百分数）
            preview = getString(R.string.game_td_upgrade_preview_amplifier, next,
                    Math.round(t.type.amplifierAttackSpeedBonusAt(next) * 100),
                    Math.round(t.type.amplifierRangeBonusAt(next) * 100));
        } else {
            preview = getString(R.string.game_td_upgrade_preview_next, next,
                    codexNum(t.type.damageAt(next)), codexNum(t.type.rangeAt(next)),
                    codexNum(t.type.fireIntervalAt(next)));
            if (game != null
                    && (game.getAttackSpeedBonus(t) > 0f || game.getRangeBonus(t) > 0f)) {
                preview += getString(R.string.game_td_upgrade_preview_amplified_note);
            }
        }
        tvUpgradePreview.setText(preview);
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
        mergeSource = null;
        if (game != null && !game.isEnded()) {
            recordAbandonedKills();
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

        // 章节分组：滚动顺序 = 章节顺序，每章一个章头（章节名 + 已通进度）+ 该章关卡卡片。
        // 兼容：catalog 未携带章节元数据（历史 installForTesting 纯关卡装填）时维持平铺渲染。
        List<TdLevels.ChapterGroup> chapters = TdLevels.chapterGroups();
        if (chapters.isEmpty()) {
            for (int i = 0; i < ids.size(); i++) {
                addLevelCard(panel, i, ids.get(i), unlocked);
            }
        } else {
            int firstIdx = 0;
            for (TdLevels.ChapterGroup group : chapters) {
                panel.addView(chapterSection(group, firstIdx, unlocked),
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                firstIdx += group.levelIds().size();
            }
        }

        TextView stats = new TextView(requireContext());
        stats.setText(getString(R.string.game_td_stats_summary,
                countCleared(), save.getTotalKills(), save.getPlayCount()));
        stats.setTextSize(12);
        stats.setTextColor(0xFFB0BEC5);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(dp(8), dp(14), dp(8), dp(4));
        panel.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        // 战绩/成就面板入口：风格与「无尽模式」按钮一致（程序化 Button + 蓝底圆角）
        Button statsEntry = new Button(requireContext());
        statsEntry.setText(getString(R.string.game_td_btn_stats));
        statsEntry.setTextSize(12);
        statsEntry.setAllCaps(false);
        statsEntry.setStateListAnimator(null);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0xFF1E88E5);
        sb.setCornerRadius(dp(8));
        statsEntry.setBackground(sb);
        statsEntry.setTextColor(0xFFFFFFFF);
        statsEntry.setOnClickListener(v -> showStatsPanel());
        panel.addView(statsEntry, new LinearLayout.LayoutParams(dp(120), dp(40)));
        ((LinearLayout.LayoutParams) statsEntry.getLayoutParams()).topMargin = dp(10);
        ((LinearLayout.LayoutParams) statsEntry.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        // 图鉴入口：与战绩按钮同构（程序化 Button + 圆角），青色区分战绩/无尽入口
        Button codexEntry = new Button(requireContext());
        codexEntry.setText(getString(R.string.game_td_btn_codex));
        codexEntry.setTextSize(12);
        codexEntry.setAllCaps(false);
        codexEntry.setStateListAnimator(null);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0xFF00897B);
        cb.setCornerRadius(dp(8));
        codexEntry.setBackground(cb);
        codexEntry.setTextColor(0xFFFFFFFF);
        codexEntry.setOnClickListener(v -> showCodexPanel());
        panel.addView(codexEntry, new LinearLayout.LayoutParams(dp(120), dp(40)));
        ((LinearLayout.LayoutParams) codexEntry.getLayoutParams()).topMargin = dp(8);
        ((LinearLayout.LayoutParams) codexEntry.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        Button back = ctrlButton(requireContext(), getString(R.string.game_td_btn_back_hall));
        back.setOnClickListener(v -> exitToHall());
        panel.addView(back, new LinearLayout.LayoutParams(dp(120), dp(40)));
        ((LinearLayout.LayoutParams) back.getLayoutParams()).topMargin = dp(8);
        ((LinearLayout.LayoutParams) back.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF01E2A1F);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * 一个章节分组段：章头（章节名 + 已通进度）在上，该章关卡卡片按全局顺序排列在其下，
     * 整体滚动顺序与章节顺序一致。tag 前缀（td_chapter:* / td_level_card:*）供 UI 回归测试定位。
     */
    private LinearLayout chapterSection(TdLevels.ChapterGroup group, int firstIdx, int unlocked) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("td_chapter:" + group.id);
        section.addView(chapterHeader(group));
        List<String> groupIds = group.levelIds();
        for (int i = 0; i < groupIds.size(); i++) {
            addLevelCard(section, firstIdx + i, groupIds.get(i), unlocked);
        }
        return section;
    }

    /** 章头：章节名（TdLevels 按 locale 解析）+ 该章通关进度，延续浮层既有的金色标题风格。 */
    private LinearLayout chapterHeader(TdLevels.ChapterGroup group) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(12), dp(4), 0);

        TextView name = new TextView(requireContext());
        name.setText(group.displayName());
        name.setTextSize(15);
        name.setTextColor(0xFFFFD54F);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTag("td_chapter_name:" + group.id);
        header.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 进度口径：该章内 getBestStars>0 的关卡数记为「已通」，与战绩面板 countCleared 一致
        // （星数非零即通关；锁定关与未获星关均不计入）。总数为该章 levelCount。
        int cleared = 0;
        for (String id : group.levelIds()) {
            if (save.getBestStars(id) > 0) cleared++;
        }
        TextView progress = new TextView(requireContext());
        progress.setText(getString(R.string.game_td_chapter_progress, cleared, group.levelIds().size()));
        progress.setTextSize(11);
        progress.setTextColor(0xFFB0BEC5);
        progress.setTag("td_chapter_progress:" + group.id);
        header.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    /** 单张关卡卡片（平铺时代的结构原样保留：序号+名称/副题+星级 + 开始/无尽入口），按全局序号判定锁定。 */
    private void addLevelCard(LinearLayout parent, int idx, String id, int unlocked) {
        final int levelIdx = idx;
        String levelId = id;
        boolean locked = levelIdx >= unlocked;
        int stars = save.getBestStars(levelId);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(locked ? 0xFF3A3A3A : 0xFF4A4238);
        bg.setStroke(dp(1), locked ? 0xFF555555 : 0xFFFFD54F);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        card.setTag("td_level_card:" + levelId);

        TextView name = new TextView(requireContext());
        name.setText((levelIdx + 1) + ". " + TdLevels.levelDisplayName(levelIdx, levelId));
        name.setTextSize(16);
        name.setTextColor(locked ? 0xFF777777 : 0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);

        TextView meta = new TextView(requireContext());
        meta.setText(locked ? getString(R.string.game_td_label_locked)
                : TdLevels.levelSub(levelIdx, levelId) + "  ·  " + starsText(stars));
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
            play.setOnClickListener(v -> showDifficultySelect(levelIdx));
            card.addView(play, new LinearLayout.LayoutParams(dp(56), dp(36)));
            // 无尽模式入口：复用同一张关卡地图/路线/蛋位，难度与塔组流程与战役一致
            Button endless = new Button(requireContext());
            endless.setText(getString(R.string.game_td_btn_endless));
            endless.setTextSize(11);
            endless.setAllCaps(false);
            endless.setStateListAnimator(null);
            GradientDrawable eb = new GradientDrawable();
            eb.setColor(0xFF1E88E5);
            eb.setCornerRadius(dp(8));
            endless.setBackground(eb);
            endless.setTextColor(0xFFFFFFFF);
            endless.setOnClickListener(v -> showDifficultySelect(levelIdx, true));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(64), dp(36));
            elp.leftMargin = dp(6);
            card.addView(endless, elp);
        }
        parent.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = dp(10);
    }

    private void showDifficultySelect(final int levelIdx) {
        showDifficultySelect(levelIdx, false);
    }

    /** endless=true 时沿用战役的难度/塔组流程，仅开局模式切换为 ENDLESS。 */
    private void showDifficultySelect(final int levelIdx, final boolean endless) {
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
                showDeckSelect(levelIdx, d, endless);
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

    private void showDeckSelect(final int levelIdx, final TdGame.Difficulty difficulty) {
        showDeckSelect(levelIdx, difficulty, false);
    }

    /** 开局选塔：最多 5 张牌，底部只显示本次选中的塔。endless=true 时以无尽模式开局。 */
    private void showDeckSelect(final int levelIdx, final TdGame.Difficulty difficulty,
                                final boolean endless) {
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
        addBattleBriefing(grid, levelIdx, difficulty,
                endless ? TdGame.Mode.ENDLESS : TdGame.Mode.CAMPAIGN);
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
        ScrollView deckScroll = new ScrollView(requireContext());
        deckScroll.setTag("td_deck_scroll");
        deckScroll.addView(grid);
        panel.addView(deckScroll, new LinearLayout.LayoutParams(
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
            // 先关闭塔组面板；startLevel 可能创建剧情浮层，开局后不能再次清除。
            clearOverlay();
            startLevel(levelIdx, difficulty,
                    endless ? TdGame.Mode.ENDLESS : TdGame.Mode.CAMPAIGN);
        });
        panel.addView(start, new LinearLayout.LayoutParams(dp(220), dp(44)));
        ((LinearLayout.LayoutParams) start.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Preview uses the real level factory/difficulty rules without replacing the live session. */
    private void addBattleBriefing(LinearLayout parent, int levelIdx,
                                   TdGame.Difficulty difficulty, TdGame.Mode mode) {
        String levelId = TdLevels.levelIds().get(levelIdx);
        TdGame preview = TdLevels.buildLevel(levelId, mode);
        preview.applyDifficulty(difficulty);
        TextView summary = new TextView(requireContext());
        summary.setTag("td_battle_brief_summary");
        summary.setText(TdLevels.levelDisplayName(levelIdx, levelId) + "\n"
                + getString(R.string.game_td_brief_resources, preview.getCoin(),
                        preview.getMascotHp(), preview.getPaths().length) + "\n"
                + getString(mode == TdGame.Mode.ENDLESS ? R.string.game_td_brief_endless
                        : R.string.game_td_brief_campaign, preview.getTotalWaves()));
        summary.setTextSize(13);
        summary.setTextColor(0xFFFFF8E1);
        summary.setPadding(dp(8), dp(8), dp(8), dp(4));
        parent.addView(summary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Set<MonsterType> types = new LinkedHashSet<>();
        for (TdGame.Wave wave : preview.getWaves()) {
            java.util.Collections.addAll(types, wave.compositionTypes());
        }
        StringBuilder names = new StringBuilder();
        for (MonsterType type : types) {
            if (names.length() > 0) names.append(" · ");
            names.append(monsterName(type));
        }
        TextView enemies = new TextView(requireContext());
        enemies.setTag("td_battle_brief_enemies");
        enemies.setText(getString(mode == TdGame.Mode.ENDLESS ? R.string.game_td_brief_opening_enemies
                : R.string.game_td_brief_enemies, names.toString()));
        enemies.setTextSize(12);
        enemies.setTextColor(0xFFB3E5FC);
        enemies.setPadding(dp(8), 0, dp(8), dp(12));
        parent.addView(enemies, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    // ===== 战绩 / 成就面板（数据实时读取自存档；成就解锁状态持久化于 td_achv_* 布尔键） =====

    /** 主入口：战绩/成就浮层。打开时对局处于覆盖层暂停态，无并发写存档问题。 */
    private void showStatsPanel() {
        showStatsPanel(null);
    }

    /** Builds the stats panel and optionally shows a visible one-shot notice below its title. */
    private void showStatsPanel(@Nullable String notice) {
        clearOverlay();
        Context ctx = requireContext();
        // 成就补漏：成就系统上线前的老存档（已通关/已累计但未落章）在此幂等补齐；
        // 解锁判定主路径在存档写入点，此处为查询时纵深兜底
        save.syncAchievementsFromState();
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView title = new TextView(ctx);
        title.setText(getString(R.string.game_td_stats_title));
        title.setTextSize(22);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        if (notice != null && !notice.isEmpty()) {
            TextView noticeView = new TextView(ctx);
            noticeView.setText(notice);
            noticeView.setTextSize(13);
            noticeView.setTextColor(0xFF9CFFB0);
            noticeView.setGravity(Gravity.CENTER);
            noticeView.setTag("td_stats_notice");
            panel.addView(noticeView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        }

        int levelCount = TdLevels.levelIds().size();
        int[] campaignStars = new int[levelCount];
        for (int i = 0; i < levelCount; i++) {
            campaignStars[i] = save.getBestStars(TdLevels.levelIds().get(i));
        }

        // 战绩总览：总击杀 / 总局数
        addStatsSection(panel, getString(R.string.game_td_stats_section_record));
        addStatsRow(panel, getString(R.string.game_td_stats_label_kills),
                String.valueOf(save.getTotalKills()), 0xFFFFF8E1);
        addStatsRow(panel, getString(R.string.game_td_stats_label_play_count),
                String.valueOf(save.getPlayCount()), 0xFFFFF8E1);

        // 战役进度：已解锁关数/总关数、累计星级/总星数
        addStatsSection(panel, getString(R.string.game_td_stats_section_campaign));
        // 分子钳制到 levelCount：历史存档可能残留越界 unlocked（recordWin 已在
        // TdSaveManager 端硬上限钳制，此处为防御旧脏数据的纵深兜底）。
        addStatsRow(panel, getString(R.string.game_td_stats_label_unlocked),
                getString(R.string.game_td_stats_value_fraction,
                        Math.min(save.getUnlockedLevelCount(), levelCount), levelCount), 0xFFFFD54F);
        addStatsRow(panel, getString(R.string.game_td_stats_label_stars),
                getString(R.string.game_td_stats_value_fraction,
                        TdSaveManager.sumBestStars(campaignStars), levelCount * MAX_STARS_PER_LEVEL),
                0xFFFFD54F);

        // 战役最佳用时按难度隔离；0 表示该难度尚未记录胜局。
        addStatsSection(panel, getString(R.string.game_td_stats_section_times));
        for (TdGame.Difficulty d : TdGame.Difficulty.values()) {
            int seconds = save.getBestCampaignTimeSec(d);
            addStatsRow(panel, difficultyName(d),
                    seconds > 0 ? getString(R.string.game_td_stats_value_seconds, seconds)
                            : getString(R.string.game_td_stats_value_none),
                    seconds > 0 ? 0xFF9CFFB0 : 0xFF78909C);
        }

        // 无尽最佳：各难度一行，0 = 未挑战
        addStatsSection(panel, getString(R.string.game_td_stats_section_endless));
        for (TdGame.Difficulty d : TdGame.Difficulty.values()) {
            int waves = save.getBestEndlessWaves(d);
            addStatsRow(panel, difficultyName(d),
                    waves > 0 ? getString(R.string.game_td_stats_value_waves, waves)
                            : getString(R.string.game_td_stats_value_none),
                    waves > 0 ? 0xFF9CFFB0 : 0xFF78909C);
        }

        // 难度成就：简单/困难通关标记
        addStatsSection(panel, getString(R.string.game_td_stats_section_achievements));
        addStatsRow(panel, getString(R.string.game_td_stats_label_cleared_easy),
                getString(save.isEasyCleared() ? R.string.game_td_stats_value_achieved
                        : R.string.game_td_stats_value_not_achieved),
                save.isEasyCleared() ? 0xFF9CFFB0 : 0xFF78909C);
        addStatsRow(panel, getString(R.string.game_td_stats_label_cleared_hard),
                getString(save.isHardCleared() ? R.string.game_td_stats_value_achieved
                        : R.string.game_td_stats_value_not_achieved),
                save.isHardCleared() ? 0xFF9CFFB0 : 0xFF78909C);

        // 成就系统：每个成就一行（名 + 达成状态），tag=td_achv:<枚举名> 供回归测试定位
        addStatsSection(panel, getString(R.string.game_td_achv_section_achievements));
        for (TdAchievement a : TdAchievement.values()) {
            boolean unlocked = save.isAchievementUnlocked(a);
            LinearLayout achvRow = addStatsRow(panel, achvName(a),
                    getString(unlocked ? R.string.game_td_stats_value_achieved
                            : R.string.game_td_stats_value_not_achieved),
                    unlocked ? 0xFF9CFFB0 : 0xFF78909C);
            achvRow.setTag("td_achv:" + a.name());
        }

        // 危险操作：清空全部战绩（必须二次确认）
        Button reset = ctrlButton(ctx, getString(R.string.game_td_stats_btn_reset));
        GradientDrawable rb = new GradientDrawable();
        rb.setColor(0xFFB3261E);
        rb.setCornerRadius(dp(8));
        reset.setBackground(rb);
        reset.setTextColor(0xFFFFFFFF);
        reset.setOnClickListener(v -> showStatsResetConfirm());
        panel.addView(reset, new LinearLayout.LayoutParams(dp(180), dp(40)));
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).topMargin = dp(18);
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        Button back = ctrlButton(ctx, getString(R.string.game_td_stats_btn_back));
        back.setOnClickListener(v -> showLevelSelect());
        panel.addView(back, new LinearLayout.LayoutParams(dp(180), dp(40)));
        ((LinearLayout.LayoutParams) back.getLayoutParams()).topMargin = dp(8);
        ((LinearLayout.LayoutParams) back.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF01E2A1F);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 面板分组标题（战绩总览/战役进度/无尽最佳/难度成就）。 */
    private void addStatsSection(LinearLayout panel, String label) {
        TextView header = new TextView(requireContext());
        header.setText(label);
        header.setTextSize(14);
        header.setTextColor(0xFFCCFFE0);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        lp.topMargin = dp(12);
        panel.addView(header, lp);
    }

    /**
     * 面板数据行：左标签右取值，样式与选关卡片同色系（深底圆角）。
     * 返回该行容器，供调用方挂 tag（成就行 tag=td_achv:*，供回归测试定位）。
     */
    private LinearLayout addStatsRow(LinearLayout panel, String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF35302A);
        bg.setCornerRadius(dp(8));
        row.setBackground(bg);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(0xFFB0BEC5);
        labelView.setSingleLine(true);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(valueColor);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        lp.topMargin = dp(4);
        panel.addView(row, lp);
        return row;
    }

    /** 清空战绩的二次确认对话框（危险操作）：确认后 clearAll 并刷新面板为全零。 */
    private void showStatsResetConfirm() {
        Context ctx = requireContext();
        FrameLayout dim = new FrameLayout(ctx);
        dim.setBackgroundColor(0x99000000);
        // 吃掉背景点击，防止穿透到下层的战绩面板
        dim.setOnClickListener(v -> { });

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20), dp(20), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2A1F);
        bg.setStroke(dp(1), 0xFFFFD54F);
        bg.setCornerRadius(dp(12));
        panel.setBackground(bg);

        TextView title = new TextView(ctx);
        title.setText(getString(R.string.game_td_stats_reset_confirm_title));
        title.setTextSize(18);
        title.setTextColor(0xFFFF8A80);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(ctx);
        body.setText(getString(R.string.game_td_stats_reset_confirm_body));
        body.setTextSize(13);
        body.setTextColor(0xFFFFF8E1);
        body.setLineSpacing(dp(2), 1f);
        body.setGravity(Gravity.CENTER);
        panel.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) body.getLayoutParams()).topMargin = dp(12);

        Button confirm = ctrlButton(ctx, getString(R.string.game_td_stats_reset_confirm_yes));
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0xFFB3261E);
        cb.setCornerRadius(dp(8));
        confirm.setBackground(cb);
        confirm.setTextColor(0xFFFFFFFF);
        confirm.setOnClickListener(v -> {
            save.clearAll();
            // HUD 消息条位于全屏浮层下方；把确认结果放入重建后的面板，确保用户可见。
            showStatsPanel(getString(R.string.game_td_stats_reset_done));
        });
        panel.addView(confirm, new LinearLayout.LayoutParams(dp(200), dp(42)));
        ((LinearLayout.LayoutParams) confirm.getLayoutParams()).topMargin = dp(16);
        ((LinearLayout.LayoutParams) confirm.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        Button cancel = ctrlButton(ctx, getString(R.string.game_td_stats_reset_confirm_no));
        cancel.setOnClickListener(v -> overlayRoot.removeView(dim));
        panel.addView(cancel, new LinearLayout.LayoutParams(dp(200), dp(42)));
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).topMargin = dp(8);
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.CENTER;
        dim.addView(panel, panelLp);
        overlayRoot.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ===== 图鉴浮层（塔与怪属性速查）=====
    // 与战绩面板同构：clearOverlay + ScrollView 长面板 + 返回选关。
    // 数值单一来源：属性行全部实时取自 TowerType/MonsterType 枚举字段格式化；
    // 特性行是纯机制描述（不含数值，避免把引擎常量复制进文案造成双源漂移）。

    /** 主入口：图鉴浮层。打开时对局处于覆盖层暂停态，只读引擎静态枚举，无并发问题。 */
    private void showCodexPanel() {
        clearOverlay();
        Context ctx = requireContext();
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView title = new TextView(ctx);
        title.setText(getString(R.string.game_td_codex_title));
        title.setTextSize(22);
        title.setTextColor(0xFFFFD54F);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView hint = new TextView(ctx);
        hint.setText(getString(R.string.game_td_codex_base_hint));
        hint.setTextSize(11);
        hint.setTextColor(0xFFB0BEC5);
        hint.setGravity(Gravity.CENTER);
        // 提示行自适应高度：EN 文案较长，窄屏折行后固定 24dp 会裁掉第二行
        panel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addStatsSection(panel, getString(R.string.game_td_codex_section_towers));
        for (TowerType t : TowerType.values()) {
            addCodexTowerSection(panel, t);
        }
        addStatsSection(panel, getString(R.string.game_td_codex_section_monsters));
        for (MonsterType m : MonsterType.values()) {
            addCodexMonsterSection(panel, m);
        }

        Button back = ctrlButton(ctx, getString(R.string.game_td_stats_btn_back));
        back.setOnClickListener(v -> showLevelSelect());
        panel.addView(back, new LinearLayout.LayoutParams(dp(180), dp(40)));
        ((LinearLayout.LayoutParams) back.getLayoutParams()).topMargin = dp(16);
        ((LinearLayout.LayoutParams) back.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF01E2A1F);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * 图鉴塔段落：本地化名 + 定位标签（复用塔栏 game_td_role_*）+ 属性行 + 特性行。
     * tag 「td_codex_tower:&lt;枚举名&gt;」供 UI 回归测试按段计数（每种塔恰一段）。
     */
    private void addCodexTowerSection(LinearLayout panel, TowerType t) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("td_codex_tower:" + t.name());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A2318);
        bg.setStroke(dp(1), 0xFF4A4238);
        bg.setCornerRadius(dp(10));
        section.setBackground(bg);
        section.setPadding(dp(12), dp(8), dp(12), dp(10));

        // 头行：程序化小模型 + 本地化名 + 定位标签
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new TowerGlyphView(requireContext(), t),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView name = new TextView(requireContext());
        name.setText(towerName(t));
        name.setTextSize(14);
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = dp(6);
        header.addView(name, nameLp);
        TextView role = new TextView(requireContext());
        role.setText(towerRole(t));
        role.setTextSize(10);
        role.setTextColor(0xFFB8E9D0);
        role.setSingleLine(true);
        header.addView(role, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 属性行：造价恒有；伤害/射程/攻速/对空仅攻击塔展示（非攻击塔 damage=0 是合法语义）
        addStatsRow(section, getString(R.string.game_td_codex_label_cost),
                String.format(Locale.US, "₿%d", t.baseCost), 0xFFFFD54F);
        if (t.damage > 0f) {
            // 攻速口径：直接展示引擎开火间隔 fireInterval（秒），不做「次/秒」倒数换算
            addStatsRow(section, getString(R.string.game_td_codex_label_damage),
                    codexNum(t.damage), 0xFFFFF8E1);
            if (t.directHitMultiplier < 1f) {
                // 直伤系数低于 1 的塔（SNOW/FAN/POISON/ROCKET）：补一行有效直伤，
                // 口径 = 单发伤害 × TowerType.directHitMultiplier（与 TdGame.fire() 同源）
                addStatsRow(section, getString(R.string.game_td_codex_label_direct_damage),
                        getString(R.string.game_td_codex_value_direct_damage,
                                codexNum(t.damage), codexNum(t.directHitMultiplier),
                                codexNum(t.damage * t.directHitMultiplier)), 0xFFFFF8E1);
            }
            if (t == TowerType.POISON) {
                // 毒泡泡直伤行后补一行毒伤持续伤害：数值取引擎常量 POISON_DPS/POISON_SEC
                // （与 TdGame 毒 DOT 同源）；插桩方式与特性行一致（单串整行），仅毒泡泡展示
                addCodexTraitLine(section, getString(R.string.game_td_codex_value_poison_dot,
                        codexNum(TowerType.POISON_DPS), codexNum(TowerType.POISON_SEC)));
            }
            addStatsRow(section, getString(R.string.game_td_codex_label_range),
                    codexNum(t.range), 0xFFFFF8E1);
            addStatsRow(section, getString(R.string.game_td_codex_label_rate),
                    getString(R.string.game_td_codex_value_fire_interval, codexNum(t.fireInterval)),
                    0xFFFFF8E1);
            addStatsRow(section, getString(R.string.game_td_codex_label_anti_air),
                    t.canAir ? "✓" : "✗", t.canAir ? 0xFF9CFFB0 : 0xFF78909C);
        } else if (t == TowerType.SUN) {
            // 收益口径：单次产币金额与产币周期（周期已提为 TowerType.incomeIntervalSec 数据源，
            // 与 TdGame.updateTowers 同源展示）；₿ 符号与塔栏造价同源
            addStatsRow(section, getString(R.string.game_td_codex_label_income),
                    getString(R.string.game_td_codex_value_income,
                            codexNum(t.incomeIntervalSec), Math.round(t.income)), 0xFFFFD54F);
        } else if (t == TowerType.AMPLIFIER) {
            // 增幅塔的 range 字段即光环半径
            addStatsRow(section, getString(R.string.game_td_codex_label_range),
                    codexNum(t.range), 0xFFB8E9D0);
        }
        addCodexTraitLine(section, codexTowerTrait(t));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        panel.addView(section, lp);
    }

    /**
     * 图鉴怪段落：本地化名 + 属性行（生命/速度/护甲/赏金/漏蛋伤害）+ 特性行。
     * 飞行信息由 FLY 的特性行承载（「飞行：仅可对空塔能攻击」），不再单列属性行。
     * tag 「td_codex_monster:&lt;枚举名&gt;」供 UI 回归测试按段计数。
     */
    private void addCodexMonsterSection(LinearLayout panel, MonsterType m) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("td_codex_monster:" + m.name());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A2318);
        bg.setStroke(dp(1), 0xFF4A4238);
        bg.setCornerRadius(dp(10));
        section.setBackground(bg);
        section.setPadding(dp(12), dp(8), dp(12), dp(10));

        TextView name = new TextView(requireContext());
        name.setText(monsterName(m));
        name.setTextSize(14);
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        section.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addStatsRow(section, getString(R.string.game_td_codex_label_hp),
                codexNum(m.hp), 0xFFFFF8E1);
        addStatsRow(section, getString(R.string.game_td_codex_label_speed),
                getString(R.string.game_td_codex_value_speed, codexNum(m.speed)), 0xFFFFF8E1);
        // 护甲 0 是「无减伤」的合法语义：直接展示 0，不套用减伤格式
        addStatsRow(section, getString(R.string.game_td_codex_label_armor),
                m.armor > 0 ? getString(R.string.game_td_codex_value_armor, m.armor) : "0",
                m.armor > 0 ? 0xFFFF8A80 : 0xFF78909C);
        addStatsRow(section, getString(R.string.game_td_codex_label_bounty),
                String.format(Locale.US, "₿%d", m.value), 0xFFFFD54F);
        addStatsRow(section, getString(R.string.game_td_codex_label_leak),
                String.format(Locale.US, "-%d", m.leakDamage), 0xFFFF8A80);
        addCodexTraitLine(section, codexMonsterTrait(m));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        panel.addView(section, lp);
    }

    /** 特性描述行：tag 「td_codex_trait」供测试锁定 switch 全枚举覆盖（漏 case 会落空串）。 */
    private void addCodexTraitLine(LinearLayout section, String trait) {
        TextView traitView = new TextView(requireContext());
        traitView.setText(trait);
        traitView.setTextSize(11);
        traitView.setTextColor(0xFFB8E9D0);
        traitView.setTag("td_codex_trait");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        section.addView(traitView, lp);
    }

    /** 图鉴塔特性行文案（game_td_codex_trait_tower_*）。新增 TowerType 必须补 case；
     *  default 落空串，由 TdUiRegressionTest 的图鉴冒烟（特性行非空）拦截。 */
    private String codexTowerTrait(TowerType t) {
        switch (t) {
            case BOTTLE: return getString(R.string.game_td_codex_trait_tower_bottle);
            case SUN: return getString(R.string.game_td_codex_trait_tower_sun);
            case SNOW: return getString(R.string.game_td_codex_trait_tower_snow);
            case FAN: return getString(R.string.game_td_codex_trait_tower_fan);
            case POISON: return getString(R.string.game_td_codex_trait_tower_poison);
            case ROCKET: return getString(R.string.game_td_codex_trait_tower_rocket);
            case LIGHTNING: return getString(R.string.game_td_codex_trait_tower_lightning);
            case SNIPER: return getString(R.string.game_td_codex_trait_tower_sniper);
            case MINE: return getString(R.string.game_td_codex_trait_tower_mine);
            case AMPLIFIER: return getString(R.string.game_td_codex_trait_tower_amplifier);
            default: return "";
        }
    }

    /** 图鉴怪特性行文案（game_td_codex_trait_mon_*）。NORMAL/TANK/SWARM/BOSS 无特殊机制，
     *  显式共用 generic；与塔侧 codexTowerTrait 对称：新增特殊怪必须补 case，
     *  default 落空串，由 TdUiRegressionTest 的图鉴冒烟（特性行非空）拦截。 */
    private String codexMonsterTrait(MonsterType m) {
        switch (m) {
            case FAST: return getString(R.string.game_td_codex_trait_mon_fast);
            case FLY: return getString(R.string.game_td_codex_trait_mon_fly);
            case HEALER: return getString(R.string.game_td_codex_trait_mon_healer);
            case SHIELD: return getString(R.string.game_td_codex_trait_mon_shield);
            case SPLITTER: return getString(R.string.game_td_codex_trait_mon_splitter);
            case CHARGER: return getString(R.string.game_td_codex_trait_mon_charger);
            case SHIELD_GENERATOR: return getString(R.string.game_td_codex_trait_mon_shield_generator);
            case SUMMONER: return getString(R.string.game_td_codex_trait_mon_summoner);
            case RESISTANT: return getString(R.string.game_td_codex_trait_mon_resistant);
            case RAGER: return getString(R.string.game_td_codex_trait_mon_rager);
            case NORMAL:
            case TANK:
            case SWARM:
            case BOSS: return getString(R.string.game_td_codex_trait_mon_generic);
            default: return "";
        }
    }

    /** 图鉴数值口径：整数去尾、非整最多两位小数；Locale.US 与引擎内部格式一致，避免小数点本地化歧义。 */
    private static String codexNum(float v) {
        String s = String.format(Locale.US, "%.2f", v);
        if (s.endsWith(".00")) return s.substring(0, s.length() - 3);
        if (s.endsWith("0")) return s.substring(0, s.length() - 1);
        return s;
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
        startLevel(idx, diff, TdGame.Mode.CAMPAIGN);
    }

    /**
     * mode=ENDLESS 时复用所选关卡的地图/路线/蛋位与初始金币、蛋生命（难度同源规则，
     * 与战役共用 applyDifficulty 的金币倍率链），仅波次耗尽后由工厂无限合成。
     */
    private void startLevel(int idx, TdGame.Difficulty diff, TdGame.Mode mode) {
        mergeSource = null;
        gameSession++;
        paused = false;
        gameEnded = false;
        killsRecordedForSession = false;
        selectedLevelIdx = idx;
        selectedMode = mode != null ? mode : TdGame.Mode.CAMPAIGN;
        btnSpeed.setText("⏸");
        btnNextWave.setText(getString(R.string.game_td_btn_fight));
        game = TdLevels.buildLevel(TdLevels.levelIds().get(idx), selectedMode);
        game.applyDifficulty(diff);
        // Every newly created session counts once, including settlement retry/next level.
        // Opening a menu or dismissing this session's story does not create another game.
        save.recordPlay();
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
        // 剧情模式：战役开局且本关有引子故事时弹故事面板；面板打开期间 tick 自动暂停，
        // 玩家点「出战」后关闭面板进入布防。无尽模式不讲故事，重玩同关再看一遍可直接出战。
        if (selectedMode == TdGame.Mode.CAMPAIGN) {
            String storyLevelId = TdLevels.levelIds().get(idx);
            String story = TdLevels.levelStoryIntro(storyLevelId);
            if (story != null && !story.isEmpty()) {
                showStoryIntro(storyLevelId, story);
            }
        }
    }

    /**
     * 开战前故事面板：标题 + 关名 + 故事正文 + 出战按钮。打开即暂停战斗
     * （tickOnce 检测到 overlay 有子视图会自动停怪），点出战关闭后面板继续布防。
     */
    private void showStoryIntro(String levelId, String story) {
        clearOverlay();
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xE61E2A1F);
        panel.setPadding(dp(24), dp(30), dp(24), dp(20));

        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.game_td_story_title));
        title.setTextSize(20);
        title.setTextColor(0xFFFFC107);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView name = new TextView(requireContext());
        name.setText(TdLevels.levelDisplayName(selectedLevelIdx, levelId));
        name.setTextSize(16);
        name.setTextColor(0xFFFFF8E1);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        panel.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        ScrollView scroll = new ScrollView(requireContext());
        TextView body = new TextView(requireContext());
        body.setTag("td_story_intro");
        body.setText(story);
        body.setTextSize(15);
        body.setTextColor(0xFFDCEDC8);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(4), 1f);
        scroll.addView(body);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        scrollParams.topMargin = dp(8);
        panel.addView(scroll, scrollParams);

        Button start = ctrlButton(requireContext(), getString(R.string.game_td_story_start));
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF2E9E4F);
        gb.setCornerRadius(dp(10));
        start.setBackground(gb);
        start.setTextColor(0xFFFFFFFF);
        start.setOnClickListener(v -> clearOverlay());
        panel.addView(start, new LinearLayout.LayoutParams(dp(160), dp(44)));
        ((LinearLayout.LayoutParams) start.getLayoutParams()).topMargin = dp(14);
        ((LinearLayout.LayoutParams) start.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        overlayRoot.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void restartLevel(int idx) {
        mainHandler.removeCallbacks(tickLoop);
        // 对局中 ◀/▶ 换关同属放弃局（总局数已在开局 recordPlay 计入）：
        // 旧局击杀先行入账；结算面板「重玩」走同一入口时 gameEnded 闸自动跳过。
        recordAbandonedKills();
        startLevel(idx, game != null ? game.getDifficulty() : TdGame.Difficulty.NORMAL,
                selectedMode);
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
        // 击杀与输赢无关：胜/无尽/败任一结局在此统一计一次（修复前仅 WON 分支计入，
        // 败局与无尽局的击杀全部丢失，KILLS 型成就也永远算不到败局击杀）。gameEnded 闸
        // 保证每局至多进入一次本方法，重开对局由 startLevel 复位，不会重复累加；KILLS 型
        // 成就由 addKills 内部写入点判定，败局/无尽击杀跨阈值同样即时解锁，并经各分支
        // 既有的 drainAchievementLine 随本局结算面板提示。
        save.addKills(game.getMonstersKilled());
        if (game.getState() == TdGame.State.WON) {
            int stars = game.starsEarned();
            int unlockedBefore = save.getUnlockedLevelCount();
            String levelId = TdLevels.levelIds().get(selectedLevelIdx);
            save.recordCampaignWin(levelId, game.getDifficulty(), (int) game.getElapsedSeconds());
            List<TowerType> newlyUnlocked = TdTowerProgression.newlyUnlockedBetween(
                    unlockedBefore, save.getUnlockedLevelCount());
            save.setBestStars(levelId, stars);
            String resultStats = getString(R.string.game_td_result_stats,
                    game.getMonstersKilled(), (int) game.getElapsedSeconds());
            if (!newlyUnlocked.isEmpty()) {
                resultStats += getString(R.string.game_td_result_new_towers, towerNames(newlyUnlocked));
            }
            showResult(getString(R.string.game_td_result_win),
                    getString(R.string.game_td_result_stars, starsText(stars)),
                    resultStats, drainAchievementLine(), 0xFF66BB6A);
        } else if (game.getMode() == TdGame.Mode.ENDLESS) {
            // 无尽结算：唯一结束方式是蛋死亡；记录按难度的最佳波数（只增不减），不显示战役星级
            int waves = game.getEndlessWaveReached();
            boolean newRecord = waves > save.getBestEndlessWaves(game.getDifficulty());
            save.recordEndlessWaves(game.getDifficulty(), waves);
            String endlessStats = getString(R.string.game_td_result_endless, waves)
                    + "  ·  " + getString(R.string.game_td_result_stats,
                            game.getMonstersKilled(), (int) game.getElapsedSeconds());
            showResult(getString(R.string.game_td_result_lose),
                    newRecord ? getString(R.string.game_td_result_endless_best) : "",
                    endlessStats, drainAchievementLine(), 0xFFE57373);
        } else {
            // 战役失败分支无结算写入点，但战绩面板 sync 补漏可能留有待提示成就，仍需一次性取走
            showResult(getString(R.string.game_td_result_lose), "",
                    getString(R.string.game_td_result_lose_stats,
                            game.getWaveIndex(), game.getMonstersKilled()),
                    drainAchievementLine(), 0xFFE57373);
        }
    }

    /**
     * 放弃局击杀入账：对局进行中（未走过结算）离开对局——点「选关」返回、对局中 ◀/▶
     * 换关、退出大厅确认后销毁——时，本局已发生的击杀计入总击杀。开局选塔时 recordPlay
     * 已让总局数 +1，而放弃局不经过 onGameEnded（三结局 WON/无尽死亡/战役败专属），
     * 不在此补记则该局击杀整体丢失（玩家直觉：杀了的怪就该算）。
     *
     * <p>防双计：同一局游戏实例可能多次走到放弃路径（选关 → 战绩/图鉴 → 再返回选关；
     * 先选关后销毁），由 {@link #killsRecordedForSession}（startLevel 开新局复位）保证
     * 每局只落账一次；已结算局 gameEnded=true 直接跳过，不与 onGameEnded 的 addKills
     * 重复累加（三结局路径零改动）。
     *
     * <p>成就提示取舍（方案 a，静默）：addKills 内部写入点会把跨阈值解锁（如 KILLS_100）
     * 记入存档布尔键并入 pendingUnlocks，放弃路径不弹提示——选关浮层 z 序高于 HUD，
     * tvMsg 弹条会被遮挡不可读（与结算面板成就行下沉的教训同理）。pendingUnlocks 滞留
     * 语义可接受：成就状态真源是存档布尔键，滞留项由下一次结算面板 drainAchievementLine
     * 顺带带出（unlockAchievements 对已解锁键幂等跳过，不复活不重复），或经战绩面板
     * syncAchievementsFromState 在成就列表可见；clearAll 同步作废 pendingUnlocks，
     * 清空战绩后不会弹出积压提示。
     */
    private void recordAbandonedKills() {
        if (game == null || gameEnded || killsRecordedForSession) return;
        killsRecordedForSession = true;
        save.addKills(game.getMonstersKilled());
    }

    /**
     * 取走结算写入点（recordWin/addKills/recordEndlessWaves/setEasyHardCleared，含战绩面板
     * sync 补漏）新解锁的成就，合并为结算面板附加行文案（game_td_achv_unlocked，多枚合并、
     * 分隔符随 locale）；无新解锁返回空串（面板不占行）。
     *
     * <p>必须在各结算分支写入点之后、showResult 之前调用并传参：成就提示随结算面板行展示，
     * 不再走 tvMsg 弹条——tvMsg 在 HUD 列，z 序低于 0xE6 全屏结算浮层，弹条在浮层弹出瞬间
     * 不可读。成就状态真源在存档，提示错过不补发。
     */
    private String drainAchievementLine() {
        List<TdAchievement> newly = save.drainNewlyUnlockedAchievements();
        if (newly.isEmpty()) return "";
        StringBuilder names = new StringBuilder();
        for (TdAchievement a : newly) {
            if (names.length() > 0) names.append(getString(R.string.game_td_list_separator));
            names.append(achvName(a));
        }
        return getString(R.string.game_td_achv_unlocked, names.toString());
    }

    /** 成就本地化名（game_td_achv_name_*）。新增 TdAchievement 必须补 case：default 只回落
     * saveKey 保底可见性（TdUiRegressionTest 逐枚举断言名 ≠ saveKey 拦截漏 case）；与图鉴特性行同约定。 */
    private String achvName(TdAchievement a) {
        switch (a) {
            case FIRST_WIN: return getString(R.string.game_td_achv_name_first_win);
            case CHAPTER1_CLEARED: return getString(R.string.game_td_achv_name_ch1_cleared);
            case CHAPTER2_CLEARED: return getString(R.string.game_td_achv_name_ch2_cleared);
            case CHAPTER3_CLEARED: return getString(R.string.game_td_achv_name_ch3_cleared);
            case CHAPTER4_CLEARED: return getString(R.string.game_td_achv_name_ch4_cleared);
            case CHAPTER5_CLEARED: return getString(R.string.game_td_achv_name_ch5_cleared);
            case CHAPTER6_CLEARED: return getString(R.string.game_td_achv_name_ch6_cleared);
            case CHAPTER7_CLEARED: return getString(R.string.game_td_achv_name_ch7_cleared);
            case CHAPTER8_CLEARED: return getString(R.string.game_td_achv_name_ch8_cleared);
            case CHAPTER9_CLEARED: return getString(R.string.game_td_achv_name_ch9_cleared);
            case CHAPTER10_CLEARED: return getString(R.string.game_td_achv_name_ch10_cleared);
            case CHAPTER11_CLEARED: return getString(R.string.game_td_achv_name_ch11_cleared);
            case CHAPTER12_CLEARED: return getString(R.string.game_td_achv_name_ch12_cleared);
            case CHAPTER13_CLEARED: return getString(R.string.game_td_achv_name_ch13_cleared);
            case KILLS_100: return getString(R.string.game_td_achv_name_kills_100);
            case KILLS_1000: return getString(R.string.game_td_achv_name_kills_1000);
            case ENDLESS_10: return getString(R.string.game_td_achv_name_endless_10);
            case ENDLESS_25: return getString(R.string.game_td_achv_name_endless_25);
            case DUAL_CROWN: return getString(R.string.game_td_achv_name_dual_crown);
            default: return a.saveKey;
        }
    }

    private void showResult(String title, String line2, String line3, String achievementLine,
            int color) {
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

        // 成就解锁附加行（星级/统计行之后）：随面板展示，避免 tvMsg 弹条被本浮层遮挡；
        // 文案来自 drainAchievementLine（game_td_achv_unlocked），空串不占行
        if (!achievementLine.isEmpty()) {
            TextView achv = new TextView(requireContext());
            achv.setText(achievementLine);
            achv.setTextSize(13);
            achv.setTextColor(0xFFFFC107);
            achv.setGravity(Gravity.CENTER);
            achv.setMaxLines(2);
            panel.addView(achv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // 胜利尾声：战役胜利且本关有尾声故事时在结算面板加一段余波，供回归测试定位。
        boolean won = game.getState() == TdGame.State.WON;
        if (won && selectedMode == TdGame.Mode.CAMPAIGN
                && selectedLevelIdx >= 0 && selectedLevelIdx < TdLevels.levelIds().size()) {
            String outro = TdLevels.levelStoryOutro(TdLevels.levelIds().get(selectedLevelIdx));
            if (outro != null && !outro.isEmpty()) {
                TextView outroTitle = new TextView(requireContext());
                outroTitle.setText(getString(R.string.game_td_story_outro_title));
                outroTitle.setTextSize(13);
                outroTitle.setTextColor(0xFFFFC107);
                outroTitle.setTypeface(Typeface.DEFAULT_BOLD);
                outroTitle.setGravity(Gravity.CENTER);
                panel.addView(outroTitle, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView outroBody = new TextView(requireContext());
                outroBody.setTag("td_story_outro");
                outroBody.setText(outro);
                outroBody.setTextSize(13);
                outroBody.setTextColor(0xFFDCEDC8);
                outroBody.setGravity(Gravity.CENTER);
                outroBody.setMaxLines(4);
                panel.addView(outroBody, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        if (won && selectedLevelIdx + 1 < TdLevels.levelIds().size()) {
            Button next = ctrlButton(requireContext(), getString(R.string.game_td_btn_next_level));
            GradientDrawable gb = new GradientDrawable();
            gb.setColor(0xFF2E9E4F);
            gb.setCornerRadius(dp(10));
            next.setBackground(gb);
            next.setTextColor(0xFFFFFFFF);
            final int nIdx = selectedLevelIdx + 1;
            next.setOnClickListener(v -> {
                TdGame.Difficulty difficulty = game.getDifficulty();
                showDeckSelect(nIdx, difficulty);
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
        if (game.getMode() == TdGame.Mode.ENDLESS) {
            // 无尽模式没有总波数概念：只显示已推进到的绝对波次；准备期钳制为第 1 波，避免「第 0 波」
            tvWave.setText(getString(R.string.game_td_hud_endless_wave,
                    Math.max(1, game.getEndlessWaveReached()))
                    + (game.getState() == TdGame.State.PREPARING
                            ? " " + getString(R.string.game_td_hud_wave_preparing) : ""));
        } else {
            tvWave.setText(getString(R.string.game_td_hud_wave, game.getWaveIndex(), game.getTotalWaves())
                    + (game.getState() == TdGame.State.PREPARING
                            ? " " + getString(R.string.game_td_hud_wave_preparing) : ""));
        }
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
        mergeSource = null;
        // 退出大厅（宿主确认框→finish）与回退栈替换不走 showLevelSelect，销毁即放弃局：
        // 击杀先行入账。killsRecordedForSession 防与选关路径双计（先选关后销毁），
        // gameEnded 闸跳过已结算局。
        recordAbandonedKills();
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
