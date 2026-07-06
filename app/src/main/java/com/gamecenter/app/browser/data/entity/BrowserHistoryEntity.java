package com.gamecenter.app.browser.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "browser_history")
public class BrowserHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String title = "";

    @NonNull
    private String url = "";

    @NonNull
    private String host = "";

    @ColumnInfo(defaultValue = "1")
    private int visitCount = 1;

    private long firstVisitTime;

    private long lastVisitTime;

    @Nullable
    private String faviconPath;

    @ColumnInfo(defaultValue = "0")
    private boolean isDeleted = false;

    public long getId() { return id; }
    @NonNull public String getTitle() { return title; }
    @NonNull public String getUrl() { return url; }
    @NonNull public String getHost() { return host; }
    public int getVisitCount() { return visitCount; }
    public long getFirstVisitTime() { return firstVisitTime; }
    public long getLastVisitTime() { return lastVisitTime; }
    @Nullable public String getFaviconPath() { return faviconPath; }
    public boolean isDeleted() { return isDeleted; }

    public void setId(long id) { this.id = id; }
    public void setTitle(@NonNull String title) { this.title = title; }
    public void setUrl(@NonNull String url) { this.url = url; }
    public void setHost(@NonNull String host) { this.host = host; }
    public void setVisitCount(int visitCount) { this.visitCount = visitCount; }
    public void setFirstVisitTime(long firstVisitTime) { this.firstVisitTime = firstVisitTime; }
    public void setLastVisitTime(long lastVisitTime) { this.lastVisitTime = lastVisitTime; }
    public void setFaviconPath(@Nullable String faviconPath) { this.faviconPath = faviconPath; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
