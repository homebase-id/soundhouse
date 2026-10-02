package id.homebase.api.video

import id.homebase.api.image.MediaQuality

/** Encoder tier a video payload was queued with; kept so stored outbox rows still decode. */
enum class VideoQuality {
    /** ~480p short edge, ~1.25 Mbps video + 128 kbps audio. */
    LOW,

    /** ~720p short edge, ~2.5 Mbps video + 128 kbps audio. Current default. */
    STANDARD,

    /** ~1080p short edge, ~5 Mbps video + 192 kbps audio. */
    HIGH,
}

