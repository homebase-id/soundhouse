package id.homebase.audio.importing

import co.touchlab.kermit.Logger
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.file.FileOperationsProvider
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.data.TrackOrigin
import id.homebase.audio.data.TrackUploadTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

enum class ImportStatus { Queued, Uploading, Done, Failed }

data class ImportJob(
    val id: Uuid,
    val fileName: String,
    val status: ImportStatus = ImportStatus.Queued,
    val progress: Float = 0f,
)

/**
 * Uploads picked or recorded files one at a time. Lives in an app-lifetime scope so an import keeps
 * going when the user leaves the screen that started it.
 */
class TrackImporter(
    private val target: TrackUploadTarget,
    private val fileOps: FileOperationsProvider,
    scope: CoroutineScope,
    private val onUploaded: suspend (HomebaseFile) -> Unit,
    private val readMetadata: suspend (String) -> AudioFileMetadata = ::readAudioMetadata,
    private val readCover: suspend (String) -> ByteArray? = ::readCoverArt,
) {
    private class Request(
        val job: ImportJob,
        val path: String,
        val title: String?,
        val origin: TrackOrigin,
        val deleteSourceAfter: Boolean,
        val tags: List<Uuid>,
    )

    private val _jobs = MutableStateFlow<List<ImportJob>>(emptyList())
    val jobs: StateFlow<List<ImportJob>> = _jobs.asStateFlow()

    private val queue = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch { for (request in queue) run(request) }
    }

    /** [title] overrides the file's own metadata; [deleteSourceAfter] for app-owned copies. */
    fun enqueue(
        path: String,
        fileName: String,
        title: String? = null,
        origin: TrackOrigin = TrackOrigin.Imported,
        deleteSourceAfter: Boolean = false,
        tags: List<Uuid> = emptyList(),
    ): Uuid {
        val job = ImportJob(Uuid.random(), fileName)
        _jobs.update { it + job }
        queue.trySend(Request(job, path, title, origin, deleteSourceAfter, tags))
        return job.id
    }

    fun dismiss(id: Uuid) {
        _jobs.update { jobs -> jobs.filterNot { it.id == id && it.status != ImportStatus.Uploading && it.status != ImportStatus.Queued } }
    }

    fun clearFinished() {
        _jobs.update { jobs -> jobs.filterNot { it.status == ImportStatus.Done } }
    }

    private suspend fun run(request: Request) {
        val id = request.job.id
        setJob(id) { it.copy(status = ImportStatus.Uploading) }
        val succeeded = try {
            upload(request)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, TAG) { "Import of ${request.job.fileName} failed" }
            false
        } finally {
            if (request.deleteSourceAfter) fileOps.deleteTempFile(request.path)
        }
        setJob(id) {
            if (succeeded) it.copy(status = ImportStatus.Done, progress = 1f) else it.copy(status = ImportStatus.Failed)
        }
    }

    private suspend fun upload(request: Request) {
        val id = request.job.id
        val fileName = request.job.fileName
        val metadata = readMetadata(request.path)
        val content = AudioTrackContent(
            title = request.title?.trim()?.takeIf { it.isNotEmpty() } ?: trackTitle(metadata, fileName),
            sizeBytes = fileOps.getFileSize(request.path),
            mimeType = mimeTypeForFileName(fileName),
            durationMs = metadata.durationMs,
            fileName = fileName,
            origin = request.origin,
        )
        val uploaded = target.uploadTrack(
            sourcePath = request.path,
            content = content,
            tags = request.tags,
            coverArt = if (request.origin == TrackOrigin.Imported) readCover(request.path) else null,
            onProgress = { progress -> setJob(id) { it.copy(progress = progress) } },
        )
        target.getTrackFile(uploaded.fileId)?.let { onUploaded(it) }
    }

    private fun setJob(id: Uuid, change: (ImportJob) -> ImportJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
    }

    private companion object {
        const val TAG = "TrackImporter"
    }
}
