package com.gamecenter.app.cloudsync

/**
 * P3-13 (CLOUD_SAVE_SYNC): 云存档同步提供者抽象。
 *
 * 不同云服务（WebDAV、Google Drive 等）实现此接口，
 * 由 [CloudSyncManager] 统一调度上传/下载/冲突解决。
 */
interface CloudSyncProvider {

    /** 提供者唯一标识，如 "webdav"、"gdrive" */
    val providerId: String

    /** 显示名称 */
    val displayName: String

    /** 是否已配置（如 WebDAV 需要填写服务器地址+凭据） */
    fun isConfigured(): Boolean

    /**
     * 测试连接是否可用。
     * @return null 表示成功，非 null 为错误描述
     */
    fun testConnection(): String?

    /**
     * 上传存档数据。
     * @param remotePath 远程路径（相对根目录）
     * @param data JSON 字节数组
     * @param localTimestamp 本地时间戳（用于冲突检测）
     * @return UploadResult
     */
    fun upload(remotePath: String, data: ByteArray, localTimestamp: Long): UploadResult

    /**
     * 下载存档数据。
     * @param remotePath 远程路径
     * @return DownloadResult（包含数据和远程时间戳）
     */
    fun download(remotePath: String): DownloadResult

    /**
     * 获取远程存档的元信息（时间戳、大小），不下载内容。
     * @return RemoteMeta 或 null（远程不存在）
     */
    fun getRemoteMeta(remotePath: String): RemoteMeta?

    /** 上传结果 */
    sealed class UploadResult {
        object Success : UploadResult()
        data class Conflict(val remoteTimestamp: Long) : UploadResult()
        data class Failure(val message: String) : UploadResult()
    }

    /** 下载结果 */
    sealed class DownloadResult {
        data class Success(val data: ByteArray, val remoteTimestamp: Long) : DownloadResult()
        object NotFound : DownloadResult()
        data class Failure(val message: String) : DownloadResult()
    }

    /** 远程元信息 */
    data class RemoteMeta(
        val lastModified: Long,
        val size: Long
    )
}
