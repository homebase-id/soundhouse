package id.homebase.audio.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.drives.QueryBatchRequest
import id.homebase.api.client.drives.QueryBatchResultOptionsRequest
import id.homebase.api.client.drives.QueryBatchSortField
import id.homebase.api.client.drives.QueryBatchSortOrder
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.files.PayloadFile
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

/** Network operations on the Audio drive. Everything the app writes goes through here. */
class AudioDriveApi(
    private val queryProvider: DriveQueryProvider,
    private val uploadProvider: DriveUploadProvider,
    private val fileProvider: DriveFileProvider,
    private val fileOps: FileOperationsProvider,
    private val driveId: Uuid = audioDriveId,
) {
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

    suspend fun getTrackFile(fileId: Uuid): HomebaseFile? = fileProvider.getFileHeader(driveId, fileId)

    /**
     * Encrypts [sourcePath] into a temp file (the upload provider deletes it afterwards) and
     * uploads it as the track payload. [onProgress] is 0..1 over the bytes sent.
     */
    suspend fun uploadTrack(
        sourcePath: String,
        content: AudioTrackContent,
        tags: List<Uuid> = emptyList(),
        uniqueId: Uuid = Uuid.random(),
        onProgress: (Float) -> Unit = {},
    ): UploadedTrack {
        val keyHeader = KeyHeader.newRandom16()
        val encryptedPath = fileOps.createUploadTempPath("audio-", ".bin")
        try {
            fileOps.writeStream(
                encryptedPath,
                AesCbc.streamEncryptWithCbc(fileOps.readFileAsFlow(sourcePath), keyHeader.aesKey, keyHeader.iv),
            )
            val metadata = trackMetadata(content, uniqueId, tags, versionTag = null).encryptContent(keyHeader)
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
                        )
                    ),
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

    suspend fun renameTrack(track: AudioTrack, newTitle: String): Uuid {
        val content = track.content.copy(title = newTitle)
        // The server rejects an update that reuses the header IV (mustRotateKeyHeaderIvWhenUpdating).
        val keyHeader = KeyHeader(iv = ByteArrayUtil.getRndByteArray(16), aesKey = track.keyHeader.aesKey)
        val metadata = trackMetadata(content, track.uniqueId, track.tags, track.versionTag)
            .encryptContent(keyHeader)
        val result = uploadProvider.updateFileByFileId(
            UpdateFileByFileIdRequest(
                driveId = driveId,
                fileId = track.fileId,
                keyHeader = keyHeader,
                instructions = FileUpdateInstructionSet(
                    transferIv = ByteArrayUtil.getRndByteArray(16),
                    locale = UpdateLocale.Local,
                    recipients = emptyList(),
                    manifest = UpdateManifest.build(payloads = emptyList(), thumbnails = emptyList()),
                ),
                metadata = metadata,
                payloads = emptyList(),
                thumbnails = emptyList(),
            )
        ) ?: error("Rename of ${track.fileId} returned no result")
        return result.newVersionTag
    }

    suspend fun deleteTrack(fileId: Uuid) {
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

    private fun trackMetadata(
        content: AudioTrackContent,
        uniqueId: Uuid?,
        tags: List<Uuid>,
        versionTag: Uuid?,
    ) = UploadFileMetadata(
        allowDistribution = false,
        isEncrypted = true,
        appData = UploadAppFileMetaData(
            uniqueId = uniqueId,
            tags = tags.ifEmpty { null },
            fileType = AUDIO_TRACK_FILE_TYPE,
            content = OdinSystemSerializer.serialize(content),
        ),
        versionTag = versionTag,
    )

    private companion object {
        const val PAGE_SIZE = 200
    }
}
