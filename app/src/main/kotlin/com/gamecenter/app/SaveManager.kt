package com.gamecenter.app

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(gameId: String, slotKey: String, jsonState: String?) {
        prefs.edit().putString(buildSaveKey(gameId, slotKey), jsonState).apply()
    }

    fun load(gameId: String, slotKey: String): String? {
        return prefs.getString(buildSaveKey(gameId, slotKey), null)
    }

    fun hasSave(gameId: String, slotKey: String): Boolean {
        return prefs.contains(buildSaveKey(gameId, slotKey))
    }

    fun deleteSave(gameId: String, slotKey: String) {
        prefs.edit().remove(buildSaveKey(gameId, slotKey)).apply()
    }

    fun saveProgress(gameId: String, jsonProgress: String?) {
        prefs.edit().putString(buildProgressKey(gameId), jsonProgress).apply()
    }

    fun loadProgress(gameId: String): String? {
        return prefs.getString(buildProgressKey(gameId), null)
    }

    fun hasProgress(gameId: String): Boolean {
        return prefs.contains(buildProgressKey(gameId))
    }

    fun deleteProgress(gameId: String) {
        prefs.edit().remove(buildProgressKey(gameId)).apply()
    }

    private fun buildSaveKey(gameId: String, slotKey: String): String {
        return "${KEY_PREFIX_SAVE}${gameId}_$slotKey"
    }

    private fun buildProgressKey(gameId: String): String {
        return "${KEY_PREFIX_PROGRESS}$gameId"
    }

    companion object {
        private const val PREFS_NAME = "GameMatrix_saves"
        private const val KEY_PREFIX_SAVE = "save_"
        private const val KEY_PREFIX_PROGRESS = "progress_"

        @Volatile
        private var instance: SaveManager? = null

        @JvmStatic
        @Deprecated("推荐通过 Hilt 依赖注入获取实例，避免手动管理单例")
        fun getInstance(context: Context): SaveManager {
            return instance ?: synchronized(SaveManager::class.java) {
                instance ?: SaveManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        instance = this
    }
}
