package com.gamecenter.app.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gamecenter.app.modules.store.DownloadSourceSelector
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 游戏库主页状态层（计划 §6.5）：
 * - 筛选状态经 SavedStateHandle 跨 Activity 重建恢复；
 * - 真源快照在后台线程获取，加载代次防止旧回调写入新页面；
 * - 不持有 Activity/View/短生命周期 Context。
 */
class GameHomeViewModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_CATEGORY = "category"
        private const val KEY_FAVORITES_ONLY = "favorites_only"
        private const val KEY_RECENT_EXPANDED = "recent_expanded"
    }

    private val repository = GameHomeRepository(application)
    private val executor = Executors.newSingleThreadExecutor()

    private val _filters = MutableLiveData(
        GameHomeFilters(
            query = state[KEY_QUERY] ?: "",
            categoryKey = state[KEY_CATEGORY],
            favoritesOnly = state[KEY_FAVORITES_ONLY] ?: false,
            recentExpanded = state[KEY_RECENT_EXPANDED] ?: false,
        )
    )
    val filters: LiveData<GameHomeFilters> get() = _filters

    private val _uiState = MutableLiveData<GameHomeUiState>()
    val uiState: LiveData<GameHomeUiState> get() = _uiState

    /** 加载代次：旧异步结果不得覆盖新一代状态。 */
    private var generation = 0

    init {
        refresh()
    }

    fun setQuery(query: String) {
        state[KEY_QUERY] = query
        _filters.value = _filters.value?.copy(query = query)
        refresh()
    }

    fun setCategory(categoryKey: String?) {
        state[KEY_CATEGORY] = categoryKey
        _filters.value = _filters.value?.copy(categoryKey = categoryKey)
        refresh()
    }

    fun setFavoritesOnly(enabled: Boolean) {
        state[KEY_FAVORITES_ONLY] = enabled
        _filters.value = _filters.value?.copy(favoritesOnly = enabled)
        refresh()
    }

    fun toggleRecentExpanded() {
        val next = !(_filters.value?.recentExpanded ?: false)
        state[KEY_RECENT_EXPANDED] = next
        _filters.value = _filters.value?.copy(recentExpanded = next)
        refresh()
    }

    /** 显式刷新（onResume / 模块安装后 / 返回游戏后）。 */
    fun refresh() {
        val filtersNow = _filters.value ?: GameHomeFilters()
        val gen = ++generation
        executor.execute {
            val snapshot = repository.snapshot()
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (gen != generation) return@launch // 代次失效
                val strings = homeStrings()
                val health = healthReminderText((snapshot.todayPlayTimeMs / 60_000L).toInt())
                _uiState.value = GameHomeStateBuilder.build(
                    allEntries = snapshot.entries,
                    recentIds = snapshot.recentIds,
                    categories = snapshot.categories,
                    lastPlayedTextById = snapshot.lastPlayedTextById,
                    favoriteIds = snapshot.favoriteIds,
                    filters = filtersNow,
                    strings = strings,
                    healthReminderText = health,
                )
            }
        }
    }

    /** 首选分发镜像（供继续/最近启动前的下载级联复用，保持与 W2 一致）。 */
    fun preferredMirrorBase(): String? =
        DownloadSourceSelector.preferredMirrorBases(getApplication()).firstOrNull()

    private fun homeStrings(): GameHomeStrings {
        val res = getApplication<Application>().resources
        return GameHomeStrings(
            continueTitle = res.getString(com.gamecenter.app.R.string.game_library_continue),
            recentTitle = res.getString(com.gamecenter.app.R.string.game_library_recent),
            allGamesTitle = res.getString(com.gamecenter.app.R.string.game_library_all_games),
            emptyLibrary = res.getString(com.gamecenter.app.R.string.game_library_empty),
            emptyLibraryAction = res.getString(com.gamecenter.app.R.string.game_library_empty_browse),
            emptyFavorites = res.getString(com.gamecenter.app.R.string.game_library_empty),
            emptyFavoritesAction = res.getString(com.gamecenter.app.R.string.game_library_all_games),
            noSearchResults = res.getString(com.gamecenter.app.R.string.game_library_no_search_results),
            clearSearch = res.getString(com.gamecenter.app.R.string.game_library_clear_search),
            viewAll = res.getString(com.gamecenter.app.R.string.game_library_view_all),
            collapse = res.getString(com.gamecenter.app.R.string.game_library_collapse),
            allFilter = res.getString(com.gamecenter.app.R.string.game_library_all_filter),
        )
    }

    /** 健康提醒（Batch 11-3 语义）：今日时长达 MILD/SEVERE 档位时给出低强调提示。 */
    private fun healthReminderText(todayMinutes: Int): String? {
        val level = com.gamecenter.app.ui.PlaytimeReminderHelper.Level.entries
            .firstOrNull { it.minutesThreshold in 1..todayMinutes && todayMinutes >= it.minutesThreshold }
            ?: return null
        val res = getApplication<Application>().resources
        val title = res.getString(com.gamecenter.app.R.string.playtime_reminder_title)
        val today = res.getString(
            com.gamecenter.app.R.string.playtime_reminder_today_format,
            "$todayMinutes"
        )
        return "$title · $today"
    }
}
