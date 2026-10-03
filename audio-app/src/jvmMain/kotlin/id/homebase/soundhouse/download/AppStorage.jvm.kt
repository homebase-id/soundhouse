package id.homebase.soundhouse.download

import id.homebase.api.file.JvmFileSystemUtil

actual fun appDataDirectory(): String = JvmFileSystemUtil.getAppDataDirectory().absolutePath
