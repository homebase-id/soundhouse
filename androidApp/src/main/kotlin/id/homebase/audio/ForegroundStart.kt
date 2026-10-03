package id.homebase.audio

import android.app.Notification
import android.app.Service
import android.os.Build

// The typed overload is API 29; below it the manifest's foregroundServiceType applies.
internal fun Service.startForegroundCompat(id: Int, notification: Notification, type: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(id, notification, type)
    else startForeground(id, notification)
}
