package com.gamecenter.app.moduleloader

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

/**
 * 为动态加载的模块 Fragment 提供使用模块自身 Resources 的 LayoutInflater。
 */
object ModuleContextHelper {

    fun getLayoutInflater(
        context: Context,
        moduleResources: Resources,
        classLoader: ClassLoader,
        themeResId: Int
    ): LayoutInflater {
        val moduleContext = getModuleContext(context, moduleResources, classLoader, themeResId)
        return ModuleLayoutInflater(moduleContext, moduleContext.resources)
    }

    /**
     * 获取模块 Context：Resources 与 Theme 均来自模块 APK，但仍保留宿主 Context 的
     * ApplicationContext 等系统能力。
     */
    fun getModuleContext(
        context: Context,
        moduleResources: Resources,
        classLoader: ClassLoader,
        themeResId: Int
    ): Context {
        return ModuleContextWrapper(
            context,
            FallbackResources(moduleResources, context.resources),
            themeResId,
            classLoader
        )
    }

    /**
     * 模块 ContextWrapper
     */
    private class ModuleContextWrapper(
        base: Context,
        private val moduleResources: Resources,
        themeResId: Int,
        private val classLoader: ClassLoader
    ) : ContextThemeWrapper(base, themeResId) {
        override fun getResources(): Resources = moduleResources

        override fun getClassLoader(): ClassLoader = classLoader
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
