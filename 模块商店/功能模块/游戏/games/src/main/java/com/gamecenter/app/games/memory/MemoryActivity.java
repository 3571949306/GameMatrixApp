package com.gamecenter.app.games.memory;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 翻牌子记忆游戏 Activity
 * <p>
 * 功能：
 * - 翻开两张卡片寻找相同配对
 * - 全部配对成功即获胜
 * - 自适应屏幕尺寸
 * <p>
 * 职责：
 * - 初始化 MemoryGame 游戏逻辑和 MemoryView 自定义视图
 * - 管理重新开始和教程按钮
 * - 监听卡片翻转事件并更新得分显示
 * <p>
 * 关键设计决策：
 * - MemoryView 以代码方式动态添加到 FrameLayout 中（替换 ViewStub），支持全屏游戏区域
 * - 通过 OnCardFlipListener 回调机制将视图层事件传递到 Activity 层更新 UI
 * <p>
 * 布局：res/layout/activity_game_simple.xml
 */
public class MemoryActivity extends AppCompatActivity {

    private MemoryView memoryView;
    private MemoryGame game;
    /** 得分文字 */
    private TextView tvScore;

    /**
     * Activity 创建时初始化视图和游戏逻辑
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_simple);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_memory);

        tvScore = findViewById(R.id.tv_game_score);

        // 动态创建 MemoryView 并替换布局中的 ViewStub 占位符
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
        // 监听卡片翻转事件，每次翻转后更新得分显示
        memoryView.setOnCardFlipListener(this::updateScore);

        // 重新开始按钮：重置游戏状态并刷新界面
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

    /**
     * 更新得分显示文字
     * <p>
     * 格式为"已匹配: X/PAIRS"，其中 PAIRS 为总配对数。
     */
    private void updateScore() {
        if (tvScore != null) {
            tvScore.setText("已匹配: " + game.getMatched() + "/" + MemoryGame.PAIRS);
        }
    }
}
