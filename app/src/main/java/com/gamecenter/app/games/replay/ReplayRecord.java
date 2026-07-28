package com.gamecenter.app.games.replay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * P2-7 (BOARD_REPLAY): 一局已录制对局的回放数据。
 */
public class ReplayRecord {

    public final String gameId;
    public final int difficulty;
    public final long startTimeMs;
    public final long endTimeMs;
    public final String result;
    public final List<ReplayMove> moves;

    public ReplayRecord(String gameId, int difficulty, long startTimeMs, long endTimeMs,
                        String result, List<ReplayMove> moves) {
        this.gameId = gameId;
        this.difficulty = difficulty;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.result = result;
        this.moves = moves;
    }

    public int getMoveCount() {
        return moves.size();
    }

    public long getDurationMs() {
        return endTimeMs - startTimeMs;
    }

    public String getResultLabel() {
        switch (result) {
            case ReplayRecorder.RESULT_WIN: return "胜";
            case ReplayRecorder.RESULT_LOSS: return "负";
            case ReplayRecorder.RESULT_DRAW: return "平";
            default: return "—";
        }
    }

    static ReplayRecord fromJson(JSONObject o) throws Exception {
        String gameId = o.getString("gameId");
        int difficulty = o.optInt("difficulty", 0);
        long startTime = o.optLong("startTime", 0);
        long endTime = o.optLong("endTime", 0);
        String result = o.optString("result", ReplayRecorder.RESULT_ONGOING);
        List<ReplayMove> moves = new ArrayList<>();
        JSONArray arr = o.getJSONArray("moves");
        for (int i = 0; i < arr.length(); i++) {
            moves.add(ReplayMove.decode(arr.getString(i)));
        }
        return new ReplayRecord(gameId, difficulty, startTime, endTime, result, moves);
    }
}
