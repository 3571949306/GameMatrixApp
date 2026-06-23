package com.gamecenter.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.database.AppDatabase
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

    // 注意：AiMessage 相关测试已移除，因为 AiMessageEntity 和 aiMessageDao()
    // 定义在 AI 模块中，不在 app 模块的 AppDatabase 里。

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

        val stats = dao.getByGameTypeSync("doudizhu")
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
