package id.homebase.api.client.drives.files

import co.touchlab.kermit.Logger
import id.homebase.api.client.KeyHeader
import id.homebase.api.client.NotFoundException
import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.FileSystemType
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.drives.ServerFile
import id.homebase.api.client.drives.cache.DriveFileProviderCached
import id.homebase.api.client.drives.upload.TransferUploadStatus
import id.homebase.api.common.OdinId
import id.homebase.api.crypto.AesCbc
import id.homebase.api.crypto.EncryptedKeyHeader
import id.homebase.api.serialization.OdinSystemSerializer
import id.homebase.api.file.FileOperationsProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.options
import io.ktor.http.Headers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/** Options for payload operations with range support. */
data class PayloadOperationOptions(
    val fileSystemType: FileSystemType = FileSystemType.Standard,
    val chunkStart: Long? = null,
    val chunkLength: Long? = null,
    val lastModified: Long? = null  // TODO: <-- what is this? Cheapo versionTag check??
)

/** Response containing bytes and their content type. */
data class BytesResponse(val bytes: ByteArray, val contentType: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BytesResponse
        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}

@OptIn(ExperimentalEncodingApi::class)
public class DriveFileProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager,
    private val driveCache: DriveFileProviderCached
) : OdinApiProviderBase(httpClient, credentialsManager), ResendPayloadByteSource {
    companion object {
        private const val TAG = "DriveFileProvider"
    }

    // ==================== GET METHODS ====================

    /**
     * Gets a file header with optional decryption.
     *
     * @param driveId The target drive id containing the file
     * @param fileId The ID of the file
     * @param options Optional operation options
     * @return The HomebaseFile or null if not found
     */

    suspend fun getFileHeader(
        driveId: Uuid,
        fileId: Uuid
    ): HomebaseFile? {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")

        val creds = requireCreds()
        val url = apiUrl(
            creds.domain,
            "/drives/$driveId/files/$fileId/header"
        )

        val response = encryptedGet(
            url = url,
            token = creds.accessToken,
            secret = creds.secret
        )

        if (response.status == 404) {
            return null
        }

        throwForFailure(response)

        var file = deserialize<ServerFile>(response.body)
        return file.asHomebaseFile(creds.secret)
    }

    /**
     * Gets a file header by its uniqueId.
     *
     * @param driveId The target drive id containing the file
     * @param uniqueId The unique ID of the file
     * @return The HomebaseFile or null if not found
     */
    suspend fun getFileHeaderByUid(
        driveId: Uuid,
        uniqueId: Uuid
    ): HomebaseFile? {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(uniqueId, "uniqueId")

        val creds = requireCreds()
        val url = apiUrl(
            creds.domain,
            "/drives/$driveId/files/by-uid/$uniqueId/header"
        )

        val response = encryptedGet(
            url = url,
            token = creds.accessToken,
            secret = creds.secret
        )

        if (response.status == 404) {
            return null
        }

        throwForFailure(response)

        var file = deserialize<ServerFile>(response.body)
        return file.asHomebaseFile(creds.secret)
    }

    /** Downloads a single byterange of a payload into the encrypted disk cache without decrypting.
     *  The cache is keyed by (chunkStart, chunkLength), so a later player request with the
     *  identical range will hit this entry. Used to warm the first HLS segment. */
    suspend fun prefetchPayloadChunk(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        chunkStart: Long,
        chunkLength: Long,
        onDownloadProgress: ((Float) -> Unit)? = null,
    ) {
        driveCache.getPayloadBytesRaw(
            driveId = driveId,
            fileId = fileId,
            key = key,
            options = PayloadOperationOptions(
                chunkStart = chunkStart,
                chunkLength = chunkLength,
            ),
            onDownloadProgress = onDownloadProgress,
        )
    }

    suspend fun getPayloadBytesDecrypted(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        keyHeader: KeyHeader,
        chunkStart: Long? = null,
        chunkLength: Long? = null,
        onDownloadProgress: ((Float) -> Unit)? = null,
        // Versions the cache entry: a payload replaced under the same key would otherwise read stale.
        lastModified: Long? = null,
    ): BytesResponse? {
        return driveCache.getPayloadBytesDecrypted(
            driveId, fileId, key, keyHeader, chunkStart, chunkLength, onDownloadProgress, lastModified
        )
    }

    /**
     * Fetch the FULL still-encrypted bytes of a payload, going through the disk cache.
     * Returns null on 404. Used by the chat heal-redistribute path to pull an existing
     * payload (the group image) off our own drive and re-attach it to a `updateFileByUniqueId`
     * request as a `PayloadFile(isPreEncrypted=true, iv=<original iv>)` — that way peers
     * receiving the heal'd file also receive the payload bytes (the manifest would
     * otherwise be empty and the payload wouldn't ship).
     */
    override suspend fun getPayloadBytesEncrypted(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
    ): ByteArray? {
        val response = driveCache.getPayloadBytesRaw(driveId, fileId, key)
        if (response.status == 404) return null
        return response.bytes
    }

    /**
     * Fetch the FULL still-encrypted bytes of one of a payload's thumbnails, going
     * through the disk cache. Returns null on 404. Counterpart to
     * [getPayloadBytesEncrypted] for the heal-redistribute path: the thumbnails
     * must be re-attached alongside the payload or the `updateFileByUniqueId`
     * AppendOrOverwrite wipes them server-side (see [ResendPayloadByteSource]).
     */
    override suspend fun getThumbBytesEncrypted(
        driveId: Uuid,
        fileId: Uuid,
        payloadKey: String,
        width: Int,
        height: Int,
        lastModified: Long?,
    ): ByteArray? {
        val response = driveCache.getThumbBytesRaw(driveId, fileId, payloadKey, width, height, lastModified)
        if (response.status == 404) return null
        return response.bytes
    }

    /**
     * Seed the encrypted payload disk cache with locally-produced bytes — the
     * exact AES-CBC ciphertext the server would return for this payload. Used
     * at optimistic send time so a message whose upload later fails permanently
     * still has retrievable media: [getPayloadBytesEncrypted] checks this cache
     * before the network. Counterpart write API to [getPayloadBytesEncrypted].
     */
    suspend fun cachePayloadBytesEncrypted(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) = driveCache.cachePayloadBytesEncrypted(driveId, fileId, key, bytes, contentType)

    /** Thumbnail counterpart to [cachePayloadBytesEncrypted]; pairs with [getThumbBytesEncrypted]. */
    suspend fun cacheThumbBytesEncrypted(
        driveId: Uuid,
        fileId: Uuid,
        payloadKey: String,
        width: Int,
        height: Int,
        bytes: ByteArray,
        contentType: String,
        lastModified: Long? = null,
    ) = driveCache.cacheThumbBytesEncrypted(driveId, fileId, payloadKey, width, height, bytes, contentType, lastModified)

    /**
     * Move a file's seeded cache entries (payloads + thumbnails) from the
     * optimistic client-minted fileId to the server-assigned one at sync-back,
     * so the sender's own media keeps hitting the cache after the local record
     * adopts the server fileId. [payloads] must be the SYNCED file's
     * descriptors (their `lastModified` becomes part of the thumb cache key).
     * Counterpart to the seed APIs above.
     */
    suspend fun rekeyCachedFile(
        driveId: Uuid,
        oldFileId: Uuid,
        newFileId: Uuid,
        payloads: List<PayloadDescriptor>,
    ) = driveCache.rekeyCachedFile(driveId, oldFileId, newFileId, payloads)

    /**
     * Fetch the raw (still-encrypted) bytes for a specific byterange of a payload, going through
     * the disk cache. Used by the iOS HLS resource loader, which decrypts each HLS segment as a
     * standalone AES-CBC blob (FFmpeg encrypts each segment independently with PKCS7 padding).
     */
    suspend fun getPayloadBytesEncryptedChunk(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        chunkStart: Long,
        chunkLength: Long,
    ): ByteArray? {
        val response = driveCache.getPayloadBytesRaw(
            driveId = driveId,
            fileId = fileId,
            key = key,
            options = id.homebase.api.client.drives.files.PayloadOperationOptions(
                chunkStart = chunkStart,
                chunkLength = chunkLength,
            ),
        )
        if (response.status == 404) return null
        return response.bytes
    }

    suspend fun streamPayloadDecryptedToPath(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        keyHeader: KeyHeader,
        outputPath: String,
        fileOps: FileOperationsProvider,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = driveCache.streamPayloadDecryptedToPath(
        driveId, fileId, key, keyHeader, outputPath, fileOps, onProgress)

    suspend fun streamPayloadDecryptedToPath(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        keyHeader: KeyHeader,
        outputPath: String,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = driveCache.streamPayloadDecryptedToPath(
        driveId, fileId, key, keyHeader, outputPath, onProgress = onProgress)

    suspend fun getThumbBytesDecrypted(
        driveId: Uuid,
        fileId: Uuid,
        payloadKey: String,
        keyHeader: KeyHeader,
        width: Int,
        height: Int,
        lastModified: Long? = null
    ): BytesResponse? {
        return driveCache.getThumbBytesDecrypted(
            driveId, fileId, payloadKey, keyHeader, width, height, lastModified
        )
    }

    /**
     * Gets transfer history for a file (not the same as the TransferHistorySummary
     * which is sufficient for quick renders).
     *
     * @param driveId The target drive containing the file
     * @param fileId The ID of the file
     * @param fileSystemType Optional file system type
     * @return The TransferHistory or null if not found
     */
    suspend fun getTransferHistory(
        driveId: Uuid,
        fileId: Uuid
    ): TransferHistory? {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")

        val creds = requireCreds()
        val endpoint = "/drives/${driveId}/files/${fileId}/transfer-history"

        val response = encryptedGet(
            url = apiUrl(creds.domain, endpoint),
            token = creds.accessToken,
            secret = creds.secret
        )

        if (response.status == 404) {
            return null
        }

        throwForFailure(response)

        return deserialize<TransferHistory>(response.body)
    }

    // ==================== DELETE METHODS ====================

    /**
     * Deletes a single file from the drive.
     *
     * @param driveId The target drive containing the file
     * @param fileId The ID of the file to delete
     * @param recipients Optional list of recipients to notify
     * @param hardDelete If true, performs a hard delete instead of soft delete
     * @return True if the file was deleted successfully
     */
    suspend fun softDeleteFile(
        driveId: Uuid,
        fileId: Uuid,
        recipients: List<OdinId>? = null
    ): DeleteFileResult {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")

        val endpoint = "/drives/$driveId/files/$fileId/delete"

        val creds = requireCreds()

        // fileId not used  because we pass it in via query string
        val request =
            DeleteFileRequest(
                fileId = Uuid.NIL,
                recipients = recipients
            )

        val response = encryptedPostJson(
            url = apiUrl(creds.domain, endpoint),
            token = creds.accessToken,
            jsonBody = OdinSystemSerializer.serialize(request),
            secret = creds.secret
        )

        throwForFailure(response)

        return deserialize<DeleteFileResult>(response.body)
    }

    suspend fun hardDeleteFile(
        driveId: Uuid,
        fileId: Uuid,
        recipients: List<OdinId>? = null,
    ): Boolean {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")

        val endpoint = "/drives/$driveId/files/$fileId/hard-delete"

        val creds = requireCreds()

        // fileId not used  because we pass it in via query string
        val request =
            DeleteFileRequest(
                fileId = Uuid.NIL,
                recipients = recipients
            )

        val response = encryptedPostJson(
            url = apiUrl(creds.domain, endpoint),
            token = creds.accessToken,
            jsonBody = OdinSystemSerializer.serialize(request),
            secret = creds.secret
        )

        throwForFailure(response)

        return response.status == 200
    }

    // ==================== PRIVATE HELPER METHODS ====================
}

// Request data classes for delete operations

@Serializable
data class DeleteFileRequest(val fileId: Uuid, val recipients: List<OdinId>? = null)

@Serializable
data class DeleteFileResult(
    val fileId: Uuid,
    var localFileDeleted: Boolean,
    var localFileNotFound: Boolean,
    val recipientStatus: Map<String, TransferUploadStatus>? = null
)

