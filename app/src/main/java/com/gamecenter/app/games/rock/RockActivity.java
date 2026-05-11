package com.gamecenter.app.games.rock;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

public class RockActivity extends AppCompatActivity {

    private RockView rockView;
    private RockGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rock);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_rock);

        rockView = new RockView(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(rockView);

        game = new RockGame();
        rockView.setGame(game);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            rockView.invalidate();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showRockTutorial(this));

        MaterialButton btnOnline = findViewById(R.id.btn_online);
        btnOnline.setOnClickListener(v -> {
            Intent intent = new Intent(this, RockOnlineActivity.class);
            startActivity(intent);
        });
    }
}
