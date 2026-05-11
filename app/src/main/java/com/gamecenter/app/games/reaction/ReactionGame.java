package com.gamecenter.app.games.reaction;

import java.util.Random;

public class ReactionGame {

    public enum State {
        IDLE, WAITING, READY, TAPPED, TOO_SOON
    }

    public interface OnStateChangeListener {
        void onStateChanged(State state);
    }

    private static final int MAX_ROUNDS = 5;
    private static final long MIN_WAIT_MS = 1000;
    private static final long MAX_WAIT_MS = 4000;

    private State state;
    private int round;
    private long readyTime;
    private long tapTime;
    private long[] results;
    private final Random random;
    private OnStateChangeListener listener;

    public ReactionGame() {
        random = new Random();
        results = new long[MAX_ROUNDS];
        state = State.IDLE;
        round = 0;
    }

    public void reset() {
        state = State.IDLE;
        round = 0;
        readyTime = 0;
        tapTime = 0;
        for (int i = 0; i < results.length; i++) results[i] = 0;
    }

    public void onTimeout() {
        if (state != State.WAITING) return;
        state = State.READY;
        readyTime = System.currentTimeMillis();
        notifyListener();
    }

    public void onTap() {
        switch (state) {
            case IDLE:
                startRound();
                return;
            case WAITING:
                state = State.TOO_SOON;
                notifyListener();
                return;
            case READY:
                tapTime = System.currentTimeMillis();
                long ms = tapTime - readyTime;
                results[round] = ms;
                round++;
                state = State.TAPPED;
                notifyListener();
                return;
            case TAPPED:
                if (round >= MAX_ROUNDS) {
                    reset();
                    notifyListener();
                } else {
                    startRound();
                }
                return;
            case TOO_SOON:
                startRound();
                return;
        }
    }

    private void startRound() {
        state = State.WAITING;
        readyTime = 0;
        tapTime = 0;
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        this.listener = listener;
    }

    public long getWaitingDelay() {
        return MIN_WAIT_MS + random.nextLong() % (MAX_WAIT_MS - MIN_WAIT_MS + 1);
    }

    public State getState() {
        return state;
    }

    public int getRound() {
        return round;
    }

    public long getCurrentResult() {
        if (tapTime > 0) return tapTime - readyTime;
        return 0;
    }

    public double getAverage() {
        if (round == 0) return 0;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < round; i++) {
            if (results[i] > 0) {
                sum += results[i];
                count++;
            }
        }
        return count == 0 ? 0 : (double) sum / count;
    }

    public long getBest() {
        long best = Long.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < results.length; i++) {
            if (results[i] > 0 && results[i] < best) {
                best = results[i];
                found = true;
            }
        }
        return found ? best : 0;
    }
}
