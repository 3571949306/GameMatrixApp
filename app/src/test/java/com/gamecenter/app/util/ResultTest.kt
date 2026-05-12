package com.gamecenter.app.util

import org.junit.Assert.*
import org.junit.Test

class ResultTest {
    
    @Test
    fun `Success maps correctly`() {
        val result = Result.Success(10)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is Result.Success)
        assertEquals(20, (mapped as Result.Success).data)
    }
    
    @Test
    fun `Error does not map`() {
        val result: Result<Int> = Result.Error("error")
        val mapped = result.map { it * 2 }
        assertTrue(mapped is Result.Error)
        assertEquals("error", (mapped as Result.Error).message)
    }
    
    @Test
    fun `onSuccess is called for Success`() {
        var called = false
        Result.Success(10).onSuccess { called = true }
        assertTrue(called)
    }
    
    @Test
    fun `onSuccess is not called for Error`() {
        var called = false
        val result: Result<Int> = Result.Error("error")
        result.onSuccess { called = true }
        assertFalse(called)
    }
    
    @Test
    fun `onError is called for Error`() {
        var message = ""
        val result: Result<Int> = Result.Error("test error")
        result.onError { message = it }
        assertEquals("test error", message)
    }
    
    @Test
    fun `getOrNull returns data for Success`() {
        val result = Result.Success(42)
        assertEquals(42, result.getOrNull())
    }
    
    @Test
    fun `getOrNull returns null for Error`() {
        val result: Result<Int> = Result.Error("error")
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `Result of catches exception`() {
        val result = Result.of { throw RuntimeException("test") }
        assertTrue(result is Result.Error)
        assertEquals("test", (result as Result.Error).message)
    }
    
    @Test
    fun `Result of returns Success for valid operation`() {
        val result = Result.of { 42 }
        assertTrue(result is Result.Success)
        assertEquals(42, (result as Result.Success).data)
    }
}
