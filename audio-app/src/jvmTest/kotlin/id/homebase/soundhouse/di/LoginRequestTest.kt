package id.homebase.soundhouse.di

import id.homebase.core.config.appPermissions
import id.homebase.core.config.audioLabeledDrive
import id.homebase.core.config.circleDriveTargetRequest
import id.homebase.core.config.loginCircleIds
import id.homebase.core.config.targetDriveAccessRequest
import id.homebase.api.youauth.DrivePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The owner page rejects or over-grants anything else, so the sign-in request is pinned exactly. */
class LoginRequestTest {
    @Test
    fun `sign-in asks for read and write on the Soundhouse drive and nothing else`() {
        val request = targetDriveAccessRequest.single()
        assertEquals("2da95fb3-19c8-4c50-a8d3-073afda07d4c", request.alias)
        assertEquals("bfaf50d6-f74d-40d5-bf1e-ecb83e474d76", request.type)
        assertEquals(audioLabeledDrive.drive.alias.toString(), request.alias)
        assertEquals("Soundhouse", request.name)
        assertEquals("soundhouse", request.driveSlug)
        assertEquals("soundhouse", request.driveTypeSlug)
        assertEquals(setOf(DrivePermission.Read, DrivePermission.Write), request.permissions.toSet())
        assertTrue(appPermissions.isEmpty())
        assertTrue(circleDriveTargetRequest.isEmpty())
        assertTrue(loginCircleIds.isEmpty())
    }
}
