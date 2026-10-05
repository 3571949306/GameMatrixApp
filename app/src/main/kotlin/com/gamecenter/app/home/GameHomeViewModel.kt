package com.gamecenter.app.home

import android.app.Application
import android.content.res.Resources
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
 * - 留存卡片（批次 2a/2b）取数在 Repository/后台，文案格式化经 [GameHomeCardTexts]，
 *   纯值交给 [GameHomeStateBuilder] 编排；
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
        private const val MILLIS_PER_MINUTE = 60_000L
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
                _uiState.value = buildUiState(snapshot, filtersNow)
            }
        }
    }

    /** 首选分发镜像（供继续/最近启动前的下载级联复用，保持与 W2 一致）。 */
    fun preferredMirrorBase(): String? =
        DownloadSourceSelector.preferredMirrorBases(getApplication()).firstOrNull()

    // ===== 内部：纯值装配 =====

    /** 快照 + 筛选 → 页面状态；快照已在后台取好，此处只做文案格式化与行编排。 */
    private fun buildUiState(
        snapshot: GameHomeRepository.Snapshot,
        filters: GameHomeFilters,
    ): GameHomeUiState {
        val res = getApplication<Application>().resources
        val lastPlayedTextById = snapshot.lastPlayedAtById.mapValues { (_, at) ->
            GameHomeCardTexts.relativeTime(res, at)
        }
        val cards = GameHomeRetentionCards(
            playtimeReminder = GameHomeCardTexts.playtimeReminder(
                res,
                snapshot.reminderLevel,
                (snapshot.todayPlayTimeMs / MILLIS_PER_MINUTE).toInt()
            ),
            quickStats = GameHomeCardTexts.quickStats(res, snapshot),
            dailyChallenge = snapshot.todayChallenge?.let { challenge ->
                GameHomeCardTexts.dailyChallenge(
                    res,
                    challenge,
                    snapshot.entries.firstOrNull { it.id == challenge.gameId },
                )
            },
            streakSummary = GameHomeCardTexts.streakSummary(
                res, snapshot.streakDays, snapshot.bestStreakDays
            ),
            recentAchievement = snapshot.recentAchievement?.let { recent ->
                GameHomeItem.RecentAchievementBanner(
                    name = com.gamecenter.app.ui.RecentAchievementHelper
                        .resolveTitle(getApplication(), recent.id),
                    desc = com.gamecenter.app.ui.RecentAchievementHelper
                        .resolveDescription(getApplication(), recent.id),
                )
            },
            todayFeatured = snapshot.todayFeaturedEntry?.let { featured ->
                GameHomeItem.TodayFeaturedCard(
                    entry = featured,
                    desc = featured.desc.ifEmpty { featured.category },
                )
            },
        )
        return GameHomeStateBuilder.build(
            allEntries = snapshot.entries,
            recentIds = snapshot.recentIds,
            lastPlayedTextById = lastPlayedTextById,
            favoriteIds = snapshot.favoriteIds,
            categories = snapshot.categories,
            filters = filters,
            strings = homeStrings(res),
            retention = cards,
        )
    }

    private fun homeStrings(res: Resources): GameHomeStrings {
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
            positioningTitle = res.getString(com.gamecenter.app.R.string.home_positioning_sections),
            resumeTimeFormat = res.getString(com.gamecenter.app.R.string.home_resume_game_time_format),
        )
    }
}
