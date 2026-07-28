package com.gamecenter.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * P3-11 (ONBOARDING_ENHANCE): App 级新手引导。
 *
 * 首次启动时展示，包含：
 * 1. 欢迎页
 * 2. 兴趣选择（棋类/卡牌/益智/休闲/策略，多选）
 * 3. 难度自评（新手/普通/高手，单选）
 * 4. 完成页
 *
 * 完成后持久化到 `onboarding` SharedPreferences，
 * 并将兴趣和难度写入 `app_settings` 供推荐算法使用。
 *
 * 触发方式：SplashActivity 检查 `onboarding_completed` 标志，
 * 未完成则跳转到此 Activity。
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "onboarding"
        private const val KEY_COMPLETED = "onboarding_completed"
        private const val KEY_INTERESTS = "onboarding_interests"
        private const val KEY_DIFFICULTY = "onboarding_difficulty"

        /** 难度等级：0=新手, 1=普通, 2=高手 */
        const val DIFFICULTY_BEGINNER = 0
        const val DIFFICULTY_NORMAL = 1
        const val DIFFICULTY_EXPERT = 2

        /** 是否已完成新手引导 */
        @JvmStatic
        fun isCompleted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)
        }

        /** 获取用户选择的兴趣标签集合 */
        @JvmStatic
        fun getInterests(context: Context): Set<String> {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_INTERESTS, "") ?: ""
            return if (raw.isEmpty()) emptySet() else raw.split(",").toSet()
        }

        /** 获取用户自评难度等级（0=新手, 1=普通, 2=高手） */
        @JvmStatic
        fun getDifficultyLevel(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DIFFICULTY, DIFFICULTY_NORMAL)
        }

        /** 重置引导（设置页"重新查看引导"入口） */
        @JvmStatic
        fun reset(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED, false)
                .apply()
        }
    }

    private var currentPage = 0
    private val totalPages = 4

    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var pageWelcome: LinearLayout
    private lateinit var pageInterest: LinearLayout
    private lateinit var pageDifficulty: LinearLayout
    private lateinit var pageDone: LinearLayout
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var chipGroupInterest: ChipGroup
    private lateinit var rbBeginner: MaterialRadioButton
    private lateinit var rbNormal: MaterialRadioButton
    private lateinit var rbExpert: MaterialRadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        progressIndicator = findViewById(R.id.onboarding_progress)
        pageWelcome = findViewById(R.id.page_welcome)
        pageInterest = findViewById(R.id.page_interest)
        pageDifficulty = findViewById(R.id.page_difficulty)
        pageDone = findViewById(R.id.page_done)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnSkip = findViewById(R.id.btn_skip)
        chipGroupInterest = findViewById(R.id.chip_group_interest)
        rbBeginner = findViewById(R.id.rb_beginner)
        rbNormal = findViewById(R.id.rb_normal)
        rbExpert = findViewById(R.id.rb_expert)

        btnPrev.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updatePage()
            }
        }
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePage()
            } else {
                finishOnboarding()
            }
        }
        btnSkip.setOnClickListener {
            finishOnboarding()
        }

        updatePage()
    }

    private fun updatePage() {
        // 页面切换
        pageWelcome.visibility = if (currentPage == 0) View.VISIBLE else View.GONE
        pageInterest.visibility = if (currentPage == 1) View.VISIBLE else View.GONE
        pageDifficulty.visibility = if (currentPage == 2) View.VISIBLE else View.GONE
        pageDone.visibility = if (currentPage == 3) View.VISIBLE else View.GONE

        // 进度条
        val progress = ((currentPage + 1).toFloat() / totalPages * 100).toInt()
        progressIndicator.setProgressCompat(progress, true)

        // 按钮文本和可见性
        btnPrev.visibility = if (currentPage == 0) View.INVISIBLE else View.VISIBLE
        btnNext.text = if (currentPage == totalPages - 1) {
            getString(R.string.onboarding_finish)
        } else {
            getString(R.string.onboarding_next)
        }

        // 跳过按钮：最后一页隐藏
        btnSkip.visibility = if (currentPage == totalPages - 1) View.INVISIBLE else View.VISIBLE
    }

    /** 收集用户选择并持久化，然后进入 MainActivity */
    private fun finishOnboarding() {
        // 收集兴趣
        val interests = mutableSetOf<String>()
        for (i in 0 until chipGroupInterest.childCount) {
            val chip = chipGroupInterest.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) {
                interests.add(chip.text.toString())
            }
        }
        // 收集难度
        val difficulty = when {
            rbBeginner.isChecked -> DIFFICULTY_BEGINNER
            rbExpert.isChecked -> DIFFICULTY_EXPERT
            else -> DIFFICULTY_NORMAL
        }

        // 持久化
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_INTERESTS, interests.joinToString(","))
            .putInt(KEY_DIFFICULTY, difficulty)
            .apply()

        // 同步写入 app_settings 供推荐算法使用
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("user_interests", interests.joinToString(","))
            .putInt("user_difficulty_level", difficulty)
            .apply()

        // 进入 MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
