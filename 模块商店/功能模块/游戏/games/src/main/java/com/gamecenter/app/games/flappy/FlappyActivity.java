package com.gamecenter.app.games.flappy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;

/**
 * Flappy Bird 风格游戏 Activity
 *
 * <p>作为 Flappy Bird 游戏的入口界面，负责管理游戏生命周期和用户交互。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>通过 Handler+Runnable 实现约60FPS的游戏主循环</li>
 *   <li>管理游戏开始、暂停、恢复和销毁的生命周期</li>
 *   <li>游戏结束时记录分数到 GameUsageStore</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用固定16ms间隔（约60FPS）的 gameLoop，而非基于游戏逻辑的定时调度，
 *       因为 Flappy Bird 需要流畅的实时物理模拟</li>
 *   <li>游戏循环在 onResume 中启动、onPause 中暂停，确保后台时不消耗资源</li>
 *   <li>FlappyView 以代码方式动态添加到布局中，而非在 XML 中静态声明</li>
 *   <li>触摸事件由 FlappyView 自行处理（jump/reset），Activity 仅监听游戏结束事件用于记录分数</li>
 * </ul>
 */
public class FlappyActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于分数记录 */
    private static final String GAME_ID = "flappy";

    /** 游戏画面自定义 View */
    private FlappyView flappyView;

    /** Flappy Bird 游戏逻辑实例 */
    private FlappyGame game;

    /** 主线程 Handler，用于调度游戏主循环 */
    private Handler handler;

    /** 游戏是否正在运行 */
    private boolean isRunning;

    /** 游戏使用记录存储，用于持久化分数 */
    private GameUsageStore usageStore;

    /**
     * 游戏主循环 Runnable。
     *
     * <p>以约60FPS（16ms间隔）不断调用 game.update() 更新物理状态，
     * 并触发 View 重绘。与贪吃蛇/俄罗斯方块不同，Flappy Bird
     * 使用固定帧率而非可变间隔，因为需要流畅的物理模拟。</p>
     */
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

    /**
     * Activity 创建回调。
     *
     * <p>初始化游戏实例、FlappyView（动态添加到布局）、
     * 按钮事件和触摸监听。</p>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flappy);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_flappy);

        usageStore = new GameUsageStore(this);

        // 动态创建 FlappyView 并添加到布局中
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

        // 监听触摸事件，仅在游戏结束按下时记录分数
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

    /**
     * Activity 恢复回调。
     *
     * <p>启动游戏主循环。游戏在 onResume 时自动恢复运行。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        handler.post(gameLoop);
    }

    /**
     * Activity 暂停回调。
     *
     * <p>停止游戏主循环，避免后台运行消耗资源。</p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacks(gameLoop);
        }
    }

    /**
     * Activity 销毁回调。
     *
     * <p>停止游戏循环，清除所有 Handler 回调，释放引用以避免内存泄漏。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        game = null;
        flappyView = null;
        handler = null;
    }
}
