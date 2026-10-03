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
    fun `sign-in asks for read and write on the audio drive and nothing else`() {
        val request = targetDriveAccessRequest.single()
        assertEquals("7dc6798f-4395-4760-850f-4c447036c2d1", request.alias)
        assertEquals("9834ff1c-4147-446f-a3fb-483d2b0c3ce0", request.type)
        assertEquals(audioLabeledDrive.drive.alias.toString(), request.alias)
        assertEquals("audio", request.driveSlug)
        assertEquals("audio", request.driveTypeSlug)
        assertEquals(setOf(DrivePermission.Read, DrivePermission.Write), request.permissions.toSet())
        assertTrue(appPermissions.isEmpty())
        assertTrue(circleDriveTargetRequest.isEmpty())
        assertTrue(loginCircleIds.isEmpty())
    }
}
