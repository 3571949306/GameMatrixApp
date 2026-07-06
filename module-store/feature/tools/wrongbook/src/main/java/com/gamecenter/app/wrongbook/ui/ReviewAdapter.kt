package com.gamecenter.app.wrongbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.ReviewPlanEntity
import com.gamecenter.app.wrongbook.databinding.ItemReviewBinding

class ReviewAdapter(
    private val onComplete: (ReviewPlanEntity) -> Unit,
    private val onSkip: (ReviewPlanEntity) -> Unit
) : ListAdapter<ReviewPlanEntity, ReviewAdapter.ViewHolder>(DiffCallback()) {

    private val questionCache = mutableMapOf<Long, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReviewPlanEntity) {
            val ctx = binding.root.context
            binding.tvStage.text = ctx.getString(R.string.wrongbook_review_stage_format, item.stage)
            binding.tvContent.text = questionCache[item.questionId] ?: ""
            binding.tvSubject.text = ""

            binding.btnComplete.setOnClickListener { onComplete(item) }
            binding.btnSkip.setOnClickListener { onSkip(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ReviewPlanEntity>() {
        override fun areItemsTheSame(oldItem: ReviewPlanEntity, newItem: ReviewPlanEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReviewPlanEntity, newItem: ReviewPlanEntity): Boolean {
            return oldItem == newItem
        }
    }
}
