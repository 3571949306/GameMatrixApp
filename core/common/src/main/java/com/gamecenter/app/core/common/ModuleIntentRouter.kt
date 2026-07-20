package com.gamecenter.app.core.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * 模块间 Intent 路由（P1 Shell 基础设施）。
 *
 * 解耦模块间跳转：模块不需要知道目标模块的具体 Activity 类名，
 * 只需通过 scheme 路由到目标模块声明的入口。
 *
 * 支持的 action：
 * - `app://module/{moduleId}` 启动模块主入口
 * - `app://module/{moduleId}/route/{route}` 启动模块内指定页面
 * - `app://game/{gameId}` 启动指定游戏
 * - `app://tool/{toolId}` 启动指定工具
 */
object ModuleIntentRouter {

    private const val TAG = "ModuleIntentRouter"
    private const val SCHEME = "app://"

    interface RouteHandler {
        fun canHandle(route: RouteRequest): Boolean
        fun handle(context: Context, request: RouteRequest): Boolean
    }

    data class RouteRequest(
        val moduleId: String,
        val path: String,
        val params: Bundle = Bundle()
    ) {
        companion object {
            fun parse(uriString: String): RouteRequest? {
                val uri = try {
                    android.net.Uri.parse(uriString)
                } catch (_: Exception) {
                    return null
                }
                if (uri.scheme != "app") return null

                val segments = uri.pathSegments ?: return null
                if (segments.isEmpty()) return null

                val type = segments[0]
                val moduleId = if (segments.size > 1) segments[1] else ""
                val path = if (segments.size > 2) segments.subList(2, segments.size).joinToString("/") else ""

                val params = Bundle()
                for (key in uri.queryParameterNames) {
                    uri.getQueryParameter(key)?.let { params.putString(key, it) }
                }

                return RouteRequest(moduleId, path, params)
            }
        }
    }

    private val handlers = mutableListOf<RouteHandler>()

    fun registerHandler(handler: RouteHandler) {
        if (!handlers.contains(handler)) {
            handlers.add(handler)
            Log.d(TAG, "注册路由处理器: ${handler.javaClass.simpleName}")
        }
    }

    fun unregisterHandler(handler: RouteHandler) {
        handlers.remove(handler)
    }

    /**
     * 尝试路由到指定 URI。
     *
     * @return 是否成功处理
     */
    fun route(context: Context, uriString: String): Boolean {
        val request = RouteRequest.parse(uriString)
        if (request == null) {
            Log.w(TAG, "无法解析路由: $uriString")
            return false
        }

        for (handler in handlers) {
            if (handler.canHandle(request)) {
                try {
                    val success = handler.handle(context, request)
                    if (success) {
                        Log.d(TAG, "路由处理成功: $uriString")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "路由处理器异常: $uriString", e)
                }
            }
        }

        Log.w(TAG, "没有处理器可以处理路由: $uriString")
        return false
    }

    /**
     * 构建启动模块的 Intent（供外部调用）。
     */
    fun createModuleLaunchIntent(context: Context, moduleId: String, params: Bundle = Bundle()): Intent? {
        val manifest = ModuleRegistry.getManifest(moduleId) ?: return null
        val module = ModuleRegistry.getLoadedModule(moduleId) ?: return null

        return try {
            val intent = Intent(context, module::class.java)
            intent.putExtras(params)
            intent
        } catch (e: Exception) {
            Log.e(TAG, "构建模块启动 Intent 失败: $moduleId", e)
            null
        }
    }

    fun createRouteUri(moduleId: String, path: String = "", params: Map<String, String> = emptyMap()): String {
        val query = params.entries.joinToString("&") { "${it.key}=${android.net.Uri.encode(it.value)}" }
        return if (path.isEmpty()) {
            "app://module/$moduleId${if (query.isNotEmpty()) "?$query" else ""}"
        } else {
            "app://module/$moduleId/$path${if (query.isNotEmpty()) "?$query" else ""}"
        }
    }

    fun clear() {
        handlers.clear()
    }
}
