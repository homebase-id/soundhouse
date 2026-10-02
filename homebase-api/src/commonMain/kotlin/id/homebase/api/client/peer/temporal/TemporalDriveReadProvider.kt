package id.homebase.api.client.peer.temporal

import co.touchlab.kermit.Logger
import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.PayloadSizePolicy
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.drives.QueryBatchRequest
import id.homebase.api.client.drives.QueryBatchResponse
import id.homebase.api.client.drives.ServerFile
import id.homebase.api.client.drives.files.BytesResponse
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.common.OdinId
import id.homebase.api.serialization.OdinSystemSerializer
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlin.uuid.Uuid

/**
 * Client for the **temporal (time-boxed / "emergency") read** API a peer identity hosts for its
 * sensitive drives (odin-core PR #1567). The caller's access derives from
 * `DrivePermission.ConditionalTemporalRead` granted via a circle on the peer; the peer clamps every
 * read to a recent window and notifies its owner. Used by the Location App's family-emergency feature
 * to pull a relative's recent location history.
 *
 * Like the rest of the peer API, every call hits the **user's own host** (`creds.domain`) with
 * `bearerAuth` + shared-secret encryption; the own host brokers to the peer server-side and
 * re-encrypts the response under the caller's shared secret, so the decode path is identical to the
 * non-temporal providers. Mirrors [id.homebase.api.client.peer.PeerDriveQueryProvider]'s style and
 * reuses [DriveQueryProvider.mapQueryBatchResponse] / [DriveFileProvider.decryptBytes] so no decode
 * or decrypt logic is duplicated.
 *
 * Routes (all under `/peer/{odinId}/drives/{driveId}/temporal`):
 * - `POST verify`                                  → [TemporalAccessStatus]
 * - `POST query-batch`                             → [QueryBatchResponse]
 * - `GET  files/{fileId}/header`                   → [HomebaseFile] (null outside the window / missing)
 * - `GET  files/{fileId}/payload/{payloadKey}`     → payload bytes (null outside the window / missing)
 * - `GET  files/{fileId}/payload/{payloadKey}/thumb/{w}/{h}` → thumbnail bytes
 */
class TemporalDriveReadProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager,
    private val driveQueryProvider: DriveQueryProvider,
    private val driveFileProvider: DriveFileProvider,
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object { private const val TAG = "TemporalRead" }
}
