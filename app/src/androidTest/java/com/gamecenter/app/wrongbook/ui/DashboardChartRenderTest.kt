package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso Canvas 视图渲染与像素边界自动化测试类。
 */
@RunWith(AndroidJUnit4::class)
class DashboardChartRenderTest {

    @Test
    fun testWeeklyTrendChartViewRendersCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moduleContext = ModuleContextHelper.getModuleContext(context)
        
        val chartView = WeeklyTrendChartView(moduleContext)
        val testData = intArrayOf(5, 10, 15, 8, 12, 6, 9)
        chartView.setWeeklyData(testData)

        chartView.measure(
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        )
        chartView.layout(0, 0, 800, 400)

        assertTrue(chartView.width > 0)
        assertTrue(chartView.height > 0)

        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        chartView.draw(canvas)

        var nonTransparentPixels = 0
        for (x in 0 until 800 step 10) {
            for (y in 0 until 400 step 10) {
                if (bitmap.getPixel(x, y) != 0) {
                    nonTransparentPixels++
                }
            }
        }

        assertTrue("Chart view should draw non-transparent pixels", nonTransparentPixels > 0)
        bitmap.recycle()
    }

    @Test
    fun testSubjectPieChartViewRendersCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moduleContext = ModuleContextHelper.getModuleContext(context)
        
        val pieChartView = SubjectPieChartView(moduleContext)
        val testData = mapOf("Math" to 10, "English" to 5, "Physics" to 8)
        pieChartView.setData(testData)

        pieChartView.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        )
        pieChartView.layout(0, 0, 500, 500)

        assertTrue(pieChartView.width > 0)
        assertTrue(pieChartView.height > 0)

        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        pieChartView.draw(canvas)

        var nonTransparentPixels = 0
        for (x in 0 until 500 step 10) {
            for (y in 0 until 500 step 10) {
                if (bitmap.getPixel(x, y) != 0) {
                    nonTransparentPixels++
                }
            }
        }

        assertTrue("Pie chart view should draw non-transparent pixels", nonTransparentPixels > 0)
        bitmap.recycle()
    }
}
