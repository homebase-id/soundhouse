package id.homebase.audio.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.homebase.audio.importing.AudioQuality
import id.homebase.audio.importing.Codecs
import id.homebase.audio.importing.QualityTier
import id.homebase.audio.importing.formatKilohertz
import id.homebase.audio.importing.tier
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.quality_bit_depth
import id.homebase.audio.resources.quality_bitrate
import id.homebase.audio.resources.quality_channels
import id.homebase.audio.resources.quality_hi_res
import id.homebase.audio.resources.quality_lossless
import id.homebase.audio.resources.quality_lossy
import id.homebase.audio.resources.quality_mono
import id.homebase.audio.resources.quality_sample_rate
import id.homebase.audio.resources.quality_stereo
import id.homebase.audio.ui.theme.tabular
import org.jetbrains.compose.resources.stringResource

fun codecDisplayName(codec: String): String = when (codec) {
    Codecs.OPUS -> "Opus"
    Codecs.VORBIS -> "Vorbis"
    else -> codec.uppercase()
}

@Composable
fun tierLabel(tier: QualityTier): String = stringResource(
    when (tier) {
        QualityTier.HiRes -> AR.string.quality_hi_res
        QualityTier.Lossless -> AR.string.quality_lossless
        QualityTier.Lossy -> AR.string.quality_lossy
    }
)

@Composable
fun sampleRateLabel(hz: Int): String = stringResource(AR.string.quality_sample_rate, formatKilohertz(hz))

@Composable
fun bitDepthLabel(bits: Int): String = stringResource(AR.string.quality_bit_depth, bits)

@Composable
fun bitrateLabel(bps: Long): String = stringResource(AR.string.quality_bitrate, ((bps + 500) / 1000).toInt())

@Composable
fun channelsLabel(channels: Int): String = when (channels) {
    1 -> stringResource(AR.string.quality_mono)
    2 -> stringResource(AR.string.quality_stereo)
    else -> stringResource(AR.string.quality_channels, channels)
}

/** Lossless formats are described by depth and rate, lossy ones by bitrate: "FLAC · 24-bit · 96 kHz", "MP3 · 320 kbps". */
@Composable
fun qualitySummary(quality: AudioQuality): String {
    val lossy = quality.tier == QualityTier.Lossy
    return listOfNotNull(
        quality.codec?.let(::codecDisplayName),
        quality.bitDepth?.takeUnless { lossy }?.let { bitDepthLabel(it) },
        quality.sampleRateHz?.takeUnless { lossy }?.let { sampleRateLabel(it) },
        quality.bitrateBps?.takeIf { lossy }?.let { bitrateLabel(it) },
    ).joinToString(" · ")
}

/** Only lossless tiers get a badge; lossy is the unremarkable default. */
@Composable
fun QualityBadge(tier: QualityTier, modifier: Modifier = Modifier) {
    if (tier == QualityTier.Lossy) return
    val hiRes = tier == QualityTier.HiRes
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (hiRes) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (hiRes) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            tierLabel(tier),
            style = MaterialTheme.typography.labelMedium.tabular(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
