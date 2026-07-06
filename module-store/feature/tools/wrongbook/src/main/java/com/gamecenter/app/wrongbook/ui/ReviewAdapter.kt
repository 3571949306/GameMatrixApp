package com.gamecenter.app.wrongbook.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.databinding.ItemReviewBinding
import java.io.File

class ReviewAdapter(
    private val onComplete: (ReviewItem) -> Unit,
    private val onSkip: (ReviewItem) -> Unit
) : ListAdapter<ReviewItem, ReviewAdapter.ViewHolder>(DiffCallback()) {

    // 记录已经点击过“显示解析与答案”的项的 ID，防止滑动复用时状态丢失
    private val revealedIds = mutableSetOf<Long>()

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

        fun bind(item: ReviewItem) {
            val ctx = binding.root.context
            val plan = item.plan
            val question = item.question

            binding.tvStage.text = ctx.getString(R.string.wrongbook_review_stage_format, plan.stage)
            binding.tvSubject.text = question?.subject ?: "通用"
            binding.tvContent.text = question?.rawText ?: ""
            binding.tvAnalysis.text = question?.analysis ?: ""

            // 图片显示
            if (question != null && question.imagePath.isNotBlank() && File(question.imagePath).exists()) {
                binding.thumbnailContainer.visibility = View.VISIBLE
                binding.ivThumbnail.setImageURI(Uri.fromFile(File(question.imagePath)))
            } else {
                binding.thumbnailContainer.visibility = View.GONE
            }

            // 遮挡/背诵模式状态管理
            val isRevealed = revealedIds.contains(plan.id)
            if (isRevealed) {
                binding.layoutAnswer.visibility = View.VISIBLE
                binding.layoutActions.visibility = View.VISIBLE
                binding.btnReveal.visibility = View.GONE
            } else {
                binding.layoutAnswer.visibility = View.GONE
                binding.layoutActions.visibility = View.GONE
                binding.btnReveal.visibility = View.VISIBLE
            }

            // “显示解析与答案”点击
            binding.btnReveal.setOnClickListener {
                revealedIds.add(plan.id)
                // 动效平滑显示
                binding.layoutAnswer.visibility = View.VISIBLE
                binding.layoutActions.visibility = View.VISIBLE
                binding.btnReveal.visibility = View.GONE
                
                binding.layoutAnswer.alpha = 0f
                binding.layoutAnswer.animate().alpha(1.0f).setDuration(250).start()
                binding.layoutActions.alpha = 0f
                binding.layoutActions.animate().alpha(1.0f).setDuration(250).start()
            }

            // 完成和跳过
            binding.btnComplete.setOnClickListener {
                revealedIds.remove(plan.id)
                onComplete(item)
            }
            binding.btnSkip.setOnClickListener {
                revealedIds.remove(plan.id)
                onSkip(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ReviewItem>() {
        override fun areItemsTheSame(oldItem: ReviewItem, newItem: ReviewItem): Boolean {
            return oldItem.plan.id == newItem.plan.id
        }

        override fun areContentsTheSame(oldItem: ReviewItem, newItem: ReviewItem): Boolean {
            return oldItem == newItem
        }
    }
}
