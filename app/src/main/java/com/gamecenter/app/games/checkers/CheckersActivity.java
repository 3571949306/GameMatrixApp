package com.gamecenter.app.games.checkers;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

public class CheckersActivity extends AppCompatActivity {

    private static final String GAME_ID = "checkers";
    private CheckersView checkersView;
    private TextView tvStatus;
    private boolean gameOver = false;
    private GameUsageStore usageStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkers);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_checkers);

        tvStatus = findViewById(R.id.tv_game_status);

        checkersView = findViewById(R.id.checkers_view);
        usageStore = new GameUsageStore(this);
        checkersView.setOnGameStateListener(new CheckersView.OnGameStateListener() {
            @Override
            public void onPlayerChanged(int player) {
                if (!gameOver) {
                    tvStatus.setText(player == 0 ? "黑方回合" : "白方回合");
                }
            }

            @Override
            public void onGameOver(int winner) {
                gameOver = true;
                String winnerName = (winner == 0) ? "黑方" : "白方";
                tvStatus.setText(winnerName + "获胜!");
                Toast.makeText(CheckersActivity.this, winnerName + "获胜!", Toast.LENGTH_LONG).show();
                if (winner == 0) {
                    usageStore.recordWin(GAME_ID);
                } else {
                    usageStore.recordLoss(GAME_ID);
                }
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            gameOver = false;
            checkersView.reset();
            tvStatus.setText("黑方回合");
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showCheckersTutorial(this));

        tvStatus.setText("黑方回合");
    }
}