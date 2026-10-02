package id.homebase.api.client.drives.files.reactions

import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.files.ValidationUtil
import id.homebase.api.client.websockets.InternalDriveFileId
import id.homebase.api.common.OdinId
import id.homebase.api.serialization.OdinSystemSerializer
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

// ==================== REQUEST / RESPONSE MODELS ====================

// ==================== PROVIDER ====================

@OptIn(ExperimentalEncodingApi::class)
class DriveFileGroupReactionProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object {
        private const val TAG = "DriveFileGroupReactionProvider"
    }

    // -------------------- ADD --------------------

    // -------------------- DELETE --------------------

    // -------------------- LIST --------------------

    // -------------------- SUMMARY --------------------

    // -------------------- LIST BY IDENTITY --------------------
}

