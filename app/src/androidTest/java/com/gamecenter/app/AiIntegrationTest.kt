package com.gamecenter.app

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hamcrest.Matcher
import org.hamcrest.Matchers.instanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 入口集成测试。
 *
 * BL-009 教训：底部导航已动态化——menuId 由 BottomNavigationManager.refreshNavigation
 * 运行时按目录顺序分配（1..n），静态 R.id.navigation_ai 不复存在；而 androidTest
 * 因 BL-008 死门禁从未在 CI 编译，断裂长期无人发现。
 * 现按标题匹配选中导航项，走与真实点按相同的 onItemSelected→navigateTo 路径。
 */
@RunWith(AndroidJUnit4::class)
class AiIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun selectNavByTitle(title: String) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = instanceOf(BottomNavigationView::class.java)
        override fun getDescription() = "select nav item titled $title"
        override fun perform(uiController: UiController, view: View) {
            val nav = view as BottomNavigationView
            for (i in 0 until nav.menu.size()) {
                val item = nav.menu.getItem(i)
                if (item.title.toString() == title) {
                    nav.selectedItemId = item.itemId
                    return
                }
            }
            val present = (0 until nav.menu.size()).joinToString { nav.menu.getItem(it).title.toString() }
            throw AssertionError("导航项未找到: $title；当前存在: $present")
        }
    }

    private fun aiNavTitle(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.nav_ai)

    @Test
    fun testAiTabIsAccessible() {
        onView(withId(R.id.nav_view)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_view)).perform(selectNavByTitle(aiNavTitle()))
        // 选中后 AI 页应呈现（et_ai_input 定义于 fragment_ai.xml）
        onView(withId(R.id.et_ai_input)).check(matches(isDisplayed()))
    }

    @Test
    fun testAiInputFieldIsDisplayed() {
        onView(withId(R.id.nav_view)).perform(selectNavByTitle(aiNavTitle()))
        onView(withId(R.id.et_ai_input)).check(matches(isDisplayed()))
    }
}
