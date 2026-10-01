package id.homebase.audio.download

import id.homebase.api.ActivityProvider

actual fun appDataDirectory(): String = ActivityProvider.requireApplicationContext().filesDir.absolutePath
