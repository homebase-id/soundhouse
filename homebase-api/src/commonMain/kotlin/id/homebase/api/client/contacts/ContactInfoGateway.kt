package id.homebase.api.client.contacts

import id.homebase.api.client.profile.ProfileCard
import id.homebase.api.client.profile.PublicProfileProviderCached
import id.homebase.api.common.OdinId

// The one place that reaches /pub/profile and /pub/image.
class ContactInfoGateway internal constructor(
    private val publicProfiles: PublicProfileProviderCached,
) {
    suspend fun avatarBytes(odinId: OdinId): ByteArray? = publicProfiles.getPublicImage(odinId)

    suspend fun profileCard(odinId: OdinId): ProfileCard? = publicProfiles.getPublicProfile(odinId)

    suspend fun clearCaches() = publicProfiles.clearCaches()
}
