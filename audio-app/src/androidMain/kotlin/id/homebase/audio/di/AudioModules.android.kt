package id.homebase.audio.di

import id.homebase.api.file.AndroidFileOperationsProvider
import id.homebase.api.file.FileOperationsProvider
import id.homebase.core.audio.AndroidAudioPlayer
import id.homebase.core.audio.AndroidAudioRecorder
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.settings.createSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { AndroidFileOperationsProvider(androidContext()) }
    single { createSettings(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }
    factory<AudioPlayer> { AndroidAudioPlayer() }
}
