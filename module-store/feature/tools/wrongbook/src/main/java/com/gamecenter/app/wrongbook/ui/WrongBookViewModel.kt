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
import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class SortType {
    TIME_DESC, TIME_ASC, DIFFICULTY_DESC, DIFFICULTY_ASC, MASTERY_DESC, MASTERY_ASC
}

/**
 * 错题本模块 ViewModel。
 */
class WrongBookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WrongBookRepository(application)
    private val ocrService = OcrService(application)
    private val aiService = AiAnalysisService(application)

    /** 当前 OCR 引擎标识（供 UI 溯源写入数据库） */
    val currentOcrProvider: String
        get() = ocrService.currentEngine

    /** 当前 OCR 引擎是否为云端（供 UI 决定是否弹 consent） */
    val isCloudOcrEngine: Boolean
        get() = ocrService.isCloudEngine

    /** 当前 AI 模式标识（供 UI 溯源写入数据库） */
    val currentAiProvider: String
        get() = aiService.mode

    /** 当前 AI 模型名称（供 UI 溯源写入数据库） */
    val currentAiModel: String
        get() = aiService.model

    /** 当前 AI 分析是否为云端模式（cloud / backend_proxy） */
    val isCloudAiMode: Boolean
        get() = aiService.mode != "local"

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

    private val _importExportStatus = MutableLiveData<Boolean?>()
    val importExportStatus: LiveData<Boolean?> = _importExportStatus

    private val _selectTabEvent = MutableLiveData<Int?>()
    val selectTabEvent: LiveData<Int?> = _selectTabEvent

    fun selectTab(position: Int) {
        _selectTabEvent.postValue(position)
    }

    fun clearSelectTabEvent() {
        _selectTabEvent.value = null
    }

    private var currentSubjectFilter: String? = null
    private var currentSortType = SortType.TIME_DESC
    private var showOnlyFavorites = false
    private var searchQuery = ""

    init {
        loadQuestions()
        loadSubjects()
        loadReviews()
        loadTopicMastery()
    }

    fun loadQuestions() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            var list = if (searchQuery.isNotBlank()) {
                repository.searchQuestions(searchQuery)
            } else if (currentSubjectFilter.isNullOrBlank()) {
                repository.getAllQuestions()
            } else {
                repository.getQuestionsBySubject(currentSubjectFilter!!)
            }

            // 过滤收藏
            if (showOnlyFavorites) {
                list = list.filter { it.isFavorite }
            }

            // 排序
            list = when (currentSortType) {
                SortType.TIME_DESC -> list.sortedByDescending { it.createdAt }
                SortType.TIME_ASC -> list.sortedBy { it.createdAt }
                SortType.DIFFICULTY_DESC -> list.sortedByDescending { it.difficulty }
                SortType.DIFFICULTY_ASC -> list.sortedBy { it.difficulty }
                SortType.MASTERY_DESC -> list.sortedByDescending { it.mastery }
                SortType.MASTERY_ASC -> list.sortedBy { it.mastery }
            }

            _questions.postValue(list)
            _isLoading.postValue(false)
        }
    }

    fun setSubjectFilter(subject: String?) {
        currentSubjectFilter = subject
        loadQuestions()
    }

    fun setSortType(sortType: SortType) {
        currentSortType = sortType
        loadQuestions()
    }

    fun setFavoriteFilter(onlyFavorites: Boolean) {
        showOnlyFavorites = onlyFavorites
        loadQuestions()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
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

    fun recognizeImage(imageUri: Uri, accurate: Boolean = false, forceLocal: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = if (forceLocal) {
                ocrService.recognizeLocal(getApplication(), imageUri, accurate)
            } else {
                ocrService.recognize(getApplication(), imageUri, accurate)
            }
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

    suspend fun saveQuestion(
        rawText: String,
        analysisResult: AnalysisResult,
        imagePath: String = "",
        isFavorite: Boolean = false,
        tags: String = "",
        ocrText: String = "",
        sourceType: String = "manual",
        ocrProvider: String = "",
        aiProvider: String = "",
        aiModel: String = ""
    ): Boolean {
        if (rawText.isBlank()) {
            _errorMessage.value = "题目内容为空"
            return false
        }
        _isLoading.value = true
        return try {
            val subject = analysisResult.subject.ifBlank { "通用" }
            repository.ensureSubject(subject)
            val entity = QuestionEntity(
                rawText = rawText,
                subject = subject,
                difficulty = analysisResult.difficulty,
                analysis = analysisResult.analysis,
                knowledgePoints = JSONArray(analysisResult.knowledgePoints).toString(),
                imagePath = imagePath,
                isFavorite = isFavorite,
                tags = tags,
                // 第三阶段扩展字段
                questionType = analysisResult.questionType,
                question = analysisResult.question,
                optionsJson = JSONArray(analysisResult.options).toString(),
                answer = analysisResult.answer,
                wrongReason = analysisResult.wrongReason,
                reviewSuggestion = analysisResult.reviewSuggestion,
                confidence = analysisResult.confidence,
                ocrText = ocrText,
                correctedText = rawText,
                sourceType = sourceType,
                ocrProvider = ocrProvider,
                aiProvider = aiProvider,
                aiModel = aiModel
            )
            val id = repository.saveQuestion(entity)
            repository.generateReviewPlans(id)
            loadQuestions()
            loadSubjects()
            loadReviews()
            loadTopicMastery()
            true
        } catch (e: Exception) {
            Log.e("WrongBookViewModel", "保存错题失败", e)
            _errorMessage.postValue("错题保存失败：${e.message}")
            false
        } finally {
            _isLoading.postValue(false)
        }
    }

    fun updateQuestionDetails(entity: QuestionEntity) {
        viewModelScope.launch {
            repository.updateQuestion(entity)
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

    fun deleteQuestions(ids: List<Long>) {
        viewModelScope.launch {
            repository.deleteQuestionsByIds(ids)
            loadQuestions()
            loadReviews()
            loadTopicMastery()
        }
    }

    fun batchUpdateFavorite(ids: List<Long>, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.batchUpdateFavorite(ids, isFavorite)
            loadQuestions()
        }
    }

    fun batchUpdateSubject(ids: List<Long>, subject: String) {
        viewModelScope.launch {
            repository.batchUpdateSubject(ids, subject)
            loadQuestions()
            loadSubjects()
            loadTopicMastery()
        }
    }

    fun updateSubject(entity: SubjectEntity) {
        viewModelScope.launch {
            repository.updateSubject(entity)
            loadSubjects()
            loadQuestions()
            loadTopicMastery()
        }
    }

    fun deleteSubject(entity: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(entity)
            loadSubjects()
            loadQuestions()
            loadTopicMastery()
        }
    }

    fun ensureSubject(name: String) {
        viewModelScope.launch {
            repository.ensureSubject(name)
            loadSubjects()
        }
    }

    fun exportDatabase(exportFile: java.io.File) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            val success = repository.exportToJson(exportFile)
            _importExportStatus.postValue(success)
            _isLoading.postValue(false)
        }
    }

    fun importDatabase(importFile: java.io.File) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            val success = repository.importFromJson(importFile)
            _importExportStatus.postValue(success)
            _isLoading.postValue(false)
            if (success) {
                loadQuestions()
                loadSubjects()
                loadReviews()
                loadTopicMastery()
            }
        }
    }

    fun exportToCloud(context: Context, backupFile: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val localSuccess = repository.exportToJson(backupFile)
            if (!localSuccess) {
                _importExportStatus.postValue(false)
                _isLoading.postValue(false)
                return@launch
            }

            val success = try {
                val client = com.gamecenter.app.network.OkHttpClientProvider.getInstance(context).httpClient
                val jsonContent = backupFile.readText()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = okhttp3.RequestBody.create(
                    mediaType,
                    jsonContent
                )
                val request = okhttp3.Request.Builder()
                    .url("https://${com.gamecenter.app.BuildConfig.MODULE_HOST}/api/wrongbook/backup")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("WrongBookViewModel", "Cloud backup request failed: ${e.message}", e)
                _errorMessage.postValue("云端备份失败：${e.message}")
                false
            }

            _importExportStatus.postValue(success)
            _isLoading.postValue(false)
        }
    }

    fun importFromCloud(context: Context, backupFile: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            
            val cloudData = try {
                val client = com.gamecenter.app.network.OkHttpClientProvider.getInstance(context).httpClient
                val request = okhttp3.Request.Builder()
                    .url("https://${com.gamecenter.app.BuildConfig.MODULE_HOST}/api/wrongbook/restore")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("WrongBookViewModel", "Cloud restore request failed: ${e.message}", e)
                _errorMessage.postValue("云端恢复失败：${e.message}")
                null
            }

            val dataToImport = if (cloudData != null && cloudData.isNotEmpty()) {
                backupFile.writeText(cloudData)
                backupFile
            } else {
                if (backupFile.exists()) backupFile else null
            }

            if (dataToImport == null) {
                _importExportStatus.postValue(false)
                _isLoading.postValue(false)
                return@launch
            }

            val success = repository.importFromJson(dataToImport)
            _importExportStatus.postValue(success)
            _isLoading.postValue(false)
            if (success) {
                withContext(Dispatchers.Main) {
                    loadQuestions()
                    loadSubjects()
                    loadReviews()
                    loadTopicMastery()
                }
            }
        }
    }

    fun clearImportExportStatus() {
        _importExportStatus.value = null
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

    /**
     * 跳过复习项：持久化 status="skipped"，避免重新进入复习页时再次出现。
     * 跳过不会提升 mastery（与 completeReview 区分）。
     */
    fun skipReview(plan: ReviewPlanEntity) {
        viewModelScope.launch {
            repository.skipReview(plan)
            loadReviews()
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
