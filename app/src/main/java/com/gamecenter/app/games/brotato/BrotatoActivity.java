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

/**
 * Brotato 风格射击生存游戏的 Activity 控制器。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理游戏生命周期（开始、重启、销毁）</li>
 *   <li>处理虚拟摇杆触摸输入，将方向指令传递给游戏逻辑</li>
 *   <li>驱动 ~60fps 的游戏主循环（通过 Handler + Runnable 定时调度）</li>
 *   <li>在升级阶段展示升级选项面板，供玩家选择强化</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 Handler.postDelayed 实现 16ms 间隔的游戏循环，而非 Choreographer，
 *       以简化实现并保证在主线程上更新 UI</li>
 *   <li>摇杆采用"按下位置为圆心"的浮动模式，手指移动距离映射为方向输入</li>
 * </ul>
 */
public class BrotatoActivity extends AppCompatActivity {

    /** 游戏画面渲染视图 */
    private BrotatoView gameView;

    /** 开始界面面板（包含难度选择等） */
    private LinearLayout startPanel;

    /** 底部控制面板（重启、刷新升级等按钮） */
    private LinearLayout controlPanel;

    /** 升级选择面板（显示三个升级选项） */
    private LinearLayout upgradePanel;

    /** 玩家属性信息文本 */
    private TextView tvStats;

    /** 武器信息文本 */
    private TextView tvWeapons;

    /** 升级面板标题文本 */
    private TextView tvUpgradeTitle;

    /** 三个升级选项按钮 */
    private MaterialButton[] optionButtons;

    /** 游戏核心逻辑对象 */
    private BrotatoGame game;

    /** 用于调度游戏主循环的 Handler（绑定主线程 Looper） */
    private Handler gameHandler;

    /** 游戏主循环的 Runnable，每 16ms 执行一次 */
    private Runnable gameRunnable;

    /** 游戏循环是否正在运行 */
    private boolean isRunning = false;

    /** 升级面板是否正在显示（防止重复弹出） */
    private boolean upgradeShowing = false;

    /** 虚拟摇杆是否处于激活状态 */
    private boolean joystickActive = false;

    /** 摇杆圆心 X 坐标（手指按下时的位置） */
    private float joystickBaseX;

    /** 摇杆圆心 Y 坐标（手指按下时的位置） */
    private float joystickBaseY;

    /** 摇杆归一化方向输入 X 分量，范围 [-1, 1] */
    private float joystickInputX;

    /** 摇杆归一化方向输入 Y 分量，范围 [-1, 1] */
    private float joystickInputY;

    /**
     * Activity 创建时的初始化入口。
     * <p>
     * 完成视图绑定、游戏对象创建、按钮与触摸控制设置，
     * 以及初始信息面板的刷新。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
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

    /**
     * 初始化所有按钮的点击事件监听器。
     * <p>
     * 包括：开始按钮、教程按钮、重启按钮、刷新升级选项按钮，
     * 以及三个升级选项按钮和游戏视图的点击重启逻辑。
     */
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

        // 为每个升级选项按钮绑定对应索引的选择回调
        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> chooseUpgrade(index));
        }

        // 游戏结束后点击画面可快速重新开始
        gameView.setOnClickListener(v -> {
            if (game.isGameOver()) {
                startGame();
            }
        });
    }

    /**
     * 设置游戏视图的触摸监听器，实现浮动虚拟摇杆。
     * <p>
     * 摇杆逻辑：
     * <ul>
     *   <li>ACTION_DOWN：记录按下位置为摇杆圆心，激活摇杆</li>
     *   <li>ACTION_MOVE：根据手指偏移计算归一化方向输入</li>
     *   <li>ACTION_UP/CANCEL：释放摇杆，归零输入</li>
     * </ul>
     * 当游戏未运行、已结束或等待升级时，触摸事件被忽略。
     */
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

    /**
     * 根据手指当前位置更新摇杆方向输入。
     * <p>
     * 计算手指相对于按下位置的偏移量，限制在摇杆半径内，
     * 并归一化为 [-1, 1] 的方向值。当偏移量过小时（低于半径的 18%）视为死区，输入归零。
     *
     * @param touchX 当前触摸点 X 坐标
     * @param touchY 当前触摸点 Y 坐标
     */
    private void updateJoystick(float touchX, float touchY) {
        float dx = touchX - joystickBaseX;
        float dy = touchY - joystickBaseY;
        // 摇杆半径：取 64px 与视图宽度 14% 中的较大值
        float radius = Math.max(64f, gameView.getWidth() * 0.14f);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        // 将偏移量钳制在摇杆半径内
        if (len > radius) {
            dx = dx / len * radius;
            dy = dy / len * radius;
            len = radius;
        }
        // 死区处理：偏移量小于半径 18% 时视为无输入
        if (len < radius * 0.18f) {
            joystickInputX = 0f;
            joystickInputY = 0f;
        } else {
            joystickInputX = dx / radius;
            joystickInputY = dy / radius;
        }
        gameView.setJoystick(true, joystickBaseX, joystickBaseY, joystickInputX, joystickInputY);
    }

    /**
     * 启动（或重新启动）游戏。
     * <p>
     * 重置游戏状态，隐藏开始面板，显示控制面板，
     * 启动以 16ms 为间隔的主循环。循环内依次执行：
     * 处理摇杆输入 → 更新游戏逻辑 → 检查升级触发 → 刷新 UI。
     * 游戏结束后自动停止循环。
     */
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

                // 将摇杆输入传递给游戏逻辑
                if (joystickActive && !game.isWaitingForUpgrade()) {
                    game.movePlayerInput(joystickInputX, joystickInputY);
                }
                game.update(System.currentTimeMillis());

                // 升级触发检测：仅在首次进入升级状态时弹出面板
                if (game.isWaitingForUpgrade() && !upgradeShowing) {
                    showUpgradePanel();
                }
                updateInfoPanels();
                gameView.invalidate();

                if (!game.isGameOver()) {
                    // 约 60fps 的循环间隔
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

    /**
     * 重启游戏，回到初始开始界面。
     * <p>
     * 停止游戏循环，重置游戏状态，隐藏游戏中的面板，
     * 重新显示开始面板。
     */
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

    /**
     * 显示升级选择面板。
     * <p>
     * 若当前没有可用的升级选项，则先随机生成一组。
     * 面板标题显示当前等级，三个按钮分别显示对应选项的标题和描述。
     * 显示面板的同时清除摇杆状态，避免误操作。
     */
    private void showUpgradePanel() {
        List<BrotatoGame.UpgradeOption> options = game.getUpgradeOptions();
        // 如果选项为空（例如首次进入升级），先随机生成
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

    /**
     * 玩家选择一个升级选项后的处理。
     * <p>
     * 将选择索引传递给游戏逻辑执行升级效果，
     * 隐藏升级面板，恢复游戏运行。
     *
     * @param index 选择的升级选项索引（0、1 或 2）
     */
    private void chooseUpgrade(int index) {
        game.chooseUpgrade(index);
        upgradePanel.setVisibility(View.GONE);
        upgradeShowing = false;
        updateInfoPanels();
        gameView.invalidate();
    }

    /**
     * 刷新玩家属性和武器信息的文本显示。
     */
    private void updateInfoPanels() {
        if (tvStats != null) {
            tvStats.setText(game.getStatsText());
        }
        if (tvWeapons != null) {
            tvWeapons.setText(game.getWeaponsText());
        }
    }

    /**
     * 从 Handler 中移除待执行的游戏循环回调，停止循环。
     */
    private void removeGameLoop() {
        if (gameRunnable != null) {
            gameHandler.removeCallbacks(gameRunnable);
        }
    }

    /**
     * 重置摇杆状态为未激活，归零方向输入，并通知视图更新。
     */
    private void clearJoystick() {
        joystickActive = false;
        joystickInputX = 0f;
        joystickInputY = 0f;
        if (gameView != null) {
            gameView.setJoystick(false, joystickBaseX, joystickBaseY, 0f, 0f);
        }
    }

    /**
     * 处理返回键：若当前在游戏中则返回开始界面，否则执行默认返回行为。
     */
    @Override
    public void onBackPressed() {
        if (startPanel.getVisibility() == View.GONE) {
            restartGame();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Activity 销毁时停止游戏循环，防止内存泄漏。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        removeGameLoop();
    }
}
