package com.gamecenter.app.games.doudizhu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;

/**
 * 斗地主游戏菜单界面 (DouDiZhu Menu Activity)
 *
 * <p>作为斗地主游戏的入口界面，提供不同游戏模式的导航功能。</p>
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
 *   <li>按钮使用空指针保护（null check），防止布局中缺少对应按钮时崩溃</li>
 *   <li>远程P2P模式复用联机 Activity，通过 Intent Extra 区分模式</li>
 * </ul>
 */
public class DouDiZhuMenuActivity extends AppCompatActivity {

    /**
     * Activity 创建时的初始化入口。
     * <p>设置布局并初始化所有按钮及其点击事件。</p>
     *
     * @param savedInstanceState 保存的实例状态（用于恢复场景，此处未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doudizhu_menu);

        initButtons();
    }

    /**
     * 初始化所有菜单按钮及其点击监听器。
     *
     * <p>包含以下按钮：</p>
     * <ul>
     *   <li><b>单机模式</b>：跳转到 {@link DouDiZhuActivity}，与AI对战</li>
     *   <li><b>联机模式</b>：跳转到 {@link DouDiZhuOnlineActivity}，局域网对战</li>
     *   <li><b>远程P2P</b>：跳转到 {@link DouDiZhuOnlineActivity} 并携带
     *       {@code EXTRA_REMOTE_P2P=true} 标记，启用远程点对点模式</li>
     *   <li><b>返回</b>：关闭当前 Activity，返回上一页</li>
     * </ul>
     *
     * <p>每个按钮均做了空指针保护，避免布局文件中缺少对应 ID 时抛出 NullPointerException。</p>
     */
    private void initButtons() {
        // 单机模式按钮：启动本地AI对战
        Button btnSinglePlayer = findViewById(R.id.btnSinglePlayer);
        if (btnSinglePlayer != null) {
            btnSinglePlayer.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuActivity.class);
                startActivity(intent);
            });
        }

        // 联机模式按钮：启动局域网联机对战
        Button btnOnline = findViewById(R.id.btnOnline);
        if (btnOnline != null) {
            btnOnline.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuOnlineActivity.class);
                startActivity(intent);
            });
        }

        // 远程P2P按钮：启动远程点对点联机，通过 Extra 标记区分模式
        Button btnRemoteP2P = findViewById(R.id.btnRemoteP2P);
        if (btnRemoteP2P != null) {
            btnRemoteP2P.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuOnlineActivity.class);
                intent.putExtra(DouDiZhuOnlineActivity.EXTRA_REMOTE_P2P, true);
                startActivity(intent);
            });
        }

        // 返回按钮：关闭当前页面
        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}
