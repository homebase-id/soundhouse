package id.homebase.api.client.peer

import co.touchlab.kermit.Logger
import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.common.OdinId
import io.ktor.client.HttpClient
import kotlin.uuid.Uuid

/**
 * Asks the user's own server to check whether a file exists on a connected peer.
 * The peer-to-peer call is performed server-side; this client only talks to
 * the user's own host (creds.domain) over the standard V2 OwnerOrApp API.
 *
 * Every call logs request + raw response under the `PeerDriveQuery` tag so
 * heal flows (and anything else asking "does the peer hold this file?") can
 * be audited against the wire-level answer without re-running the call.
 */
class PeerDriveQueryProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager,
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object { private const val TAG = "PeerDriveQuery" }
}
