package com.gamecenter.app.games.achievement

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.games.config.GameConfigLoader
import com.gamecenter.app.games.model.AchievementDef
import com.gamecenter.app.games.model.GameConfig
import com.gamecenter.app.games.model.enums.AchievementLevel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Batch 9-3 (ACHIEVEMENT_DETAIL_PAGE): 单个游戏的成就详情页。
 *
 * 显示该游戏全部成就的：
 * - 名称 + 稀有度（青铜/白银/黄金/铂金，对应不同色块）
 * - 解锁状态 / 解锁日期
 * - 整体进度条 + 百分比
 * - 筛选 Chip（全部 / 已解锁 / 未解锁）
 *
 * 数据源与 [AchievementCenterActivity] 保持一致：
 * - PREF_NAME = "achievements"
 * - key 前缀 = "achievement_" + gameId + "_" + achievementKey + "_unlocked"
 * - 解锁时间 = "achievement_" + fullId + "_unlocked_at"
 *
 * 受 [BuildConfig.ACHIEVEMENT_DETAIL_PAGE] feature flag 控制。
 */
class AchievementDetailActivity : AppCompatActivity() {

    private lateinit var rvList: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvGameName: TextView
    private lateinit var tvUnlockedSummary: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressOverall: ProgressBar
    private lateinit var chipGroup: ChipGroup
    private lateinit var ivGameIcon: ImageView

    private lateinit var configLoader: GameConfigLoader
    private lateinit var achievementDao: com.gamecenter.app.database.dao.AchievementDao
    private var gameConfig: GameConfig? = null
    private val allItems = mutableListOf<AchievementRow>()

    private var currentFilter: Int = FILTER_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!BuildConfig.ACHIEVEMENT_DETAIL_PAGE) {
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievement_detail)

        configLoader = GameConfigLoader(this)
        achievementDao = com.gamecenter.app.database.AppDatabase.getDatabase(this).achievementDao()

        bindViews()
        loadGameData()
        setupChips()
        refreshList()
    }

    private fun bindViews() {
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }
        rvList = findViewById(R.id.rv_achievement_list)
        tvEmpty = findViewById(R.id.tv_empty)
        tvGameName = findViewById(R.id.tv_game_name)
        tvUnlockedSummary = findViewById(R.id.tv_unlocked_summary)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
        progressOverall = findViewById(R.id.progress_game_overall)
        chipGroup = findViewById(R.id.chip_group_filter)
        ivGameIcon = findViewById(R.id.iv_game_icon)

        rvList.layoutManager = LinearLayoutManager(this)
    }

    private fun loadGameData() {
        val gameId = intent?.getStringExtra(EXTRA_GAME_ID) ?: run {
            finish()
            return
        }
        gameConfig = configLoader.loadAllConfigs().firstOrNull { it.gameId == gameId }?.also { cfg ->
            // 标题
            findViewById<TextView>(R.id.tv_detail_title).text = cfg.name
            tvGameName.text = cfg.name
            if (cfg.iconResId != 0) {
                ivGameIcon.setImageResource(cfg.iconResId)
                ivGameIcon.visibility = View.VISIBLE
            } else {
                ivGameIcon.visibility = View.GONE
            }

            // 构造行数据
            allItems.clear()
            cfg.achievements?.forEach { def ->
                val fullKey = def.getFullId(cfg.gameId)
                val entity = achievementDao.getByIdSync(fullKey)
                val unlocked = entity?.unlocked ?: false
                val unlockedAt = entity?.unlockedAt ?: 0L
                allItems.add(
                    AchievementRow(
                        name = formatAchievementName(def.key),
                        desc = resolveDescription(cfg.gameId, def),
                        level = def.level,
                        unlocked = unlocked,
                        unlockedAt = unlockedAt
                    )
                )
            }
        }

        if (gameConfig == null) {
            finish()
            return
        }

        // 总进度
        val total = allItems.size
        val unlocked = allItems.count { it.unlocked }
        val percent = if (total > 0) (unlocked * 100f / total).toInt() else 0
        progressOverall.progress = percent
        tvProgressPercent.text = getString(R.string.achievement_detail_percent_format, percent)
        tvUnlockedSummary.text = getString(R.string.achievement_detail_unlocked_format, unlocked, total)
    }

    private fun setupChips() {
        val chipAll = findViewById<Chip>(R.id.chip_filter_all)
        val chipUnlocked = findViewById<Chip>(R.id.chip_filter_unlocked)
        val chipLocked = findViewById<Chip>(R.id.chip_filter_locked)
        chipAll.setOnClickListener {
            currentFilter = FILTER_ALL
            chipAll.isChecked = true
            chipUnlocked.isChecked = false
            chipLocked.isChecked = false
            refreshList()
        }
        chipUnlocked.setOnClickListener {
            currentFilter = FILTER_UNLOCKED
            chipAll.isChecked = false
            chipUnlocked.isChecked = true
            chipLocked.isChecked = false
            refreshList()
        }
        chipLocked.setOnClickListener {
            currentFilter = FILTER_LOCKED
            chipAll.isChecked = false
            chipUnlocked.isChecked = false
            chipLocked.isChecked = true
            refreshList()
        }
    }

    private fun refreshList() {
        val filtered = when (currentFilter) {
            FILTER_UNLOCKED -> allItems.filter { it.unlocked }
            FILTER_LOCKED -> allItems.filter { !it.unlocked }
            else -> allItems.toList()
        }
        if (filtered.isEmpty()) {
            rvList.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = getString(R.string.achievement_detail_empty_filter)
        } else {
            rvList.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            rvList.adapter = AchievementDetailAdapter(filtered)
        }
    }

    /**
     * 尝试用 "achievement_desc_<gameId>_<key>" 字符串资源描述成就；
     * 找不到时回退到默认占位文案。
     */
    private fun resolveDescription(gameId: String, def: AchievementDef): String {
        val resId = resources.getIdentifier(
            "achievement_desc_${gameId}_${def.key}",
            "string",
            packageName
        )
        return if (resId != 0) getString(resId)
        else getString(R.string.achievement_detail_default_desc)
    }

    /** snake_case -> "Snake Case" 风格。 */
    private fun formatAchievementName(key: String?): String {
        if (key.isNullOrEmpty()) return ""
        return key.split("_").joinToString(" ") { part ->
            if (part.isEmpty()) part
            else part.substring(0, 1).uppercase(Locale.getDefault()) + part.substring(1)
        }
    }

    private fun formatDate(ts: Long): String {
        if (ts <= 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private inner class AchievementDetailAdapter(
        private val items: List<AchievementRow>
    ) : RecyclerView.Adapter<AchievementDetailAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val viewRarity: View = view.findViewById(R.id.view_rarity_badge)
            val tvName: TextView = view.findViewById(R.id.tv_achievement_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_achievement_desc)
            val tvRarityLabel: TextView = view.findViewById(R.id.tv_rarity_label)
            val tvStatus: TextView = view.findViewById(R.id.tv_status)
            val tvUnlockDate: TextView = view.findViewById(R.id.tv_unlock_date)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_achievement_detail_full, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            holder.tvName.text = item.name
            holder.tvDesc.text = item.desc
            val colorHex = item.level.colorHex
            val color = try { Color.parseColor(colorHex) } catch (e: Exception) { Color.GRAY }
            holder.viewRarity.setBackgroundColor(color)
            holder.tvRarityLabel.text = rarityLabel(ctx, item.level)
            holder.tvRarityLabel.setBackgroundColor(color)

            if (item.unlocked) {
                holder.tvStatus.text = ctx.getString(R.string.achievement_unlocked)
                holder.tvStatus.setTextColor(Color.parseColor("#5B8A72"))
                holder.tvUnlockDate.text = ctx.getString(
                    R.string.achievement_detail_unlock_date_format,
                    formatDate(item.unlockedAt)
                )
            } else {
                holder.tvStatus.text = ctx.getString(R.string.achievement_locked)
                holder.tvStatus.setTextColor(Color.parseColor("#A8A198"))
                holder.tvUnlockDate.text = ""
            }
        }

        override fun getItemCount(): Int = items.size

        private fun rarityLabel(ctx: Context, level: AchievementLevel): String {
            val res = when (level) {
                AchievementLevel.BRONZE -> R.string.achievement_rarity_bronze
                AchievementLevel.SILVER -> R.string.achievement_rarity_silver
                AchievementLevel.GOLD -> R.string.achievement_rarity_gold
                AchievementLevel.PLATINUM -> R.string.achievement_rarity_platinum
            }
            return ctx.getString(res)
        }
    }

    private data class AchievementRow(
        val name: String,
        val desc: String,
        val level: AchievementLevel,
        val unlocked: Boolean,
        val unlockedAt: Long
    )

    companion object {
        private const val EXTRA_GAME_ID = "extra_game_id"
        private const val PREF_NAME = "achievements"
        private const val KEY_PREFIX = "achievement_"
        private const val FILTER_ALL = 0
        private const val FILTER_UNLOCKED = 1
        private const val FILTER_LOCKED = 2

        /** 启动成就详情页。Java 侧通过 AchievementDetailActivity.launch(...) 调用。 */
        @JvmStatic
        fun launch(context: Context, gameId: String) {
            val intent = Intent(context, AchievementDetailActivity::class.java)
            intent.putExtra(EXTRA_GAME_ID, gameId)
            context.startActivity(intent)
        }
    }
}
