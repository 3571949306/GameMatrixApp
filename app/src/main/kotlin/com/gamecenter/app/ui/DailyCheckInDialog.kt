package com.gamecenter.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.games.achievement.DailyCheckInManager

/**
 * 每日签到对话框（Feature: DAILY_CHECKIN）。
 *
 * <p>展示连续签到天数与今日奖励，用户点击按钮完成签到。</p>
 *
 * <p>通过 [BuildConfig.DAILY_CHECKIN] 控制是否启用：若 flag 为 false，对话框直接关闭。</p>
 *
 * <p>主题处理：因当前主题未提供 Dialog 专用变体，这里采用
 * [STYLE_NO_TITLE] + 透明 window 背景的兜底方案，让布局自身的 ?attr/colorSurface 生效。</p>
 */
class DailyCheckInDialog(
    private val callback: OnCheckInResult? = null
) : DialogFragment() {

    /** 签到结果回调。 */
    interface OnCheckInResult {
        fun onCheckedIn(points: Int, consecutiveDays: Int)
    }

    private lateinit var tvConsecutiveValue: TextView
    private lateinit var tvTodayRewardValue: TextView
    private lateinit var btnCheckIn: Button
    private lateinit var tvDone: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 无标题栏，让布局自绘
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_daily_checkin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Feature flag 未启用则直接关闭
        if (!BuildConfig.DAILY_CHECKIN) {
            dismiss()
            return
        }

        tvConsecutiveValue = view.findViewById(R.id.tv_checkin_consecutive_value)
        tvTodayRewardValue = view.findViewById(R.id.tv_checkin_today_reward_value)
        btnCheckIn = view.findViewById(R.id.btn_checkin)
        tvDone = view.findViewById(R.id.tv_checkin_done)

        val manager = DailyCheckInManager.getInstance(requireContext())
        val consecutive = manager.getConsecutiveDays()

        // 预估今日奖励（假设连胜延续：newStreak = consecutive + 1）
        val estimatedNext = consecutive + 1
        val estimatedReward = 5 + Math.min(estimatedNext - 1, 10) * 2

        if (manager.isCheckedInToday()) {
            // 已签到：隐藏按钮，显示已签到状态，今日奖励显示 0
            btnCheckIn.visibility = View.GONE
            tvDone.visibility = View.VISIBLE
            tvConsecutiveValue.text = getString(R.string.daily_checkin_unit_days, consecutive)
            tvTodayRewardValue.text =
                getString(R.string.daily_checkin_reward_format, 0)
        } else {
            // 未签到：显示按钮与预估奖励
            btnCheckIn.visibility = View.VISIBLE
            tvDone.visibility = View.GONE
            tvConsecutiveValue.text = getString(R.string.daily_checkin_unit_days, consecutive)
            tvTodayRewardValue.text =
                getString(R.string.daily_checkin_reward_format, estimatedReward)

            btnCheckIn.setOnClickListener {
                val result = manager.checkInToday()
                if (result.success) {
                    // 更新 UI 为实际签到结果
                    tvConsecutiveValue.text =
                        getString(R.string.daily_checkin_unit_days, result.consecutiveDays)
                    tvTodayRewardValue.text =
                        getString(R.string.daily_checkin_reward_format, result.points)
                    btnCheckIn.visibility = View.GONE
                    tvDone.visibility = View.VISIBLE
                    tvDone.text = getString(R.string.daily_checkin_claimed)
                    Toast.makeText(
                        requireContext(),
                        R.string.daily_checkin_claimed,
                        Toast.LENGTH_SHORT
                    ).show()
                    callback?.onCheckedIn(result.points, result.consecutiveDays)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 透明背景，让布局自身的 colorSurface 圆角背景生效
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
