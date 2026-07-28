package com.gamecenter.app.wrongbook.ui

import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.MasteryLevel
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.databinding.ItemQuestionBinding
import org.json.JSONArray
import java.io.File

class QuestionAdapter(
    var onItemClick: ((QuestionEntity) -> Unit)? = null,
    var onItemLongClick: ((QuestionEntity) -> Boolean)? = null,
    var onFavoriteClick: ((QuestionEntity) -> Unit)? = null
) : ListAdapter<QuestionEntity, QuestionAdapter.ViewHolder>(DiffCallback()) {

    var isMultiSelectMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    val selectedIds = mutableSetOf<Long>()

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
    }

    fun selectAll(ids: List<Long>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemQuestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QuestionEntity) {
            val context = binding.root.context
            
            binding.tvSubject.text = item.subject
            binding.tvContent.text = item.rawText

            // 掌握度（统一阈值，详见 MasteryLevel）
            binding.pbMastery.progress = item.mastery
            binding.tvMasteryPercent.text = "${item.mastery}%"
            val masteryColor = MasteryLevel.colorResByMastery(item.mastery)
            binding.pbMastery.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, masteryColor)
            )

            // 难度星级
            binding.layoutStars.removeAllViews()
            for (i in 1..5) {
                val star = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (16 * context.resources.displayMetrics.density).toInt(),
                        (16 * context.resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(0, 0, (2 * context.resources.displayMetrics.density).toInt(), 0)
                    }
                    setImageResource(if (i <= item.difficulty) R.drawable.wrongbook_star_filled else R.drawable.wrongbook_star_empty)
                }
                binding.layoutStars.addView(star)
            }

            // 收藏状态
            binding.btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.wrongbook_star_filled else R.drawable.wrongbook_star_empty
            )
            binding.btnFavorite.setOnClickListener {
                onFavoriteClick?.invoke(item)
            }

            // 批量选择
            if (isMultiSelectMode) {
                binding.btnFavorite.visibility = View.GONE
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = selectedIds.contains(item.id)
                binding.root.isSelected = selectedIds.contains(item.id)
            } else {
                binding.btnFavorite.visibility = View.VISIBLE
                binding.cbSelect.visibility = View.GONE
                binding.root.isSelected = false
            }

            // 缩略图
            if (item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
                binding.thumbnailContainer.visibility = View.VISIBLE
                binding.ivThumbnail.setImageURI(Uri.fromFile(File(item.imagePath)))
            } else {
                binding.thumbnailContainer.visibility = View.GONE
            }

            // 知识点与自定义标签 Chip
            val topics = parseKnowledgePoints(item.knowledgePoints) + 
                         item.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            
            binding.tagsContainer.removeAllViews()
            topics.distinct().forEach { topic ->
                val tv = TextView(context).apply {
                    text = topic
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.wrongbook_chip_text))
                    setBackgroundResource(R.drawable.wrongbook_chip_background)
                    val pxPaddingHorizontal = (8 * context.resources.displayMetrics.density).toInt()
                    val pxPaddingVertical = (3 * context.resources.displayMetrics.density).toInt()
                    setPadding(pxPaddingHorizontal, pxPaddingVertical, pxPaddingHorizontal, pxPaddingVertical)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, (6 * context.resources.displayMetrics.density).toInt(), 0)
                    }
                }
                binding.tagsContainer.addView(tv)
            }
            binding.tagsScrollView.visibility = if (topics.isEmpty()) View.GONE else View.VISIBLE

            // 事件点击
            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick?.invoke(item)
                }
            }

            binding.root.setOnLongClickListener {
                onItemLongClick?.invoke(item) ?: false
            }
        }

        private fun parseKnowledgePoints(json: String): List<String> {
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<QuestionEntity>() {
        override fun areItemsTheSame(oldItem: QuestionEntity, newItem: QuestionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: QuestionEntity, newItem: QuestionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
