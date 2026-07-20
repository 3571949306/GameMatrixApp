package com.gamecenter.app.browser.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 阅读列表实体（P1-3）。
 * <p>用户将网页加入"稍后阅读"列表，统一管理后续阅读。</p>
 */
@Entity(
    tableName = "browser_reading_list",
    indices = {@Index(value = "url", unique = true)}
)
public class BrowserReadingListEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String url = "";

    @NonNull
    private String title = "";

    /** 内容摘要（前 N 字），用于列表展示 */
    @ColumnInfo(defaultValue = "")
    @NonNull
    private String summary = "";

    /** 站点 host，用于显示 favicon */
    @ColumnInfo(defaultValue = "")
    @NonNull
    private String host = "";

    /** 收藏时间（毫秒） */
    private long savedAt;

    /** 是否已读：0=未读，1=已读 */
    @ColumnInfo(defaultValue = "0")
    private int read;

    public long getId() { return id; }
    @NonNull public String getUrl() { return url; }
    @NonNull public String getTitle() { return title; }
    @NonNull public String getSummary() { return summary; }
    @NonNull public String getHost() { return host; }
    public long getSavedAt() { return savedAt; }
    public int getRead() { return read; }

    public void setId(long id) { this.id = id; }
    public void setUrl(@NonNull String url) { this.url = url; }
    public void setTitle(@NonNull String title) { this.title = title; }
    public void setSummary(@NonNull String summary) { this.summary = summary; }
    public void setHost(@NonNull String host) { this.host = host; }
    public void setSavedAt(long savedAt) { this.savedAt = savedAt; }
    public void setRead(int read) { this.read = read; }
}
