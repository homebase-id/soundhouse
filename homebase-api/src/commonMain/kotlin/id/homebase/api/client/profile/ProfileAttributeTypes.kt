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
    const val PHOTO = "5ae0c1c8a5260bc7b6648f6fbd115c35"
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

    companion object {
    }
}
