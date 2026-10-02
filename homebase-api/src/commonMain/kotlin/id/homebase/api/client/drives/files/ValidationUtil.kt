package id.homebase.api.client.drives.files

import kotlin.uuid.Uuid

object ValidationUtil {
    fun requireValidUuid(value: Uuid?, name: String): Uuid {
        val nonNull = requireNotNull(value) {
            "$name is required"
        }

        require(nonNull != Uuid.NIL) {
            "$name must not be all zeros"
        }

        return nonNull
    }
}
