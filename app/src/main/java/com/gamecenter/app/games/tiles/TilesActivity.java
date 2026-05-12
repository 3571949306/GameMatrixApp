package com.gamecenter.app.games.tiles;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

public class TilesActivity extends AppCompatActivity {

    private TilesView tilesView;
    private TilesGame game;
    private Handler handler;
    private boolean isRunning;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                game.update();
                tilesView.invalidate();
                handler.postDelayed(this, 16);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiles);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_tiles);

        tilesView = new TilesView(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(tilesView);

        game = new TilesGame();
        tilesView.setGame(game);

        handler = new Handler(Looper.getMainLooper());

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            tilesView.invalidate();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showTilesTutorial(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        handler.post(gameLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        handler = null;
        game = null;
        tilesView = null;
    }
}
