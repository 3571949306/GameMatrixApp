package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleManager
import java.io.File

/**
 * P5 Store-Owned 模块更新管理器。
 *
 * 职责：
 * 1. 以远程/缓存目录为权威来源，扫描可更新模块
 * 2. 按依赖顺序下载并安装更新
 * 3. 更新失败时触发事务回滚（依赖 TransactionInstaller）
 * 4. 记录更新结果供上层 UI 展示
 *
 * 说明：
 * - 更新由商店目录驱动，不是由模块自身发起
 * - 关键模块（isBaseFramework/required）失败时优先回滚
 * - 非关键模块失败时移入 quarantine，避免影响主流程
 */
object ModuleUpdateManager {

    private const val TAG = "ModuleUpdateManager"

    /**
     * 可更新模块信息。
     */
    data class UpdateCandidate(
        val moduleId: String,
        val installedVersion: Int,
        val availableVersion: Int,
        val manifest: ModuleManifest
    )

    /**
     * 批量更新结果。
     */
    data class BatchUpdateResult(
        val checked: Int,
        val updated: List<String>,
        val failed: List<String>,
        val skipped: List<String>
    )

    /**
     * 检查目录中所有模块是否有可用更新。
     *
     * @param context 上下文
     * @param manifests 权威目录提供的模块清单列表
     * @return 可更新模块列表（按 versionCode 升序）
     */
    fun checkForUpdates(
        context: Context,
        manifests: List<ModuleManifest>
    ): List<UpdateCandidate> {
        if (!BuildConfig.ENABLE_P5_STORE_OWNED_UPDATE) {
            Log.d(TAG, "P5 Store-Owned 更新已禁用")
            return emptyList()
        }

        val candidates = mutableListOf<UpdateCandidate>()
        for (manifest in manifests) {
            if (manifest.builtIn) continue
            if (manifest.downloadUrl.isEmpty() && manifest.fileName.isEmpty()) continue

            val installedVersion = ModuleManager.getInstalledVersionCode(context, manifest.id)
            if (installedVersion > 0 && manifest.versionCode > installedVersion) {
                candidates.add(
                    UpdateCandidate(
                        moduleId = manifest.id,
                        installedVersion = installedVersion,
                        availableVersion = manifest.versionCode,
                        manifest = manifest
                    )
                )
            }
        }
        return sortCandidatesByDependencyOrder(candidates)
    }

    /**
     * 按依赖顺序对可更新候选进行排序。
     *
     * 排序规则：
     * 1. 关键模块（isBaseFramework / required）排在普通模块之前
     * 2. 同组内按依赖拓扑排序：被依赖模块先于依赖模块
     * 3. 无依赖关系时保持原相对顺序（稳定排序）
     * 4. 循环依赖时记录警告并保留安全顺序，避免无限循环
     */
    private fun sortCandidatesByDependencyOrder(
        candidates: List<UpdateCandidate>
    ): List<UpdateCandidate> {
        val candidateMap = candidates.associateBy { it.moduleId }
        val critical = candidates.filter {
            it.manifest.isBaseFramework || it.manifest.required
        }
        val normal = candidates.filter {
            !it.manifest.isBaseFramework && !it.manifest.required
        }

        return topologicalSort(critical, candidateMap) + topologicalSort(normal, candidateMap)
    }

    /**
     * 对一组候选进行拓扑排序。
     *
     * 仅考虑该组内部以及组内模块依赖的模块。依赖不在当前组/候选列表中的忽略。
     */
    private fun topologicalSort(
        group: List<UpdateCandidate>,
        allCandidates: Map<String, UpdateCandidate>
    ): List<UpdateCandidate> {
        val groupIds = group.map { it.moduleId }.toSet()
        val inDegree = mutableMapOf<String, Int>()
        val dependents = mutableMapOf<String, MutableList<String>>()

        for (candidate in group) {
            inDegree[candidate.moduleId] = 0
        }

        for (candidate in group) {
            for (depId in candidate.manifest.depends) {
                // 只考虑同组内且也在整体候选列表中的依赖
                if (depId in groupIds) {
                    inDegree[candidate.moduleId] = (inDegree[candidate.moduleId] ?: 0) + 1
                    dependents.getOrPut(depId) { mutableListOf() }.add(candidate.moduleId)
                }
            }
        }

        val queue = ArrayDeque<String>()
        val order = mutableListOf<String>()

        // 保持原始顺序稳定性：先按 group 顺序加入入度为 0 的节点
        for (candidate in group) {
            if ((inDegree[candidate.moduleId] ?: 0) == 0) {
                queue.add(candidate.moduleId)
            }
        }

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            order.add(currentId)

            val dependentList = dependents[currentId] ?: emptyList()
            // 保持依赖声明顺序稳定
            for (dependentId in dependentList.sortedBy { depId -> group.indexOfFirst { it.moduleId == depId } }) {
                val newDegree = (inDegree[dependentId] ?: 0) - 1
                inDegree[dependentId] = newDegree
                if (newDegree == 0) {
                    queue.add(dependentId)
                }
            }
        }

        // 循环依赖检测：存在未处理节点
        val unresolved = groupIds - order.toSet()
        if (unresolved.isNotEmpty()) {
            Log.w(TAG, "检测到模块循环依赖或排序异常，保留原顺序: ${unresolved.joinToString()}")
            val unresolvedInOriginalOrder = group.filter { it.moduleId in unresolved }
            return order.mapNotNull { allCandidates[it] } + unresolvedInOriginalOrder
        }

        return order.mapNotNull { allCandidates[it] }
    }

    /**
     * 执行批量更新。
     *
     * @param context 上下文
     * @param candidates 可更新模块列表
     * @param callback 进度/结果回调
     */
    fun performBatchUpdate(
        context: Context,
        candidates: List<UpdateCandidate>,
        callback: UpdateCallback? = null
    ): BatchUpdateResult {
        if (!BuildConfig.ENABLE_P5_STORE_OWNED_UPDATE) {
            Log.d(TAG, "P5 Store-Owned 更新已禁用")
            return BatchUpdateResult(0, emptyList(), emptyList(), emptyList())
        }

        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (candidate in candidates) {
            val moduleId = candidate.moduleId
            callback?.onCheck(moduleId)

            // 重复依赖：如果该模块已被前置模块依赖安装，则跳过
            if (ModuleManager.getInstalledVersionCode(context, moduleId) >= candidate.availableVersion) {
                skipped.add(moduleId)
                continue
            }

            val success = updateSingleModule(context, candidate, callback)
            if (success) {
                updated.add(moduleId)
            } else {
                failed.add(moduleId)
                // 关键模块失败时中断批量更新，避免级联问题
                if (candidate.manifest.isBaseFramework || candidate.manifest.required) {
                    Log.e(TAG, "关键模块 $moduleId 更新失败，中断批量更新")
                    callback?.onCancelled(moduleId)
                    break
                }
            }
        }

        callback?.onComplete(BatchUpdateResult(candidates.size, updated, failed, skipped))
        return BatchUpdateResult(candidates.size, updated, failed, skipped)
    }

    /**
     * 更新单个模块（同步阻塞，应在后台线程调用）。
     */
    private fun updateSingleModule(
        context: Context,
        candidate: UpdateCandidate,
        callback: UpdateCallback?
    ): Boolean {
        val moduleId = candidate.moduleId
        val manifest = candidate.manifest

        Log.d(TAG, "开始更新模块: $moduleId ${candidate.installedVersion} -> ${candidate.availableVersion}")
        callback?.onStart(moduleId, candidate.installedVersion, candidate.availableVersion)

        val latch = java.util.concurrent.CountDownLatch(1)
        var result = false
        var errorMessage: String? = null

        ModuleManager.downloadModule(
            context,
            moduleId,
            object : ModuleDownloader.Callback {
                override fun onProgress(id: String, downloaded: Long, total: Long, speed: Long) {
                    callback?.onProgress(id, downloaded, total, speed)
                }

                override fun onComplete(id: String, file: File) {
                    Log.d(TAG, "模块更新下载完成: $id -> ${file.absolutePath}")
                    result = true
                    latch.countDown()
                }

                override fun onError(id: String, message: String) {
                    Log.e(TAG, "模块更新失败: $id - $message")
                    errorMessage = message
                    latch.countDown()
                }

                override fun onError(id: String, errorCode: Int, message: String) {
                    onError(id, message)
                }

                override fun onSourceSwitch(id: String, sourceIndex: Int, url: String) {
                    Log.d(TAG, "模块更新源切换: $id -> $url")
                }
            }
        )

        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            errorMessage = "等待更新中断"
        }

        if (!result) {
            // 更新失败：尝试回滚到 last_good
            if (manifest.rollbackAllowed && BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
                Log.w(TAG, "尝试回滚模块: $moduleId")
                val rollbackOk = TransactionInstaller.rollback(context, manifest)
                callback?.onRollback(moduleId, rollbackOk)
            }
            callback?.onFailed(moduleId, errorMessage ?: "未知错误")
            return false
        }

        callback?.onSuccess(moduleId)
        return true
    }

    /**
     * 更新进度/结果回调接口。
     */
    interface UpdateCallback {
        fun onCheck(moduleId: String) {}
        fun onStart(moduleId: String, fromVersion: Int, toVersion: Int) {}
        fun onProgress(moduleId: String, downloaded: Long, total: Long, speed: Long) {}
        fun onSuccess(moduleId: String) {}
        fun onFailed(moduleId: String, reason: String) {}
        fun onRollback(moduleId: String, success: Boolean) {}
        fun onCancelled(moduleId: String) {}
        fun onComplete(result: BatchUpdateResult) {}
    }
}
