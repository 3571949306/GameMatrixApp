package com.gamecenter.app.browser.core.player.enhancement;

import androidx.annotation.NonNull;

/**
 * Phase H（播放器能力增强）的契约骨架。
 *
 * <p>仅当 Phase 0 兼容性矩阵（0-1）证明接管路径稳定后启动，避免在流沙上盖楼。
 * 本文件定义各增强能力的接口与占位，真实实现见各 TODO。
 *
 * <p>依赖：H-1 直链下载/原生兜底（VideoView）、H-2 后台音频（前台服务）、H-3 投屏（DLNA/Cast，需新依赖）、
 * H-4 手势与倍速自定义（依赖 B24 设置页）、H-5 播放历史与续播（Room 新表，AGENTS.md §4 高风险）。
 */
public final class PlayerEnhancementContracts {

    private PlayerEnhancementContracts() {}

    /** H-1 直链视频下载与原生播放器兜底。 */
    public interface DirectDownload {
        void downloadDirect(@NonNull String videoUrl);
        void playWithNativeFallback(@NonNull String videoUrl);
    }

    /** H-2 后台音频（息屏/切模块后继续放声音）。真实实现需评估前台服务。 */
    public interface BackgroundAudio {
        void startForegroundService();
        void stopForegroundService();
    }

    /** H-3 投屏（DLNA / Cast）。需调研 dependencies.gradle 冲突面后再实现。 */
    public interface Cast {
        void startDiscovery();
        void castTo(@NonNull String deviceId);
    }

    /** H-4 手势与倍速自定义（长按倍速档位、是否启用长按快进）。依赖 B24 设置页。 */
    public interface GestureCustomization {
        void applyLongPressRate(float rate);
        void setLongPressEnabled(boolean enabled);
    }

    /** H-5 播放历史与续播。Room 新表（AGENTS.md §4 高风险变更）。 */
    public interface PlayHistory {
        void record(@NonNull String pageUrl, long positionMs);
        long resumePosition(@NonNull String pageUrl);
    }
}
