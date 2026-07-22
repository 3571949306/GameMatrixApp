package com.gamecenter.app.modules.bridge

import com.gamecenter.app.BuildConfig
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.bridge.generated.NativeCatalog
import com.gamecenter.app.modules.bridge.generated.NativeDownloadProgress
import com.gamecenter.app.modules.bridge.generated.NativeModule
import com.gamecenter.app.modules.bridge.generated.NativeModuleError
import com.gamecenter.app.modules.bridge.generated.NativeModuleEvent
import com.gamecenter.app.modules.bridge.generated.NativeOperationResult
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.CatalogV2
import com.gamecenter.app.modules.core.ModuleCoreError
import com.gamecenter.app.modules.core.ModuleCoreEvent
import com.gamecenter.app.modules.core.ModuleCoreFacade
import com.gamecenter.app.modules.core.ModuleOperationResult
import com.gamecenter.app.modules.core.ModuleProgress
import com.gamecenter.app.modules.core.ModuleState

class ModuleBridgeMapper(
    private val facade: ModuleCoreFacade,
    private val context: android.content.Context
) {
    fun catalog(value: CatalogV2) = NativeCatalog(
        schemaVersion = value.schemaVersion.toLong(),
        catalogVersion = value.catalogVersion.toLong(),
        generatedAt = value.generatedAt,
        source = value.source,
        offline = value.offline,
        modules = value.modules.map(::module)
    )

    fun module(value: CatalogModule): NativeModule {
        val installedVersion = ModuleManager.getInstalledVersionCode(context, value.id)
        val state = facade.state(value)
        return NativeModule(
            id = value.id,
            name = value.name,
            shortDescription = value.shortDescription,
            description = value.description,
            versionName = value.versionName,
            versionCode = value.versionCode.toLong(),
            installedVersionCode = installedVersion.toLong(),
            runtime = value.runtimeType.wireValue,
            deliveryType = value.deliveryType.wireValue,
            state = state.wireValue,
            route = value.route,
            entryClass = value.entryClass,
            entry = value.entry,
            serviceType = value.serviceType,
            launcherId = value.launcherId,
            iconUrl = value.legacyManifest?.iconUrl.orEmpty(),
            category = value.category,
            fileSize = value.packageInfo.fileSize,
            builtIn = value.deliveryType.wireValue == "builtin",
            required = value.required,
            featured = value.featured,
            enabled = ModuleManager.isModuleEnabled(context, value.id),
            updateAvailable = state == ModuleState.UPDATE_AVAILABLE,
            compatible = value.isCompatibleWithHost(BuildConfig.VERSION_CODE),
            rollbackAvailable = ModuleManager.hasRollback(context, value.id),
            minHostVersionCode = value.minHostVersionCode.toLong(),
            maxHostVersionCode = value.maxHostVersionCode.toLong(),
            permissions = value.permissions.map { it.id },
            permissionsDescription = value.permissions.map { it.description },
            dependencies = value.dependencies,
            tags = value.tags,
            screenshots = value.screenshots,
            changelog = value.changelog
        )
    }

    fun operation(result: ModuleOperationResult, moduleId: String): NativeOperationResult =
        NativeOperationResult(
            success = result.success,
            module = facade.module(moduleId)?.let(::module),
            error = result.error?.let(::error)
        )

    fun progress(value: ModuleProgress) = NativeDownloadProgress(
        moduleId = value.moduleId,
        downloadedBytes = value.downloadedBytes,
        totalBytes = value.totalBytes,
        speedKbps = value.speedKbps,
        percent = value.percent.toLong(),
        state = value.state.wireValue
    )

    fun event(value: ModuleCoreEvent) = NativeModuleEvent(
        eventType = value.eventType,
        moduleId = value.moduleId,
        runtime = value.runtimeType.wireValue,
        state = value.state.wireValue,
        timestampMillis = value.timestampMillis,
        progress = value.progress?.let(::progress),
        error = value.error?.let(::error)
    )

    private fun error(value: ModuleCoreError) = NativeModuleError(
        errorCode = value.errorCode,
        message = value.message,
        moduleId = value.moduleId,
        runtime = value.runtimeType.wireValue,
        recoverable = value.recoverable,
        suggestedAction = value.suggestedAction,
        technicalDetails = value.technicalDetails
    )
}
