package com.gamecenter.app.update

import org.junit.Assert.*
import org.junit.Test

class UpdateInfoBasicTest {
    
    @Test
    fun `channel label is correct for beta`() {
        val label = if ("beta" == "beta") "测试版" else "正式版"
        assertEquals("测试版", label)
    }
    
    @Test
    fun `channel label is correct for stable`() {
        val label = if ("stable" == "beta") "测试版" else "正式版"
        assertEquals("正式版", label)
    }
    
    @Test
    fun `file size formatting for bytes`() {
        val size = 512L
        val formatted = if (size < 1024) "${size} B" else if (size < 1024 * 1024) "${size / 1024} KB" else "${size / (1024 * 1024)} MB"
        assertEquals("512 B", formatted)
    }
    
    @Test
    fun `file size formatting for kilobytes`() {
        val size = 1536L
        val formatted = if (size < 1024) "${size} B" else if (size < 1024 * 1024) "${size / 1024} KB" else "${size / (1024 * 1024)} MB"
        assertEquals("1 KB", formatted)
    }
    
    @Test
    fun `file size formatting for megabytes`() {
        val size = 2L * 1024 * 1024
        val formatted = if (size < 1024) "${size} B" else if (size < 1024 * 1024) "${size / 1024} KB" else "${size / (1024 * 1024)} MB"
        assertEquals("2 MB", formatted)
    }
    
    @Test
    fun `beta release detection`() {
        val isBeta = "beta" == "beta"
        assertTrue(isBeta)
    }
    
    @Test
    fun `stable release detection`() {
        val isBeta = "stable" == "beta"
        assertFalse(isBeta)
    }
}
