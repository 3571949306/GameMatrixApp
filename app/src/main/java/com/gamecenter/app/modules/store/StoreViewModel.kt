package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import com.gamecenter.app.modules.store.model.StoreCatalog
import com.gamecenter.app.modules.store.model.StoreUiConfig

/**
 * 商店 ViewModel（P2.6）。
 *
 * 职责：
 * - 持有 [StoreCatalogRepository] 和 [StoreUiConfigRepository] 引用
 * - 提供目录和 UI 配置的获取与刷新
 * - 暴露当前 [StorePageState]（只读视图，状态更新由 Activity 负责）
 * - 注册 Repository 观察者，自动同步 catalog/uiConfig 到 pageState
 *
 * 本轮设计原则：
 * - 轻量级，不继承 AndroidX ViewModel（避免引入 Lifecycle 依赖）
 * - 不强行接管 Activity 的全部状态（避免破坏现有功能）
 * - 后续 P3/P4 可逐步把状态管理迁移到 ViewModel
 *
 * 使用方式：
 *   val viewModel = StoreViewModel(applicationContext)
 *   viewModel.addObserver { state -> /* 更新 UI */ }
 *   viewModel.refresh() // 触发远程刷新
 */
class StoreViewModel(private val appContext: Context) {

    private val catalogRepository: DefaultStoreCatalogRepository =
        DefaultStoreCatalogRepository.getInstance(appContext)
    private val uiConfigRepository: DefaultStoreUiConfigRepository =
        DefaultStoreUiConfigRepository.getInstance(appContext)

    /** 当前页面状态（AtomicReference 保证线程安全） */
    private val stateRef = java.util.concurrent.atomic.AtomicReference(StorePageState())

    /** 状态观察者列表 */
    private val observers = java.util.concurrent.CopyOnWriteArrayList<(StorePageState) -> Unit>()

    /** 目录观察者：catalog 更新时同步到 state */
    private val catalogObserver = { catalog: StoreCatalog ->
        val current = stateRef.get()
        val newState = current.copy(catalog = catalog)
        stateRef.set(newState)
        notifyObservers(newState)
    }

    /** UI 配置观察者：uiConfig 更新时同步到 state */
    private val uiConfigObserver = { config: StoreUiConfig ->
        val current = stateRef.get()
        val newState = current.copy(uiConfig = config)
        stateRef.set(newState)
        notifyObservers(newState)
    }

    init {
        catalogRepository.addObserver(catalogObserver)
        uiConfigRepository.addObserver(uiConfigObserver)
        // 立即应用一次缓存，避免首次进入商店时 state 为空
        catalogRepository.getCachedCatalog()?.let { catalogObserver(it) }
        uiConfigRepository.getCachedConfig()?.let { uiConfigObserver(it) }
    }

    /** 获取当前状态（线程安全） */
    fun getState(): StorePageState = stateRef.get()

    /**
     * 由 Activity 调用更新状态（如 modules / currentCategory / searchKeyword 等字段）。
     * catalog 和 uiConfig 字段由内部观察者自动同步，Activity 不应直接修改这两个字段。
     */
    fun updateState(transform: (StorePageState) -> StorePageState) {
        val newState = transform(stateRef.get())
        stateRef.set(newState)
        notifyObservers(newState)
    }

    /** 注册状态观察者 */
    fun addObserver(observer: (StorePageState) -> Unit) {
        observers.addIfAbsent(observer)
    }

    /** 注销状态观察者 */
    fun removeObserver(observer: (StorePageState) -> Unit) {
        observers.remove(observer)
    }

    /**
     * 触发远程刷新：目录 + UI 配置。
     * callback 在主线程回调，success 表示至少目录刷新成功。
     */
    fun refresh(callback: ((Boolean) -> Unit)? = null) {
        var catalogDone = false
        var uiConfigDone = false
        var anySuccess = false

        catalogRepository.refresh { result ->
            catalogDone = true
            if (result.isSuccess) anySuccess = true
            if (catalogDone && uiConfigDone) callback?.invoke(anySuccess)
        }
        uiConfigRepository.refresh { result ->
            uiConfigDone = true
            if (result.isSuccess) anySuccess = true
            if (catalogDone && uiConfigDone) callback?.invoke(anySuccess)
        }
    }

    /** 仅刷新目录（不刷新 UI 配置） */
    fun refreshCatalog(callback: ((Boolean) -> Unit)? = null) {
        catalogRepository.refresh { result -> callback?.invoke(result.isSuccess) }
    }

    /** 仅刷新 UI 配置（不刷新目录） */
    fun refreshUiConfig(callback: ((Boolean) -> Unit)? = null) {
        uiConfigRepository.refresh { result -> callback?.invoke(result.isSuccess) }
    }

    /** 销毁时注销观察者，避免内存泄漏 */
    fun destroy() {
        catalogRepository.removeObserver(catalogObserver)
        uiConfigRepository.removeObserver(uiConfigObserver)
        observers.clear()
    }

    private fun notifyObservers(state: StorePageState) {
        for (observer in observers) {
            try {
                observer(state)
            } catch (e: Exception) {
                Log.w("StoreViewModel", "观察者回调失败: ${e.message}")
            }
        }
    }
}
