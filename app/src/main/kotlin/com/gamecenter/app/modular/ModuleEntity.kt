package com.gamecenter.app.modular

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey
    val moduleId: String,

    val moduleName: String,

    val versionCode: Int,

    val versionName: String,

    val downloadUrl: String,

    val fileSize: Long,

    val sha256: String,

    val entryClass: String,

    val minAppVersion: Int,

    val description: String,

    val state: String = ModuleState.NOT_DOWNLOADED.name,

    val downloadedSize: Long = 0L,

    val localPath: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)

enum class ModuleState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    VERIFIED,
    LOADED,
    ERROR
}
