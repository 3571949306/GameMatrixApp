package com.gamecenter.app.games.dice;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

public class DiceActivity extends AppCompatActivity {

    private DiceView diceView;
    private DiceGame game;
    private TextView tvScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_dice);

        tvScore = findViewById(R.id.tv_game_score);

        diceView = new DiceView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(diceView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new DiceGame();
        diceView.setGame(game);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            diceView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showDiceTutorial(this));

        updateScore();
    }

    private void updateScore() {
        if (tvScore != null && game != null) {
            tvScore.setText("你 " + game.getPlayerWins() + "胜  "
                    + game.getAiWins() + "负  " + game.getDraws() + "平  第" + (game.getRound() + 1) + "局");
        }
    }
}
