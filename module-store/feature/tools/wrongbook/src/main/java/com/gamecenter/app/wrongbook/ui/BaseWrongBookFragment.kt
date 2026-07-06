package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.gamecenter.app.modules.ModuleManager

/**
 * 错题本模块内 Fragment 的基类。
 *
 * 重写 [onGetLayoutInflater] 使子 Fragment 在解析布局时使用模块自身的 Resources 与主题，
 * 避免模块 R.id/R.layout/R.string 与宿主 R 值冲突导致 View Binding 找不到视图或字符串缺失。
 */
open class BaseWrongBookFragment : Fragment() {

    /**
     * 模块 Resources 缓存，供子类获取字符串、颜色等资源。
     */
    protected val moduleResources: Resources
        get() = ModuleManager.getModuleResources(ModuleContextHelper.MODULE_ID)?.resources
            ?: super.getResources()

    /**
     * 模块 Context：基于宿主 Context，但 Resources 和 Theme 替换为模块自身。
     * 供 [MaterialAlertDialogBuilder] 等需要 Context 的 Material 组件使用。
     */
    protected val moduleContext: Context
        get() = ModuleContextHelper.getModuleContext(requireContext())

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        return try {
            ModuleContextHelper.getLayoutInflater(requireContext())
        } catch (_: IllegalStateException) {
            super.onGetLayoutInflater(savedInstanceState)
        }
    }
}
