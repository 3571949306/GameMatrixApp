package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardChartRenderTest {

    @Test
    fun weeklyTrendChartRendersPixels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chartView = WeeklyTrendChartView(context).apply {
            setWeeklyData(intArrayOf(5, 10, 15, 8, 12, 6, 9))
        }

        assertViewRenders(chartView, width = 800, height = 400)
    }

    @Test
    fun subjectPieChartRendersPixels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chartView = SubjectPieChartView(context).apply {
            setData(mapOf("Math" to 10, "English" to 5, "Physics" to 8))
        }

        assertViewRenders(chartView, width = 500, height = 500)
    }

    private fun assertViewRenders(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        var drawnPixels = 0
        for (x in 0 until width step 10) {
            for (y in 0 until height step 10) {
                if (bitmap.getPixel(x, y) != 0) drawnPixels++
            }
        }
        bitmap.recycle()

        assertTrue("View should draw non-transparent pixels", drawnPixels > 0)
    }
}
