package id.homebase.audio.importing

import id.homebase.audio.download.writeTextAtomically
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import id.homebase.api.file.systemFileSystem
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import kotlin.uuid.Uuid
import id.homebase.api.client.ForbiddenException
import id.homebase.api.client.NetworkException
import id.homebase.api.client.OdinApiException
import id.homebase.api.client.ServerException
import id.homebase.api.client.UnauthorizedException
import id.homebase.api.file.SourceUnavailableException
import id.homebase.core.files.materializeForUpload
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path

enum class ImportStatus { Queued, Uploading, Retrying, Done, Failed }

enum class ImportFailure { Connection, TooLarge, NotAllowed, Server, Unreadable, Unknown }

fun importFailureOf(error: Throwable): ImportFailure {
    var cause: Throwable? = error
    while (cause != null) {
        when (cause) {
            is NetworkException -> return ImportFailure.Connection
            is SourceUnavailableException -> return ImportFailure.Unreadable
            is UnauthorizedException, is ForbiddenException -> return ImportFailure.NotAllowed
            is ServerException -> return ImportFailure.Server
            is OdinApiException -> if (cause.status == 413) return ImportFailure.TooLarge
            else -> {}
        }
        cause = cause.cause
    }
    return ImportFailure.Unknown
}

data class ImportJob(
    val id: Uuid,
    val fileName: String,
    val status: ImportStatus = ImportStatus.Queued,
    val progress: Float = 0f,
    val failure: ImportFailure? = null,
)

val ImportJob.isActive: Boolean
    get() = status == ImportStatus.Queued || status == ImportStatus.Uploading || status == ImportStatus.Retrying

/** What survives a restart: everything needed to run the upload again. */
@Serializable
data class PendingImport(
    val id: String,
    val path: String,
    val fileName: String,
    val title: String? = null,
    val origin: TrackOrigin = TrackOrigin.Imported,
    val ownsSource: Boolean = false,
    val tags: List<String> = emptyList(),
    val failure: ImportFailure? = null,
)

/**
 * Uploads picked or recorded files one at a time. Lives in an app-lifetime scope so an import keeps
 * going when the user leaves the screen that started it. With a [queueFile] the queue survives the
 * process: app-owned copies move into [stagingDir] (the cache can be cleared under the app) and stay
 * until the upload lands or the user dismisses it, so a failed import can be retried.
 */
class TrackImporter(
    private val target: TrackUploadTarget,
    private val fileOps: FileOperationsProvider,
    private val scope: CoroutineScope,
    private val onUploaded: suspend (HomebaseFile) -> Unit,
    private val readMetadata: suspend (String) -> AudioFileMetadata = ::readAudioMetadata,
    private val readCover: suspend (String) -> ByteArray? = ::readCoverArt,
    private val queueFile: String? = null,
    private val stagingDir: String? = null,
    private val fileSystem: FileSystem = systemFileSystem,
    private val retryDelaysMs: List<Long> = listOf(10_000, 30_000, 60_000, 120_000, 300_000),
    eventBus: EventBus? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pending = MutableStateFlow<Map<Uuid, PendingImport>>(emptyMap())
    private val writeLock = Mutex()

    private val _jobs = MutableStateFlow<List<ImportJob>>(emptyList())
    val jobs: StateFlow<List<ImportJob>> = _jobs.asStateFlow()

    private val intake = Channel<PendingImport>(Channel.UNLIMITED)
    private val queue = Channel<Uuid>(Channel.UNLIMITED)

    init {
        scope.launch {
            restore()
            for (item in intake) {
                val staged = stage(item)
                pending.update { it + (Uuid.parse(staged.id) to staged) }
                save()
                queue.send(Uuid.parse(staged.id))
            }
        }
        scope.launch { for (id in queue) run(id) }
        if (eventBus != null) {
            scope.launch { eventBus.events.collect { if (it is BackendEvent.SessionEnded) dropAll() } }
        }
    }

    /** Signed out: nothing queued may land on whichever identity signs in next. */
    private fun dropAll() {
        pending.value.keys.forEach(::forget)
        _jobs.update { jobs -> jobs.filter { it.status == ImportStatus.Uploading } }
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
        intake.trySend(PendingImport(job.id.toString(), path, fileName, title, origin, deleteSourceAfter, tags.map { it.toString() }))
        return job.id
    }

    fun retry(id: Uuid) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status != ImportStatus.Failed || id !in pending.value) return
        setJob(id) { it.copy(status = ImportStatus.Queued, progress = 0f, failure = null) }
        queue.trySend(id)
    }

    fun dismiss(id: Uuid) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.isActive) return
        _jobs.update { jobs -> jobs.filterNot { it.id == id } }
        forget(id)
    }

    fun clearFinished() {
        _jobs.update { jobs -> jobs.filterNot { it.status == ImportStatus.Done } }
    }

    private suspend fun run(id: Uuid) {
        val request = pending.value[id] ?: run {
            _jobs.update { jobs -> jobs.filterNot { it.id == id } }
            return
        }
        var attempt = 0
        while (true) {
            setJob(id) { it.copy(status = ImportStatus.Uploading, progress = 0f, failure = null) }
            val failure = try {
                upload(id, request)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, TAG) { "Import of ${request.fileName} failed (attempt ${attempt + 1})" }
                importFailureOf(e)
            }
            if (failure == null) {
                forget(id)
                setJob(id) { it.copy(status = ImportStatus.Done, progress = 1f) }
                return
            }
            val wait = retryDelaysMs.getOrNull(attempt++)
            if (failure != ImportFailure.Connection || wait == null) {
                pending.update { it + (id to request.copy(failure = failure)) }
                save()
                setJob(id) { it.copy(status = ImportStatus.Failed, failure = failure) }
                return
            }
            setJob(id) { it.copy(status = ImportStatus.Retrying, failure = failure) }
            delay(wait)
        }
    }

    private suspend fun upload(id: Uuid, request: PendingImport) {
        val metadata = readMetadata(request.path)
        val content = AudioTrackContent(
            title = request.title?.trim()?.takeIf { it.isNotEmpty() } ?: trackTitle(metadata, request.fileName),
            sizeBytes = fileOps.getFileSize(request.path),
            mimeType = mimeTypeForFileName(request.fileName),
            durationMs = metadata.durationMs,
            fileName = request.fileName,
            origin = request.origin,
        )
        val uploaded = target.uploadTrack(
            sourcePath = request.path,
            content = content,
            tags = request.tags.map(Uuid::parse),
            coverArt = if (request.origin == TrackOrigin.Imported) readCover(request.path) else null,
            onProgress = { progress -> setJob(id) { it.copy(progress = progress) } },
        )
        target.getTrackFile(uploaded.fileId)?.let { onUploaded(it) }
    }

    private suspend fun restore() {
        val file = queueFile ?: return
        val restored = withContext(Dispatchers.IO) {
            val path = file.toPath()
            if (!fileSystem.exists(path)) return@withContext emptyList()
            try {
                json.decodeFromString<List<PendingImport>>(fileSystem.read(path) { readUtf8() })
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Import queue unreadable; starting empty" }
                emptyList()
            }
        }
        if (restored.isEmpty()) return
        pending.value = restored.associateBy { Uuid.parse(it.id) }
        _jobs.update { current ->
            restored.map { item ->
                if (item.failure != null) ImportJob(Uuid.parse(item.id), item.fileName, ImportStatus.Failed, failure = item.failure)
                else ImportJob(Uuid.parse(item.id), item.fileName)
            } + current
        }
        restored.filter { it.failure == null }.forEach { queue.send(Uuid.parse(it.id)) }
    }

    /** Moves an app-owned copy out of the cache so it outlives a restart. */
    private suspend fun stage(item: PendingImport): PendingImport {
        val dir = stagingDir ?: return item
        if (!item.ownsSource || item.path.startsWith(dir.trimEnd('/') + "/")) return item
        return withContext(Dispatchers.IO) {
            val source = item.path.toPath()
            val destination = dir.toPath() / "${item.id}_${source.name}"
            fileSystem.createDirectories(dir.toPath())
            try {
                fileSystem.atomicMove(source, destination)
            } catch (e: IOException) {
                // Different volume (desktop temp dir): fall back to a copy.
                fileSystem.copy(source, destination)
                fileSystem.delete(source)
            }
            item.copy(path = destination.toString())
        }
    }

    private fun forget(id: Uuid) {
        val removed = pending.value[id] ?: return
        pending.update { it - id }
        if (removed.ownsSource) fileOps.deleteTempFile(removed.path)
        save()
    }

    private fun save() {
        val file = queueFile ?: return
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // Read under the lock: saves can start out of order, and the last write must be the latest state.
                val snapshot = pending.value.values.toList()
                try {
                    fileSystem.writeTextAtomically(file, json.encodeToString(snapshot))
                } catch (e: Exception) {
                    Logger.w(e, TAG) { "Could not save the import queue" }
                }
            }
        }
    }

    private fun setJob(id: Uuid, change: (ImportJob) -> ImportJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
    }

    private companion object {
        const val TAG = "TrackImporter"
    }
}

/** Copies each pick into the sandbox while the picker's read grant is live, then queues it. */
suspend fun enqueuePicked(files: List<PlatformFile>, importer: TrackImporter, fileOps: FileOperationsProvider) {
    files.forEach { picked ->
        val copy = picked.materializeForUpload(fileOps)
        importer.enqueue(copy.path, picked.name, deleteSourceAfter = copy.path != picked.path)
    }
}
