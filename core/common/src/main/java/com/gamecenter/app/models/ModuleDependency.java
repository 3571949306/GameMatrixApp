package com.gamecenter.app.models;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

/**
 * 模块依赖关系数据模型。
 * 
 * 定义模块之间的依赖关系：依赖模块 ID、最低版本、最高版本等。
 * 用于模块依赖管理和加载顺序计算。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleDependency implements Parcelable {
    
    /** 依赖模块 ID */
    @NonNull
    private String moduleId;
    
    /** 被依赖的模块 ID */
    @NonNull
    private String dependsOn;
    
    /** 最低依赖版本（包含） */
    @NonNull
    private String minVersion;
    
    /** 最高依赖版本（包含，空字符串表示无上限） */
    @NonNull
    private String maxVersion;
    
    /** 依赖是否可选（可选依赖失败时仅警告） */
    private boolean optional;
    
    /**
     * 默认构造函数。
     */
    public ModuleDependency() {
        this.moduleId = "";
        this.dependsOn = "";
        this.minVersion = "1.0.0";
        this.maxVersion = "";
        this.optional = false;
    }
    
    /**
     * 完整参数构造函数。
     */
    public ModuleDependency(@NonNull String moduleId, @NonNull String dependsOn) {
        this.moduleId = moduleId != null ? moduleId : "";
        this.dependsOn = dependsOn != null ? dependsOn : "";
        this.minVersion = "1.0.0";
        this.maxVersion = "";
        this.optional = false;
    }
    
    /**
     * 带版本约束的构造函数。
     */
    public ModuleDependency(@NonNull String moduleId, @NonNull String dependsOn,
                           @NonNull String minVersion, @NonNull String maxVersion) {
        this.moduleId = moduleId != null ? moduleId : "";
        this.dependsOn = dependsOn != null ? dependsOn : "";
        this.minVersion = minVersion != null ? minVersion : "1.0.0";
        this.maxVersion = maxVersion != null ? maxVersion : "";
        this.optional = false;
    }
    
    // ========== Parcelable 实现 ==========
    
    protected ModuleDependency(Parcel in) {
        moduleId = in.readString();
        dependsOn = in.readString();
        minVersion = in.readString();
        maxVersion = in.readString();
        optional = in.readByte() != 0;
    }
    
    public static final Creator<ModuleDependency> CREATOR = new Creator<ModuleDependency>() {
        @Override
        public ModuleDependency createFromParcel(Parcel in) {
            return new ModuleDependency(in);
        }
        
        @Override
        public ModuleDependency[] newArray(int size) {
            return new ModuleDependency[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(moduleId);
        dest.writeString(dependsOn);
        dest.writeString(minVersion);
        dest.writeString(maxVersion);
        dest.writeByte((byte) (optional ? 1 : 0));
    }
    
    // ========== Getter 和 Setter 方法 ==========
    
    @NonNull
    public String getModuleId() {
        return moduleId;
    }
    
    public void setModuleId(@NonNull String moduleId) {
        this.moduleId = moduleId != null ? moduleId : "";
    }
    
    @NonNull
    public String getDependsOn() {
        return dependsOn;
    }
    
    public void setDependsOn(@NonNull String dependsOn) {
        this.dependsOn = dependsOn != null ? dependsOn : "";
    }
    
    @NonNull
    public String getMinVersion() {
        return minVersion;
    }
    
    public void setMinVersion(@NonNull String minVersion) {
        this.minVersion = minVersion != null ? minVersion : "1.0.0";
    }
    
    @NonNull
    public String getMaxVersion() {
        return maxVersion;
    }
    
    public void setMaxVersion(@NonNull String maxVersion) {
        this.maxVersion = maxVersion != null ? maxVersion : "";
    }
    
    public boolean isOptional() {
        return optional;
    }
    
    public void setOptional(boolean optional) {
        this.optional = optional;
    }
    
    /**
     * 检查指定的版本是否满足此依赖约束。
     * 
     * @param versionCode 待检查的版本号
     * @param minRequired 依赖模块的最低版本号
     * @return 满足返回 true，否则返回 false
     */
    public boolean isSatisfied(int versionCode, int minRequired) {
        if (versionCode < minRequired) {
            return false;
        }
        // 如果有最高版本限制，检查是否超出
        if (!maxVersion.isEmpty()) {
            // 简化实现：假设 maxVersion 也是 versionCode
            // 实际应使用语义化版本比较
            return versionCode <= parseVersionCode(maxVersion);
        }
        return true;
    }
    
    /**
     * 将版本名称转换为版本号（简化实现）。
     * 
     * @param versionName 版本名称（如 "1.1.0"）
     * @return 版本号
     */
    private int parseVersionCode(@NonNull String versionName) {
        try {
            String[] parts = versionName.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + patch;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    @NonNull
    public String toString() {
        return "ModuleDependency{" +
                "moduleId='" + moduleId + '\'' +
                ", dependsOn='" + dependsOn + '\'' +
                ", minVersion='" + minVersion + '\'' +
                ", maxVersion='" + maxVersion + '\'' +
                ", optional=" + optional +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ModuleDependency that = (ModuleDependency) obj;
        return moduleId.equals(that.moduleId) && dependsOn.equals(that.dependsOn);
    }
    
    @Override
    public int hashCode() {
        return moduleId.hashCode() + dependsOn.hashCode();
    }
}
