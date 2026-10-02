package id.homebase.api.client.drives.files

import co.touchlab.kermit.Logger
import id.homebase.api.client.ByteApiResponse
import id.homebase.api.client.KeyHeader
import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.PayloadSizePolicy
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.common.OdinId
import id.homebase.api.crypto.AesCbc
import id.homebase.api.crypto.EncryptedKeyHeader
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.serialization.OdinSystemSerializer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.ktor.utils.io.ByteReadChannel
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable

private fun ByteReadChannel.asFlow(chunkSize: Int = 64 * 1024): Flow<ByteArray> = flow {
    val buffer = ByteArray(chunkSize)

    while (!isClosedForRead) {
        val read = readAvailable(buffer)
        if (read > 0) emit(buffer.copyOf(read))
    }
}

@OptIn(ExperimentalEncodingApi::class)
public class DriveFileHttpProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object {
        private const val TAG = "DriveFileHttpProvider"
    }

    // ==================== GET METHODS ====================
    suspend fun streamPayloadDecryptedToPath(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        keyHeader: KeyHeader,
        outputPath: String,
        fileOps: FileOperationsProvider,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")
        require(key.isNotBlank()) { "Key must be defined" }

        val creds = requireCreds()

        val url =
            apiUrl(
                creds.domain,
                "/drives/$driveId/files/$fileId/payload/$key"
            )

        val response =
            httpClient.get(url) {
                bearerAuth(creds.accessToken)
            }

        if (response.status.value == 404) return false

        if (response.status.value !in listOf(200, 206)) {
            throwForFailure(
                ByteApiResponse(
                    status = response.status.value,
                    headers = response.headers,
                    bytes = ByteArray(0),
                    contentType = "application/octet-stream"
                )
            )
        }

        // Progress from a bytes-read counter over Content-Length (when present) —
        // needed by the rerouted MP4 render path (#845), which showed real download
        // progress back when it buffered the whole payload.
        val totalBytes = response.contentLength()
        var readSoFar = 0L
        val encryptedFlow =
            response.bodyAsChannel().asFlow().let { upstream ->
                if (onProgress == null || totalBytes == null || totalBytes <= 0L) upstream
                else flow {
                    upstream.collect { chunk ->
                        readSoFar += chunk.size
                        onProgress((readSoFar.toFloat() / totalBytes.toFloat()).coerceAtMost(1f))
                        emit(chunk)
                    }
                }
            }

        val decryptedFlow =
            AesCbc.streamDecryptWithCbc(
                encryptedFlow,
                keyHeader.aesKey,
                keyHeader.iv
            )

        fileOps.writeStream(outputPath, decryptedFlow)
        onProgress?.invoke(1f)

        return true
    }

    // This ought to be private / protected and only used by driveCache but probably rewire it all
    /**
     * Full (non-range) reads are size-guarded at [PayloadSizePolicy.RENDER_LIMIT_BYTES]
     * (#845): a payload the app can't render has no business in RAM or the LRU —
     * export flows must use [streamPayloadDecryptedToPath]. Range reads
     * (`options.chunkStart != null`) are exempt by shape: they're the HLS playback
     * path and are bounded by their requested chunkLength.
     */
    suspend fun getPayloadBytesRawNetwork(
        driveId: Uuid,
        fileId: Uuid,
        key: String,
        options: PayloadOperationOptions = PayloadOperationOptions(),
        onDownloadProgress: ((Float) -> Unit)? = null,
    ): ByteApiResponse {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")
        require(key.isNotBlank()) { "Key must be defined" }

        val creds = requireCreds()

        val queryParams =
            buildMap<String, String> {
                options.lastModified?.let {
                    put("lastModified", it.toString())
                }
            }

        val rangeResult =
            DriveFileHelpers.getRangeHeader(
                options.chunkStart,
                options.chunkLength
            )

        val path =
            if (options.chunkStart != null)
                "/drives/$driveId/files/$fileId/payload/$key/${options.chunkStart}/${options.chunkLength ?: ""}"
            else
                "/drives/$driveId/files/$fileId/payload/$key"

        val url = apiUrl(creds.domain, path)

        val response = requestBytes(
            maxBytes = if (options.chunkStart == null) PayloadSizePolicy.RENDER_LIMIT_BYTES else null
        ) {
            httpClient.get(url) {
                bearerAuth(creds.accessToken)

                queryParams.forEach { (k, v) ->
                    url { parameters.append(k, v) }
                }

                rangeResult.rangeHeader?.let {
                    header(HttpHeaders.Range, it)
                }

                onDownloadProgress?.let { callback ->
                    onDownload { bytesReceived, contentLength ->
                        val total = contentLength ?: 0L
                        if (total > 0L) {
                            callback(bytesReceived.toFloat() / total.toFloat())
                        }
                        // if Content-Length is absent we still get called — progress stays
                        // at the synthetic milestones emitted by the caller
                    }
                }
            }
        }

        if (response.status != 200 && response.status != 206) {
            throwForFailure(response)
        }

        return response
    }

    // TODO: I suppose we can live with this NOT being streaming, but maybe we can insert a
    // safeguard so that if it's larger than 1MB we return an error (I believe thumb max is
    // roughly 1MB)
    suspend fun getThumbBytesRawNetwork(
        driveId: Uuid,
        fileId: Uuid,
        payloadKey: String,
        width: Int,
        height: Int,
        lastModified: Long? = null
    ): ByteApiResponse {
        ValidationUtil.requireValidUuid(driveId, "driveId")
        ValidationUtil.requireValidUuid(fileId, "fileId")
        require(payloadKey.isNotBlank()) { "PayloadKey must be defined" }
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }

        val creds = requireCreds()

        val queryParams =
            buildMap<String, String> {
                put("width", width.toString())
                put("height", height.toString())
                lastModified?.let {
                    put("lastModified", it.toString())
                }
            }

        val url =
            apiUrl(
                creds.domain,
                "/drives/$driveId/files/$fileId/payload/$payloadKey/thumb"
            )

        val response = requestBytes {
            httpClient.get(url) {
                bearerAuth(creds.accessToken)
                queryParams.forEach { (k, v) ->
                    url { parameters.append(k, v) }
                }
            }
        }

        if (response.status != 200 && response.status != 206) {
            throwForFailure(response)
        }

        return response
    }

    // ==================== DELETE METHODS ====================

    // ==================== INBOX METHODS ====================

    // Disabled — server now auto-processes the inbox on QueryBatch, so the
    // explicit HTTP poke is no longer needed. Kept commented so it can be
    // re-enabled if the server-side behaviour regresses.
    //
    // suspend fun processInbox(
    //     driveId: Uuid,
    //     batchSize: Int = 10
    // ): InboxStatus {
    //     ValidationUtil.requireValidUuid(driveId, "driveId")
    //
    //     val creds = requireCreds()
    //     val endpoint = "/drives/$driveId/inbox/process"
    //
    //     val response = encryptedGet(
    //         url = apiUrl(creds.domain, endpoint),
    //         token = creds.accessToken,
    //         secret = creds.secret,
    //         queryString = "batchSize=$batchSize"
    //     )
    //
    //     throwForFailure(response)
    //
    //     return deserialize<InboxStatus>(response.body)
    // }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Decrypts bytes using the shared secret (full payload/thumbnail decryption).
     */
    public suspend fun decryptBytes(
        keyHeader: KeyHeader,
        headers: Headers,
        bytes: ByteArray
    ): ByteArray {
        val payloadEncrypted =
            headers["payloadencrypted"]?.equals("true", ignoreCase = true) == true

//        val encryptedHeader64 =
//            headers["sharedsecretencryptedheader64"]

        return when {
            payloadEncrypted -> {
                decryptUsingKeyHeader(bytes, keyHeader)
            }

            else -> bytes
        }
    }

    /** Decrypts chunked bytes with offset handling. */
    suspend fun decryptChunkedBytes(
        headers: Headers,
        responseBytes: ByteArray,
        keyHeader: KeyHeader,
        startOffset: Int,
        chunkStart: Int
    ): ByteArray {
        val payloadEncrypted =
            headers["payloadencrypted"]?.equals("true", ignoreCase = true) == true

        if (payloadEncrypted) {
            val key = keyHeader.aesKey

            val (iv, cipher) = run {
                val padding = ByteArray(16) { 16 }

                val encryptedPadding =
                    AesCbc.encrypt(
                        padding,
                        key,
                        iv = responseBytes.copyOfRange(
                            responseBytes.size - 16,
                            responseBytes.size
                        )
                    ).copyOfRange(0, 16)

                // getRangeHeader fetches from 0 for any start below 16, so those reads begin at
                // the first block and need the file IV, not a preceding ciphertext block.
                if (chunkStart < 16) {
                    // First block
                    Pair(
                        keyHeader.iv,
                        mergeByteArrays(
                            listOf(responseBytes, encryptedPadding)
                        )
                    )
                } else {
                    // Middle blocks
                    Pair(
                        responseBytes.copyOfRange(0, 16),
                        mergeByteArrays(
                            listOf(
                                responseBytes.copyOfRange(16, responseBytes.size),
                                encryptedPadding
                            )
                        )
                    )
                }
            }

            val decryptedBytes = AesCbc.decrypt(cipher, key, iv)

            // Match TS behavior:
            // decryptedBytes.slice(startOffset ? startOffset - 16 : 0)
            val sliceStart =
                if (chunkStart < 16) startOffset else maxOf(startOffset - 16, 0)

            return decryptedBytes.copyOfRange(sliceStart, decryptedBytes.size)
        } else {
            // Not encrypted → return raw bytes with offset
            return responseBytes.copyOfRange(startOffset, responseBytes.size)
        }
    }

    fun mergeByteArrays(chunks: List<ByteArray>): ByteArray {
        var size = 0
        for (chunk in chunks) {
            size += chunk.size
        }

        val merged = ByteArray(size)
        var offset = 0

        for (chunk in chunks) {
            chunk.copyInto(
                destination = merged,
                destinationOffset = offset
            )
            offset += chunk.size
        }

        return merged
    }

    private suspend fun decryptUsingKeyHeader(
        encryptedBytes: ByteArray,
        keyHeader: KeyHeader
    ): ByteArray {
        return keyHeader.decrypt(encryptedBytes)
    }
}
