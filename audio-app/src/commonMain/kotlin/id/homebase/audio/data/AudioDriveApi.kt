package id.homebase.audio.data

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
        onProgress: (Float) -> Unit = {},
    ): UploadedTrack

    suspend fun getTrackFile(fileId: Uuid): HomebaseFile?
}

interface TrackEditor {
    suspend fun renameTrack(track: AudioTrack, newTitle: String): Uuid
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
        onProgress: (Float) -> Unit,
    ): UploadedTrack {
        val keyHeader = KeyHeader.newRandom16()
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
                    ),
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
        }
    }

    override suspend fun renameTrack(track: AudioTrack, newTitle: String): Uuid =
        updateTrackHeader(track, track.content.copy(title = newTitle), track.tags)

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

    private suspend fun updateTrackHeader(track: AudioTrack, content: AudioTrackContent, tags: List<Uuid>): Uuid =
        updateHeader(track.fileId, track.keyHeader, trackMetadata(content, track.uniqueId, tags, track.versionTag, track.coverPreview))

    private suspend fun updateHeader(fileId: Uuid, current: KeyHeader, metadata: UploadFileMetadata): Uuid {
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
                    manifest = UpdateManifest.build(payloads = emptyList(), thumbnails = emptyList()),
                ),
                metadata = metadata.encryptContent(keyHeader),
                payloads = emptyList(),
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
            content = OdinSystemSerializer.serialize(content),
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
            content = OdinSystemSerializer.serialize(AudioCollectionContent(name)),
        ),
        versionTag = versionTag,
    )

    private companion object {
        const val TAG = "AudioDriveApi"
        const val PAGE_SIZE = 200
        val COVER_SIZES = listOf(
            ThumbnailInstruction(quality = 84, maxPixelDimension = 320, maxBytes = 40 * 1024),
            ThumbnailInstruction(quality = 84, maxPixelDimension = 640, maxBytes = 120 * 1024),
        )
    }
}
