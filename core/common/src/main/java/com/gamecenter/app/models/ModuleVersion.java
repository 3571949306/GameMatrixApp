package com.gamecenter.app.models;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Date;

/**
 * 模块版本信息数据模型。
 * 
 * 包含特定版本的详细信息：版本名、版本号、更新日志、下载地址等。
 * 用于版本管理和更新检查。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleVersion implements Parcelable {
    
    /** 版本名称（如 "1.1.0"） */
    @NonNull
    private String versionName;
    
    /** 版本号（用于版本比较） */
    private int versionCode;
    
    /** 更新日志 */
    @NonNull
    private String changelog;
    
    /** 此版本的下载 URL */
    @NonNull
    private String downloadUrl;
    
    /** 此版本的文件大小（字节） */
    private long fileSize;
    
    /** 此版本文件的 SHA-256 哈希值 */
    @NonNull
    private String sha256;
    
    /** 发布日期 */
    @NonNull
    private Date releaseDate;
    
    /** 是否为预发布版本 */
    private boolean preRelease;
    
    /** 最低支持的框架版本号 */
    private int minFrameworkVersion;
    
    /** 此版本的最低 SDK 版本要求 */
    private int minSdkVersion;
    
    /** 此版本的 target SDK 版本 */
    private int targetSdkVersion;
    
    /**
     * 默认构造函数。
     */
    public ModuleVersion() {
        this.versionName = "1.0.0";
        this.versionCode = 1;
        this.changelog = "";
        this.downloadUrl = "";
        this.fileSize = 0L;
        this.sha256 = "";
        this.releaseDate = new Date();
        this.preRelease = false;
        this.minFrameworkVersion = 1;
        this.minSdkVersion = 21;
        this.targetSdkVersion = 34;
    }
    
    /**
     * 完整参数构造函数。
     */
    public ModuleVersion(@NonNull String versionName, int versionCode, 
                        @NonNull String downloadUrl, long fileSize) {
        this.versionName = versionName != null ? versionName : "1.0.0";
        this.versionCode = Math.max(versionCode, 1);
        this.changelog = "";
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
        this.fileSize = Math.max(fileSize, 0L);
        this.sha256 = "";
        this.releaseDate = new Date();
        this.preRelease = false;
        this.minFrameworkVersion = 1;
        this.minSdkVersion = 21;
        this.targetSdkVersion = 34;
    }
    
    // ========== Parcelable 实现 ==========
    
    protected ModuleVersion(Parcel in) {
        versionName = in.readString();
        versionCode = in.readInt();
        changelog = in.readString();
        downloadUrl = in.readString();
        fileSize = in.readLong();
        sha256 = in.readString();
        long releaseTime = in.readLong();
        releaseDate = new Date(releaseTime);
        preRelease = in.readByte() != 0;
        minFrameworkVersion = in.readInt();
        minSdkVersion = in.readInt();
        targetSdkVersion = in.readInt();
    }
    
    public static final Creator<ModuleVersion> CREATOR = new Creator<ModuleVersion>() {
        @Override
        public ModuleVersion createFromParcel(Parcel in) {
            return new ModuleVersion(in);
        }
        
        @Override
        public ModuleVersion[] newArray(int size) {
            return new ModuleVersion[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(versionName);
        dest.writeInt(versionCode);
        dest.writeString(changelog);
        dest.writeString(downloadUrl);
        dest.writeLong(fileSize);
        dest.writeString(sha256);
        dest.writeLong(releaseDate.getTime());
        dest.writeByte((byte) (preRelease ? 1 : 0));
        dest.writeInt(minFrameworkVersion);
        dest.writeInt(minSdkVersion);
        dest.writeInt(targetSdkVersion);
    }
    
    // ========== Getter 和 Setter 方法 ==========
    
    @NonNull
    public String getVersionName() {
        return versionName;
    }
    
    public void setVersionName(@NonNull String versionName) {
        this.versionName = versionName != null ? versionName : "1.0.0";
    }
    
    public int getVersionCode() {
        return versionCode;
    }
    
    public void setVersionCode(int versionCode) {
        this.versionCode = Math.max(versionCode, 1);
    }
    
    @NonNull
    public String getChangelog() {
        return changelog;
    }
    
    public void setChangelog(@NonNull String changelog) {
        this.changelog = changelog != null ? changelog : "";
    }
    
    @NonNull
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(@NonNull String downloadUrl) {
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = Math.max(fileSize, 0L);
    }
    
    @NonNull
    public String getSha256() {
        return sha256;
    }
    
    public void setSha256(@NonNull String sha256) {
        this.sha256 = sha256 != null ? sha256 : "";
    }
    
    @NonNull
    public Date getReleaseDate() {
        return releaseDate;
    }
    
    public void setReleaseDate(@NonNull Date releaseDate) {
        this.releaseDate = releaseDate != null ? releaseDate : new Date();
    }
    
    public boolean isPreRelease() {
        return preRelease;
    }
    
    public void setPreRelease(boolean preRelease) {
        this.preRelease = preRelease;
    }
    
    public int getMinFrameworkVersion() {
        return minFrameworkVersion;
    }
    
    public void setMinFrameworkVersion(int minFrameworkVersion) {
        this.minFrameworkVersion = Math.max(minFrameworkVersion, 1);
    }
    
    public int getMinSdkVersion() {
        return minSdkVersion;
    }
    
    public void setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = Math.max(minSdkVersion, 21);
    }
    
    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }
    
    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = Math.max(targetSdkVersion, minSdkVersion);
    }
    
    /**
     * 检查此版本是否兼容指定的框架版本。
     * 
     * @param frameworkVersionCode 框架版本号
     * @return 兼容返回 true，否则返回 false
     */
    public boolean isCompatibleWithFramework(int frameworkVersionCode) {
        return frameworkVersionCode >= minFrameworkVersion;
    }
    
    /**
     * 检查此版本是否兼容指定的 Android SDK 版本。
     * 
     * @param sdkVersion Android SDK 版本号
     * @return 兼容返回 true，否则返回 false
     */
    public boolean isCompatibleWithSdk(int sdkVersion) {
        return sdkVersion >= minSdkVersion;
    }
    
    @Override
    @NonNull
    public String toString() {
        return "ModuleVersion{" +
                "versionName='" + versionName + '\'' +
                ", versionCode=" + versionCode +
                ", preRelease=" + preRelease +
                ", releaseDate=" + releaseDate +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ModuleVersion that = (ModuleVersion) obj;
        return versionCode == that.versionCode;
    }
    
    @Override
    public int hashCode() {
        return versionCode;
    }
}
