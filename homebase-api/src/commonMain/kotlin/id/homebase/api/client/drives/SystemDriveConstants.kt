@file:OptIn(ExperimentalUuidApi::class)

package id.homebase.api.client.drives

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Built-in drives
 *
 * Ported from C# Odin.Services.Drives.SystemDriveConstants
 *
 * DO NOT CHANGE ANY VALUES
 */
object SystemDriveConstants {
    val chatDrive = TargetDrive(
        alias = Uuid.parse("9ff813af-f2d6-1e2f-9b9d-b189e72d1a11"),
        type = Uuid.parse("66ea8355-ae41-55c3-9b5a-719166b510e3")
    )

    val feedDrive =
        TargetDrive(
            alias = Uuid.parse("4db49422-ebad-02e9-9ab9-6e9c477d1e08"),
            type = Uuid.parse("a3227ffb-a876-08be-eb24-fee9b70d92a6")
        )
}
