package com.gamecenter.app.games.flappy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

public class FlappyActivity extends AppCompatActivity {

    private static final String GAME_ID = "flappy";
    private FlappyView flappyView;
    private FlappyGame game;
    private Handler handler;
    private boolean isRunning;
    private GameUsageStore usageStore;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                game.update(System.currentTimeMillis());
                flappyView.invalidate();
                handler.postDelayed(this, 16);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flappy);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_flappy);

        usageStore = new GameUsageStore(this);

        flappyView = new FlappyView(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(flappyView);

        game = new FlappyGame();
        flappyView.setGame(game);

        handler = new Handler(Looper.getMainLooper());

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            flappyView.invalidate();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showFlappyTutorial(this));

        flappyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (game.isGameOver()) {
                        usageStore.recordScore(GAME_ID, game.getScore());
                    }
                }
                return false;
            }
        });
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
        handler.removeCallbacks(gameLoop);
    }
}
