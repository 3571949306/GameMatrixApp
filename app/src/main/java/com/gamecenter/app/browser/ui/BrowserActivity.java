package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.gamecenter.app.R;

/**
 * 浏览器主页面 Activity - 浏览器模块的外壳容器。
 *
 * <p>职责：加载 BrowserFragment，传递初始 URL。
 */
public class BrowserActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrowserActivity.class));
    }

    public static void start(Context context, String url) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_browser);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container), (v, insets) -> {
            v.setPadding(
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            String url = getIntent().getStringExtra("url");
            BrowserFragment fragment = BrowserFragment.newInstance(url);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment, BrowserFragment.TAG);
            transaction.commit();
        }
    }
}
