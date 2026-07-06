package com.gamecenter.app.wrongbook.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gamecenter.app.wrongbook.analysis.AiAnalysisService
import com.gamecenter.app.wrongbook.analysis.AnalysisResult
import com.gamecenter.app.wrongbook.analysis.OcrResult
import com.gamecenter.app.wrongbook.analysis.OcrService
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.data.ReviewPlanEntity
import com.gamecenter.app.wrongbook.data.SubjectEntity
import com.gamecenter.app.wrongbook.data.TopicMasteryEntity
import com.gamecenter.app.wrongbook.data.WrongBookRepository
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * 错题本模块 ViewModel。
 */
class WrongBookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WrongBookRepository(application)
    private val ocrService = OcrService(application)
    private val aiService = AiAnalysisService(application)

    private val _questions = MutableLiveData<List<QuestionEntity>>()
    val questions: LiveData<List<QuestionEntity>> = _questions

    private val _subjects = MutableLiveData<List<SubjectEntity>>()
    val subjects: LiveData<List<SubjectEntity>> = _subjects

    private val _reviews = MutableLiveData<List<ReviewPlanEntity>>()
    val reviews: LiveData<List<ReviewPlanEntity>> = _reviews

    private val _reviewItems = MutableLiveData<List<ReviewItem>>()
    val reviewItems: LiveData<List<ReviewItem>> = _reviewItems

    private val _topicMastery = MutableLiveData<List<TopicMasteryEntity>>()
    val topicMastery: LiveData<List<TopicMasteryEntity>> = _topicMastery

    private val _ocrResult = MutableLiveData<OcrResult?>()
    val ocrResult: LiveData<OcrResult?> = _ocrResult

    private val _analysisResult = MutableLiveData<AnalysisResult?>()
    val analysisResult: LiveData<AnalysisResult?> = _analysisResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var currentSubjectFilter: String? = null

    init {
        loadQuestions()
        loadSubjects()
        loadReviews()
        loadTopicMastery()
    }

    fun loadQuestions() {
        viewModelScope.launch {
            val list = if (currentSubjectFilter.isNullOrBlank()) {
                repository.getAllQuestions()
            } else {
                repository.getQuestionsBySubject(currentSubjectFilter!!)
            }
            _questions.postValue(list)
        }
    }

    fun setSubjectFilter(subject: String?) {
        currentSubjectFilter = subject
        loadQuestions()
    }

    fun loadSubjects() {
        viewModelScope.launch {
            _subjects.postValue(repository.getAllSubjects())
        }
    }

    fun loadReviews() {
        viewModelScope.launch {
            val plans = repository.getPendingReviewsBefore(System.currentTimeMillis())
            _reviews.postValue(plans)
            val questions = repository.getAllQuestions().associateBy { it.id }
            _reviewItems.postValue(plans.map { ReviewItem(it, questions[it.questionId]) })
        }
    }

    fun loadTopicMastery() {
        viewModelScope.launch {
            _topicMastery.postValue(repository.getAllTopicMastery())
        }
    }

    fun recognizeImage(imageUri: Uri) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = ocrService.recognize(getApplication(), imageUri)
            _ocrResult.postValue(result)
            _isLoading.postValue(false)
            if (!result.success) {
                _errorMessage.postValue(result.message)
            }
        }
    }

    fun analyzeText(text: String) {
        if (text.isBlank()) {
            _errorMessage.value = "题目内容为空"
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = aiService.analyze(text)
            _analysisResult.postValue(result)
            _isLoading.postValue(false)
            if (!result.success) {
                _errorMessage.postValue(result.message)
            }
        }
    }

    fun saveQuestion(rawText: String, analysisResult: AnalysisResult, imagePath: String = "") {
        if (rawText.isBlank()) {
            _errorMessage.value = "题目内容为空"
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val subject = analysisResult.subject.ifBlank { "通用" }
            repository.ensureSubject(subject)
            val entity = QuestionEntity(
                rawText = rawText,
                subject = subject,
                difficulty = analysisResult.difficulty,
                analysis = analysisResult.analysis,
                knowledgePoints = JSONArray(analysisResult.knowledgePoints).toString(),
                imagePath = imagePath
            )
            val id = repository.saveQuestion(entity)
            repository.generateReviewPlans(id)
            _isLoading.postValue(false)
            loadQuestions()
            loadSubjects()
            loadReviews()
            loadTopicMastery()
        }
    }

    fun deleteQuestion(entity: QuestionEntity) {
        viewModelScope.launch {
            repository.deleteQuestion(entity)
            loadQuestions()
            loadReviews()
            loadTopicMastery()
        }
    }

    fun completeReview(plan: ReviewPlanEntity) {
        viewModelScope.launch {
            repository.completeReview(plan)
            // 提升对应错题掌握度
            repository.getQuestionById(plan.questionId)?.let { q ->
                val newMastery = (q.mastery + 15).coerceAtMost(100)
                repository.updateQuestion(q.copy(mastery = newMastery))
            }
            loadReviews()
            loadQuestions()
            loadTopicMastery()
        }
    }

    fun clearOcrResult() {
        _ocrResult.value = null
    }

    fun clearAnalysisResult() {
        _analysisResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
