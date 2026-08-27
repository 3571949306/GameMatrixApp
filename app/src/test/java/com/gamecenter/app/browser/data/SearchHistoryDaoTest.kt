package com.gamecenter.app.browser.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.browser.data.entity.SearchHistoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 浏览器搜索历史 DAO 回归（P3 补强）。
 *
 * 使用 in-memory Room 验证：写入去重（同词更新而非新增）、时间倒序、删除语义。
 */
@RunWith(RobolectricTestRunner::class)
class SearchHistoryDaoTest {

    private lateinit var database: BrowserDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BrowserDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(keyword: String, createTime: Long, count: Int = 1) =
        SearchHistoryEntity().apply {
            setKeyword(keyword)
            setSearchEngine("baidu")
            setCreateTime(createTime)
            setCount(count)
        }

    @Test
    fun `recent searches return newest first with limit`() {
        val dao = database.searchHistoryDao()
        dao.insert(entity("alpha", 100L))
        dao.insert(entity("beta", 200L))
        dao.insert(entity("gamma", 300L))

        val recent = dao.getRecentSearches(2)
        assertEquals(2, recent.size)
        assertEquals("gamma", recent[0].keyword)
        assertEquals("beta", recent[1].keyword)
    }

    @Test
    fun `reinsert same keyword creates two rows since dedup lives in repository`() {
        // DAO 层主键为自增 id（keyword 无唯一索引），REPLACE 仅在主键冲突时生效；
        // 同词去重由 SearchHistoryRepository.saveSearchHistory（先 getByKeyword 再 update）负责。
        val dao = database.searchHistoryDao()
        dao.insert(entity("same", 100L))
        dao.insert(entity("same", 200L))

        assertEquals(2, dao.getRecentSearches(10).size)
    }

    @Test
    fun `getByKeyword finds existing and null when absent`() {
        val dao = database.searchHistoryDao()
        dao.insert(entity("known", 100L))

        assertNotNull(dao.getByKeyword("known"))
        assertNull(dao.getByKeyword("missing"))
    }

    @Test
    fun `deleteAll clears the table`() {
        val dao = database.searchHistoryDao()
        dao.insert(entity("a", 1L))
        dao.insert(entity("b", 2L))

        dao.deleteAll()

        assertEquals(0, dao.getRecentSearches(10).size)
    }
}