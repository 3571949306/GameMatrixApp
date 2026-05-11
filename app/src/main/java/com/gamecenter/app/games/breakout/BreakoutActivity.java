package com.gamecenter.app.games.breakout;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

public class BreakoutActivity extends AppCompatActivity {

    private static final String GAME_ID = "breakout";
    private BreakoutView breakoutView;
    private TextView tvScore;
    private TextView tvLives;
    private GameUsageStore usageStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breakout);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_breakout);

        tvScore = findViewById(R.id.tv_game_score);
        tvLives = findViewById(R.id.tv_game_lives);
        usageStore = new GameUsageStore(this);

        breakoutView = findViewById(R.id.breakout_view);
        breakoutView.setOnGameStateListener(new BreakoutView.OnGameStateListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("得分: " + score);
            }

            @Override
            public void onLivesChanged(int lives) {
                tvLives.setText("生命: " + lives);
            }

            @Override
            public void onGameOver(int score) {
                tvScore.setText("游戏结束! 得分: " + score);
                usageStore.recordScore(GAME_ID, score);
            }

            @Override
            public void onGameWon(int score) {
                Toast.makeText(BreakoutActivity.this, "恭喜通关!", Toast.LENGTH_LONG).show();
                usageStore.recordScore(GAME_ID, score);
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> breakoutView.resetGame());

        breakoutView.postDelayed(() -> breakoutView.startGame(), 500);
    }
}