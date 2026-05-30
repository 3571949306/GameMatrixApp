package com.gamecenter.app.models;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块信息数据模型。
 * 
 * 包含模块的元数据：ID、名称、版本、类型、依赖关系等。
 * 用于模块商店展示、模块加载和版本管理。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleInfo implements Parcelable {
    
    /** 模块唯一标识符（格式：category_name，如 game_doudizhu） */
    @NonNull
    private String moduleId;
    
    /** 模块显示名称 */
    @NonNull
    private String moduleName;
    
    /** 版本名称（如 "1.1.0"） */
    @NonNull
    private String versionName;
    
    /** 版本号（用于版本比较，如 110） */
    private int versionCode;
    
    /** 模块类型：game、tool、nav 等 */
    @NonNull
    private String type;
    
    /** 是否为内置模块 */
    private boolean builtIn;
    
    /** 模块依赖列表 */
    @NonNull
    private List<String> dependencies;
    
    /** 模块下载 URL */
    private String downloadUrl;
    
    /** 模块文件大小（字节） */
    private long fileSize;
    
    /** 模块文件的 SHA-256 哈希值 */
    private String sha256;
    
    /** 模块在商店中的分类 */
    private String storeCategory;
    
    /** 模块描述 */
    private String description;
    
    /** 模块图标资源 ID 或 URL */
    private String iconUrl;
    
    /** 最低支持的框架版本号 */
    private int minFrameworkVersion;
    
    /** 模块状态：available、installed、update_available、incompatible */
    private String status;
    
    /** 更新日志 */
    private String changelog;
    
    /**
     * 默认构造函数。
     */
    public ModuleInfo() {
        this.moduleId = "";
        this.moduleName = "";
        this.versionName = "1.0.0";
        this.versionCode = 1;
        this.type = "game";
        this.builtIn = false;
        this.dependencies = new ArrayList<>();
        this.downloadUrl = "";
        this.fileSize = 0L;
        this.sha256 = "";
        this.storeCategory = "game";
        this.description = "";
        this.iconUrl = "";
        this.minFrameworkVersion = 1;
        this.status = "available";
        this.changelog = "";
    }
    
    /**
     * 完整参数构造函数。
     */
    public ModuleInfo(@NonNull String moduleId, @NonNull String moduleName, 
                      @NonNull String versionName, int versionCode, @NonNull String type) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.type = type;
        this.builtIn = false;
        this.dependencies = new ArrayList<>();
        this.downloadUrl = "";
        this.fileSize = 0L;
        this.sha256 = "";
        this.storeCategory = type;
        this.description = "";
        this.iconUrl = "";
        this.minFrameworkVersion = 1;
        this.status = "available";
        this.changelog = "";
    }
    
    // ========== Parcelable 实现 ==========
    
    protected ModuleInfo(Parcel in) {
        moduleId = in.readString();
        moduleName = in.readString();
        versionName = in.readString();
        versionCode = in.readInt();
        type = in.readString();
        builtIn = in.readByte() != 0;
        dependencies = new ArrayList<>();
        in.readStringList(dependencies);
        downloadUrl = in.readString();
        fileSize = in.readLong();
        sha256 = in.readString();
        storeCategory = in.readString();
        description = in.readString();
        iconUrl = in.readString();
        minFrameworkVersion = in.readInt();
        status = in.readString();
        changelog = in.readString();
    }
    
    public static final Creator<ModuleInfo> CREATOR = new Creator<ModuleInfo>() {
        @Override
        public ModuleInfo createFromParcel(Parcel in) {
            return new ModuleInfo(in);
        }
        
        @Override
        public ModuleInfo[] newArray(int size) {
            return new ModuleInfo[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(moduleId);
        dest.writeString(moduleName);
        dest.writeString(versionName);
        dest.writeInt(versionCode);
        dest.writeString(type);
        dest.writeByte((byte) (builtIn ? 1 : 0));
        dest.writeStringList(dependencies);
        dest.writeString(downloadUrl);
        dest.writeLong(fileSize);
        dest.writeString(sha256);
        dest.writeString(storeCategory);
        dest.writeString(description);
        dest.writeString(iconUrl);
        dest.writeInt(minFrameworkVersion);
        dest.writeString(status);
        dest.writeString(changelog);
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
    public String getModuleName() {
        return moduleName;
    }
    
    public void setModuleName(@NonNull String moduleName) {
        this.moduleName = moduleName != null ? moduleName : "";
    }
    
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
    public String getType() {
        return type;
    }
    
    public void setType(@NonNull String type) {
        this.type = type != null ? type : "game";
    }
    
    public boolean isBuiltIn() {
        return builtIn;
    }
    
    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }
    
    @NonNull
    public List<String> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(@NonNull List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = Math.max(fileSize, 0L);
    }
    
    public String getSha256() {
        return sha256;
    }
    
    public void setSha256(String sha256) {
        this.sha256 = sha256 != null ? sha256 : "";
    }
    
    public String getStoreCategory() {
        return storeCategory;
    }
    
    public void setStoreCategory(String storeCategory) {
        this.storeCategory = storeCategory != null ? storeCategory : this.type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl != null ? iconUrl : "";
    }
    
    public int getMinFrameworkVersion() {
        return minFrameworkVersion;
    }
    
    public void setMinFrameworkVersion(int minFrameworkVersion) {
        this.minFrameworkVersion = Math.max(minFrameworkVersion, 1);
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status != null ? status : "available";
    }
    
    public String getChangelog() {
        return changelog;
    }
    
    public void setChangelog(String changelog) {
        this.changelog = changelog != null ? changelog : "";
    }
    
    @Override
    @NonNull
    public String toString() {
        return "ModuleInfo{" +
                "moduleId='" + moduleId + '\'' +
                ", moduleName='" + moduleName + '\'' +
                ", versionName='" + versionName + '\'' +
                ", versionCode=" + versionCode +
                ", type='" + type + '\'' +
                ", builtIn=" + builtIn +
                ", status='" + status + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ModuleInfo that = (ModuleInfo) obj;
        return moduleId.equals(that.moduleId) && versionCode == that.versionCode;
    }
    
    @Override
    public int hashCode() {
        return moduleId.hashCode() + versionCode;
    }
}
