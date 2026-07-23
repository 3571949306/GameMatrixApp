package com.gamecenter.app.games.chinesechess;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 中国象棋AI提示次数限制器。
 *
 * <p>功能：
 * <ul>
 *   <li>每局最多10次提示</li>
 *   <li>每步最多1次提示</li>
 *   <li>提示冷却时间3秒</li>
 *   <li>次数用完后显示Toast提示</li>
 *   <li>支持重置当前步提示次数</li>
 * </ul>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-07-23
 */
public class HintLimiter {

    private static final int MAX_HINTS_PER_GAME = 10;
    private static final int MAX_HINTS_PER_MOVE = 1;
    private static final long HINT_COOLDOWN_MS = 3000;

    private int hintsUsedThisGame = 0;
    private int hintsUsedThisMove = 0;
    private long lastHintTime = 0;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 检查是否可以使用提示。
     *
     * @return true如果可以使用提示，false表示无法使用
     */
    public boolean canUseHint() {
        long currentTime = System.currentTimeMillis();
        
        // 检查冷却时间
        if (currentTime - lastHintTime < HINT_COOLDOWN_MS) {
            return false;
        }
        
        // 检查本局剩余次数
        if (hintsUsedThisGame >= MAX_HINTS_PER_GAME) {
            return false;
        }
        
        // 检查本步剩余次数
        if (hintsUsedThisMove >= MAX_HINTS_PER_MOVE) {
            return false;
        }
        
        return true;
    }

    /**
     * 使用一次提示。
     *
     * @return true如果使用成功，false表示无法使用
     */
    public boolean useHint() {
        if (!canUseHint()) {
            showLimitToast();
            return false;
        }
        
        hintsUsedThisGame++;
        hintsUsedThisMove++;
        lastHintTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 重置当前步的提示次数（当玩家走了一步后调用）。
     */
    public void resetMoveHints() {
        hintsUsedThisMove = 0;
    }

    /**
     * 获取本局剩余的提示次数。
     *
     * @return 剩余的提示次数
     */
    public int getRemainingHints() {
        return Math.max(0, MAX_HINTS_PER_GAME - hintsUsedThisGame);
    }

    /**
     * 重置本局的所有提示次数（新一局游戏时调用）。
     */
    public void resetGameHints() {
        hintsUsedThisGame = 0;
        hintsUsedThisMove = 0;
        lastHintTime = 0;
    }

    /**
     * 获取冷却剩余时间（毫秒）。
     *
     * @return 剩余冷却时间，如果不在冷却中则返回0
     */
    public long getRemainingCooldown() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastHintTime;
        if (elapsed >= HINT_COOLDOWN_MS) {
            return 0;
        }
        return HINT_COOLDOWN_MS - elapsed;
    }

    /**
     * 检查是否本局提示次数已用完。
     *
     * @return true表示已用完，false表示还有剩余
     */
    public boolean isGameHintLimitReached() {
        return hintsUsedThisGame >= MAX_HINTS_PER_GAME;
    }

    /**
     * 检查是否当前步提示次数已用完。
     *
     * @return true表示已用完，false表示还有剩余
     */
    public boolean isMoveHintLimitReached() {
        return hintsUsedThisMove >= MAX_HINTS_PER_MOVE;
    }

    /**
     * 构造函数
     *
     * @param context 用于显示Toast的上下文
     */
    public HintLimiter(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 显示限制Toast提示。
     */
    private void showLimitToast() {
        mainHandler.post(() -> {
            String message;
            if (hintsUsedThisGame >= MAX_HINTS_PER_GAME) {
                message = "本局提示次数已用完（共" + MAX_HINTS_PER_GAME + "次）";
            } else if (hintsUsedThisMove >= MAX_HINTS_PER_MOVE) {
                message = "当前步已使用过提示，请走棋后再试";
            } else {
                message = "提示冷却中，请稍后再试";
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 获取本局已使用的提示次数。
     *
     * @return 已使用的提示次数
     */
    public int getHintsUsedThisGame() {
        return hintsUsedThisGame;
    }

    /**
     * 获取当前步已使用的提示次数。
     *
     * @return 当前步已使用的提示次数
     */
    public int getHintsUsedThisMove() {
        return hintsUsedThisMove;
    }
}