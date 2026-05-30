package com.gamecenter.app.games.rock;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 石头剪刀布游戏 Activity（单机模式）
 * <p>
 * 职责：
 * - 初始化 RockGame 游戏逻辑和 RockView 自定义视图
 * - 提供重新开始和教程按钮
 * - 提供入口跳转到联机对战模式（RockOnlineActivity）
 * <p>
 * 关键设计决策：
 * - RockView 以代码方式动态添加到布局中（替换 ViewStub），而非在 XML 中静态声明
 * - 单机模式与联机模式使用不同的 Activity，保持职责分离
 * <p>
 * 布局：res/layout/activity_rock.xml
 */
public class RockActivity extends AppCompatActivity {

    private RockView rockView;
    private RockGame game;

    /**
     * Activity 创建时初始化视图和游戏逻辑
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rock);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_rock);

        // 动态创建 RockView 并替换布局中的 ViewStub 占位符
        rockView = new RockView(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(rockView);

        game = new RockGame();
        rockView.setGame(game);

        // 重新开始按钮：重置游戏状态并刷新视图
        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            rockView.invalidate();
        });

        // 教程按钮：显示石头剪刀布玩法说明
        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showRockTutorial(this));

        // 联机对战按钮：跳转到 RockOnlineActivity
        MaterialButton btnOnline = findViewById(R.id.btn_online);
        btnOnline.setOnClickListener(v -> {
            Intent intent = new Intent(this, RockOnlineActivity.class);
            startActivity(intent);
        });
    }
}
