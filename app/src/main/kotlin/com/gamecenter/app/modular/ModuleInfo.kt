package com.gamecenter.app.modular

import com.google.gson.annotations.SerializedName

data class ModuleInfo(
    @SerializedName("moduleId")
    val moduleId: String,

    @SerializedName("moduleName")
    val moduleName: String,

    @SerializedName("versionCode")
    val versionCode: Int,

    @SerializedName("versionName")
    val versionName: String,

    @SerializedName("downloadUrl")
    val downloadUrl: String,

    @SerializedName("fileSize")
    val fileSize: Long,

    @SerializedName("sha256")
    val sha256: String,

    @SerializedName("entryClass")
    val entryClass: String,

    @SerializedName("minAppVersion")
    val minAppVersion: Int = 0,

    @SerializedName("description")
    val description: String = ""
)
