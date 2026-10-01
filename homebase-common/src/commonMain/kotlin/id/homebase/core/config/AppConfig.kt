package id.homebase.core.config

import id.homebase.api.client.drives.TargetDrive
import id.homebase.api.common.OdinId
import id.homebase.api.youauth.AppPermissionType
import id.homebase.api.youauth.DrivePermission
import id.homebase.api.youauth.PermissionExtensionConfig
import id.homebase.api.youauth.TargetDriveAccessRequest
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * A drive the app mounts for sync, paired with a human-readable label.
 *
 * @param ownerOdinId the **owning identity** when this drive is hosted on a peer (a community
 *   owner's collaborative drive); null for the logged-in user's own drives. When set, the drive is
 *   synced/queried/written over peer and gets a per-owner peer websocket instead of riding the
 *   user's own-host websocket. Nullable with a default so existing serialized registry files (which
 *   predate this field) still parse as own drives.
 */
@Serializable
data class LabeledDrive(
    val drive: TargetDrive,
    val label: String,
    val ownerOdinId: OdinId? = null,
)

/**
 * Central app configuration for authentication, permissions, and drives. Used by both
 * LoginViewModel (for initial auth) and HomeViewModel (for permission checking).
 */
object AppConfig {
    /**
     * Borrowed from Homebase Chat until this app gets its own registration: the test session was
     * minted for this id. Changing it also means changing [APP_SLUG] ("chat" belongs to this id).
     */
    const val APP_ID = "2d78140138044b57b4aad8e4e2ef39f4"

    const val APP_NAME = "Homebase Simple Audio"

    /**
     * App slug, sent as `as` in the YouAuth permission request. "chat" is the slug odin-core's
     * built-in Chat app owns for [APP_ID]; give this app its own slug together with its own id.
     */
    const val APP_SLUG = "chat"

    // Deep link scheme for returning from permission extension
    const val DEEP_LINK_SCHEME = "homebase-audio"

    const val CREATE_ACCOUNT_CALLBACK_HOST = "create-account-callback"

    const val REPORT_CONTENT_URL = "https://ravenhosting.cloud/report/content"
}

/**
 * Return URL the owner console redirects the browser to once the user has finished
 * extending app permissions. Platform-specific because the mechanism differs:
 *
 * - **Mobile (Android/iOS)**: a custom URL scheme deep link
 *   (`homebase-audio://permission-callback`) registered on the device.
 * - **Web**: `<base>permission-callback`, which index.html posts back to the opener tab.
 * - **Desktop (JVM)**: a localhost loopback URL handled by the in-process
 *   [id.homebase.api.browser.LocalCallbackServer] (the same server the OAuth login
 *   flow uses). The implementation must ensure the server is running before returning.
 *
 * Invoked at URL-build time (per extend-permissions click) via the lambda in
 * [PermissionExtensionConfig.returnUrl], not at config-build time — so a JVM
 * callback server that was stopped between checks is restarted, and the URL
 * carries a live port.
 */
expect fun returnUrl(): String

/**
 * Return URL the owner data-upgrade page redirects to once the upgrade completes:
 * deep link on mobile, localhost loopback on desktop, the app's own page on web.
 */
expect fun dataUpgradeReturnUrl(): String

/**
 * Return URL the sign-up flow sends the user back to once their new identity is set up,
 * carrying the created domain as `?domain=`. Null where nothing can catch it: the owner
 * console would redirect the browser at a scheme the OS doesn't know, so those platforms
 * ask for no return at all and the user finishes in the browser.
 *
 * Mobile only today — desktop could use [id.homebase.api.browser.LocalCallbackServer] the
 * way [returnUrl] does, once a desktop sign-up is worth the route.
 */
expect fun createAccountReturnUrl(): String?

// TypeIds
const val OWNER_FOLLOWER_TYPE_ID = "2cc468af-109b-4216-8119-542401e32f4d"
const val OWNER_CONNECTION_REQUEST_TYPE_ID = "8ee62e9e-c224-47ad-b663-21851207f768"
const val OWNER_CONNECTION_ACCEPTED_TYPE_ID = "79f0932a-056e-490b-8208-3a820ad7c321"
const val OWNER_INTRODUCTION_RECEIVED_TYPE_ID = "f100bfa0-ac4e-468a-9322-bdaf6059ec8a"
const val OWNER_INTRODUCTION_ACCEPTED_TYPE_ID = "f56ee792-56dd-45fd-8f9e-f96bb5d0e3de"
const val FEED_NEW_CONTENT_TYPE_ID = "ad695388-c2df-47a0-ad5b-fc9f9e1fffc9"
const val FEED_NEW_REACTION_TYPE_ID = "37dae95d-e137-4bd4-b782-8512aaa2c96a"
const val FEED_NEW_COMMENT_TYPE_ID = "1e08b70a-3826-4840-8372-18410bfc02c7"

// APP IDs
const val MAIL_APP_ID = "6e8ecfff-7c15-40e4-94f4-d6e83bfb5857"
const val FEED_APP_ID = "5f887d80-0132-4294-ba40-bda79155551d"
const val OWNER_APP_ID = "ac126e09-54cb-4878-a690-856be692da16"
const val COMMUNITY_APP_ID = "77ed6136-6b33-4654-8088-3d89c91e6065"

// The server requires both slugs on every drive named in a permission request.
private const val AUDIO_DRIVE_SLUG = "audio"
private const val AUDIO_DRIVE_TYPE_SLUG = "audio"

val audioLabeledDrive = LabeledDrive(
    drive = TargetDrive(
        alias = Uuid.parse("7dc6798f-4395-4760-850f-4c447036c2d1"),
        type = Uuid.parse("9834ff1c-4147-446f-a3fb-483d2b0c3ce0"),
    ),
    label = "Audio",
)

val appPermissions: List<AppPermissionType> = emptyList()

// The sign-in request: Read+Write on the Audio drive only.
val targetDriveAccessRequest: List<TargetDriveAccessRequest> =
    listOf(
        TargetDriveAccessRequest(
            alias = audioLabeledDrive.drive.alias.toString(),
            type = audioLabeledDrive.drive.type.toString(),
            name = audioLabeledDrive.label,
            description = "Drive which contains your audio library",
            permissions = listOf(DrivePermission.Read, DrivePermission.Write),
            driveSlug = AUDIO_DRIVE_SLUG,
            driveTypeSlug = AUDIO_DRIVE_TYPE_SLUG,
        ),
    )

// Sign-in asks for the Audio drive only: no circle drives and no circles.
val circleDriveTargetRequest: List<TargetDriveAccessRequest> = emptyList()
val loginCircleIds: List<String> = emptyList()

// Always mounted and synced; the only drive this app reads or writes.
val mandatorySyncDrives: List<LabeledDrive> = listOf(audioLabeledDrive)
