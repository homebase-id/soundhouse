package id.homebase.audio.download

import id.homebase.api.ActivityProvider

actual fun downloadsDirectory(): String =
    ActivityProvider.requireApplicationContext().filesDir.resolve("downloads").absolutePath
