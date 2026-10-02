package id.homebase.api.file

import co.touchlab.kermit.Logger
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * Shared logic for the DURABLE outbox staging directory (#842) — the one place
 * encrypted, ready-to-transmit upload payloads live while their outbox row is
 * pending. Unlike the cache directory, the staging dir is never reclaimed by
 * the OS under storage pressure and is invisible to [CacheSweeper] (it sits
 * outside `getCacheDirectory()`), so a payload survives restarts, "Clear
 * caches", and storage-pressure events until its row drains.
 *
 * Lifecycle (the ONLY ways a staged file dies):
 *  - send success — `DriveUploadProvider.cleanupPayloadTempFiles`;
 *  - logout — [wipeOutboxStaging] (paired with the outbox table wipe).
 *
 * All functions take an injectable [FileSystem] (pattern: [safeDeleteRecursively],
 * `HlsScratchCleanup`) so they run unchanged over a test `FakeFileSystem`.
 */
const val OUTBOX_STAGING_DIR_NAME: String = "outbox-staging"

/**
 * Reserve a unique, not-yet-written path `<stagingDir>/<prefix><token><suffix>`,
 * creating [stagingDir] if needed. Writes NOTHING — this is the seam for stream
 * writers (`writeStream` of an encrypting Flow), which must target the staging
 * dir directly instead of writing to cache scratch and copying.
 */
fun createStagingPathIn(
    stagingDir: String,
    prefix: String,
    suffix: String,
    fileSystem: FileSystem = systemFileSystem,
): String {
    val dir = stagingDir.toPath()
    fileSystem.createDirectories(dir)
    while (true) {
        val candidate = dir / "$prefix${randomToken()}$suffix"
        if (!fileSystem.exists(candidate)) return candidate.toString()
    }
}

/**
 * Delete everything inside [stagingDir] (logout). Each child goes through
 * [safeDeleteRecursively]'s guards; the dir itself stays. Best-effort — a
 * per-child failure is logged there and doesn't stop the wipe.
 */
fun wipeOutboxStaging(
    stagingDir: String,
    fileSystem: FileSystem = systemFileSystem,
) {
    val dir = stagingDir.toPath()
    val children = runCatching { fileSystem.list(dir) }.getOrElse { return }
    for (child in children) {
        safeDeleteRecursively(stagingDir, child.name, fileSystem)
    }
}

private fun copyRecursively(fileSystem: FileSystem, source: Path, target: Path) {
    val meta = fileSystem.metadata(source)
    if (meta.isDirectory) {
        fileSystem.createDirectories(target)
        for (child in fileSystem.list(source)) {
            copyRecursively(fileSystem, child, target / child.name)
        }
    } else {
        fileSystem.copy(source, target)
    }
}

private fun randomToken(): String = Random.nextLong().toULong().toString(16)

private const val TAG = "OutboxStaging"
