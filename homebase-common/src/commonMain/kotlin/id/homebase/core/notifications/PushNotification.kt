package id.homebase.core.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Push notification payload matching the backend DevicePushNotificationRequest format. */
@Serializable
data class PushNotification(
    val id: String? = null,
    val senderId: String,
    val unread: Boolean = false,
    @SerialName("timestamp")
    val created: Long = 0,
    val options: PushNotificationPayloadOptions,
    val appDisplayName: String? = null
)

/** Options embedded in a push notification payload. */
@Serializable
data class PushNotificationPayloadOptions(
    val appId: String,
    val typeId: String,
    val tagId: String = "",
    val silent: Boolean = false,
    val unEncryptedMessage: String? = null,
    val peerSubscriptionId: String? = null,
    // TODO: Encrypted notification body support — when backend sends keyHeader,
    // use EncryptedKeyHeader.decryptAesToKeyHeader() + KeyHeader.decrypt() to
    // decrypt encryptedBody before display. See NotificationService.decryptNotificationBody().
    val keyHeader: String? = null,
    val encryptedBody: String? = null,
)

