package com.gamecenter.app.core.common

import android.content.Context

interface ModuleInterface {

    fun init(context: Context)

    fun start(context: Context)

    fun stop()

    fun getId(): String

    fun getName(): String

    fun getVersion(): String {
        return "1.0.0"
    }

    fun getDescription(): String {
        return ""
    }

    fun isRunning(): Boolean
}
