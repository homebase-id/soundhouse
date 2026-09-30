package id.homebase.audio.di

import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.JvmFileOperationsProvider
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import id.homebase.core.audio.JvmAudioPlayer
import id.homebase.core.audio.JvmAudioRecorder
import id.homebase.core.settings.createSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun audioPlatformModule(): Module = module {
    single<FileOperationsProvider> { JvmFileOperationsProvider() }
    single { createSettings() }
    single<AudioRecorder> { JvmAudioRecorder() }
    factory<AudioPlayer> { JvmAudioPlayer() }
}
