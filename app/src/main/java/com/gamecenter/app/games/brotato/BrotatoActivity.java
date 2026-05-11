package com.gamecenter.app.games.brotato;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class BrotatoActivity extends AppCompatActivity {

    private BrotatoView gameView;
    private LinearLayout startPanel;
    private LinearLayout controlPanel;
    private LinearLayout upgradePanel;
    private TextView tvStats;
    private TextView tvWeapons;
    private TextView tvUpgradeTitle;
    private MaterialButton[] optionButtons;

    private BrotatoGame game;
    private Handler gameHandler;
    private Runnable gameRunnable;
    private boolean isRunning = false;
    private boolean upgradeShowing = false;
    private boolean joystickActive = false;
    private float joystickBaseX;
    private float joystickBaseY;
    private float joystickInputX;
    private float joystickInputY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brotato);

        gameHandler = new Handler(Looper.getMainLooper());
        game = new BrotatoGame();

        gameView = findViewById(R.id.game_view);
        startPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        upgradePanel = findViewById(R.id.upgrade_panel);
        tvStats = findViewById(R.id.tv_stats);
        tvWeapons = findViewById(R.id.tv_weapons);
        tvUpgradeTitle = findViewById(R.id.tv_upgrade_title);
        optionButtons = new MaterialButton[] {
                findViewById(R.id.btn_upgrade1),
                findViewById(R.id.btn_upgrade2),
                findViewById(R.id.btn_upgrade3)
        };

        gameView.setGame(game);
        setupButtons();
        setupTouchControls();
        updateInfoPanels();
    }

    private void setupButtons() {
        MaterialButton btnStart = findViewById(R.id.btn_easy);
        MaterialButton btnTutorial = findViewById(R.id.btn_tutorial);
        MaterialButton btnRestart = findViewById(R.id.btn_restart);
        MaterialButton btnRefresh = findViewById(R.id.btn_refresh);

        if (btnStart != null) {
            btnStart.setOnClickListener(v -> startGame());
        }
        if (btnTutorial != null) {
            btnTutorial.setOnClickListener(v -> GameTutorialHelper.showBrotatoTutorial(this));
        }
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> restartGame());
        }
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                game.rollUpgradeOptions();
                showUpgradePanel();
            });
        }

        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> chooseUpgrade(index));
        }

        gameView.setOnClickListener(v -> {
            if (game.isGameOver()) {
                startGame();
            }
        });
    }

    private void setupTouchControls() {
        gameView.setOnTouchListener((v, event) -> {
            if (!isRunning || game.isGameOver() || game.isWaitingForUpgrade()) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    joystickActive = true;
                    joystickBaseX = event.getX();
                    joystickBaseY = event.getY();
                    joystickInputX = 0f;
                    joystickInputY = 0f;
                    gameView.setJoystick(joystickActive, joystickBaseX, joystickBaseY, joystickInputX, joystickInputY);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateJoystick(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    joystickActive = false;
                    joystickInputX = 0f;
                    joystickInputY = 0f;
                    gameView.setJoystick(false, joystickBaseX, joystickBaseY, 0f, 0f);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void updateJoystick(float touchX, float touchY) {
        float dx = touchX - joystickBaseX;
        float dy = touchY - joystickBaseY;
        float radius = Math.max(64f, gameView.getWidth() * 0.14f);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > radius) {
            dx = dx / len * radius;
            dy = dy / len * radius;
            len = radius;
        }
        if (len < radius * 0.18f) {
            joystickInputX = 0f;
            joystickInputY = 0f;
        } else {
            joystickInputX = dx / radius;
            joystickInputY = dy / radius;
        }
        gameView.setJoystick(true, joystickBaseX, joystickBaseY, joystickInputX, joystickInputY);
    }

    private void startGame() {
        isRunning = false;
        removeGameLoop();
        game.reset();
        startPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        upgradePanel.setVisibility(View.GONE);
        upgradeShowing = false;
        clearJoystick();
        updateInfoPanels();

        isRunning = true;
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (joystickActive && !game.isWaitingForUpgrade()) {
                    game.movePlayerInput(joystickInputX, joystickInputY);
                }
                game.update(System.currentTimeMillis());
                if (game.isWaitingForUpgrade() && !upgradeShowing) {
                    showUpgradePanel();
                }
                updateInfoPanels();
                gameView.invalidate();

                if (!game.isGameOver()) {
                    gameHandler.postDelayed(this, 16);
                } else {
                    isRunning = false;
                    upgradePanel.setVisibility(View.GONE);
                    upgradeShowing = false;
                    clearJoystick();
                }
            }
        };
        gameHandler.postDelayed(gameRunnable, 16);
    }

    private void restartGame() {
        isRunning = false;
        removeGameLoop();
        game.reset();
        gameView.invalidate();
        updateInfoPanels();
        upgradePanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.GONE);
        startPanel.setVisibility(View.VISIBLE);
        upgradeShowing = false;
        clearJoystick();
    }

    private void showUpgradePanel() {
        List<BrotatoGame.UpgradeOption> options = game.getUpgradeOptions();
        if (options.isEmpty()) {
            game.rollUpgradeOptions();
            options = game.getUpgradeOptions();
        }

        tvUpgradeTitle.setText("等级 " + game.getLevel() + "：选择一项强化");
        for (int i = 0; i < optionButtons.length; i++) {
            BrotatoGame.UpgradeOption option = options.get(i);
            optionButtons[i].setText(option.title + "\n" + option.desc);
        }
        updateInfoPanels();
        upgradePanel.setVisibility(View.VISIBLE);
        upgradeShowing = true;
        clearJoystick();
    }

    private void chooseUpgrade(int index) {
        game.chooseUpgrade(index);
        upgradePanel.setVisibility(View.GONE);
        upgradeShowing = false;
        updateInfoPanels();
        gameView.invalidate();
    }

    private void updateInfoPanels() {
        if (tvStats != null) {
            tvStats.setText(game.getStatsText());
        }
        if (tvWeapons != null) {
            tvWeapons.setText(game.getWeaponsText());
        }
    }

    private void removeGameLoop() {
        if (gameRunnable != null) {
            gameHandler.removeCallbacks(gameRunnable);
        }
    }

    private void clearJoystick() {
        joystickActive = false;
        joystickInputX = 0f;
        joystickInputY = 0f;
        if (gameView != null) {
            gameView.setJoystick(false, joystickBaseX, joystickBaseY, 0f, 0f);
        }
    }

    @Override
    public void onBackPressed() {
        if (startPanel.getVisibility() == View.GONE) {
            restartGame();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        removeGameLoop();
    }
}
