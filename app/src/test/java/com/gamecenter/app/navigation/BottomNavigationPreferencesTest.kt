package com.gamecenter.app.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomNavigationPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: BottomNavigationPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = BottomNavigationPreferences(context)
        preferences.reset()
    }

    @After
    fun tearDown() {
        preferences.reset()
    }

    @Test
    fun `saved order and hidden destinations are applied`() {
        val items = defaultItems()
        val reordered = listOf(items[0], items[2], items[1])

        preferences.save(reordered, setOf("games_hall", "profile"))

        assertEquals(listOf("games_hall", "profile", "browser"), preferences.orderedItems(items).map { it.id })
        assertEquals(listOf("games_hall", "profile"), preferences.visibleItems(items).map { it.id })
    }

    @Test
    fun `required game hall survives hiding and six item overflow`() {
        val items = (1..7).map { index ->
            item("item_$index", index * 10)
        } + item("games_hall", 1000, required = true)

        preferences.save(items, items.mapTo(mutableSetOf()) { it.id })
        val visible = preferences.visibleItems(items)

        assertEquals(BottomNavigationCatalog.MAX_VISIBLE_ITEMS, visible.size)
        assertTrue(visible.any { it.id == "games_hall" })
        assertFalse(preferences.hiddenIds().contains("games_hall"))
    }

    @Test
    fun `new catalog destination is appended without disturbing custom order`() {
        val items = defaultItems()
        preferences.save(listOf(items[0], items[2], items[1]), items.mapTo(mutableSetOf()) { it.id })
        val newItem = item("new_module", 15)

        assertEquals(
            listOf("games_hall", "profile", "browser", "new_module"),
            preferences.orderedItems(items + newItem).map { it.id }
        )
    }

    private fun defaultItems() = listOf(
        item("games_hall", 10, required = true),
        item("browser", 20),
        item("profile", 100)
    )

    private fun item(id: String, order: Int, required: Boolean = false) = BottomNavigationCatalog.Item(
        id = id,
        moduleId = id,
        title = id,
        iconResId = R.drawable.ic_extension,
        defaultOrder = order,
        requiredVisible = required,
        destinationKind = BottomNavigationCatalog.DestinationKind.MODULE_SHELL
    )
}
