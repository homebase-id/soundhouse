package id.homebase.api.client.peer

import co.touchlab.kermit.Logger
import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.UploadProgress
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.upload.TransferUploadStatus
import id.homebase.api.client.drives.upload.TransitInstructionSet
import id.homebase.api.client.drives.upload.TransitUploadResult
import id.homebase.api.client.drives.upload.UploadFileRequest
import id.homebase.api.client.drives.upload.UploadManifest
import id.homebase.api.client.drives.upload.buildSharedSecretEncryptedUploadDescriptor
import id.homebase.api.client.drives.upload.buildTransitFormData
import id.homebase.api.client.drives.upload.calculateUploadSize
import id.homebase.api.crypto.ByteArrayUtil
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.serialization.OdinSystemSerializer
import io.ktor.client.HttpClient

/**
 * Writes a file straight onto a **peer's** drive (a community owner's collaborative drive) over
 * transit. Mirrors the JS `uploadFileOverPeer` (`js-lib/.../peer/peerData/Upload/PeerFileUploader.ts`).
 *
 * The user's OWN host (`creds.domain`) brokers the send: the request is posted to the local host,
 * which forwards the encrypted file to the [TransitInstructionSet.recipients] (the owner) and lands
 * it on [TransitInstructionSet.remoteTargetDrive]. Descriptor/payload encryption is identical to the
 * own-host upload path — the caller pre-encrypts `metadata.appData.content` and we encrypt the
 * descriptor with the caller's own shared secret (see [buildSharedSecretEncryptedUploadDescriptor]).
 *
 * This is what honours the previously-unused [id.homebase.api.client.drives.upload.TransitOptions.remoteTargetDrive].
 */
class PeerDriveUploadProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager,
    private val fileOperationsProvider: FileOperationsProvider,
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object {
        private const val TAG = "PeerDriveUpload"
    }
}
