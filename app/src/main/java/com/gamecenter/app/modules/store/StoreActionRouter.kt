package com.gamecenter.app.modules.store

import android.util.Log

/**
 * 商店动作路由（P2.5）。
 *
 * 第一版白名单（6 个）：
 * - [ACTION_OPEN_MODULE]：打开模块（必需参数：moduleId）
 * - [ACTION_OPEN_MODULE_DETAIL]：打开模块详情 BottomSheet（必需参数：moduleId）
 * - [ACTION_OPEN_INSTALLED_MODULES]：跳转到已安装模块分类
 * - [ACTION_REFRESH_CATALOG]：触发目录与 UI 配置刷新
 * - [ACTION_SWITCH_CATEGORY]：切换当前分类（必需参数：categoryId）
 * - [ACTION_OPEN_UPDATE_LIST]：跳转到可更新模块列表
 *
 * 严禁：
 * - 任意 Intent URI
 * - 任意类名（不允许服务器指定 Activity 类）
 * - 任意 Shell 命令
 * - 任意文件路径
 * - 任意 JavaScript / 表达式
 *
 * 未知动作一律拒绝并记录日志；必需参数缺失也拒绝。
 */
object StoreActionRouter {

    private const val TAG = "StoreActionRouter"

    const val ACTION_OPEN_MODULE = "open_module"
    const val ACTION_OPEN_MODULE_DETAIL = "open_module_detail"
    const val ACTION_OPEN_INSTALLED_MODULES = "open_installed_modules"
    const val ACTION_REFRESH_CATALOG = "refresh_catalog"
    const val ACTION_SWITCH_CATEGORY = "switch_category"
    const val ACTION_OPEN_UPDATE_LIST = "open_update_list"

    /** 白名单集合（测试用） */
    val ALLOWED_ACTIONS: Set<String> = setOf(
        ACTION_OPEN_MODULE,
        ACTION_OPEN_MODULE_DETAIL,
        ACTION_OPEN_INSTALLED_MODULES,
        ACTION_REFRESH_CATALOG,
        ACTION_SWITCH_CATEGORY,
        ACTION_OPEN_UPDATE_LIST
    )

    /** 每个动作的必需参数名 */
    private val REQUIRED_PARAMS: Map<String, Set<String>> = mapOf(
        ACTION_OPEN_MODULE to setOf("moduleId"),
        ACTION_OPEN_MODULE_DETAIL to setOf("moduleId"),
        ACTION_OPEN_INSTALLED_MODULES to emptySet(),
        ACTION_REFRESH_CATALOG to emptySet(),
        ACTION_SWITCH_CATEGORY to setOf("categoryId"),
        ACTION_OPEN_UPDATE_LIST to emptySet()
    )

    /**
     * 参数值黑名单（防止注入 Intent / 类名 / Shell / JavaScript）。
     *
     * 规则：
     * 1. 危险字符：双引号、反斜杠、分号、反引号、$、|、&、<、>、换行
     * 2. 危险关键字（完整单词匹配，大小写不敏感）：
     *    - Intent / Activity / Class（防止反射启动任意组件）
     *    - Runtime / Process / exec / shell（防止执行任意命令）
     *    - javascript（防止 JS 注入）
     *    - intent URI scheme（防止 Intent URI 注入）
     *
     * 注意：file / content / http / https 不在关键字黑名单中，因为合法文本可能包含这些词。
     * URL 不应作为动作参数（moduleId / categoryId 应为短 ID），由调用方在语义层校验。
     */
    private val PARAM_VALUE_BLACKLIST = Regex(
        "[\"\\\\;`$|&<>\\n\\r]|\\b(Intent|Activity|Class|Runtime|Process|exec|shell|javascript|intent)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * 校验并派发动作。
     *
     * @param action 动作名（必须在白名单内）
     * @param params 动作参数（必须满足必需参数，且值不得包含黑名单字符）
     * @param host 宿主回调
     * @return true 表示派发成功；false 表示动作被拒绝（未知动作 / 参数缺失 / 参数非法）
     */
    fun dispatch(action: String, params: Map<String, String>, host: StoreRendererHost): Boolean {
        // 1. 动作白名单校验
        if (action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "拒绝未知动作: $action")
            return false
        }

        // 2. 必需参数校验
        val required = REQUIRED_PARAMS[action] ?: emptySet()
        for (key in required) {
            val value = params[key]
            if (value.isNullOrEmpty()) {
                Log.w(TAG, "拒绝动作 $action: 缺少必需参数 $key")
                return false
            }
        }

        // 3. 参数值黑名单校验（防止 Intent / 类名 / Shell / JavaScript 注入）
        for ((key, value) in params) {
            if (PARAM_VALUE_BLACKLIST.containsMatchIn(value)) {
                Log.w(TAG, "拒绝动作 $action: 参数 $key 包含非法字符或关键字")
                return false
            }
        }

        // 4. 派发到宿主
        return try {
            host.dispatchAction(action, params)
            true
        } catch (e: Exception) {
            Log.e(TAG, "派发动作 $action 失败: ${e.message}", e)
            false
        }
    }

    /** 测试用：校验动作是否在白名单内（不派发） */
    fun isAllowed(action: String): Boolean = action in ALLOWED_ACTIONS

    /** 测试用：校验参数是否满足必需参数（不派发） */
    fun hasRequiredParams(action: String, params: Map<String, String>): Boolean {
        val required = REQUIRED_PARAMS[action] ?: return false
        return required.all { key -> !params[key].isNullOrEmpty() }
    }

    /** 测试用：校验参数值是否包含黑名单字符 */
    fun isParamValueSafe(value: String): Boolean = !PARAM_VALUE_BLACKLIST.containsMatchIn(value)
}
