package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;

public class GameUsageStore {
    public GameUsageStore(Context context) {}
    public void recordUsage(String gameId) {}
    public long getLastPlayedTime(String gameId) { return 0; }
    public int getPlayCount(String gameId) { return 0; }
}
