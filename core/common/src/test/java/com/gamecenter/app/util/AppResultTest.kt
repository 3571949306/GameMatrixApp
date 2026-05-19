package com.gamecenter.app.util

import org.junit.Assert.*
import org.junit.Test

class AppResultTest {

    @Test
    fun `Success maps correctly`() {
        val result = AppResult.Success(10)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is AppResult.Success)
        assertEquals(20, (mapped as AppResult.Success).data)
    }

    @Test
    fun `Error does not map`() {
        val result: AppResult<Int> = AppResult.Error("error")
        val mapped = result.map { it * 2 }
        assertTrue(mapped is AppResult.Error)
        assertEquals("error", (mapped as AppResult.Error).message)
    }

    @Test
    fun `onSuccess is called for Success`() {
        var called = false
        AppResult.Success(10).onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onSuccess is not called for Error`() {
        var called = false
        val result: AppResult<Int> = AppResult.Error("error")
        result.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onError is called for Error`() {
        var message = ""
        val result: AppResult<Int> = AppResult.Error("test error")
        result.onError { message = it }
        assertEquals("test error", message)
    }

    @Test
    fun `getOrNull returns data for Success`() {
        val result = AppResult.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for Error`() {
        val result: AppResult<Int> = AppResult.Error("error")
        assertNull(result.getOrNull())
    }

    @Test
    fun `AppResult of catches exception`() {
        val result = AppResult.of { throw RuntimeException("test") }
        assertTrue(result is AppResult.Error)
        assertEquals("test", (result as AppResult.Error).message)
    }

    @Test
    fun `AppResult of returns Success for valid operation`() {
        val result = AppResult.of { 42 }
        assertTrue(result is AppResult.Success)
        assertEquals(42, (result as AppResult.Success).data)
    }
}
