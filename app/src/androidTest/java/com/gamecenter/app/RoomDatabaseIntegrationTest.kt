package com.gamecenter.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.entity.AiMessageEntity
import com.gamecenter.app.database.entity.GameStatsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDatabaseIntegrationTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndQueryAiMessage() = runTest {
        val dao = database.aiMessageDao()
        val entity = AiMessageEntity(
            sessionId = "test-session",
            role = "user",
            content = "Hello AI"
        )
        val id = dao.insert(entity)
        assertTrue(id > 0)

        val messages = dao.getBySessionId("test-session")
        assertEquals(1, messages.size)
        assertEquals("Hello AI", messages[0].content)
        assertEquals("user", messages[0].role)
    }

    @Test
    fun testDeleteAiMessagesBySessionId() = runTest {
        val dao = database.aiMessageDao()
        dao.insert(AiMessageEntity(sessionId = "s1", role = "user", content = "msg1"))
        dao.insert(AiMessageEntity(sessionId = "s2", role = "user", content = "msg2"))
        dao.deleteBySessionId("s1")
        assertEquals(0, dao.getBySessionId("s1").size)
        assertEquals(1, dao.getBySessionId("s2").size)
    }

    @Test
    fun testInsertAndQueryGameStats() = runTest {
        val dao = database.gameStatsDao()
        val entity = GameStatsEntity(
            gameType = "doudizhu",
            result = "win",
            durationMs = 60000L
        )
        val id = dao.insert(entity)
        assertTrue(id > 0)

        val stats = dao.getByGameType("doudizhu")
        assertEquals(1, stats.size)
        assertEquals("win", stats[0].result)
    }

    @Test
    fun testCountByGameTypeAndResult() = runTest {
        val dao = database.gameStatsDao()
        dao.insert(GameStatsEntity(gameType = "doudizhu", result = "win", durationMs = 60000L))
        dao.insert(GameStatsEntity(gameType = "doudizhu", result = "win", durationMs = 45000L))
        dao.insert(GameStatsEntity(gameType = "doudizhu", result = "lose", durationMs = 30000L))
        assertEquals(2, dao.countByGameTypeAndResult("doudizhu", "win"))
        assertEquals(1, dao.countByGameTypeAndResult("doudizhu", "lose"))
    }
}
