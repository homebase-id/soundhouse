package id.homebase.audio.di

import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.IOSFileOperationsProvider
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.audio.IOSAudioPlayer
import id.homebase.core.audio.IOSAudioRecorder
import id.homebase.core.settings.createSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { IOSFileOperationsProvider() }
    single { createSettings() }
    single<AudioRecorder> { IOSAudioRecorder() }
    factory<AudioPlayer> { IOSAudioPlayer() }
}
