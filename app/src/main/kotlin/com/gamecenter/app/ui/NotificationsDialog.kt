package com.gamecenter.app.ui

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.gamecenter.app.games.achievement.DailyCheckInManager
import com.gamecenter.app.games.achievement.StreakTracker

/**
 * 通知中心对话框（Feature E / HOME_CARD_ENHANCE）。
 *
 * 聚合展示每日挑战、连胜、签到等通知入口，受
 * [BuildConfig.NOTIFICATIONS_CENTER] feature flag 控制。
 */
class NotificationsDialog : DialogFragment() {

    /** 通知条目数据。 */
    data class NotificationItem(
        val type: String,
        val titleRes: Int,
        val desc: String,
        val iconRes: Int,
        val tintRes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = Resources.getSystem().displayMetrics.widthPixels * 9 / 10
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!BuildConfig.NOTIFICATIONS_CENTER) {
            dismiss()
            return
        }

        view.findViewById<ImageView>(R.id.btn_close_notifications).setOnClickListener {
            dismiss()
        }

        val notifications = buildNotifications()

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_notifications)
        val emptyView = view.findViewById<TextView>(R.id.tv_empty_notifications)

        if (notifications.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = NotificationAdapter(notifications) { item ->
                dismiss()
                Toast.makeText(
                    requireContext(),
                    R.string.notifications_center_view_detail,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 构建通知列表。 */
    private fun buildNotifications(): List<NotificationItem> {
        val list = mutableListOf<NotificationItem>()
        val context = requireContext()

        // 1. 每日挑战（进行中 / 已完成）
        val challenge = DailyChallengeManager.getInstance(context).getTodayChallenge()
        val gameName = challenge.gameName.ifEmpty {
            getString(R.string.notifications_center_achievement)
        }
        val challengeDesc = String.format("%s %d/%d", gameName, challenge.progress, challenge.target)
        list.add(
            NotificationItem(
                type = TYPE_DAILY_CHALLENGE,
                titleRes = R.string.notifications_center_daily_challenge,
                desc = challengeDesc,
                iconRes = R.drawable.ic_notification,
                tintRes = android.R.color.holo_blue_light
            )
        )

        // 2. 连胜保持中
        val streak = StreakTracker.getInstance(context).getCurrentStreak()
        if (streak > 0) {
            val streakDesc = String.format(getString(R.string.daily_checkin_unit_days), streak)
            list.add(
                NotificationItem(
                    type = TYPE_STREAK,
                    titleRes = R.string.notifications_center_streak,
                    desc = streakDesc,
                    iconRes = R.drawable.ic_notification,
                    tintRes = android.R.color.holo_orange_light
                )
            )
        }

        // 3. 今日签到（未签到 / 已签到）
        // 注：DailyCheckInManager 由独立任务创建，此处引用其 API。
        val checkInManager = DailyCheckInManager.getInstance(context)
        val checkedInToday = checkInManager.isCheckedInToday()
        val totalDays = checkInManager.getTotalCheckInDays()
        val checkInDesc = String.format(getString(R.string.daily_checkin_unit_days), totalDays)
        list.add(
            NotificationItem(
                type = TYPE_CHECKIN,
                titleRes = R.string.notifications_center_checkin,
                desc = checkInDesc,
                iconRes = R.drawable.ic_notification,
                tintRes = if (checkedInToday) android.R.color.holo_green_light
                          else android.R.color.holo_red_light
            )
        )

        return list
    }

    /** 通知条目 Adapter。 */
    private class NotificationAdapter(
        private val items: List<NotificationItem>,
        private val onItemClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_notification_icon)
            val title: TextView = view.findViewById(R.id.tv_notification_title)
            val desc: TextView = view.findViewById(R.id.tv_notification_desc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = holder.itemView.context.getString(item.titleRes)
            holder.desc.text = item.desc
            holder.icon.setImageResource(item.iconRes)
            ImageViewCompat.setImageTintList(
                holder.icon,
                ContextCompat.getColorStateList(holder.itemView.context, item.tintRes)
            )
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private const val TYPE_DAILY_CHALLENGE = "daily_challenge"
        private const val TYPE_STREAK = "streak"
        private const val TYPE_CHECKIN = "checkin"
    }
}
