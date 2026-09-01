package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.score.ScoreBoard;

import java.util.List;

/**
 * 斗地主单机牌桌视图。
 *
 * <p>纵向两段布局：上方是牌桌区（{@link DouDiZhuTableView} 自绘 +
 * {@link DouDiZhuEffectsView} 特效覆盖层，weight=1 占满剩余高度），
 * 下方是固定高度的操作条（出牌/不出/提示 或 不叫/1分/2分/3分）。
 * 操作条不再悬浮覆盖手牌，手牌完整可见。</p>
 *
 * <p>P3 对局流程：叫分制三档（不叫/1分/2分/3分）；操作条左端常驻倍数 HUD
 * （出牌阶段显示总倍数，叫分阶段显示当前最高叫分）；对局结束弹出结算明细
 * （胜负/地主/叫分/炸弹/春天/倍数/得分/耗时），并回调
 * {@link SettlementListener} 由宿主 Fragment 上报成绩与清档。</p>
 *
 * <p>本视图不持有游戏逻辑：全部操作委托 {@link DoudizhuGameController}，
 * 并通过其 UiCallback 接收回推（按钮显隐/全量同步/结算弹窗）。</p>
 */
public class DoudizhuGameScreen extends LinearLayout implements DoudizhuGameController.UiCallback {

    /** 退出牌桌（返回菜单）回调，由宿主 Fragment 实现 */
    public interface ExitListener {
        void onExitRequested();
    }

    /** 结算上报回调：弹窗展示结算明细后由宿主记录成绩并清档 */
    public interface SettlementListener {
        void onSettled(ScoreBoard.Settlement settlement);
    }

    private final DoudizhuGameController controller;
    private final DouDiZhuTableView tableView;
    private final DouDiZhuEffectsView effectsView;

    private final LinearLayout playBar;
    private final LinearLayout bidBar;
    private final Button btnHint;
    private final Button btnPass;
    private final Button btnPlay;
    private final Button btnBidPass;
    private final Button btnBid1;
    private final Button btnBid2;
    private final Button btnBid3;
    private final TextView hudText;
    /** 操作条上方行内提示（P4：非法出牌/叫分等反馈不再用 Toast 打断） */
    private final TextView inlineMessage;

    private final ExitListener exitListener;
    private final SettlementListener settlementListener;

    /** 行内提示展示时长（毫秒） */
    private static final long INLINE_MESSAGE_DURATION_MS = 2500L;

    public DoudizhuGameScreen(@NonNull Context context, @NonNull DoudizhuGameController controller,
                              ExitListener exitListener, SettlementListener settlementListener) {
        super(context);
        this.controller = controller;
        this.exitListener = exitListener;
        this.settlementListener = settlementListener;

        setOrientation(VERTICAL);
        // P4 暗色适配：亮色传统毛毡绿，暗色更低亮度背景（对齐 TableView 背景）
        boolean night = isNightMode(context);
        setBackgroundColor(night ? 0xFF081B07 : 0xFF0A3D12);

        // ===== 牌桌区：TableView + 特效覆盖 + 退出按钮 =====
        FrameLayout tableArea = new FrameLayout(context);
        addView(tableArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tableView = new DouDiZhuTableView(context);
        tableArea.addView(tableView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        effectsView = new DouDiZhuEffectsView(context);
        tableArea.addView(effectsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 退出按钮：放回牌桌区右上角（P4 布局修正：操作条空间紧张，
        // 顶角按钮保证不与状态栏/导航栏重叠；横屏时状态栏在系统栏 inset 内）
        Button btnExitTop = new Button(context);
        btnExitTop.setText("✕");
        btnExitTop.setTextSize(16);
        btnExitTop.setTextColor(Color.WHITE);
        btnExitTop.setAllCaps(false);
        btnExitTop.setPadding(0, 0, 0, 0);
        btnExitTop.setBackground(pillBackground(night ? 0xFF37474F : 0xFF455A64));
        btnExitTop.setContentDescription(context.getString(R.string.game_doudizhu_exit));
        int exitMarginTop = dp(40); // 让出顶部底牌/记牌器区域
        int exitMarginEnd = dp(10);
        FrameLayout.LayoutParams exitLp = new FrameLayout.LayoutParams(
                dp(40), dp(40), Gravity.TOP | Gravity.END);
        exitLp.setMargins(0, exitMarginTop, exitMarginEnd, 0);
        tableArea.addView(btnExitTop, exitLp);
        btnExitTop.setOnClickListener(v -> confirmExit());

        // ===== 操作条（固定高度，不覆盖手牌；左端 HUD、右端退出按钮） =====
        int barHeight = dp(64);

        btnHint = makeBarButton(context, R.string.game_doudizhu_btn_hint, false);
        btnPass = makeBarButton(context, R.string.game_doudizhu_btn_pass, false);
        btnPlay = makeBarButton(context, R.string.game_doudizhu_btn_play, true);

        playBar = new LinearLayout(context);
        playBar.setOrientation(LinearLayout.HORIZONTAL);
        playBar.setGravity(Gravity.CENTER);
        playBar.addView(btnHint);
        playBar.addView(btnPass);
        playBar.addView(btnPlay);

        btnBidPass = makeBarButton(context, R.string.game_doudizhu_bid_pass, false, dp(72));
        btnBid1 = makeBarButton(context, R.string.game_ddz_bid_score_1, false, dp(72));
        btnBid2 = makeBarButton(context, R.string.game_ddz_bid_score_2, false, dp(72));
        btnBid3 = makeBarButton(context, R.string.game_ddz_bid_score_3, true, dp(72));

        bidBar = new LinearLayout(context);
        bidBar.setOrientation(LinearLayout.HORIZONTAL);
        bidBar.setGravity(Gravity.CENTER);
        bidBar.addView(btnBidPass);
        bidBar.addView(btnBid1);
        bidBar.addView(btnBid2);
        bidBar.addView(btnBid3);
        bidBar.setVisibility(GONE);

        hudText = new TextView(context);
        hudText.setTextSize(14);
        hudText.setTextColor(Color.WHITE);
        hudText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        hudText.setShadowLayer(2f, 1f, 1f, 0xFF000000);

        FrameLayout barArea = new FrameLayout(context);
        barArea.addView(playBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        barArea.addView(bidBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL | Gravity.START);
        hudLp.setMargins(dp(12), 0, 0, 0);
        barArea.addView(hudText, hudLp);

        // ===== 行内提示行（固定高度，位于操作条上方；P4 替代 Toast） =====
        inlineMessage = new TextView(context);
        inlineMessage.setTextSize(13);
        inlineMessage.setTextColor(Color.WHITE);
        inlineMessage.setGravity(Gravity.CENTER);
        inlineMessage.setSingleLine(true);
        inlineMessage.setShadowLayer(2f, 1f, 1f, 0xFF000000);
        LinearLayout msgRow = new LinearLayout(context);
        msgRow.setOrientation(LinearLayout.HORIZONTAL);
        msgRow.setGravity(Gravity.CENTER);
        msgRow.addView(inlineMessage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(msgRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        addView(barArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barHeight));

        btnPlay.setOnClickListener(v -> {
            List<Card> selected = tableView.getSelectedCards();
            if (selected == null || selected.isEmpty()) {
                showInline(R.string.game_doudizhu_select_cards);
                return;
            }
            controller.onHumanPlay(selected);
        });
        btnPass.setOnClickListener(v -> controller.onHumanPass());
        btnHint.setOnClickListener(v -> {
            List<Card> hint = controller.nextHint();
            if (hint != null && !hint.isEmpty()) {
                tableView.selectCards(hint);
                showInline(R.string.game_ddz_hint_selected);
            } else {
                showInline(R.string.game_doudizhu_cannot_beat);
            }
        });
        btnBidPass.setOnClickListener(v -> controller.onHumanBid(0));
        btnBid1.setOnClickListener(v -> controller.onHumanBid(1));
        btnBid2.setOnClickListener(v -> controller.onHumanBid(2));
        btnBid3.setOnClickListener(v -> controller.onHumanBid(3));

        controller.attachUi(this);
    }

    // ============ 行内提示 ============

    /** 行内提示自动清除任务（在 inlineMessage 初始化后创建，View.postDelayed 调度）。 */
    private Runnable inlineClearTask;

    /** 在操作条上方显示行内提示，2.5 秒后自动清除；再次显示时重置计时。 */
    private void showInline(int textRes) {
        showInlineText(getContext().getString(textRes));
    }

    /** 显示已格式化的行内提示文本，2.5 秒后自动清除。 */
    private void showInlineText(CharSequence text) {
        if (inlineClearTask == null) {
            inlineClearTask = () -> inlineMessage.setVisibility(GONE);
        }
        removeCallbacks(inlineClearTask);
        inlineMessage.setText(text);
        inlineMessage.setVisibility(VISIBLE);
        postDelayed(inlineClearTask, INLINE_MESSAGE_DURATION_MS);
    }

    // ============ UiCallback（控制器回推） ============

    @Override
    public void onBidControlsChanged(boolean show) {
        bidBar.setVisibility(show ? VISIBLE : GONE);
    }

    @Override
    public void onPlayControlsChanged(boolean show, boolean enablePass) {
        playBar.setVisibility(show ? VISIBLE : GONE);
        btnPlay.setEnabled(show);
        btnHint.setEnabled(show);
        btnPass.setEnabled(enablePass);
    }

    @Override
    public void onTableSyncRequired() {
        controller.pushTableState(tableView);
        updateHud();
    }

    /** 倍数 HUD：出牌阶段显示总倍数；叫分阶段显示当前最高叫分。 */
    private void updateHud() {
        Context ctx = getContext();
        if (controller.isInGame()
                && controller.state().getGameState() == DouDiZhuGameStateManager.STATE_BIDDING) {
            int highest = controller.getHighestBid();
            hudText.setText(highest > 0
                    ? ctx.getString(R.string.game_ddz_hud_highest_bid, highest)
                    : ctx.getString(R.string.game_ddz_hud_multiplier, 1));
        } else {
            hudText.setText(ctx.getString(R.string.game_ddz_hud_multiplier,
                    controller.getMultiplier()));
        }
    }

    @Override
    public void onCardsPlayed(List<Card> cards, CardType type) {
        if (type == CardType.BOMB) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.BOMB,
                    getWidth() / 2f, getHeight() * 0.35f);
        } else if (type == CardType.JOKER_BOMB) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.ROCKET,
                    getWidth() / 2f, getHeight() * 0.35f);
        } else if (type == CardType.AIRPLANE || type == CardType.AIRPLANE_WITH_WINGS) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.PLANE,
                    getWidth() / 2f, getHeight() * 0.35f);
        }
    }

    @Override
    public void onInvalidPlay(boolean illegalCombo, boolean cannotBeat) {
        showInline(illegalCombo ? R.string.game_doudizhu_invalid_card_type
                : R.string.game_doudizhu_cannot_beat);
    }

    @Override
    public void onInvalidBid() {
        showInline(R.string.game_doudizhu_must_bid_higher);
    }

    @Override
    public void onRedeal(int redealCount) {
        showInlineText(getContext().getString(R.string.game_ddz_redeal_notice, redealCount));
    }

    @Override
    public void onForcedLandlord() {
        showInline(R.string.game_ddz_forced_landlord);
    }

    @Override
    public void onGameFinished(ScoreBoard.Settlement settlement) {
        if (settlementListener != null) {
            settlementListener.onSettled(settlement);
        }
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.game_doudizhu_game_over)
                .setMessage(buildSettlementMessage(settlement))
                .setCancelable(false)
                .setPositiveButton(R.string.game_doudizhu_play_again, (d, w) ->
                        controller.startNewGame(controller.getDifficulty()))
                .setNegativeButton(R.string.game_doudizhu_exit, (d, w) -> {
                    if (exitListener != null) exitListener.onExitRequested();
                })
                .show();
    }

    /** 结算明细：胜负结论 + 地主/叫分/炸弹/春天/倍数/得分/耗时逐行展示。 */
    private String buildSettlementMessage(ScoreBoard.Settlement s) {
        Context ctx = getContext();
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getString(s.humanWon()
                ? R.string.game_doudizhu_you_win : R.string.game_doudizhu_you_lose))
                .append("（").append(ctx.getString(s.landlordWon
                ? R.string.game_ddz_settle_landlord_win : R.string.game_ddz_settle_farmer_win))
                .append("）\n");
        sb.append(ctx.getString(R.string.game_ddz_settle_landlord, seatName(s.landlordSeat)))
                .append("\n");
        sb.append(ctx.getString(R.string.game_ddz_settle_bid, s.bidScore)).append("\n");
        sb.append(ctx.getString(R.string.game_ddz_settle_bombs, s.bombCount)).append("\n");
        if (s.spring) {
            sb.append(ctx.getString(R.string.game_ddz_settle_spring)).append("\n");
        } else if (s.antiSpring) {
            sb.append(ctx.getString(R.string.game_ddz_settle_anti_spring)).append("\n");
        }
        sb.append(ctx.getString(R.string.game_ddz_settle_multiplier, s.multiplier)).append("\n");
        sb.append(ctx.getString(R.string.game_ddz_settle_score,
                formatScoreDelta(s.humanScoreDelta))).append("\n");
        sb.append(ctx.getString(R.string.game_ddz_settle_duration, s.formatDuration()));
        return sb.toString();
    }

    /** 座位名（复用既有宿主 key：你/左AI/右AI）。 */
    private String seatName(int seat) {
        Context ctx = getContext();
        switch (seat) {
            case Seats.SEAT_PLAYER: return ctx.getString(R.string.game_doudizhu_player_you);
            case Seats.SEAT_LEFT_AI: return ctx.getString(R.string.game_doudizhu_player_left_ai);
            default: return ctx.getString(R.string.game_doudizhu_player_right_ai);
        }
    }

    /** 得分变动格式化：正数带 + 号。 */
    private static String formatScoreDelta(int delta) {
        return delta >= 0 ? "+" + delta : String.valueOf(delta);
    }

    // ============ 内部 ============

    /** 当前是否暗色模式（P4 暗色主题适配，对齐 ModuleFragment 菜单页口径）。 */
    private static boolean isNightMode(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void confirmExit() {
        new android.app.AlertDialog.Builder(getContext())
                .setMessage(R.string.game_ddz_exit_confirm)
                .setPositiveButton(R.string.game_doudizhu_exit, (d, w) -> {
                    if (exitListener != null) exitListener.onExitRequested();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Button makeBarButton(Context context, int textRes, boolean primary) {
        return makeBarButton(context, textRes, primary, dp(88));
    }

    private Button makeBarButton(Context context, int textRes, boolean primary, int widthDp) {
        Button btn = new Button(context);
        btn.setText(textRes);
        btn.setTextSize(16);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(pillBackground(primary ? 0xFF6200EE : 0xFF37474F));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                widthDp, dp(44));
        lp.setMargins(dp(8), 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private GradientDrawable pillBackground(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(22));
        return gd;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(inlineClearTask);
        controller.detachUi();
        super.onDetachedFromWindow();
    }
}
