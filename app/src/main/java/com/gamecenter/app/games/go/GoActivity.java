package com.gamecenter.app.games.go;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoActivity extends AppCompatActivity {

    private GoView goView;
    private LinearLayout controlPanel;
    private TextView tvStatus;

    private GoGame game;
    private Handler uiHandler;
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go);

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        goView = findViewById(R.id.go_view);
        controlPanel = findViewById(R.id.control_panel);
        tvStatus = findViewById(R.id.tv_status);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_go);

        game = new GoGame();
        goView.setGame(game);
        goView.setOnCellClickListener(this::handleCellClick);

        findViewById(R.id.btn_pass).setOnClickListener(v -> handlePass());
        findViewById(R.id.btn_restart).setOnClickListener(v -> restart());
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGoTutorial(this));
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGoTutorial(this));
        findViewById(R.id.btn_online).setOnClickListener(v -> {
            Intent intent = new Intent(this, GoOnlineActivity.class);
            startActivity(intent);
        });

        updateStatus();
    }

    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        if (game.getCurrentPlayer() != GoGame.BLACK) return;
        if (aiThinking) return;

        if (!game.isValidMove(x, y)) {
            Toast.makeText(this, "此处不可落子", Toast.LENGTH_SHORT).show();
            return;
        }

        game.makeMove(x, y);
        game.switchPlayer();
        goView.invalidate();
        updateStatus();

        aiThinking = true;
        aiExecutor.execute(() -> {
            int[] move = game.getBestMove();
            uiHandler.postDelayed(() -> {
                if (move != null) {
                    game.makeMove(move[0], move[1]);
                } else {
                    game.pass();
                    Toast.makeText(this, "AI 弃权一手", Toast.LENGTH_SHORT).show();
                }
                game.switchPlayer();
                aiThinking = false;
                goView.invalidate();
                updateStatus();
            }, 400);
        });
    }

    private void handlePass() {
        if (game.isGameOver()) return;
        if (aiThinking) return;
        game.pass();
        game.switchPlayer();
        goView.invalidate();
        updateStatus();

        if (game.isGameOver()) return;

        aiThinking = true;
        aiExecutor.execute(() -> {
            int[] move = game.getBestMove();
            uiHandler.postDelayed(() -> {
                if (move != null) {
                    game.makeMove(move[0], move[1]);
                } else {
                    game.pass();
                }
                game.switchPlayer();
                aiThinking = false;
                goView.invalidate();
                updateStatus();
            }, 400);
        });
    }

    private void updateStatus() {
        if (game.isGameOver()) {
            tvStatus.setText("对局结束 - 黑吃子" + game.getBlackCaptures()
                    + "  白吃子" + game.getWhiteCaptures());
        } else {
            tvStatus.setText(game.getCurrentPlayer() == GoGame.BLACK ? "黑方回合" : "白方回合 (AI)");
        }
    }

    private void restart() {
        aiThinking = false;
        game.reset();
        goView.setGame(game);
        goView.invalidate();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiExecutor.shutdownNow();
    }
}
