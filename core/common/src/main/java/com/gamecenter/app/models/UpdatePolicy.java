package com.gamecenter.app.models;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

/**
 * 模块更新策略数据模型。
 * 
 * 定义模块的更新策略：自动更新、预发布版本允许、更新通道、最大重试次数等。
 * 用于控制模块更新行为。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class UpdatePolicy implements Parcelable {
    
    /** 模块 ID */
    @NonNull
    private String moduleId;
    
    /** 是否允许自动更新 */
    private boolean autoUpdate;
    
    /** 是否允许预发布版本 */
    private boolean allowPrerelease;
    
    /** 更新通道：stable、beta、alpha */
    @NonNull
    private String updateChannel;
    
    /** 下载失败时的最大重试次数 */
    private int maxRetries;
    
    /** 更新检查间隔（小时） */
    private int checkIntervalHours;
    
    /** 是否允许后台下载 */
    private boolean allowBackgroundDownload;
    
    /** 是否强制更新（用户无法跳过） */
    private boolean forceUpdate;
    
    /** 更新通知类型：none、silent、notification、dialog */
    @NonNull
    private String notificationType;
    
    /**
     * 默认构造函数。
     */
    public UpdatePolicy() {
        this.moduleId = "";
        this.autoUpdate = true;
        this.allowPrerelease = false;
        this.updateChannel = "stable";
        this.maxRetries = 3;
        this.checkIntervalHours = 24;
        this.allowBackgroundDownload = true;
        this.forceUpdate = false;
        this.notificationType = "notification";
    }
    
    /**
     * 完整参数构造函数。
     */
    public UpdatePolicy(@NonNull String moduleId) {
        this.moduleId = moduleId != null ? moduleId : "";
        this.autoUpdate = true;
        this.allowPrerelease = false;
        this.updateChannel = "stable";
        this.maxRetries = 3;
        this.checkIntervalHours = 24;
        this.allowBackgroundDownload = true;
        this.forceUpdate = false;
        this.notificationType = "notification";
    }
    
    // ========== Parcelable 实现 ==========
    
    protected UpdatePolicy(Parcel in) {
        moduleId = in.readString();
        autoUpdate = in.readByte() != 0;
        allowPrerelease = in.readByte() != 0;
        updateChannel = in.readString();
        maxRetries = in.readInt();
        checkIntervalHours = in.readInt();
        allowBackgroundDownload = in.readByte() != 0;
        forceUpdate = in.readByte() != 0;
        notificationType = in.readString();
    }
    
    public static final Creator<UpdatePolicy> CREATOR = new Creator<UpdatePolicy>() {
        @Override
        public UpdatePolicy createFromParcel(Parcel in) {
            return new UpdatePolicy(in);
        }
        
        @Override
        public UpdatePolicy[] newArray(int size) {
            return new UpdatePolicy[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(moduleId);
        dest.writeByte((byte) (autoUpdate ? 1 : 0));
        dest.writeByte((byte) (allowPrerelease ? 1 : 0));
        dest.writeString(updateChannel);
        dest.writeInt(maxRetries);
        dest.writeInt(checkIntervalHours);
        dest.writeByte((byte) (allowBackgroundDownload ? 1 : 0));
        dest.writeByte((byte) (forceUpdate ? 1 : 0));
        dest.writeString(notificationType);
    }
    
    // ========== Getter 和 Setter 方法 ==========
    
    @NonNull
    public String getModuleId() {
        return moduleId;
    }
    
    public void setModuleId(@NonNull String moduleId) {
        this.moduleId = moduleId != null ? moduleId : "";
    }
    
    public boolean isAutoUpdate() {
        return autoUpdate;
    }
    
    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }
    
    public boolean isAllowPrerelease() {
        return allowPrerelease;
    }
    
    public void setAllowPrerelease(boolean allowPrerelease) {
        this.allowPrerelease = allowPrerelease;
    }
    
    @NonNull
    public String getUpdateChannel() {
        return updateChannel;
    }
    
    public void setUpdateChannel(@NonNull String updateChannel) {
        this.updateChannel = updateChannel != null ? updateChannel : "stable";
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(maxRetries, 0);
    }
    
    public int getCheckIntervalHours() {
        return checkIntervalHours;
    }
    
    public void setCheckIntervalHours(int checkIntervalHours) {
        this.checkIntervalHours = Math.max(checkIntervalHours, 1);
    }
    
    public boolean isAllowBackgroundDownload() {
        return allowBackgroundDownload;
    }
    
    public void setAllowBackgroundDownload(boolean allowBackgroundDownload) {
        this.allowBackgroundDownload = allowBackgroundDownload;
    }
    
    public boolean isForceUpdate() {
        return forceUpdate;
    }
    
    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }
    
    @NonNull
    public String getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(@NonNull String notificationType) {
        this.notificationType = notificationType != null ? notificationType : "notification";
    }
    
    /**
     * 检查指定的版本是否应该被安装（根据更新策略）。
     * 
     * @param version 待检查的版本
     * @return 应该安装返回 true，否则返回 false
     */
    public boolean shouldInstallVersion(@NonNull ModuleVersion version) {
        if (version == null) {
            return false;
        }
        
        // 预发布版本检查
        if (version.isPreRelease() && !allowPrerelease) {
            return false;
        }
        
        // 更新通道检查（简化实现）
        // 实际应实现更精细的通道匹配逻辑
        return true;
    }
    
    @Override
    @NonNull
    public String toString() {
        return "UpdatePolicy{" +
                "moduleId='" + moduleId + '\'' +
                ", autoUpdate=" + autoUpdate +
                ", updateChannel='" + updateChannel + '\'' +
                ", forceUpdate=" + forceUpdate +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UpdatePolicy that = (UpdatePolicy) obj;
        return moduleId.equals(that.moduleId);
    }
    
    @Override
    public int hashCode() {
        return moduleId.hashCode();
    }
}
