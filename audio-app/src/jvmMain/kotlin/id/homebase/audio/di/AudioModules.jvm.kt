package id.homebase.audio.di

import id.homebase.core.util.NetworkMonitor
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.JvmFileOperationsProvider
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.audio.JvmAudioPlayer
import id.homebase.core.audio.JvmAudioRecorder
import id.homebase.core.settings.createSettings
import coil3.ImageLoader
import coil3.PlatformContext
import id.homebase.core.image.PublicImageFetcher
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { JvmFileOperationsProvider() }
    single { createSettings() }
    single<AudioRecorder> { JvmAudioRecorder() }
    // Login's identity avatar (PublicAvatar) injects this; disk cache off as in chat-kmp, Coil would store avatars unencrypted.
    single {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(PublicImageFetcher.Factory(get())) }
            .diskCache(null)
            .build()
    }
    factory<AudioPlayer> { JvmAudioPlayer() }
    // Desktops have no metered-network signal; treat them as unmetered.
    single<NetworkMonitor> { NetworkMonitor { true } }
}
