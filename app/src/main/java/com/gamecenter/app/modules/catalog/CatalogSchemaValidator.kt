package com.gamecenter.app.modules.catalog

import java.net.URI

object CatalogSchemaValidator {

    fun validate(catalog: CatalogV2): CatalogValidationResult {
        val issues = mutableListOf<CatalogValidationIssue>()
        if (catalog.schemaVersion != 2) {
            issues += issue("schemaVersion", "unsupported_schema", "schemaVersion must be 2")
        }
        val seen = mutableSetOf<String>()
        catalog.modules.forEachIndexed { index, module ->
            val path = "modules[$index]"
            if (module.id.isBlank()) issues += issue("$path.id", "required", "id is required")
            if (!seen.add(module.id)) issues += issue("$path.id", "duplicate", "id must be unique")
            if (module.name.isBlank()) issues += issue("$path.name", "required", "name is required")
            if (module.versionCode <= 0) issues += issue("$path.versionCode", "invalid", "versionCode must be positive")
            if (module.maxHostVersionCode > 0 && module.maxHostVersionCode < module.minHostVersionCode) {
                issues += issue(path, "host_range", "maxHostVersionCode is below minHostVersionCode")
            }
            validateRuntime(module, path, issues)
            validatePackage(module, path, issues)
        }
        return CatalogValidationResult(issues)
    }

    private fun validateRuntime(
        module: CatalogModule,
        path: String,
        issues: MutableList<CatalogValidationIssue>
    ) {
        val allowedDeliveries = when (module.runtimeType) {
            RuntimeType.FLUTTER -> setOf(DeliveryType.BUILTIN)
            RuntimeType.WEB -> setOf(DeliveryType.BUILTIN, DeliveryType.ZIP)
            RuntimeType.ASSET -> setOf(DeliveryType.BUILTIN, DeliveryType.ZIP)
            RuntimeType.ANDROID -> setOf(DeliveryType.BUILTIN, DeliveryType.APK)
            RuntimeType.NATIVE_SERVICE -> setOf(DeliveryType.BUILTIN, DeliveryType.APK)
            RuntimeType.UNITY -> setOf(DeliveryType.BUILTIN, DeliveryType.APK, DeliveryType.CONTENT)
        }
        if (module.deliveryType !in allowedDeliveries) {
            issues += issue(
                "$path.deliveryType",
                "invalid_delivery",
                "${module.runtimeType.wireValue} does not support ${module.deliveryType.wireValue} delivery"
            )
        }
        when (module.runtimeType) {
            RuntimeType.FLUTTER -> if (!module.route.startsWith('/')) {
                issues += issue("$path.route", "invalid_route", "Flutter modules require an absolute route")
            }
            RuntimeType.WEB -> if (module.entry.isBlank()) {
                issues += issue("$path.entry", "required", "Web modules require an entry file")
            }
            RuntimeType.ANDROID -> if (module.deliveryType != DeliveryType.BUILTIN && module.entryClass.isBlank()) {
                issues += issue("$path.entryClass", "required", "Downloaded Android modules require entryClass")
            }
            RuntimeType.NATIVE_SERVICE -> {
                if (module.serviceType.isBlank()) {
                    issues += issue("$path.serviceType", "required", "Native services require serviceType")
                }
                if (module.deliveryType != DeliveryType.BUILTIN && module.entryClass.isBlank()) {
                    issues += issue(
                        "$path.entryClass",
                        "required",
                        "Downloaded native services require entryClass"
                    )
                }
            }
            RuntimeType.UNITY -> if (module.launcherId.isBlank()) {
                issues += issue("$path.launcherId", "required", "Unity modules require launcherId")
            }
            RuntimeType.ASSET -> Unit
        }
    }

    private fun validatePackage(
        module: CatalogModule,
        path: String,
        issues: MutableList<CatalogValidationIssue>
    ) {
        if (module.deliveryType == DeliveryType.BUILTIN) return
        val packageInfo = module.packageInfo
        if (packageInfo.fileName.isBlank()) issues += issue("$path.package.fileName", "required", "package fileName is required")
        if (packageInfo.getUrls().isEmpty()) {
            issues += issue("$path.package.downloadUrl", "required", "at least one package URL is required")
        }
        if (packageInfo.sha256.length != 64 || packageInfo.sha256.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            issues += issue("$path.package.sha256", "invalid_sha256", "remote packages require a 64-character SHA-256")
        }
        packageInfo.getUrls().forEach { url ->
            val scheme = runCatching { URI(url).scheme }.getOrNull()
            if (!scheme.equals("https", ignoreCase = true)) {
                issues += issue("$path.package.downloadUrl", "https_required", "package URLs must use HTTPS")
            }
        }
    }

    private fun CatalogPackage.getUrls(): List<String> = listOf(downloadUrl, fallbackUrl, githubUrl).filter { it.isNotBlank() }

    private fun issue(path: String, code: String, message: String) = CatalogValidationIssue(path, code, message)
}
