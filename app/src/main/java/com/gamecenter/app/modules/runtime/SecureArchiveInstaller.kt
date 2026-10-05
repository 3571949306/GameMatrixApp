package com.gamecenter.app.modules.runtime

import android.content.Context
import android.util.Log
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Installs non-code ZIP packages into isolated, transactional runtime dirs. */
object SecureArchiveInstaller {
    private const val TAG = "SecureArchiveInstaller"
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
        val previousManifest = ModuleManager.getVerifiedLastGoodManifest(context, module.id)
        if (previousManifest != null) {
            // Reinstall/repair can leave current at the new version already. The verified
            // outer backup is the authority for rollback, never the mutable runtime tree.
            val preparedLastGood = File(root, "last-good-staging")
            makeWritableRecursively(preparedLastGood)
            if ((preparedLastGood.exists() && !preparedLastGood.deleteRecursively()) ||
                !preparedLastGood.mkdirs()
            ) {
                staging.deleteRecursively()
                return RuntimeResult(false, "backup_failed", context.getString(R.string.module_error_unable_preserve_runtime))
            }
            val backup = extractSafely(
                TransactionInstaller.getLastGoodFile(context, previousManifest), preparedLastGood
            )
            if (!backup.success) {
                staging.deleteRecursively()
                // The old ZIP was authenticated by its own snapshot. Do not validate it
                // against this new version's entry/launcher, or quarantine the valid new ZIP.
                return RuntimeResult(false, "backup_archive_invalid", backup.message)
            }
            // Both generations must match their authenticated archives. Comparing current
            // alone could keep a backup from the wrong update, even with identical payloads.
            if (runtimeTreesMatch(current, staging) && runtimeTreesMatch(lastGood, preparedLastGood)) {
                staging.deleteRecursively()
                preparedLastGood.deleteRecursively()
                return RuntimeResult(true)
            }
            if (!activatePreparedRuntime(root, staging, preparedLastGood)) {
                makeWritableRecursively(staging)
                staging.deleteRecursively()
                makeWritableRecursively(preparedLastGood)
                preparedLastGood.deleteRecursively()
                return RuntimeResult(false, "atomic_switch_failed", context.getString(R.string.module_error_unable_activate_staged))
            }
            return RuntimeResult(true)
        }
        val previousLastGood = if (lastGood.exists()) {
            val temporary = File(root, ".last-good-previous-${System.nanoTime()}")
            makeWritableRecursively(lastGood)
            if (!lastGood.renameTo(temporary)) {
                staging.deleteRecursively()
                rejectPackage(context, manifest, archive)
                return RuntimeResult(false, "backup_failed", context.getString(R.string.module_error_unable_preserve_runtime))
            }
            temporary
        } else {
            null
        }
        if (current.exists() && !current.renameTo(lastGood)) {
            if (previousLastGood?.isDirectory == true) previousLastGood.renameTo(lastGood)
            staging.deleteRecursively()
            rejectPackage(context, manifest, archive)
            return RuntimeResult(false, "backup_failed", context.getString(R.string.module_error_unable_preserve_runtime))
        }
        if (!staging.renameTo(current)) {
            makeWritableRecursively(lastGood)
            if (lastGood.isDirectory) lastGood.renameTo(current)
            if (previousLastGood?.isDirectory == true) previousLastGood.renameTo(lastGood)
            rejectPackage(context, manifest, archive)
            return RuntimeResult(false, "atomic_switch_failed", context.getString(R.string.module_error_unable_activate_staged))
        }
        current.walkTopDown().forEach { it.setReadOnly() }
        previousLastGood?.deleteRecursively()
        return RuntimeResult(true)
    }

    /** Exact tree equality; size checks precede streaming SHA checks, and links are refused. */
    internal fun runtimeTreesMatch(current: File, prepared: File): Boolean = runCatching {
        val expected = readRuntimeTree(prepared)
        val actual = readRuntimeTree(current, expected.size)
        if (actual.keys != expected.keys) return@runCatching false
        for ((relative, expectedFile) in expected) {
            val actualFile = actual.getValue(relative)
            if (actualFile.isDirectory != expectedFile.isDirectory) return@runCatching false
            if (expectedFile.isFile &&
                (actualFile.length() != expectedFile.length() ||
                    !fileDigest(actualFile).contentEquals(fileDigest(expectedFile)))
            ) return@runCatching false
        }
        true
    }.getOrDefault(false)

    private fun readRuntimeTree(root: File, maxEntries: Int = Int.MAX_VALUE): Map<String, File> {
        val canonicalRoot = canonicalRuntimeRoot(root)
        require(canonicalRoot.isDirectory) { "Runtime root is not a directory" }
        val entries = mutableMapOf<String, File>()
        fun visit(file: File, relative: String) {
            require(entries.size < maxEntries) { "Runtime contains extra entries" }
            entries[relative] = file
            if (file.isDirectory) {
                val children = file.listFiles() ?: error("Unable to list runtime directory")
                for (child in children) {
                    val canonicalChild = canonicalRuntimeChild(child, file)
                    val childRelative = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                    visit(canonicalChild, childRelative)
                }
            }
        }
        visit(canonicalRoot, "")
        return entries
    }

    /** Resolve parent aliases such as /data/user, but reject an alias in the runtime slot itself. */
    private fun canonicalRuntimeRoot(root: File): File {
        val absolute = root.absoluteFile
        val parent = absolute.parentFile?.canonicalFile ?: error("Runtime root has no parent")
        return canonicalRuntimeChild(absolute, parent)
    }

    /** Each direct child must keep its original name beneath the already verified parent. */
    private fun canonicalRuntimeChild(child: File, canonicalParent: File): File {
        val expected = File(canonicalParent, child.name)
        val canonical = child.canonicalFile
        require(canonical == expected) { "Runtime contains an aliased or escaped path" }
        require(canonical.isDirectory || canonical.isFile) { "Unsupported runtime entry type" }
        return canonical
    }

    private fun fileDigest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest()
    }

    /**
     * Activates both generations only after both archives have been prepared. The rename
     * seam exercises real filesystem compensation without relying on platform permissions.
     * A replaced current is retained in quarantine; no unverified content becomes last_good.
     */
    internal fun activatePreparedRuntime(
        root: File,
        staging: File,
        preparedLastGood: File,
        rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) }
    ): Boolean {
        val current = File(root, "current")
        val lastGood = File(root, "last_good")
        val token = System.nanoTime()
        val previousCurrent = File(root, "quarantine/previous-current-$token")
        val previousLastGood = File(root, ".last-good-previous-$token")
        val completedMoves = mutableListOf<Pair<File, File>>()
        fun move(source: File, target: File) {
            check(!target.exists() && rename(source, target)) {
                "Unable to move runtime ${source.name} to ${target.name}"
            }
            completedMoves.add(source to target)
        }
        try {
            check(staging.isDirectory && preparedLastGood.isDirectory) { "Prepared runtime is missing" }
            check(previousCurrent.parentFile!!.let { it.mkdirs() || it.isDirectory }) {
                "Unable to prepare runtime quarantine"
            }
            makeWritableRecursively(current)
            makeWritableRecursively(lastGood)
            if (current.exists()) {
                move(current, previousCurrent)
            }
            if (lastGood.exists()) {
                move(lastGood, previousLastGood)
            }
            move(staging, current)
            move(preparedLastGood, lastGood)
        } catch (error: Exception) {
            // Undo only completed moves, in reverse order. Never delete an old generation
            // if the filesystem also rejects compensation; keep it available for recovery.
            var restored = true
            completedMoves.asReversed().forEach { (source, target) ->
                val reverted = !source.exists() &&
                    runCatching { rename(target, source) }.getOrDefault(false)
                restored = reverted && restored
            }
            makeReadOnlyRecursively(current)
            makeReadOnlyRecursively(lastGood)
            Log.e(TAG, "Runtime activation failed; previous layout restored=$restored", error)
            return false
        }
        makeReadOnlyRecursively(current)
        makeReadOnlyRecursively(lastGood)
        if (previousLastGood.exists()) {
            // A refused link in an old tree must not be followed later by recursive cleanup.
            val safeToDelete = runCatching { readRuntimeTree(previousLastGood) }.isSuccess
            if (!safeToDelete || !previousLastGood.deleteRecursively()) {
                Log.w(TAG, "Retaining superseded runtime backup: ${previousLastGood.absolutePath}")
            }
        }
        return true
    }

    /**
     * Roll back the runtime and optionally commit the matching outer-module state.
     * If the outer commit fails, restore the runtime directories to their pre-rollback
     * layout so callers never observe a split version.
     */
    fun rollback(
        context: Context,
        moduleId: String,
        commitOuterState: (() -> Boolean)? = null
    ): RuntimeResult {
        val root = runtimeRoot(context, moduleId)
        val current = File(root, "current")
        val lastGood = File(root, "last_good")
        if (!lastGood.isDirectory) return RuntimeResult(false, "rollback_unavailable", context.getString(R.string.module_error_no_last_good_package))
        val quarantine = File(root, "quarantine/${System.currentTimeMillis()}")
        quarantine.parentFile?.mkdirs()
        val hadCurrent = current.exists()
        makeWritableRecursively(current)
        makeWritableRecursively(lastGood)
        if (hadCurrent && !current.renameTo(quarantine)) {
            return RuntimeResult(false, "quarantine_failed", context.getString(R.string.module_error_unable_quarantine))
        }
        if (!lastGood.renameTo(current)) {
            if (hadCurrent) {
                makeWritableRecursively(quarantine)
                if (!quarantine.renameTo(current)) {
                    current.deleteRecursively()
                    Log.e(TAG, "回滚失败后无法恢复原 runtime current: $moduleId")
                }
            }
            return RuntimeResult(false, "rollback_failed", context.getString(R.string.module_error_unable_restore_last_good))
        }

        current.walkTopDown().forEach { it.setReadOnly() }
        val outerCommitted = commitOuterState?.let { callback ->
            runCatching { callback() }.getOrDefault(false)
        } ?: true
        if (!outerCommitted) {
            val restored = restorePreRollbackRuntime(current, lastGood, quarantine, hadCurrent)
            if (!restored) Log.e(TAG, "外层状态提交失败且 runtime 回滚无法复原: $moduleId")
            return RuntimeResult(false, "state_sync_failed", context.getString(R.string.module_error_unable_restore_last_good))
        }
        return RuntimeResult(true)
    }

    /** Restore current/last_good/quarantine to the layout from before rollback began. */
    private fun restorePreRollbackRuntime(
        current: File,
        lastGood: File,
        quarantine: File,
        hadCurrent: Boolean
    ): Boolean {
        makeWritableRecursively(current)
        makeWritableRecursively(lastGood)
        makeWritableRecursively(quarantine)
        if (current.exists() && !current.renameTo(lastGood)) return false
        if (!hadCurrent) {
            return !current.exists()
        }
        if (!quarantine.isDirectory || !quarantine.renameTo(current)) {
            if (lastGood.isDirectory && !current.exists()) lastGood.renameTo(current)
            return false
        }
        current.walkTopDown().forEach { it.setReadOnly() }
        return true
    }

    fun uninstall(context: Context, moduleId: String): RuntimeResult {
        val root = runtimeRoot(context, moduleId)
        if (!root.exists()) return RuntimeResult(true)
        makeWritableRecursively(root)
        return if (root.deleteRecursively()) RuntimeResult(true)
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

    /** Restore write access before a lifecycle operation replaces or removes read-only content. */
    private fun makeWritableRecursively(root: File) {
        visitRuntimePermissions(root) { it.setWritable(true, false) }
    }

    private fun makeReadOnlyRecursively(root: File) {
        visitRuntimePermissions(root) { it.setReadOnly() }
    }

    /** API 24-compatible permission walk: leave unreadable/aliased entries untouched. */
    private fun visitRuntimePermissions(root: File, action: (File) -> Unit) {
        val canonicalRoot = runCatching { canonicalRuntimeRoot(root) }.getOrNull() ?: return
        fun visit(file: File) {
            if (file.isDirectory) {
                val children = file.listFiles() ?: return
                for (child in children) {
                    val canonicalChild = runCatching { canonicalRuntimeChild(child, file) }.getOrNull()
                    if (canonicalChild != null) visit(canonicalChild)
                }
            }
            action(file)
        }
        visit(canonicalRoot)
    }

    /**
     * A package can pass transport SHA/signature checks and still be unusable as a runtime
     * archive. This is called before the runtime current directory is switched, so reject
     * only isolates the bad outer archive; it must never route an archive/web/unity failure
     * through the APK ModuleManager rollback directory.
     */
    private fun rejectPackage(
        context: Context,
        manifest: com.gamecenter.app.modules.ModuleManifest,
        archive: File
    ) {
        if (!archive.exists()) return

        // runtime/current 尚未切换：保留旧 runtime last-good/current 不动，只隔离
        // 本次坏外层包。quarantine 是审计资产，不能因为回滚失败而删除。
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
