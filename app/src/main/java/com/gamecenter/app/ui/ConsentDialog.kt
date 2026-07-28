package com.gamecenter.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.gamecenter.app.R
import com.gamecenter.app.core.common.ConsentComponent
import com.gamecenter.app.core.common.ConsentDecision

/**
 * 云端调用前明示对话框（#24.2）。
 *
 * 在任意云端调用前展示 [ConsentComponent] 的 5 项必填信息，
 * 支持三种决策：同意走云端 / 拒绝 / 改用本地。
 *
 * 使用方式（在 FragmentActivity 中）：
 * ```
 * ConsentDialog.show(this, consent) { decision ->
 *     when (decision) {
 *         ConsentDecision.AGREE_CLOUD -> doCloudCall()
 *         ConsentDecision.USE_LOCAL -> doLocalCall()
 *         ConsentDecision.REFUSE -> { /* 取消 */ }
 *     }
 * }
 * ```
 *
 * 特性：
 * - 同意结果按 scope + versionCode 缓存，"不再提示"后跳过弹窗
 * - 条款版本更新后强制重新询问
 * - 有本地替代时显示"改用本地"按钮，否则隐藏
 */
class ConsentDialog : DialogFragment() {

    companion object {
        private const val TAG = "ConsentDialog"
        private const val ARG_SCOPE = "scope"
        private const val ARG_VERSION = "version_code"
        private const val ARG_TITLE = "title"
        private const val ARG_SEND = "send_data"
        private const val ARG_PURPOSE = "purpose"
        private const val ARG_LOCAL = "local_alt"
        private const val ARG_COST = "cost_network"
        private const val ARG_CANCEL = "cancel_hint"
        private const val ARG_PROVIDER = "provider_info"
        private const val ARG_RETENTION = "data_retention"

        /**
         * 显示 consent 弹窗（异步回调）。
         *
         * 如果用户此前已同意过此版本且选择了"不再提示"，则直接回调 [ConsentDecision.AGREE_CLOUD]。
         * 否则弹出对话框让用户决策。
         *
         * @param activity FragmentActivity（用于获取 FragmentManager）
         * @param consent consent 组件
         * @param onDecision 决策回调（主线程）
         */
        @JvmStatic
        fun show(
            activity: FragmentActivity,
            consent: ConsentComponent,
            onDecision: (ConsentDecision) -> Unit
        ) {
            // 已有有效同意且选择"不再提示" → 直接通过
            if (ConsentComponent.isDontAskAgain(activity, consent.scope) &&
                ConsentComponent.hasValidConsent(activity, consent.scope, consent.versionCode)
            ) {
                onDecision(ConsentDecision.AGREE_CLOUD)
                return
            }

            val args = Bundle().apply {
                putString(ARG_SCOPE, consent.scope)
                putInt(ARG_VERSION, consent.versionCode)
                putString(ARG_TITLE, consent.title)
                putString(ARG_SEND, consent.sendData)
                putString(ARG_PURPOSE, consent.purpose)
                putString(ARG_LOCAL, consent.localAlternative)
                putString(ARG_COST, consent.costAndNetwork)
                putString(ARG_CANCEL, consent.cancelHint)
                putString(ARG_PROVIDER, consent.providerInfo)
                putString(ARG_RETENTION, consent.dataRetention)
            }

            val dialog = ConsentDialog().apply { arguments = args }
            dialog.callback = onDecision
            dialog.show(activity.supportFragmentManager, "$TAG:${consent.scope}")
        }

        /**
         * 同步检查是否需要显示 consent。
         * @return true 表示需要显示弹窗；false 表示已有有效同意，可直接执行云端调用。
         */
        @JvmStatic
        fun needsConsent(context: Context, consent: ConsentComponent): Boolean {
            if (ConsentComponent.isDontAskAgain(context, consent.scope) &&
                ConsentComponent.hasValidConsent(context, consent.scope, consent.versionCode)
            ) {
                return false
            }
            return true
        }
    }

    private var callback: ((ConsentDecision) -> Unit)? = null
    private var dontAskAgain = false
    private var delivered = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val scope = args.getString(ARG_SCOPE) ?: ""
        val versionCode = args.getInt(ARG_VERSION, 1)
        val title = args.getString(ARG_TITLE).orEmpty()
        val sendData = args.getString(ARG_SEND).orEmpty()
        val purpose = args.getString(ARG_PURPOSE).orEmpty()
        val localAlt = args.getString(ARG_LOCAL).orEmpty()
        val costNetwork = args.getString(ARG_COST).orEmpty()
        val cancelHint = args.getString(ARG_CANCEL).orEmpty()
        val providerInfo = args.getString(ARG_PROVIDER).orEmpty()
        val dataRetention = args.getString(ARG_RETENTION).orEmpty()

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_cloud_consent, null)

        view.findViewById<TextView>(R.id.tv_consent_title)?.text =
            title.ifEmpty { getString(R.string.consent_default_title) }
        view.findViewById<TextView>(R.id.tv_consent_send)?.text = sendData
        view.findViewById<TextView>(R.id.tv_consent_purpose)?.text = purpose
        view.findViewById<TextView>(R.id.tv_consent_cost)?.text = costNetwork
        view.findViewById<TextView>(R.id.tv_consent_cancel)?.text = cancelHint

        view.findViewById<TextView>(R.id.tv_consent_provider)?.apply {
            visibility = if (providerInfo.isNotEmpty()) View.VISIBLE else View.GONE
            text = providerInfo
        }

        view.findViewById<TextView>(R.id.tv_consent_retention)?.apply {
            visibility = if (dataRetention.isNotEmpty()) View.VISIBLE else View.GONE
            text = dataRetention
        }

        view.findViewById<View>(R.id.row_local_alt)?.apply {
            visibility = if (localAlt.isNotEmpty()) View.VISIBLE else View.GONE
        }
        view.findViewById<TextView>(R.id.tv_consent_local)?.text = localAlt

        val cbDontAsk = view.findViewById<CheckBox>(R.id.cb_dont_ask_again)
        cbDontAsk?.setOnCheckedChangeListener { _, isChecked -> dontAskAgain = isChecked }

        val builder = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)

        builder.setPositiveButton(R.string.consent_agree_cloud) { _, _ ->
            ConsentComponent.recordConsent(requireContext(), scope, versionCode, dontAskAgain)
            deliver(ConsentDecision.AGREE_CLOUD)
        }

        if (localAlt.isNotEmpty()) {
            builder.setNeutralButton(R.string.consent_use_local) { _, _ ->
                deliver(ConsentDecision.USE_LOCAL)
            }
        }

        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
            deliver(ConsentDecision.REFUSE)
        }

        return builder.create()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // 用户按返回键 dismiss 时视为拒绝
        deliver(ConsentDecision.REFUSE)
    }

    private fun deliver(decision: ConsentDecision) {
        if (delivered) return
        delivered = true
        callback?.invoke(decision)
        callback = null
    }
}
