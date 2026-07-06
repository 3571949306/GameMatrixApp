package com.gamecenter.app.wrongbook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WrongBookDao {

    // ===== 错题 =====
    @Query("SELECT * FROM questions ORDER BY updatedAt DESC")
    suspend fun getAllQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY updatedAt DESC")
    suspend fun getQuestionsBySubject(subject: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: Long): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(entity: QuestionEntity): Long

    @Update
    suspend fun updateQuestion(entity: QuestionEntity)

    @Delete
    suspend fun deleteQuestion(entity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE subject = :subject")
    suspend fun getQuestionCountBySubject(subject: String): Int

    // 新增：按收藏筛选
    @Query("SELECT * FROM questions WHERE isFavorite = 1 ORDER BY createdAt DESC")
    suspend fun getFavoriteQuestions(): List<QuestionEntity>

    // 新增：按条件排序（基础排序查询）
    @Query("SELECT * FROM questions ORDER BY isFavorite DESC, createdAt DESC")
    suspend fun getQuestionsFavoriteFirst(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    suspend fun getQuestionsSortedByTimeDesc(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY createdAt ASC")
    suspend fun getQuestionsSortedByTimeAsc(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY difficulty DESC")
    suspend fun getQuestionsSortedByDifficultyDesc(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY difficulty ASC")
    suspend fun getQuestionsSortedByDifficultyAsc(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY mastery DESC")
    suspend fun getQuestionsSortedByMasteryDesc(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY mastery ASC")
    suspend fun getQuestionsSortedByMasteryAsc(): List<QuestionEntity>

    // 新增：模糊搜索题目内容、知识点和科目
    @Query("SELECT * FROM questions WHERE rawText LIKE :query OR tags LIKE :query OR subject LIKE :query OR knowledgePoints LIKE :query ORDER BY createdAt DESC")
    suspend fun searchQuestions(query: String): List<QuestionEntity>

    // 新增：批量删除错题
    @Query("DELETE FROM questions WHERE id IN (:ids)")
    suspend fun deleteQuestionsByIds(ids: List<Long>)

    // 新增：批量更新收藏状态
    @Query("UPDATE questions SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun batchUpdateFavorite(ids: List<Long>, isFavorite: Boolean)

    // 新增：批量移动科目
    @Query("UPDATE questions SET subject = :subject WHERE id IN (:ids)")
    suspend fun batchUpdateSubject(ids: List<Long>, subject: String)

    // ===== 复习计划 =====
    @Query("SELECT * FROM review_plans WHERE questionId = :questionId ORDER BY stage ASC")
    suspend fun getReviewPlansByQuestion(questionId: Long): List<ReviewPlanEntity>

    @Query(
        "SELECT * FROM review_plans " +
                "WHERE status = 'pending' AND scheduledAt <= :beforeTime " +
                "ORDER BY scheduledAt ASC"
    )
    suspend fun getPendingReviewsBefore(beforeTime: Long): List<ReviewPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewPlan(entity: ReviewPlanEntity): Long

    @Update
    suspend fun updateReviewPlan(entity: ReviewPlanEntity)

    @Query("DELETE FROM review_plans WHERE questionId = :questionId")
    suspend fun deleteReviewPlansByQuestion(questionId: Long)

    // ===== 知识点掌握度 =====
    @Query("SELECT * FROM topic_mastery ORDER BY subject, mastery ASC")
    suspend fun getAllTopicMastery(): List<TopicMasteryEntity>

    @Query("SELECT * FROM topic_mastery WHERE subject = :subject ORDER BY mastery ASC")
    suspend fun getTopicMasteryBySubject(subject: String): List<TopicMasteryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicMastery(entity: TopicMasteryEntity): Long

    @Update
    suspend fun updateTopicMastery(entity: TopicMasteryEntity)

    // ===== 科目 =====
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSubjects(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(entity: SubjectEntity): Long

    @Update
    suspend fun updateSubject(entity: SubjectEntity)

    @Delete
    suspend fun deleteSubject(entity: SubjectEntity)
}
