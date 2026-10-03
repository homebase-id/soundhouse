package id.homebase.soundhouse.download

import okio.FileSystem
import okio.Path.Companion.toPath

/** Durable per-app directory (survives cache clears). */
expect fun appDataDirectory(): String

fun downloadsDirectory(): String = appDataDirectory().trimEnd('/') + "/downloads"

fun listeningHistoryFile(): String = appDataDirectory().trimEnd('/') + "/listening-history.json"

fun importQueueFile(): String = appDataDirectory().trimEnd('/') + "/imports.json"

fun importStagingDirectory(): String = appDataDirectory().trimEnd('/') + "/imports"

fun offlineStateFile(): String = appDataDirectory().trimEnd('/') + "/offline.json"

/** Writes through a temp file so a crash mid-write never leaves a torn file. */
fun FileSystem.writeTextAtomically(path: String, text: String) {
    val target = path.toPath()
    target.parent?.let { createDirectories(it) }
    val temp = "$path.tmp".toPath()
    write(temp) { writeUtf8(text) }
    atomicMove(temp, target)
}
