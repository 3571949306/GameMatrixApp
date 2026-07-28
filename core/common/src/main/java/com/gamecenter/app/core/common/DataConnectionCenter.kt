package com.gamecenter.app.core.common

/**
 * 数据与连接中心数据模型（#23.1）。
 *
 * 聚合用户设备上与数据/网络相关的 6 项状态，供设置页"数据与连接中心"展示：
 * 1. 已授权权限（运行时权限）
 * 2. 已启用联网模块（隐私卡涉及云端的模块）
 * 3. 最近同步（WebDAV 等云同步状态）
 * 4. 下载记录（模块下载历史）
 * 5. 缓存大小（可清理的临时文件）
 * 6. 本地数据汇总（各模块 PrivacyCard.localData）
 *
 * 所有字段可选——聚合失败时 UI 回退为"未获取"提示。
 *
 * @property grantedPermissions 已授权的运行时权限列表
 * @property networkModules 涉及云端/网络的已安装模块列表
 * @property lastSync 最近一次云同步状态
 * @property downloadRecords 模块下载记录（活跃 + 历史）
 * @property cacheSizeBytes 可清理缓存大小（字节）
 * @property localDataSummary 各模块本地数据声明汇总
 */
data class DataConnectionCenter(
    val grantedPermissions: List<PermissionInfo> = emptyList(),
    val networkModules: List<NetworkModuleInfo> = emptyList(),
    val lastSync: SyncInfo = SyncInfo(),
    val downloadRecords: List<DownloadRecord> = emptyList(),
    val cacheSizeBytes: Long = 0L,
    val localDataSummary: List<LocalDataSummary> = emptyList()
) {
    /** 是否有任何数据可展示 */
    val hasContent: Boolean get() = grantedPermissions.isNotEmpty() ||
        networkModules.isNotEmpty() ||
        lastSync.hasContent ||
        downloadRecords.isNotEmpty() ||
        cacheSizeBytes > 0L ||
        localDataSummary.isNotEmpty()

    /** 涉及云端的模块数量 */
    val networkModuleCount: Int get() = networkModules.size

    /** 活跃下载数量 */
    val activeDownloadCount: Int get() = downloadRecords.count { it.isActive }
}

/** 已授权的运行时权限信息 */
data class PermissionInfo(
    val permission: String = "",
    val label: String = "",
    val description: String = "",
    /** 是否为危险权限（按 Android 权限分级） */
    val isDangerous: Boolean = false
)

/** 涉及云端/网络的已安装模块信息 */
data class NetworkModuleInfo(
    val moduleId: String = "",
    val moduleName: String = "",
    /** 该模块连接的域名/IP 列表（来自 PrivacyCard.networkDomains） */
    val networkDomains: List<String> = emptyList(),
    /** 云端数据描述（来自 PrivacyCard.cloudData） */
    val cloudData: String = "",
    /** 同步位置（来自 PrivacyCard.syncLocation） */
    val syncLocation: String = ""
) {
    val hasContent: Boolean get() = networkDomains.isNotEmpty() ||
        cloudData.isNotEmpty() ||
        syncLocation.isNotEmpty()
}

/** 最近一次云同步状态 */
data class SyncInfo(
    /** 上次同步时间戳（0 = 从未同步） */
    val lastSyncTime: Long = 0L,
    /** 上次同步状态：success / conflict / failure / not_configured / no_data / "" */
    val lastSyncStatus: String = "",
    /** 云同步是否已配置（WebDAV 凭据是否填写） */
    val isConfigured: Boolean = false,
    /** 是否启用自动同步 */
    val autoSyncEnabled: Boolean = false
) {
    val hasContent: Boolean get() = lastSyncTime > 0L ||
        lastSyncStatus.isNotEmpty() ||
        isConfigured ||
        autoSyncEnabled

    /** 是否存在未解决的冲突 */
    val hasConflict: Boolean get() = lastSyncStatus == "conflict"
}

/** 模块下载记录 */
data class DownloadRecord(
    val moduleId: String = "",
    val moduleName: String = "",
    /** 模块当前状态（NOT_DOWNLOADED/DOWNLOADING/DOWNLOADED/VERIFIED/LOADED/ERROR） */
    val state: String = "",
    /** 已下载字节数 */
    val downloadedSize: Long = 0L,
    /** 模块包总字节数 */
    val totalSize: Long = 0L,
    /** 最后更新时间戳 */
    val updatedAt: Long = 0L
) {
    /** 是否处于活跃下载状态 */
    val isActive: Boolean get() = state == "DOWNLOADING"

    /** 下载进度百分比（0-100，-1 表示无法计算） */
    val progressPercent: Int get() {
        if (totalSize <= 0L) return -1
        return ((downloadedSize * 100L) / totalSize).toInt().coerceIn(0, 100)
    }
}

/** 各模块本地数据声明汇总（来自 PrivacyCard.localData） */
data class LocalDataSummary(
    val moduleId: String = "",
    val moduleName: String = "",
    /** 本地数据描述（来自 PrivacyCard.localData） */
    val localData: String = "",
    /** 保存期限（来自 PrivacyCard.retentionPeriod） */
    val retentionPeriod: String = "",
    /** 删除方式（来自 PrivacyCard.deletionMethod） */
    val deletionMethod: String = ""
) {
    val hasContent: Boolean get() = localData.isNotEmpty() ||
        retentionPeriod.isNotEmpty() ||
        deletionMethod.isNotEmpty()
}
