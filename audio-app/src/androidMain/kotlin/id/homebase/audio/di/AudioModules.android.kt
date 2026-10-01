package id.homebase.audio.di

import id.homebase.api.file.AndroidFileOperationsProvider
import id.homebase.api.file.FileOperationsProvider
import id.homebase.core.audio.AndroidAudioPlayer
import id.homebase.core.audio.AndroidAudioRecorder
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.settings.createSettings
import org.koin.android.ext.koin.androidContext
import coil3.ImageLoader
import id.homebase.core.image.PublicImageFetcher
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { AndroidFileOperationsProvider(androidContext()) }
    single { createSettings(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }
    // Login's identity avatar (PublicAvatar) injects this; disk cache off as in chat-kmp, Coil would store avatars unencrypted.
    single {
        ImageLoader.Builder(androidContext())
            .components { add(PublicImageFetcher.Factory(get())) }
            .diskCache(null)
            .build()
    }
    factory<AudioPlayer> { AndroidAudioPlayer() }
}
