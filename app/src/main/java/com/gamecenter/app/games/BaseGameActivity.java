package com.gamecenter.app.games;

import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.utils.SoundManager;

/**
 * 游戏基类
 * 提供音效、震动、动画等通用功能
 */
public abstract class BaseGameActivity extends AppCompatActivity {

    protected SoundManager soundManager;
    protected Vibrator vibrator;
    protected SettingsManager settings;
    protected boolean soundEnabled = true;
    protected boolean vibrationEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = SettingsManager.getInstance(this);
        soundEnabled = settings.isSoundEnabled();
        vibrationEnabled = settings.isVibrationEnabled();

        soundManager = new SoundManager(this);
        soundManager.setEnabled(soundEnabled);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        loadGameSounds();
    }

    protected void loadGameSounds() {
        // 子类重写此方法加载游戏特定音效
    }

    protected void playClickSound() {
        if (soundManager != null) {
            soundManager.playSound(R.raw.sound_click_button);
        }
    }

    protected void vibrateShort() {
        if (vibrationEnabled && vibrator != null) {
            vibrator.vibrate(50);
        }
    }

    protected void vibrateLong() {
        if (vibrationEnabled && vibrator != null) {
            vibrator.vibrate(200);
        }
    }

    protected void animateView(View view, int animResId) {
        if (view != null) {
            Animation anim = AnimationUtils.loadAnimation(this, animResId);
            view.startAnimation(anim);
        }
    }

    protected void animateViewWithAction(View view, int animResId, Runnable onEnd) {
        if (view != null) {
            Animation anim = AnimationUtils.loadAnimation(this, animResId);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (onEnd != null) onEnd.run();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            view.startAnimation(anim);
        }
    }

    protected void playWinAnimation(View view) {
        animateView(view, R.anim.win_celebrate);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (soundManager != null) {
            soundManager.pauseBackgroundMusic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (soundManager != null) {
            soundManager.resumeBackgroundMusic();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
    }
}
