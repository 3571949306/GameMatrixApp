package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.view.LayoutInflater
import com.gamecenter.app.moduleloader.ModuleContextHelper as GenericHelper
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.ModuleLoader
import com.gamecenter.app.wrongbook.R

/**
 * 错题本模块专用的 Context 与 LayoutHelper。
 * 委托至核心公共库 [com.gamecenter.app.moduleloader.ModuleContextHelper] 实现。
 */
internal object ModuleContextHelper {

    internal const val MODULE_ID = "wrongbook"

    fun getLayoutInflater(context: Context): LayoutInflater {
        val resources = ModuleManager.getModuleResources(MODULE_ID)?.resources
            ?: context.resources
        val classLoader = ModuleLoader.getClassLoader(MODULE_ID)
            ?: context.classLoader
        return GenericHelper.getLayoutInflater(context, resources, classLoader, R.style.Theme_GameMatrixApp)
    }

    fun getModuleContext(context: Context): Context {
        val resources = ModuleManager.getModuleResources(MODULE_ID)?.resources
            ?: context.resources
        val classLoader = ModuleLoader.getClassLoader(MODULE_ID)
            ?: context.classLoader
        return GenericHelper.getModuleContext(context, resources, classLoader, R.style.Theme_GameMatrixApp)
    }
}
