package id.homebase.audio.download

import id.homebase.api.file.JvmFileSystemUtil

actual fun downloadsDirectory(): String = JvmFileSystemUtil.getAppDataDirectory().resolve("downloads").absolutePath
