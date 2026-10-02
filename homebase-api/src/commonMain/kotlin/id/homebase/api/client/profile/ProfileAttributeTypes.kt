package id.homebase.api.client.profile

/**
 * The owner's editable **standard-profile** attribute types and their `data` keys.
 *
 * Each profile attribute is a file on the owner's ProfileDrive (`fileType = 77`). The attribute's
 * `type` is one of the no-dash GUIDs below; the server validates `type` on write but does NOT
 * validate the `data` keys — a misspelled key still saves (200) but never surfaces, so the keys
 * here must match the server's expectation exactly (note the deliberate `birtday_date` typo).
 *
 * Type GUIDs are stored **no-dash** because that is the shape the V2 `/profile/attributes` write
 * endpoint accepts and the shape we normalize drive-read types to (see
 * [ProfileAttribute.normalizeType]).
 */
object ProfileAttributeTypes {
    // --- type ids (no-dash GUIDs) ---
    const val NAME = "b068931cc450442b63f5b3d276ea4297"
    const val STATUS = "9acb44549b41563697bb490144ec6258"
    const val TWITTER = "54ecbdc035fd1a44d0524303cd104411"
    const val FACEBOOK = "ccda59a703e94accdaab95b58f7c20b6"
    const val INSTAGRAM = "345fef7bada5b100001e4c78111c86de"
    const val TIKTOK = "d58890b2f156a0b9413b388773b1b0a7"
    const val LINKEDIN = "a050c5ee4b5139b730cd7eb44e7db69a"
    /** The owner's profile photo — written via the dedicated `/profile/attributes/photo` endpoint,
     *  not [ProfileProvider.saveAttribute]. Multiple photo attributes can coexist (one per
     *  [ProfileVisibility] tier); see [ProfileRepository.uploadPhoto]. */
    const val PHOTO = "5ae0c1c8a5260bc7b6648f6fbd115c35"

    // --- data keys: Name ---
    const val KEY_GIVEN_NAME = "givenName"
    const val KEY_SURNAME = "surname"

    const val KEY_STATUS = "status"

    // --- data keys: Socials (the key is the network name) ---
    const val KEY_TWITTER = "twitter"
}

/**
 * Who may read a profile attribute. Serialized to the `visibility` string the V2 write endpoint
 * expects, and parsed back from the file's `requiredSecurityGroup` on read.
 */
enum class ProfileVisibility(val wireValue: String) {
    ANONYMOUS("anonymous"),
    AUTHENTICATED("authenticated"),
    CONNECTED("connected"),
    OWNER("owner");

    /** The `/profile/attributes/photo` endpoint's `visibility` field is PascalCase
     *  ("Anonymous"/"Authenticated"/"Connected"/"Owner"), unlike [wireValue] used by the generic
     *  attribute endpoint — see [ProfileRepository.uploadPhoto]. */
    val photoWireValue: String get() = wireValue.replaceFirstChar { it.uppercaseChar() }

    companion object {
    }
}
