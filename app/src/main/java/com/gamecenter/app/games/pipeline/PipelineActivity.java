package com.gamecenter.app.games.pipeline;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;

/**
 * 接水管游戏的Activity
 *
 * <p>负责初始化游戏界面、绑定PipelineView和PipelineGame、
 * 处理关卡完成事件和重新开始按钮逻辑。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>关卡完成通过PipelineView的OnLevelCompleteListener回调通知</li>
 *   <li>重新开始直接调用game.reset()，无需重建Activity</li>
 * </ul>
 */
public class PipelineActivity extends AppCompatActivity {

    /** 接水管游戏自定义视图 */
    private PipelineView pipelineView;
    /** 接水管游戏逻辑对象 */
    private PipelineGame game;

    /**
     * Activity创建时的初始化
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置布局并更新标题</li>
     *   <li>创建PipelineGame实例并绑定到PipelineView</li>
     *   <li>注册关卡完成监听器，接通时显示Toast提示</li>
     *   <li>设置重新开始按钮</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pipeline);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_pipeline);

        TextView tvStatus = findViewById(R.id.tv_game_status);

        pipelineView = findViewById(R.id.pipeline_view);
        game = new PipelineGame();
        pipelineView.setGame(game);

        // 关卡完成时更新状态文字并弹出Toast
        pipelineView.setOnLevelCompleteListener(() -> {
            tvStatus.setText("水管接通！");
            Toast.makeText(this, "恭喜！水管已接通！", Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            pipelineView.invalidate();
            tvStatus.setText("");
        });

        pipelineView.invalidate();
    }
}
