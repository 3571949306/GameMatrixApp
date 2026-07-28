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
     * 跳过复习项：将 status 持久化为 "skipped"，避免重新进入复习页时再次出现。
     * 之前 ReviewFragment.onSkip 只修改内存列表，未调用此方法，导致跳过的题目"复活"。
     */
    suspend fun skipReview(plan: ReviewPlanEntity) = withContext(Dispatchers.IO) {
        dao.updateReviewPlan(plan.copy(status = "skipped", completedAt = System.currentTimeMillis()))
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

    // ===== 新增查询、排序与批量操作 =====
    suspend fun getFavoriteQuestions(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getFavoriteQuestions()
    }

    suspend fun getQuestionsFavoriteFirst(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsFavoriteFirst()
    }

    suspend fun getQuestionsSortedByTimeDesc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByTimeDesc()
    }

    suspend fun getQuestionsSortedByTimeAsc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByTimeAsc()
    }

    suspend fun getQuestionsSortedByDifficultyDesc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByDifficultyDesc()
    }

    suspend fun getQuestionsSortedByDifficultyAsc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByDifficultyAsc()
    }

    suspend fun getQuestionsSortedByMasteryDesc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByMasteryDesc()
    }

    suspend fun getQuestionsSortedByMasteryAsc(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.getQuestionsSortedByMasteryAsc()
    }

    suspend fun searchQuestions(query: String): List<QuestionEntity> = withContext(Dispatchers.IO) {
        dao.searchQuestions("%$query%")
    }

    suspend fun deleteQuestionsByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            dao.deleteReviewPlansByQuestion(id)
        }
        dao.deleteQuestionsByIds(ids)
    }

    suspend fun batchUpdateFavorite(ids: List<Long>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.batchUpdateFavorite(ids, isFavorite)
    }

    suspend fun batchUpdateSubject(ids: List<Long>, subject: String) = withContext(Dispatchers.IO) {
        dao.insertSubject(SubjectEntity(name = subject))
        dao.batchUpdateSubject(ids, subject)
    }

    suspend fun updateSubject(entity: SubjectEntity) = withContext(Dispatchers.IO) {
        dao.updateSubject(entity)
    }

    // ===== JSON 导入/导出 =====
    suspend fun exportToJson(exportFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            val questions = dao.getAllQuestions()
            val subjects = dao.getAllSubjects()
            
            val root = org.json.JSONObject()
            
            val qArray = org.json.JSONArray()
            questions.forEach { q ->
                val obj = org.json.JSONObject()
                obj.put("id", q.id)
                obj.put("rawText", q.rawText)
                obj.put("subject", q.subject)
                obj.put("difficulty", q.difficulty)
                obj.put("analysis", q.analysis)
                obj.put("knowledgePoints", q.knowledgePoints)
                obj.put("imagePath", q.imagePath)
                obj.put("createdAt", q.createdAt)
                obj.put("updatedAt", q.updatedAt)
                obj.put("mastery", q.mastery)
                obj.put("isFavorite", q.isFavorite)
                obj.put("sortOrder", q.sortOrder)
                obj.put("tags", q.tags)
                // 第三阶段扩展字段
                obj.put("questionType", q.questionType)
                obj.put("question", q.question)
                obj.put("optionsJson", q.optionsJson)
                obj.put("answer", q.answer)
                obj.put("wrongReason", q.wrongReason)
                obj.put("reviewSuggestion", q.reviewSuggestion)
                obj.put("confidence", q.confidence)
                obj.put("ocrText", q.ocrText)
                obj.put("correctedText", q.correctedText)
                obj.put("sourceType", q.sourceType)
                obj.put("ocrProvider", q.ocrProvider)
                obj.put("aiProvider", q.aiProvider)
                obj.put("aiModel", q.aiModel)
                qArray.put(obj)
            }
            root.put("questions", qArray)
            
            val sArray = org.json.JSONArray()
            subjects.forEach { s ->
                val obj = org.json.JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("color", s.color)
                obj.put("sortOrder", s.sortOrder)
                sArray.put(obj)
            }
            root.put("subjects", sArray)
            
            exportFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            exportFile.writeText(root.toString(4))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importFromJson(importFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!importFile.exists()) return@withContext false
            val text = importFile.readText()
            val root = org.json.JSONObject(text)
            
            val sArray = root.optJSONArray("subjects")
            if (sArray != null) {
                for (i in 0 until sArray.length()) {
                    val obj = sArray.getJSONObject(i)
                    val name = obj.optString("name", "")
                    if (name.isNotEmpty()) {
                        dao.insertSubject(SubjectEntity(
                            name = name,
                            color = obj.optInt("color", 0),
                            sortOrder = obj.optInt("sortOrder", 0)
                        ))
                    }
                }
            }
            
            val qArray = root.optJSONArray("questions")
            if (qArray != null) {
                for (i in 0 until qArray.length()) {
                    val obj = qArray.getJSONObject(i)
                    val rawText = obj.optString("rawText", "")
                    if (rawText.isNotEmpty()) {
                        val q = QuestionEntity(
                            rawText = rawText,
                            subject = obj.optString("subject", "通用"),
                            difficulty = obj.optInt("difficulty", 3),
                            analysis = obj.optString("analysis", ""),
                            knowledgePoints = obj.optString("knowledgePoints", "[]"),
                            imagePath = obj.optString("imagePath", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            mastery = obj.optInt("mastery", 0),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            sortOrder = obj.optInt("sortOrder", 0),
                            tags = obj.optString("tags", ""),
                            // 第三阶段扩展字段（向后兼容：旧备份文件缺失时使用默认值）
                            questionType = obj.optString("questionType", "unknown"),
                            question = obj.optString("question", ""),
                            optionsJson = obj.optString("optionsJson", "[]"),
                            answer = obj.optString("answer", ""),
                            wrongReason = obj.optString("wrongReason", ""),
                            reviewSuggestion = obj.optString("reviewSuggestion", ""),
                            confidence = obj.optDouble("confidence", 0.0),
                            ocrText = obj.optString("ocrText", ""),
                            correctedText = obj.optString("correctedText", ""),
                            sourceType = obj.optString("sourceType", "manual"),
                            ocrProvider = obj.optString("ocrProvider", ""),
                            aiProvider = obj.optString("aiProvider", ""),
                            aiModel = obj.optString("aiModel", "")
                        )
                        val newId = dao.insertQuestion(q)
                        if (dao.getReviewPlansByQuestion(newId).isEmpty()) {
                            generateReviewPlans(newId)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
