package com.gamecenter.app.games.doudizhu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.gamecenter.app.R;

/**
 * 斗地主游戏菜单界面 (DouDiZhu Menu Activity)
 *
 * <p>作为斗地主游戏的入口界面，提供不同游戏模式的导航功能。
 * 你可以把这个页面想象成棋牌室的"前台"——在这里选择你想怎么玩，
 * 然后前台会把你带到对应的牌桌。</p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>展示游戏模式选择（单机模式、联机模式、远程P2P模式）</li>
 *   <li>将用户导航到对应的游戏 Activity</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>竖屏显示，作为游戏入口页</li>
 *   <li>按钮使用空指针保护（null check），防止布局中缺少对应按钮时崩溃
 *       （就像"先确认门存在再去推"，避免撞墙）</li>
 *   <li>远程P2P模式复用联机 Activity，通过 Intent Extra 区分模式
 *       （同一个Activity，不同的"开关"控制行为）</li>
 * </ul>
 */
public class DouDiZhuMenuActivity extends AppCompatActivity {

    /**
     * Activity 创建时的初始化入口。
     * <p>设置布局并初始化所有按钮及其点击事件。
     * 这是Activity的"出生方法"，页面一打开就会自动调用。</p>
     *
     * @param savedInstanceState 保存的实例状态（用于恢复场景，此处未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doudizhu_menu);

        initTitleAnimation();
        initButtons();
    }

    /**
     * 初始化标题淡入动画。
     * <p>标题从完全透明渐变到完全不透明，持续500ms，
     * 使用 OvershootInterpolator 产生轻微的过冲效果，让动画更有活力。</p>
     */
    private void initTitleAnimation() {
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setAlpha(0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
            fadeIn.setDuration(500);
            fadeIn.setInterpolator(new OvershootInterpolator());
            fadeIn.start();
        }
    }

    /**
     * 初始化所有菜单按钮及其点击监听器。
     *
     * <p>包含以下按钮（每个按钮都做了空指针保护，避免布局文件缺少ID时崩溃）：</p>
     * <ul>
     *   <li><b>单机模式</b>：跳转到 {@link DouDiZhuActivity}，和两个AI电脑对战</li>
     *   <li><b>联机模式</b>：跳转到 {@link DouDiZhuOnlineActivity}，同一WiFi下和朋友对战</li>
     *   <li><b>远程P2P</b>：跳转到 {@link DouDiZhuOnlineActivity} 并携带
     *       {@code EXTRA_REMOTE_P2P=true} 标记，通过互联网远程对战</li>
     *   <li><b>返回</b>：关闭当前 Activity，返回上一页</li>
     * </ul>
     *
     * <p>每个按钮均做了空指针保护，避免布局文件中缺少对应 ID 时抛出 NullPointerException。</p>
     */
    private void initButtons() {
        // 单机模式按钮：启动本地AI对战
        AppCompatButton btnSinglePlayer = findViewById(R.id.btnSinglePlayer);
        if (btnSinglePlayer != null) {
            setupButtonAnimation(btnSinglePlayer);
            btnSinglePlayer.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuActivity.class);
                startActivity(intent);
            });
        }

        // 联机模式按钮：启动局域网联机对战
        AppCompatButton btnOnline = findViewById(R.id.btnOnline);
        if (btnOnline != null) {
            setupButtonAnimation(btnOnline);
            btnOnline.setOnClickListener(v -> {
                android.widget.Toast.makeText(
                        this,
                        "联机模式已移到模块商店，当前内置版仅保留单机模式",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            });
        }

        // 远程P2P按钮：启动远程点对点联机，通过 Extra 标记区分模式
        AppCompatButton btnRemoteP2P = findViewById(R.id.btnRemoteP2P);
        if (btnRemoteP2P != null) {
            setupButtonAnimation(btnRemoteP2P);
            btnRemoteP2P.setOnClickListener(v -> {
                android.widget.Toast.makeText(
                        this,
                        "远程 P2P 已移到模块商店，当前内置版仅保留单机模式",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            });
        }

        // 返回按钮：关闭当前页面
        AppCompatButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            setupButtonAnimation(btnBack);
            btnBack.setOnClickListener(v -> finish());
        }
    }

    /**
     * 为按钮添加按下缩放动画效果。
     * <p>当用户按下按钮时，按钮会缩小到0.95倍；松开时恢复原始大小。
     * 这种微妙的反馈让用户感受到按钮的"物理感"，提升交互体验。</p>
     *
     * @param button 要添加动画的按钮
     */
    private void setupButtonAnimation(AppCompatButton button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // 按下时缩小到 0.95x
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // 松开时恢复原始大小
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new BounceInterpolator())
                        .start();
                    break;
            }
            return false;
        });
    }
}
