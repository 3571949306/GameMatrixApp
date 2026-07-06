package com.gamecenter.app.wrongbook.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 错题本数据仓库。
 */
class WrongBookRepository(context: Context) {

    private val dao = WrongBookDatabase.getInstance(context).wrongBookDao()

    // ===== 错题 =====
    suspend fun getAllQuestions(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getAllQuestions()
    }

    suspend fun getQuestionsBySubject(subject: String): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsBySubject(subject)
    }

    suspend fun getQuestionById(id: Long): QuestionEntity? = withContext(Dispatchers.IO) {
        dao.getQuestionById(id)
    }

    suspend fun saveQuestion(entity: QuestionEntity): Long = withContext(Dispatchers.IO) {
        val id = dao.insertQuestion(entity)
        updateTopicMastery(entity.subject)
        id
    }

    suspend fun updateQuestion(entity: QuestionEntity) = withContext(Dispatchers.IO) {
        dao.updateQuestion(entity.copy(updatedAt = System.currentTimeMillis()))
        updateTopicMastery(entity.subject)
    }

    suspend fun deleteQuestion(entity: QuestionEntity) = withContext(Dispatchers.IO) {
        dao.deleteReviewPlansByQuestion(entity.id)
        dao.deleteQuestion(entity)
        updateTopicMastery(entity.subject)
    }

    // ===== 科目 =====
    suspend fun getAllSubjects(): List<SubjectEntity> = withContext(Dispatchers.IO) {
        dao.getAllSubjects()
    }

    suspend fun ensureSubject(name: String) = withContext(Dispatchers.IO) {
        dao.insertSubject(SubjectEntity(name = name))
    }

    suspend fun deleteSubject(entity: SubjectEntity) = withContext(Dispatchers.IO) {
        dao.deleteSubject(entity)
    }

    // ===== 复习计划 =====
    suspend fun getPendingReviewsBefore(beforeTime: Long): List<ReviewPlanEntity> =
        withContext(Dispatchers.IO) {
            dao.getPendingReviewsBefore(beforeTime)
        }

    suspend fun getReviewPlansByQuestion(questionId: Long): List<ReviewPlanEntity> =
        withContext(Dispatchers.IO) {
            dao.getReviewPlansByQuestion(questionId)
        }

    suspend fun completeReview(plan: ReviewPlanEntity) = withContext(Dispatchers.IO) {
        dao.updateReviewPlan(plan.copy(status = "completed", completedAt = System.currentTimeMillis()))
    }

    /**
     * 为指定错题生成艾宾浩斯复习计划。
     */
    suspend fun generateReviewPlans(questionId: Long) = withContext(Dispatchers.IO) {
        dao.deleteReviewPlansByQuestion(questionId)
        val now = System.currentTimeMillis()
        // 艾宾浩斯遗忘曲线：20分钟、1小时、9小时、1天、2天、6天、31天
        val intervals = listOf(20L, 60L, 9 * 60L, 24 * 60L, 2 * 24 * 60L, 6 * 24 * 60L, 31 * 24 * 60L)
        intervals.forEachIndexed { index, minutes ->
            dao.insertReviewPlan(
                ReviewPlanEntity(
                    questionId = questionId,
                    stage = index + 1,
                    scheduledAt = now + minutes * 60 * 1000
                )
            )
        }
    }

    // ===== 知识点掌握度 =====
    suspend fun getAllTopicMastery(): List<TopicMasteryEntity> = withContext(Dispatchers.IO) {
        dao.getAllTopicMastery()
    }

    suspend fun getTopicMasteryBySubject(subject: String): List<TopicMasteryEntity> =
        withContext(Dispatchers.IO) {
            dao.getTopicMasteryBySubject(subject)
        }

    private suspend fun updateTopicMastery(subject: String) {
        val questions = dao.getQuestionsBySubject(subject)
        val topicMap = mutableMapOf<String, MutableList<Int>>()
        questions.forEach { q ->
            runCatching { JSONArray(q.knowledgePoints) }.getOrNull()?.let { array ->
                for (i in 0 until array.length()) {
                    val topic = array.optString(i, "").ifEmpty { return@let }
                    topicMap.getOrPut(topic) { mutableListOf() }.add(q.mastery)
                }
            }
        }
        topicMap.forEach { (topic, list) ->
            val avg = if (list.isNotEmpty()) list.sum() / list.size else 0
            val existing = dao.getAllTopicMastery().find { it.subject == subject && it.topic == topic }
            if (existing != null) {
                dao.updateTopicMastery(existing.copy(mastery = avg, questionCount = list.size))
            } else {
                dao.insertTopicMastery(TopicMasteryEntity(subject = subject, topic = topic, mastery = avg, questionCount = list.size))
            }
        }
    }
}
