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

    @Delete
    suspend fun deleteSubject(entity: SubjectEntity)
}
