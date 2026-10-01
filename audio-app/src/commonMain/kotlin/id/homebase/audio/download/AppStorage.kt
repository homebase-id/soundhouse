package id.homebase.audio.download

/** Durable per-app directory (survives cache clears). */
expect fun appDataDirectory(): String

fun downloadsDirectory(): String = appDataDirectory().trimEnd('/') + "/downloads"

fun listeningHistoryFile(): String = appDataDirectory().trimEnd('/') + "/listening-history.json"
