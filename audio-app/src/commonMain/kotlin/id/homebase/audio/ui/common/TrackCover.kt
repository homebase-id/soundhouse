package id.homebase.audio.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import id.homebase.audio.data.AudioDriveApi
import id.homebase.audio.data.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Decrypted, decoded cover thumbnails, kept in a small LRU so scrolling doesn't re-decode. */
class CoverLoader(private val api: AudioDriveApi, private val maxEntries: Int = 64) {
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(track: AudioTrack, minPixels: Int): ImageBitmap? {
        if (!track.hasCover) return null
        val thumb = track.covers.firstOrNull { it.width >= minPixels } ?: track.covers.last()
        val key = "${track.fileId}:${thumb.width}:${thumb.lastModified}"
        lock.withLock { cache.remove(key)?.let { cache[key] = it; return it } }
        val image = try {
            withContext(Dispatchers.Default) { api.readCover(track, minPixels)?.decodeToImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, "CoverLoader") { "Cover for ${track.fileId} could not be loaded" }
            null
        } ?: return null
        lock.withLock {
            cache[key] = image
            if (cache.size > maxEntries) cache.remove(cache.keys.first())
        }
        return image
    }
}

/** Absent in previews and render tests, where tracks fall back to generated artwork. */
val LocalCoverLoader = staticCompositionLocalOf<CoverLoader?> { null }

/** The track's real cover when it has one, over its generated artwork (shown until the cover arrives). */
@Composable
fun TrackCover(track: AudioTrack, modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp, minPixels: Int = 320) {
    val loader = LocalCoverLoader.current
    val cover by produceState<ImageBitmap?>(null, track.fileId, track.covers, loader) {
        value = loader?.load(track, minPixels)
    }
    val coverAlpha by animateFloatAsState(if (cover != null) 1f else 0f)
    Box(modifier.clip(RoundedCornerShape(cornerRadius))) {
        TrackArtwork(track.title, seed = track.fileId.toString(), modifier = Modifier.fillMaxSize(), cornerRadius = cornerRadius)
        cover?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(coverAlpha),
            )
        }
    }
}
