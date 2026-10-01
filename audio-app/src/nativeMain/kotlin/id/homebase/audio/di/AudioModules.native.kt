package id.homebase.audio.di

import id.homebase.core.util.IosNetworkMonitor
import id.homebase.core.util.NetworkMonitor
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.IOSFileOperationsProvider
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.audio.IOSAudioPlayer
import id.homebase.core.audio.IOSAudioRecorder
import id.homebase.core.settings.createSettings
import coil3.ImageLoader
import coil3.PlatformContext
import id.homebase.core.image.PublicImageFetcher
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { IOSFileOperationsProvider() }
    single { createSettings() }
    single<AudioRecorder> { IOSAudioRecorder() }
    // Login's identity avatar (PublicAvatar) injects this; disk cache off as in chat-kmp, Coil would store avatars unencrypted.
    single {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(PublicImageFetcher.Factory(get())) }
            .diskCache(null)
            .build()
    }
    factory<AudioPlayer> { IOSAudioPlayer() }
    single<NetworkMonitor> { IosNetworkMonitor() }
}
