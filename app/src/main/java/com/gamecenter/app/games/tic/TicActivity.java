package com.gamecenter.app.games.tic;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * 井字棋游戏 Activity
 *
 * 功能：
 * - 先手对弈电脑
 * - AI自动下棋
 * - 自适应屏幕尺寸
 *
 * 布局：res/layout/activity_game_simple.xml
 */
public class TicActivity extends AppCompatActivity {

    private static final String GAME_ID = "tic";
    private TicView ticView;
    private TicGame game;
    private TextView tvScore;
    private GameUsageStore usageStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_tic);

        tvScore = findViewById(R.id.tv_game_score);

        ticView = new TicView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(ticView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new TicGame();
        ticView.setGame(game);
        usageStore = new GameUsageStore(this);

        ticView.setOnGameOverListener(winner -> {
            if (winner == TicGame.PLAYER) {
                usageStore.recordWin(GAME_ID);
            } else if (winner == TicGame.COMPUTER) {
                usageStore.recordLoss(GAME_ID);
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            ticView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showTicTutorial(this));

        updateScore();
    }

    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText("你先下 X");
        }
    }
}
