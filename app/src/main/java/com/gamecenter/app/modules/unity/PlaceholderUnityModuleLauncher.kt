package com.gamecenter.app.modules.unity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gamecenter.app.R

/**
 * P6 Unity 模块占位启动器。
 *
 * 当未集成真实 Unity SDK 或模块未提供自定义启动器时使用。
 * - launchStandalone：打开一个占位 Activity，提示用户当前为占位实现
 * - createEmbeddedFragment：返回一个占位 Fragment
 * - isSupported：始终返回 true（占位实现不检查 GPU/Vulkan 能力）
 *
 * 安全说明：
 * - 此实现不加载任何原生库，仅用于架构验证和 UI 占位
 * - 真实 Unity 模块应提供自己的 UnityModuleLauncher 实现
 */
class PlaceholderUnityModuleLauncher(
    private val moduleId: String
) : UnityModuleLauncher {

    companion object {
        private const val TAG = "PlaceholderUnityLauncher"
    }

    override fun getModuleId(): String = moduleId

    override fun launchStandalone(context: Context, launchArgs: Map<String, String>): Boolean {
        Log.d(TAG, "启动 Unity 占位 Activity: $moduleId, args=$launchArgs")
        val intent = Intent(context, UnityPlayerPlaceholderActivity::class.java).apply {
            putExtra(UnityPlayerPlaceholderActivity.EXTRA_MODULE_ID, moduleId)
            launchArgs.forEach { (key, value) ->
                putExtra("${UnityPlayerPlaceholderActivity.EXTRA_LAUNCH_ARGS_PREFIX}$key", value)
            }
        }
        // 非 Activity Context 需要 NEW_TASK flag
        if (context !is AppCompatActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    override fun createEmbeddedFragment(context: Context, launchArgs: Map<String, String>): Fragment? {
        return PlaceholderUnityFragment.newInstance(moduleId, launchArgs)
    }

    override fun isSupported(context: Context): Boolean = true

    /**
     * 占位嵌入 Fragment。
     */
    class PlaceholderUnityFragment : Fragment() {

        companion object {
            private const val ARG_MODULE_ID = "module_id"

            fun newInstance(moduleId: String, args: Map<String, String>): PlaceholderUnityFragment {
                return PlaceholderUnityFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_MODULE_ID, moduleId)
                        putSerializable("launch_args", HashMap(args))
                    }
                }
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val moduleId = arguments?.getString(ARG_MODULE_ID) ?: "unknown"
            val context = requireContext()
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
            val title = TextView(context).apply {
                text = getString(R.string.unity_module_placeholder_title, moduleId)
                textSize = 18f
                gravity = Gravity.CENTER
            }
            val desc = TextView(context).apply {
                text = getString(R.string.unity_module_placeholder_desc)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            }
            layout.addView(title)
            layout.addView(desc)
            return layout
        }

        private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    }
}
