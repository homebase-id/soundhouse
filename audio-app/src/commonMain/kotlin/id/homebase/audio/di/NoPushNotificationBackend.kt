package id.homebase.audio.di

import id.homebase.core.notifications.NotificationBackend
import id.homebase.core.notifications.NotificationListener

/** The audio app registers no push token and shows no notifications. */
object NoPushNotificationBackend : NotificationBackend {
    override fun addListener(listener: NotificationListener) = Unit
    override fun setLogger(log: (String) -> Unit) = Unit
    override fun showLocalNotification(id: Int, title: String, body: String, payloadData: Map<String, String>) = Unit
    override suspend fun getPushToken(): String? = null
    override suspend fun deletePushToken() = Unit
}
