package id.homebase.api.client.drives

import id.homebase.api.serialization.UuidSerializer
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A drive specifier for incoming requests to perform actions on a drive.
 * (essentially, this hides the internal DriveId).
 *
 */

// TODO: probably remove "type" and rename alias to driveId
@Serializable
data class GlobalTransitIdFileIdentifier(

    @Serializable(with = UuidSerializer::class)
    val globalTransitId: Uuid,

    val targetDrive: TargetDrive

)

@Serializable
data class TargetDrive(
    @Serializable(with = UuidSerializer::class)
    val alias: Uuid,
    @Serializable(with = UuidSerializer::class)
    val type: Uuid
) {
    fun isValid(): Boolean {
        return alias != Uuid.NIL && type != Uuid.NIL
    }

    override fun toString(): String {
        return "Alias=$alias Type=$type"
    }

    companion object {
    }
}