package com.gamecenter.app.browser.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "browser_search_history")
public class SearchHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull private String keyword = "";

    @ColumnInfo(defaultValue = "baidu")
    @NonNull private String searchEngine = "baidu";

    private long createTime;

    @ColumnInfo(defaultValue = "1")
    private int count = 1;

    public long getId() { return id; }
    @NonNull public String getKeyword() { return keyword; }
    @NonNull public String getSearchEngine() { return searchEngine; }
    public long getCreateTime() { return createTime; }
    public int getCount() { return count; }

    public void setId(long id) { this.id = id; }
    public void setKeyword(@NonNull String keyword) { this.keyword = keyword; }
    public void setSearchEngine(@NonNull String searchEngine) { this.searchEngine = searchEngine; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public void setCount(int count) { this.count = count; }
}
