package id.homebase.soundhouse.data

import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.JvmFileOperationsProvider
import java.io.File

/** Real JVM file ops with the cache rooted in a temp dir instead of the app's cache directory. */
internal class TestFileOps(private val cacheDir: File) : FileOperationsProvider by JvmFileOperationsProvider() {
    override fun getCacheDirectory(): String = cacheDir.absolutePath
}
