package com.gamecenter.app.core.common

import android.content.Context
import androidx.fragment.app.Fragment

/**
 * 可下载功能模块的接口。
 *
 * 每个从模块商店下载的功能模块需实现此接口。
 * ModuleShellFragment 通过此接口获取模块的主 Fragment 进行展示。
 */
interface FeatureModule {

    /** 返回模块提供的主 Fragment */
    fun createFragment(context: Context): Fragment
}