package com.gamecenter.app.modules.unity

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamecenter.app.R

/**
 * P6 Unity 模块独立启动占位 Activity。
 *
 * 当未集成真实 Unity SDK 或模块未提供自定义 Activity 时使用。
 * 显示模块 ID 与占位文案，支持浅色/深色主题（继承 Theme.GameMatrixApp）。
 *
 * 入口参数：
 * - EXTRA_MODULE_ID：模块唯一 ID
 * - EXTRA_LAUNCH_ARGS.*：启动参数键值对
 */
class UnityPlayerPlaceholderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UnityPlaceholderActivity"
        const val EXTRA_MODULE_ID = "unity_module_id"
        const val EXTRA_LAUNCH_ARGS_PREFIX = "unity_launch_args."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID) ?: "unknown"
        val args = mutableMapOf<String, String>()
        intent.extras?.keySet()?.forEach { key ->
            if (key.startsWith(EXTRA_LAUNCH_ARGS_PREFIX)) {
                args[key.removePrefix(EXTRA_LAUNCH_ARGS_PREFIX)] = intent.getStringExtra(key) ?: ""
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.unity_module_placeholder_title, moduleId)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val descView = TextView(this).apply {
            text = getString(R.string.unity_module_placeholder_desc)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }

        layout.addView(titleView)
        layout.addView(descView)
        setContentView(layout)

        Log.d(TAG, "Unity 占位 Activity 已显示: $moduleId, args=$args")
    }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
}
