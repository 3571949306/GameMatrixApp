package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.wrongbook.R

/**
 * 为动态加载的模块 Fragment 提供使用模块自身 Resources 的 LayoutInflater。
 *
 * 问题背景：模块作为独立 APK 被 DexClassLoader 加载后，其 Fragment 仍由宿主 Activity
 * 的 LayoutInflater 解析布局，导致模块 R.id 与宿主 R.id 数值冲突，View Binding 找不到视图。
 *
 * 修复策略：使用完全自包含的模块 Context：
 * - Resources 指向模块 APK 资源，保证 R.layout/R.id 与模块代码一致。
 * - Theme 指向模块内部定义的 [R.style.Theme_GameMatrixApp]，其中包含模块所需的所有
 *   Material 主题属性（colorPrimary、colorSurface、colorOnSurface 等），不再依赖宿主主题。
 * 这样 Material 组件初始化时读取 ?attr/xxx 会走模块自己的主题与颜色，避免宿主/模块资源
 * ID 冲突导致的 Resources$NotFoundException。
 */
internal object ModuleContextHelper {

    internal const val MODULE_ID = "wrongbook"

    fun getLayoutInflater(context: Context): LayoutInflater {
        val moduleContext = getModuleContext(context)
        return ModuleLayoutInflater(moduleContext, moduleContext.resources)
    }

    /**
     * 获取模块 Context：Resources 与 Theme 均来自模块 APK，但仍保留宿主 Context 的
     * ApplicationContext、PackageManager 等系统能力。
     */
    fun getModuleContext(context: Context): Context {
        val moduleRes = ModuleManager.getModuleResources(MODULE_ID)?.resources
            ?: return context
        return ModuleContextWrapper(context, FallbackResources(moduleRes, context.resources))
    }

    /**
     * 模块 Context：使用宿主 Context 作为基础，但替换 Resources 和 Theme 为模块自身。
     */
    private class ModuleContextWrapper(
        base: Context,
        private val moduleResources: Resources
    ) : ContextThemeWrapper(base, R.style.Theme_GameMatrixApp) {
        override fun getResources(): Resources = moduleResources
    }

    private class FallbackResources(
        private val moduleResources: Resources,
        private val hostResources: Resources
    ) : Resources(
        moduleResources.assets,
        moduleResources.displayMetrics,
        moduleResources.configuration
    ) {
        override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
            try {
                moduleResources.getValue(id, outValue, resolveRefs)
            } catch (_: NotFoundException) {
                hostResources.getValue(id, outValue, resolveRefs)
            }
        }

        override fun getText(id: Int): CharSequence =
            try {
                moduleResources.getText(id)
            } catch (_: NotFoundException) {
                hostResources.getText(id)
            }

        override fun getString(id: Int): String =
            try {
                moduleResources.getString(id)
            } catch (_: NotFoundException) {
                hostResources.getString(id)
            }

        override fun getString(id: Int, vararg formatArgs: Any?): String =
            try {
                moduleResources.getString(id, *formatArgs)
            } catch (_: NotFoundException) {
                hostResources.getString(id, *formatArgs)
            }

        override fun getDrawable(id: Int, theme: Theme?): Drawable =
            try {
                moduleResources.getDrawable(id, theme)
            } catch (_: NotFoundException) {
                hostResources.getDrawable(id, theme)
            }

        override fun getColor(id: Int, theme: Theme?): Int =
            try {
                moduleResources.getColor(id, theme)
            } catch (_: NotFoundException) {
                hostResources.getColor(id, theme)
            }

        override fun getColorStateList(id: Int, theme: Theme?): ColorStateList =
            try {
                moduleResources.getColorStateList(id, theme)
            } catch (_: NotFoundException) {
                hostResources.getColorStateList(id, theme)
            }

        override fun getLayout(id: Int): XmlResourceParser =
            try {
                moduleResources.getLayout(id)
            } catch (_: NotFoundException) {
                hostResources.getLayout(id)
            }
    }

    /**
     * 模块专用 LayoutInflater。
     *
     * - [inflate] 从模块 [moduleResources] 获取布局 XML，解决模块/宿主 R 值冲突。
     * - 解析与 View 创建使用模块 [moduleContext]，从而使用模块主题与模块颜色。
     */
    private class ModuleLayoutInflater(
        moduleContext: Context,
        private val moduleResources: Resources
    ) : LayoutInflater(moduleContext) {

        override fun cloneInContext(newContext: Context): LayoutInflater {
            return ModuleLayoutInflater(newContext, moduleResources)
        }

        override fun inflate(resource: Int, root: ViewGroup?): View {
            return inflate(resource, root, root != null)
        }

        override fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View {
            val parser = moduleResources.getLayout(resource)
            return super.inflate(parser, root, attachToRoot)
        }

        override fun onCreateView(name: String, attrs: AttributeSet): View {
            try {
                return super.onCreateView(name, attrs)
            } catch (exception: ClassNotFoundException) {
                return createPlatformView(name, attrs, exception)
            }
        }

        override fun onCreateView(parent: View?, name: String, attrs: AttributeSet): View? {
            try {
                return super.onCreateView(parent, name, attrs)
            } catch (exception: ClassNotFoundException) {
                return createPlatformView(name, attrs, exception)
            }
        }

        private fun createPlatformView(
            name: String,
            attrs: AttributeSet,
            cause: ClassNotFoundException
        ): View {
            val prefixes = arrayOf("android.widget.", "android.view.", "android.webkit.")
            for (prefix in prefixes) {
                try {
                    return createView(name, prefix, attrs)
                } catch (_: ClassNotFoundException) {
                    // Try the next platform view prefix.
                }
            }
            throw cause
        }
    }
}
