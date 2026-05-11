package com.gamecenter.app.games.memory;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 翻牌子记忆游戏 Activity
 *
 * 功能：
 * - 翻开两张卡片寻找相同配对
 * - 全部配对成功即获胜
 * - 自适应屏幕尺寸
 *
 * 布局：res/layout/activity_game_simple.xml
 */
public class MemoryActivity extends AppCompatActivity {

    private MemoryView memoryView;
    private MemoryGame game;
    private TextView tvScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_memory);

        tvScore = findViewById(R.id.tv_game_score);

        memoryView = new MemoryView(this);
        View stub = findViewById(R.id.game_view_stub);
        if (stub.getParent() instanceof FrameLayout) {
            stub.setVisibility(View.GONE);
            ((FrameLayout) stub.getParent()).addView(memoryView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        game = new MemoryGame();
        memoryView.setGame(game);
        memoryView.setOnCardFlipListener(this::updateScore);

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            memoryView.invalidate();
            updateScore();
        });
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showMemoryTutorial(this));

        updateScore();
    }

    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText("已匹配: " + game.getMatched() + "/" + MemoryGame.PAIRS);
        }
    }
}
