package com.gamecenter.app.games;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public abstract class BaseGameActivity extends Activity {
    protected String gameId;
    protected String gameName;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    protected void initGame(String gameId, String gameName) {
        this.gameId = gameId;
        this.gameName = gameName;
    }
    
    protected void startGame() {}
    protected void pauseGame() {}
    protected void resumeGame() {}
    protected void endGame(int score) {}
    
    public static void launch(Context context, String gameId, String gameName) {
        // Stub implementation
    }
}
