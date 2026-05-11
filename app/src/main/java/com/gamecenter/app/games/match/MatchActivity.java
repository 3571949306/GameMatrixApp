package com.gamecenter.app.games.match;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

public class MatchActivity extends androidx.appcompat.app.AppCompatActivity {

    private MatchView matchView;
    private TextView tvScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_match);

        tvScore = findViewById(R.id.tv_game_score);

        matchView = findViewById(R.id.match_view);
        matchView.setOnGameStateListener(new MatchView.OnGameStateListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("得分: " + score);
            }

            @Override
            public void onNoMoreMoves() {
                tvScore.setText("暂无移动! 得分: " + matchView.getScore());
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            matchView.reset();
            tvScore.setText("得分: 0");
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showMatchTutorial(this));

        tvScore.setText("得分: 0");
    }
}