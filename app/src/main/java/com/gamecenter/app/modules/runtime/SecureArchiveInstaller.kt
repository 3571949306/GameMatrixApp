package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.R
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Installs non-code ZIP packages into isolated, transactional runtime dirs. */
object SecureArchiveInstaller {
    private const val MAX_ENTRY_COUNT = 2_048
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 250L * 1024L * 1024L
    private const val MAX_COMPRESSION_RATIO = 200L

    fun install(context: Context, module: CatalogModule): RuntimeResult {
        val manifest = module.legacyManifest
            ?: return RuntimeResult(false, "manifest_missing", context.getString(R.string.module_error_package_mapping_missing))
        val archive = TransactionInstaller.getCurrentFile(context, manifest)
        if (!archive.isFile) return RuntimeResult(false, "archive_missing", context.getString(R.string.module_error_archive_missing))

        val root = runtimeRoot(context, module.id)
        val staging = File(root, "staging")
        val current = File(root, "current")
        val lastGood = File(root, "last_good")
        staging.deleteRecursively()
        staging.mkdirs()

        val extraction = extractSafely(archive, staging)
        if (!extraction.success) {
            rejectPackage(context, manifest, archive)
            return extraction
        }
        if (module.runtimeType == RuntimeType.WEB &&
            !File(staging, module.entry).isFile
        ) {
            staging.deleteRecursively()
            rejectPackage(context, manifest, archive)
            return RuntimeResult(false, "entry_missing", context.getString(R.string.module_error_web_entry_missing, module.entry))
        }
        validateContentManifest(module, staging)?.let { invalid ->
            staging.deleteRecursively()
            rejectPackage(context, manifest, archive)
            return invalid
        }
        lastGood.deleteRecursively()
        if (current.exists() && !current.renameTo(lastGood)) {
            staging.deleteRecursively()
            rejectPackage(context, manifest, archive)
            return RuntimeResult(false, "backup_failed", context.getString(R.string.module_error_unable_preserve_runtime))
        }
        if (!staging.renameTo(current)) {
            if (lastGood.exists()) lastGood.renameTo(current)
            rejectPackage(context, manifest, archive)
            return RuntimeResult(false, "atomic_switch_failed", context.getString(R.string.module_error_unable_activate_staged))
        }
        current.walkTopDown().forEach { it.setReadOnly() }
        return RuntimeResult(true)
    }

    fun rollback(context: Context, moduleId: String): RuntimeResult {
        val root = runtimeRoot(context, moduleId)
        val current = File(root, "current")
        val lastGood = File(root, "last_good")
        if (!lastGood.isDirectory) return RuntimeResult(false, "rollback_unavailable", context.getString(R.string.module_error_no_last_good_package))
        val quarantine = File(root, "quarantine/${System.currentTimeMillis()}")
        quarantine.parentFile?.mkdirs()
        if (current.exists() && !current.renameTo(quarantine)) {
            return RuntimeResult(false, "quarantine_failed", context.getString(R.string.module_error_unable_quarantine))
        }
        return if (lastGood.renameTo(current)) RuntimeResult(true)
        else RuntimeResult(false, "rollback_failed", context.getString(R.string.module_error_unable_restore_last_good))
    }

    fun uninstall(context: Context, moduleId: String): RuntimeResult {
        val root = runtimeRoot(context, moduleId)
        return if (!root.exists() || root.deleteRecursively()) RuntimeResult(true)
        else RuntimeResult(false, "uninstall_failed", context.getString(R.string.module_error_unable_remove_runtime_dir))
    }

    fun currentDirectory(context: Context, moduleId: String): File =
        File(runtimeRoot(context, moduleId), "current")

    private fun validateContentManifest(module: CatalogModule, staging: File): RuntimeResult? {
        val manifestName = when {
            module.runtimeType == RuntimeType.ASSET -> "asset-manifest.json"
            module.runtimeType == RuntimeType.UNITY && module.deliveryType == DeliveryType.CONTENT ->
                "unity-manifest.json"
            else -> return null
        }
        val errorCode = if (module.runtimeType == RuntimeType.ASSET) {
            "asset_manifest_invalid"
        } else {
            "unity_manifest_invalid"
        }
        return runCatching {
            val manifestFile = File(staging, manifestName)
            require(manifestFile.isFile) { "$manifestName is missing" }
            val json = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(json.optInt("schemaVersion") == 1) { "$manifestName schemaVersion must be 1" }
            require(json.optString("moduleId") == module.id) { "$manifestName moduleId does not match" }
            require(json.optInt("versionCode") == module.versionCode) { "$manifestName versionCode does not match" }
            if (module.runtimeType == RuntimeType.UNITY) {
                require(json.optString("launcherId") == module.launcherId) {
                    "$manifestName launcherId does not match"
                }
            }
            val files = json.optJSONArray("files")
                ?: error("$manifestName files is required")
            require(files.length() > 0) { "$manifestName files must not be empty" }
            val rootPath = staging.canonicalFile.path + File.separator
            for (index in 0 until files.length()) {
                val relative = files.getString(index).replace('\\', '/')
                require(relative.isNotBlank() && !relative.startsWith('/') && '\u0000' !in relative) {
                    "$manifestName contains an invalid file path"
                }
                val target = File(staging, relative).canonicalFile
                require(target.path.startsWith(rootPath) && target.isFile) {
                    "$manifestName references a missing or escaped file"
                }
            }
            null
        }.getOrElse { error ->
            RuntimeResult(false, errorCode, error.message ?: "$manifestName validation failed")
        }
    }

    private fun runtimeRoot(context: Context, moduleId: String): File {
        require(moduleId.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid module id" }
        return File(context.filesDir, "modules/runtime/$moduleId").apply { mkdirs() }
    }

    /**
     * A package can pass transport SHA/signature checks and still be unusable as a runtime
     * archive. Restore the previous verified package when available; otherwise remove the
     * rejected archive from current and preserve it in the transaction quarantine.
     */
    private fun rejectPackage(
        context: Context,
        manifest: com.gamecenter.app.modules.ModuleManifest,
        archive: File
    ) {
        if (TransactionInstaller.rollback(context, manifest)) return
        if (!archive.exists()) return

        val quarantineFile = TransactionInstaller.getQuarantineFile(context, manifest)
        quarantineFile.parentFile?.mkdirs()
        if (!archive.renameTo(quarantineFile)) {
            runCatching {
                archive.copyTo(quarantineFile, overwrite = true)
                archive.delete()
            }
        }
    }

    internal fun extractSafely(archive: File, destination: File): RuntimeResult {
        val destinationPath = destination.canonicalFile.path + File.separator
        var count = 0
        var total = 0L
        return runCatching {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count++
                    require(count <= MAX_ENTRY_COUNT) { "ZIP contains too many entries" }
                    val normalizedName = entry.name.replace('\\', '/')
                    require(normalizedName.isNotBlank() && '\u0000' !in normalizedName) { "Invalid ZIP entry name" }
                    require(!normalizedName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalizedName)) {
                        "Absolute ZIP entry path"
                    }
                    val target = File(destination, normalizedName).canonicalFile
                    require(target.path.startsWith(destinationPath)) { "ZIP path traversal detected" }
                    if (entry.isDirectory) {
                        require(target.mkdirs() || target.isDirectory) { "Unable to create ZIP directory" }
                    } else {
                        target.parentFile?.let { require(it.mkdirs() || it.isDirectory) }
                        var entryBytes = 0L
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read = zip.read(buffer)
                            while (read >= 0) {
                                if (read > 0) {
                                    entryBytes += read
                                    total += read
                                    require(entryBytes <= MAX_ENTRY_BYTES) { "ZIP entry exceeds size limit" }
                                    require(total <= MAX_TOTAL_BYTES) { "ZIP exceeds total size limit" }
                                    output.write(buffer, 0, read)
                                }
                                read = zip.read(buffer)
                            }
                        }
                        if (entry.compressedSize > 0) {
                            require(entryBytes <= entry.compressedSize * MAX_COMPRESSION_RATIO) {
                                "ZIP compression ratio exceeds limit"
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            RuntimeResult(true)
        }.getOrElse { error ->
            destination.deleteRecursively()
            RuntimeResult(false, "unsafe_archive", error.message ?: "Archive extraction failed")
        }
    }
}
