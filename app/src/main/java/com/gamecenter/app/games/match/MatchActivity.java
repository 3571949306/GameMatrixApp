package com.gamecenter.app.games.match;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 连连看（三消）游戏的Activity
 *
 * <p>负责初始化游戏界面、绑定MatchView、处理分数显示、
 * 无可用移动提示、重新开始和教程按钮逻辑。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>游戏逻辑和绘制全部在MatchView中完成，Activity仅负责UI编排</li>
 *   <li>通过OnGameStateListener回调获取分数变化和无移动事件</li>
 *   <li>教程功能委托给GameTutorialHelper统一管理</li>
 * </ul>
 */
public class MatchActivity extends androidx.appcompat.app.AppCompatActivity {

    /** 连连看游戏自定义视图 */
    private MatchView matchView;
    /** 分数显示文本框 */
    private TextView tvScore;

    /**
     * Activity创建时的初始化
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置布局并更新标题</li>
     *   <li>获取MatchView引用并注册游戏状态监听器</li>
     *   <li>设置重新开始按钮和教程按钮</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_match);

        tvScore = findViewById(R.id.tv_game_score);

        matchView = findViewById(R.id.match_view);
        // 注册游戏状态监听器，处理分数变化和无可用移动事件
        matchView.setOnGameStateListener(new MatchView.OnGameStateListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("得分: " + score);
            }

            @Override
            public void onNoMoreMoves() {
                tvScore.setText("暂无移动! 得分: " + matchView.getScore());
            }
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            matchView.reset();
            tvScore.setText("得分: 0");
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showMatchTutorial(this));

        tvScore.setText("得分: 0");
    }
}
