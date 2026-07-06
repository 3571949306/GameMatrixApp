package com.gamecenter.app.browser.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.util.List;

@Dao
public interface BrowserDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BrowserDownloadEntity download);

    @Query("UPDATE browser_download SET status = :status WHERE id = :id")
    void updateStatus(long id, int status);

    @Query("UPDATE browser_download SET status = :status, downloadedSize = :downloadedSize WHERE id = :id")
    void updateProgress(long id, int status, long downloadedSize);

    @Query("UPDATE browser_download SET status = :status, finishTime = :finishTime WHERE id = :id")
    void updateFinishTime(long id, int status, long finishTime);

    @Query("SELECT * FROM browser_download ORDER BY createTime DESC")
    List<BrowserDownloadEntity> getAllDownloads();

    @Query("SELECT * FROM browser_download WHERE systemDownloadId = :systemId LIMIT 1")
    BrowserDownloadEntity getBySystemDownloadId(long systemId);

    @Query("UPDATE browser_download SET status = :status, filePath = :filePath, finishTime = :finishTime WHERE systemDownloadId = :systemId")
    void updateBySystemDownloadId(long systemId, int status, String filePath, long finishTime);

    @Query("SELECT * FROM browser_download WHERE status IN (:statuses) ORDER BY createTime DESC")
    List<BrowserDownloadEntity> getByStatuses(int[] statuses);

    @Query("UPDATE browser_download SET status = :status, totalSize = :totalSize, downloadedSize = :downloadedSize WHERE systemDownloadId = :systemId")
    void updateProgressBySystemDownloadId(long systemId, int status, long totalSize, long downloadedSize);

    @Query("SELECT * FROM browser_download WHERE id = :id LIMIT 1")
    BrowserDownloadEntity getById(long id);

    @Query("DELETE FROM browser_download WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM browser_download")
    void deleteAll();
}
