package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;

import java.util.List;

/**
 * 斗地主单机牌桌视图。
 *
 * <p>FrameLayout 三层结构：底层 {@link DouDiZhuTableView}（自绘牌桌），
 * 中层真实按钮（出牌/不出/提示、叫地主/不叫、退出——替代被禁用的 Canvas 绘制按钮，
 * 可获焦点、可访问），顶层 {@link DouDiZhuEffectsView}（炸弹/火箭/飞机特效）。</p>
 *
 * <p>本视图不持有游戏逻辑：全部操作委托 {@link DoudizhuGameController}，
 * 并通过其 UiCallback 接收回推（按钮显隐/全量同步/结束弹窗）。</p>
 */
public class DoudizhuGameScreen extends FrameLayout implements DoudizhuGameController.UiCallback {

    /** 退出牌桌（返回菜单）回调，由宿主 Fragment 实现 */
    public interface ExitListener {
        void onExitRequested();
    }

    private final DoudizhuGameController controller;
    private final DouDiZhuTableView tableView;
    private final DouDiZhuEffectsView effectsView;

    private final LinearLayout playBar;
    private final LinearLayout bidBar;
    private final Button btnHint;
    private final Button btnPass;
    private final Button btnPlay;
    private final Button btnBidCall;
    private final Button btnBidPass;

    private final ExitListener exitListener;

    public DoudizhuGameScreen(@NonNull Context context, @NonNull DoudizhuGameController controller,
                              ExitListener exitListener) {
        super(context);
        this.controller = controller;
        this.exitListener = exitListener;

        tableView = new DouDiZhuTableView(context);
        addView(tableView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnHint = makeBarButton(context, R.string.game_doudizhu_btn_hint);
        btnPass = makeBarButton(context, R.string.game_doudizhu_btn_pass);
        btnPlay = makeBarButton(context, R.string.game_doudizhu_btn_play);

        playBar = new LinearLayout(context);
        playBar.setOrientation(LinearLayout.HORIZONTAL);
        playBar.setGravity(Gravity.CENTER);
        playBar.addView(btnHint);
        playBar.addView(btnPass);
        playBar.addView(btnPlay);
        addView(playBar, bottomBarParams());

        btnBidPass = makeBarButton(context, R.string.game_doudizhu_bid_pass);
        btnBidCall = makeBarButton(context, R.string.game_doudizhu_bid);
        btnBidCall.setTextColor(Color.WHITE);
        btnBidCall.setBackgroundColor(0xFF6200EE);

        bidBar = new LinearLayout(context);
        bidBar.setOrientation(LinearLayout.HORIZONTAL);
        bidBar.setGravity(Gravity.CENTER);
        bidBar.addView(btnBidPass);
        bidBar.addView(btnBidCall);
        bidBar.setVisibility(GONE);
        addView(bidBar, bottomBarParams());

        Button btnExit = new Button(context);
        btnExit.setText(R.string.game_doudizhu_exit);
        btnExit.setTextSize(12);
        FrameLayout.LayoutParams exitLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        int m8 = dp(8);
        exitLp.setMargins(m8, m8, m8, m8);
        addView(btnExit, exitLp);

        effectsView = new DouDiZhuEffectsView(context);
        addView(effectsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnPlay.setOnClickListener(v -> {
            List<Card> selected = tableView.getSelectedCards();
            if (selected == null || selected.isEmpty()) {
                toast(R.string.game_doudizhu_select_cards);
                return;
            }
            controller.onHumanPlay(selected);
        });
        btnPass.setOnClickListener(v -> controller.onHumanPass());
        btnHint.setOnClickListener(v -> {
            List<Card> hint = controller.nextHint();
            if (hint != null && !hint.isEmpty()) {
                tableView.selectCards(hint);
            } else {
                toast(R.string.game_doudizhu_cannot_beat);
            }
        });
        btnBidCall.setOnClickListener(v -> controller.onHumanBid(true));
        btnBidPass.setOnClickListener(v -> controller.onHumanBid(false));
        btnExit.setOnClickListener(v -> confirmExit());

        controller.attachUi(this);
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
    }

    @Override
    public void onCardsPlayed(List<Card> cards, CardType type) {
        if (type == CardType.BOMB) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.BOMB,
                    getWidth() / 2f, getHeight() * 0.42f);
        } else if (type == CardType.JOKER_BOMB) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.ROCKET,
                    getWidth() / 2f, getHeight() * 0.42f);
        } else if (type == CardType.AIRPLANE || type == CardType.AIRPLANE_WITH_WINGS) {
            effectsView.showEffect(DouDiZhuEffectsView.EffectType.PLANE,
                    getWidth() / 2f, getHeight() * 0.42f);
        }
    }

    @Override
    public void onInvalidPlay(boolean illegalCombo, boolean cannotBeat) {
        toast(illegalCombo ? R.string.game_doudizhu_invalid_card_type
                : R.string.game_doudizhu_cannot_beat);
    }

    @Override
    public void onGameFinished(int winnerIndex) {
        boolean playerWon = winnerIndex == Seats.SEAT_PLAYER;
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.game_doudizhu_game_over)
                .setMessage(playerWon ? R.string.game_doudizhu_you_win : R.string.game_doudizhu_you_lose)
                .setCancelable(false)
                .setPositiveButton(R.string.game_doudizhu_play_again, (d, w) -> {
                    controller.startNewGame(controller.getDifficulty());
                })
                .setNegativeButton(R.string.game_doudizhu_exit, (d, w) -> {
                    if (exitListener != null) exitListener.onExitRequested();
                })
                .show();
    }

    // ============ 内部 ============

    private void confirmExit() {
        new android.app.AlertDialog.Builder(getContext())
                .setMessage(R.string.game_doudizhu_exit_confirm)
                .setPositiveButton(R.string.game_doudizhu_exit, (d, w) -> {
                    if (exitListener != null) exitListener.onExitRequested();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private FrameLayout.LayoutParams bottomBarParams() {
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = dp(16);
        return flp;
    }

    private Button makeBarButton(Context context, int textRes) {
        Button btn = new Button(context);
        btn.setText(textRes);
        btn.setTextSize(15);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFF37474F);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void toast(int resId) {
        Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDetachedFromWindow() {
        controller.detachUi();
        super.onDetachedFromWindow();
    }
}
