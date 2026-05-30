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
import com.gamecenter.app.R;

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

        handler.postDelayed(this::playExitAnimation, 500 + SPLASH_DURATION);
    }

    private void playExitAnimation() {
        View root = findViewById(R.id.splash_root);
        root.animate()
                .alpha(0.0f)
                .setDuration(350)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
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
