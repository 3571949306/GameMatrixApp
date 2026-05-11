package com.gamecenter.app.games.doudizhu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;

/**
 * 斗地主游戏菜单界面 (DouDiZhu Menu Activity)
 * 作为斗地主游戏的入口界面
 * 竖屏显示，提供单机模式和联机模式选择
 */
public class DouDiZhuMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doudizhu_menu);

        initButtons();
    }

    private void initButtons() {
        // 单机模式按钮
        Button btnSinglePlayer = findViewById(R.id.btnSinglePlayer);
        if (btnSinglePlayer != null) {
            btnSinglePlayer.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuActivity.class);
                startActivity(intent);
            });
        }

        // 联机模式按钮
        Button btnOnline = findViewById(R.id.btnOnline);
        if (btnOnline != null) {
            btnOnline.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuOnlineActivity.class);
                startActivity(intent);
            });
        }

        Button btnRemoteP2P = findViewById(R.id.btnRemoteP2P);
        if (btnRemoteP2P != null) {
            btnRemoteP2P.setOnClickListener(v -> {
                Intent intent = new Intent(this, DouDiZhuOnlineActivity.class);
                intent.putExtra(DouDiZhuOnlineActivity.EXTRA_REMOTE_P2P, true);
                startActivity(intent);
            });
        }

        // 返回按钮
        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}
