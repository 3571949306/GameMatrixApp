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
    fun testSinglePlayerButtonIsDisplayed() {
        onView(withId(R.id.btnSinglePlayer))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testOnlineButtonIsDisplayed() {
        onView(withId(R.id.btnOnline))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSinglePlayerButtonStartsGame() {
        onView(withId(R.id.btnSinglePlayer))
            .check(matches(isDisplayed()))
            .perform(click())
    }
}
