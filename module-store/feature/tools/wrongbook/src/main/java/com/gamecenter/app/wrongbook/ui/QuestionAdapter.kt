package com.gamecenter.app.wrongbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.databinding.ItemQuestionBinding
import org.json.JSONArray

class QuestionAdapter(
    private val onDeleteClick: (QuestionEntity) -> Unit
) : ListAdapter<QuestionEntity, QuestionAdapter.ViewHolder>(DiffCallback()) {

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
            binding.tvSubject.text = item.subject
            binding.tvDifficulty.text = "${item.difficulty}/5"
            binding.tvContent.text = item.rawText

            val topics = parseKnowledgePoints(item.knowledgePoints)
            binding.tvKnowledge.text = if (topics.isEmpty()) "" else "知识点：${topics.joinToString(", ")}"

            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
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
