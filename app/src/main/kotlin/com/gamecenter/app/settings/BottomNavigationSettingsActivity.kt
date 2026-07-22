package com.gamecenter.app.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.gamecenter.app.navigation.BottomNavigationCatalog
import com.gamecenter.app.navigation.BottomNavigationPreferences
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** 用户自定义宿主底部导航的排序与可见性。 */
class BottomNavigationSettingsActivity : AppCompatActivity() {

    private lateinit var preferences: BottomNavigationPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NavigationAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bottom_navigation_settings)
        preferences = BottomNavigationPreferences(this)

        findViewById<MaterialToolbar>(R.id.toolbar_bottom_navigation_settings)
            .setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.text_bottom_navigation_settings_description).text = getString(
            R.string.bottom_nav_settings_description,
            BottomNavigationCatalog.MAX_VISIBLE_ITEMS
        )
        recyclerView = findViewById(R.id.recycler_bottom_navigation_settings)
        recyclerView.layoutManager = LinearLayoutManager(this)

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                saveState()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = false
        })
        touchHelper.attachToRecyclerView(recyclerView)

        findViewById<MaterialButton>(R.id.button_reset_bottom_navigation).setOnClickListener {
            preferences.reset()
            loadItems()
            Toast.makeText(this, R.string.bottom_nav_settings_reset_done, Toast.LENGTH_SHORT).show()
        }
        loadItems()
    }

    private fun loadItems() {
        val discovered = BottomNavigationCatalog.discover(this)
        val ordered = preferences.orderedItems(discovered)
        val visibleIds = preferences.visibleItems(discovered).mapTo(mutableSetOf()) { it.id }
        adapter = NavigationAdapter(
            ordered.map { EditableItem(it, it.id in visibleIds) }.toMutableList(),
            onStartDrag = touchHelper::startDrag,
            onVisibilityChange = ::changeVisibility
        )
        recyclerView.adapter = adapter
    }

    private fun changeVisibility(position: Int, visible: Boolean): Boolean {
        val row = adapter.items.getOrNull(position) ?: return false
        if (row.item.requiredVisible && !visible) {
            Toast.makeText(this, R.string.bottom_nav_settings_required_hint, Toast.LENGTH_SHORT).show()
            return false
        }
        if (visible && adapter.items.count { it.visible } >= BottomNavigationCatalog.MAX_VISIBLE_ITEMS) {
            Toast.makeText(
                this,
                getString(R.string.bottom_nav_settings_max_items, BottomNavigationCatalog.MAX_VISIBLE_ITEMS),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        row.visible = visible
        adapter.notifyItemChanged(position)
        saveState()
        return true
    }

    private fun saveState() {
        preferences.save(
            adapter.items.map { it.item },
            adapter.items.filter { it.visible }.mapTo(mutableSetOf()) { it.item.id }
        )
    }

    private data class EditableItem(
        val item: BottomNavigationCatalog.Item,
        var visible: Boolean
    )

    private class NavigationAdapter(
        val items: MutableList<EditableItem>,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val onVisibilityChange: (Int, Boolean) -> Boolean
    ) : RecyclerView.Adapter<NavigationAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bottom_navigation_setting, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position], onStartDrag, onVisibilityChange)
        }

        fun move(from: Int, to: Int) {
            if (from !in items.indices || to !in items.indices || from == to) return
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.image_bottom_nav_item_icon)
            private val title: TextView = view.findViewById(R.id.text_bottom_nav_item_title)
            private val subtitle: TextView = view.findViewById(R.id.text_bottom_nav_item_subtitle)
            private val visibleSwitch: MaterialSwitch = view.findViewById(R.id.switch_bottom_nav_item_visible)
            private val dragHandle: ImageView = view.findViewById(R.id.image_bottom_nav_drag_handle)
            private var suppressSwitchCallback = false

            fun bind(
                row: EditableItem,
                onStartDrag: (RecyclerView.ViewHolder) -> Unit,
                onVisibilityChange: (Int, Boolean) -> Boolean
            ) {
                val context = itemView.context
                icon.setImageResource(row.item.iconResId)
                title.text = row.item.title
                subtitle.setText(
                    if (row.item.requiredVisible) R.string.bottom_nav_settings_required
                    else R.string.bottom_nav_settings_optional
                )
                visibleSwitch.setOnCheckedChangeListener(null)
                visibleSwitch.isChecked = row.visible
                visibleSwitch.isEnabled = !row.item.requiredVisible
                visibleSwitch.setOnCheckedChangeListener { _, checked ->
                    if (suppressSwitchCallback) return@setOnCheckedChangeListener
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                    if (!onVisibilityChange(position, checked)) {
                        suppressSwitchCallback = true
                        visibleSwitch.isChecked = row.visible
                        suppressSwitchCallback = false
                    }
                }
                itemView.setOnClickListener {
                    if (visibleSwitch.isEnabled) visibleSwitch.isChecked = !visibleSwitch.isChecked
                }
                dragHandle.contentDescription = context.getString(
                    R.string.bottom_nav_settings_drag_description,
                    row.item.title
                )
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(this)
                    false
                }
            }
        }
    }
}
