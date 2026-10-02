package id.homebase.api.client.drives.upload

import id.homebase.api.client.drives.TargetDrive
import kotlinx.serialization.Serializable
import id.homebase.api.common.OdinId

/** Base transit options for file transfers. */
@Serializable
data class TransitOptions(
    val recipients: List<OdinId>? = null,

    /** If true, file is removed after it's received by all recipients. */
        val isTransient: Boolean? = null,
    val schedule: ScheduleOptions? = null,
    val priority: PriorityOptions? = null,
    val sendContents: SendContents? = null,
    val remoteTargetDrive: TargetDrive? = null,

    /** If true, send app notifications. */
        val useAppNotification: Boolean? = null,

    /** App notification options, required when useAppNotification is true. */
        val appNotificationOptions: PushNotificationOptions? = null
) {
    companion object {
    }
}
