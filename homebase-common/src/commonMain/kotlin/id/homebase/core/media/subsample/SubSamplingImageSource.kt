package id.homebase.core.media.subsample

import id.homebase.api.common.OdinId
import id.homebase.core.image.HomebaseImageData

sealed interface SubSamplingImageSource {
    class Remote(
        val imageData: HomebaseImageData,
    ) : SubSamplingImageSource

    /** An identity's published `/pub/image`, keyed on the identity so no caller handles its URL. */
    class Avatar(
        val odinId: OdinId,
    ) : SubSamplingImageSource
}

fun avatarSharedElementKey(odinId: OdinId): String = "avatar-${odinId.domainName}"
