package id.homebase.api.client.drives.files

import id.homebase.api.client.OdinApiProviderBase
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.common.OdinId
import id.homebase.api.common.time.UnixTimeUtc
import id.homebase.api.serialization.OdinSystemSerializer
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

data class SendReadReceiptResultFileItem(
    val fileId: Uuid,
    val status: List<SendReadReceiptResultRecipientStatusItem>
)

@Serializable
data class SendReadReceiptResultRecipientStatusItem(
    val recipient: OdinId?,
    val status: SendReadReceiptResultStatus
)

@Serializable
enum class SendReadReceiptResultStatus {
    @SerialName("notConnectedToOriginalSender")
    NotConnectedToOriginalSender,

    @SerialName("fileDoesNotExist")
    FileDoesNotExist,

    @SerialName("fileDoesNotHaveSender")
    FileDoesNotHaveSender,

    @SerialName("missingGlobalTransitId")
    MissingGlobalTransitId,

    @SerialName("enqueued")
    Enqueued,

    @SerialName("cannotSendReadReceiptToSelf")
    CannotSendReadReceiptToSelf
}

@OptIn(ExperimentalEncodingApi::class)
public class DriveFileOperationsProvider(
    httpClient: HttpClient,
    credentialsManager: CredentialsManager
) : OdinApiProviderBase(httpClient, credentialsManager) {
    companion object {
        private const val TAG = "DriveFileOperationsProvider"
    }
}

