package com.gamecenter.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamecenter.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testAiTabIsAccessible() {
        onView(withId(R.id.navigation_ai))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    @Test
    fun testAiInputFieldIsDisplayed() {
        onView(withId(R.id.navigation_ai))
            .perform(click())

        onView(withId(R.id.et_ai_input))
            .check(matches(isDisplayed()))
    }
}
