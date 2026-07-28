package com.gamecenter.app.games.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * P2-7 (BOARD_REPLAY): 回放播放器，支持逐步前进/后退/跳转。
 *
 * 用法：
 * <pre>
 *   ReplayPlayer player = new ReplayPlayer(record);
 *   player.stepForward();  // 执行下一步
 *   player.stepBack();     // 撤销上一步
 *   player.gotoStep(5);    // 跳转到第 5 步
 *   List<ReplayMove> played = player.getPlayedMoves(); // 已执行的走法
 * </pre>
 *
 * 回调接口 [Listener] 用于通知 UI 刷新棋盘。
 */
public class ReplayPlayer {

    private final ReplayRecord record;
    private int currentIndex = 0; // 已播放到第几步（0 = 初始局面）
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        /** 棋盘需刷新到第 [step] 步的局面（0 = 初始空盘） */
        void onBoardUpdated(int step, List<ReplayMove> playedMoves);
        /** 回放结束 */
        void onReplayFinished();
        /** 回放重置到起点 */
        void onReplayReset();
    }

    public ReplayPlayer(ReplayRecord record) {
        this.record = record;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** 总步数 */
    public int getTotalSteps() {
        return record.getMoveCount();
    }

    /** 当前已播放到第几步（0 = 初始局面） */
    public int getCurrentStep() {
        return currentIndex;
    }

    /** 已执行的走法列表（截至当前步） */
    public List<ReplayMove> getPlayedMoves() {
        return new ArrayList<>(record.moves.subList(0, currentIndex));
    }

    /** 前进一步，返回是否还有更多 */
    public boolean stepForward() {
        if (currentIndex >= record.getMoveCount()) {
            for (Listener l : listeners) l.onReplayFinished();
            return false;
        }
        currentIndex++;
        notifyUpdated();
        if (currentIndex >= record.getMoveCount()) {
            for (Listener l : listeners) l.onReplayFinished();
        }
        return currentIndex < record.getMoveCount();
    }

    /** 后退一步，返回是否在起点 */
    public boolean stepBack() {
        if (currentIndex <= 0) return true;
        currentIndex--;
        notifyUpdated();
        return currentIndex == 0;
    }

    /** 跳转到指定步（0 = 初始局面） */
    public void gotoStep(int step) {
        int target = Math.max(0, Math.min(step, record.getMoveCount()));
        currentIndex = target;
        notifyUpdated();
        if (currentIndex >= record.getMoveCount()) {
            for (Listener l : listeners) l.onReplayFinished();
        }
    }

    /** 重置到起点 */
    public void reset() {
        currentIndex = 0;
        for (Listener l : listeners) l.onReplayReset();
        notifyUpdated();
    }

    /** 是否在起点 */
    public boolean isAtStart() {
        return currentIndex == 0;
    }

    /** 是否在终点 */
    public boolean isAtEnd() {
        return currentIndex >= record.getMoveCount();
    }

    private void notifyUpdated() {
        List<ReplayMove> played = getPlayedMoves();
        for (Listener l : listeners) l.onBoardUpdated(currentIndex, played);
    }
}
