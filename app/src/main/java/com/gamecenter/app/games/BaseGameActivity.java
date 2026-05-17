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
 * 游戏Activity基类
 * <p>
 * 为所有游戏Activity提供通用的音效、震动和动画功能。
 * 子类继承此类后可直接使用音效播放、震动反馈和视图动画等能力，
 * 无需重复实现这些基础设施。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>音效和震动的开关状态由SettingsManager统一管理，Activity生命周期内自动同步</li>
 *   <li>SoundManager在onDestroy时释放资源，避免内存泄漏</li>
 *   <li>背景音乐在onPause时暂停、onResume时恢复，符合Android生命周期规范</li>
 *   <li>子类通过重写loadGameSounds()方法来加载各自特定的音效资源</li>
 * </ul>
 * </p>
 */
public abstract class BaseGameActivity extends AppCompatActivity {

    /** 音效管理器，负责播放游戏音效和背景音乐 */
    protected SoundManager soundManager;
    /** 震动器，用于提供触觉反馈 */
    protected Vibrator vibrator;
    /** 设置管理器，读取用户的音效和震动偏好 */
    protected SettingsManager settings;
    /** 音效是否启用，由用户设置决定 */
    protected boolean soundEnabled = true;
    /** 震动是否启用，由用户设置决定 */
    protected boolean vibrationEnabled = true;

    /**
     * Activity创建时的初始化
     * <p>
     * 按顺序完成以下初始化工作：
     * 1. 从SettingsManager读取用户的音效和震动偏好
     * 2. 创建并配置SoundManager
     * 3. 获取系统震动器服务
     * 4. 调用loadGameSounds()让子类加载特定音效
     * </p>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 从全局设置中读取用户的音效和震动偏好
        settings = SettingsManager.getInstance(this);
        soundEnabled = settings.isSoundEnabled();
        vibrationEnabled = settings.isVibrationEnabled();

        // 初始化音效管理器并应用用户偏好
        soundManager = new SoundManager(this);
        soundManager.setEnabled(soundEnabled);

        // 获取系统震动服务
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // 让子类在此加载各自的游戏音效
        loadGameSounds();
    }

    /**
     * 加载游戏特定的音效资源
     * <p>
     * 子类应重写此方法来加载各自需要的音效文件。
     * 默认实现为空，不加载任何音效。
     * </p>
     */
    protected void loadGameSounds() {
    }

    /**
     * 播放点击音效
     * <p>
     * 播放通用的按钮点击音效，仅在soundManager不为null时播放。
     * </p>
     */
    protected void playClickSound() {
        if (soundManager != null) {
            soundManager.playSound(R.raw.sound_click_button);
        }
    }

    /**
     * 短震动反馈（50毫秒）
     * <p>
     * 用于轻量级的触觉反馈，如按钮点击、落子等场景。
     * 仅在震动启用且震动器可用时触发。
     * </p>
     */
    protected void vibrateShort() {
        if (vibrationEnabled && vibrator != null) {
            vibrator.vibrate(50);
        }
    }

    /**
     * 长震动反馈（200毫秒）
     * <p>
     * 用于较强烈的触觉反馈，如游戏胜利、碰撞等场景。
     * 仅在震动启用且震动器可用时触发。
     * </p>
     */
    protected void vibrateLong() {
        if (vibrationEnabled && vibrator != null) {
            vibrator.vibrate(200);
        }
    }

    /**
     * 对视图执行动画
     * <p>
     * 加载指定的动画资源并应用到目标视图上。
     * 如果视图为null则不执行任何操作，防止空指针异常。
     * </p>
     *
     * @param view       要执行动画的视图
     * @param animResId  动画资源ID，如R.anim.fade_in
     */
    protected void animateView(View view, int animResId) {
        if (view != null) {
            Animation anim = AnimationUtils.loadAnimation(this, animResId);
            view.startAnimation(anim);
        }
    }

    /**
     * 对视图执行动画并在动画结束时执行回调
     * <p>
     * 加载指定的动画资源并应用到目标视图上，动画播放完毕后执行指定的回调操作。
     * 适用于需要在动画结束后进行状态切换或页面跳转的场景。
     * </p>
     *
     * @param view       要执行动画的视图
     * @param animResId  动画资源ID
     * @param onEnd      动画结束后的回调，可为null
     */
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

    /**
     * 播放胜利庆祝动画
     * <p>
     * 对指定视图应用胜利庆祝动画效果（R.anim.win_celebrate）。
     * </p>
     *
     * @param view 要播放动画的视图
     */
    protected void playWinAnimation(View view) {
        animateView(view, R.anim.win_celebrate);
    }

    /**
     * Activity暂停时暂停背景音乐
     * <p>
     * 遵循Android生命周期规范，在Activity不可见时暂停背景音乐，
     * 避免在后台继续播放声音干扰用户。
     * </p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (soundManager != null) {
            soundManager.pauseBackgroundMusic();
        }
    }

    /**
     * Activity恢复时恢复背景音乐
     * <p>
     * Activity重新获得焦点时恢复背景音乐播放。
     * </p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (soundManager != null) {
            soundManager.resumeBackgroundMusic();
        }
    }

    /**
     * Activity销毁时释放音效资源
     * <p>
     * 释放SoundManager持有的所有音效资源，并将引用置为null，
     * 防止内存泄漏。这是资源清理的关键步骤。
     * </p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
    }
}
