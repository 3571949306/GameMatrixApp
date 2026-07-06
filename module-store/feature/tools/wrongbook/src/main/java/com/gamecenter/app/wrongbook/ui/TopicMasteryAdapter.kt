package com.gamecenter.app.wrongbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.data.TopicMasteryEntity
import com.gamecenter.app.wrongbook.databinding.ItemTopicBinding

class TopicMasteryAdapter : ListAdapter<TopicMasteryEntity, TopicMasteryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopicBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTopicBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TopicMasteryEntity) {
            binding.tvTopicName.text = item.topic
            binding.tvTopicSubject.text = item.subject
            binding.tvMastery.text = "${item.mastery}%"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TopicMasteryEntity>() {
        override fun areItemsTheSame(oldItem: TopicMasteryEntity, newItem: TopicMasteryEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TopicMasteryEntity, newItem: TopicMasteryEntity): Boolean {
            return oldItem == newItem
        }
    }
}
