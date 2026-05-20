package com.gamecenter.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamecenter.app.games.doudizhu.DouDiZhuMenuActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DouDiZhuIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(DouDiZhuMenuActivity::class.java)

    @Test
    fun testDouDiZhuMenuIsDisplayed() {
        onView(withId(R.id.doudizhu_menu_root))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testLocalGameButtonStartsGame() {
        onView(withId(R.id.btn_doudizhu_local))
            .check(matches(isDisplayed()))
            .perform(click())
    }
}
