@file:OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)

package id.homebase.api.client.profile

import id.homebase.api.client.ClientException
import id.homebase.api.client.OdinClientErrorCode
import id.homebase.api.client.drives.QueryBatchRequest
import id.homebase.api.client.drives.QueryBatchResultOptionsRequest
import id.homebase.api.client.drives.SystemDriveConstants
import id.homebase.api.client.drives.files.ThumbnailFile
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.client.drives.query.FileQueryParams
import id.homebase.api.client.drives.upload.EmbeddedThumb
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Thrown by [ProfileRepository.uploadPhoto] when the server rejects the photo for exceeding its
 *  size cap (400 `maxContentLengthExceeded`) — callers should prompt for a smaller photo rather
 *  than show a generic upload failure. Mirrors `ContactAppDataTooLargeException`. */
class ProfilePhotoTooLargeException(message: String) : Exception(message)

/**
 * The blur-up preview thumb for [ProfileRepository.uploadPhoto] — [bytes] is the *tiny* rendition
 * (~20px WebP, plaintext), but [naturalPixelWidth]/[naturalPixelHeight] must be the **source**
 * image's dimensions, not this tiny rendition's own resized size (a deliberate server/odin-js
 * convention — see [SetPhotoAttributeRequest.previewThumbnail]). Kept as its own type rather than
 * reusing [ThumbnailFile] so that distinction can't be missed at the call site.
 */
data class PreviewThumbnail(
    val bytes: ByteArray,
    val naturalPixelWidth: Int,
    val naturalPixelHeight: Int,
    val contentType: String = "image/webp",
)

/**
 * Read + write source of truth for the owner's standard-profile attributes.
 *
 * READ goes straight to the ProfileDrive via [DriveQueryProvider.queryBatch] (`fileType = 77`) — a
 * one-shot, on-demand query (the ProfileDrive is intentionally NOT in `mandatorySyncDrives`, so
 * there's no local index to read). The query response already decrypts each file's content, so
 * parsing is plain JSON. WRITE goes through [ProfileProvider]; on a 409 (stale versionTag) this
 * re-reads the attribute and resends, bounded by `maxAttempts`.
 *
 * Mirrors the [id.homebase.api.client.contacts.ContactRepository] / `ContactsProvider` split.
 */
class ProfileRepository(
    private val driveQueryProvider: DriveQueryProvider,
    private val profileProvider: ProfileProvider,
) {
    private val profileDriveId: Uuid = SystemDriveConstants.profileDrive.alias
}
