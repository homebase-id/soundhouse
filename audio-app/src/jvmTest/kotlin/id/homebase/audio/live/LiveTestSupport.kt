package id.homebase.audio.live

import id.homebase.audio.data.AudioDriveApi
import kotlin.uuid.Uuid

/** Tag on every file the live suite writes, so cleanup can find leftovers from failed runs. */
internal val LIVE_TEST_TAG: Uuid = Uuid.parse("0a0d1e57-7e57-4a0d-9e57-1ea5ed7e57a0")

/**
 * Soft delete first so signed-in devices sync the deletion, then hard delete to keep the drive tidy.
 * A hard delete alone never reaches other devices' libraries (sync only sees changes).
 */
internal suspend fun purgeTagged(api: AudioDriveApi) {
    api.queryTrackFiles(tagsAnyOf = listOf(LIVE_TEST_TAG)).forEach {
        api.deleteTrack(it.fileId)
        api.hardDeleteTrack(it.fileId)
    }
}

internal fun fixtureBytes(name: String): ByteArray =
    checkNotNull(LiveTestSupportAnchor::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
        .use { it.readBytes() }

private object LiveTestSupportAnchor
