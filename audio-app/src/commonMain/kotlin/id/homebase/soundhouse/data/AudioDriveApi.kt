package id.homebase.soundhouse.data

import id.homebase.api.client.drives.upload.PayloadDeleteKey
import id.homebase.api.util.truncateToCodePoints
import id.homebase.api.client.KeyHeader
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.drives.QueryBatchRequest
import id.homebase.api.client.drives.QueryBatchResultOptionsRequest
import id.homebase.api.client.drives.QueryBatchSortField
import id.homebase.api.client.drives.QueryBatchSortOrder
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.files.PayloadFile
import id.homebase.api.client.drives.files.ThumbnailFile
import id.homebase.api.client.drives.upload.EmbeddedThumb
import id.homebase.api.image.ThumbnailInstruction
import id.homebase.api.image.createThumbnails
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.client.drives.query.FileQueryParams
import id.homebase.api.client.drives.upload.DriveUploadProvider
import id.homebase.api.client.drives.upload.FileUpdateInstructionSet
import id.homebase.api.client.drives.upload.UpdateFileByFileIdRequest
import id.homebase.api.client.drives.upload.UpdateLocale
import id.homebase.api.client.drives.upload.UpdateManifest
import id.homebase.api.client.drives.upload.UploadAppFileMetaData
import id.homebase.api.client.drives.upload.UploadFileMetadata
import id.homebase.api.client.drives.upload.UploadFileRequest
import id.homebase.api.crypto.AesCbc
import id.homebase.api.crypto.ByteArrayUtil
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.serialization.OdinSystemSerializer
import kotlin.uuid.Uuid

data class UploadedTrack(val fileId: Uuid, val uniqueId: Uuid, val versionTag: Uuid)

interface TrackUploadTarget {
    suspend fun uploadTrack(
        sourcePath: String,
        content: AudioTrackContent,
        tags: List<Uuid> = emptyList(),
        uniqueId: Uuid = Uuid.random(),
        coverArt: ByteArray? = null,
        notes: String? = null,
        onProgress: (Float) -> Unit = {},
    ): UploadedTrack

    suspend fun getTrackFile(fileId: Uuid): HomebaseFile?
}

interface TrackEditor {
    /** Replaces the track's encrypted content (title, format, details); the audio payload is untouched. */
    suspend fun updateTrackContent(track: AudioTrack, content: AudioTrackContent, notes: NotesChange = NotesChange.Keep): Uuid

    /** The track's notes: its notes payload, or a comment left in the header by an older version. */
    suspend fun readNotes(track: AudioTrack): String?
    suspend fun deleteTrack(fileId: Uuid)
    suspend fun getTrackFile(fileId: Uuid): HomebaseFile?
}

interface CollectionEditor {
    suspend fun createCollection(name: String, id: Uuid = Uuid.random()): Uuid
    suspend fun renameCollection(collection: AudioCollection, newName: String)
    suspend fun deleteCollection(fileId: Uuid)
    suspend fun setTrackTags(track: AudioTrack, tags: List<Uuid>)
    suspend fun getFile(fileId: Uuid): HomebaseFile?
}

/** Network operations on the Audio drive. Everything the app writes goes through here. */
class AudioDriveApi(
    private val queryProvider: DriveQueryProvider,
    private val uploadProvider: DriveUploadProvider,
    private val fileProvider: DriveFileProvider,
    private val fileOps: FileOperationsProvider,
    private val driveId: Uuid = audioDriveId,
) : TrackUploadTarget, TrackEditor, CollectionEditor {
    suspend fun queryTrackFiles(tagsAnyOf: List<Uuid>? = null): List<HomebaseFile> {
        val files = mutableListOf<HomebaseFile>()
        var cursor: String? = null
        do {
            val response = queryProvider.queryBatch(
                driveId,
                QueryBatchRequest(
                    queryParams = FileQueryParams(
                        fileType = listOf(AUDIO_TRACK_FILE_TYPE),
                        tagsMatchAtLeastOne = tagsAnyOf,
                    ),
                    resultOptionsRequest = QueryBatchResultOptionsRequest(
                        cursorState = cursor,
                        maxRecords = PAGE_SIZE,
                        includeMetadataHeader = true,
                        ordering = QueryBatchSortOrder.NewestFirst,
                        sorting = QueryBatchSortField.CreatedDate,
                    ),
                ),
            )
            files += response.searchResults
            cursor = response.cursorState
        } while (response.hasMoreRows && response.searchResults.isNotEmpty())
        return files
    }

    suspend fun queryTracks(tagsAnyOf: List<Uuid>? = null): List<AudioTrack> =
        queryTrackFiles(tagsAnyOf).mapNotNull { it.toAudioTrackOrNull() }

    override suspend fun getTrackFile(fileId: Uuid): HomebaseFile? = fileProvider.getFileHeader(driveId, fileId)

    /**
     * Encrypts [sourcePath] into a temp file (the upload provider deletes it afterwards) and
     * uploads it as the track payload. [onProgress] is 0..1 over the bytes sent.
     */
    override suspend fun uploadTrack(
        sourcePath: String,
        content: AudioTrackContent,
        tags: List<Uuid>,
        uniqueId: Uuid,
        coverArt: ByteArray?,
        notes: String?,
        onProgress: (Float) -> Unit,
    ): UploadedTrack {
        val keyHeader = KeyHeader.newRandom16()
        val notesPayload = cleanNotes(notes)?.let { notesPayload(it, keyHeader) }
        val art = coverArt?.let { coverThumbnails(it, keyHeader) }
        val encryptedPath = fileOps.createUploadTempPath("audio-", ".bin")
        try {
            fileOps.writeStream(
                encryptedPath,
                AesCbc.streamEncryptWithCbc(fileOps.readFileAsFlow(sourcePath), keyHeader.aesKey, keyHeader.iv),
            )
            val metadata = trackMetadata(content, uniqueId, tags, versionTag = null, preview = art?.preview)
                .encryptContent(keyHeader)
            val result = uploadProvider.uploadFile(
                UploadFileRequest(
                    driveId = driveId,
                    keyHeader = keyHeader,
                    metadata = metadata,
                    payloads = listOf(
                        PayloadFile(
                            key = AUDIO_PAYLOAD_KEY,
                            filePath = encryptedPath,
                            contentType = content.mimeType,
                            isPreEncrypted = true,
                            iv = keyHeader.iv,
                            previewThumbnail = art?.preview,
                        )
                    ) + listOfNotNull(notesPayload),
                    thumbnails = art?.thumbnails.orEmpty(),
                ),
                onProgress = { sent, total ->
                    if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                },
            ) ?: error("Upload of '${content.title}' returned no result")
            onProgress(1f)
            return UploadedTrack(result.fileId, uniqueId, result.newVersionTag)
        } finally {
            fileOps.deleteTempFile(encryptedPath)
            notesPayload?.let { fileOps.deleteTempFile(it.filePath) }
        }
    }

    // The upload provider sends payload files as they are, so notes are encrypted here like the
    // audio: the file's key with an IV of their own.
    private suspend fun notesPayload(text: String, keyHeader: KeyHeader): PayloadFile {
        val iv = ByteArrayUtil.getRndByteArray(16)
        val encrypted = AesCbc.encrypt(text.encodeToByteArray(), keyHeader.aesKey, iv)
        return PayloadFile(
            key = NOTES_PAYLOAD_KEY,
            filePath = fileOps.writeBytesToTempFile(encrypted, "notes-", ".bin"),
            contentType = NOTES_CONTENT_TYPE,
            isPreEncrypted = true,
            iv = iv,
        )
    }

    override suspend fun readNotes(track: AudioTrack): String? {
        val notesKey = track.notesKeyHeader ?: return track.details?.comment
        return fileProvider.getPayloadBytesDecrypted(
            driveId = driveId,
            fileId = track.fileId,
            key = NOTES_PAYLOAD_KEY,
            keyHeader = notesKey,
            lastModified = track.notesLastModified,
        )?.bytes?.decodeToString()
    }

    override suspend fun updateTrackContent(track: AudioTrack, content: AudioTrackContent, notes: NotesChange): Uuid =
        updateTrackHeader(track, content, track.tags, notes)

    override suspend fun setTrackTags(track: AudioTrack, tags: List<Uuid>) {
        updateTrackHeader(track, track.content, tags)
    }

    override suspend fun getFile(fileId: Uuid): HomebaseFile? = fileProvider.getFileHeader(driveId, fileId)

    override suspend fun createCollection(name: String, id: Uuid): Uuid {
        val keyHeader = KeyHeader.newRandom16()
        val result = uploadProvider.uploadFile(
            UploadFileRequest(driveId = driveId, keyHeader = keyHeader, metadata = collectionMetadata(name, id, null).encryptContent(keyHeader)),
        ) ?: error("Creating collection returned no result")
        return result.fileId
    }

    override suspend fun renameCollection(collection: AudioCollection, newName: String) {
        updateHeader(collection.fileId, collection.keyHeader, collectionMetadata(newName, collection.id, collection.versionTag))
    }

    override suspend fun deleteCollection(fileId: Uuid) {
        fileProvider.softDeleteFile(driveId, fileId)
    }

    private suspend fun updateTrackHeader(
        track: AudioTrack,
        content: AudioTrackContent,
        tags: List<Uuid>,
        notes: NotesChange = NotesChange.Keep,
    ): Uuid {
        val metadata = trackMetadata(content, track.uniqueId, tags, track.versionTag, track.coverPreview)
        return when (notes) {
            NotesChange.Keep -> updateHeader(track.fileId, track.keyHeader, metadata)
            NotesChange.Remove -> updateHeader(
                track.fileId, track.keyHeader, metadata,
                deletePayloads = if (track.hasNotesPayload) listOf(NOTES_PAYLOAD_KEY) else emptyList(),
            )
            is NotesChange.Set -> {
                val payload = notesPayload(notes.text, track.keyHeader)
                try {
                    updateHeader(track.fileId, track.keyHeader, metadata, payloads = listOf(payload))
                } finally {
                    fileOps.deleteTempFile(payload.filePath)
                }
            }
        }
    }

    private suspend fun updateHeader(
        fileId: Uuid,
        current: KeyHeader,
        metadata: UploadFileMetadata,
        payloads: List<PayloadFile> = emptyList(),
        deletePayloads: List<String> = emptyList(),
    ): Uuid {
        // The server rejects an update that reuses the header IV (mustRotateKeyHeaderIvWhenUpdating).
        val keyHeader = KeyHeader(iv = ByteArrayUtil.getRndByteArray(16), aesKey = current.aesKey)
        val result = uploadProvider.updateFileByFileId(
            UpdateFileByFileIdRequest(
                driveId = driveId,
                fileId = fileId,
                keyHeader = keyHeader,
                instructions = FileUpdateInstructionSet(
                    transferIv = ByteArrayUtil.getRndByteArray(16),
                    locale = UpdateLocale.Local,
                    recipients = emptyList(),
                    manifest = UpdateManifest.build(
                        payloads = payloads,
                        toDeletePayloads = deletePayloads.map(::PayloadDeleteKey),
                        thumbnails = emptyList(),
                    ),
                ),
                metadata = metadata.encryptContent(keyHeader),
                payloads = payloads,
                thumbnails = emptyList(),
            )
        ) ?: error("Update of $fileId returned no result")
        return result.newVersionTag
    }

    override suspend fun deleteTrack(fileId: Uuid) {
        fileProvider.softDeleteFile(driveId, fileId)
    }

    suspend fun hardDeleteTrack(fileId: Uuid): Boolean = fileProvider.hardDeleteFile(driveId, fileId)

    /** Decrypted bytes `[start, start + length)` of the track payload; the server sees ciphertext ranges only. */
    suspend fun readRange(track: AudioTrack, start: Long, length: Long): ByteArray =
        fileProvider.getPayloadBytesDecrypted(
            driveId = driveId,
            fileId = track.fileId,
            key = AUDIO_PAYLOAD_KEY,
            keyHeader = track.payloadKeyHeader,
            chunkStart = start,
            chunkLength = length,
            onDownloadProgress = null,
        )?.bytes ?: error("Payload of ${track.fileId} not found")

    /** Decrypted bytes of the smallest cover thumbnail at least [minPixels] wide, or the largest there is. */
    suspend fun readCover(track: AudioTrack, minPixels: Int): ByteArray? {
        val thumb = track.covers.firstOrNull { it.width >= minPixels } ?: track.covers.lastOrNull() ?: return null
        return fileProvider.getThumbBytesDecrypted(
            driveId = driveId,
            fileId = track.fileId,
            payloadKey = AUDIO_PAYLOAD_KEY,
            keyHeader = track.payloadKeyHeader,
            width = thumb.width,
            height = thumb.height,
            lastModified = thumb.lastModified,
        )?.bytes
    }

    suspend fun downloadTo(track: AudioTrack, outputPath: String, onProgress: (Float) -> Unit = {}): Boolean =
        fileProvider.streamPayloadDecryptedToPath(
            driveId = driveId,
            fileId = track.fileId,
            key = AUDIO_PAYLOAD_KEY,
            keyHeader = track.payloadKeyHeader,
            outputPath = outputPath,
            fileOps = fileOps,
            onProgress = onProgress,
        )

    private class CoverThumbnails(val preview: EmbeddedThumb?, val thumbnails: List<ThumbnailFile>)

    /** Thumbnails are encrypted with the payload's key and IV, so they read back like the audio. */
    private suspend fun coverThumbnails(image: ByteArray, keyHeader: KeyHeader): CoverThumbnails? = try {
        val (_, preview, thumbnails) = createThumbnails(image, AUDIO_PAYLOAD_KEY, COVER_SIZES)
        CoverThumbnails(preview, thumbnails.map { it.copy(thumbnailBytes = keyHeader.encryptDataAes(it.thumbnailBytes)) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, TAG) { "Cover art could not be decoded; uploading without it" }
        null
    }

    private fun trackMetadata(
        content: AudioTrackContent,
        uniqueId: Uuid?,
        tags: List<Uuid>,
        versionTag: Uuid?,
        preview: EmbeddedThumb? = null,
    ) = UploadFileMetadata(
        allowDistribution = false,
        isEncrypted = true,
        appData = UploadAppFileMetaData(
            uniqueId = uniqueId,
            tags = tags.ifEmpty { null },
            fileType = AUDIO_TRACK_FILE_TYPE,
            content = OdinSystemSerializer.serialize(content.fittedToHeader()),
            previewThumbnail = preview,
        ),
        versionTag = versionTag,
    )

    private fun collectionMetadata(name: String, id: Uuid, versionTag: Uuid?) = UploadFileMetadata(
        allowDistribution = false,
        isEncrypted = true,
        appData = UploadAppFileMetaData(
            uniqueId = id,
            fileType = AUDIO_COLLECTION_FILE_TYPE,
            content = OdinSystemSerializer.serialize(AudioCollectionContent(name.truncateToCodePoints(MAX_NAME_CODE_POINTS))),
        ),
        versionTag = versionTag,
    )

    private companion object {
        // Far inside the header limit even in four-byte characters; see MAX_APP_DATA_CONTENT_LENGTH.
        const val MAX_NAME_CODE_POINTS = 200
        const val TAG = "AudioDriveApi"
        const val PAGE_SIZE = 200
        val COVER_SIZES = listOf(
            ThumbnailInstruction(quality = 84, maxPixelDimension = 320, maxBytes = 40 * 1024),
            ThumbnailInstruction(quality = 84, maxPixelDimension = 640, maxBytes = 120 * 1024),
        )
    }
}
