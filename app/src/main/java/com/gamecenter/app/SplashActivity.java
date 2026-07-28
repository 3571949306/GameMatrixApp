package com.gamecenter.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.ui.LaunchTimeTracker;

/**
 * Launch screen Activity - shows logo animation then auto-enters MainActivity.
 *
 * Animation flow:
 * 1. Logo fade-in + scale (900ms)
 * 2. App name & tagline fade-in (delay 400ms, 700ms)
 * 3. Hold for 1200ms then fade-out and enter MainActivity
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 400L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Batch 12-4 (APP_LAUNCH_TIME_DISPLAY): 在 SplashActivity 入口最早处记录启动开始时间，
        // 用于后续在首页显示启动耗时。必须在所有其他初始化之前调用。
        if (BuildConfig.APP_LAUNCH_TIME_DISPLAY) {
            LaunchTimeTracker.INSTANCE.markStart();
        }
        // 接入 AndroidX SplashScreen API：必须在 super.onCreate 之前调用，
        // 以正确处理 Android 12+ 系统启动屏到应用主题的过渡，避免双启动屏闪烁。
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        playEnterAnimation();
    }

    private void playEnterAnimation() {
        View logo = findViewById(R.id.iv_logo);
        View appName = findViewById(R.id.tv_app_name);
        View tagline = findViewById(R.id.tv_tagline);

        logo.setAlpha(0f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);

        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1.0f);
        logoAlpha.setDuration(500);
        logoAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1.0f);
        logoScaleX.setDuration(500);
        logoScaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1.0f);
        logoScaleY.setDuration(500);
        logoScaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoSet.start();

        appName.setAlpha(0f);
        appName.setTranslationY(16f);

        ObjectAnimator nameAlpha = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1.0f);
        nameAlpha.setDuration(400);
        nameAlpha.setStartDelay(350);
        nameAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator nameTransY = ObjectAnimator.ofFloat(appName, "translationY", 16f, 0f);
        nameTransY.setDuration(400);
        nameTransY.setStartDelay(350);
        nameTransY.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet nameSet = new AnimatorSet();
        nameSet.playTogether(nameAlpha, nameTransY);
        nameSet.start();

        tagline.setAlpha(0f);
        tagline.setTranslationY(16f);

        ObjectAnimator tagAlpha = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1.0f);
        tagAlpha.setDuration(400);
        tagAlpha.setStartDelay(500);
        tagAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator tagTransY = ObjectAnimator.ofFloat(tagline, "translationY", 16f, 0f);
        tagTransY.setDuration(400);
        tagTransY.setStartDelay(500);
        tagTransY.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet tagSet = new AnimatorSet();
        tagSet.playTogether(tagAlpha, tagTransY);
        tagSet.start();

        // Batch 4 (SPLASH_ANIMATION_ENHANCE)：波纹扩散动画
        // 2 圈错开扩散，让启动屏更有"生命力"，避免静态简约风的单调感
        if (BuildConfig.SPLASH_ANIMATION_ENHANCE) {
            playRippleExpandAnimation();
        }

        handler.postDelayed(this::playExitAnimation, 500 + SPLASH_DURATION);
    }

    /**
     * Batch 4：波纹扩散动画。
     * <p>
     * 2 个波纹环错开启动，每个环从 scale 1.0 扩散到 2.2，alpha 从 0.6 渐变到 0，
     * duration 1200ms，第二圈比第一圈延迟 400ms 启动，形成连续扩散的视觉效果。
     * </p>
     */
    private void playRippleExpandAnimation() {
        View ring1 = findViewById(R.id.v_ripple_ring_1);
        View ring2 = findViewById(R.id.v_ripple_ring_2);
        if (ring1 != null) {
            startSingleRipple(ring1, 200);
        }
        if (ring2 != null) {
            startSingleRipple(ring2, 600);
        }
    }

    /**
     * 启动单个波纹环的扩散动画。
     *
     * @param ring       波纹环 View
     * @param startDelay 启动延迟（ms）
     */
    private void startSingleRipple(View ring, long startDelay) {
        ring.setVisibility(View.VISIBLE);
        ring.setAlpha(0.6f);
        ring.setScaleX(1.0f);
        ring.setScaleY(1.0f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1.0f, 2.2f);
        scaleX.setDuration(1200);
        scaleX.setStartDelay(startDelay);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1.0f, 2.2f);
        scaleY.setDuration(1200);
        scaleY.setStartDelay(startDelay);
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0.0f);
        alpha.setDuration(1200);
        alpha.setStartDelay(startDelay);
        alpha.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.start();
    }

    private void playExitAnimation() {
        View root = findViewById(R.id.splash_root);
        root.animate()
                .alpha(0.0f)
                .setDuration(350)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // P3-11: 首次启动时进入新手引导
                        if (!com.gamecenter.app.ui.onboarding.OnboardingActivity.isCompleted(SplashActivity.this)) {
                            Intent onboardingIntent = new Intent(SplashActivity.this,
                                    com.gamecenter.app.ui.onboarding.OnboardingActivity.class);
                            String navTab = getIntent().getStringExtra(MainActivity.EXTRA_NAV_TAB);
                            if (navTab != null) {
                                onboardingIntent.putExtra(MainActivity.EXTRA_NAV_TAB, navTab);
                            }
                            startActivity(onboardingIntent);
                            finish();
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            return;
                        }
                        Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
                        // Batch 14 (BROWSER_SMART_URL_BAR 测试辅助)：转发 EXTRA_NAV_TAB extra，
                        // 支持通过 adb am start --es extra_nav_tab browser 直接启动到指定 tab，
                        // 绕过 MIUI 底部手势拦截导致的 adb input 无法点击底部导航的问题。
                        String navTab = getIntent().getStringExtra(MainActivity.EXTRA_NAV_TAB);
                        if (navTab != null) {
                            mainIntent.putExtra(MainActivity.EXTRA_NAV_TAB, navTab);
                        }
                        startActivity(mainIntent);
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
