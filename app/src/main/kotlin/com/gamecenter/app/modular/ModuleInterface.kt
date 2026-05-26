package com.gamecenter.app.modular

import android.content.Context

interface ModuleInterface {

    fun init(context: Context)

    fun start()

    fun stop()

    fun getModuleId(): String
}
