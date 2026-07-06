package com.gamecenter.app.wrongbook.ui

import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.data.ReviewPlanEntity

/**
 * 复习计划展示项，关联原始错题。
 */
data class ReviewItem(
    val plan: ReviewPlanEntity,
    val question: QuestionEntity?
)
