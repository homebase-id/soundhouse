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
    val contactDrive =
        TargetDrive(
            alias = Uuid.parse("2612429d-1c3f-0372-82b8-d42fb2cc0499"),
            type = Uuid.parse("70e92f0f-94d0-5f5c-7dcd-36466094f3a5")
        )

    val profileDrive =
        TargetDrive(
            alias = Uuid.parse("8f12d8c4-9338-13d3-7848-8d91ed23b64c"),
            type = Uuid.parse("59724153-0e3e-f24b-28b9-a75ec3a5c45c")
        )

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
