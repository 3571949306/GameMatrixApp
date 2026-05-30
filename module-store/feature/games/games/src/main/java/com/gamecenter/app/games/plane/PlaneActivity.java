package com.gamecenter.app.games.plane;

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
 * 飞机大战游戏 Activity
 *
 * <p>职责：作为飞机大战游戏的入口界面，负责初始化游戏视图、
 * 管理游戏主循环（约60FPS）、记录得分以及提供重新开始和教程功能。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 Handler + Runnable 实现游戏主循环，每 16ms（约60FPS）刷新一次</li>
 *   <li>游戏更新传入当前时间戳（System.currentTimeMillis()），用于帧率无关的运动计算</li>
 *   <li>在 onResume 中启动循环、onPause 中暂停，避免后台消耗资源</li>
 *   <li>onDestroy 中释放 Handler、Game、View 引用，防止内存泄漏</li>
 *   <li>游戏结束时通过 OnTouchListener 记录得分到 GameUsageStore</li>
 * </ul>
 *
 * <p>布局：res/layout/activity_plane.xml</p>
 */
public class PlaneActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于 GameUsageStore 记录得分 */
    private static final String GAME_ID = "plane";

    /** 自定义游戏视图，负责绘制飞机/子弹/敌机和触摸控制 */
    private PlaneView planeView;

    /** 游戏逻辑核心，管理飞机位置、敌机生成、碰撞检测等 */
    private PlaneGame game;

    /** 主线程 Handler，用于调度游戏循环 */
    private Handler handler;

    /** 游戏循环是否运行中 */
    private boolean isRunning;

    /** 游戏使用统计存储，记录得分 */
    private GameUsageStore usageStore;

    /**
     * 游戏主循环 Runnable
     *
     * <p>每 16ms 执行一次（约60FPS），流程为：</p>
     * <ol>
     *   <li>调用 game.update(currentTimeMillis) 推进游戏状态</li>
     *   <li>调用 planeView.invalidate() 触发重绘</li>
     *   <li>通过 handler.postDelayed 递归调度下一次循环</li>
     * </ol>
     *
     * <p>注意：update 传入当前时间戳而非固定间隔，使运动计算与帧率解耦。</p>
     */
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                game.update(System.currentTimeMillis());
                planeView.invalidate();
                handler.postDelayed(this, 16);
            }
        }
    };

    /**
     * Activity 创建回调
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>设置布局并绑定标题</li>
     *   <li>创建 PlaneView 并替换布局中的占位 View</li>
     *   <li>创建 PlaneGame 并关联到视图</li>
     *   <li>初始化 GameUsageStore 和 Handler</li>
     *   <li>绑定重启和教程按钮</li>
     *   <li>注册触摸监听器，游戏结束时记录得分</li>
     * </ol>
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plane);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_plane);

        planeView = new PlaneView(this);
        findViewById(R.id.game_view_stub).setVisibility(android.view.View.GONE);
        ((android.widget.FrameLayout) findViewById(R.id.game_view_stub).getParent()).addView(planeView);

        game = new PlaneGame();
        planeView.setGame(game);

        usageStore = new GameUsageStore(this);
        handler = new Handler(Looper.getMainLooper());

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            planeView.invalidate();
        });

        MaterialButton btnTutorial = findViewById(R.id.btn_game_tutorial);
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showPlaneTutorial(this));

        planeView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && game.isGameOver()) {
                    usageStore.recordScore(GAME_ID, game.getScore());
                }
                return false;
            }
        });
    }

    /**
     * Activity 恢复时启动游戏循环
     *
     * <p>将 isRunning 设为 true 并通过 Handler 投递游戏循环任务。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        handler.post(gameLoop);
    }

    /**
     * Activity 暂停时停止游戏循环
     *
     * <p>将 isRunning 设为 false 并移除 Handler 中所有待执行的消息，
     * 防止后台持续消耗 CPU 资源。</p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Activity 销毁时释放所有资源
     *
     * <p>停止游戏循环，移除 Handler 回调，并将 handler、game、planeView 置空，
     * 防止 Activity 泄漏。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        handler = null;
        game = null;
        planeView = null;
    }
}
