package id.homebase.api.file

import okio.FileSystem
import okio.Path.Companion.toPath

object AppCacheDirs {
    const val SCRATCH_DIR_NAME: String = "hb-scratch"
    const val HLS_DIR_PREFIX: String = "hls_"

    const val EXPORT: String = "export"
    const val HLS: String = "hls"
    const val PICKER_COPIES: String = "picker-copies"

    fun scratchRoot(cacheDir: String): String = cacheDir.trimEnd('/') + "/" + SCRATCH_DIR_NAME

    fun scratchPath(cacheDir: String, sub: String): String = scratchRoot(cacheDir) + "/" + sub

    fun scratchDir(cacheDir: String, sub: String, fileSystem: FileSystem = systemFileSystem): String =
        scratchPath(cacheDir, sub).also { fileSystem.createDirectories(it.toPath()) }
}

fun FileOperationsProvider.scratchDir(sub: String): String =
    AppCacheDirs.scratchDir(getCacheDirectory(), sub)
