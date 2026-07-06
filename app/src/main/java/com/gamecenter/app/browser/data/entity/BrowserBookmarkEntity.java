package com.gamecenter.app.browser.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "browser_bookmark")
public class BrowserBookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String title = "";

    @NonNull
    private String url = "";

    @NonNull
    private String host = "";

    @ColumnInfo(defaultValue = "default")
    @NonNull
    private String folderName = "default";

    private long createTime;

    private long updateTime;

    @ColumnInfo(defaultValue = "0")
    private int sortOrder = 0;

    public long getId() { return id; }
    @NonNull public String getTitle() { return title; }
    @NonNull public String getUrl() { return url; }
    @NonNull public String getHost() { return host; }
    @NonNull public String getFolderName() { return folderName; }
    public long getCreateTime() { return createTime; }
    public long getUpdateTime() { return updateTime; }
    public int getSortOrder() { return sortOrder; }

    public void setId(long id) { this.id = id; }
    public void setTitle(@NonNull String title) { this.title = title; }
    public void setUrl(@NonNull String url) { this.url = url; }
    public void setHost(@NonNull String host) { this.host = host; }
    public void setFolderName(@NonNull String folderName) { this.folderName = folderName; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
