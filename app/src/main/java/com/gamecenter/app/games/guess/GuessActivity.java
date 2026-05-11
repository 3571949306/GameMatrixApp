package com.gamecenter.app.games.guess;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

public class GuessActivity extends AppCompatActivity {

    private GuessView guessView;
    private GuessGame game;
    private TextView tvScore;
    private TextView tvBest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_guess);

        tvScore = findViewById(R.id.tv_game_score);

        tvBest = new TextView(this);
        tvBest.setTextSize(16);
        tvBest.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        android.widget.LinearLayout parent = (android.widget.LinearLayout) tvScore.getParent();
        parent.addView(tvBest, parent.indexOfChild(tvScore) + 1);

        guessView = new GuessView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(guessView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new GuessGame();
        guessView.setGame(game);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            guessView.resetInput();
            guessView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showGuessTutorial(this));

        updateScore();
    }

    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText(game.getDifficultyName() + " — 第" + (game.getAttempts() + 1) + "次");
        }
        if (tvBest != null) {
            int best = game.getBestScore();
            tvBest.setText("最佳记录: " + (best > 0 ? best + "次" : "暂无"));
        }
    }
}
