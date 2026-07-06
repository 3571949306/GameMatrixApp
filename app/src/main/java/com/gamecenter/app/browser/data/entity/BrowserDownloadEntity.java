package com.gamecenter.app.browser.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "browser_download")
public class BrowserDownloadEntity {

    public static final int STATUS_WAITING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull private String fileName = "";
    @NonNull private String url = "";
    @NonNull private String mimeType = "";
    @NonNull private String filePath = "";

    private long totalSize;
    private long downloadedSize;

    @ColumnInfo(defaultValue = "0")
    private int status = STATUS_WAITING;

    @ColumnInfo(defaultValue = "-1")
    private long systemDownloadId = -1;

    private long createTime;
    private long finishTime;

    public long getId() { return id; }
    @NonNull public String getFileName() { return fileName; }
    @NonNull public String getUrl() { return url; }
    @NonNull public String getMimeType() { return mimeType; }
    @NonNull public String getFilePath() { return filePath; }
    public long getTotalSize() { return totalSize; }
    public long getDownloadedSize() { return downloadedSize; }
    public int getStatus() { return status; }
    public long getSystemDownloadId() { return systemDownloadId; }
    public long getCreateTime() { return createTime; }
    public long getFinishTime() { return finishTime; }

    public void setId(long id) { this.id = id; }
    public void setFileName(@NonNull String fileName) { this.fileName = fileName; }
    public void setUrl(@NonNull String url) { this.url = url; }
    public void setMimeType(@NonNull String mimeType) { this.mimeType = mimeType; }
    public void setFilePath(@NonNull String filePath) { this.filePath = filePath; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public void setDownloadedSize(long downloadedSize) { this.downloadedSize = downloadedSize; }
    public void setStatus(int status) { this.status = status; }
    public void setSystemDownloadId(long systemDownloadId) { this.systemDownloadId = systemDownloadId; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public void setFinishTime(long finishTime) { this.finishTime = finishTime; }
}
